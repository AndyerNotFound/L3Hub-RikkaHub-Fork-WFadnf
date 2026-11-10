package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 关闭消息队列后，发送不允许再进队列：能在当前状态开的立刻发，开不了的拒绝。
 */
class MessageQueueDisabledSendPolicyTest {
    private fun decide(
        notice: Boolean = false,
        hasJob: Boolean = false,
        awaitingApproval: Boolean = false,
    ) = decideUnqueuedSend(notice, hasJob, awaitingApproval)

    @Test
    fun `idle conversation sends immediately`() {
        assertEquals(UnqueuedSend.Dispatch, decide())
    }

    @Test
    fun `running generation is interrupted instead of queued`() {
        assertEquals(UnqueuedSend.Interrupt, decide(hasJob = true))
    }

    @Test
    fun `pending tool approval is interrupted instead of queued`() {
        // 等审批同样是「占着这一轮」：发送要能立刻开新一轮，审批中的工具交给新一轮开头取消
        assertEquals(UnqueuedSend.Interrupt, decide(awaitingApproval = true))
        assertEquals(UnqueuedSend.Interrupt, decide(hasJob = true, awaitingApproval = true))
    }

    @Test
    fun `sub agent results never interrupt running work`() {
        assertEquals(UnqueuedSend.DeferNotice, decide(notice = true, hasJob = true))
        assertEquals(UnqueuedSend.DeferNotice, decide(notice = true, awaitingApproval = true))
        assertEquals(UnqueuedSend.Dispatch, decide(notice = true))
    }

    private fun canDispatch(
        hasJob: Boolean = false,
        awaitingApproval: Boolean = false,
        queueEnabled: Boolean = false,
        hasUserMessage: Boolean = true,
    ) = canDispatchInbox(hasJob, awaitingApproval, queueEnabled, hasUserMessage)

    @Test
    fun `idle inbox dispatches in both queue modes`() {
        assertTrue(canDispatch(queueEnabled = true))
        assertTrue(canDispatch(queueEnabled = false))
    }

    @Test
    fun `running generation always holds the inbox`() {
        assertFalse(canDispatch(hasJob = true))
        assertFalse(canDispatch(queueEnabled = true, hasJob = true))
    }

    @Test
    fun `queue on waits for tool approval`() {
        assertFalse(canDispatch(queueEnabled = true, awaitingApproval = true))
    }

    @Test
    fun `queue off lets user input skip a pending tool approval`() {
        // 关闭队列后用户接着发消息 = 不要那半截了，派发时由新一轮开头取消待审批的工具
        assertTrue(canDispatch(queueEnabled = false, awaitingApproval = true, hasUserMessage = true))
        // 只有通知在等时仍然让路
        assertFalse(canDispatch(queueEnabled = false, awaitingApproval = true, hasUserMessage = false))
    }
}
