package me.rerere.rikkahub.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SyncManifestTest {
    @Test
    fun `only whitelisted folders count as syncable`() {
        assertTrue(isSyncablePath("upload/a.png"))
        assertTrue(isSyncablePath("skills/deep/a.md"))
        assertFalse(isSyncablePath("../upload/a.png"))
        assertFalse(isSyncablePath("/upload/a.png"))
        assertFalse(isSyncablePath("databases/rikka_hub"))
        assertFalse(isSyncablePath("upload/../databases/x"))
        assertFalse(isSyncablePath("upload\\a.png"))
        assertFalse(isSyncablePath("upload//a.png"))
    }

    @Test
    fun `files with the same size are not fetched again`() {
        val dir = tempDir()
        File(dir, "upload").mkdirs()
        File(dir, "upload/same.png").writeBytes(ByteArray(4))
        val manifest = SyncManifest(
            files = listOf(
                SyncManifest.FileEntry("upload/same.png", 4),
                SyncManifest.FileEntry("upload/new.png", 10),
                SyncManifest.FileEntry("upload/grown.png", 99),
            )
        )
        val plan = planFilesToFetch(manifest, dir)
        assertFalse(plan.full)
        assertEquals(listOf("upload/new.png", "upload/grown.png"), plan.paths)
    }

    @Test
    fun `too many missing files fall back to a full copy`() {
        val manifest = SyncManifest(
            files = (1..5).map { SyncManifest.FileEntry("upload/$it.png", 1) }
        )
        val plan = planFilesToFetch(manifest, tempDir(), maxPaths = 2)
        assertTrue(plan.full)
        assertTrue(plan.paths.isEmpty())
    }

    @Test
    fun `only files the peer is missing are sent`() {
        val local = listOf(
            SyncManifest.FileEntry("upload/a.png", 4),
            SyncManifest.FileEntry("upload/b.png", 5),
        )
        val remote = SyncManifest(files = listOf(SyncManifest.FileEntry("upload/a.png", 4)))
        val plan = planFilesToSend(local, remote)
        assertFalse(plan.full)
        assertEquals(listOf("upload/b.png"), plan.paths)
    }

    @Test
    fun `same path with a different size is sent again`() {
        val local = listOf(SyncManifest.FileEntry("upload/a.png", 8))
        val remote = SyncManifest(files = listOf(SyncManifest.FileEntry("upload/a.png", 4)))
        assertEquals(listOf("upload/a.png"), planFilesToSend(local, remote).paths)
    }

    private fun tempDir(): File =
        java.nio.file.Files.createTempDirectory("sync-manifest").toFile()
}
