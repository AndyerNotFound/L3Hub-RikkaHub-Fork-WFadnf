package me.rerere.rikkahub.ui.pages.chat

import kotlinx.datetime.LocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * 地图投影的纯 JVM 断言：投影只碰 Conversation/MessageNode/UIMessage，
 * 不加载 View，也不需要 android 运行时。
 */
class ConversationMapProjectionTest {

    private fun message(
        role: MessageRole,
        vararg parts: UIMessagePart,
        id: Uuid = Uuid.random(),
        finished: Boolean = true,
    ) = UIMessage(
        id = id,
        role = role,
        parts = parts.toList(),
        finishedAt = if (finished) LocalDateTime(2026, 1, 1, 12, 0) else null,
    )

    private fun text(role: MessageRole, text: String, finished: Boolean = true) =
        message(role, UIMessagePart.Text(text), finished = finished)

    private fun conversation(vararg nodes: MessageNode) = Conversation(
        assistantId = Uuid.random(),
        messageNodes = nodes.toList(),
    )

    private fun node(vararg messages: UIMessage, selectIndex: Int = 0) = MessageNode(
        messages = messages.toList(),
        selectIndex = selectIndex,
    )

    @Test
    fun `empty conversation projects to an empty snapshot`() {
        val snapshot = ConversationMapProjection().project(conversation())

        assertTrue(snapshot.isEmpty)
        assertEquals(emptyList<ConversationMapNode>(), snapshot.nodes)
        assertEquals(0, snapshot.nodeCount)
        assertEquals(0, snapshot.branchCount)
    }

    @Test
    fun `spine keeps node order and only the tail is head`() {
        val first = node(text(MessageRole.USER, "你好"))
        val second = node(text(MessageRole.ASSISTANT, "在的"))
        val snapshot = ConversationMapProjection().project(conversation(first, second))

        assertEquals(listOf(0, 1), snapshot.nodes.map { it.nodeIndex })
        assertEquals(listOf(false, true), snapshot.nodes.map { it.head })
        assertEquals(2, snapshot.nodeCount)
        assertEquals(0, snapshot.branchCount)
    }

    @Test
    fun `selected variant comes first and siblings keep their real index`() {
        val variants = node(
            text(MessageRole.ASSISTANT, "第一次"),
            text(MessageRole.ASSISTANT, "第二次"),
            text(MessageRole.ASSISTANT, "第三次"),
            selectIndex = 2,
        )
        val snapshot = ConversationMapProjection().project(conversation(variants))
        val ofNode = snapshot.nodes.filter { it.nodeIndex == 0 }

        assertEquals(listOf(2, 0, 1), ofNode.map { it.messageIndex })
        assertEquals(listOf(true, false, false), ofNode.map { it.selected })
        assertEquals("第三次", ofNode.first().label)
        assertEquals(3, ofNode.first().variantCount)
        assertEquals(2, snapshot.branchCount)
        // 只有选中的那条算 head
        assertEquals(1, ofNode.count { it.head })
    }

    @Test
    fun `label uses the first text part with whitespace collapsed and truncated`() {
        val long = "  ## 标题\n\n第一段   内容 " + "x".repeat(80)
        val snapshot = ConversationMapProjection().project(
            conversation(node(message(MessageRole.ASSISTANT, UIMessagePart.Text("   "), UIMessagePart.Text(long))))
        )
        val label = snapshot.nodes.single().label

        assertTrue("空白应折叠成单个空格: $label", !label.contains("  "))
        assertTrue("换行应该被折叠: $label", !label.contains("\n"))
        assertTrue("开头不该留空白: $label", !label.startsWith(" "))
        assertTrue("应截断到 28 字以内: $label", label.length <= 28)
        assertTrue("截断要带省略号: $label", label.endsWith("…"))
    }

    @Test
    fun `message without text keeps an empty label instead of invented text`() {
        val image = UIMessagePart.Image(url = "file:///tmp/a.png")
        val snapshot = ConversationMapProjection().project(
            conversation(node(message(MessageRole.USER, image)))
        )

        assertEquals("", snapshot.nodes.single().label)
        assertEquals(MapNodeKind.USER, snapshot.nodes.single().kind)
    }

