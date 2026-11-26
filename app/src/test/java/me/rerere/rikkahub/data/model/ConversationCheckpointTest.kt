package me.rerere.rikkahub.data.model

import me.rerere.ai.ui.UIMessage
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
    fun `previous checkpoints remain in conversation history`() {
        val nodes = List(4) { UIMessage.user("message $it").toMessageNode() }
        val source = Conversation(assistantId = Uuid.random(), messageNodes = nodes)

        val first = source.insertContextCheckpoint(afterNodeId = nodes[1].id, summary = "first")!!
        val second = first.insertContextCheckpoint(afterNodeId = nodes[2].id, summary = "second")!!

        assertEquals(2, second.messageNodes.count { it.currentMessage.isContextCheckpoint })
        assertEquals("first", second.messageNodes[2].currentMessage.toText())
        assertEquals(nodes[2], second.messageNodes[3])
        assertEquals("second", second.messageNodes[4].currentMessage.toText())
        assertEquals(nodes[3], second.messageNodes[5])
        // 聊天记录保留旧摘要，模型上下文仍然只从最新检查点开始
        assertEquals(
            second.currentMessages.subList(4, 6),
            second.currentMessages.limitContext(0),
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
