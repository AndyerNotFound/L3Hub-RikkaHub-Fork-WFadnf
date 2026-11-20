package me.rerere.rikkahub.data.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.SQLiteConfiguration
import me.rerere.rikkahub.utils.AppLanguage
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID
import java.util.zip.ZipFile

private const val TAG = "PendingDelta"

/**
 * 增量同步的待安装目录，与 [PendingRestore] 平行的另一条路。
 *
 * 它只装增量（会话、记忆、工作区、删除墓碑与缺的那部分附件），不替换整份数据库，所以不需要
 * payload/database/files 那一套结构；但仍在下次启动、Room 打开之前安装——运行中的
 * [me.rerere.rikkahub.service.ConversationSession] 以内存状态为权威，边跑边合并会被它下一次
 * flush 整份覆盖回去。
 *
 * 增量是幂等的：会话按主键补缺、对端较新的整条替换，文件按相对路径覆盖，重复安装同一份增量
 * 不会产生第二份数据。
 */
object PendingDelta {
    /** 增量包里的数据库文件名 */
    const val DELTA_ENTRY = SyncDelta.FILE_NAME

    /** 增量包里的设置文件名，与整份备份保持一致 */
    const val SETTINGS_ENTRY = "settings.json"

    private const val ROOT_DIR = "version-sync"
    private const val OPTIONS_FILE = "options.json"

    fun hasPending(context: Context): Boolean = File(rootOf(context), "pending").isDirectory

    /**
     * 把对端的两份归档放进待安装目录：一份是增量库（可含 settings.json），另一份只含本次要补的附件。
     * 归档先全部落到临时目录并校验，最后原子改名为 pending，中途失败不会留下半份增量。
     */
    suspend fun stage(
        context: Context,
        deltaArchive: File,
        filesArchive: File?,
        options: RestoreOptions,
    ): RestoreStageResult = withContext(Dispatchers.IO) {
        val root = rootOf(context)
        check(root.isDirectory || root.mkdirs()) { "Cannot create the delta staging directory" }
        check(!File(root, "pending").exists()) {
            "A sync is already pending. Restart the app first."
        }
        val staging = Files.createTempDirectory(root.toPath(), "preparing-").toFile()
        try {
            var hasDatabase = false
            var hasSettings = false
            extract(deltaArchive, staging) { name ->
                when (name) {
                    DELTA_ENTRY -> hasDatabase = true
                    SETTINGS_ENTRY -> hasSettings = true
                }
                name == DELTA_ENTRY || name == SETTINGS_ENTRY
            }
            if (hasDatabase) {
                check(File(staging, DELTA_ENTRY).length() > 0L) { "Delta database is empty" }
            }
            var hasFiles = false
            if (filesArchive != null) {
                extract(filesArchive, File(staging, "files")) { name ->
                    val accepted = isSyncablePath(name)
                    if (accepted) hasFiles = true
                    accepted
                }
            }
            val staged = buildSet {
                if (hasDatabase && RestoreItem.CHAT_RECORDS in options.items) add(RestoreItem.CHAT_RECORDS)
                if (hasSettings && RestoreItem.SETTINGS in options.items) add(RestoreItem.SETTINGS)
                if (hasFiles && RestoreItem.FILES in options.items) add(RestoreItem.FILES)
            }
            require(staged.isNotEmpty()) { "The other build had nothing new to send" }
            PendingRestore.writeDurably(
                File(staging, OPTIONS_FILE),
                Json.encodeToString(RestoreOptions(items = staged, mode = options.mode)),
            )
            val pending = File(root, "pending")
            Files.move(staging.toPath(), pending.toPath(), ATOMIC_MOVE)
            RestoreStageResult(staged = staged, skipped = options.items - staged)
        } finally {
            staging.deleteRecursively()
        }
    }

