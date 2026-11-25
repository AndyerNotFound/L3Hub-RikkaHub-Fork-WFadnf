package me.rerere.rikkahub.ui.components.richtext

private val FenceListMarker = Regex("""(?:[-+*]|[0-9]{1,9}[.)])[ \t]+""")

/** Tracks ordinary fences plus fences opened directly under a quote or list marker. */
internal class MarkdownFenceTracker {
    private data class Fence(val marker: Char, val length: Int, val quoteDepth: Int, val indent: Int)

    private var open: Fence? = null

    fun contains(line: String): Boolean {
        var quoteDepth = 0
        var start = 0
        val existing = open
        // Further quote markers inside an open fence are code, not new containers.
        while (start < line.length && (existing == null || quoteDepth < existing.quoteDepth)) {
            val marker = line.afterIndent(start)
            if (line.getOrNull(marker) != '>') break
            quoteDepth++
            start = marker + 1
            if (line.getOrNull(start) == ' ' || line.getOrNull(start) == '\t') start++
        }
        var marker = line.afterIndent(start)
        if (existing != null) {
            val inside = quoteDepth == existing.quoteDepth &&
                (existing.indent == 0 || line.substring(start).isBlank() ||
                    (start until start + existing.indent).all { line.getOrNull(it) == ' ' })
            if (inside) {
                marker = line.afterIndent(start + existing.indent)
                if (line.getOrNull(marker) == existing.marker) {
                    val end = line.afterRun(marker, existing.marker)
                    if (end - marker >= existing.length && line.substring(end).isBlank()) open = null
                }
                return true
            }
            open = null
            return contains(line)
        }

        val list = FenceListMarker.matchAt(line, marker)
        val indent = if (list == null) 0 else list.range.last + 1 - start
        if (list != null) marker = start + indent
        val char = line.getOrNull(marker)?.takeIf { it == '`' || it == '~' } ?: return false
        val end = line.afterRun(marker, char)
        if (end - marker < 3 || (char == '`' && line.indexOf('`', end) >= 0)) return false
        open = Fence(char, end - marker, quoteDepth, indent)
        return true
    }

    private fun String.afterIndent(start: Int): Int {
        var end = start
        while (end - start < 3 && getOrNull(end) == ' ') end++
        return end
    }

    private fun String.afterRun(start: Int, char: Char): Int {
        var end = start
        while (getOrNull(end) == char) end++
        return end
    }
}
