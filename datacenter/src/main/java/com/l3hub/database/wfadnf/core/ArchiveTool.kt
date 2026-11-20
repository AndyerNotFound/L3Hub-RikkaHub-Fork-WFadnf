package com.l3hub.database.wfadnf.core

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 备份归档的读写。
 *
 * 收发两端都按 L3Hub 原有的备份格式：数据库条目固定叫 `rikka_hub.db`，附件按 `upload/...` 这样的
 * 相对路径平铺在根下。目标侧的 `BackupManager.stageRestore` 与 `PendingDelta.stage` 认的就是这套名字。
 */
object ArchiveTool {
    const val DATABASE_ENTRY = "rikka_hub.db"

    fun extractDatabase(archive: File, destination: File) {
        destination.parentFile?.mkdirs()
        ZipFile(archive).use { zip ->
            val entry = zip.getEntry(DATABASE_ENTRY) ?: error("归档里没有数据库条目")
            zip.getInputStream(entry).use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            }
        }
    }

    /** 只带数据库的投递包；[files] 是相对路径到本地文件的映射，覆盖模式下一并带走附件 */
    fun buildDatabaseArchive(database: File, destination: File, files: Map<String, File> = emptyMap()) {
        destination.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(destination)).use { zip ->
            zip.putNextEntry(ZipEntry(DATABASE_ENTRY))
            database.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            for ((path, file) in files) {
                require(isSafeRelative(path)) { "非法的附件路径：$path" }
                zip.putNextEntry(ZipEntry(path))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun buildFilesArchive(mirrorDir: File, paths: List<String>, destination: File) {
        destination.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(destination)).use { zip ->
            for (path in paths) {
                if (!isSafeRelative(path)) continue
                val file = File(mirrorDir, path)
                if (!file.isFile) continue
                zip.putNextEntry(ZipEntry(path))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun extractFiles(archive: File, mirrorDir: File) {
        ZipFile(archive).use { zip ->
            for (entry in zip.entries()) {
                if (entry.isDirectory) continue
                if (!isSafeRelative(entry.name)) continue
                val target = File(mirrorDir, entry.name)
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
            }
        }
    }

    fun countFiles(directory: File): Pair<Int, Long> {
        if (!directory.isDirectory) return 0 to 0L
        var count = 0
        var bytes = 0L
        directory.walkTopDown().filter { it.isFile }.forEach {
            count++
            bytes += it.length()
        }
        return count to bytes
    }

    fun isSafeRelative(path: String): Boolean {
        if (path.isBlank() || path.startsWith('/') || path.startsWith('\\')) return false
        if ('\\' in path) return false
        return path.split('/').none { it.isEmpty() || it == "." || it == ".." }
    }
}