    /**
     * 在启动最早阶段安装待应用的增量。返回是否安装过。
     *
     * 顺序是文件、数据库、设置：文件失败可以原地回滚；数据库在一个事务里改，失败由 SQLite 回滚；
     * 设置是单次写入，放在最后。失败时把整个目录移到 failed-*，不让下一次启动反复卡在同一个坏包上。
     */
    suspend fun applyPending(context: Context, json: Json): Boolean = withContext(Dispatchers.IO) {
        val root = rootOf(context)
        cleanup(root)
        val pending = File(root, "pending")
        if (!pending.isDirectory) return@withContext false
        val options = readOptions(pending)
        try {
            val installed = installFiles(pending, context.filesDir)
            try {
                val delta = File(pending, DELTA_ENTRY)
                val liveDatabase = context.getDatabasePath(SQLiteConfiguration.DATABASE_NAME)
                if (RestoreItem.CHAT_RECORDS in options.items && delta.isFile && liveDatabase.isFile) {
                    SyncDelta.apply(
                        context = context,
                        liveDatabase = liveDatabase,
                        deltaFile = delta,
                    )
                }
            } catch (e: Exception) {
                rollbackFiles(installed)
                throw e
            }
            val settings = File(pending, SETTINGS_ENTRY)
            if (RestoreItem.SETTINGS in options.items && settings.isFile) {
                restoreSettings(context, json, settings.readText(), options.mode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delta sync failed; pending payload isolated", e)
            runCatching { Files.move(pending.toPath(), File(root, "failed-${UUID.randomUUID()}").toPath()) }
            return@withContext false
        }
        Files.move(pending.toPath(), File(root, "completed-${UUID.randomUUID()}").toPath())
        Log.i(TAG, "Delta applied")
        true
    }

    private fun extract(archive: File, target: File, accept: (String) -> Boolean) {
        check(target.isDirectory || target.mkdirs()) { "Cannot create the delta directory" }
        ZipFile(archive).use { zip ->
            for (entry in zip.entries()) {
                if (entry.isDirectory) continue
                if (!accept(entry.name)) continue
                val output = PendingRestore.resolveInside(target, entry.name)
                output.parentFile?.let { check(it.isDirectory || it.mkdirs()) }
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(output).use { sink ->
                        input.copyTo(sink)
                        sink.fd.sync()
                    }
                }
            }
        }
    }

    private class InstalledFile(val target: File, val backup: File?)

    /** 把增量里的附件覆盖到 filesDir；被覆盖的旧文件先挪进 originals，失败时按原路放回 */
    private fun installFiles(pending: File, filesDir: File): List<InstalledFile> {
        val source = File(pending, "files")
        if (!source.isDirectory) return emptyList()
        val originals = File(pending, "originals")
        check(originals.isDirectory || originals.mkdirs()) { "Cannot create the delta backup directory" }
        val installed = mutableListOf<InstalledFile>()
        try {
            source.walkTopDown().filter { it.isFile }.toList().forEach { file ->
                val relative = file.relativeTo(source).invariantSeparatorsPath
                if (!isSyncablePath(relative)) return@forEach
                val target = PendingRestore.resolveInside(filesDir, relative)
                val backup = if (target.isFile) {
                    val destination = PendingRestore.resolveInside(originals, relative)
                    destination.parentFile?.let { check(it.isDirectory || it.mkdirs()) }
                    Files.move(target.toPath(), destination.toPath(), REPLACE_EXISTING)
                    destination
                } else {
                    null
                }
                target.parentFile?.let { check(it.isDirectory || it.mkdirs()) }
                Files.move(file.toPath(), target.toPath(), REPLACE_EXISTING)
                installed += InstalledFile(target, backup)
            }
        } catch (e: Exception) {
            rollbackFiles(installed)
            throw e
        }
        return installed
    }

    private fun rollbackFiles(installed: List<InstalledFile>) {
        installed.asReversed().forEach { entry ->
            runCatching {
                val backup = entry.backup
                if (backup != null && backup.isFile) {
                    Files.move(backup.toPath(), entry.target.toPath(), REPLACE_EXISTING)
                } else if (backup == null) {
                    entry.target.delete()
                }
            }.onFailure { Log.w(TAG, "Could not roll back ${entry.target.name}", it) }
        }
    }

    private suspend fun restoreSettings(context: Context, json: Json, raw: String, mode: RestoreMode) {
        val settings = json.decodeFromString<Settings>(raw)
        val element = json.parseToJsonElement(raw).jsonObject
        val launchCount = element["launchCount"]?.jsonPrimitive?.intOrNull ?: 0
        SettingsStore.restoreBeforeInitialization(
            context = context,
            settings = settings,
            launchCount = launchCount,
            merge = mode == RestoreMode.MERGE,
        )
        if (mode == RestoreMode.OVERWRITE) {
            element["appLanguageTag"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?.let { AppLanguage.setTag(context, it) }
        }
    }

    private fun readOptions(pending: File): RestoreOptions {
        val file = File(pending, OPTIONS_FILE)
        if (!file.isFile) return RestoreOptions()
        return runCatching { Json.decodeFromString<RestoreOptions>(file.readText()) }
            .getOrDefault(RestoreOptions())
    }

    private fun cleanup(root: File) {
        root.listFiles()?.filter {
            it.name.startsWith("completed-") || it.name.startsWith("preparing-") ||
                it.name.startsWith("failed-")
        }?.forEach { it.deleteRecursively() }
    }

    private fun rootOf(context: Context): File = File(context.noBackupFilesDir, ROOT_DIR)
}
