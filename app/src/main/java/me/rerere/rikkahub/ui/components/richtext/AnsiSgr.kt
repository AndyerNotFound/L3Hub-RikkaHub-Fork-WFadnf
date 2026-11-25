package me.rerere.rikkahub.ui.components.richtext

/**
 * 只读的 ANSI/SGR 文本解析。
 *
 * 这里刻意只做 SGR（`ESC[...m`）的颜色与字形，其它控制序列一律丢弃：
 * OSC（窗口标题、超链接、剪贴板）、DCS（含图片）、设备查询、光标与模式设置全部不参与显示。
 * 未终止的字符串直接丢到结尾，不会把它的内容当成正文露出来。
 *
 * 纯函数、无 Compose 依赖，方便单独测试。源码本身不会被改写，复制/源码视图仍是原文。
 */

/** 解析后的一个字符样式片段。颜色为 ARGB，`null` 表示跟随主题。 */
internal data class AnsiStyle(
    val foreground: Int? = null,
    val background: Int? = null,
    val bold: Boolean = false,
    val dim: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false,
    val strike: Boolean = false,
) {
    companion object {
        val Default = AnsiStyle()
    }

    /** 反显时前景取背景色，没设置就退回主题色。 */
    fun resolvedForeground(defaultArgb: Int): Int =
        if (inverse) background ?: defaultArgb else foreground ?: defaultArgb

    fun resolvedBackground(defaultArgb: Int): Int =
        if (inverse) foreground ?: defaultArgb else background ?: defaultArgb
}

internal data class AnsiSegment(val text: String, val style: AnsiStyle)

internal data class AnsiDocument(
    val segments: List<AnsiSegment>,
    val truncated: Boolean = false,
) {
    val plainText: String get() = buildString { segments.forEach { append(it.text) } }
}

internal const val MAX_ANSI_SOURCE_CHARS = 128 * 1024
private const val MAX_ANSI_SEGMENTS = 4000

private val ANSI_BASE_COLORS = intArrayOf(
    0xFF000000.toInt(), 0xFFCD0000.toInt(), 0xFF00CD00.toInt(), 0xFFCDCD00.toInt(),
    0xFF0000EE.toInt(), 0xFFCD00CD.toInt(), 0xFF00CDCD.toInt(), 0xFFE5E5E5.toInt(),
    0xFF7F7F7F.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFFFFFF00.toInt(),
    0xFF5C5CFF.toInt(), 0xFFFF00FF.toInt(), 0xFF00FFFF.toInt(), 0xFFFFFFFF.toInt(),
)

/** 语言别名：这些围栏按终端输出处理。 */
private val ANSI_FENCE_LANGUAGES = setOf("ansi", "terminal", "console", "console-output", "xterm")
private val ANSI_GENERIC_LANGUAGES =
    setOf("", "text", "plaintext", "plain", "txt", "log", "shell", "bash")

internal fun isAnsiFence(language: String): Boolean =
    normalizeCodeFenceLanguage(language) in ANSI_FENCE_LANGUAGES

/** 只有出现 ESC 才值得解析；纯文本按普通代码块渲染更省。 */
internal fun hasAnsiEscapes(code: String): Boolean =
    code.contains('\u001B') ||
        code.contains("\\x1b", ignoreCase = true) ||
        code.contains("\\u001b", ignoreCase = true)

internal fun shouldRenderAsAnsi(language: String, code: String): Boolean {
    if (code.length > MAX_ANSI_SOURCE_CHARS) return false
    if (!hasAnsiEscapes(code)) return false
    // 泛型围栏也接受：日志里常见 ```text 加 ESC。源码不变，只是换个显示方式。
    val declared = normalizeCodeFenceLanguage(language)
    return isAnsiFence(language) || declared in ANSI_GENERIC_LANGUAGES
}

