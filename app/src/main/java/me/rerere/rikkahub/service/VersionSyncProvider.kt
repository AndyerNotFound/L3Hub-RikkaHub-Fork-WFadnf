package me.rerere.rikkahub.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.os.BundleCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import com.l3hub.syncproto.BundleFormat
import me.rerere.rikkahub.data.sync.BackupManager
import me.rerere.rikkahub.data.sync.BundleRequest
import me.rerere.rikkahub.data.sync.PendingDelta
import me.rerere.rikkahub.data.sync.PendingRestore
import me.rerere.rikkahub.data.sync.RestoreOptions
import me.rerere.rikkahub.data.sync.SyncDelta
import me.rerere.rikkahub.data.sync.bridge.SyncBridge
import me.rerere.rikkahub.data.sync.buildSyncManifest
import me.rerere.rikkahub.data.sync.collectSyncableFiles
import me.rerere.rikkahub.data.sync.readDeviceSchema
import me.rerere.rikkahub.data.sync.isSyncablePath
import me.rerere.rikkahub.utils.JsonInstant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val TAG = "VersionSyncProvider"

/**
 * 把本应用的数据交给另一个版本（正式版与预览版互调）。
 *
 * 协议 2 起有两条路：
 * - 整份归档：按请求打包现有格式的备份，由 [BackupManager.stageRestore] 安装；
 * - 增量：`manifest` 给出会话与文件的清单，`exportDelta` 只返回按水位变更的会话、删除墓碑与缺的附件，
 *   由 [PendingDelta] 在下次启动时安装。
 *
 * 两条路都由接收侧在下次启动、Room 打开之前落地——[PendingRestore] 与 [PendingDelta] 都必须在
 * Koin 与 Room 之前安装，运行中的进程换不掉数据库。
 *
 * 鉴权见 [SyncBridge] 的说明：两版签名不同，只能用 UID 反查包名。provider 默认跑在本应用的主进程里，
 * 被调用时会连带走一遍 RikkaHubApp.onCreate（Web 服务等），这是 ContentProvider 的固有行为。
 */
class VersionSyncProvider : ContentProvider(), KoinComponent {
    private val backupManager: BackupManager by inject()
    private val database: AppDatabase by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val exports = ConcurrentHashMap<String, Export>()

    private data class Export(
        val state: String,
        val file: File? = null,
        val size: Long = 0L,
        val error: String? = null,
    )

    /** 打包与接收的中转目录；进程重起时整个清掉，没人会再来取上次的归档 */
    private val cacheDir: File
        get() = File(requireNotNull(context).cacheDir, "version-sync")

