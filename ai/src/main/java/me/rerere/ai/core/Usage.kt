package me.rerere.ai.core

import kotlinx.serialization.Serializable

@Serializable
data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    // 缓存命中 / 缓存读取的 token，已包含在 promptTokens 里
    val cachedTokens: Int = 0,
    val totalTokens: Int = 0,
    // 写入缓存的 token（Anthropic 的 cache_creation_input_tokens），也已包含在 promptTokens 里
    val cacheWriteTokens: Int = 0,
)

/**
 * 合并同一次请求内的 usage 快照：各字段取最新的非零值。
 * 跨请求（如多轮工具调用的各个步骤）累计请用 [sum]。
 */
fun TokenUsage?.merge(other: TokenUsage): TokenUsage {
    val promptTokens = if (other.promptTokens > 0) {
        other.promptTokens
    } else {
        this?.promptTokens ?: 0
    }
    val completionTokens = if (other.completionTokens > 0) {
        other.completionTokens
    } else {
        this?.completionTokens ?: 0
    }
    val totalTokens = promptTokens + completionTokens
    val cachedTokens = if (other.cachedTokens > 0) {
        other.cachedTokens
    } else {
        this?.cachedTokens ?: 0
    }
    val cacheWriteTokens = if (other.cacheWriteTokens > 0) {
        other.cacheWriteTokens
    } else {
        this?.cacheWriteTokens ?: 0
    }
    return TokenUsage(
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalTokens = totalTokens,
        cachedTokens = cachedTokens,
        cacheWriteTokens = cacheWriteTokens
    )
}

/**
 * 累加多次请求的 usage，每次请求都会按完整上下文计费，因此 prompt 也直接相加。
 */
fun TokenUsage?.sum(other: TokenUsage?): TokenUsage? {
    if (this == null) return other
    if (other == null) return this
    return TokenUsage(
        promptTokens = promptTokens + other.promptTokens,
        completionTokens = completionTokens + other.completionTokens,
        cachedTokens = cachedTokens + other.cachedTokens,
        cacheWriteTokens = cacheWriteTokens + other.cacheWriteTokens,
        totalTokens = totalTokens + other.totalTokens,
    )
}