    @Test
    fun `notice and checkpoint kinds are distinguished from plain user text`() {
        val notice = UIMessage(
            id = Uuid.random(),
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text("子代理完成")),
            isSubAgentNotice = true,
        )
        val checkpoint = UIMessage(
            id = Uuid.random(),
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text("历史摘要")),
            isContextCheckpoint = true,
        )
        val plain = text(MessageRole.USER, "普通消息")
        val snapshot = ConversationMapProjection().project(
            conversation(node(plain), node(notice), node(checkpoint), node(text(MessageRole.TOOL, "ok")))
        )

        assertEquals(
            listOf(MapNodeKind.USER, MapNodeKind.NOTICE, MapNodeKind.SYSTEM, MapNodeKind.TOOL),
            snapshot.nodes.map { it.kind },
        )
    }

    @Test
    fun `tool and reasoning counts come from the real parts`() {
        val target = message(
            MessageRole.ASSISTANT,
            UIMessagePart.Text("跑完了"),
            UIMessagePart.Tool(toolCallId = "c1", toolName = "read_file", input = "{}"),
            UIMessagePart.Tool(toolCallId = "c2", toolName = "grep", input = "{}"),
            UIMessagePart.Reasoning(reasoning = "先看看"),
        )
        val snapshot = ConversationMapProjection().project(conversation(node(target)))
        val projected = snapshot.nodes.single()

        assertEquals(2, projected.toolCount)
        assertEquals(1, projected.reasoningCount)
    }

    @Test
    fun `streaming label only refreshes after it grows by a step`() {
        val id = Uuid.random()
        val projection = ConversationMapProjection()
        val head = { content: String ->
            conversation(
                node(
                    UIMessage(
                        id = id,
                        role = MessageRole.ASSISTANT,
                        parts = listOf(UIMessagePart.Text(content)),
                        finishedAt = null,
                    )
                )
            )
        }

        val first = projection.project(head("你好"))
        val small = projection.project(head("你好呀，我在"))
        val big = projection.project(head("你好呀，我在" + "，继续写下去".repeat(6)))

        assertEquals("你好", first.nodes.single().label)
        assertEquals("小幅增长时沿用上一次的标题", "你好", small.nodes.single().label)
        assertNotEquals("明显变长后要更新", "你好", big.nodes.single().label)
    }

    @Test
    fun `finished message updates the label immediately`() {
        val id = Uuid.random()
        val projection = ConversationMapProjection()
        val before = projection.project(
            conversation(node(UIMessage(id = id, role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("短")))))
        )
        val after = projection.project(
            conversation(
                node(
                    UIMessage(
                        id = id,
                        role = MessageRole.ASSISTANT,
                        parts = listOf(UIMessagePart.Text("短一点但已经写完了")),
                        finishedAt = LocalDateTime(2026, 1, 1, 12, 0),
                    )
                )
            )
        )

        assertEquals("短", before.nodes.single().label)
        assertEquals("短一点但已经写完了", after.nodes.single().label)
    }

    @Test
    fun `unchanged node is reused between projections`() {
        val projection = ConversationMapProjection()
        val first = node(text(MessageRole.USER, "不动"))
        val greeting = projection.project(conversation(first, node(text(MessageRole.ASSISTANT, "好"))))
        val second = projection.project(
            conversation(
                first,
                node(text(MessageRole.ASSISTANT, "好")),
                node(text(MessageRole.USER, "新的一条")),
            )
        )

        assertSame(
            "没变的节点必须复用同一个对象，避免下游重复布局",
            greeting.nodes[0],
            second.nodes[0],
        )
    }

    @Test
    fun `selectIndex change switches the visible text of the same node`() {
        val variants = arrayOf(
            text(MessageRole.ASSISTANT, "甲"),
            text(MessageRole.ASSISTANT, "乙"),
        )
        val projection = ConversationMapProjection()
        val first = projection.project(conversation(node(*variants, selectIndex = 0)))
        val second = projection.project(conversation(node(*variants, selectIndex = 1)))

        // 一个节点有两条变体，所以 nodes 里有两项：只有 selected 那条是可见的主卡
        val firstVisible = first.nodes.first { it.selected }
        val secondVisible = second.nodes.first { it.selected }
        assertEquals("甲", firstVisible.label)
        assertEquals("乙", secondVisible.label)
        assertEquals(0, firstVisible.messageIndex)
        assertEquals(1, secondVisible.messageIndex)
    }

    @Test
    fun `selectIndex out of range falls back to the first variant`() {
        val variants = arrayOf(
            text(MessageRole.ASSISTANT, "甲"),
            text(MessageRole.ASSISTANT, "乙"),
        )
        val projection = ConversationMapProjection()

        val tooBig = projection.project(conversation(node(*variants, selectIndex = 7)))
        val negative = projection.project(conversation(node(*variants, selectIndex = -1)))

        // 越界的 selectIndex 不能让节点没有可见变体，也不能抛
        assertEquals("甲", tooBig.nodes.first { it.selected }.label)
        assertEquals(0, tooBig.nodes.first { it.selected }.messageIndex)
        assertEquals(1, tooBig.nodes.count { it.selected })
        assertEquals(0, negative.nodes.first { it.selected }.messageIndex)
        assertEquals(1, negative.nodes.count { it.selected })
        assertEquals(listOf(0, 1), tooBig.nodes.map { it.messageIndex })
    }

    @Test
    fun `trailing empty node does not steal the head marker`() {
        val empty = MessageNode(messages = emptyList(), selectIndex = 0)
        val snapshot = ConversationMapProjection().project(
            conversation(
                node(text(MessageRole.USER, "问")),
                node(text(MessageRole.ASSISTANT, "答")),
                empty,
            )
        )

        // 末尾那个空节点不画，head 仍然落在最后一个真的有内容的节点上
        val heads = snapshot.nodes.filter { it.head }
        assertEquals(1, heads.count())
        assertEquals(1, heads.single().nodeIndex)
        assertEquals(3, snapshot.nodeCount)
        assertEquals(listOf(0, 1), snapshot.nodes.map { it.nodeIndex })
    }

    @Test
    fun `node without messages is skipped but keeps real indices`() {
        val empty = MessageNode(messages = emptyList(), selectIndex = 0)
        val snapshot = ConversationMapProjection().project(
            conversation(empty, node(text(MessageRole.USER, "第一条")))
        )

        assertEquals(listOf(1), snapshot.nodes.map { it.nodeIndex })
        // 节点数仍然按真实列表算，空节点只是不画
        assertEquals(2, snapshot.nodeCount)
    }

    @Test
    fun `tap resolves stable ids after nodes and variants are reordered`() {
        val first = text(MessageRole.ASSISTANT, "first")
        val second = text(MessageRole.ASSISTANT, "second")
        val target = node(first, second)
        val prefix = node(text(MessageRole.USER, "inserted"))
        val latest = conversation(prefix, target.copy(messages = listOf(second, first)))

        val resolved = resolveMapTap(latest, target.id.toString(), second.id.toString())!!

        assertEquals(target.id, resolved.nodeId)
        assertEquals(1, resolved.nodeIndex)
        assertEquals(0, resolved.messageIndex)
    }

    @Test
    fun `tap for a removed message is ignored rather than selecting its old index`() {
        val removed = text(MessageRole.ASSISTANT, "removed")
        val replacement = text(MessageRole.ASSISTANT, "replacement")
        val target = node(removed)
        val latest = conversation(target.copy(messages = listOf(replacement)))

        assertNull(resolveMapTap(latest, target.id.toString(), removed.id.toString()))
        assertNull(resolveMapTap(conversation(), target.id.toString(), removed.id.toString()))
    }

    @Test
    fun `only message nodes show up, no synthetic edges or sub agent nodes`() {
        val anchor = node(text(MessageRole.USER, "起点"))
        val reply = node(text(MessageRole.ASSISTANT, "回应"))
        val snapshot = ConversationMapProjection().project(conversation(anchor, reply))
        val nodeIds = setOf(anchor.id, reply.id)

        assertTrue(snapshot.nodes.all { it.nodeId in nodeIds })
        assertEquals(snapshot.nodes.size, snapshot.nodes.map { it.nodeIndex to it.messageIndex }.distinct().size)
    }
}
