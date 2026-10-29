package me.rerere.rikkahub.data.ai.subagent

/**
 * background 子代理完成后送回父会话的提示。
 *
 * 父会话正忙时结果先缓冲，[formatSubAgentResults] 把积攒的多条结果合并成一条消息，
 * 让父代理空闲后一次读完，而不是被逐条打断。
 */
internal fun formatSubAgentResults(results: List<String>): String = buildString {
    append("有 ")
    append(results.size)
    append(" 个子代理任务已完成，结果如下。请据此继续当前工作，已经完成的部分不要重复执行。")
    results.forEachIndexed { index, result ->
        appendLine()
        appendLine()
        append("## ")
        append(index + 1)
        appendLine()
        append(result.trim())
    }
}
