package me.rerere.rikkahub.ui.pages.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSendGateTest {
    // 分支顺序与改前的 if-else 链一致：没模型优先，其次「到顶 + 自动压缩」的确认，
    // 再次是到顶拦截，最后才是放行
    @Test
    fun `no model wins over every other condition`() {
        assertEquals(
            ChatSendGate.SelectModel,
            chatSendGate(
                hasModel = false,
                contextLimitReached = false,
                contextLimitBlocksSend = false,
                autoCompress = false,
            ),
        )
        assertEquals(
            ChatSendGate.SelectModel,
            chatSendGate(
                hasModel = false,
                contextLimitReached = true,
                contextLimitBlocksSend = true,
                autoCompress = true,
            ),
        )
    }

    @Test
    fun `under the limit sends directly`() {
        assertEquals(
            ChatSendGate.Allow,
            chatSendGate(
                hasModel = true,
                contextLimitReached = false,
                contextLimitBlocksSend = false,
                autoCompress = true,
            ),
        )
    }

    @Test
    fun `limit reached with auto compress asks before sending`() {
        assertEquals(
            ChatSendGate.ConfirmCompress,
            chatSendGate(
                hasModel = true,
                contextLimitReached = true,
                contextLimitBlocksSend = true,
                autoCompress = true,
            ),
        )
    }

    @Test
    fun `limit reached with enforcement blocks instead of sending`() {
        assertEquals(
            ChatSendGate.BlockedByContextLimit,
            chatSendGate(
                hasModel = true,
                contextLimitReached = true,
                contextLimitBlocksSend = true,
                autoCompress = false,
            ),
        )
    }

    // 模型关掉强制拦截时，到顶只提示一次，仍然放行
    @Test
    fun `limit reached without enforcement still allows sending`() {
        assertEquals(
            ChatSendGate.Allow,
            chatSendGate(
                hasModel = true,
                contextLimitReached = true,
                contextLimitBlocksSend = false,
                autoCompress = true,
            ),
        )
        assertEquals(
            ChatSendGate.Allow,
            chatSendGate(
                hasModel = true,
                contextLimitReached = true,
                contextLimitBlocksSend = false,
                autoCompress = false,
            ),
        )
    }

    // 插话与长按「不回答」只过上下文这一道门
    @Test
    fun `interject and long press only check the context limit`() {
        assertTrue(contextAllowsSend(contextLimitReached = false, contextLimitBlocksSend = false))
        assertTrue(contextAllowsSend(contextLimitReached = true, contextLimitBlocksSend = false))
        assertFalse(contextAllowsSend(contextLimitReached = true, contextLimitBlocksSend = true))
    }
}
