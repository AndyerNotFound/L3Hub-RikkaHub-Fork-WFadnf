package me.rerere.rikkahub.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/**
 * Kelivo 导出的附件来源判定与归档命名。
 *
 * 两条历史问题各对应一组用例：`file://` 曾经不限目录（导入恶意备份后能把设置、数据库、
 * 共享存储里的任意可读文件带走），`content://` 曾经直接拿 DISPLAY_NAME 当落盘文件名
 * （`../../` 写穿缓存目录，同名的还会互相覆盖）。
 */
class KelivoAttachmentPathsTest {
    @get:Rule val temporary = TemporaryFolder()

    private val filesDir: File get() = File(temporary.root, "files")

    private fun write(file: File, contents: String = "x"): File {
        file.parentFile!!.mkdirs()
        file.writeText(contents)
        return file
    }

    private fun link(target: File, link: File): Boolean {
        link.parentFile?.mkdirs()
        return runCatching { Files.createSymbolicLink(link.toPath(), target.toPath()) }.isSuccess
    }

    // ---- file:// 来源白名单 ----

    @Test fun `keeps attachments from whitelisted folders`() {
        val photo = write(File(filesDir, "upload/photo.png"))
        val image = write(File(filesDir, "images/generated.webp"))
        val font = write(File(filesDir, "fonts/custom.ttf"))
        val avatar = write(File(filesDir, "avatars/me.mp4"))

        assertEquals(photo.canonicalFile, KelivoAttachmentPaths.sourceFile(filesDir, photo.path))
        assertEquals(image.canonicalFile, KelivoAttachmentPaths.sourceFile(filesDir, image.path))
        assertEquals(font.canonicalFile, KelivoAttachmentPaths.sourceFile(filesDir, font.path))
        assertEquals(avatar.canonicalFile, KelivoAttachmentPaths.sourceFile(filesDir, avatar.path))
    }

    @Test fun `keeps nested skill and media files`() {
        val skill = write(File(filesDir, "skills/probe/scripts/run.sh"))
        val media = write(File(filesDir, "media_creation/session-1/clip.mp4"))

        assertEquals(skill.canonicalFile, KelivoAttachmentPaths.sourceFile(filesDir, skill.path))
        assertEquals(media.canonicalFile, KelivoAttachmentPaths.sourceFile(filesDir, media.path))
        // 这两个目录不在归档保留列表里，条目不跟着目录走
        assertEquals("upload", KelivoAttachmentPaths.archiveFolder("skills/probe/scripts/run.sh"))
        assertEquals("upload", KelivoAttachmentPaths.archiveFolder("media_creation/session-1/clip.mp4"))
    }

    @Test fun `rejects files outside the whitelist`() {
        val settings = write(File(filesDir, "datastore/settings.preferences_pb"))
        val database = write(File(temporary.root, "databases/rikka_hub"))
        val shared = write(File(temporary.root, "shared/Download/report.pdf"))
        val workspace = write(File(filesDir, "workspaces/notes.md"))

        assertNull(KelivoAttachmentPaths.sourceFile(filesDir, settings.path))
        assertNull(KelivoAttachmentPaths.sourceFile(filesDir, database.path))
        assertNull(KelivoAttachmentPaths.sourceFile(filesDir, shared.path))
        assertNull(KelivoAttachmentPaths.sourceFile(filesDir, workspace.path))
    }

    @Test fun `rejects traversal and relative paths`() {
        write(File(filesDir, "upload/photo.png"))
        write(File(filesDir, "datastore/settings.preferences_pb"))

        val throughParent = "${filesDir.path}/upload/../datastore/settings.preferences_pb"
        assertNull(KelivoAttachmentPaths.sourceFile(filesDir, throughParent))
        assertNull(KelivoAttachmentPaths.sourceFile(filesDir, "upload/photo.png"))
        assertNull(KelivoAttachmentPaths.sourceFile(filesDir, "/data/data/com.termux/files/usr/bin/bash"))
        assertNull(KelivoAttachmentPaths.sourceFile(filesDir, "upload/photo\u0000.png"))
        assertNull(KelivoAttachmentPaths.sourceFile(filesDir, null))
        assertNull(KelivoAttachmentPaths.sourceFile(filesDir, ""))
    }

    @Test fun `rejects a symbolic link that points outside the whitelist`() {
        val settings = write(File(filesDir, "datastore/settings.preferences_pb"), "secret")
        val escape = File(filesDir, "upload/leak.png")
        assumeTrue("需要软链支持", link(settings, escape))

        assertNull(KelivoAttachmentPaths.sourceFile(filesDir, escape.path))
    }

    @Test fun `rejects a symbolic link from a whitelisted folder to shared storage`() {
        val shared = write(File(temporary.root, "shared/Download/report.pdf"), "shared")
        val escape = File(filesDir, "images/report.pdf")
        assumeTrue("需要软链支持", link(shared, escape))

        assertNull(KelivoAttachmentPaths.sourceFile(filesDir, escape.path))
    }

