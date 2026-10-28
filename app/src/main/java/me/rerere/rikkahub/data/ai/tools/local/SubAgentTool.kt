package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.subagent.SubAgentRunner
import me.rerere.rikkahub.data.model.Assistant
import kotlin.uuid.Uuid

const val SUB_AGENT_TOOL_NAME = "dispatch_sub_agent"

internal fun buildSubAgentTool(
    conversationId: Uuid,
    subAgents: List<Assistant>,
    runner: SubAgentRunner,
): Tool {
    val catalog = subAgents.joinToString(separator = "\n") { assistant ->
        val name = assistant.name.ifBlank { assistant.id.toString() }
        val intro = assistant.systemPrompt.lineSequence().firstOrNull()?.trim()?.take(60).orEmpty()
        if (intro.isEmpty()) "- $name" else "- $name：$intro"
    }

    return Tool(
        name = SUB_AGENT_TOOL_NAME,
        description = """
            把一项自成一体的任务派发给子代理，子代理在后台的独立上下文里执行，不会占用当前对话的上下文。
            派发后立即返回，不会阻塞当前回合；子代理完成后结果会在下一次请求模型时回传到当前会话。
            任务描述里要写清背景、目标、约束和期望的输出格式，子代理看不到当前对话。
            可用子代理：
            $catalog
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("assistant", buildJsonObject {
                        put("type", "string")
                        put("description", "子代理名称，必须是可用子代理列表里的名字")
                    })
                    put("task", buildJsonObject {
                        put("type", "string")
                        put("description", "要子代理完成的任务，自包含、可独立执行")
                    })
                },
                required = listOf("assistant", "task"),
            )
        },
        execute = { args ->
            val obj = args.jsonObject
            val target = obj["assistant"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val task = obj["task"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val result = when {
                target.isEmpty() -> "缺少参数 assistant"
                task.isEmpty() -> "缺少参数 task"
                subAgents.none { it.name.equals(target, ignoreCase = true) || it.id.toString() == target } ->
                    "没有名为「$target」的子代理，可用：${subAgents.joinToString { it.name }}"
                else -> runner.dispatch(conversationId, target, task)
            }
            listOf(UIMessagePart.Text(result))
        },
    )
}