/** 把字面量 `\x1b` / `\u001b` 还原成真正的 ESC，模型输出的日志常这么转义。 */
internal fun unescapeAnsiSource(source: String): String =
    if ('\\' !in source) source
    else source.replace("\\x1b", "\u001B", ignoreCase = true)
        .replace("\\u001b", "\u001B", ignoreCase = true)

internal fun ansiDocument(source: String): AnsiDocument {
    val bounded = if (source.length > MAX_ANSI_SOURCE_CHARS) source.take(MAX_ANSI_SOURCE_CHARS) else source
    val input = unescapeAnsiSource(bounded)
    val segments = ArrayList<AnsiSegment>()
    val pending = StringBuilder()
    var style = AnsiStyle.Default
    var truncated = source.length > MAX_ANSI_SOURCE_CHARS

    fun flush() {
        if (pending.isEmpty()) return
        val text = pending.toString()
        pending.setLength(0)
        val last = segments.lastOrNull()
        // 相邻同样式片段合并，减少 Text 里 SpanStyle 的数量。
        if (last != null && last.style == style) {
            segments[segments.lastIndex] = last.copy(text = last.text + text)
        } else {
            segments += AnsiSegment(text, style)
        }
    }

    var index = 0
    while (index < input.length) {
        val char = input[index]
        when {
            char == '\u001B' -> {
                flush()
                val step = consumeEscape(input, index + 1, style)
                style = step.style
                index = step.next
            }
            char == '\u009B' -> {
                flush()
                val step = consumeCsi(input, index + 1, style)
                style = step.style
                index = step.next
            }
            char == '\u009D' -> index = skipControlString(input, index + 1, osc = true)
            char == '\u0090' || char == '\u0098' || char == '\u009E' || char == '\u009F' ->
                index = skipControlString(input, index + 1, osc = false)
            char == '\n' || char == '\t' -> {
                pending.append(char)
                index++
            }
            char == '\r' -> index++ // 静态显示不做回车覆盖，直接丢掉
            char == '\b' -> index++
            char.code < 0x20 || char.code == 0x7F -> index++
            char.code in 0x80..0x9F -> index++ // 其余 C1 控制符
            else -> {
                pending.append(char)
                index++
            }
        }
        if (segments.size >= MAX_ANSI_SEGMENTS) {
            truncated = true
            break
        }
    }
    flush()
    return AnsiDocument(segments.take(MAX_ANSI_SEGMENTS), truncated)
}

private data class AnsiStep(val style: AnsiStyle, val next: Int)

/** `ESC` 之后的分派。 */
private fun consumeEscape(input: String, start: Int, style: AnsiStyle): AnsiStep {
    val introducer = input.getOrNull(start)
    return when (introducer) {
        '[' -> consumeCsi(input, start + 1, style)
        ']' -> AnsiStep(style, skipControlString(input, start + 1, osc = true))
        'P', '_', '^', 'X' -> AnsiStep(style, skipControlString(input, start + 1, osc = false))
        null -> AnsiStep(style, input.length)
        else -> {
            // 其它转义：吃掉中间字节，直到终止字节为止。
            var i = start
            while (i < input.length && input[i].code in 0x20..0x2F) i++
            AnsiStep(style, if (i < input.length) i + 1 else i)
        }
    }
}

/** 解析 CSI。只处理 `m`，其余终止字节一律忽略。 */
private fun consumeCsi(input: String, start: Int, style: AnsiStyle): AnsiStep {
    var i = start
    val paramsStart = i
    while (i < input.length && input[i].code in 0x20..0x3F) i++
    if (i >= input.length) return AnsiStep(style, i)
    val finalByte = input[i]
    if (finalByte.code !in 0x40..0x7E) return AnsiStep(style, i)
    val params = input.substring(paramsStart, i)
    i++
    val next = if (finalByte == 'm' && params.length <= 128) applySgr(params, style) else style
    return AnsiStep(next, i)
}

