package me.rerere.rikkahub.data.ai.subagent

/**
 * background 子代理完成后送回父会话的提示。
 *
 * 父会话正忙时结果先缓冲，[formatSubAgentResults] 把积攒的多条结果合并成一条消息，
 * 让父代理空闲后一次读完，而不是被逐条打断。
 *
 * 这条消息是系统通知，不是用户发言：[NOTICE_FRAMING] 只在组装请求时加到最前面，
 * 界面和导出看到的是 [formatSubAgentResults] 的正文。
 */
internal const val SUB_AGENT_NOTICE_FRAMING = "(This is an automatic system notice carrying results from sub-agent tasks, not something the user typed. Continue from it directly.)"

internal fun formatSubAgentResults(results: List<String>): String = buildString {
    append(results.size)
    append(" parallel sub-agent task(s) have finished; their results are below. Continue the current work from them and do not repeat what is already done.")
    results.forEachIndexed { index, result ->
        appendLine()
        appendLine()
        append("## ")
        append(index + 1)
        appendLine()
        append(result.trim())
    }
}
