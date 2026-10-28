package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.ai.ui.UIMessage
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
 *
 * 只统计最后一个压缩检查点之后的消息：检查点之前的内容已经由摘要代替，不会发给模型。
 * token 数按字符粗略估算（CJK 一字约一 token，其它约四字符一 token），
 * 只用来决定压缩范围，不要求精确。
 */
fun Conversation.keepRecentMessagesForTokens(targetTokens: Int): Int {
    if (targetTokens <= 0 || messageNodes.isEmpty()) return 0
    var used = 0
    var kept = 0
    for (node in messageNodes.asReversed()) {
        val message = node.messages.getOrNull(node.selectIndex) ?: continue
        // 到检查点为止：再往前的内容不在上下文里
        if (message.isContextCheckpoint) break
        val cost = estimateMessageTokens(message)
        if (kept > 0 && used + cost > targetTokens) break
        used += cost
        kept += 1
    }
    return kept
}

/** 压缩检查点之后的上下文 token 估算；压缩前的老消息已经不会再发出去，不计入 */
fun Conversation.estimateContextTokens(): Int {
    val checkpointIndex = messageNodes.indexOfLast { it.currentMessage.isContextCheckpoint }
    val nodes = if (checkpointIndex >= 0) messageNodes.drop(checkpointIndex) else messageNodes
    return nodes.sumOf { node ->
        node.messages.getOrNull(node.selectIndex)?.let { estimateMessageTokens(it) } ?: 0
    }
}

/** 单条消息的 token 估算，工具调用的输入输出也算在内 */
internal fun estimateMessageTokens(message: UIMessage): Int {
    var total = 4
    message.parts.forEach { part ->
        when (part) {
            is UIMessagePart.Text -> total += estimateTokens(part.text)
            is UIMessagePart.Tool -> {
                total += estimateTokens(part.toolName) + estimateTokens(part.input)
                part.output.filterIsInstance<UIMessagePart.Text>()
                    .forEach { total += estimateTokens(it.text) }
            }
            else -> Unit
        }
    }
    return total
}

internal fun estimateTokens(text: String): Int {
    if (text.isEmpty()) return 0
    var cjk = 0
    var other = 0
    for (ch in text) {
        if (ch.code >= 0x2E80) cjk++ else other++
    }
    return cjk + (other + 3) / 4
}
