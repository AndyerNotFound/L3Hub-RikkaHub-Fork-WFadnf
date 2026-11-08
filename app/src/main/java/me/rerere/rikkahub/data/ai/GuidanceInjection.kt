package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.buildTodoPrompt
import me.rerere.rikkahub.data.model.Guidance
import me.rerere.rikkahub.data.model.TodoItem
import kotlin.uuid.Uuid

/**
 * 引导和清单注入用的合成工具名。
 *
 * 它们不进工具表：模型调不到，历史里却是一条标准的 tool_use + tool_result。
 * 各家 provider 转换时只按已执行的 Tool part 分组序列化，不校验工具名是否声明过，
 * 所以不需要为它们常驻声明一个工具——声明反而会诱导模型真的去调用。
 */
const val GUIDANCE_TOOL_NAME = "guidance"
const val TODO_REMINDER_TOOL_NAME = "todo_reminder"

/**
 * 把一条引导包成工具调用。
 *
 * 每次插入都用新的 toolCallId：同一条助手消息里出现两个相同 id 的 tool_use，
 * Anthropic 会直接拒绝整个请求。多轮模式每一步都会插一条，不能复用 id。
 */
fun guidanceToolPart(guidance: Guidance): UIMessagePart.Tool = UIMessagePart.Tool(
    toolCallId = shortToolCallId("g_"),
    toolName = GUIDANCE_TOOL_NAME,
    input = "{}",
    output = listOf(UIMessagePart.Text(guidance.content)),
)

/** 把当前清单包成工具调用；没有清单时返回 null */
fun todoReminderToolPart(todos: List<TodoItem>): UIMessagePart.Tool? {
    if (todos.isEmpty()) return null
    return UIMessagePart.Tool(
        toolCallId = shortToolCallId("t_"),
        toolName = TODO_REMINDER_TOOL_NAME,
        input = "{}",
        output = listOf(UIMessagePart.Text(buildTodoPrompt(todos))),
    )
}

/**
 * 合成调用的 id。
 *
 * OpenAI 的 tool_call_id 上限 40 字符，完整 uuid 加上前缀会到 45，整条请求会被拒；
 * 这里取 16 位十六进制，一个回合里的几条注入足够不重复。
 */
private fun shortToolCallId(prefix: String): String =
    prefix + Uuid.random().toString().replace("-", "").take(16)

/** 应用注入的合成调用（引导或清单） */
fun UIMessagePart.isInjectedTool(): Boolean =
    this is UIMessagePart.Tool && (toolName == GUIDANCE_TOOL_NAME || toolName == TODO_REMINDER_TOOL_NAME)

/** 这条消息里有没有注入过清单 */
fun UIMessage.hasTodoReminder(): Boolean =
    parts.any { it is UIMessagePart.Tool && it.toolName == TODO_REMINDER_TOOL_NAME }

/** 这条消息里最后一次注入的清单文本，用来判断清单有没有变过 */
fun UIMessage.lastTodoReminderText(): String? = parts
    .filterIsInstance<UIMessagePart.Tool>()
    .lastOrNull { it.toolName == TODO_REMINDER_TOOL_NAME }
    ?.todoReminderText()

/** 引导的正文；模型自己幻觉出来的同名调用（没执行、没输出）不算，免得吞掉工具卡片 */
fun UIMessagePart.Tool.guidanceText(): String? =
    if (toolName == GUIDANCE_TOOL_NAME && isExecuted) {
        output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }.ifBlank { null }
    } else {
        null
    }

/** 清单提醒的正文；同样只认应用注入的那一种 */
fun UIMessagePart.Tool.todoReminderText(): String? =
    if (toolName == TODO_REMINDER_TOOL_NAME && isExecuted) {
        output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }.ifBlank { null }
    } else {
        null
    }
