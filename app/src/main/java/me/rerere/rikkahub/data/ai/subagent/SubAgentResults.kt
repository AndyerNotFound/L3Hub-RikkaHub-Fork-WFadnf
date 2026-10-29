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
internal const val SUB_AGENT_NOTICE_FRAMING = "（这是系统自动回传的子代理任务结果，不是你输入的内容，直接据此继续即可）"

internal fun formatSubAgentResults(results: List<String>): String = buildString {
    append("以下 ")
    append(results.size)
    append(" 个后台子代理任务已完成，结果已回传。请据此继续当前工作，已经完成的部分不要重复执行。")
    results.forEachIndexed { index, result ->
        appendLine()
        appendLine()
        append("## ")
        append(index + 1)
        appendLine()
        append(result.trim())
    }
}
