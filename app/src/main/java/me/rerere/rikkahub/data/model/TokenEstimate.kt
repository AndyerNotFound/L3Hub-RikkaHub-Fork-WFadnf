package me.rerere.rikkahub.data.model

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * 按字符粗略估算 token：CJK 按 1 字 1 token，其余按 4 字符 1 token。
 *
 * 只用于界面上的用量拆分和压缩请求的 max_tokens 预算，不参与自动压缩的触发判断。
 */
internal fun estimateTokens(text: String): Int {
    if (text.isEmpty()) return 0
    var cjk = 0
    var other = 0
    for (ch in text) {
        if (ch.code >= 0x2E80) cjk++ else other++
    }
    return cjk + (other + 3) / 4
}

private const val MESSAGE_TOKEN_OVERHEAD = 4

internal fun estimateMessageTokens(message: UIMessage): Int =
    MESSAGE_TOKEN_OVERHEAD + message.parts.sumOf(::estimatePartTokens)

internal fun estimatePartTokens(part: UIMessagePart): Int = when (part) {
    is UIMessagePart.Text -> estimateTokens(part.text)
    is UIMessagePart.Reasoning -> estimateTokens(part.reasoning)
    is UIMessagePart.Tool ->
        estimateTokens(part.toolName) +
            estimateTokens(part.input) +
            part.output.filterIsInstance<UIMessagePart.Text>().sumOf { estimateTokens(it.text) }

    is UIMessagePart.ServerTool ->
        estimateTokens(part.toolName) +
            estimateTokens(part.input?.toString().orEmpty()) +
            estimateTokens(part.output?.toString().orEmpty())

    is UIMessagePart.ToolCall -> estimateTokens(part.toolName) + estimateTokens(part.arguments)

    is UIMessagePart.ToolResult ->
        estimateTokens(part.toolName) +
            estimateTokens(part.content.toString()) +
            estimateTokens(part.arguments.toString())

    is UIMessagePart.Document -> 512 + estimateTokens(part.fileName)
    is UIMessagePart.Image, is UIMessagePart.Video, is UIMessagePart.Audio -> 768
    else -> 0
}
