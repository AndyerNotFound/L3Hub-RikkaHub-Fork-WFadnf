package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonElement

/** 「工具覆写」页里的工具来源分组，顺序即页面展示顺序。 */
enum class ToolCatalogGroup {
    LOCAL,
    MEMORY,
    SEARCH,
    CONVERSATION,
    WORKSPACE,
    TODO,
    SKILLS,
    MCP,
}

/**
 * 设置页展示用的工具定义快照。
 *
 * 只描述“默认长什么样”，不含执行入口；覆写内容单独从设置里取，
 * 因此这个目录不会因为某次对话注册了哪些工具而变化。
 */
data class ToolCatalogEntry(
    val name: String,
    val description: String,
    val parameters: JsonElement?,
    val instructions: String,
    val group: ToolCatalogGroup,
    /** MCP 服务器名这类二级说明 */
    val groupDetail: String? = null,
)
