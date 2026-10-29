package me.rerere.rikkahub.ui.pages.chat.terminal

/** 终端里可用的斜杠命令。args 为命令名之后的原始文本。 */
data class TerminalCommand(
    val name: String,
    val args: String,
)

data class TerminalCommandSpec(
    val name: String,
    val usage: String,
    val description: String,
)

val TERMINAL_COMMANDS: List<TerminalCommandSpec> = listOf(
    TerminalCommandSpec("help", "/help", "显示命令列表"),
    TerminalCommandSpec("status", "/status", "显示会话、模型与上下文状态"),
    TerminalCommandSpec("model", "/model [名称]", "切换模型；不带参数时打开模型列表"),
    TerminalCommandSpec("new", "/new", "新建会话"),
    TerminalCommandSpec("messages", "/messages", "列出当前消息与编号"),
    TerminalCommandSpec("send", "/send <文本>", "直接发送一条消息"),
    TerminalCommandSpec("retry", "/retry [编号]", "重新生成指定或最后一条回复"),
    TerminalCommandSpec("edit", "/edit <编号>", "把指定消息载入输入框编辑"),
    TerminalCommandSpec("delete", "/delete [编号]", "删除指定或最后一条消息"),
    TerminalCommandSpec("copy", "/copy [编号]", "复制指定或最后一条消息"),
    TerminalCommandSpec("branch", "/branch [编号]", "从指定或最后一条消息创建分支"),
    TerminalCommandSpec("compress", "/compress [要求]", "压缩上下文，保留最近 0 条消息"),
    TerminalCommandSpec("usage", "/usage", "打开 Usage 看板"),
    TerminalCommandSpec("tree", "/tree", "打开子代理任务树"),
    TerminalCommandSpec("files", "/files", "打开附件与工作区面板"),
    TerminalCommandSpec("voice", "/voice", "进入语音输入"),
    TerminalCommandSpec("stop", "/stop", "停止当前生成"),
    TerminalCommandSpec("title", "/title <标题>", "重命名当前会话"),
    TerminalCommandSpec("pin", "/pin", "置顶或取消置顶当前会话"),
    TerminalCommandSpec("search", "/search on|off", "开关联网搜索"),
    TerminalCommandSpec("export", "/export", "复制整段会话为 Markdown"),
    TerminalCommandSpec("assistant", "/assistant [名称]", "列出或切换助手"),
    TerminalCommandSpec("chats", "/chats", "打开会话历史"),
    TerminalCommandSpec("open", "/open <会话ID>", "打开指定会话"),
    TerminalCommandSpec("favorite", "/favorite", "打开收藏夹"),
    TerminalCommandSpec("close", "/close", "删除当前会话并返回"),
    TerminalCommandSpec("fav", "/fav [编号]", "收藏或取消收藏消息"),
    TerminalCommandSpec("switch", "/switch <分支号>", "切换消息分支"),
    TerminalCommandSpec("translate", "/translate [编号]", "翻译指定或最后一条回复"),
    TerminalCommandSpec("mcp", "/mcp", "打开 MCP 设置"),
    TerminalCommandSpec("workspace", "/workspace", "打开工作区与附件面板"),
    TerminalCommandSpec("save", "/save", "把会话导出为 Markdown 文件"),
    TerminalCommandSpec("settings", "/settings", "打开设置"),
    TerminalCommandSpec("terminal", "/terminal on|off", "开关终端界面"),
    TerminalCommandSpec("clear", "/clear", "清空输入框"),
)

fun parseTerminalCommand(input: String): TerminalCommand? {
    val trimmed = input.trim()
    if (!trimmed.startsWith("/") || trimmed.length < 2) return null
    val body = trimmed.drop(1)
    val name = body.substringBefore(' ').lowercase()
    if (name.isBlank()) return null
    val args = body.substringAfter(' ', "").trim()
    return TerminalCommand(name, args)
}

/**
 * 输入 `/` 或 `/he` 时给出补全候选；已经输入参数后不再提示。
 */
fun terminalCommandSuggestions(input: String, limit: Int = 6): List<TerminalCommandSpec> {
    val trimmed = input.trimStart()
    if (!trimmed.startsWith("/") || trimmed.contains(' ')) return emptyList()
    val query = trimmed.drop(1).lowercase()
    return TERMINAL_COMMANDS
        .filter { it.name.startsWith(query) }
        .take(limit)
}

fun terminalHelpText(): String = buildString {
    appendLine("L3HUB // COMMAND REFERENCE")
    appendLine("─".repeat(56))
    TERMINAL_COMMANDS.forEach { command ->
        append(command.usage.padEnd(22))
        append("  ")
        appendLine(command.description)
    }
    appendLine("─".repeat(56))
    append("消息编号是每行开头的 [00]；不带编号时默认最后一条。")
}
