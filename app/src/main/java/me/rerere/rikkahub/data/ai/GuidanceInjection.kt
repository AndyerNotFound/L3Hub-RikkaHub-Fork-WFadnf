package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import me.rerere.ai.core.Tool
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

// 注入的调用 id 前缀。模型照着历史误调同名工具时，provider 给的是它自己的 id，
// 没有这个前缀，据此还分得清「应用插的」和「模型自己调的」
private const val GUIDANCE_ID_PREFIX = "g_"
private const val TODO_ID_PREFIX = "t_"

/**
 * 把一条引导包成工具调用。
 *
 * 每次插入都用新的 toolCallId：同一条助手消息里出现两个相同 id 的 tool_use，
 * Anthropic 会直接拒绝整个请求。多轮模式插入多次，不能复用 id。
 * [step] 是插入时的步号，间隔靠它计算，见 [stepsSinceLastGuidance]。
 */
fun guidanceToolPart(guidance: Guidance, step: Int): UIMessagePart.Tool = UIMessagePart.Tool(
    toolCallId = shortToolCallId(GUIDANCE_ID_PREFIX),
    toolName = GUIDANCE_TOOL_NAME,
    // 记下这是第几步插的，间隔按步号算；不能用「相邻 part」推批次，
    // 连续两步都只吐工具调用、中间没有文本时，两个批次会挤成一段
    input = """{"step":$step}""",
    output = listOf(UIMessagePart.Text(guidance.content)),
)

/** 把当前清单包成工具调用；没有清单时返回 null */
fun todoReminderToolPart(todos: List<TodoItem>, step: Int): UIMessagePart.Tool? {
    if (todos.isEmpty()) return null
    return UIMessagePart.Tool(
        toolCallId = shortToolCallId(TODO_ID_PREFIX),
        toolName = TODO_REMINDER_TOOL_NAME,
        input = """{"step":$step}""",
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

/** 应用注入的合成调用（引导或清单）：名字对得上，id 也得是注入时给的那个前缀 */
fun UIMessagePart.isInjectedTool(): Boolean {
    val tool = this as? UIMessagePart.Tool ?: return false
    return when (tool.toolName) {
        GUIDANCE_TOOL_NAME -> tool.toolCallId.startsWith(GUIDANCE_ID_PREFIX)
        TODO_REMINDER_TOOL_NAME -> tool.toolCallId.startsWith(TODO_ID_PREFIX)
        else -> false
    }
}

/**
 * 模型照着历史误调合成调用时的兜底。
 *
 * 只参与执行、不声明给模型（[me.rerere.ai.agent.AgentRequest.executorOnlyTools]），
 * 所以不会诱导它去调；真调了也只是拿到一句说明。
 */
fun injectedToolFallback(name: String) = Tool(
    name = name,
    description = "Injected by the app after each tool batch; never call it.",
    isReadOnly = true,
    execute = {
        listOf(
            UIMessagePart.Text(
                "This note is injected by the app automatically and takes no input. " +
                    "You never need to call \"$name\"; continue with the task."
            )
        )
    },
)

/**
 * 从上一次插引导之后，这一步又跑了几步。
 *
 * 返回 null 表示这条消息里还没插过引导——这一批就该插，让用户的指令尽快生效。
 * 步号取自消息上的 [UIMessage.stepCount]，不能用「连续工具 part」去推：
 * 模型连续两步都只吐工具调用、中间没有文本时，两批会挤成一段，间隔就永远数不到。
 */
fun UIMessage.stepsSinceLastGuidance(): Int? =
    stepsSinceLastInjection { it.isInjectedTool() && it.toolName == GUIDANCE_TOOL_NAME }

/** 从上一次补清单之后又走了几步；null 表示还没补过 */
fun UIMessage.stepsSinceLastTodoReminder(): Int? =
    stepsSinceLastInjection { it.isInjectedTool() && it.toolName == TODO_REMINDER_TOOL_NAME }

private fun UIMessage.stepsSinceLastInjection(match: (UIMessagePart.Tool) -> Boolean): Int? {
    val injectedAt = parts
        .filterIsInstance<UIMessagePart.Tool>()
        .lastOrNull(match)
        ?.inputAsJson()
        // 模型幻觉出来的同名调用参数不是我们写的，取不到步号就当没插过
        ?.let { element -> (element as? JsonObject)?.get("step") as? JsonPrimitive }
        ?.intOrNull
        ?: return null
    return ((stepCount ?: 0) - injectedAt).coerceAtLeast(0)
}

/** 这条消息里有没有注入过清单 */
fun UIMessage.hasTodoReminder(): Boolean =
    parts.filterIsInstance<UIMessagePart.Tool>()
        .any { it.isInjectedTool() && it.toolName == TODO_REMINDER_TOOL_NAME }

/** 引导的正文；模型自己幻觉出来的同名调用（没执行、没输出）不算，免得吞掉工具卡片 */
fun UIMessagePart.Tool.guidanceText(): String? =
    if (isInjectedTool() && isExecuted) {
        output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }.ifBlank { null }
    } else {
        null
    }

/** 清单提醒的正文；同样只认应用注入的那一种 */
fun UIMessagePart.Tool.todoReminderText(): String? =
    if (isInjectedTool() && isExecuted) {
        output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }.ifBlank { null }
    } else {
        null
    }
