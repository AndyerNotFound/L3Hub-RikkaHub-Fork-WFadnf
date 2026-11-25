package me.rerere.rikkahub.ui.components.richtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnsiSgrTest {
    private val esc = "\u001B"

    @Test
    fun `plain text keeps a single default segment`() {
        val document = ansiDocument("hello\nworld")
        assertEquals("hello\nworld", document.plainText)
        assertEquals(1, document.segments.size)
        assertEquals(AnsiStyle.Default, document.segments.single().style)
    }

    @Test
    fun `basic colors and reset`() {
        val document = ansiDocument("${esc}[32mOK${esc}[0m done")
        assertEquals("OK done", document.plainText)
        assertEquals(0xFF00CD00.toInt(), document.segments[0].style.foreground)
        assertNull(document.segments[1].style.foreground)
    }

    @Test
    fun `styles accumulate until reset`() {
        val document = ansiDocument("${esc}[1;31mbold red${esc}[0m")
        val style = document.segments.single().style
        assertTrue(style.bold)
        assertEquals(0xFFCD0000.toInt(), style.foreground)
    }

    @Test
    fun `osc hyperlink payload never leaks into text`() {
        val document = ansiDocument("${esc}]8;;https://evil.example${esc}\\click${esc}]8;;${esc}\\")
        assertEquals("click", document.plainText)
    }

    @Test
    fun `osc terminated by bel is dropped`() {
        val document = ansiDocument("${esc}]0;title\u0007visible")
        assertEquals("visible", document.plainText)
    }

    @Test
    fun `unterminated control string is discarded entirely`() {
        val document = ansiDocument("before${esc}]8;;https://secret.example")
        assertEquals("before", document.plainText)
    }

    @Test
    fun `dcs and device queries are dropped`() {
        val document = ansiDocument("a${esc}Pq#0;2;0;0;0${esc}\\b${esc}[6nc")
        assertEquals("abc", document.plainText)
    }

    @Test
    fun `cursor movement is dropped not printed`() {
        val document = ansiDocument("${esc}[2J${esc}[1;1Hdone")
        assertEquals("done", document.plainText)
    }

    @Test
    fun `literal escaped esc sequences are unescaped`() {
        val document = ansiDocument("\\x1b[31mred\\x1b[0m")
        assertEquals("red", document.plainText)
        assertEquals(0xFFCD0000.toInt(), document.segments.single().style.foreground)
    }

    @Test
    fun `256 color cube and grayscale`() {
        assertEquals(0xFF000000.toInt(), palette256(16))
        assertEquals(0xFFFFFFFF.toInt(), palette256(231))
        assertEquals(0xFF080808.toInt(), palette256(232))
        assertEquals(0xFFEEEEEE.toInt(), palette256(255))
    }

    @Test
    fun `extended color parameters do not leak into text`() {
        val document = ansiDocument("${esc}[38;2;1;2;3mX${esc}[0m")
        assertEquals("X", document.plainText)
        assertEquals(0xFF010203.toInt(), document.segments.single().style.foreground)
    }

    @Test
    fun `inverse swaps resolved colors`() {
        val style = AnsiStyle(foreground = 0xFF111111.toInt(), background = 0xFF222222.toInt(), inverse = true)
        assertEquals(0xFF222222.toInt(), style.resolvedForeground(0xFFFFFFFF.toInt()))
        assertEquals(0xFF111111.toInt(), style.resolvedBackground(0xFFFFFFFF.toInt()))
    }

    @Test
    fun `fence gating requires escapes and a terminal-ish language`() {
        assertTrue(shouldRenderAsAnsi("ansi", "${esc}[31mx"))
        assertTrue(shouldRenderAsAnsi("text", "${esc}[31mx"))
        assertTrue(shouldRenderAsAnsi("", "${esc}[31mx"))
        assertFalse(shouldRenderAsAnsi("kotlin", "${esc}[31mx"))
        assertFalse(shouldRenderAsAnsi("ansi", "no escapes here"))
    }

    @Test
    fun `oversized source is rejected before parsing`() {
        val huge = "${esc}[31m" + "x".repeat(MAX_ANSI_SOURCE_CHARS)
        assertFalse(shouldRenderAsAnsi("ansi", huge))
        assertTrue(ansiDocument(huge).truncated)
    }

    @Test
    fun `adjacent identical styles merge`() {
        val document = ansiDocument("${esc}[31ma${esc}[31mb")
        assertEquals(1, document.segments.size)
        assertEquals("ab", document.segments.single().text)
    }
}
