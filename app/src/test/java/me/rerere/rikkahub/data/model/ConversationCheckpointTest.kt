package me.rerere.rikkahub.data.model

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.limitContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationCheckpointTest {
    @Test
    fun `context checkpoint is inserted after the anchor node without touching other nodes`() {
        val nodes = List(4) { UIMessage.user("message $it").toMessageNode() }
        val source = Conversation(assistantId = Uuid.random(), messageNodes = nodes)

        val result = source.insertContextCheckpoint(afterNodeId = nodes[1].id, summary = "summary")!!

        assertEquals(nodes.subList(0, 2), result.messageNodes.subList(0, 2))
        assertEquals(nodes.subList(2, 4), result.messageNodes.subList(3, 5))
        val checkpoint = result.messageNodes[2].currentMessage
        assertTrue(checkpoint.isContextCheckpoint)
        assertEquals("summary", checkpoint.toText())
        // 只有检查点之后的消息会继续发送给模型
        assertEquals(result.currentMessages.subList(2, 5), result.currentMessages.limitContext(0))
    }

    @Test
    fun `checkpoint split keeps the original message and only trims the request context`() {
        val parts = listOf(
            UIMessagePart.Text("summarized prefix"),
            UIMessagePart.Text("kept suffix"),
        )
        val split = UIMessage.assistant("").copy(parts = parts).toMessageNode()
        val nodes = listOf(UIMessage.user("hello").toMessageNode(), split)
        val source = Conversation(assistantId = Uuid.random(), messageNodes = nodes)

        val result = source.insertContextCheckpoint(
            afterNodeId = nodes[0].id,
            summary = "summary",
            splitMessageId = split.currentMessage.id,
            keepPartsFrom = 1,
        )!!

        assertEquals(parts, result.messageNodes[2].currentMessage.parts)
        assertEquals(
            listOf(result.messageNodes[1].currentMessage, split.currentMessage.copy(parts = parts.drop(1))),
            result.currentMessages.limitContext(0),
        )
    }

    @Test
    fun `context checkpoint is not inserted when the anchor node is gone`() {
        val source = Conversation(
            assistantId = Uuid.random(),
            messageNodes = listOf(UIMessage.user("message").toMessageNode()),
        )

        assertNull(source.insertContextCheckpoint(afterNodeId = Uuid.random(), summary = "summary"))
    }
}
