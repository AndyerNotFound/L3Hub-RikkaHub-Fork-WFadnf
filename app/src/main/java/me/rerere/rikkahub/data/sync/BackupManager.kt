package me.rerere.rikkahub.data.sync

import android.content.Context
import android.util.Log
import com.l3hub.syncproto.BundleFormat
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
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
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** 一份准备好的备份层：[payload] 是待追加的 zip，[layer] 是写进 `.l3b` 的头部 */
data class BundlePayload(val payload: File, val layer: BundleFormat.Layer)

/**
 * 准备一层备份时的全部输入。
 *
 * [base] 为 true 时是整份：整库快照、全部附件（[paths] 为空时）与设置。为 false 时是增量：
 * 只带 [since] 之后变化的会话、[paths] 点名的附件与声明删除的 [deletedFiles]。
 * [previousSettingsHash] 是链上最近一份设置的指纹，与当前一致时不再重复携带。
 */
data class BundleRequest(
    val includeDatabase: Boolean,
    val includeFiles: Boolean,
    val base: Boolean,
    val includeSettings: Boolean = true,
    val since: Long = 0L,
    val paths: List<String>? = null,
    val deletedFiles: List<String> = emptyList(),
    val previousSettingsHash: String? = null,
    val compress: Boolean = true,
)

