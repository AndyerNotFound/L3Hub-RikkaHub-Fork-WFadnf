package me.rerere.rikkahub.data.sync

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.MediaCreationFiles
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID

/**
 * Staged restores live outside cache and are installed before any database/settings consumers start.
 * The journal and atomic moves allow an interrupted installation to resume on the next launch.
 *
 * 恢复方式也一起存在待恢复目录里：[RestoreMode.OVERWRITE] 按原样整体替换所选数据，
 * [RestoreMode.MERGE] 只往当前数据里补入缺少的部分（文件不覆盖同名、聊天记录按主键补齐、设置取并集）。
 */
internal class PendingRestore(
    private val root: File,
    private val databaseFile: File,
    private val filesDir: File,
    private val context: Context? = null,
) {
    private val pending get() = File(root, "pending")

    fun createStagingDirectory(): File {
        check(!pending.exists()) { "A backup restore is already pending. Restart the app first." }
        check(root.isDirectory || root.mkdirs()) { "Cannot create backup staging directory" }
        return Files.createTempDirectory(root.toPath(), "preparing-").toFile()
    }

    fun publish(staging: File, options: RestoreOptions) {
        check(!pending.exists()) { "A backup restore is already pending. Restart the app first." }
        writeDurably(File(staging, OPTIONS_FILE), Json.encodeToString(options))
        move(staging, pending)
    }

    suspend fun apply(restoreSettings: suspend (String, RestoreMode) -> Unit): Boolean {
        // Completed restores must never be replayed, even if cleanup was interrupted.
        root.listFiles()?.filter {
            it.name.startsWith("completed-") || it.name.startsWith("preparing-")
        }?.forEach { it.deleteRecursively() }
        if (!pending.isDirectory) return false

        val options = readOptions()
        val journal = File(pending, "journal.json")
        val entries = if (journal.exists()) {
            // An existing journal may describe an interrupted installation; keep it if reading fails.
            Json.decodeFromString<List<RestoreEntry>>(journal.readText())
        } else {
            try {
                buildEntries(options).also { writeDurably(journal, Json.encodeToString(it)) }
            } catch (e: Exception) {
                // No live files have been changed yet, so this restore can be safely rejected.
                move(pending, File(root, "failed-${UUID.randomUUID()}"))
                throw RestoreFailedException(e)
            }
        }
        try {
            entries.forEach { entry -> install(entry) }
            if (options.mode == RestoreMode.MERGE) mergeDatabase()
            val settings = File(pending, "settings.json")
            if (settings.isFile) restoreSettings(settings.readText(), options.mode)
        } catch (e: Exception) {
            try {
                rollback(entries)
            } catch (rollbackError: Exception) {
                e.addSuppressed(rollbackError)
                // Do not start the app with a partially restored database. Keep the journal for retry.
                throw e
            }
            move(pending, File(root, "failed-${UUID.randomUUID()}"))
            throw RestoreFailedException(e)
        }

        // Commit by removing the pending name atomically before any live connection can be opened.
        // If this move fails, startup aborts and the next launch retries the same installation.
        val completed = File(root, "completed-${UUID.randomUUID()}")
        move(pending, completed)
        completed.deleteRecursively()
        return true
    }

    private fun install(entry: RestoreEntry) {
        val target = targetFile(entry.path)
        val original = File(pending, "originals/${entry.path}")
        val source = File(pending, "payload/${entry.path}")
        if (entry.install && !source.exists()) {
            // A previous process already moved this source into place.
            check(target.isFile) { "Interrupted restore is missing ${entry.path}" }
            return
        }
        if (entry.hadOriginal && !original.exists() && target.exists()) move(target, original)
        if (entry.install) move(source, target)
    }

    /**
     * 合并模式下不替换数据库文件，而是把备份库里缺少的行补进现有库。
     * 现有库还不存在（全新安装）时，上面已经把备份库整份装进去了，这里不用再做。
     */
    private fun mergeDatabase() {
        val staged = File(pending, "payload/database/${databaseFile.name}")
        if (!staged.isFile || databaseFile.length() == 0L) return
        val appContext = requireNotNull(context) { "Database merge requires an application context" }
        DatabaseMerge.merge(appContext, databaseFile, staged)
    }

    private fun buildEntries(options: RestoreOptions): List<RestoreEntry> {
        val payload = File(pending, "payload")
        val paths = payload.walkTopDown().filter { it.isFile }.map {
            it.relativeTo(payload).invariantSeparatorsPath
        }.toList().sorted()
        // 合并模式下只有当前还没有数据库时才整份装入，否则交给 DatabaseMerge 增量补齐
        val installDatabase = options.mode == RestoreMode.OVERWRITE ||
            (options.mode == RestoreMode.MERGE && databaseFile.length() == 0L)
        val entries = paths.map { path ->
            val install = when {
                path.startsWith("database/") -> installDatabase
                options.mode == RestoreMode.OVERWRITE -> true
                // 合并只补缺少的文件；空文件多半是上次写入中断留下的，也当作缺
                else -> !targetFile(path).exists() || targetFile(path).length() == 0L
            }
            RestoreEntry(
                path = path,
                install = install,
                hadOriginal = install && targetFile(path).exists(),
            )
        }.toMutableList()
        if (installDatabase && paths.contains("database/${databaseFile.name}")) {
            // The new database is standalone. Keep the old DB's sidecars with the old DB only.
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                val path = "database/${databaseFile.name}$suffix"
                entries.add(0, RestoreEntry(path, install = false, hadOriginal = targetFile(path).exists()))
            }
        }
        if (options.mode == RestoreMode.OVERWRITE && RestoreItem.FILES in options.items) {
            entries += removalEntries(paths.toSet())
        }
        return entries
    }

    /**
     * 覆盖模式下，备份里没有的资源文件也要清掉，才算真的「用备份替换当前数据」。
     * 它们会被挪进 originals，失败回滚时原样放回。媒体创作里还没下完的分片保留。
     */
    private fun removalEntries(payloadPaths: Set<String>): List<RestoreEntry> {
        val entries = mutableListOf<RestoreEntry>()
        for (folder in BackupManager.ATTACHMENT_FOLDERS) {
            val directory = File(filesDir, folder)
            if (!directory.isDirectory) continue
            val files = if (folder in BackupManager.NESTED_ATTACHMENT_FOLDERS) {
                directory.walkTopDown()
            } else {
                directory.listFiles().orEmpty().asSequence()
            }
            for (file in files) {
                if (!file.isFile) continue
                if (folder == FileFolders.MEDIA_CREATION &&
                    file.name.endsWith(MediaCreationFiles.PARTIAL_SUFFIX)
                ) {
                    continue
                }
                val relative = file.relativeTo(directory).invariantSeparatorsPath
                val path = "files/$folder/$relative"
                if (path in payloadPaths) continue
                entries += RestoreEntry(path, install = false, hadOriginal = true)
            }
        }
        return entries
    }

    private fun rollback(entries: List<RestoreEntry>) {
        entries.asReversed().forEach { entry ->
            val target = targetFile(entry.path)
            val original = File(pending, "originals/${entry.path}")
            val source = File(pending, "payload/${entry.path}")
            if (entry.install && !source.exists() && target.exists()) move(target, source)
            if (original.exists()) move(original, target)
        }
    }

    private fun readOptions(): RestoreOptions {
        val file = File(pending, OPTIONS_FILE)
        if (!file.isFile) return RestoreOptions()
        return runCatching { Json.decodeFromString<RestoreOptions>(file.readText()) }
            .getOrDefault(RestoreOptions())
    }

    private fun targetFile(path: String): File {
        return when {
            path.startsWith("database/") -> {
                val name = path.removePrefix("database/")
                require(name in listOf(
                    databaseFile.name, databaseFile.name + "-wal",
                    databaseFile.name + "-shm", databaseFile.name + "-journal"
                )) {
                    "Invalid restore database path"
                }
                File(databaseFile.parentFile, name)
            }
            path.startsWith("files/") -> resolveInside(filesDir, path.removePrefix("files/"))
            else -> error("Invalid restore path: $path")
        }
    }

    @Serializable
    private data class RestoreEntry(val path: String, val install: Boolean, val hadOriginal: Boolean)

    companion object {
        private const val OPTIONS_FILE = "options.json"

        fun resolveInside(root: File, relativePath: String): File {
            require(relativePath.isNotBlank() && !relativePath.startsWith('/') &&
                '\\' !in relativePath && relativePath.split('/').none { it == ".." || it == "." }) {
                "Invalid backup file path: $relativePath"
            }
            val target = File(root, relativePath).canonicalFile
            require(target.path.startsWith(root.canonicalPath + File.separator)) {
                "Backup file is outside its target directory"
            }
            return target
        }

        fun writeDurably(file: File, text: String) {
            val temporary = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(temporary).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            move(temporary, file)
        }

        private fun move(source: File, target: File) {
            val parent = requireNotNull(target.parentFile)
            check(parent.isDirectory || parent.mkdirs()) { "Cannot create restore directory" }
            Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        }
    }
}

/** Only thrown when original files are intact and the failed import has been isolated. */
internal class RestoreFailedException(cause: Exception) : Exception("Backup restore failed; original files restored", cause)
