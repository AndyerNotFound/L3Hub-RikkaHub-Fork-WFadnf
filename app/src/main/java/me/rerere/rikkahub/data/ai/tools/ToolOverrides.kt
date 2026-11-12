package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.Json
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.model.ToolOverride

/** 工具参数 schema 在设置页里的存法：格式化 JSON，缺省字段不写 null。 */
internal val ToolOverrideJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    prettyPrint = true
}

/** 解析用户写入的工具定义；格式不对时返回 null，调用方继续用默认定义。 */
fun decodeToolOverrideParameters(raw: String): InputSchema? =
    runCatching { ToolOverrideJson.decodeFromString(InputSchema.serializer(), raw) }.getOrNull()

fun encodeToolOverrideParameters(schema: InputSchema): String =
    ToolOverrideJson.encodeToString(InputSchema.serializer(), schema)

/**
 * 把用户覆写盖到本轮注册的工具上。
 *
 * 覆写按工具名匹配，内置工具、MCP 工具和动态生成的定义走同一条路；某一部分没有覆写时保留工具自己的默认值，
 * 覆写之后应用升级或 MCP 重新同步都不会再改到这一部分。
 */
fun applyToolOverrides(tools: List<Tool>, overrides: List<ToolOverride>): List<Tool> {
    if (overrides.isEmpty()) return tools
    val byName = overrides.associateBy { it.name }
    return tools.map { tool ->
        val override = byName[tool.name] ?: return@map tool
        val description = override.description
        val instructions = override.instructions
        val parameters = override.parameters?.let(::decodeToolOverrideParameters)
        tool.copy(
            description = description ?: tool.description,
            parameters = if (parameters != null) {
                { parameters }
            } else {
                tool.parameters
            },
            systemPrompt = if (instructions != null) {
                { _, _ -> instructions }
            } else {
                tool.systemPrompt
            },
        )
    }
}