    override fun onCreate(): Boolean {
        runCatching { cacheDir.deleteRecursively() }
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        requireTrustedCaller()
        return when (method) {
            SyncBridge.METHOD_PING -> describeLocalBuild()
            SyncBridge.METHOD_EXPORT -> startExport(extras)
            SyncBridge.METHOD_JOB -> exportState(arg)
            SyncBridge.METHOD_IMPORT -> importArchive(extras)
            SyncBridge.METHOD_CLEANUP -> cleanupExport(arg)
            SyncBridge.METHOD_MANIFEST -> startManifest(extras)
            SyncBridge.METHOD_EXPORT_DELTA -> startDeltaExport(extras)
            SyncBridge.METHOD_IMPORT_DELTA -> importDelta(extras)
            SyncBridge.METHOD_SCHEMA -> describeSchema()
            SyncBridge.METHOD_EXPORT_BUNDLE -> startBundleExport(extras)
            else -> throw IllegalArgumentException("Unknown method: $method")
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        requireTrustedCaller()
        require(!mode.contains('w')) { "Version sync archives are read-only" }
        val token = uri.lastPathSegment ?: throw IllegalArgumentException("Missing export token")
        val archive = exports[token]?.file ?: throw IllegalStateException("Export is not finished")
        return ParcelFileDescriptor.open(archive, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = throw UnsupportedOperationException("Version sync has no query")

    override fun getType(uri: Uri): String? = "application/zip"

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Version sync does not insert rows")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Version sync does not delete rows")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Version sync does not update rows")

    private fun requireTrustedCaller() {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) return
        val caller = requireNotNull(context)
        val packages = caller.packageManager.getPackagesForUid(uid)?.toList().orEmpty()
        // 同 UID 的包必须全部在允许列表里：包名全设备唯一，伪造只能靠改包名，那会撞掉安装
        if (packages.isEmpty() || packages.any { it !in SyncBridge.TRUSTED_CALLERS }) {
            throw SecurityException("Version sync is limited to L3Hub builds")
        }
    }

    private fun describeLocalBuild(): Bundle {
        val caller = requireNotNull(context)
        val info = caller.packageManager.getPackageInfo(caller.packageName, 0)
        return Bundle().apply {
            putString(SyncBridge.KEY_PACKAGE, info.packageName)
            putString(SyncBridge.KEY_VERSION_NAME, info.versionName)
            putLong(SyncBridge.KEY_VERSION_CODE, PackageInfoCompat.getLongVersionCode(info))
            putInt(SyncBridge.KEY_SCHEMA_VERSION, localSchemaVersion())
            // 增量与整份恢复都只认一个待安装目录，任何一份在等重启都算忙
            putBoolean(
                SyncBridge.KEY_PENDING_RESTORE,
                PendingRestore.hasPending(caller) || PendingDelta.hasPending(caller),
            )
            putInt(SyncBridge.KEY_PROTOCOL, SyncBridge.ADVERTISED_PROTOCOL)
        }
    }

    /**
     * 当前库的结构与统计，交给数据中心做跨版本换算。
     *
     * 走 Bundle 而不是中转文件：一段 DDL 加几行计数只有几 KB，远不到 Binder 事务上限。
     * 老构建没有这个方法，调用方收到 IllegalArgumentException，据此降到「只读快照」。
     */
    private fun describeSchema(): Bundle {
        val schema = readDeviceSchema(database.openHelper.readableDatabase)
        return Bundle().apply {
            putString(SyncBridge.KEY_SCHEMA, JsonInstant.encodeToString(schema))
        }
    }

    /** 已迁移到当前版本的用户库版本；库还打不开时返回 0，让调用方跳过版本比较而不是拦住同步 */
    private fun localSchemaVersion(): Int = runCatching {
        database.openHelper.readableDatabase.version
    }.getOrDefault(0)

    /** 生成一份中转文件并把 token 交给调用方；文件生成在后台，调用方轮询 job */
    private fun startJob(block: suspend () -> File): Bundle {
        val token = UUID.randomUUID().toString()
        exports[token] = Export(state = SyncBridge.STATE_RUNNING)
        scope.launch {
            runCatching { block() }
                .onSuccess { archive ->
                    exports[token] = Export(SyncBridge.STATE_DONE, file = archive, size = archive.length())
                }
                .onFailure { error ->
                    Log.e(TAG, "Version sync job failed", error)
                    exports[token] = Export(
                        state = SyncBridge.STATE_FAILED,
                        error = error.message ?: error.javaClass.simpleName,
                    )
                }
        }
        return Bundle().apply { putString(SyncBridge.KEY_TOKEN, token) }
    }

    private fun startExport(extras: Bundle?): Bundle {
        val includeDatabase = extras?.getBoolean(SyncBridge.KEY_INCLUDE_DATABASE) ?: true
        val includeFiles = extras?.getBoolean(SyncBridge.KEY_INCLUDE_FILES) ?: true
        return startJob {
            // 同设备内的中转，归档不压缩：这台机器上 deflate 只有十几 MB/s，整库压一次要一分钟，
            // 而整份复制不到一秒。
            val archive = backupManager.createBackup(includeDatabase, includeFiles, compress = false)
            // createBackup 把归档放在 cacheDir 根下，挪进自己的目录，清理时不会连累别人的临时文件
            check(cacheDir.isDirectory || cacheDir.mkdirs()) { "Cannot create sync directory" }
            val target = File(cacheDir, archive.name)
            check(archive.renameTo(target)) { "Cannot move backup archive" }
            target
        }
    }

    /** 清单：会话 id 与更新时间、删除墓碑、附件路径与字节数；不含消息内容 */
    private fun startManifest(extras: Bundle?): Bundle {
        val includeFiles = extras?.getBoolean(SyncBridge.KEY_INCLUDE_FILES) ?: true
        return startJob {
            val caller = requireNotNull(context)
            val manifest = buildSyncManifest(
                database = database.openHelper.writableDatabase,
                filesDir = caller.filesDir,
                schemaVersion = localSchemaVersion(),
                includeFiles = includeFiles,
            )
            check(cacheDir.isDirectory || cacheDir.mkdirs()) { "Cannot create sync directory" }
            val file = File(cacheDir, "manifest-${UUID.randomUUID()}.json")
            file.writeText(JsonInstant.encodeToString(manifest))
            file
        }
    }

    /**
     * 增量包：一个 zip，内含按水位导出的 [SyncDelta] 库、可选的 settings.json、以及调用方点名要的附件。
     * 附件列表由调用方按清单比对后给出；列表为空表示只同步数据库与设置，null 表示连附件一起整份带上。
     */
    private fun startDeltaExport(extras: Bundle?): Bundle {
        val since = extras?.getLong(SyncBridge.KEY_SINCE, 0L) ?: 0L
        val includeDatabase = extras?.getBoolean(SyncBridge.KEY_INCLUDE_DATABASE) ?: true
        val includeSettings = extras?.getBoolean(SyncBridge.KEY_INCLUDE_SETTINGS) ?: false
        val includeFiles = extras?.getBoolean(SyncBridge.KEY_INCLUDE_FILES) ?: false
        val requestedPaths = extras?.getStringArrayList(SyncBridge.KEY_PATHS)
        return startJob {
            val caller = requireNotNull(context)
            check(cacheDir.isDirectory || cacheDir.mkdirs()) { "Cannot create sync directory" }
            val staging = Files.createTempDirectory(cacheDir.toPath(), "delta-").toFile()
            try {
                val archive = File(cacheDir, "delta-${UUID.randomUUID()}.zip")
                ZipOutputStream(FileOutputStream(archive)).use { zip ->
                    zip.setLevel(Deflater.NO_COMPRESSION)
                    if (includeDatabase) {
                        val delta = File(staging, PendingDelta.DELTA_ENTRY)
                        SyncDelta.export(database.openHelper.writableDatabase, delta, since)
                        addEntry(zip, delta, PendingDelta.DELTA_ENTRY)
                    }
                    if (includeSettings) {
                        addText(zip, PendingDelta.SETTINGS_ENTRY, backupManager.settingsJson())
                    }
                    if (includeFiles) {
                        val paths = requestedPaths
                            ?: collectSyncableFiles(caller.filesDir).map { it.path }
                        for (path in paths) {
                            if (!isSyncablePath(path)) continue
                            val file = File(caller.filesDir, path)
                            if (!file.isFile) continue
                            addEntry(zip, file, path)
                        }
                    }
                }
                archive
            } finally {
                staging.deleteRecursively()
            }
        }
    }

    /**
     * 导出一层 `.l3b`。
     *
     * [SyncBridge.KEY_BASE] 为 true 时是整份（整库 + 全部附件 + 设置），否则是按
     * [SyncBridge.KEY_SINCE] 水位切出的增量层；附件由调用方按清单比对后点名给出。
     * 同设备中转，payload 不压缩。
     */
    private fun startBundleExport(extras: Bundle?): Bundle {
        val base = extras?.getBoolean(SyncBridge.KEY_BASE) ?: false
        val since = extras?.getLong(SyncBridge.KEY_SINCE, 0L) ?: 0L
        val includeDatabase = extras?.getBoolean(SyncBridge.KEY_INCLUDE_DATABASE) ?: true
        val includeSettings = extras?.getBoolean(SyncBridge.KEY_INCLUDE_SETTINGS) ?: false
        val includeFiles = extras?.getBoolean(SyncBridge.KEY_INCLUDE_FILES) ?: false
        val paths = extras?.getStringArrayList(SyncBridge.KEY_PATHS)
        return startJob {
            check(cacheDir.isDirectory || cacheDir.mkdirs()) { "Cannot create sync directory" }
            val prepared = backupManager.prepareBundleLayer(
                BundleRequest(
                    includeDatabase = includeDatabase,
                    includeFiles = includeFiles,
                    base = base,
                    includeSettings = includeSettings,
                    since = since,
                    paths = paths?.toList(),
                    compress = false,
                )
            )
            val target = File(cacheDir, "bundle-${UUID.randomUUID()}.${BundleFormat.SUFFIX}")
            try {
                backupManager.appendBundle(target, prepared)
            } finally {
                prepared.payload.delete()
            }
            target
        }
    }

    private fun exportState(token: String?): Bundle {
        val export = token?.let(exports::get) ?: throw IllegalArgumentException("Unknown export token")
        return Bundle().apply {
            putString(SyncBridge.KEY_STATE, export.state)
            putLong(SyncBridge.KEY_SIZE, export.size)
            export.error?.let { putString(SyncBridge.KEY_ERROR, it) }
        }
    }

    private fun cleanupExport(token: String?): Bundle {
        if (token == null) return Bundle.EMPTY
        exports.remove(token)?.file?.delete()
        return Bundle.EMPTY
    }

    private fun importArchive(extras: Bundle?): Bundle {
        val arguments = extras ?: Bundle.EMPTY
        val descriptor = BundleCompat.getParcelable(
            arguments,
            SyncBridge.KEY_ARCHIVE,
            ParcelFileDescriptor::class.java,
        ) ?: throw IllegalArgumentException("Missing backup archive")
        val options = RestoreOptions(
            items = SyncBridge.decodeItems(arguments.getStringArrayList(SyncBridge.KEY_ITEMS)),
            mode = SyncBridge.decodeMode(arguments.getString(SyncBridge.KEY_MODE)),
        )
        check(!PendingRestore.hasPending(requireNotNull(context))) {
            "A backup restore is already pending. Restart the app first."
        }
        check(cacheDir.isDirectory || cacheDir.mkdirs()) { "Cannot create sync directory" }
        val archive = File.createTempFile("import-", ".zip", cacheDir)
        return try {
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                archive.outputStream().use { output -> input.copyTo(output) }
            }
            val result = runBlocking(Dispatchers.IO) {
                // 归档在解包时已由 zip 的 CRC 校验过字节，整库 integrity_check 只是让 1.2 GB 的库
                // 再整读一遍；坏库仍会在 Room 打开时报出来。
                backupManager.stageRestore(archive = archive, options = options, verify = false)
            }
            Bundle().apply {
                putStringArrayList(SyncBridge.KEY_STAGED, ArrayList(result.staged.map { it.name }))
                putStringArrayList(SyncBridge.KEY_SKIPPED, ArrayList(result.skipped.map { it.name }))
            }
        } finally {
            archive.delete()
        }
    }

