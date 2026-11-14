package me.rerere.rikkahub.ui.components.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditedPathTreeTest {

    @Test
    fun `single file collapses its whole parent chain into one row`() {
        val tree = buildEditedPathTree(listOf("/a/b/c/d.kt"))

        assertTrue(tree.files.isEmpty())
        val node = tree.dirs.single()
        assertEquals("/a/b/c", node.path)
        assertEquals(listOf("/a/b/c/d.kt"), node.files)
        assertTrue(node.dirs.isEmpty())
    }

    @Test
    fun `files in the same directory share one row`() {
        val tree = buildEditedPathTree(listOf("/a/b/d.kt", "/a/b/c.kt"))

        assertEquals(listOf("/a/b"), tree.dirs.map { it.path })
        assertEquals(listOf("/a/b/c.kt", "/a/b/d.kt"), tree.dirs.single().files)
    }

    @Test
    fun `sibling directories split at the divergence point`() {
        val tree = buildEditedPathTree(listOf("/a/b/c.kt", "/a/d/e.kt"))

        val root = tree.dirs.single()
        assertEquals("/a", root.path)
        assertTrue(root.files.isEmpty())
        assertEquals(listOf("/a/b", "/a/d"), root.dirs.map { it.path })
        assertEquals(listOf("/a/b/c.kt"), root.dirs.first().files)
        assertEquals(listOf("/a/d/e.kt"), root.dirs.last().files)
    }

    @Test
    fun `files next to a subdirectory are listed under their own directory`() {
        val tree = buildEditedPathTree(listOf("/a/b/c.kt", "/a/b/sub/d.kt"))

        val root = tree.dirs.single()
        assertEquals("/a/b", root.path)
        assertEquals(listOf("/a/b/c.kt"), root.files)
        assertEquals(listOf("/a/b/sub"), root.dirs.map { it.path })
        assertEquals(listOf("/a/b/sub/d.kt"), root.dirs.single().files)
    }

    @Test
    fun `duplicate paths are listed once`() {
        val tree = buildEditedPathTree(listOf("/a/b/c.kt", "/a/b/c.kt"))

        assertEquals(listOf("/a/b/c.kt"), tree.dirs.single().files)
    }

    @Test
    fun `paths without a parent stay at the top level`() {
        val tree = buildEditedPathTree(listOf("notes.md", "/tmp/x.txt"))

        assertEquals(listOf("notes.md"), tree.files)
        assertEquals(listOf("/tmp"), tree.dirs.map { it.path })
        assertEquals(listOf("/tmp/x.txt"), tree.dirs.single().files)
    }
}
