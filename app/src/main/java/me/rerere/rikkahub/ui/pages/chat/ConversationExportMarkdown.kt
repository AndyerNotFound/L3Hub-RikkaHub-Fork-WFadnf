package me.rerere.rikkahub.ui.pages.chat

import java.time.LocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.encodeBase64
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.toLocalString

// ConversationExport.kt 的区域文件：Markdown 导出的正文拼装。
// 除 private → internal（供同包源文件调用）外，函数体逐字节照搬。
internal fun buildMarkdownExport(
    conversation: Conversation,
    messages: List<UIMessage>,
    options: ExportOptions,
): String = buildString {
    append("# ${conversation.title}\n\n")
    append("*Exported on ${LocalDateTime.now().toLocalString()}*\n\n")

    messages.filterNot { it.isNoticeOnly }.forEach { message ->
        val role = if (message.role == MessageRole.USER) "**User**" else "**Assistant**"
        append("$role:\n\n")
        message.parts.forEach { part ->
            when (part) {
                is UIMessagePart.Text -> {
                    append(part.text)
                    appendLine()
                }

                is UIMessagePart.Image -> {
                    append("![Image](${part.encodeBase64().getOrNull()?.base64})")
                    appendLine()
                }

                is UIMessagePart.Reasoning -> {
                    if (!options.includeReasoning) return@forEach
                    part.reasoning.lines()
                        .filter { it.isNotBlank() }
                        .forEach {
                            append("> $it")
                            appendLine()
                        }
                    appendLine()
                }

                is UIMessagePart.Tool -> {
                    append("**Tool**: `${part.toolName}`")
                    appendLine()
                    if (part.toolCallId.isNotBlank()) {
                        append("- Call ID: `${part.toolCallId}`")
                        appendLine()
                    }

                    append("Input:")
                    appendLine()
                    append("```json")
                    appendLine()
                    append(JsonInstantPretty.encodeToString(part.inputAsJson()))
                    appendLine()
                    append("```")
                    appendLine()

                    if (part.output.isNotEmpty()) {
                        append("Output:")
                        appendLine()
                        part.output.forEach { outputPart ->
                            when (outputPart) {
                                is UIMessagePart.Text -> {
                                    append("```text")
                                    appendLine()
                                    append(outputPart.text)
                                    appendLine()
                                    append("```")
                                    appendLine()
                                }

                                is UIMessagePart.Reasoning -> {
                                    outputPart.reasoning.lines()
                                        .filter { it.isNotBlank() }
                                        .forEach {
                                            append("> $it")
                                            appendLine()
                                        }
                                }

                                is UIMessagePart.Image -> {
                                    append("![Tool Image](${outputPart.encodeBase64().getOrNull()?.base64})")
                                    appendLine()
                                }

                                is UIMessagePart.Document -> {
                                    append("[Document: ${outputPart.fileName}](${outputPart.url})")
                                    appendLine()
                                }

                                is UIMessagePart.Video -> {
                                    append("[Video](${outputPart.url})")
                                    appendLine()
                                }

                                is UIMessagePart.Audio -> {
                                    append("[Audio](${outputPart.url})")
                                    appendLine()
                                }

                                else -> {}
                            }
                        }
                    }
                    appendLine()
                }

                else -> {}
            }
        }
        appendLine()
        append("---")
        appendLine()
    }
}
