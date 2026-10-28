package me.rerere.rikkahub.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class BackupPathsTest {
    private val filesDir = File("/data/user/0/com.rikkahub.l3hub.wfadnf/files")

    @Test
    fun `rewrites attachments from another package`() {
        val json = """{"background":"file:///data/user/0/me.rerere.rikkahub/files/upload/a.png"}"""
        assertEquals(
            """{"background":"file:///data/user/0/com.rikkahub.l3hub.wfadnf/files/upload/a.png"}""",
            BackupPaths.rewrite(json, filesDir),
        )
    }

    @Test
    fun `rewrites legacy data-data paths`() {
        assertEquals(
            "file:///data/user/0/com.rikkahub.l3hub.wfadnf/files/upload/a.jpg",
            BackupPaths.rewrite("file:///data/data/me.rerere.rikkahub.debug/files/upload/a.jpg", filesDir),
        )
    }

    @Test
    fun `rewrites bare paths used by fonts`() {
        assertEquals(
            "/data/user/0/com.rikkahub.l3hub.wfadnf/files/fonts/my.ttf",
            BackupPaths.rewrite("/data/user/0/me.rerere.rikkahub/files/fonts/my.ttf", filesDir),
        )
    }

    @Test
    fun `keeps unrelated uris untouched`() {
        val text = """["content://media/external/images/1","/storage/emulated/0/Download/a.png","https://x.com/a.png"]"""
        assertEquals(text, BackupPaths.rewrite(text, filesDir))
    }

    @Test
    fun `rewriting is idempotent`() {
        val once = BackupPaths.rewrite("file:///data/user/0/me.rerere.rikkahub/files/upload/a.png", filesDir)
        assertEquals(once, BackupPaths.rewrite(once, filesDir))
    }

    @Test
    fun `first prefix keeps the scheme`() {
        assertEquals(
            "file:///data/user/0/me.rerere.rikkahub/files/",
            BackupPaths.firstPrefix("\"file:///data/user/0/me.rerere.rikkahub/files/upload/a.png\""),
        )
        assertEquals(
            "/data/user/0/me.rerere.rikkahub/files/",
            BackupPaths.firstPrefix("\"/data/user/0/me.rerere.rikkahub/files/fonts/a.ttf\""),
        )
        assertNull(BackupPaths.firstPrefix("https://example.com/a.png"))
    }
}
