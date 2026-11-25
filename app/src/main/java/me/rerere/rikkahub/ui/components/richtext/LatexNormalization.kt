package me.rerere.rikkahub.ui.components.richtext

private val BACKTICK_RUN = Regex("`+")
private val DOLLAR_RUN = Regex("\\$+")
private val LATEX_DELIMITER = Regex(
    """\\(?:\\|[()\[\]]|(begin|end)\{(equation\*?|align\*?|gather\*?|multline\*?|displaymath)\})""",
    RegexOption.IGNORE_CASE,
)
private val BARE_LATEX_LINE = Regex(
    """\\(?:displaystyle[ \t]+)?(?:d?frac|tfrac|sqrt|sum|prod|int|lim|begin\{cases\}|mathbf|mathbb|mathcal|operatorname)(?![A-Za-z]).*""",
)
// Android uses ICU regex, which rejects quantifying a zero-width boundary (\b?).
// TeX word commands end at a non-letter; punctuation commands need no boundary.
private val LATEX_LAYOUT_COMMAND_REGEX = Regex("\\\\(?:(?:left|right|quad|qquad)(?![A-Za-z])|[,;!])")

internal fun normalizeMarkdownLatex(content: String, streaming: Boolean = false): String {
    if ('\\' !in content) return content
    return buildString(content.length) {
        var proseStart = 0
        var lineStart = 0
        val fences = MarkdownFenceTracker()
        while (lineStart < content.length) {
            val lineEnd = content.lineEnd(lineStart, content.length)
            val nextLine = content.afterLineBreak(lineEnd, content.length)
            val line = content.substring(lineStart, lineEnd)
            val indent = line.takeWhile { it == ' ' || it == '\t' }
            val boundary = fences.contains(line) || line.isBlank() || indent.length >= 4 || '\t' in indent
            if (boundary) {
                appendLatexParagraph(content.substring(proseStart, lineStart))
                append(content, lineStart, nextLine)
                proseStart = nextLine
            }
            lineStart = nextLine
        }
        appendLatexParagraph(content.substring(proseStart), streaming)
    }
}

private fun StringBuilder.appendLatexParagraph(prose: String, streaming: Boolean = false) {
    if ('\\' !in prose) {
        append(prose)
        return
    }
    val runs = BACKTICK_RUN.findAll(prose).map { it.range }.toList()
    val nextByLength = mutableMapOf<Int, Int>()
    // Look up whole closing runs once. An unmatched run is just ordinary Markdown text.
    val closing = IntArray(runs.size)
    for (i in runs.indices.reversed()) {
        closing[i] = nextByLength.put(runs[i].last - runs[i].first + 1, i) ?: -1
    }
    var literalStart = 0
    var index = 0
    while (index < runs.size) {
        val codeEnd = closing[index]
        val codeStart = runs[index].first
        if (prose.isEscaped(codeStart)) {
            index++
            continue
        }
        if (codeEnd < 0) {
            if (streaming) {
                appendLatexText(prose, literalStart, codeStart)
                append(prose, codeStart, prose.length)
                return
            }
            index++
            continue
        }
        appendLatexText(prose, literalStart, codeStart)
        literalStart = runs[codeEnd].last + 1
        append(prose, codeStart, literalStart)
        index = codeEnd + 1
    }
    appendLatexText(prose, literalStart, prose.length)
}

private fun String.isEscaped(index: Int): Boolean {
    var before = index - 1
    while (before >= 0 && this[before] == '\\') before--
    return (index - before - 1) % 2 != 0
}

private data class LatexOpening(val start: Int, val end: Int, val closing: String)

private fun StringBuilder.appendLatexText(prose: String, start: Int, end: Int) {
    if (start == end) return
    val runs = DOLLAR_RUN.findAll(prose.substring(start, end)).map { it.range }.toList()
    if (runs.isEmpty()) {
        appendLatexOutsideDollars(prose, start, end)
        return
    }
    // Existing $/$$ formulas keep their source, including TeX escapes like \left\[.
    // Check run length and approximate the ASCII flanking rules of BetterText's GFM parser.
    val nextByLength = mutableMapOf<Int, Int>()
    val closing = IntArray(runs.size)
    for (i in runs.indices.reversed()) {
        val run = runs[i]
        val length = run.last - run.first + 1
        val after = prose.getOrNull(start + run.last + 1)
        val before = prose.getOrNull(start + run.first - 1)
        closing[i] = nextByLength[length] ?: -1
        if (!prose.isEscaped(start + run.first) && !after.isAsciiWord() &&
            (length > 1 || before?.isWhitespace() == false)
        ) {
            nextByLength[length] = i
        }
    }
    var copied = start
    var i = 0
    while (i < runs.size) {
        val open = start + runs[i].first
        val close = closing[i]
        val canOpen = !prose.getOrNull(open - 1).isAsciiWord() &&
            (runs[i].first < runs[i].last || prose.getOrNull(open + 1)?.isWhitespace() == false)
        if (!canOpen || close < 0 || prose.isEscaped(open)) {
            i++
            continue
        }
        appendLatexOutsideDollars(prose, copied, open)
        copied = start + runs[close].last + 1
        append(prose, open, copied)
        i = close + 1
    }
    appendLatexOutsideDollars(prose, copied, end)
}