    private fun importDelta(extras: Bundle?): Bundle {
        val arguments = extras ?: Bundle.EMPTY
        val descriptor = BundleCompat.getParcelable(
            arguments,
            SyncBridge.KEY_ARCHIVE,
            ParcelFileDescriptor::class.java,
        ) ?: throw IllegalArgumentException("Missing delta archive")
        val options = RestoreOptions(
            items = SyncBridge.decodeItems(arguments.getStringArrayList(SyncBridge.KEY_ITEMS)),
            mode = SyncBridge.decodeMode(arguments.getString(SyncBridge.KEY_MODE)),
        )
        val caller = requireNotNull(context)
        check(!PendingRestore.hasPending(caller)) {
            "A backup restore is already pending. Restart the app first."
        }
        check(!PendingDelta.hasPending(caller)) {
            "A sync is already pending. Restart the app first."
        }
        check(cacheDir.isDirectory || cacheDir.mkdirs()) { "Cannot create sync directory" }
        val archive = File.createTempFile("delta-import-", ".zip", cacheDir)
        return try {
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                archive.outputStream().use { output -> input.copyTo(output) }
            }
            // 同一个包里既有增量库也有附件，两次解压各取所需；`.l3b` 走分层备份包的恢复路径
            val result = runBlocking(Dispatchers.IO) {
                if (BundleFormat.isBundle(archive)) {
                    backupManager.stageRestore(archive = archive, options = options, verify = false)
                } else {
                    PendingDelta.stage(caller, archive, archive, options)
                }
            }
            Bundle().apply {
                putStringArrayList(SyncBridge.KEY_STAGED, ArrayList(result.staged.map { it.name }))
                putStringArrayList(SyncBridge.KEY_SKIPPED, ArrayList(result.skipped.map { it.name }))
            }
        } finally {
            archive.delete()
        }
    }

    private fun addEntry(zip: ZipOutputStream, file: File, name: String) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }

    private fun addText(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
