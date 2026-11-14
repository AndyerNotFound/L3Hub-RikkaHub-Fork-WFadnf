package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import me.rerere.rikkahub.data.ai.subagent.DEFAULT_SUB_AGENT_WAIT_SECONDS
import me.rerere.rikkahub.data.ai.subagent.MAX_WAIT_TIMEOUT_SECONDS
import me.rerere.rikkahub.data.ai.subagent.MIN_WAIT_TIMEOUT_SECONDS
import me.rerere.rikkahub.data.ai.tools.local.JS_EXECUTION_TIMEOUT_MS
import me.rerere.rikkahub.data.ai.tools.local.SUB_AGENT_AWAIT_TOOL_NAME
import me.rerere.rikkahub.data.ai.tools.local.SUB_AGENT_TOOL_NAME
import me.rerere.workspace.WorkspaceManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val SHELL_TOOL_NAME = "workspace_shell"
private const val SHELL_OUTPUT_TOOL_NAME = "workspace_shell_output"
private const val JAVASCRIPT_TOOL_NAME = "eval_javascript"

/**
 * 这次工具调用自身的超时预算：到点会真正终止执行的时限。
 *
 * 只认工具定义里实际生效的时限（shell 命令的 timeout、跑测试的 timeout、子代理等待的 timeout_seconds 等），
 * 没有时限的工具返回 null，界面退回不定进度动画，而不是拿一个假想的值当分母。
 */
fun toolCallTimeout(toolName: String, arguments: JsonElement): Duration? {
    val args = arguments as? JsonObject ?: return null
    return when (toolName) {
        SHELL_TOOL_NAME -> {
            // 后台命令会立刻返回，入参里的 timeout 不生效
            if (args.isTrue("background") || args.isTrue("run_in_background")) return null
            args.secondsOrNull("timeout")
                ?.coerceIn(1L, SHELL_TIMEOUT_MAX_SECONDS)?.seconds
                ?: WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS.milliseconds
        }

        SHELL_OUTPUT_TOOL_NAME -> args.secondsOrNull("wait")
            ?.takeIf { it > 0 }
            ?.coerceIn(1L, SHELL_TIMEOUT_MAX_SECONDS)?.seconds

        WORKSPACE_TEST_TOOL_NAME -> args.secondsOrNull("timeout")
            ?.coerceIn(1L, TEST_TIMEOUT_MAX_SECONDS)?.seconds
            ?: TEST_TIMEOUT_DEFAULT_SECONDS.seconds

        JAVASCRIPT_TOOL_NAME -> JS_EXECUTION_TIMEOUT_MS.milliseconds

        SUB_AGENT_TOOL_NAME, SUB_AGENT_AWAIT_TOOL_NAME -> {
            // dispatch 只有 wait=true 才真的等，否则启动后立即返回
            if (toolName == SUB_AGENT_TOOL_NAME && !args.isTrue("wait")) return null
            args.secondsOrNull("timeout_seconds")
                ?.coerceIn(MIN_WAIT_TIMEOUT_SECONDS.toLong(), MAX_WAIT_TIMEOUT_SECONDS.toLong())
                ?.seconds
                ?: DEFAULT_SUB_AGENT_WAIT_SECONDS.toLong().seconds
        }

        else -> null
    }
}

private fun JsonObject.secondsOrNull(name: String): Long? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

private fun JsonObject.isTrue(name: String): Boolean =
    (this[name] as? JsonPrimitive)?.booleanOrNull == true
