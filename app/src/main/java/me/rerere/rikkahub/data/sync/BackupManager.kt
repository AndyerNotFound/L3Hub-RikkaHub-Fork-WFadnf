package me.rerere.rikkahub.data.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.migration.SettingsJsonMigrator
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import me.rerere.rikkahub.data.db.SQLiteConfiguration
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.MediaCreationFiles
import me.rerere.rikkahub.utils.AppLanguage
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Shared archive format and restore lifecycle for local, WebDAV and S3 backups. */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
    private val json: Json,
) {
    private val restoreMutex = Mutex()

    /**
     * 生成与整份备份里完全相同的 settings.json。
     *
     * 增量同步只带设置文件时用它：设置是单个 DataStore 文件加启动次数、语言标签，没有可切分的增量，
     * 整体带上最省事，也与恢复端的解析沿用同一套格式。
     */
    suspend fun settingsJson(): String {
        val settings = settingsStore.awaitLoaded()
        return json.encodeSettings(
            settings = settings,
            launchCount = settingsStore.launchCountFlow.first(),
            appLanguageTag = AppLanguage.currentTag(context),
        )
    }

    /**
     * 打包一份备份归档。
     *
     * [compress] 只对同设备内的 IPC 同步关闭：deflate 在这类机器上只有十几 MB/s，1.2 GB 的库压一次
     * 要一分钟，而整份复制只要一秒——压缩在中转场景是纯亏本。上传 WebDAV / S3 与手动备份保持压缩。
     */
    suspend fun createBackup(
        includeDatabase: Boolean,
        includeFiles: Boolean,
        compress: Boolean = true,
    ): File = withContext(Dispatchers.IO) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val archive = File.createTempFile("backup_${timestamp}_", ".zip", context.cacheDir)
        val staging = Files.createTempDirectory(context.cacheDir.toPath(), "backup-").toFile()
        try {
            val settings = settingsStore.awaitLoaded()
            ZipOutputStream(FileOutputStream(archive)).use { zip ->
                if (!compress) zip.setLevel(Deflater.NO_COMPRESSION)
                zip.putNextEntry(ZipEntry("settings.json"))
                val settingsJson = json.encodeSettings(
                    settings = settings,
                    launchCount = settingsStore.launchCountFlow.first(),
                    appLanguageTag = AppLanguage.currentTag(context),
                )
                zip.write(settingsJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                if (includeDatabase) {
                    val snapshot = File(staging, SQLiteConfiguration.DATABASE_NAME)
                    DatabaseBackup.createSnapshot(database.openHelper.writableDatabase, snapshot)
                    addFile(zip, snapshot, DatabaseBackup.ARCHIVE_DATABASE)
                }
                if (includeFiles) {
                    for (folder in ATTACHMENT_FOLDERS) {
                        val directory = File(context.filesDir, folder)
                        val files = backupFiles(directory, nested = folder in NESTED_ATTACHMENT_FOLDERS)
                        for (file in files) {
                            currentCoroutineContext().ensureActive()
                            // A media result still being downloaded is incomplete and gets renamed when it finishes.
                            if (folder == FileFolders.MEDIA_CREATION &&
                                file.name.endsWith(MediaCreationFiles.PARTIAL_SUFFIX)
                            ) {
                                continue
                            }
                            val relative = file.relativeTo(directory).invariantSeparatorsPath
                            // 软链指到目录之外的条目恢复端也写不回去，跳过这一条，不让整份备份作废
                            if (PendingRestore.resolveInsideOrNull(directory, relative) == null) {
                                Log.w(TAG, "createBackup: skip $folder/$relative, it resolves outside the folder")
                                continue
                            }
                            addFile(zip, file, "$folder/$relative")
                        }
                    }
                }
            }
            archive
        } catch (e: Throwable) {
            archive.delete()
            throw e
        } finally {
            staging.deleteRecursively()
        }
    }

    /**
     * 把一份归档落成待安装目录。
     *
     * [verify] 为 false 时跳过整库 `integrity_check`：IPC 同步自带的归档在解包时已由 zip 的 CRC
     * 校验过字节，而整库扫一遍 1.2 GB 的库要几十秒。手动恢复、WebDAV / S3 恢复仍然校验。
     */
    suspend fun stageRestore(
        archive: File,
        options: RestoreOptions,
        verify: Boolean = true,
    ): RestoreStageResult =
        withContext(Dispatchers.IO) {
            restoreMutex.withLock {
                val restore = pendingRestore(context)
                val staging = restore.createStagingDirectory()
                try {
                    val payload = File(staging, "payload")
                    val stagedDatabase = File(payload, "database/${SQLiteConfiguration.DATABASE_NAME}")
                    val stagedWal = File(stagedDatabase.path + "-wal")
                    val seen = mutableSetOf<String>()
                    val stagedItems = mutableSetOf<RestoreItem>()
                    ZipFile(archive).use { zip ->
                        for (entry in zip.entries()) {
                            currentCoroutineContext().ensureActive()
                            if (entry.isDirectory) continue
                            val target = when (entry.name) {
                                "settings.json" -> if (RestoreItem.SETTINGS in options.items) {
                                    File(staging, "settings.json").also { stagedItems += RestoreItem.SETTINGS }
                                } else {
                                    null
                                }

                                DatabaseBackup.ARCHIVE_DATABASE -> if (RestoreItem.CHAT_RECORDS in options.items) {
                                    stagedDatabase.also { stagedItems += RestoreItem.CHAT_RECORDS }
                                } else {
                                    null
                                }

                                DatabaseBackup.WAL -> if (RestoreItem.CHAT_RECORDS in options.items) {
                                    stagedWal
                                } else {
                                    null
                                }

                                DatabaseBackup.SHM -> null // Rebuilt by SQLite; never restore shared-memory state.
                                else -> if (RestoreItem.FILES in options.items && isAttachment(entry.name)) {
                                    PendingRestore.resolveInside(File(payload, "files"), entry.name)
                                        .also { stagedItems += RestoreItem.FILES }
                                } else {
                                    null
                                }
                            } ?: continue
                            require(seen.add(entry.name)) { "Duplicate backup entry: ${entry.name}" }
                            check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) {
                                "Cannot create backup staging directory"
                            }
                            zip.getInputStream(entry).use { input ->
                                FileOutputStream(target).use { output ->
                                    input.copyTo(output)
                                    output.fd.sync()
                                }
                            }
                        }
                    }
                    require(stagedItems.isNotEmpty()) { "No selected data found in the backup" }
                    require(!stagedWal.exists() || stagedDatabase.exists()) { "Backup WAL has no matching database" }
                    if (stagedDatabase.exists()) {
                        DatabaseBackup.normalize(context, stagedDatabase, verify = verify)
                        // Reject unsupported schemas before publishing; run supported old migrations on the copy.
                        val room = AppDatabaseFactory.create(context, stagedDatabase.absolutePath)
                        try {
                            // 附件是绝对路径，换包名后指向原应用私有目录，这里改写到当前应用
                            BackupPaths.rewriteDatabase(room.openHelper.writableDatabase, context.filesDir)
                            // 换设备/换包名后 Keystore 密钥不同，清掉解不开的 SSH 凭据
                            SshCredentialSanitizer.dropUnreadable(room.openHelper.writableDatabase)
                            DatabaseBackup.checkpoint(room.openHelper.writableDatabase)
                        } finally {
                            room.close()
                        }
                        DatabaseBackup.removeSidecars(stagedDatabase)
                    }

                    val settingsFile = File(staging, "settings.json")
                    if (settingsFile.exists()) {
                        val migrated = SettingsJsonMigrator.migrate(settingsFile.readText())
                        val rewritten = BackupPaths.rewrite(migrated, context.filesDir)
                        val settings = json.decodeFromString<Settings>(rewritten)
                        require(!settings.init) { "Backup contains uninitialized settings" }
                        // Persist the migrated value once, including generated IDs, for restart/retry consistency.
                        PendingRestore.writeDurably(
                            settingsFile,
                            json.encodeSettings(
                                settings = settings,
                                launchCount = json.launchCountOf(rewritten),
                                appLanguageTag = json.appLanguageTagOf(rewritten).orEmpty(),
                            )
                        )
                    }
                    currentCoroutineContext().ensureActive()
                    val staged = stagedItems.toSet()
                    // 只记下备份里确实存在的类别：恢复时按这份清单决定动不动当前数据
                    restore.publish(staging, RestoreOptions(items = staged, mode = options.mode))
                    RestoreStageResult(staged = staged, skipped = options.items - staged)
                } finally {
                    staging.deleteRecursively()
                }
            }
        }

    private fun isAttachment(name: String): Boolean {
        val folder = name.substringBefore('/')
        if (folder !in ATTACHMENT_FOLDERS || '/' !in name) return false
        val relative = name.substringAfter('/')
        require(relative.isNotBlank()) { "Invalid backup attachment: $name" }
        require(folder in NESTED_ATTACHMENT_FOLDERS || '/' !in relative) { "Invalid backup attachment: $name" }
        return true
    }

    private fun addFile(zip: ZipOutputStream, file: File, name: String) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    companion object {
        // 备份只收白名单里的这些目录。workspaces/（工作区文件）和 drafts/（流式草稿）都是运行时状态，
        // 换个进程/设备恢复出来没有意义，所以不列在这里，也就不会被打包
        internal val ATTACHMENT_FOLDERS = listOf(
            FileFolders.UPLOAD,
            FileFolders.IMAGES,
            FileFolders.SKILLS,
            FileFolders.FONTS,
            FileFolders.MEDIA_CREATION,
        )

        /** Backed up with their subdirectories; the other folders only contain top-level files. */
        internal val NESTED_ATTACHMENT_FOLDERS = setOf(FileFolders.SKILLS, FileFolders.MEDIA_CREATION)

        private const val TAG = "BackupManager"

        /** 软链一律不跟：它可能指向目录之外，也可能成环把遍历拖死 */
        private fun isSymbolicLink(file: File): Boolean =
            runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)

        /**
         * 备份与覆盖式恢复要走的文件。[nested] 为 true 时逐层下探子目录，否则只取顶层文件。
         *
         * 只收真实文件：软链既写不回原始位置（恢复端按常规文件落盘），也可能把目录外的内容整片带进来，
         * 所以逐个跳过；层级按名称排定，同一份数据每次走出来的顺序一致。
         */
        internal fun backupFiles(directory: File, nested: Boolean): Sequence<File> = sequence {
            for (child in directory.listFiles().orEmpty().sortedBy { it.name }) {
                if (isSymbolicLink(child)) continue
                if (child.isDirectory) {
                    if (nested) yieldAll(backupFiles(child, nested = true))
                } else if (child.isFile) {
                    yield(child)
                }
            }
        }

        // 启动次数不在 Settings 里，备份文件里仍放在 settings.json 的这个字段，和旧备份保持一致
        private const val LAUNCH_COUNT_KEY = "launchCount"
        // L3Hub 的语言选择存在 SharedPreferences 里，不随 Settings 走，备份时单独带上
        private const val APP_LANGUAGE_KEY = "appLanguageTag"

        private fun Json.encodeSettings(
            settings: Settings,
            launchCount: Int,
            appLanguageTag: String,
        ): String {
            val fields = (encodeToJsonElement(settings).jsonObject + (LAUNCH_COUNT_KEY to JsonPrimitive(launchCount)))
                .toMutableMap()
            if (appLanguageTag.isNotBlank()) {
                fields[APP_LANGUAGE_KEY] = JsonPrimitive(appLanguageTag)
            }
            return encodeToString(JsonObject(fields))
        }

        // 更早的备份没有这个字段，按 0 恢复，和它还在 Settings 里时的默认值一致
        private fun Json.launchCountOf(settingsJson: String): Int =
            parseToJsonElement(settingsJson).jsonObject[LAUNCH_COUNT_KEY]?.jsonPrimitive?.intOrNull ?: 0

        private fun Json.appLanguageTagOf(settingsJson: String): String? =
            parseToJsonElement(settingsJson).jsonObject[APP_LANGUAGE_KEY]
                ?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        private fun pendingRestore(context: Context) = PendingRestore(
            root = PendingRestore.rootOf(context),
            databaseFile = context.getDatabasePath(SQLiteConfiguration.DATABASE_NAME),
            filesDir = context.filesDir,
            context = context.applicationContext,
        )

        /** Must finish before Koin, Room, SettingsStore or any background consumers are initialized. */
        suspend fun applyPendingRestore(context: Context, json: Json): Boolean = withContext(Dispatchers.IO) {
            pendingRestore(context).apply { settingsJson, mode ->
                SettingsStore.restoreBeforeInitialization(
                    context = context,
                    settings = json.decodeFromString<Settings>(settingsJson),
                    launchCount = json.launchCountOf(settingsJson),
                    merge = mode == RestoreMode.MERGE,
                )
                // 合并模式下语言属于「保留当前」的单值设置，只有整份覆盖才跟着备份走
                if (mode == RestoreMode.OVERWRITE) {
                    json.appLanguageTagOf(settingsJson)?.let { tag -> AppLanguage.setTag(context, tag) }
                }
            }
        }
    }
}
