package me.rerere.rikkahub.ui.components.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 数字输入框的草稿规则：输入 0 / 007 时文本和光标不能被外部回声重置，
 * 切模型、点预设这类真的外部变化又必须刷新。
 */
class NumberInputDraftTest {
    /** 门限输入框的规则：填 0 等于清空，清空输入框也提交 null */
    private fun threshold(input: String, draft: NumberInputDraft) = draft.withInput(
        input = input,
        maxDigits = 8,
        normalize = { it.takeIf { tokens -> tokens > 0 } },
        commitEmpty = true,
    )

    /** 滑杆输入框的规则：区间之外先记草稿、不提交 */
    private fun slider(input: String, draft: NumberInputDraft, range: IntRange = 4..1024) =
        draft.withInput(input = input, maxDigits = 7, accepts = { it in range })

    @Test
    fun `typing zero clears the threshold but keeps the text`() {
        val typed = threshold("0", numberInputDraft(128))

        assertEquals("0", typed.text)
        assertTrue(typed.pending)
        assertNull(typed.expected)

        // 上游把 null 写下来了：这是自己刚提交的回声，输入的 0 不能被抹掉
        val echoed = typed.withExternal(null)
        assertEquals("0", echoed.text)
        assertFalse(echoed.pending)
    }

    @Test
    fun `leading zeros survive the echo`() {
        val typed = threshold("007", numberInputDraft(128))
        assertEquals("007", typed.text)
        assertEquals(7, typed.expected!!)

        val echoed = typed.withExternal(7)
        assertEquals("007", echoed.text)
        assertEquals(7, echoed.external)
    }

    @Test
    fun `empty input commits null for thresholds and nothing for sliders`() {
        val cleared = threshold("", numberInputDraft(64))
        assertEquals("", cleared.text)
        assertTrue(cleared.pending)
        assertNull(cleared.expected)

        val blank = slider("", numberInputDraft(64))
        assertEquals("", blank.text)
        assertFalse(blank.pending)
    }

    @Test
    fun `external change replaces the text`() {
        val typed = threshold("007", numberInputDraft(128)).withExternal(7)
        // 点预设 / 拖滑杆：外部值变成 448，不是这次输入的回声
        val preset = typed.withExternal(448)
        assertEquals("448", preset.text)
        assertEquals(448, preset.external)
    }

    @Test
    fun `switching model refreshes even after a matched commit`() {
        val typed = slider("512", numberInputDraft(128))
        assertEquals(512, typed.expected!!)
        val committed = typed.withExternal(512)
        assertEquals("512", committed.text)

        // 换一个模型，值回到 128
        val switched = committed.withExternal(128)
        assertEquals("128", switched.text)
    }

    @Test
    fun `out of range digits stay in the box`() {
        val typed = slider("2000", numberInputDraft(256))
        assertEquals("2000", typed.text)
        assertFalse(typed.pending)

        // 外部值没变，草稿原样留着
        assertEquals("2000", typed.withExternal(256).text)
        // 真换了值才覆盖
        assertEquals("512", typed.withExternal(512).text)
    }

    @Test
    fun `non digits are dropped`() {
        val typed = slider("1a2b", numberInputDraft(4))
        assertEquals("12", typed.text)
        assertEquals(12, typed.expected!!)
    }

    @Test
    fun `same external value does not touch the draft`() {
        val draft = numberInputDraft(128)
        assertEquals(draft, draft.withExternal(128))
    }

    @Test
    fun `every external reconciliation records the value it received`() {
        val draft = numberInputDraft(128)
        assertNull(draft.withExternal(null).external)
        assertEquals(7, draft.withExternal(7).external)
        assertEquals(448, draft.withExternal(448).external)
    }
}