/** 跳过 OSC/DCS 等字符串，终止于 BEL、ST 或结束。 */
private fun skipControlString(input: String, start: Int, osc: Boolean): Int {
    var i = start
    while (i < input.length) {
        val char = input[i]
        if (char == '\u009C') return i + 1
        if (osc && char == '\u0007') return i + 1
        if (char == '\u001B' && input.getOrNull(i + 1) == '\\') return i + 2
        i++
    }
    return i
}

/** SGR 是累积的：在 [current] 上继续修改，返回新样式。 */
private fun applySgr(params: String, current: AnsiStyle): AnsiStyle {
    val raw = params.split(';')
    var style = current
    var i = 0
    while (i < raw.size) {
        val token = raw[i]
        val code = if (token.isEmpty()) 0 else token.substringBefore(':').toIntOrNull() ?: -1
        when {
            code == 0 -> style = AnsiStyle.Default
            code == 1 -> style = style.copy(bold = true)
            code == 2 -> style = style.copy(dim = true)
            code == 3 -> style = style.copy(italic = true)
            code == 4 -> style = style.copy(underline = true)
            code == 7 -> style = style.copy(inverse = true)
            code == 9 -> style = style.copy(strike = true)
            code == 21 || code == 22 -> style = style.copy(bold = false, dim = false)
            code == 23 -> style = style.copy(italic = false)
            code == 24 -> style = style.copy(underline = false)
            code == 27 -> style = style.copy(inverse = false)
            code == 29 -> style = style.copy(strike = false)
            code in 30..37 -> style = style.copy(foreground = ANSI_BASE_COLORS[code - 30])
            code == 39 -> style = style.copy(foreground = null)
            code in 40..47 -> style = style.copy(background = ANSI_BASE_COLORS[code - 40])
            code == 49 -> style = style.copy(background = null)
            code in 90..97 -> style = style.copy(foreground = ANSI_BASE_COLORS[code - 90 + 8])
            code in 100..107 -> style = style.copy(background = ANSI_BASE_COLORS[code - 100 + 8])
            code == 38 || code == 48 -> {
                val (argb, consumed) = readExtendedColor(raw, i)
                if (argb != null) {
                    style = if (code == 38) style.copy(foreground = argb)
                    else style.copy(background = argb)
                }
                i += consumed
            }
        }
        i++
    }
    return style
}

/** 返回颜色与被消费的参数个数。 */
private fun readExtendedColor(raw: List<String>, index: Int): Pair<Int?, Int> {
    val mode = raw.getOrNull(index + 1)?.substringBefore(':')?.toIntOrNull()
    return when (mode) {
        5 -> {
            val n = raw.getOrNull(index + 2)?.substringBefore(':')?.toIntOrNull()
            if (n == null) null to 1 else palette256(n) to 2
        }
        2 -> {
            val r = raw.getOrNull(index + 2)?.substringBefore(':')?.toIntOrNull()
            val g = raw.getOrNull(index + 3)?.substringBefore(':')?.toIntOrNull()
            val b = raw.getOrNull(index + 4)?.substringBefore(':')?.toIntOrNull()
            if (r == null || g == null || b == null) null to 1
            else {
                val argb = 0xFF000000.toInt() or
                    (r.coerceIn(0, 255) shl 16) or
                    (g.coerceIn(0, 255) shl 8) or
                    b.coerceIn(0, 255)
                argb to 4
            }
        }
        else -> null to 0
    }
}

/** xterm 256 色：0-15 基础色，16-231 6×6×6 立方，232-255 灰阶。 */
internal fun palette256(index: Int): Int {
    val n = index.coerceIn(0, 255)
    if (n < 16) return ANSI_BASE_COLORS[n]
    if (n < 232) {
        val cube = n - 16
        fun level(value: Int) = if (value == 0) 0 else 55 + value * 40
        val r = level(cube / 36)
        val g = level((cube % 36) / 6)
        val b = level(cube % 6)
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }
    val gray = 8 + (n - 232) * 10
    return 0xFF000000.toInt() or (gray shl 16) or (gray shl 8) or gray
}
