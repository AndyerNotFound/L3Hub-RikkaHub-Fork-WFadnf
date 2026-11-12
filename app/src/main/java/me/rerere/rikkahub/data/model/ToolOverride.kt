package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable

/**
 * 用户手写的一份工具覆写，按工具名全局生效。
 *
 * 三个字段各自独立：为 null 表示这一部分继续跟随默认值（内置代码、MCP 同步结果或动态生成的内容），
 * 不为 null 时原样取代默认值，之后应用升级、MCP 重新同步都不会再改写它。
 */
@Serializable
data class ToolOverride(
    val name: String,
    /** 工具说明，对应 Tool.description */
    val description: String? = null,
    /** 追加到系统提示词的工具说明，对应 Tool.systemPrompt */
    val instructions: String? = null,
    /** 工具参数的 JSON Schema 文本，对应 Tool.parameters */
    val parameters: String? = null,
) {
    val isEmpty: Boolean
        get() = description == null && instructions == null && parameters == null
}
