package me.rerere.rikkahub.service

import kotlin.uuid.Uuid

// 只收能指向「上下文/输入超限」的说法。像 "payload too large" 和单独的「令牌数」这种
// 更像网关的请求体大小限制或无关的 token 报错，匹配上会白跑一次付费压缩再失败。
private val CONTEXT_OVERFLOW_HINTS = listOf(
    "context_length_exceeded",
    "maximum context length",
    "context window",
    "context length",
    "prompt is too long",
    "input is too long",
    "too many tokens",
    "exceeds the maximum number of tokens",
    "reduce the length of the messages",
    "context limit",
    "maximum context",
    "token limit",
    "context length exceeded",
    "input tokens exceed",
    "request too large",
    "exceeds the context window",
    "maximum number of tokens",
    "input length",
    "上下文长度",
    "上下文窗口",
    "超出上下文",
    "超过上下文",
    "输入过长",
    "请求过长",
    "提示词过长",
)

data class ChatError(
    val id: Uuid = Uuid.random(),
    val title: String? = null,
    val error: Throwable,
    val conversationId: Uuid? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val solution: ChatErrorSolution? = null,
)

enum class ChatErrorSolution {
    CheckFastModelSettings,
}

/** 错误文案里没有列出的超限表达（常见于中转网关）再用一个宽松的语义匹配兜底 */
internal fun isContextOverflowMessage(message: String): Boolean {
    val text = message.lowercase()
    if (CONTEXT_OVERFLOW_HINTS.any { it in text }) return true
    return Regex(
        """(?:maximum|max|exceed(?:ed)?).{0,40}(?:context|prompt|input|token).{0,40}(?:length|window|token)""",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(text)
}
