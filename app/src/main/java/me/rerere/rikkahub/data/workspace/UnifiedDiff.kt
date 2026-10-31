package me.rerere.rikkahub.data.workspace

/**
 * 一个 hunk。lines 保留行首的 ' ' '+' '-'；
 * "\ No newline at end of file" 也原样放进 lines（不参与行数统计）。
 */
data class DiffHunk(
    val oldStart: Int,
    val oldCount: Int,
    val newStart: Int,
    val newCount: Int,
    val section: String,
    val lines: List<String>,
)

data class FileDiff(
    val oldPath: String?,
    val newPath: String?,
    val hunks: List<DiffHunk>,
    val isNewFile: Boolean,
    val isDeletedFile: Boolean,
    val isBinary: Boolean,
)

sealed interface ApplyOutcome {
    data class Success(
        val content: String,
        val added: Int,
        val removed: Int,
    ) : ApplyOutcome

    data class Conflict(
        val path: String?,
        val hunkIndex: Int,
        val line: Int,
        val reason: String,
    ) : ApplyOutcome
}

object UnifiedDiff {

    private const val SEARCH_WINDOW = 200

    private val HUNK_HEADER =
        Regex("""^@@[ \t]+-(\d+)(?:,(\d+))?[ \t]+\+(\d+)(?:,(\d+))?[ \t]*@@(.*)$""")

