package me.rerere.rikkahub.data.sync.bridge

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.sync.BackupManager
import me.rerere.rikkahub.data.sync.PendingRestore
import me.rerere.rikkahub.data.sync.RestoreItem
import me.rerere.rikkahub.data.sync.RestoreOptions
import me.rerere.rikkahub.data.sync.RestoreStageResult
import java.io.File

/**
 * 驱动另一个版本的 [me.rerere.rikkahub.service.VersionSyncProvider]。
 *
 * 拉取：让对方打包 → 轮询状态 → 用 provider 给的 fd 拷回本地 → 本地 stageRestore（下次启动安装）。
 * 推送：本地打包 → 把自己的归档 fd 递给对方 → 对方 stageRestore（对方下次启动安装）。
 * 两个方向都只搬现成的备份归档，归档本身经 Binder 传文件描述符，不受 1MB 事务上限影响。
 */
class VersionSyncClient(
    private val context: Context,
    private val backupManager: BackupManager,
    private val database: AppDatabase,
) {
    /** 设备上另一个版本的概况；[supported] 为 false 表示那边还是没有这套协议的老包 */
    data class Peer(
        val packageName: String,
        val isPreview: Boolean,
        val installed: Boolean = true,
        val supported: Boolean = true,
        val versionName: String = "",
        val versionCode: Long = 0L,
        val schemaVersion: Int = 0,
        val pendingRestore: Boolean = false,
    )

    sealed interface Progress {
        /** 对方正在打包，进度不可知 */
        data object Packing : Progress
        data class Copying(val copied: Long, val total: Long) : Progress
        data object Importing : Progress
    }

    /** 同步失败的原因；界面按类型取文案，不直接显示底层报错 */
    sealed class Failure(message: String, cause: Throwable? = null) : Exception(message, cause) {
        class Unsupported(packageName: String) :
            Failure("$packageName does not support version sync")

        class PeerBusy(packageName: String) :
            Failure("$packageName has a pending restore")

        class LocalBusy : Failure("A restore is already pending in this app")

        class NewerSchema(val peerVersion: Int, val localVersion: Int) :
            Failure("Peer database v$peerVersion is newer than v$localVersion")

        class Remote(val detail: String, cause: Throwable? = null) : Failure(detail, cause)
    }

    suspend fun installedPeers(): List<Peer> = withContext(Dispatchers.IO) {
        SyncBridge.PACKAGES
            .filter { it != context.packageName }
            .map(::probe)
            .filter { it.installed }
    }

    suspend fun pull(
        peer: Peer,
        options: RestoreOptions,
        onProgress: (Progress) -> Unit,
    ): RestoreStageResult = withContext(Dispatchers.IO) {
        checkPeer(peer)
        if (PendingRestore.hasPending(context)) throw Failure.LocalBusy()

        onProgress(Progress.Packing)
        val extras = Bundle().apply {
            putBoolean(SyncBridge.KEY_INCLUDE_DATABASE, RestoreItem.CHAT_RECORDS in options.items)
            putBoolean(SyncBridge.KEY_INCLUDE_FILES, RestoreItem.FILES in options.items)
        }
        val token = call(peer, SyncBridge.METHOD_EXPORT, extras = extras)
            ?.getString(SyncBridge.KEY_TOKEN)
            ?: throw Failure.Remote("The other build did not start an export")

        val archive = File.createTempFile("version-sync-", ".zip", context.cacheDir)
        try {
            val size = awaitExport(peer, token)
            onProgress(Progress.Copying(0, size))
            copyArchive(peer, token, archive, size, onProgress)
            onProgress(Progress.Importing)
            backupManager.stageRestore(archive = archive, options = options)
        } finally {
            runCatching { call(peer, SyncBridge.METHOD_CLEANUP, arg = token) }
            archive.delete()
        }
    }

    /** 把本应用的归档递给对方；返回对方暂存下来的类别 */
    suspend fun push(
        peer: Peer,
        options: RestoreOptions,
        onProgress: (Progress) -> Unit,
    ): RestoreStageResult = withContext(Dispatchers.IO) {
        checkPeer(peer)

        onProgress(Progress.Packing)
        val archive = backupManager.createBackup(
            includeDatabase = RestoreItem.CHAT_RECORDS in options.items,
            includeFiles = RestoreItem.FILES in options.items,
        )
        try {
            onProgress(Progress.Copying(0, archive.length()))
            val result = withArchiveDescriptor(archive, options) { extras ->
                onProgress(Progress.Importing)
                call(peer, SyncBridge.METHOD_IMPORT, extras = extras)
            } ?: throw Failure.Remote("The other build did not answer")
            RestoreStageResult(
                staged = SyncBridge.decodeItems(result.getStringArrayList(SyncBridge.KEY_STAGED)),
                skipped = SyncBridge.decodeItems(result.getStringArrayList(SyncBridge.KEY_SKIPPED)),
            )
        } finally {
            archive.delete()
        }
    }

    private inline fun <T> withArchiveDescriptor(
        archive: File,
        options: RestoreOptions,
        block: (Bundle) -> T,
    ): T {
        val descriptor = ParcelFileDescriptor.open(archive, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val extras = Bundle().apply {
                putParcelable(SyncBridge.KEY_ARCHIVE, descriptor)
                putStringArrayList(SyncBridge.KEY_ITEMS, SyncBridge.encodeItems(options.items))
                putString(SyncBridge.KEY_MODE, options.mode.name)
            }
            return block(extras)
        } finally {
            descriptor.close()
        }
    }

    private fun checkPeer(peer: Peer) {
        if (!peer.supported) throw Failure.Unsupported(peer.packageName)
        // 对方有一份恢复在等重启时不能再塞一份：PendingRestore 只认一个待恢复目录
        if (peer.pendingRestore) throw Failure.PeerBusy(peer.packageName)
        val local = runCatching { database.openHelper.readableDatabase.version }.getOrDefault(0)
        // 对方库比本机新时先拦住：Room 的迁移报错太技术，而且提示不了「该升级哪一端」
        if (local > 0 && peer.schemaVersion > local) {
            throw Failure.NewerSchema(peer.schemaVersion, local)
        }
    }

    private suspend fun awaitExport(peer: Peer, token: String): Long {
        val deadline = System.currentTimeMillis() + EXPORT_TIMEOUT_MS
        while (true) {
            val state = call(peer, SyncBridge.METHOD_JOB, arg = token)
                ?: throw Failure.Remote("The other build stopped responding")
            when (state.getString(SyncBridge.KEY_STATE)) {
                SyncBridge.STATE_DONE -> return state.getLong(SyncBridge.KEY_SIZE)
                SyncBridge.STATE_FAILED -> throw Failure.Remote(
                    state.getString(SyncBridge.KEY_ERROR).orEmpty().ifBlank { "Export failed" }
                )

                else -> {
                    if (System.currentTimeMillis() > deadline) {
                        throw Failure.Remote("Timed out waiting for the other build to pack")
                    }
                    delay(EXPORT_POLL_INTERVAL)
                }
            }
        }
    }

    private fun copyArchive(
        peer: Peer,
        token: String,
        target: File,
        total: Long,
        onProgress: (Progress) -> Unit,
    ) {
        val input = context.contentResolver.openInputStream(SyncBridge.exportUri(peer.packageName, token))
            ?: throw Failure.Remote("The other build did not hand over its archive")
        input.use { source ->
            target.outputStream().use { sink ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                var reported = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    sink.write(buffer, 0, read)
                    copied += read
                    // 大归档每满 1MB 才回调一次，别让每 8KB 都往 Compose 状态里推一遍
                    if (copied - reported >= PROGRESS_STEP || copied == total) {
                        reported = copied
                        onProgress(Progress.Copying(copied, total))
                    }
                }
            }
        }
    }

    private fun probe(packageName: String): Peer {
        val isPreview = packageName.endsWith(".debug")
        val info = runCatching { context.packageManager.getPackageInfo(packageName, 0) }.getOrNull()
            ?: return Peer(packageName = packageName, isPreview = isPreview, installed = false)
        val versionName = info.versionName.orEmpty()
        val versionCode = PackageInfoCompat.getLongVersionCode(info)
        val ping = runCatching {
            call(SyncBridge.endpoint(packageName), SyncBridge.METHOD_PING)
        }.getOrNull() ?: return Peer(
            packageName = packageName,
            isPreview = isPreview,
            supported = false,
            versionName = versionName,
            versionCode = versionCode,
        )
        return Peer(
            packageName = packageName,
            isPreview = isPreview,
            versionName = ping.getString(SyncBridge.KEY_VERSION_NAME) ?: versionName,
            versionCode = ping.getLong(SyncBridge.KEY_VERSION_CODE, versionCode),
            schemaVersion = ping.getInt(SyncBridge.KEY_SCHEMA_VERSION),
            pendingRestore = ping.getBoolean(SyncBridge.KEY_PENDING_RESTORE),
        )
    }

    private fun call(peer: Peer, method: String, arg: String? = null, extras: Bundle? = null): Bundle? =
        call(SyncBridge.endpoint(peer.packageName), method, arg, extras)

    private fun call(endpoint: Uri, method: String, arg: String? = null, extras: Bundle? = null): Bundle? =
        try {
            context.contentResolver.call(endpoint, method, arg, extras)
        } catch (e: RuntimeException) {
            // 对方被强制停止、正在升级或刚被卸载时，系统抛的是找不到 provider 一类的运行时异常
            throw Failure.Remote(e.message ?: e.javaClass.simpleName, e)
        }

    private companion object {
        const val EXPORT_POLL_INTERVAL = 400L
        const val PROGRESS_STEP = 1024L * 1024L

        /** 大归档（资源文件几百 MB）打包要几分钟，超过这个时长就当对端卡死了 */
        const val EXPORT_TIMEOUT_MS = 10 * 60 * 1000L
    }
}
