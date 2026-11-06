package me.rerere.rikkahub.ui.pages.chat.terminal

import androidx.annotation.StringRes
import me.rerere.rikkahub.R

/** 终端里可用的斜杠命令。args 为命令名之后的原始文本。 */
data class TerminalCommand(
    val name: String,
    val args: String,
)

data class TerminalCommandSpec(
    val name: String,
    val usage: String,
    @StringRes val description: Int,
)

val TERMINAL_COMMANDS: List<TerminalCommandSpec> = listOf(
    TerminalCommandSpec("help", "/help", R.string.terminal_cmd_help_desc),
    TerminalCommandSpec("status", "/status", R.string.terminal_cmd_status_desc),
    TerminalCommandSpec("model", "/model [name]", R.string.terminal_cmd_model_desc),
    TerminalCommandSpec("new", "/new", R.string.terminal_cmd_new_desc),
    TerminalCommandSpec("messages", "/messages", R.string.terminal_cmd_messages_desc),
    TerminalCommandSpec("send", "/send <text>", R.string.terminal_cmd_send_desc),
    TerminalCommandSpec("retry", "/retry [index]", R.string.terminal_cmd_retry_desc),
    TerminalCommandSpec("edit", "/edit <index>", R.string.terminal_cmd_edit_desc),
    TerminalCommandSpec("delete", "/delete [index]", R.string.terminal_cmd_delete_desc),
    TerminalCommandSpec("copy", "/copy [index]", R.string.terminal_cmd_copy_desc),
    TerminalCommandSpec("branch", "/branch [index]", R.string.terminal_cmd_branch_desc),
    TerminalCommandSpec("compress", "/compress [request]", R.string.terminal_cmd_compress_desc),
    TerminalCommandSpec("usage", "/usage", R.string.terminal_cmd_usage_desc),
    TerminalCommandSpec("tree", "/tree", R.string.terminal_cmd_tree_desc),
    TerminalCommandSpec("files", "/files", R.string.terminal_cmd_files_desc),
    TerminalCommandSpec("voice", "/voice", R.string.terminal_cmd_voice_desc),
    TerminalCommandSpec("stop", "/stop", R.string.terminal_cmd_stop_desc),
    TerminalCommandSpec("title", "/title <title>", R.string.terminal_cmd_title_desc),
    TerminalCommandSpec("pin", "/pin", R.string.terminal_cmd_pin_desc),
    TerminalCommandSpec("search", "/search on|off", R.string.terminal_cmd_search_desc),
    TerminalCommandSpec("export", "/export", R.string.terminal_cmd_export_desc),
    TerminalCommandSpec("assistant", "/assistant [name]", R.string.terminal_cmd_assistant_desc),
    TerminalCommandSpec("chats", "/chats", R.string.terminal_cmd_chats_desc),
    TerminalCommandSpec("open", "/open <conversation ID>", R.string.terminal_cmd_open_desc),
    TerminalCommandSpec("favorite", "/favorite", R.string.terminal_cmd_favorite_desc),
    TerminalCommandSpec("close", "/close", R.string.terminal_cmd_close_desc),
    TerminalCommandSpec("fav", "/fav [index]", R.string.terminal_cmd_fav_desc),
    TerminalCommandSpec("switch", "/switch <branch index>", R.string.terminal_cmd_switch_desc),
    TerminalCommandSpec("translate", "/translate [index]", R.string.terminal_cmd_translate_desc),
    TerminalCommandSpec("mcp", "/mcp", R.string.terminal_cmd_mcp_desc),
    TerminalCommandSpec("workspace", "/workspace", R.string.terminal_cmd_workspace_desc),
    TerminalCommandSpec("save", "/save", R.string.terminal_cmd_save_desc),
    TerminalCommandSpec("settings", "/settings", R.string.terminal_cmd_settings_desc),
    TerminalCommandSpec("terminal", "/terminal on|off", R.string.terminal_cmd_terminal_desc),
    TerminalCommandSpec("clear", "/clear", R.string.terminal_cmd_clear_desc),
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

fun terminalHelpText(resolveText: (Int) -> String = { "" }): String = buildString {
    appendLine("L3HUB // COMMAND REFERENCE")
    appendLine("─".repeat(56))
    TERMINAL_COMMANDS.forEach { command ->
        append(command.usage.padEnd(22))
        append("  ")
        appendLine(resolveText(command.description))
    }
    appendLine("─".repeat(56))
    append(resolveText(R.string.terminal_help_index_note))
}