    @Test fun `accepts a symbolic link that stays inside the whitelist`() {
        val photo = write(File(filesDir, "upload/photo.png"))
        val alias = File(filesDir, "images/alias.png")
        assumeTrue("需要软链支持", link(photo, alias))

        assertEquals(photo.canonicalFile, KelivoAttachmentPaths.sourceFile(filesDir, alias.path))
    }

    // ---- content:// 落盘命名 ----

    @Test fun `generated cache name never carries the provider display name`() {
        val cacheDir = File(temporary.root, "cache")
        val target = requireNotNull(
            KelivoAttachmentPaths.cacheTarget(
                cacheDir,
                "../../../datastore/settings.preferences_pb",
                "application/octet-stream",
            )
        )

        assertEquals(KelivoAttachmentPaths.cacheDirectory(cacheDir), target.parentFile)
        assertFalse(target.name.contains('/'))
        assertFalse(target.name.contains(".."))
        assertFalse(target.name.contains("settings"))
    }

    @Test fun `absolute display name cannot escape the cache directory`() {
        val cacheDir = File(temporary.root, "cache")
        val target = requireNotNull(
            KelivoAttachmentPaths.cacheTarget(
                cacheDir,
                "/data/data/com.rikkahub.l3hub.wfadnf/files/datastore/settings.preferences_pb",
                "application/octet-stream",
            )
        )

        assertEquals(KelivoAttachmentPaths.cacheDirectory(cacheDir), target.parentFile)
    }

    @Test fun `cache directory cannot be a symbolic link outside cache`() {
        val cacheDir = File(temporary.root, "cache").apply { mkdirs() }
        val outside = File(temporary.root, "private").apply { mkdirs() }
        val directory = KelivoAttachmentPaths.cacheDirectory(cacheDir)
        assumeTrue("需要软链支持", link(outside, directory))

        assertNull(KelivoAttachmentPaths.cacheTarget(cacheDir, "photo.png", "image/png"))
        assertTrue(outside.listFiles().orEmpty().isEmpty())
    }

    @Test fun `cache name keeps a usable extension`() {
        val cacheDir = File(temporary.root, "cache")

        fun cachedName(name: String, mime: String?): String =
            requireNotNull(KelivoAttachmentPaths.cacheTarget(cacheDir, name, mime)).name

        assertTrue(cachedName("holiday photo.JPG", "image/jpeg").endsWith(".jpg"))
        assertTrue(cachedName("clip", "video/mp4").endsWith(".mp4"))
        assertTrue(cachedName("weird", "application/octet-stream").endsWith(".bin"))
        // 扩展名只认字母数字，`a.b/c` 这类串里带出来的片段不会被当成扩展名
        assertTrue(cachedName("a.b/c", "application/octet-stream").endsWith(".bin"))
    }

    @Test fun `cache targets of the same display name do not collide`() {
        val cacheDir = File(temporary.root, "cache")
        val first = requireNotNull(KelivoAttachmentPaths.cacheTarget(cacheDir, "photo.png", "image/png"))
        val second = requireNotNull(KelivoAttachmentPaths.cacheTarget(cacheDir, "photo.png", "image/png"))

        assertTrue(first.name != second.name)
    }

    @Test fun `cache token is stable per uri and distinct between uris`() {
        val same = KelivoAttachmentPaths.cacheToken("content://media/external/images/1")
        assertEquals(same, KelivoAttachmentPaths.cacheToken("content://media/external/images/1"))
        assertFalse(same == KelivoAttachmentPaths.cacheToken("content://media/external/images/2"))

        val cacheDir = File(temporary.root, "cache")
        val first = requireNotNull(
            KelivoAttachmentPaths.cacheTarget(cacheDir, "photo.png", "image/png", same)
        )
        val second = requireNotNull(
            KelivoAttachmentPaths.cacheTarget(cacheDir, "photo.png", "image/png", same)
        )
        assertEquals(first.name, second.name)
    }

    @Test fun `cache token cannot be talked into a path`() {
        val cacheDir = File(temporary.root, "cache")
        val target = requireNotNull(
            KelivoAttachmentPaths.cacheTarget(
                cacheDir,
                "photo.png",
                "image/png",
                "../../datastore/settings.preferences_pb",
            )
        )

        assertEquals(KelivoAttachmentPaths.cacheDirectory(cacheDir), target.parentFile)
        assertFalse(target.name.contains(".."))
        assertFalse(target.name.contains('/'))
    }

    // ---- 归档条目与展示名 ----

    @Test fun `archive names are rebuilt from a single safe segment`() {
        assertEquals("photo.png", KelivoAttachmentPaths.sanitizeSegment("../../photo.png"))
        assertEquals("photo.png", KelivoAttachmentPaths.sanitizeSegment("..\\..\\photo.png"))
        assertEquals("photo.png", KelivoAttachmentPaths.sanitizeSegment("/data/data/x/files/upload/photo.png"))
        assertEquals("file", KelivoAttachmentPaths.sanitizeSegment(".."))
        assertEquals("file", KelivoAttachmentPaths.sanitizeSegment(""))
        assertEquals("file", KelivoAttachmentPaths.sanitizeSegment(null))
        assertFalse(KelivoAttachmentPaths.sanitizeSegment("....//....//x").startsWith("."))
    }

