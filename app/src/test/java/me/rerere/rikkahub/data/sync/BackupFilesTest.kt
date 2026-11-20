package me.rerere.rikkahub.data.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/**
 * 目录里混着软链时的备份与恢复：只认真实文件。
 *
 * 软链可能指向目录之外（恢复端也写不回原位置），也可能成环；以前这两种情况都会让整份备份导出失败。
 */
class BackupFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun write(file: File, contents: String): File {
        file.parentFile!!.mkdirs()
        file.writeText(contents)
        return file
    }

    private fun link(target: File, link: File): Boolean {
        link.parentFile?.mkdirs()
        return runCatching { Files.createSymbolicLink(link.toPath(), target.toPath()) }.isSuccess
    }

    @Test fun nestedWalkKeepsRealFilesAndSkipsSymbolicLinks() {
        val skills = File(temporary.root, "skills")
        write(File(skills, "probe/SKILL.md"), "probe")
        write(File(skills, "probe/scripts/run.sh"), "run")
        val settings = write(File(temporary.root, "datastore/settings.preferences_pb"), "settings")
        assumeTrue("需要软链支持", link(temporary.root, File(skills, ".probe-files")))
        assumeTrue("需要软链支持", link(settings, File(skills, "probe/settings.link")))

        val walked = BackupManager.backupFiles(skills, nested = true)
            .map { it.relativeTo(skills).invariantSeparatorsPath }
            .toList()

        assertEquals(listOf("probe/SKILL.md", "probe/scripts/run.sh"), walked)
    }

    @Test fun nestedWalkDoesNotLoopOnASymbolicLinkBackToTheRoot() {
        val skills = File(temporary.root, "skills")
        write(File(skills, "probe/SKILL.md"), "probe")
        assumeTrue("需要软链支持", link(skills, File(skills, "loop")))

        val walked = BackupManager.backupFiles(skills, nested = true)
            .map { it.relativeTo(skills).invariantSeparatorsPath }
            .toList()

        assertEquals(listOf("probe/SKILL.md"), walked)
    }

    @Test fun topLevelFoldersOnlyTakeTheirOwnFiles() {
        val upload = File(temporary.root, "upload")
        write(File(upload, "photo.jpg"), "photo")
        write(File(upload, "nested/ignored.txt"), "ignored")

        val walked = BackupManager.backupFiles(upload, nested = false)
            .map { it.name }
            .toList()

        assertEquals(listOf("photo.jpg"), walked)
    }

    @Test fun restoreSkipsPayloadThatWouldLandThroughASymbolicLink() = runBlocking {
        val files = File(temporary.root, "files")
        write(File(files, "skills/probe/SKILL.md"), "old")
        // 技能目录里指向 files 根的软链：payload 里的 skills/escape/... 本会落到 files/datastore/...
        assumeTrue("需要软链支持", link(files, File(files, "skills/escape")))

        val restore = PendingRestore(
            root = File(temporary.root, "restore"),
            databaseFile = File(temporary.root, "databases/rikka_hub"),
            filesDir = files,
        )
        val staging = restore.createStagingDirectory()
        write(File(staging, "payload/files/skills/escape/datastore/settings.preferences_pb"), "hijacked")
        write(File(staging, "payload/files/skills/probe/SKILL.md"), "new")
        restore.publish(
            staging,
            RestoreOptions(items = setOf(RestoreItem.FILES), mode = RestoreMode.OVERWRITE),
        )

        val applied = restore.apply { _, _ -> }

        assertTrue(applied)
        assertEquals("new", File(files, "skills/probe/SKILL.md").readText())
        assertFalse(File(files, "datastore/settings.preferences_pb").exists())
    }
}
