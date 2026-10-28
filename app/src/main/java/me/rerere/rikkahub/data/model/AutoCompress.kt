package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.ai.ui.UIMessagePart

/** 自动压缩的触发点：上下文占用到达模型配置的 WARN 或 MAX 门限 */
@Serializable
enum class AutoCompressThreshold {
    WARN,
    MAX,
}

/** 自动压缩保留多少最近上下文：按消息条数或按 token 数 */
@Serializable
enum class AutoCompressTarget {
    MESSAGES,
    TOKENS,
}

/**
 * 从对话末尾往前数，保留 [targetTokens] 上下的最近消息，返回保留的消息条数。
 * token 数按字符粗略估算（CJK 一字约一 token，其它约四字符一 token），
 * 只用来决定压缩范围，不要求精确。
 */
fun Conversation.keepRecentMessagesForTokens(targetTokens: Int): Int {
    if (targetTokens <= 0 || messageNodes.isEmpty()) return 0
    var used = 0
    var kept = 0
    for (node in messageNodes.asReversed()) {
        val message = node.messages.getOrNull(node.selectIndex) ?: continue
        val cost = estimateMessageTokens(message)
        if (kept > 0 && used + cost > targetTokens) break
        used += cost
        kept += 1
    }
    return kept
}

private fun estimateMessageTokens(message: me.rerere.ai.ui.UIMessage): Int = estimateTokens(
    message.parts.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
)

internal fun estimateTokens(text: String): Int {
    if (text.isEmpty()) return 0
    var cjk = 0
    var other = 0
    for (ch in text) {
        if (ch.code >= 0x2E80) cjk++ else other++
    }
    return cjk + (other + 3) / 4
}
