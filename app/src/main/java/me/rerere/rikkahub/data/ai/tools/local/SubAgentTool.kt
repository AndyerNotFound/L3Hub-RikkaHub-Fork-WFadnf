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
            Dispatch a self-contained task to a sub-agent. The sub-agent runs in its own isolated context and does not consume the current conversation's context.
            You must choose the dispatch mode yourself:
            - wait: block until the sub-agent finishes and return its final result as the tool result. Use it when the next step depends on the result.
            - background: return immediately. Use it for tasks unrelated to the current thread, tasks that can run in parallel, or long-running work. The result is delivered back to this conversation automatically when the sub-agent finishes, no polling needed.
            Do not repeat the same task after a sub-agent has completed it.
            The task description must be self-contained: background, goal, constraints, and the expected output format. The sub-agent cannot see this conversation.
            Available sub-agents:
            $catalog
        """.trimIndent(),
        systemPrompt = { _, _ ->
            """
            ## Sub-agents
            When a task can be finished independently, needs multi-step research or heavy file/terminal work, or is unrelated to the current thread, prefer calling dispatch_sub_agent instead of doing everything step by step yourself.
            Pick the dispatch mode yourself: use wait when the next step depends on the sub-agent's result; use background when the task is independent, can run in parallel, or may take a long time.
            background results are delivered back to this conversation automatically when the sub-agent finishes; if this conversation is still busy, multiple results are merged and delivered once when you are idle.
            Good candidates: research and comparison, batch file processing, independent code changes, summarizing long documents, and subtasks that can run in parallel.
            Not worth dispatching: one-line questions, tasks that need details of this conversation, or anything that needs the user's confirmation or approval.
            Do not repeat a task after dispatching it; background results arrive automatically, so do not poll and do not assume a result is ready before it arrives.
            """.trimIndent()
        },
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("assistant", buildJsonObject {
                        put("type", "string")
                        put("description", "Sub-agent name. Must be one of the names in the available sub-agent list.")
                    })
                    put("task", buildJsonObject {
                        put("type", "string")
                        put("description", "The task for the sub-agent. Must be self-contained and independently executable.")
                    })
                    put("mode", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("wait")
                            add("background")
                        })
                        put(
                            "description",
                            "wait: wait and return the final result; background: return immediately without the final result. Defaults to background.",
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
