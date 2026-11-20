package me.rerere.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspacePathsTest {
    @Test
    fun `normalize folds dot segments and duplicate slashes`() {
        assertEquals("/workspace/a/b", WorkspacePaths.normalize("/workspace/./a//b/"))
        assertEquals("/workspace/b", WorkspacePaths.normalize("/workspace/a/../b"))
        assertEquals("/", WorkspacePaths.normalize("//"))
        assertEquals("/workspace/a", WorkspacePaths.normalize(" /workspace/a "))
        assertEquals("/workspace/a", WorkspacePaths.normalize("\\workspace\\a"))
    }

    @Test
    fun `normalize rejects relative paths and escapes above the root`() {
        assertNull(WorkspacePaths.normalize("workspace/a"))
        assertNull(WorkspacePaths.normalize(""))
        assertNull(WorkspacePaths.normalize("/.."))
        assertNull(WorkspacePaths.normalize("/workspace/../.."))
        assertNull(WorkspacePaths.normalize("/workspace/\u0000a"))
    }

    @Test
    fun `join keeps relative paths inside the base`() {
        assertEquals("/workspace", WorkspacePaths.join("/workspace", ""))
        assertEquals("/workspace/src", WorkspacePaths.join("/workspace", "src/"))
        assertEquals("/workspace/a/b", WorkspacePaths.join("/workspace", "a/./b"))
        assertNull(WorkspacePaths.join("/workspace", "../etc/passwd"))
        assertNull(WorkspacePaths.join("/workspace", "a/../../etc"))
    }

    @Test
    fun `join treats a leading slash as relative`() {
        assertEquals("/workspace/etc/hostname", WorkspacePaths.join("/workspace", "/etc/hostname"))
        assertEquals("/etc/hostname", WorkspacePaths.join("/", "etc/hostname"))
        assertNull(WorkspacePaths.join("/workspace", "/../etc/hostname"))
    }

    @Test
    fun `relativeTo respects directory boundaries`() {
        assertEquals("", WorkspacePaths.relativeTo("/workspace", "/workspace"))
        assertEquals("src/a.kt", WorkspacePaths.relativeTo("/workspace", "/workspace/src/a.kt"))
        assertNull(WorkspacePaths.relativeTo("/workspace", "/workspace-other/a.kt"))
        assertNull(WorkspacePaths.relativeTo("/workspace", "src/a.kt"))
        assertEquals("etc/hostname", WorkspacePaths.relativeTo("/", "/etc/hostname"))
    }

    @Test
    fun `contains does not treat a sibling prefix as a child`() {
        assertTrue(WorkspacePaths.contains("/workspace", "/workspace/a"))
        assertTrue(WorkspacePaths.contains("/workspace", "/workspace"))
        assertFalse(WorkspacePaths.contains("/workspace", "/workspace-other"))
        assertFalse(WorkspacePaths.contains("/workspace", "/workspace/../etc"))
    }
}
