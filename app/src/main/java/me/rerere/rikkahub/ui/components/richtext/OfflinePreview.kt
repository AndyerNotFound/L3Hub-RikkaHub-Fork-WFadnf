package me.rerere.rikkahub.ui.components.richtext

import java.util.Locale

/**
 * 离线、无脚本的整条消息预览。
 *
 * 目标端的 `mark.html` 通过 esm.sh / jsdelivr 在运行时取 markdown-it、KaTeX、highlight.js，
 * 断网时预览是空的。这里提供一个完全不依赖网络、也不执行任何脚本的替代：
 * 所有文本先转义，再只由本文件生成固定的标签；CSP 里 `script-src` 直接留空。
 *
 * 覆盖的是“看内容”需要的子集：标题、段落、引用、列表、表格、分隔线、围栏代码块，
 * 行内粗体/斜体/行内代码/链接。图片不加载（离线场景下会失败），只保留替代文字和地址。
 * 需要交互能力（Mermaid、ECharts 等）的渲染器不走这里，仍在聊天内用各自的原生路径。
 */

/** 预览正文的长度上限，超出后截断并明确标注。 */
internal const val MAX_OFFLINE_PREVIEW_CHARS = 200_000

/** 单个代码块的长度上限，避免一个巨型围栏把整页撑爆。 */
private const val MAX_OFFLINE_CODE_CHARS = 20_000
private const val MAX_QUOTE_DEPTH = 8
private const val MAX_INLINE_DEPTH = 8
private const val MAX_LINK_LENGTH = 2048
private const val MAX_TABLE_COLUMNS = 64
private const val MAX_TABLE_ROWS = 128

private class PreviewBudget {
    var remaining = 8192
        private set
    var shortened = false

    fun spend(count: Int = 1): Boolean {
        if (count > remaining) return false
        remaining -= count
        return true
    }
}

internal data class OfflinePreviewTheme(
    val background: String = "#ffffff",
    val onBackground: String = "#1b1b1f",
    val surface: String = "#f4f4f7",
    val onSurfaceVariant: String = "#5a5a63",
    val primary: String = "#4a5bb5",
    val outlineVariant: String = "#d8d8de",
    val codeBackground: String = "#f0f0f4",
)

/**
 * 生成自包含的预览页面。
 *
 * @param markdown 原始 Markdown；不会被改写，只在渲染时读取
 * @param dark 是否使用深色配色（调用方一般传主题的深色判定结果）
 */
internal fun buildOfflinePreviewHtml(
    markdown: String,
    dark: Boolean,
    theme: OfflinePreviewTheme = if (dark) DARK_OFFLINE_THEME else LIGHT_OFFLINE_THEME,
    truncatedLabel: String = "预览已截断。完整内容请在聊天里查看。",
): String {
    val truncated = markdown.length > MAX_OFFLINE_PREVIEW_CHARS
    val budget = PreviewBudget()
    val body = renderOfflineMarkdown(markdown.takeUtf16Prefix(MAX_OFFLINE_PREVIEW_CHARS), budget, quoteDepth = 0)
    val notice = if (truncated || budget.shortened) {
        "<p class=\"notice\">${escapeHtmlText(truncatedLabel)}</p>"
    } else {
        ""
    }
    return buildString(body.length + 2048) {
        append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        // 没有脚本，也没有任何外部资源：连字体和图片都不允许取。
        append(
            "<meta http-equiv=\"Content-Security-Policy\" content=\"" +
                "default-src 'none'; img-src 'none'; style-src 'unsafe-inline'; " +
                "script-src 'none'; connect-src 'none'; font-src 'none'; " +
                "frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'\">"
        )
        append("<style>")
        append(offlinePreviewCss(theme))
        append("</style></head><body><article class=\"message\">")
        append(body)
        append(notice)
        append("</article></body></html>")
    }
}

private fun String.takeUtf16Prefix(limit: Int): String {
    if (length <= limit) return this
    if (limit == 0) return ""
    val splitPair = Character.isHighSurrogate(this[limit - 1]) && Character.isLowSurrogate(this[limit])
    return substring(0, if (splitPair) limit - 1 else limit)
}

private val LIGHT_OFFLINE_THEME = OfflinePreviewTheme()
private val DARK_OFFLINE_THEME = OfflinePreviewTheme(
    background = "#121316",
    onBackground = "#e6e6ea",
    surface = "#1c1d21",
    onSurfaceVariant = "#a8a8b2",
    primary = "#a8b4ff",
    outlineVariant = "#33343a",
    codeBackground = "#1a1b1f",
)

