package me.rerere.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HostPathsTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `relativeTo keeps the boundary between siblings`() {
        val root = tempFolder.newFolder("files")
        val sibling = tempFolder.newFolder("files-other")
        val nested = File(root, "a/b.txt").apply { parentFile?.mkdirs(); writeText("x") }

        assertEquals("", HostPaths.relativeTo(root, root))
        assertEquals("a/b.txt", HostPaths.relativeTo(root, nested))
        assertNull(HostPaths.relativeTo(root, sibling))
        assertFalse(HostPaths.contains(root, sibling))
        assertTrue(HostPaths.contains(root, nested))
    }

    @Test
    fun `resolve rejects traversal and accepts nested paths`() {
        val root = tempFolder.newFolder("root")
        val nested = File(root, "a/b.txt").apply { parentFile?.mkdirs(); writeText("x") }

        assertEquals(nested.canonicalFile, HostPaths.resolve(root, "a/b.txt"))
        assertEquals(root.canonicalFile, HostPaths.resolve(root, ""))
        assertNull(HostPaths.resolve(root, "../escape.txt"))
        assertNull(HostPaths.resolve(root, "a/../../escape.txt"))
        assertNull(HostPaths.resolve(root, "a/\u0000b"))
    }
}