private fun Char?.isAsciiWord(): Boolean = this != null &&
    (this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '_')

private fun StringBuilder.appendLatexOutsideDollars(prose: String, start: Int, end: Int) {
    var copied = start
    var pending: LatexOpening? = null
    val literalOpeners = mutableListOf<Int>()
    fun keepLiteral(open: LatexOpening?) {
        if (open != null && (open.closing == "\\)" || open.closing == "\\]")) {
            literalOpeners.add(open.start)
        }
    }
    // The regex recognizes only fixed-size tokens (including escaped backslashes), not formula bodies.
    for (match in LATEX_DELIMITER.findAll(prose.substring(start, end))) {
        val token = match.value
        val tokenStart = start + match.range.first
        val tokenEnd = start + match.range.last + 1
        val closing = when {
            token == "\\(" -> "\\)"
            token == "\\[" -> "\\]"
            match.groupValues[1].equals("begin", ignoreCase = true) -> "\\end{${match.groupValues[2]}}"
            else -> null
        }
        if (closing != null) {
            // Restart on a newer opener instead of swallowing prose between malformed and valid formulas.
            keepLiteral(pending)
            pending = LatexOpening(tokenStart, tokenEnd, closing)
            continue
        }
        val open = pending ?: continue
        if (!token.equals(open.closing, ignoreCase = true)) continue
        pending = null
        val body = prose.substring(open.end, tokenStart).trim()
        if (body.isEmpty()) {
            keepLiteral(open)
            continue
        }
        appendBareLatexLines(prose, copied, open.start, literalOpeners)
        literalOpeners.clear()
        when (open.closing) {
            "\\)" -> append('$').append(body.foldLatexLines()).append('$')
            "\\]" -> append("$$").append(body.foldLatexLines()).append("$$")
            else -> append("$$").append(prose.substring(open.start, tokenEnd).foldLatexLines()).append("$$")
        }
        copied = tokenEnd
    }
    keepLiteral(pending)
    appendBareLatexLines(prose, copied, end, literalOpeners)
}

private fun String.foldLatexLines(): String {
    if ('\n' !in this && '\r' !in this) return this
    // Joining a TeX comment and the next line would turn the rest of the formula into a comment.
    if (indices.any { this[it] == '%' && !isEscaped(it) }) return this
    return lineSequence().joinToString(" ") { it.trim(' ', '\t') }
}

private fun StringBuilder.appendBareLatexLines(prose: String, start: Int, end: Int, literalOpeners: List<Int>) {
    var cursor = start
    var openerIndex = 0
    while (cursor < end) {
        val lineEnd = prose.lineEnd(cursor, end)
        val wholeLine = (cursor == 0 || prose[cursor - 1].isLineBreak()) &&
            (lineEnd == prose.length || prose[lineEnd].isLineBreak())
        val line = prose.substring(cursor, lineEnd)
        val trimmed = line.trimStart(' ', '\t')
        if (wholeLine && trimmed.startsWith('\\') && BARE_LATEX_LINE.matches(trimmed) &&
            (literalOpeners.getOrNull(openerIndex) ?: lineEnd) >= lineEnd
        ) {
            append("$$").append(trimmed.trimEnd()).append("$$")
        } else {
            var copied = cursor
            while (openerIndex < literalOpeners.size && literalOpeners[openerIndex] < lineEnd) {
                val opening = literalOpeners[openerIndex++]
                // Display the punctuation as an ordinary Markdown escape. Hiding the raw opener
                // stops BetterText's own \(/\[ scan from pairing it across paragraphs or code.
                append(prose, copied, opening)
                append(if (prose[opening + 1] == '(') "&#40;" else "&#91;")
                copied = opening + 2
            }
            append(prose, copied, lineEnd)
        }
        cursor = prose.afterLineBreak(lineEnd, end)
        append(prose, lineEnd, cursor)
    }
}

private fun Char.isLineBreak() = this == '\r' || this == '\n'

private fun String.lineEnd(start: Int, limit: Int): Int {
    var end = start
    while (end < limit && !this[end].isLineBreak()) end++
    return end
}

