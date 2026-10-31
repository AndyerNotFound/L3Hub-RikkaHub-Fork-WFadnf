package me.rerere.rikkahub.data.ai.tools

import me.rerere.workspace.WorkspaceLayout
import me.rerere.workspace.WorkspaceStorageArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceSearchHelpersTest {

    private val layout = WorkspaceLayout(
        workspaceDir = "/workspace",
        writableRoots = listOf("/workspace"),
    )

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

    @Test
    fun `workspace paths map to the files area and rootfs paths to linux`() {
        assertEquals(WorkspaceStorageArea.FILES to "src/main", resolveStorageArea(layout, "/workspace/src/main/"))
        assertEquals(WorkspaceStorageArea.FILES to "", resolveStorageArea(layout, "/workspace"))
        assertEquals(WorkspaceStorageArea.LINUX to "root/.bashrc", resolveStorageArea(layout, "/root/.bashrc"))
        assertEquals(WorkspaceStorageArea.LINUX to "", resolveStorageArea(layout, "/"))
    }

    @Test
    fun `glob matches nested paths and bare file names`() {
        assertTrue(GlobMatcher("**/*.kt").matches("app/src/Main.kt", "Main.kt"))
        assertTrue(GlobMatcher("*.kt").matches("app/src/Main.kt", "Main.kt"))
        assertFalse(GlobMatcher("*.kt").matches("app/src/Main.java", "Main.java"))
        assertFalse(GlobMatcher("[").isValid)
    }
}
