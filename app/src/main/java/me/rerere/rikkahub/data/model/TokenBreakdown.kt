package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * Usage 看板里的 token 去向。输入侧的拆分按消息内容估算，总量始终取上游返回的 usage。
 */
enum class TokenDestination {
    SYSTEM,
    INPUT,
    HISTORY,
    TOOL,
    REASONING,
    OUTPUT,
}

data class TokenBucket(
    val destination: TokenDestination,
    val tokens: Int,
)

data class TokenBreakdown(
    val promptTotal: Int,
    val completionTotal: Int,
    val cachedTokens: Int,
    val promptBuckets: List<TokenBucket>,
    val completionBuckets: List<TokenBucket>,
) {
    val hasUsage: Boolean get() = promptTotal > 0 || completionTotal > 0
    val total: Int get() = promptTotal + completionTotal
}

/**
 * 把最近一次请求的 usage 拆成可读的去向。
 *
 * prompt 侧先用估算得到各内容的相对占比，再缩放到真实的 promptTokens；剩下的部分归入
 * 「系统 & 工具定义」，包括系统提示词、工具 schema 和协议开销。completion 侧只拆思考与正文。
 */
fun buildTokenBreakdown(messages: List<UIMessage>, usage: TokenUsage): TokenBreakdown {
    val promptTotal = usage.promptTokens.coerceAtLeast(0)
    val completionTotal = usage.completionTokens.coerceAtLeast(0)
    if (promptTotal == 0 && completionTotal == 0) {
        return TokenBreakdown(0, 0, 0, emptyList(), emptyList())
    }

    val estimates = linkedMapOf(
        TokenDestination.INPUT to 0,
        TokenDestination.HISTORY to 0,
        TokenDestination.TOOL to 0,
        TokenDestination.REASONING to 0,
    )
    messages.forEach { message ->
        if (message.isContextCheckpoint) {
            estimates[TokenDestination.SYSTEM] =
                (estimates[TokenDestination.SYSTEM] ?: 0) + estimateMessageTokens(message)
            return@forEach
        }
        message.parts.forEach { part ->
            when (part) {
                is UIMessagePart.Text -> {
                    val key = if (message.role == MessageRole.USER) {
                        TokenDestination.INPUT
                    } else {
                        TokenDestination.HISTORY
                    }
                    estimates[key] = (estimates[key] ?: 0) + estimatePartTokens(part)
                }

                is UIMessagePart.Reasoning ->
                    estimates[TokenDestination.REASONING] =
                        (estimates[TokenDestination.REASONING] ?: 0) + estimatePartTokens(part)

                is UIMessagePart.Tool,
                is UIMessagePart.ServerTool,
                is UIMessagePart.ToolCall,
                is UIMessagePart.ToolResult -> estimates[TokenDestination.TOOL] =
                    (estimates[TokenDestination.TOOL] ?: 0) + estimatePartTokens(part)

                is UIMessagePart.Image,
                is UIMessagePart.Video,
                is UIMessagePart.Audio,
                is UIMessagePart.Document -> estimates[TokenDestination.INPUT] =
                    (estimates[TokenDestination.INPUT] ?: 0) + estimatePartTokens(part)

                else -> Unit
            }
        }
    }

    val buckets = scaleBuckets(estimates, promptTotal)

    val lastAssistant = messages.lastOrNull { message ->
        message.role == MessageRole.ASSISTANT && message.usage != null
    } ?: messages.lastOrNull { message -> message.role == MessageRole.ASSISTANT }
    val reasoningEstimate = lastAssistant?.parts
        ?.filterIsInstance<UIMessagePart.Reasoning>()
        ?.sumOf(::estimatePartTokens)
        ?: 0
    val textEstimate = lastAssistant?.parts
        ?.filterIsInstance<UIMessagePart.Text>()
        ?.sumOf(::estimatePartTokens)
        ?: 0
    val completionBuckets = splitCompletion(completionTotal, reasoningEstimate, textEstimate)

    return TokenBreakdown(
        promptTotal = promptTotal,
        completionTotal = completionTotal,
        cachedTokens = usage.cachedTokens.coerceIn(0, promptTotal),
        promptBuckets = buckets,
        completionBuckets = completionBuckets,
    )
}

private fun scaleBuckets(
    estimates: Map<TokenDestination, Int>,
    total: Int,
): List<TokenBucket> {
    if (total <= 0) return emptyList()
    val positive = estimates.filterValues { it > 0 }
    val estimateSum = positive.values.sum()
    if (estimateSum <= 0) {
        return listOf(TokenBucket(TokenDestination.SYSTEM, total))
    }
    if (estimateSum <= total) {
        val scaled = positive.map { (destination, tokens) ->
            TokenBucket(destination, tokens)
        }
        val system = total - estimateSum
        return if (system > 0) {
            scaled + TokenBucket(TokenDestination.SYSTEM, system)
        } else {
            scaled
        }
    }

    var assigned = 0
    val scaled = positive.entries.mapIndexed { index, (destination, tokens) ->
        val value = if (index == positive.size - 1) {
            (total - assigned).coerceAtLeast(0)
        } else {
            (tokens.toLong() * total / estimateSum).toInt().coerceAtLeast(0)
        }
        assigned += value
        TokenBucket(destination, value)
    }
    return scaled.filter { it.tokens > 0 }
}

private fun splitCompletion(
    total: Int,
    reasoningEstimate: Int,
    textEstimate: Int,
): List<TokenBucket> {
    if (total <= 0) return emptyList()
    val estimateSum = reasoningEstimate + textEstimate
    if (estimateSum <= 0) {
        return listOf(TokenBucket(TokenDestination.OUTPUT, total))
    }
    val reasoning = (reasoningEstimate.toLong() * total / estimateSum).toInt().coerceIn(0, total)
    val output = total - reasoning
    return buildList {
        if (reasoning > 0) add(TokenBucket(TokenDestination.REASONING, reasoning))
        if (output > 0) add(TokenBucket(TokenDestination.OUTPUT, output))
    }
}