private fun offlinePreviewCss(theme: OfflinePreviewTheme): String = """
    :root{color-scheme:light dark}
    *{box-sizing:border-box}
    html,body{margin:0;padding:0;background:${theme.background};color:${theme.onBackground}}
    body{font:16px/1.65 -apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
        overflow-wrap:anywhere;-webkit-text-size-adjust:100%}
    .message{padding:16px 18px 28px}
    h1,h2,h3,h4,h5,h6{margin:1.2em 0 .5em;line-height:1.3;font-weight:650}
    h1{font-size:1.6em;border-bottom:1px solid ${theme.outlineVariant};padding-bottom:.3em}
    h2{font-size:1.38em;border-bottom:1px solid ${theme.outlineVariant};padding-bottom:.25em}
    h3{font-size:1.2em}h4{font-size:1.08em}h5{font-size:1em}h6{font-size:.94em;color:${theme.onSurfaceVariant}}
    p{margin:.7em 0}
    ul,ol{margin:.7em 0;padding-left:1.6em}
    li{margin:.25em 0}
    blockquote{margin:.8em 0;padding:.1em 0 .1em .9em;border-left:3px solid ${theme.outlineVariant};
        color:${theme.onSurfaceVariant}}
    hr{border:0;border-top:1px solid ${theme.outlineVariant};margin:1.4em 0}
    code{font-family:ui-monospace,SFMono-Regular,'JetBrains Mono',Menlo,monospace;font-size:.9em;
        background:${theme.codeBackground};padding:.12em .35em;border-radius:4px}
    pre{margin:.8em 0;padding:10px 12px;overflow-x:auto;background:${theme.codeBackground};
        border-radius:8px}
    pre code{background:none;padding:0;font-size:.88em;white-space:pre}
    .lang{display:block;font-size:.72em;letter-spacing:.04em;text-transform:uppercase;
        color:${theme.onSurfaceVariant};margin-bottom:6px}
    table{border-collapse:collapse;margin:.9em 0;width:100%;font-size:.95em}
    th,td{border:1px solid ${theme.outlineVariant};padding:6px 9px;text-align:left;vertical-align:top}
    th{background:${theme.surface};font-weight:650}
    .link{color:${theme.primary}}
    .verbatim{white-space:pre-wrap}
    .notice{margin-top:1.2em;padding:8px 10px;border-radius:8px;background:${theme.surface};
        color:${theme.onSurfaceVariant};font-size:.88em}
""".trimIndent()

/** 把 Markdown 的常用块级结构转成固定的 HTML。所有文本都经过 [escapeHtmlText]。 */
internal fun renderOfflineMarkdown(source: String): String = renderOfflineMarkdown(source, PreviewBudget(), quoteDepth = 0)

