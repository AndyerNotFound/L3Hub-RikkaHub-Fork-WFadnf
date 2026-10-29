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
import me.rerere.rikkahub.data.ai.subagent.SubAgentDispatchMode
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
            把一项自成一体的任务派发给子代理。子代理在独立上下文中执行，不占用当前对话的上下文。
            你必须根据任务自行选择派发模式：
            - wait：等待子代理完成并把最终结果直接作为工具结果返回；下一步需要结果才能继续时使用。
            - background：派发后立即返回，适合与当前主线无关、可以并行或耗时较长的任务；子代理完成后结果会自动投递回当前会话，不需要轮询。
            子代理完成后，不要把同一任务重复执行。
            任务描述必须自包含：背景、目标、约束和期望的输出格式；子代理看不到当前对话。
            可用子代理：
            $catalog
        """.trimIndent(),
        systemPrompt = { _, _ ->
            """
            ## 子代理
            当任务可以独立完成、需要多步检索或大量文件/终端操作、或与当前主线无关时，优先调用 dispatch_sub_agent 交给子代理，不要自己逐步硬做。
            派发模式由你决定：下一步必须依赖子代理结果时选择 wait；任务独立、可以并行或耗时较长时选择 background。
            background 的结果会在子代理完成后自动送回当前会话；如果当前会话还在忙，多条结果会合并后在你空闲时一次投递。
            适合派发：资料调研与对比、批量文件处理、独立代码修改、长文档阅读总结、可并行的子任务。
            不必派发：一句话问答、需要当前对话细节、需要用户确认或审批的操作。
            派发后不要重复执行同一个任务；background 的结果会自动送达，不要轮询，也不要在收到结果前假设它已经完成。
            """.trimIndent()
        },
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
                    put("mode", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("wait")
                            add("background")
                        })
                        put(
                            "description",
                            "wait：等待并返回最终结果；background：立即返回，不返回最终结果。默认 background。",
                        )
                    })
                },
                required = listOf("assistant", "task"),
            )
        },
        execute = { args ->
            val obj = args.jsonObject
            val target = obj["assistant"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val task = obj["task"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val mode = when (obj["mode"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()) {
                null, "" -> SubAgentDispatchMode.BACKGROUND
                "wait" -> SubAgentDispatchMode.WAIT
                "background" -> SubAgentDispatchMode.BACKGROUND
                else -> null
            }
            val result = when {
                mode == null -> "mode 只能是 wait 或 background"
                target.isEmpty() -> "缺少参数 assistant"
                task.isEmpty() -> "缺少参数 task"
                subAgents.none { it.name.equals(target, ignoreCase = true) || it.id.toString() == target } ->
                    "没有名为「$target」的子代理，可用：${subAgents.joinToString { it.name }}"
                else -> runner.dispatch(conversationId, target, task, mode)
            }
            listOf(UIMessagePart.Text(result))
        },
    )
}
