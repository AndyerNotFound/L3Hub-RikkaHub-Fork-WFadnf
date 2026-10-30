package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.local.ASK_USER_TOOL_NAME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoApproveToolTest {
    private fun tool(name: String) = Tool(
        name = name,
        description = "",
        needsApproval = { true },
        execute = { listOf(UIMessagePart.Text("ok")) },
    )

    @Test
    fun `sub agent tools no longer wait for approval and still execute`() = runBlocking {
        val wrapped = tool("shell").withAutoApproval()
        assertFalse(wrapped.needsApproval(JsonNull))
        assertEquals(listOf(UIMessagePart.Text("ok")), wrapped.execute(JsonNull))
    }

    @Test
    fun `ask user is answered by the sub agent itself`() = runBlocking {
        val wrapped = tool(ASK_USER_TOOL_NAME).withAutoApproval()
        assertFalse(wrapped.needsApproval(JsonNull))
        val text = (wrapped.execute(JsonNull).single() as UIMessagePart.Text).text
        assertTrue(text.contains("sub-agent conversation"))
        assertTrue(text.contains("Decide on your own"))
    }
}