/**
 * 备份归档的格式与恢复生命周期，本地、WebDAV、S3 与 IPC 共用。
 *
 * 两种文件格式：
 * - 全量 zip（旧格式，兼容保留）：`settings.json` + `rikka_hub.db` + 白名单附件；
 * - `.l3b`（新格式）：分层的追加容器，见 [BundleFormat]。base 层与旧 zip 内容等价，delta 层只带
 *   水位之后变化的会话、墓碑与附件，恢复时把 delta 按顺序打进 base 的副本。
 *
 * 恢复一律先落成待安装目录（[PendingRestore]），在下次启动、Room 打开之前安装；纯增量包
 * （没有 base 层）交给 [PendingDelta]，启动时直接合并进现有库。
 */
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
     * 打包一份整份备份归档（旧 zip 格式）。
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
            ZipOutputStream(FileOutputStream(archive)).use { zip ->
                if (!compress) zip.setLevel(Deflater.NO_COMPRESSION)
                addText(zip, BundleFormat.ENTRY_SETTINGS, settingsJson())
                if (includeDatabase) {
                    val snapshot = File(staging, SQLiteConfiguration.DATABASE_NAME)
                    DatabaseBackup.createSnapshot(database.openHelper.writableDatabase, snapshot)
                    addFile(zip, snapshot, BundleFormat.ENTRY_DATABASE)
                }
                if (includeFiles) {
                    for ((path, file) in attachmentFiles()) {
                        currentCoroutineContext().ensureActive()
                        addFile(zip, file, path)
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
     * 打一份原版 RikkaHub 能直接恢复的整份 zip。
     *
     * 与 [createBackup] 的区别只在内容：设置按原版的取值降级、库改成原版认识的版本、附件只收原版
     * 白名单里的目录。恢复流程本身在对方应用里走，这里不产生任何待安装状态。
     */
    suspend fun createUpstreamCompatBackup(
        includeDatabase: Boolean,
        includeFiles: Boolean,
    ): File = withContext(Dispatchers.IO) {
        val archive = File.createTempFile("rikkahub_upstream_", ".zip", context.cacheDir)
        val staging = Files.createTempDirectory(context.cacheDir.toPath(), "upstream-").toFile()
        try {
            ZipOutputStream(FileOutputStream(archive)).use { zip ->
                addText(zip, BundleFormat.ENTRY_SETTINGS, UpstreamCompat.downgradeSettings(settingsJson()))
                if (includeDatabase) {
                    val snapshot = File(staging, SQLiteConfiguration.DATABASE_NAME)
                    DatabaseBackup.createSnapshot(database.openHelper.writableDatabase, snapshot)
                    UpstreamCompat.downgradeDatabase(context, snapshot)
                    addFile(zip, snapshot, BundleFormat.ENTRY_DATABASE)
                }
                if (includeFiles) {
                    for ((path, file) in attachmentFiles(UpstreamCompat.ATTACHMENT_FOLDERS)) {
                        currentCoroutineContext().ensureActive()
                        addFile(zip, file, path)
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
     * 准备下一个备份层。
     *
     * 层里带什么完全由 [BundleRequest] 决定：base 层是整份（整库快照 + 全部附件 + 设置），delta 层
     * 只带水位之后的会话、点名的附件与声明删除的路径。判定「该不该重开一条链」在
     * [shouldStartNewChain]，两边分开，写入方只负责把 payload 追加到目标。
     */
    suspend fun prepareBundleLayer(request: BundleRequest): BundlePayload = withContext(Dispatchers.IO) {
        val schemaVersion = currentSchemaVersion()
        val deviceId = BundleChainStore.deviceId(context)
        val now = System.currentTimeMillis()
        val base = request.base
        val payload = File.createTempFile("bundle_", ".zip", context.cacheDir)
        val staging = Files.createTempDirectory(context.cacheDir.toPath(), "bundle-").toFile()
        try {
            val settingsText = if (request.includeSettings) settingsJson() else null
            val settingsHash = settingsText?.sha256()?.take(16)
            val carriesSettings = settingsText != null && (base || request.previousSettingsHash != settingsHash)
            val allFiles = if (request.includeFiles) attachmentFiles() else emptyMap()
            val carried: Map<String, File> = when {
                !request.includeFiles -> emptyMap()
                base && request.paths == null -> allFiles
                else -> request.paths.orEmpty().mapNotNull { path ->
                    if (!isSyncablePath(path)) return@mapNotNull null
                    allFiles[path]?.let { path to it }
                }.toMap()
            }

            var baseCounts: Map<String, Long> = emptyMap()
            var deltaSummary = SyncDeltaSummary()
            ZipOutputStream(FileOutputStream(payload)).use { zip ->
                if (!request.compress) zip.setLevel(Deflater.NO_COMPRESSION)
                if (request.includeDatabase) {
                    if (base) {
                        val snapshot = File(staging, SQLiteConfiguration.DATABASE_NAME)
                        DatabaseBackup.createSnapshot(database.openHelper.writableDatabase, snapshot)
                        addFile(zip, snapshot, BundleFormat.ENTRY_DATABASE)
                        baseCounts = databaseCounts()
                    } else {
                        val delta = File(staging, SyncDelta.FILE_NAME)
                        deltaSummary = SyncDelta.export(database.openHelper.writableDatabase, delta, request.since)
                        addFile(zip, delta, BundleFormat.ENTRY_DELTA)
                    }
                }
                if (carriesSettings) addText(zip, BundleFormat.ENTRY_SETTINGS, settingsText!!)
                for ((path, file) in carried) {
                    currentCoroutineContext().ensureActive()
                    addFile(zip, file, path)
                }
            }
            val layer = BundleFormat.Layer(
                id = UUID.randomUUID().toString(),
                kind = if (base) BundleFormat.KIND_BASE else BundleFormat.KIND_DELTA,
                createdAt = now,
                since = if (base) 0L else request.since,
                schemaVersion = schemaVersion,
                deviceId = deviceId,
                hasDatabase = request.includeDatabase,
                settingsHash = if (carriesSettings) settingsHash else null,
                counts = if (base) {
                    baseCounts
                } else {
                    mapOf(
                        "ConversationEntity" to deltaSummary.conversations.toLong(),
                        "message_node" to deltaSummary.nodes.toLong(),
                    )
                },
                files = carried.map { (path, file) -> BundleFormat.Entry(path = path, size = file.length()) },
                deletedFiles = if (base) emptyList() else request.deletedFiles,
            )
            BundlePayload(payload = payload, layer = layer)
        } catch (e: Throwable) {
            payload.delete()
            throw e
        } finally {
            staging.deleteRecursively()
        }
    }

    /**
     * 现有链还能不能接着叠。
     *
     * 换设备、结构版本变了、链太长或太旧、这次带不带数据库与链首不同，都重开一条 base 链：
     * 链只属于一台设备的一条结构版本，混着接会静默丢数据。
     */
    fun shouldStartNewChain(
        previous: BundleFormat.ChainSummary?,
        includeDatabase: Boolean,
        now: Long = System.currentTimeMillis(),
    ): Boolean = previous == null ||
        previous.hasDatabase != includeDatabase ||
        previous.schemaVersion != currentSchemaVersion() ||
        previous.layerCount >= MAX_CHAIN_LAYERS ||
        (previous.deviceId.isNotBlank() && previous.deviceId != BundleChainStore.deviceId(context)) ||
        now - previous.lastCreatedAt > MAX_CHAIN_AGE_MS

    /**
     * 把准备好的层追加到 [target]。
     *
     * [withMagic] 为 false 时只写记录不写文件头：远端把每一层存成独立对象（`xxx.l3b.d001`），
     * 恢复时按顺序拼接成一份完整的包，只有链首那份才有文件头。
     */
    fun appendBundle(
        target: File,
        prepared: BundlePayload,
        withMagic: Boolean = true,
    ): BundleFormat.Layer = BundleFormat.append(target, prepared.layer, prepared.payload, withMagic = withMagic)

    /**
     * 把一份归档落成待安装目录，支持旧 zip 与 `.l3b`。
     *
     * [verify] 为 false 时跳过整库 `integrity_check`：IPC 同步自带的归档在解包时已由 zip 的 CRC
     * 校验过字节，而整库扫一遍 1.2 GB 的库要几十秒。手动恢复、WebDAV / S3 恢复仍然校验。
     */
    suspend fun stageRestore(
        archive: File,
        options: RestoreOptions,
        verify: Boolean = true,
    ): RestoreStageResult = withContext(Dispatchers.IO) {
        restoreMutex.withLock {
            if (BundleFormat.isBundle(archive)) {
                stageBundle(archive = archive, options = options, verify = verify)
            } else {
                stageZip(archive = archive, options = options, verify = verify)
            }
        }
    }

    private suspend fun stageZip(
        archive: File,
        options: RestoreOptions,
        verify: Boolean,
    ): RestoreStageResult {
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
                        BundleFormat.ENTRY_SETTINGS -> if (RestoreItem.SETTINGS in options.items) {
                            File(staging, "settings.json").also { stagedItems += RestoreItem.SETTINGS }
                        } else {
                            null
                        }

                        BundleFormat.ENTRY_DATABASE -> if (RestoreItem.CHAT_RECORDS in options.items) {
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
                        else -> if (RestoreItem.FILES in options.items && isAttachmentName(entry.name)) {
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
            return finishStaging(restore, staging, stagedItems, options, verify)
        } finally {
            staging.deleteRecursively()
        }
    }

    /**
     * `.l3b` 的恢复。
     *
     * 链里没有 base 层时是一份纯增量包（别处投递过来的），交给 [PendingDelta] 在启动时合并进现有库；
     * 有 base 层就把整条链物化成一份完整的库，再走与整份恢复相同的落地与校验。
     */
    private suspend fun stageBundle(
        archive: File,
        options: RestoreOptions,
        verify: Boolean,
    ): RestoreStageResult {
        val index = BundleFormat.readIndex(archive)
        require(index.refs.isNotEmpty()) { "Backup bundle has no layers" }
        val chain = index.chain
        if (chain.isEmpty()) {
            // 纯增量包：没有 base 层，落到现有库上的合并交给启动阶段
            return PendingDelta.stageBundle(context = context, bundleFile = archive, options = options)
        }
        val restore = pendingRestore(context)
        val staging = restore.createStagingDirectory()
        try {
            val stagedItems = BundleRestore.materialize(
                context = context,
                bundle = archive,
                chain = chain,
                staging = staging,
                items = options.items,
            )
            return finishStaging(restore, staging, stagedItems, options, verify)
        } finally {
            staging.deleteRecursively()
        }
    }

    /** 待安装目录写好之后的公共收尾：校验、迁移库、改写附件路径、发布并返回暂存结果 */
    private fun finishStaging(
        restore: PendingRestore,
        staging: File,
        stagedItems: Set<RestoreItem>,
        options: RestoreOptions,
        verify: Boolean,
    ): RestoreStageResult {
        val payload = File(staging, "payload")
        val stagedDatabase = File(payload, "database/${SQLiteConfiguration.DATABASE_NAME}")
        val stagedWal = File(stagedDatabase.path + "-wal")
        val staged = stagedItems.toSet()
        require(staged.isNotEmpty()) { "No selected data found in the backup" }
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
        // 只记下备份里确实存在的类别：恢复时按这份清单决定动不动当前数据
        restore.publish(staging, RestoreOptions(items = staged, mode = options.mode))
        return RestoreStageResult(staged = staged, skipped = options.items - staged)
    }

    /** 白名单目录下可备份的附件，键是相对 filesDir 的路径 */
    private fun attachmentFiles(folders: List<String> = ATTACHMENT_FOLDERS): Map<String, File> {
        val map = LinkedHashMap<String, File>()
        for (folder in folders) {
            val directory = File(context.filesDir, folder)
            val files = backupFiles(directory, nested = folder in NESTED_ATTACHMENT_FOLDERS)
            for (file in files) {
                // A media result still being downloaded is incomplete and gets renamed when it finishes.
                if (folder == FileFolders.MEDIA_CREATION &&
                    file.name.endsWith(MediaCreationFiles.PARTIAL_SUFFIX)
                ) {
                    continue
                }
                val relative = file.relativeTo(directory).invariantSeparatorsPath
                // 软链指到目录之外的条目恢复端也写不回去，跳过这一条，不让整份备份作废
                if (PendingRestore.resolveInsideOrNull(directory, relative) == null) {
                    Log.w(TAG, "attachmentFiles: skip $folder/$relative, it resolves outside the folder")
                    continue
                }
                map["$folder/$relative"] = file
            }
        }
        return map
    }

    private fun databaseCounts(): Map<String, Long> = runCatching {
        database.openHelper.readableDatabase.query(
            "SELECT (SELECT COUNT(*) FROM `ConversationEntity`), (SELECT COUNT(*) FROM `message_node`), " +
                "(SELECT COUNT(*) FROM `memory_file`), (SELECT COUNT(*) FROM `workspaces`)"
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                emptyMap()
            } else {
                mapOf(
                    "ConversationEntity" to cursor.getLong(0),
                    "message_node" to cursor.getLong(1),
                    "memory_file" to cursor.getLong(2),
                    "workspaces" to cursor.getLong(3),
                )
            }
        }
    }.getOrDefault(emptyMap())

    private fun currentSchemaVersion(): Int = runCatching {
        database.openHelper.readableDatabase.version
    }.getOrDefault(0)

    private fun addFile(zip: ZipOutputStream, file: File, name: String) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun addText(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
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

        /** 一条链最多叠这么多层，再多就重开一个 base：层数越多，恢复时要打的增量越多 */
        private const val MAX_CHAIN_LAYERS = 10

        /** 链首的 base 超过这个时间就重开，避免一次恢复要打十几天的增量 */
        private const val MAX_CHAIN_AGE_MS = 7L * 24 * 60 * 60 * 1000

        /** 水位往前让一点：导出过程中正被写入的会话宁可下次多带一遍，也不要漏掉 */
        private const val CHAIN_OVERLAP_MS = 2_000L

        /** 下一层的水位：上一层创建时刻往前让两秒 */
        fun bundleWatermark(lastCreatedAt: Long): Long =
            (lastCreatedAt - CHAIN_OVERLAP_MS).coerceAtLeast(0L)

        /** 备份条目是不是白名单目录下的一条附件；`.l3b` 的层与旧 zip 用同一套名字 */
        internal fun isAttachmentName(name: String): Boolean {
            val folder = name.substringBefore('/')
            if (folder !in ATTACHMENT_FOLDERS || '/' !in name) return false
            val relative = name.substringAfter('/')
            require(relative.isNotBlank()) { "Invalid backup attachment: $name" }
            require(folder in NESTED_ATTACHMENT_FOLDERS || '/' !in relative) { "Invalid backup attachment: $name" }
            return true
        }

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

        private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

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
