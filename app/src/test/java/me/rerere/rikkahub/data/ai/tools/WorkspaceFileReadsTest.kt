package me.rerere.rikkahub.data.ai.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFileReadsTest {
    @Test
    fun `page respects offset and limit`() {
        val text = (1..100).joinToString("\n")
        val page = pageText(text, offset = 10, limit = 5)
        assertEquals("10\n11\n12\n13\n14", page.text)
        assertEquals(10, page.startLine)
        assertEquals(14, page.endLine)
        assertEquals(100, page.totalLines)
        assertTrue(page.truncated)
    }

    @Test
    fun `offset beyond the end returns empty text`() {
        val page = pageText("a\nb", offset = 50, limit = 10)
        assertEquals("", page.text)
        assertEquals(2, page.totalLines)
        assertFalse(page.truncated)
    }

    @Test
    fun `trailing newline does not add an extra line`() {
        val page = pageText("a\nb\n", offset = 1, limit = 10)
        assertEquals("a\nb", page.text)
        assertEquals(2, page.totalLines)
        assertFalse(page.truncated)
    }

    @Test
    fun `single line and empty files`() {
        assertEquals("only", pageText("only", 1, 10).text)
        val empty = pageText("", 1, 10)
        assertEquals("", empty.text)
        assertEquals(0, empty.totalLines)
    }
}