private fun renderOfflineMarkdown(source: String, budget: PreviewBudget, quoteDepth: Int): String {
    if (source.isEmpty()) return ""
    val output = StringBuilder(source.length + 256)
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    var index = 0
    val paragraph = ArrayList<String>()

    fun flushParagraph() {
        if (paragraph.isEmpty()) return
        val text = paragraph.joinToString("\n").trim()
        paragraph.clear()
        if (text.isEmpty()) return
        output.append("<p>").append(renderOfflineInline(text, budget)).append("</p>")
    }

    while (index < lines.size) {
        if (!budget.spend()) {
            flushParagraph()
            output.append("<p class=\"verbatim\">")
                .append(escapeHtmlText(lines.subList(index, lines.size).joinToString("\n"))).append("</p>")
            break
        }
        val line = lines[index]
        val trimmed = line.trim()

        // 围栏代码块：原样保留内容，只转义，不做行内解析。
        val fence = FENCE.matchEntire(line.trimStart())
        if (fence != null) {
            flushParagraph()
            val marker = fence.groupValues[1]
            val info = fence.groupValues[2].trim().take(120)
            val code = StringBuilder()
            var codeTruncated = false
            index++
            while (index < lines.size) {
                val candidate = lines[index]
                val closing = FENCE.matchEntire(candidate.trimStart())
                if (closing != null && closing.groupValues[1].first() == marker.first() &&
                    closing.groupValues[1].length >= marker.length && closing.groupValues[2].isBlank()
                ) {
                    index++
                    break
                }
                val remaining = MAX_OFFLINE_CODE_CHARS - code.length
                if (!codeTruncated) {
                    code.append(candidate.takeUtf16Prefix(remaining))
                    if (candidate.length > remaining) codeTruncated = true
                    else if (candidate.length < remaining) code.append('\n')
                }
                index++
            }
            if (codeTruncated) budget.shortened = true
            val body = code.toString().trimEnd('\n')
            output.append("<pre>")
            if (info.isNotEmpty()) {
                output.append("<span class=\"lang\">")
                    .append(escapeHtmlText(normalizeCodeFenceLanguage(info)))
                    .append("</span>")
            }
            output.append("<code>").append(escapeHtmlText(body)).append("</code></pre>")
            continue
        }

        when {
            trimmed.isEmpty() -> {
                flushParagraph()
                index++
            }
            HEADING.matchEntire(trimmed) != null -> {
                flushParagraph()
                val match = HEADING.matchEntire(trimmed)!!
                val level = match.groupValues[1].length.coerceIn(1, 6)
                val text = match.groupValues[2].trim()
                output.append("<h").append(level).append('>')
                    .append(renderOfflineInline(text, budget))
                    .append("</h").append(level).append('>')
                index++
            }
            isHorizontalRule(trimmed) -> {
                flushParagraph()
                output.append("<hr>")
                index++
            }
            quoteDepth < MAX_QUOTE_DEPTH && trimmed.startsWith(">") -> {
                flushParagraph()
                val quoted = ArrayList<String>()
                while (index < lines.size && lines[index].trim().startsWith(">")) {
                    quoted += lines[index].trim().removePrefix(">").removePrefix(" ")
                    index++
                }
                output.append("<blockquote>")
                    .append(renderOfflineMarkdown(quoted.joinToString("\n"), budget, quoteDepth + 1))
                    .append("</blockquote>")
            }
            BULLET_ITEM.matches(trimmed) || ORDERED_ITEM.matches(trimmed) -> {
                flushParagraph()
                val ordered = ORDERED_ITEM.matches(trimmed)
                output.append(if (ordered) "<ol>" else "<ul>")
                while (index < lines.size) {
                    val item = lines[index].trim()
                    val match = if (ordered) ORDERED_ITEM.matchEntire(item)
                    else BULLET_ITEM.matchEntire(item)
                    if (match == null || !budget.spend()) break
                    output.append("<li>")
                        .append(renderOfflineInline(match.groupValues[1].trim(), budget))
                        .append("</li>")
                    index++
                }
                output.append(if (ordered) "</ol>" else "</ul>")
            }
            else -> {
                val header = tableHeader(lines, index)
                if (header == null || !budget.spend(header.size + 4)) {
                    paragraph += line
                    index++
                    continue
                }
                flushParagraph()
                index += 2
                output.append("<table><thead><tr>")
                header.forEach { cell ->
                    output.append("<th>").append(renderOfflineInline(cell, budget)).append("</th>")
                }
                output.append("</tr></thead><tbody>")
                var rows = 0
                while (rows < MAX_TABLE_ROWS) {
                    val candidate = lines.getOrNull(index)?.takeIf { '|' in it } ?: break
                    val row = splitTableRow(candidate) ?: break
                    if (row.size > header.size || !budget.spend(row.size + 1)) break
                    output.append("<tr>")
                    row.forEach { cell ->
                        output.append("<td>").append(renderOfflineInline(cell, budget)).append("</td>")
                    }
                    output.append("</tr>")
                    rows++
                    index++
                }
                output.append("</tbody></table>")
            }
        }
    }
    flushParagraph()
    return output.toString()
}