    @Test fun `entry names and kelivo uris stay inside their folder`() {
        val entry = KelivoAttachmentPaths.entryName("upload", "../../escape.png")
        val uri = KelivoAttachmentPaths.kelivoUri(entry)

        assertEquals("upload/escape.png", entry)
        assertEquals("kelivo-file:///upload/escape.png", uri)
        assertFalse(entry.split('/').any { it == ".." || it == "." || it.isEmpty() })
    }

    @Test fun `entry names make room for same named sources`() {
        assertEquals("upload/a.md", KelivoAttachmentPaths.entryName("upload", "a.md"))
        assertEquals("upload/a-1.md", KelivoAttachmentPaths.entryName("upload", "a.md", 1))
        assertEquals("upload/a-2.md", KelivoAttachmentPaths.entryName("upload", "a.md", 2))
        // 没有扩展名时序号直接缀在末尾
        assertEquals("upload/a-1", KelivoAttachmentPaths.entryName("upload", "a", 1))
        // 以点开头的名字被清成普通名字，序号仍然只作用在一段之内
        assertEquals("upload/hidden-1.png", KelivoAttachmentPaths.entryName("upload", ".hidden.png", 1))
    }

    @Test fun `display names keep the readable file name`() {
        assertEquals("季度报告.pdf", KelivoAttachmentPaths.displayName("季度报告.pdf"))
        assertEquals("report.pdf", KelivoAttachmentPaths.displayName("../../../report.pdf"))
        assertEquals("file", KelivoAttachmentPaths.displayName("  "))
    }

    @Test fun `archive folder falls back to upload for non archived roots`() {
        assertEquals("upload", KelivoAttachmentPaths.archiveFolder("upload/a.png"))
        assertEquals("images", KelivoAttachmentPaths.archiveFolder("images/a.png"))
        assertEquals("fonts", KelivoAttachmentPaths.archiveFolder("fonts/a.ttf"))
        assertEquals("avatars", KelivoAttachmentPaths.archiveFolder("avatars/a.mp4"))
        assertEquals("upload", KelivoAttachmentPaths.archiveFolder("skills/a.md"))
        assertEquals("upload", KelivoAttachmentPaths.archiveFolder("datastore/settings.preferences_pb"))
        assertEquals("upload", KelivoAttachmentPaths.archiveFolder(null))
    }

    @Test fun `entry names are validated before they reach the zip`() {
        assertTrue(KelivoAttachmentPaths.isSafeEntryName("upload/a.png"))
        assertTrue(KelivoAttachmentPaths.isSafeEntryName("images/generated.webp"))
        assertFalse(KelivoAttachmentPaths.isSafeEntryName("upload/../datastore/settings.preferences_pb"))
        assertFalse(KelivoAttachmentPaths.isSafeEntryName("/upload/a.png"))
        assertFalse(KelivoAttachmentPaths.isSafeEntryName("upload\\a.png"))
        assertFalse(KelivoAttachmentPaths.isSafeEntryName("upload//a.png"))
        assertFalse(KelivoAttachmentPaths.isSafeEntryName("./a.png"))
        assertFalse(KelivoAttachmentPaths.isSafeEntryName(""))
    }

    @Test fun `the same file reuses its entry while different files make room`() {
        val first = write(File(filesDir, "skills/a/report.md"))
        val second = write(File(filesDir, "skills/b/report.md"))
        val taken = linkedMapOf<String, File>()

        val firstEntry = requireNotNull(
            KelivoAttachmentPaths.entryNameFor("upload", "report.md", first) { taken[it] }
        )
        assertEquals("upload/report.md", firstEntry)
        taken[firstEntry] = first

        val secondEntry = requireNotNull(
            KelivoAttachmentPaths.entryNameFor("upload", "report.md", second) { taken[it] }
        )
        assertEquals("upload/report-1.md", secondEntry)
        taken[secondEntry] = second

        assertEquals(
            "upload/report.md",
            KelivoAttachmentPaths.entryNameFor("upload", "report.md", first) { taken[it] },
        )
        assertEquals(2, taken.size)
    }

    @Test fun `entry name lookup moves aside for an occupied sanitized name`() {
        val file = write(File(filesDir, "upload/photo.png"))
        val taken = mapOf("upload/file" to write(File(filesDir, "upload/other.png")))

        // `..` 清洗后是 `file`，与已占用条目同名但源文件不同，于是让到 `file-1`
        assertEquals(
            "upload/file-1",
            KelivoAttachmentPaths.entryNameFor("upload", "..", file) { taken[it] },
        )
    }
}
