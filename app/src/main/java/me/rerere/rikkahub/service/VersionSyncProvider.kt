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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.sync.BackupManager
import me.rerere.rikkahub.data.sync.PendingRestore
import me.rerere.rikkahub.data.sync.RestoreOptions
import me.rerere.rikkahub.data.sync.bridge.SyncBridge
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "VersionSyncProvider"

/**
 * 把本应用的数据交给另一个版本（正式版与预览版互调）。
 *
 * 只做两件事：按请求打包一份现有格式的备份归档、把对方递来的归档放进待恢复目录。真正的解包与写入
 * 仍由 [BackupManager] 完成，而且接收侧要下次启动才生效——[PendingRestore] 必须在 Koin 与 Room
 * 之前安装，运行中的进程换不掉数据库。
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

    override fun onShutdown() {
        scope.cancel()
        runCatching { cacheDir.deleteRecursively() }
        super.onShutdown()
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        requireTrustedCaller()
        return when (method) {
            SyncBridge.METHOD_PING -> describeLocalBuild()
            SyncBridge.METHOD_EXPORT -> startExport(extras)
            SyncBridge.METHOD_JOB -> exportState(arg)
            SyncBridge.METHOD_IMPORT -> importArchive(extras)
            SyncBridge.METHOD_CLEANUP -> cleanupExport(arg)
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
        if (packages.isEmpty() || packages.any { it !in SyncBridge.PACKAGES }) {
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
            putBoolean(SyncBridge.KEY_PENDING_RESTORE, PendingRestore.hasPending(caller))
        }
    }

    /** 已迁移到当前版本的用户库版本；库还打不开时返回 0，让调用方跳过版本比较而不是拦住同步 */
    private fun localSchemaVersion(): Int = runCatching {
        database.openHelper.readableDatabase.version
    }.getOrDefault(0)

    private fun startExport(extras: Bundle?): Bundle {
        val includeDatabase = extras?.getBoolean(SyncBridge.KEY_INCLUDE_DATABASE) ?: true
        val includeFiles = extras?.getBoolean(SyncBridge.KEY_INCLUDE_FILES) ?: true
        val token = UUID.randomUUID().toString()
        exports[token] = Export(state = SyncBridge.STATE_RUNNING)
        scope.launch {
            runCatching {
                val archive = backupManager.createBackup(includeDatabase, includeFiles)
                // createBackup 把归档放在 cacheDir 根下，挪进自己的目录，清理时不会连累别人的临时文件
                check(cacheDir.isDirectory || cacheDir.mkdirs()) { "Cannot create sync directory" }
                val target = File(cacheDir, archive.name)
                check(archive.renameTo(target)) { "Cannot move backup archive" }
                target
            }.onSuccess { archive ->
                exports[token] = Export(SyncBridge.STATE_DONE, file = archive, size = archive.length())
            }.onFailure { error ->
                Log.e(TAG, "Export failed", error)
                exports[token] = Export(
                    state = SyncBridge.STATE_FAILED,
                    error = error.message ?: error.javaClass.simpleName,
                )
            }
        }
        return Bundle().apply { putString(SyncBridge.KEY_TOKEN, token) }
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
                backupManager.stageRestore(archive = archive, options = options)
            }
            Bundle().apply {
                putStringArrayList(SyncBridge.KEY_STAGED, ArrayList(result.staged.map { it.name }))
                putStringArrayList(SyncBridge.KEY_SKIPPED, ArrayList(result.skipped.map { it.name }))
            }
        } finally {
            archive.delete()
        }
    }
}
