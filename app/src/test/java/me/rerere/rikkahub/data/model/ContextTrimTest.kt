package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.limitContext
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

    /**
     * 回归：自动压缩开着「循环压缩」、保留量选「token 数」时，part 边界切分曾经把
     * 原文的前半段从会话里删掉，聊天页只剩下后半段。
     */
    @Test
    fun `token trim with checkpoint keeps every stored part of the split message`() {
        val parts = listOf(text('a', 400), text('b', 400), text('c', 400))
        val nodes = listOf(
            UIMessage.user("s".repeat(400)).toMessageNode(),
            message(*parts.toTypedArray()).toMessageNode(),
            UIMessage.user("t".repeat(400)).toMessageNode(),
        )
        val conversation = Conversation(assistantId = Uuid.random(), messageNodes = nodes)

        val trim = conversation.keepRecentContextForTokens(300)
        assertEquals(ContextTrim(nodeIndex = 1, partIndex = 1), trim)

        val result = conversation.insertContextCheckpoint(
            afterNodeId = nodes[trim.nodeIndex - 1].id,
            summary = "summary",
            splitMessageId = nodes[trim.nodeIndex].currentMessage.id,
            keepPartsFrom = trim.partIndex,
        )!!

        assertEquals(parts, result.messageNodes[2].currentMessage.parts)
        assertEquals(parts.drop(1), result.currentMessages.limitContext(0)[1].parts)
    }

    @Test
    fun `checkpoint split keeps every part visible while trimming the request`() {
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
            splitMessageId = splitNode.currentMessage.id,
            keepPartsFrom = 1,
        )!!

        assertEquals(3, result.messageNodes.size)
        // 原文（含其它分支）一个字都不少
        assertEquals(parts, result.messageNodes[2].currentMessage.parts)
        assertEquals(parts, result.messageNodes[2].messages[0].parts)
        assertEquals(alternate, result.messageNodes[2].messages[1].parts.single())
        // 只有发给模型的内容从截断点开始
        assertEquals(parts.drop(1), result.currentMessages.limitContext(0)[1].parts)
    }

    @Test
    fun `checkpoint keeps the whole message when the part index is out of range`() {
        val whole = text('a', 8)
        val nodes = listOf(UIMessage.user("hello").toMessageNode(), message(whole).toMessageNode())
        val conversation = Conversation(assistantId = Uuid.random(), messageNodes = nodes)

        val result = conversation.insertContextCheckpoint(
            afterNodeId = nodes[0].id,
            summary = "summary",
            splitMessageId = nodes[1].currentMessage.id,
            keepPartsFrom = 1,
        )!!

        assertEquals(3, result.messageNodes.size)
        // 下标越界时整条发出去，不能凭空丢掉没进摘要的内容
        assertEquals(listOf(whole), result.messageNodes[2].currentMessage.parts)
        assertEquals(listOf(whole), result.currentMessages.limitContext(0)[1].parts)
    }
}
