package me.rerere.rikkahub.ui.components.message

import me.rerere.rikkahub.ui.components.richtext.MAX_DIFF_SOURCE_CHARS
import me.rerere.rikkahub.ui.components.richtext.MarkdownFenceTracker
import me.rerere.rikkahub.ui.components.richtext.looksLikeStandaloneUnifiedDiff

private val ListMarkerRegex = Regex("^ {0,3}([-*+]|\\d{1,9}[.)])\\s")

/**
 * 把 Markdown 按空行拆成段落，用于把一条助手回复拆成多个气泡显示。
 *
 * 代码块、`$$`/`\[` 公式块里的空行不拆；空行后缩进的内容是上一个列表项的延续，并回上一段。
 * 每段内容原样保留。
 */
fun splitMarkdownParagraphs(text: String): List<String> {
    if (text.length <= MAX_DIFF_SOURCE_CHARS && looksLikeStandaloneUnifiedDiff(text)) {
        return listOf(text)
    }
    val chunks = mutableListOf<MutableList<String>>()
    var current: MutableList<String>? = null
    val fences = MarkdownFenceTracker()
    var mathEnd: String? = null

    for (line in text.lines()) {
        val trimmed = line.trim()
        when {
            mathEnd != null -> {
                if (trimmed == mathEnd) mathEnd = null
            }

            fences.contains(line) -> Unit

            trimmed.isEmpty() -> {
                current = null
                continue
            }

            else -> {
                when (trimmed) {
                    "$$" -> mathEnd = "$$"
                    "\\[" -> mathEnd = "\\]"
                }
            }
        }

        val chunk = current ?: run {
            val previous = chunks.lastOrNull()
            val continuesList = previous != null &&
                (line.startsWith(" ") || line.startsWith("\t")) &&
                previous.any { ListMarkerRegex.containsMatchIn(it) }
            if (continuesList) {
                previous.add("")
                previous
            } else {
                mutableListOf<String>().also { chunks.add(it) }
            }
        }
        chunk.add(line)
        current = chunk
    }
    return chunks.map { it.joinToString("\n") }
}