private fun String.afterLineBreak(end: Int, limit: Int): Int = when {
    end == limit -> end
    end + 1 < limit && this[end] == '\r' && this[end + 1] == '\n' -> end + 2
    else -> end + 1
}

internal fun latexRenderCandidates(latex: String): List<String> {
    val normalized = latex
        .trim()
        .stripLatexDelimiters()
        .replace("\uFEFF", "")
        .replace("\u200B", "")
        .replace("−", "-")
        .replace("×", "\\times ")
        .replace("÷", "\\div ")
        .replace("≤", "\\le ")
        .replace("≥", "\\ge ")
        .replace("≠", "\\ne ")
        .replace("±", "\\pm ")
        .replace("∓", "\\mp ")
        .replace("∞", "\\infty ")
        .replace("⋅", "\\cdot ")

    val compatibilityFallback = normalized
        .replace("\\operatorname*", "\\mathrm")
        .replace("\\dfrac", "\\frac")
        .replace("\\tfrac", "\\frac")
        .replace("\\dbinom", "\\binom")
        .replace("\\tbinom", "\\binom")
        .replace("\\operatorname", "\\mathrm")
        .replace("\\textnormal", "\\mathrm")
        .replace("\\textrm", "\\mathrm")
        .replace("\\texttt", "\\mathtt")
        .replace("\\text", "\\mathrm")
        .replace("\\boldsymbol", "\\mathbf")
        .replace("\\bm", "\\mathbf")
        .replace("\\mathbbm", "\\mathbb")
        .replace("\\pmb", "\\mathbf")

    val environmentFallback = compatibilityFallback
        .replace(
            Regex("\\\\begin\\{(?:equation\\*?|align\\*?|gather\\*?|multline\\*?|displaymath)\\}", RegexOption.IGNORE_CASE),
            "",
        )
        .replace(
            Regex("\\\\end\\{(?:equation\\*?|align\\*?|gather\\*?|multline\\*?|displaymath)\\}", RegexOption.IGNORE_CASE),
            "",
        )
        .replace(Regex("\\\\(?:label|tag)\\{[^{}]*\\}"), "")
        .replace(Regex("\\\\(?:notag|nonumber|displaystyle|textstyle)\\b"), "")
        .replace("&", "")
        .replace("\\\\", " \\quad ")

    return listOf(normalized, compatibilityFallback, environmentFallback.trim())
        .filter(String::isNotBlank)
        .distinct()
}

internal fun latexReadableFallback(latex: String): String {
    var readable = latexRenderCandidates(latex).lastOrNull().orEmpty()
    repeat(4) {
        readable = readable
            .replace(Regex("\\\\(?:dfrac|tfrac|frac)\\{([^{}]*)\\}\\{([^{}]*)\\}"), "($1)/($2)")
            .replace(Regex("\\\\sqrt\\{([^{}]*)\\}"), "√($1)")
            .replace(Regex("\\\\(?:mathrm|mathbf|mathbb|mathcal|mathtt)\\{([^{}]*)\\}"), "$1")
    }
    val symbols = linkedMapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
        "epsilon" to "ε", "theta" to "θ", "lambda" to "λ", "mu" to "μ",
        "pi" to "π", "rho" to "ρ", "sigma" to "σ", "phi" to "φ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ",
        "Sigma" to "Σ", "Phi" to "Φ", "Omega" to "Ω",
        "times" to "×", "div" to "÷", "cdot" to "·", "pm" to "±", "mp" to "∓",
        "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥", "ne" to "≠",
        "infty" to "∞", "to" to "→", "rightarrow" to "→", "leftarrow" to "←",
        "sum" to "Σ", "prod" to "Π", "int" to "∫",
    )
    symbols.forEach { (macro, symbol) ->
        readable = readable.replace(Regex("\\\\$macro\\b"), symbol)
    }
    return readable
        .replace(LATEX_LAYOUT_COMMAND_REGEX, " ")
        .replace(Regex("\\\\([A-Za-z]+)\\*?"), "$1")
        .replace("\\{", "{")
        .replace("\\}", "}")
        .replace("{", "")
        .replace("}", "")
        .replace("\\", "")
        .replace(Regex("[ \\t]{2,}"), " ")
        .trim()
        .ifBlank { latex.trim().trim('$') }
}

internal fun String.stripLatexDelimiters(): String = when {
    startsWith("$$") && endsWith("$$") && length >= 4 -> substring(2, length - 2).trim()
    startsWith("$") && endsWith("$") && length >= 2 -> substring(1, length - 1).trim()
    startsWith("\\[") && endsWith("\\]") && length >= 4 -> substring(2, length - 2).trim()
    startsWith("\\(") && endsWith("\\)") && length >= 4 -> substring(2, length - 2).trim()
    else -> this
}