/** 行内：粗体、斜体、行内代码、链接。其余字符一律转义。 */
private fun renderOfflineInline(text: String, budget: PreviewBudget, depth: Int = 0): String {
    if (depth >= MAX_INLINE_DEPTH || budget.remaining == 0) return escapeHtmlText(text)
    val output = StringBuilder(text.length + 32)
    // Stop rescanning the same bracket run and allow a valid label after a stray '['.
    var labelEnd = 0
    var nextLabel = 0
    var index = 0
    while (index < text.length) {
        if (budget.remaining == 0) {
            output.append(escapeHtmlText(text.substring(index)))
            break
        }
        val char = text[index]
        when {
            char == '`' -> {
                val close = text.indexOf('`', index + 1)
                if (close < 0) {
                    output.append('`')
                    index++
                } else {
                    budget.spend()
                    output.append("<code>")
                        .append(escapeHtmlText(text.substring(index + 1, close)))
                        .append("</code>")
                    index = close + 1
                }
            }
            text.startsWith("**", index) || text.startsWith("__", index) -> {
                val marker = text.substring(index, index + 2)
                val close = text.indexOf(marker, index + 2)
                if (close < 0) {
                    output.append(marker)
                    index += 2
                } else {
                    budget.spend()
                    output.append("<strong>")
                        .append(renderOfflineInline(text.substring(index + 2, close), budget, depth + 1))
                        .append("</strong>")
                    index = close + 2
                }
            }
            char == '*' || char == '_' -> {
                val close = text.indexOf(char, index + 1)
                if (close < 0) {
                    output.append(char)
                    index++
                } else {
                    budget.spend()
                    output.append("<em>")
                        .append(renderOfflineInline(text.substring(index + 1, close), budget, depth + 1))
                        .append("</em>")
                    index = close + 1
                }
            }
            char == '[' -> {
                if (labelEnd in 0..index) labelEnd = text.indexOf(']', index + 1)
                if (nextLabel in 0..index) nextLabel = text.indexOf('[', index + 1)
                val completeLabel = labelEnd > index && (nextLabel < 0 || nextLabel > labelEnd)
                val urlStart = if (completeLabel && text.getOrNull(labelEnd + 1) == '(') labelEnd + 2 else -1
                // 地址里允许出现成对的括号（维基链接很常见），按深度配对而不是找第一个 ')'。
                val urlEnd = if (urlStart >= 0) matchingParen(text, urlStart) else -1
                if (urlEnd < 0) {
                    output.append('[')
                    index++
                } else {
                    val label = text.substring(index + 1, labelEnd)
                    val url = previewLinkAddress(text.substring(urlStart, urlEnd))
                    if (url == null) {
                        output.append(escapeHtmlText(text.substring(index, urlEnd + 1)))
                    } else {
                        budget.spend()
                        output.append("<span class=\"link\">")
                            .append(renderOfflineInline(label, budget, depth + 1))
                            .append("</span> (").append(escapeHtmlText(url)).append(')')
                    }
                    index = urlEnd + 1
                }
            }
            else -> {
                output.appendEscaped(char)
                index++
            }
        }
    }
    return output.toString()
}

/** Display recognized addresses as copyable text. Other addresses keep their Markdown syntax. */
private fun previewLinkAddress(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isISOControl() || it == '\\' }) return null
    val schemeEnd = trimmed.indexOf(':')
    if (schemeEnd <= 0) return null
    return when (trimmed.substring(0, schemeEnd).lowercase(Locale.ROOT)) {
        "http", "https", "mailto" -> trimmed
        else -> null
    }
}

internal fun escapeHtmlText(value: String): String {
    if (value.none { it == '&' || it == '<' || it == '>' }) return value
    return buildString(value.length + 16) {
        value.forEach { appendEscaped(it) }
    }
}

private fun StringBuilder.appendEscaped(char: Char) {
    when (char) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        else -> append(char)
    }
}

private val FENCE = Regex("^(`{3,}|~{3,})(.*)$")
private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val BULLET_ITEM = Regex("^[-*+]\\s+(.*)$")
private val ORDERED_ITEM = Regex("^\\d{1,9}[.)]\\s+(.*)$")
private val TABLE_CELL_SEPARATOR = Regex(":?-+:?")

private fun isHorizontalRule(line: String): Boolean =
    line.length >= 3 && (line.all { it == '-' } || line.all { it == '*' } || line.all { it == '_' })

private fun tableHeader(lines: List<String>, index: Int): List<String>? {
    val line = lines[index].takeIf { '|' in it } ?: return null
    val separator = lines.getOrNull(index + 1)?.let(::splitTableRow) ?: return null
    val header = splitTableRow(line) ?: return null
    return header.takeIf { it.size == separator.size && separator.all(TABLE_CELL_SEPARATOR::matches) }
}

/** 从 [start]（左括号之后）找到配对的右括号位置；找不到返回 -1。 */
private fun matchingParen(text: String, start: Int): Int {
    var depth = 1
    var index = start
    val limit = minOf(text.length, start + MAX_LINK_LENGTH)
    while (index < limit) {
        when (text[index]) {
            '(' -> depth++
            ')' -> {
                depth--
                if (depth == 0) return index
            }
        }
        index++
    }
    return -1
}

private fun splitTableRow(line: String): List<String>? {
    val body = line.trim().removePrefix("|").removeSuffix("|")
    if (body.isBlank()) return null
    val cells = body.split('|', limit = MAX_TABLE_COLUMNS + 1)
    return if (cells.size > MAX_TABLE_COLUMNS) null else cells.map { it.trim() }
}
