package me.rerere.rikkahub.ui.pages.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class MessageEnterAnimationTest {
    private val a = Uuid.random()
    private val b = Uuid.random()
    private val c = Uuid.random()
    private val d = Uuid.random()

    @Test
    fun `a history list arriving into an empty frame is not an append`() {
        // 冷打开已有会话：首帧是空列表，随后整段历史一次性到位，不能当成新消息
        assertEquals(emptyList<Uuid>(), newAppendedMessageIds(emptyList(), listOf(a, b)))
    }

    @Test
    fun `empty current has nothing to animate`() {
        assertEquals(emptyList<Uuid>(), newAppendedMessageIds(listOf(a, b), emptyList()))
    }

    @Test
    fun `unchanged list has nothing to animate`() {
        assertEquals(emptyList<Uuid>(), newAppendedMessageIds(listOf(a, b), listOf(a, b)))
    }

    @Test
    fun `single message appended at the tail`() {
        assertEquals(listOf(c), newAppendedMessageIds(listOf(a, b), listOf(a, b, c)))
    }

    @Test
    fun `several messages appended at once all animate`() {
        assertEquals(listOf(c, d), newAppendedMessageIds(listOf(a, b), listOf(a, b, c, d)))
    }

    @Test
    fun `insertion in the middle is not an append`() {
        assertEquals(emptyList<Uuid>(), newAppendedMessageIds(listOf(a, b), listOf(a, c, b)))
    }

    @Test
    fun `deleting does not animate`() {
        assertEquals(emptyList<Uuid>(), newAppendedMessageIds(listOf(a, b, c), listOf(a, c)))
    }

    @Test
    fun `replacing the whole list is not an append`() {
        assertEquals(emptyList<Uuid>(), newAppendedMessageIds(listOf(a, b), listOf(c, d)))
    }

    @Test
    fun `branch switch that keeps ids does not animate`() {
        // 切换分支只改 selectIndex，id 序列不动
        assertEquals(emptyList<Uuid>(), newAppendedMessageIds(listOf(a, b, c), listOf(a, b, c)))
    }

    @Test
    fun `first frame never animates`() {
        val tracker = MessageEnterTracker { 0L }
        assertTrue(tracker.track(listOf(a, b)).isEmpty())
    }

    @Test
    fun `opening a stored conversation does not animate its history`() {
        val tracker = MessageEnterTracker { 0L }
        // 首帧是 Conversation.ofId 的空列表
        assertTrue(tracker.track(emptyList()).isEmpty())
        // 数据库读回来的整段历史
        val history = List(12) { Uuid.random() }
        assertTrue(tracker.track(history).isEmpty())
        // 之后真正追加的一条照常播
        assertEquals(setOf(c), tracker.track(history + c))
    }

    @Test
    fun `first message of an empty conversation animates`() {
        val tracker = MessageEnterTracker { 0L }
        assertTrue(tracker.track(emptyList()).isEmpty())
        assertEquals(setOf(a), tracker.track(listOf(a)))
    }

    @Test
    fun `tracker keeps the pending id until it is consumed`() {
        val tracker = MessageEnterTracker { 0L }
        assertTrue(tracker.track(listOf(a, b)).isEmpty())

        assertEquals(setOf(c), tracker.track(listOf(a, b, c)))
        // 动画还没播完又来一次重组，结果不能被清掉
        assertEquals(setOf(c), tracker.track(listOf(a, b, c)))

        tracker.consume(c)
        assertTrue(tracker.track(listOf(a, b, c)).isEmpty())
    }

    @Test
    fun `tracker replaces a stale pending id with the newer one`() {
        val tracker = MessageEnterTracker { 0L }
        tracker.track(listOf(a))
        assertEquals(setOf(b), tracker.track(listOf(a, b)))
        // b 还没播（比如被滚出屏幕），下一条追加时只认新的一条
        assertEquals(setOf(c), tracker.track(listOf(a, b, c)))
    }

    @Test
    fun `tracker short circuits when the same list instance comes back`() {
        val tracker = MessageEnterTracker { 0L }
        tracker.track(listOf(a))

        val ids = listOf(a, b)
        assertEquals(setOf(b), tracker.track(ids))
        // 同一实例走引用同一性的快路径，pending 不能被清掉
        assertEquals(setOf(b), tracker.track(ids))
    }

    @Test
    fun `pending id is dropped once the enter window has passed`() {
        var clock = 0L
        val tracker = MessageEnterTracker { clock }
        tracker.track(listOf(a))
        assertEquals(setOf(b), tracker.track(listOf(a, b)))

        // 追加时不在可见区，过了窗口才被组合出来：不补播
        clock += 900
        assertTrue(tracker.track(listOf(a, b)).isEmpty())
    }
}
