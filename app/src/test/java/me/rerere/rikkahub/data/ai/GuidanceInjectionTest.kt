package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.buildTodoPrompt
import me.rerere.rikkahub.data.model.Guidance
import me.rerere.rikkahub.data.model.GuidanceMode
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.TodoStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceInjectionTest {
    private val todos = listOf(
        TodoItem(content = "读代码", status = TodoStatus.COMPLETED),
        TodoItem(content = "改注入点", status = TodoStatus.IN_PROGRESS),
    )

    @Test
    fun `guidance becomes an executed tool call that needs no approval`() {
        val part = guidanceToolPart(Guidance(content = "先别改 app/", mode = GuidanceMode.REPEAT), step = 1)

        assertEquals(GUIDANCE_TOOL_NAME, part.toolName)
        assertTrue(part.isExecuted)
        assertTrue(part.approvalState !is me.rerere.ai.ui.ToolApprovalState.Pending)
        assertEquals("先别改 app/", (part.output.single() as UIMessagePart.Text).text)
    }

    @Test
    fun `each injection gets its own tool call id`() {
        val guidance = Guidance(content = "保持")
        val first = guidanceToolPart(guidance, step = 1)
        val second = guidanceToolPart(guidance, step = 1)

        // 同一条助手消息里两个相同 id 的 tool_use 会被 Anthropic 整条拒掉
        assertNotEquals(first.toolCallId, second.toolCallId)
    }

    @Test
    fun `tool call ids stay inside the provider limit`() {
        // OpenAI 系的 tool_call_id 上限 40 字符，超了整条请求会被拒；前缀 2 位 + 16 位十六进制
        assertEquals(18, guidanceToolPart(Guidance(content = "x"), step = 1).toolCallId.length)
        assertEquals(18, todoReminderToolPart(todos, step = 1)!!.toolCallId.length)
    }

    @Test
    fun `an empty todo list yields no reminder`() {
        assertNull(todoReminderToolPart(emptyList(), step = 1))
    }

    @Test
    fun `todo reminder carries the rendered list`() {
        val part = todoReminderToolPart(todos, step = 1)!!

        assertEquals(TODO_REMINDER_TOOL_NAME, part.toolName)
        assertEquals(buildTodoPrompt(todos), part.todoReminderText())
        assertTrue(part.todoReminderText()!!.contains("改注入点"))
    }

    @Test
    fun `steps are counted since the last guidance`() {
        fun tool(id: String) = UIMessagePart.Tool(
            toolCallId = id,
            toolName = "read",
            input = "{}",
            output = listOf(UIMessagePart.Text("ok")),
        )

        fun assistant(stepCount: Int, vararg parts: UIMessagePart) =
            UIMessage(role = MessageRole.ASSISTANT, parts = parts.toList(), stepCount = stepCount)

        // 第 2 步插的引导
        val guidance = guidanceToolPart(Guidance(content = "看这里"), step = 2)

        // 还没插过：这一步就该插
        assertNull(assistant(1, tool("1")).stepsSinceLastGuidance())
        // 插入后的下一步
        assertEquals(1, assistant(3, tool("1"), guidance, tool("2")).stepsSinceLastGuidance() ?: -1)
        // 连两步只吐工具调用、中间没有文本，也不会挤成一段
        assertEquals(2, assistant(4, tool("1"), guidance, tool("2"), tool("3")).stepsSinceLastGuidance() ?: -1)
    }

    @Test
    fun `a tool call the model made itself is not mistaken for an injection`() {
        // 模型照着历史误调同名工具时，id 是 provider 给的，没有注入前缀
        val hallucinated = UIMessagePart.Tool(
            toolCallId = "toolu_01ABC",
            toolName = TODO_REMINDER_TOOL_NAME,
            input = "{}",
            output = listOf(UIMessagePart.Text("whatever")),
        )

        assertFalse(hallucinated.isInjectedTool())
        assertNull(hallucinated.todoReminderText())
        assertFalse(
            UIMessage(role = MessageRole.ASSISTANT, parts = listOf(hallucinated)).hasTodoReminder()
        )
    }

    @Test
    fun `guidance text is only read from guidance tools`() {
        val guidance = guidanceToolPart(Guidance(content = "看这里"), step = 1)
        val todoPart = todoReminderToolPart(todos, step = 1)!!

        assertEquals("看这里", guidance.guidanceText())
        assertNull(todoPart.guidanceText())
        assertNull(guidance.todoReminderText())
    }
}
