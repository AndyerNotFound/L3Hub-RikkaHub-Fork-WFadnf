package me.rerere.rikkahub.data.model

/**
 * 上下文占用相对 MAX 门限的比例, 裁剪到 0~1。
 * 门限未设置或非法时返回 0。
 */
fun contextUsageFraction(used: Int, limit: Int?): Float {
    if (limit == null || limit <= 0) return 0f
    return (used.toFloat() / limit).coerceIn(0f, 1f)
}

/**
 * 到达 MAX 门限后不再允许发送消息。
 * 没有设置 MAX([maxTokens] 为 null)时永远不拦截。
 */
fun isContextLimitReached(tokens: Int?, maxTokens: Int?): Boolean {
    if (maxTokens == null || maxTokens <= 0) return false
    val used = tokens ?: return false
    return used >= maxTokens
}