    /** 解析可能包含多个文件的 unified diff；无法识别的行跳过 */
    fun parse(text: String): List<FileDiff> {
        if (text.isBlank()) return emptyList()
        val lines = text.split('\n').map { it.removeSuffix("\r") }
        val result = ArrayList<FileDiff>()

        var oldPath: String? = null
        var newPath: String? = null
        var isNew = false
        var isDeleted = false
        var isBinary = false
        var started = false
        var hunks = ArrayList<DiffHunk>()

        fun reset() {
            oldPath = null
            newPath = null
            isNew = false
            isDeleted = false
            isBinary = false
            started = false
            hunks = ArrayList()
        }

        fun flush() {
            if (!started) return
            // 删除空文件时没有 hunk；只有路径已经落到 +++/dev/null 才算这一块结束，
            // 否则遇到 ---/+++ 头会先把同一个文件提前发出去
            val emptyDelete = isDeleted && hunks.isEmpty() && newPath == null && oldPath != null
            if (hunks.isNotEmpty() || isBinary || emptyDelete) {
                result += FileDiff(
                    oldPath = oldPath,
                    newPath = newPath,
                    hunks = hunks.toList(),
                    isNewFile = isNew || (oldPath == null && newPath != null),
                    isDeletedFile = isDeleted || (newPath == null && oldPath != null),
                    isBinary = isBinary,
                )
            }
            reset()
        }

        var index = 0
        var skipBinaryPayload = false
        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            // 代码块围栏
            if (trimmed.startsWith("```")) {
                index++
                continue
            }
            if (skipBinaryPayload) {
                if (line.startsWith("diff --git ")) skipBinaryPayload = false else {
                    index++
                    continue
                }
            }
            when {
                line.startsWith("diff --git ") -> {
                    flush()
                    val (old, new) = gitHeaderPaths(line)
                    oldPath = old
                    newPath = new
                    started = true
                    index++
                }

                line.startsWith("--- ") && index + 1 < lines.size && lines[index + 1].startsWith("+++ ") -> {
                    flush()
                    oldPath = cleanPath(line.removePrefix("--- "))
                    newPath = cleanPath(lines[index + 1].removePrefix("+++ "))
                    started = true
                    index += 2
                }

                line.startsWith("new file mode") -> {
                    isNew = true
                    started = true
                    index++
                }

                line.startsWith("deleted file mode") -> {
                    isDeleted = true
                    started = true
                    index++
                }

                line.startsWith("Binary files ") -> {
                    isBinary = true
                    started = true
                    index++
                }

                trimmed == "GIT binary patch" -> {
                    isBinary = true
                    started = true
                    skipBinaryPayload = true
                    index++
                }

                line.startsWith("@@") -> {
                    val header = HUNK_HEADER.find(line)
                    if (header == null) {
                        index++
                    } else {
                        started = true
                        val (hunk, next) = readHunk(lines, index, header)
                        hunks += hunk
                        index = next
                    }
                }

                else -> index++
            }
        }
        flush()
        return result
    }

    /** 把单文件 patch 应用到原文；任一 hunk 失败都返回 Conflict，不返回部分结果 */
    fun apply(original: String, file: FileDiff): ApplyOutcome {
        if (file.hunks.isEmpty()) return ApplyOutcome.Success(original, 0, 0)
        if (file.isDeletedFile) {
            val removed = file.hunks.sumOf { hunk -> hunk.lines.count { it.startsWith("-") } }
            return ApplyOutcome.Success("", 0, removed)
        }

        val path = file.oldPath ?: file.newPath
        val separator = if (original.contains("\r\n")) "\r\n" else "\n"
        val originalLines = splitLines(original)
        val trailingNewline = original.isEmpty() || original.endsWith("\n")

        val out = ArrayList<OutLine>()
        var cursor = 0
        var shift = 0
        var added = 0
        var removed = 0

        for ((hunkIndex, hunk) in file.hunks.withIndex()) {
            val body = hunk.lines
            val oldLines = body
                .filter { it.firstOrNull() == ' ' || it.firstOrNull() == '-' }
                .map { it.substring(1) }
            val declaredIndex = (hunk.oldStart - 1).coerceAtLeast(0)
            val expected = declaredIndex + shift

            val matchIndex: Int
            if (oldLines.isEmpty()) {
                // 纯新增：直接插入到老文本的 oldStart 之前
                matchIndex = expected.coerceIn(0, originalLines.size)
                if (matchIndex < cursor) return conflict(path, hunkIndex, hunk, "hunk overlaps previous hunk")
            } else {
                val limit = originalLines.size - oldLines.size
                if (limit < 0) return conflict(path, hunkIndex, hunk, "context not found")
                if (expected < cursor) return conflict(path, hunkIndex, hunk, "hunk overlaps previous hunk")
                matchIndex = if (expected <= limit && matchesAt(originalLines, oldLines, expected)) {
                    expected
                } else {
                    val from = (expected - SEARCH_WINDOW).coerceAtLeast(cursor)
                    val to = (expected + SEARCH_WINDOW).coerceAtMost(limit)
                    val found = ArrayList<Int>()
                    var pos = from
                    while (pos <= to) {
                        if (matchesAt(originalLines, oldLines, pos)) found += pos
                        pos++
                    }
                    when {
                        found.isEmpty() -> return conflict(path, hunkIndex, hunk, "context not found")
                        found.size > 1 -> return conflict(path, hunkIndex, hunk, "ambiguous context")
                        else -> found[0]
                    }
                }
            }

            // 先把 matchIndex 之前的原文原样搬过去
            while (cursor < matchIndex) {
                out += OutLine(originalLines[cursor], false, newlineAfter(cursor, originalLines.size, trailingNewline))
                cursor++
            }

            var oldCursor = matchIndex
            var bodyIndex = 0
            while (bodyIndex < body.size) {
                val line = body[bodyIndex]
                val prefix = line.firstOrNull() ?: ' '
                val noNewline = bodyIndex + 1 < body.size && body[bodyIndex + 1].startsWith("\\")
                when (prefix) {
                    ' ' -> if (oldCursor < originalLines.size) {
                        out += OutLine(
                            originalLines[oldCursor],
                            noNewline,
                            newlineAfter(oldCursor, originalLines.size, trailingNewline),
                        )
                        oldCursor++
                    }

                    '-' -> {
                        oldCursor++
                        removed++
                    }

                    '+' -> {
                        out += OutLine(line.substring(1), noNewline, null)
                        added++
                    }

                    else -> Unit
                }
                bodyIndex++
            }

            cursor = maxOf(cursor, oldCursor)
            shift = matchIndex - declaredIndex
        }

        // 补上最后一个 hunk 之后的原文
        while (cursor < originalLines.size) {
            out += OutLine(originalLines[cursor], false, newlineAfter(cursor, originalLines.size, trailingNewline))
            cursor++
        }

        if (out.isEmpty()) return ApplyOutcome.Success("", added, removed)
        val last = out.last()
        val keepTrailing = if (last.noNewline) false else last.originalNewline ?: true
        val content = buildString {
            for ((position, outLine) in out.withIndex()) {
                if (position > 0) append(separator)
                append(outLine.text)
            }
            if (keepTrailing) append(separator)
        }
        return ApplyOutcome.Success(content, added, removed)
    }

    /** 每个输出行：文本 + patch 里的无换行标记 + 该行在原文里是否带结尾换行 */
    private data class OutLine(
        val text: String,
        val noNewline: Boolean,
        val originalNewline: Boolean?,
    )

    private fun conflict(path: String?, hunkIndex: Int, hunk: DiffHunk, reason: String) =
        ApplyOutcome.Conflict(path, hunkIndex, hunk.oldStart.coerceAtLeast(1), reason)

    private fun newlineAfter(index: Int, size: Int, trailing: Boolean): Boolean =
        index < size - 1 || trailing

    private fun splitLines(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val body = if (text.endsWith("\n")) text.dropLast(1) else text
        return body.split('\n').map { it.removeSuffix("\r") }
    }

    /** 逐行比较，忽略行尾空白 */
    private fun matchesAt(original: List<String>, pattern: List<String>, at: Int): Boolean {
        if (at < 0 || at + pattern.size > original.size) return false
        for (offset in pattern.indices) {
            if (original[at + offset].trimEnd() != pattern[offset].trimEnd()) return false
        }
        return true
    }

    private fun readHunk(lines: List<String>, headerIndex: Int, header: MatchResult): Pair<DiffHunk, Int> {
        val values = header.groupValues
        val oldStart = values[1].toIntOrNull() ?: 0
        val oldCount = values[2].ifEmpty { "1" }.toIntOrNull() ?: 1
        val newStart = values[3].toIntOrNull() ?: 0
        val newCount = values[4].ifEmpty { "1" }.toIntOrNull() ?: 1
        val section = values[5].trim()

        val body = ArrayList<String>()
        var index = headerIndex + 1
        while (index < lines.size) {
            if (isHunkEnd(lines, index)) break
            val line = lines[index]
            body += if (line.isEmpty()) " " else line
            index++
        }
        // 补丁末尾的空行不当作上下文
        while (body.isNotEmpty() && body.last() == " ") body.removeAt(body.size - 1)
        return DiffHunk(oldStart, oldCount, newStart, newCount, section, body) to index
    }

    private fun isHunkEnd(lines: List<String>, index: Int): Boolean {
        val line = lines[index]
        if (line.startsWith("@@")) return true
        if (line.startsWith("diff --git ")) return true
        if (line.isEmpty()) return false
        return when (line[0]) {
            ' ', '+', '\\' -> false
            '-' -> {
                if (!line.startsWith("--- ")) false
                else if (index + 1 >= lines.size || !lines[index + 1].startsWith("+++ ")) false
                else index + 2 >= lines.size || looksLikeFileHeader(lines[index + 2])
            }

            else -> true
        }
    }

    private fun looksLikeFileHeader(line: String): Boolean =
        line.startsWith("@@") ||
            line.startsWith("diff --git ") ||
            line.startsWith("index ") ||
            line.startsWith("new file mode") ||
            line.startsWith("deleted file mode") ||
            line.startsWith("old mode") ||
            line.startsWith("new mode") ||
            line.startsWith("GIT binary patch") ||
            line.startsWith("Binary files ")

    private fun gitHeaderPaths(line: String): Pair<String?, String?> {
        val rest = line.removePrefix("diff --git ").trim()
        val split = rest.indexOf(" b/")
        if (split > 0) return cleanPath(rest.substring(0, split)) to cleanPath(rest.substring(split + 1))
        val parts = rest.split(' ').filter { it.isNotEmpty() }
        return when {
            parts.size >= 2 -> cleanPath(parts[0]) to cleanPath(parts[1])
            parts.size == 1 -> cleanPath(parts[0]) to cleanPath(parts[0])
            else -> null to null
        }
    }

    /** 去掉 a/ b/ 前缀与时间戳；/dev/null 返回 null */
    private fun cleanPath(raw: String): String? {
        var path = raw.trim()
        if (path.isEmpty()) return null
        if (path.contains('\t')) path = path.substringBefore('\t').trim()
        if (path == "/dev/null") return null
        if (path.startsWith("\"") && path.endsWith("\"") && path.length > 1) {
            path = path.substring(1, path.length - 1)
        }
        if (path.startsWith("a/") || path.startsWith("b/")) path = path.substring(2)
        if (path.startsWith("./")) path = path.substring(2)
        return path.ifEmpty { null }
    }
}
