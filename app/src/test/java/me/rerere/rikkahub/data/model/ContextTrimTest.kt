package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class ContextTrimTest {
    private fun text(char: Char, length: Int) = UIMessagePart.Text(char.toString().repeat(length))

    private fun message(vararg parts: UIMessagePart) =
        UIMessage(role = MessageRole.ASSISTANT, parts = parts.toList())

    @Test
    fun `token trim cuts inside the assistant message at the nearest part boundary`() {
        val nodes = listOf(
            UIMessage.user("a".repeat(400)).toMessageNode(),
            message(text('b', 400), text('c', 400), text('d', 400)).toMessageNode(),
        )
        val conversation = Conversation(assistantId = Uuid.random(), messageNodes = nodes)

        val trim = conversation.keepRecentContextForTokens(250)

        assertEquals(ContextTrim(nodeIndex = 1, partIndex = 1), trim)
    }

    @Test
    fun `token trim keeps the whole newest message even when it alone exceeds the target`() {
        val nodes = listOf(
            UIMessage.assistant("a".repeat(400)).toMessageNode(),
            UIMessage.user("b".repeat(800)).toMessageNode(),
        )
        val conversation = Conversation(assistantId = Uuid.random(), messageNodes = nodes)

        val trim = conversation.keepRecentContextForTokens(50)

        assertEquals(ContextTrim(nodeIndex = 1), trim)
    }

    @Test
    fun `empty parts are not chosen as split boundaries`() {
        val nodes = listOf(
            UIMessage.user("a".repeat(400)).toMessageNode(),
            message(text('b', 400), UIMessagePart.Text(""), text('c', 400)).toMessageNode(),
        )
        val conversation = Conversation(assistantId = Uuid.random(), messageNodes = nodes)

        val trim = conversation.keepRecentContextForTokens(150)

        assertEquals(ContextTrim(nodeIndex = 1, partIndex = 2), trim)
    }

    @Test
    fun `reasoning parts participate in split boundary selection`() {
        val nodes = listOf(
            UIMessage.user("a".repeat(400)).toMessageNode(),
            message(
                UIMessagePart.Reasoning(reasoning = "思".repeat(400)),
                text('c', 400),
            ).toMessageNode(),
        )
        val conversation = Conversation(assistantId = Uuid.random(), messageNodes = nodes)

        val trim = conversation.keepRecentContextForTokens(150)

        assertEquals(ContextTrim(nodeIndex = 1, partIndex = 1), trim)
    }

    @Test
    fun `reasoning parts count toward context tokens`() {
        val reasoning = UIMessagePart.Reasoning(reasoning = "思".repeat(400))
        val tokens = estimateMessageTokens(message(reasoning))

        assertEquals(400 + 4, tokens)
    }

    @Test
    fun `token trim can split the first message after a checkpoint`() {
        val nodes = listOf(
            UIMessage.user("summary").copy(isContextCheckpoint = true).toMessageNode(),
            message(text('b', 400), text('c', 400), text('d', 400)).toMessageNode(),
        )
        val conversation = Conversation(assistantId = Uuid.random(), messageNodes = nodes)

        val trim = conversation.keepRecentContextForTokens(250)

        assertEquals(ContextTrim(nodeIndex = 1, partIndex = 1), trim)
    }

    @Test
    fun `token trim ignores history before the last checkpoint`() {
        val nodes = listOf(
            UIMessage.user("old".repeat(400)).toMessageNode(),
            UIMessage.user("summary").copy(isContextCheckpoint = true).toMessageNode(),
            message(text('a', 400)).toMessageNode(),
        )
        val conversation = Conversation(assistantId = Uuid.random(), messageNodes = nodes)

        val trim = conversation.keepRecentContextForTokens(150)

        assertEquals(ContextTrim(nodeIndex = 2), trim)
    }

    @Test
    fun `checkpoint truncates only the selected version of the split node`() {
        val parts = listOf(text('a', 8), text('b', 8), text('c', 8))
        val alternate = text('x', 8)
        val splitNode = MessageNode(
            messages = listOf(message(*parts.toTypedArray()), message(alternate)),
            selectIndex = 0,
        )
        val nodes = listOf(UIMessage.user("hello").toMessageNode(), splitNode)
        val conversation = Conversation(assistantId = Uuid.random(), messageNodes = nodes)

        val result = conversation.insertContextCheckpoint(
            afterNodeId = nodes[0].id,
            summary = "summary",
            truncateNodeId = splitNode.id,
            keepPartsFrom = 1,
        )!!

        assertEquals(3, result.messageNodes.size)
        assertEquals(parts.drop(1), result.messageNodes[2].currentMessage.parts)
        assertEquals(parts.drop(1), result.messageNodes[2].messages[0].parts)
        // 其它分支保持原样
        assertEquals(alternate, result.messageNodes[2].messages[1].parts.single())
    }

    @Test
    fun `checkpoint keeps the whole message when the part index is out of range`() {
        val whole = text('a', 8)
        val nodes = listOf(UIMessage.user("hello").toMessageNode(), message(whole).toMessageNode())
        val conversation = Conversation(assistantId = Uuid.random(), messageNodes = nodes)

        val result = conversation.insertContextCheckpoint(
            afterNodeId = nodes[0].id,
            summary = "summary",
            truncateNodeId = nodes[1].id,
            keepPartsFrom = 1,
        )

        assertEquals(3, result?.messageNodes?.size)
        assertEquals(listOf(whole), result?.messageNodes?.get(2)?.currentMessage?.parts)
    }
}
