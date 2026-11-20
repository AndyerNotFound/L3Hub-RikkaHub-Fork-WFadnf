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
