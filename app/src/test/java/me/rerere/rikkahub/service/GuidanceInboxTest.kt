package me.rerere.rikkahub.service

import me.rerere.rikkahub.data.model.Guidance
import me.rerere.rikkahub.data.model.GuidanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceInboxTest {
    private fun guidance(content: String, mode: GuidanceMode = GuidanceMode.SINGLE) =
        Guidance(content = content, mode = mode)

    @Test
    fun `a one-shot guidance is taken once and then dropped`() {
        val inbox = ConversationInbox()
        inbox.addGuidance(guidance("先跑测试"))

        assertEquals(listOf("先跑测试"), inbox.takeGuidances().map { it.content })
        assertTrue(inbox.state.value.guidances.isEmpty())
        assertTrue(inbox.takeGuidances().isEmpty())
    }

    @Test
    fun `a repeating guidance stays for the next tool batch`() {
        val inbox = ConversationInbox()
        inbox.addGuidance(guidance("别动 app/", GuidanceMode.REPEAT))

        assertEquals(1, inbox.takeGuidances().size)
        assertEquals(1, inbox.takeGuidances().size)
        assertEquals(1, inbox.state.value.guidances.size)
    }

    @Test
    fun `switching the mode takes effect on the next take`() {
        val inbox = ConversationInbox()
        val item = guidance("先看日志")
        inbox.addGuidance(item)

        inbox.setGuidanceMode(item.id, GuidanceMode.REPEAT)
        inbox.takeGuidances()

        assertEquals(GuidanceMode.REPEAT, inbox.state.value.guidances.single().mode)
    }

    @Test
    fun `a one-shot guidance goes out while repeating ones are held back`() {
        val inbox = ConversationInbox()
        inbox.addGuidance(guidance("一次"))
        inbox.addGuidance(guidance("常驻", GuidanceMode.REPEAT))

        val taken = inbox.takeGuidances(includeRepeating = false)

        assertEquals(listOf("一次"), taken.map { it.content })
        assertEquals(listOf("常驻"), inbox.state.value.guidances.map { it.content })
    }

    @Test
    fun `removing a guidance drops it from the next take`() {
        val inbox = ConversationInbox()
        val item = guidance("停一下")
        inbox.addGuidance(item)
        inbox.addGuidance(guidance("继续"))

        inbox.removeGuidance(item.id)

        assertEquals(listOf("继续"), inbox.takeGuidances().map { it.content })
    }

    @Test
    fun `blank guidance is ignored`() {
        val inbox = ConversationInbox()
        inbox.addGuidance(guidance("   "))

        assertTrue(inbox.state.value.guidances.isEmpty())
    }

    @Test
    fun `guidance keeps the order it was added in`() {
        val inbox = ConversationInbox()
        inbox.addGuidance(guidance("一", GuidanceMode.REPEAT))
        inbox.addGuidance(guidance("二"))
        inbox.addGuidance(guidance("三", GuidanceMode.REPEAT))

        assertEquals(listOf("一", "二", "三"), inbox.takeGuidances().map { it.content })
        assertEquals(listOf("一", "三"), inbox.state.value.guidances.map { it.content })
    }
}
