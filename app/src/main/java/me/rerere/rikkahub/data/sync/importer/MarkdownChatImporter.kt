package me.rerere.rikkahub.data.sync.importer

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * 从 Markdown 聊天记录里解析出一个会话。
 *
 * 兼容这几种角色标记（忽略大小写）：
 * ```
 * **User**: 内容            User: 内容            ## User
 * **Assistant**: 内容       Assistant: 内容       ## Assistant
 * ```
 * 代码块里的内容不会被当成角色标记；`# 标题` 作为会话标题。
 */
object MarkdownChatImporter {
    data class ParsedChat(
        val title: String,
        val messages: List<UIMessage>,
    )

    private const val ROLE_GROUP = """(user|assistant|human|ai|system|用户|助手|系统)"""

    private val roleLine = Regex(
        """^\s*(?:>\s*)?(?:#{1,6}\s*)?(?:\*\*|__)?\s*$ROLE_GROUP\s*(?:\*\*|__)?\s*[:：]\s*(.*)$""",
        RegexOption.IGNORE_CASE,
    )

    private val roleHeading = Regex(
        """^\s*(?:#{1,6})\s*$ROLE_GROUP\s*$""",
        RegexOption.IGNORE_CASE,
    )

    private val exportedAtLine = Regex("""^\s*[*_]Exported on .*[*_]\s*$""", RegexOption.IGNORE_CASE)

    fun parse(markdown: String): ParsedChat {
        var title = ""
        var role: MessageRole? = null
        var inFence = false
        val buffer = StringBuilder()
        val messages = mutableListOf<UIMessage>()

        fun flush() {
            val text = buffer.toString().trim()
            buffer.setLength(0)
            val currentRole = role ?: return
            if (text.isEmpty()) return
            messages.add(
                UIMessage(
                    role = currentRole,
                    parts = listOf(UIMessagePart.Text(text)),
                )
            )
        }

        markdown.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            val isFence = line.trimStart().startsWith("```")

            if (!inFence && title.isEmpty() && line.startsWith("# ")) {
                title = line.removePrefix("# ").trim()
                return@forEach
            }
            if (!inFence && role == null && exportedAtLine.matches(line)) {
                return@forEach
            }

            val marked = if (inFence) {
                null
            } else {
                roleLine.matchEntire(line)?.let { it.groupValues[1] to it.groupValues[2] }
                    ?: roleHeading.matchEntire(line)?.let { it.groupValues[1] to "" }
            }
            if (marked != null) {
                flush()
                role = roleOf(marked.first)
                if (marked.second.isNotBlank()) buffer.appendLine(marked.second)
                return@forEach
            }

            if (isFence) inFence = !inFence
            if (role != null) buffer.appendLine(line)
        }
        flush()

        return ParsedChat(title = title, messages = messages)
    }

    private fun roleOf(raw: String): MessageRole = when (raw.lowercase()) {
        "user", "human", "用户" -> MessageRole.USER
        "assistant", "ai", "助手" -> MessageRole.ASSISTANT
        else -> MessageRole.SYSTEM
    }
}
