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
import me.rerere.rikkahub.data.sync.PendingDelta
import me.rerere.rikkahub.data.sync.PendingRestore
import me.rerere.rikkahub.data.sync.RestoreItem
import me.rerere.rikkahub.data.sync.RestoreOptions
import me.rerere.rikkahub.data.sync.RestoreStageResult
import me.rerere.rikkahub.data.sync.SyncDelta
import me.rerere.rikkahub.data.sync.SyncFilePlan
import me.rerere.rikkahub.data.sync.SyncManifest
import me.rerere.rikkahub.data.sync.SyncWatermarks
import me.rerere.rikkahub.data.sync.collectSyncableFiles
import me.rerere.rikkahub.data.sync.isSyncablePath
import me.rerere.rikkahub.data.sync.planFilesToFetch
import me.rerere.rikkahub.data.sync.planFilesToSend
import me.rerere.rikkahub.utils.JsonInstant
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 驱动另一个版本的 [me.rerere.rikkahub.service.VersionSyncProvider]。
 *
 * 协议 2 起优先走增量：
 * 1. 先换清单（会话 id 与 update_at、删除墓碑、附件路径与字节数）；
 * 2. 按水位与文件差异只请求变更的会话、墓碑与缺的附件；
 * 3. 收到的小包交给 [PendingDelta]，在下次启动、Room 打开之前安装。
 *
 * 对端仍是协议 1 的老包，或者两边数据库版本不一致（增量库没有完整表结构，不能交给 Room 迁移）时，
 * 退回原来的整份归档：[BackupManager.createBackup] → fd → [BackupManager.stageRestore]。
 */
class VersionSyncClient(
    private val context: Context,
    private val backupManager: BackupManager,
    private val database: AppDatabase,
    private val watermarks: SyncWatermarks = SyncWatermarks(context),
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
        val protocol: Int = 1,
    ) {
        /** 对端能按清单与水位给增量，且库版本与增量格式对得上 */
        val usesDelta: Boolean
            get() = supported && protocol >= SyncBridge.PROTOCOL_VERSION && schemaVersion > 0
    }

    sealed interface Progress {
        /** 对方正在准备（打包或生成增量），进度不可知 */
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
        ensureNoPending()
        if (!peer.usesDelta || peer.schemaVersion != localSchemaVersion()) {
            return@withContext pullFull(peer, options, onProgress)
        }
        pullDelta(peer, options, onProgress)
    }

    /** 把本应用的数据递给对方；返回对方暂存下来的类别 */
    suspend fun push(
        peer: Peer,
        options: RestoreOptions,
        onProgress: (Progress) -> Unit,
    ): RestoreStageResult = withContext(Dispatchers.IO) {
        checkPeer(peer)
        ensureNoPending()
        if (!peer.usesDelta || peer.schemaVersion != localSchemaVersion()) {
            return@withContext pushFull(peer, options, onProgress)
        }
        pushDelta(peer, options, onProgress)
    }

    // ---- 增量 ----

    private suspend fun pullDelta(
        peer: Peer,
        options: RestoreOptions,
        onProgress: (Progress) -> Unit,
    ): RestoreStageResult {
        val since = watermarks.pulledAt(peer.packageName)
        // 用水位起点而不是成功时刻记录：同步期间对方的新写入宁可下次多带一遍
        val startedAt = System.currentTimeMillis()
        val includeFiles = RestoreItem.FILES in options.items
        onProgress(Progress.Packing)
        val manifest = fetchManifest(peer, includeFiles)
        val plan = if (includeFiles) {
            planFilesToFetch(manifest, context.filesDir)
        } else {
            SyncFilePlan(emptyList(), full = false)
        }
        val wantsFiles = includeFiles && (plan.full || plan.paths.isNotEmpty())
        val extras = Bundle().apply {
            putLong(SyncBridge.KEY_SINCE, since)
            putBoolean(SyncBridge.KEY_INCLUDE_DATABASE, RestoreItem.CHAT_RECORDS in options.items)
            putBoolean(SyncBridge.KEY_INCLUDE_SETTINGS, RestoreItem.SETTINGS in options.items)
            putBoolean(SyncBridge.KEY_INCLUDE_FILES, wantsFiles)
            // 路径列表大到超过 Binder 事务时会抛异常，这时索要全量附件反而更省事
            if (wantsFiles && !plan.full) {
                putStringArrayList(SyncBridge.KEY_PATHS, ArrayList(plan.paths))
            }
        }
        val token = call(peer, SyncBridge.METHOD_EXPORT_DELTA, extras = extras)
            ?.getString(SyncBridge.KEY_TOKEN)
            ?: throw Failure.Remote("The other build did not start an export")

        // 文件已经一一对上时不再计入 FILES，否则结果里会把它报成「被跳过」
        val effectiveOptions = if (wantsFiles) {
            options
        } else {
            options.copy(items = options.items - RestoreItem.FILES)
        }
        val archive = File.createTempFile("version-sync-delta-", ".zip", context.cacheDir)
        try {
            val size = awaitExport(peer, token)
            onProgress(Progress.Copying(0, size))
            copyArchive(peer, token, archive, size, onProgress)
            onProgress(Progress.Importing)
            val result = PendingDelta.stage(context, archive, archive, effectiveOptions)
            watermarks.recordPull(peer.packageName, startedAt)
            return result
        } finally {
            runCatching { call(peer, SyncBridge.METHOD_CLEANUP, arg = token) }
            archive.delete()
        }
    }

    private suspend fun pushDelta(
        peer: Peer,
        options: RestoreOptions,
        onProgress: (Progress) -> Unit,
    ): RestoreStageResult {
        val since = watermarks.pushedAt(peer.packageName)
        val startedAt = System.currentTimeMillis()
        val includeFiles = RestoreItem.FILES in options.items
        onProgress(Progress.Packing)
        val remoteManifest = fetchManifest(peer, includeFiles)
        val localFiles = if (includeFiles) collectSyncableFiles(context.filesDir) else emptyList()
        val plan = if (includeFiles) {
            planFilesToSend(localFiles, remoteManifest)
        } else {
            SyncFilePlan(emptyList(), full = false)
        }
        val paths = when {
            !includeFiles -> emptyList()
            plan.full -> localFiles.map { it.path }
            else -> plan.paths
        }
        val archive = buildDeltaArchive(
            since = since,
            includeDatabase = RestoreItem.CHAT_RECORDS in options.items,
            includeSettings = RestoreItem.SETTINGS in options.items,
            files = paths,
        )
        // 对方已经齐全时包里没有附件，同样不要把 FILES 报成「被跳过」
        val effectiveOptions = if (includeFiles && paths.isEmpty()) {
            options.copy(items = options.items - RestoreItem.FILES)
        } else {
            options
        }
        try {
            onProgress(Progress.Copying(0, archive.length()))
            val result = withArchiveDescriptor(archive, effectiveOptions) { extras ->
                onProgress(Progress.Importing)
                call(peer, SyncBridge.METHOD_IMPORT_DELTA, extras = extras)
            } ?: throw Failure.Remote("The other build did not answer")
            watermarks.recordPush(peer.packageName, startedAt)
            return RestoreStageResult(
                staged = SyncBridge.decodeItems(result.getStringArrayList(SyncBridge.KEY_STAGED)),
                skipped = SyncBridge.decodeItems(result.getStringArrayList(SyncBridge.KEY_SKIPPED)),
            )
        } finally {
            archive.delete()
        }
    }

    private suspend fun fetchManifest(peer: Peer, includeFiles: Boolean): SyncManifest {
        val extras = Bundle().apply { putBoolean(SyncBridge.KEY_INCLUDE_FILES, includeFiles) }
        val token = call(peer, SyncBridge.METHOD_MANIFEST, extras = extras)
            ?.getString(SyncBridge.KEY_TOKEN)
            ?: throw Failure.Remote("The other build did not answer with a manifest")
        val file = File.createTempFile("version-sync-manifest-", ".json", context.cacheDir)
        try {
            val size = awaitExport(peer, token)
            copyArchive(peer, token, file, size) { }
            return JsonInstant.decodeFromString(file.readText())
        } finally {
            runCatching { call(peer, SyncBridge.METHOD_CLEANUP, arg = token) }
            file.delete()
        }
    }

    private suspend fun buildDeltaArchive(
        since: Long,
        includeDatabase: Boolean,
        includeSettings: Boolean,
        files: List<String>,
    ): File = withContext(Dispatchers.IO) {
        val archive = File.createTempFile("version-sync-delta-", ".zip", context.cacheDir)
        val staging = Files.createTempDirectory(context.cacheDir.toPath(), "delta-").toFile()
        try {
            ZipOutputStream(FileOutputStream(archive)).use { zip ->
                if (includeDatabase) {
                    val delta = File(staging, PendingDelta.DELTA_ENTRY)
                    SyncDelta.export(database.openHelper.writableDatabase, delta, since)
                    addEntry(zip, delta, PendingDelta.DELTA_ENTRY)
                }
                if (includeSettings) {
                    addText(zip, PendingDelta.SETTINGS_ENTRY, backupManager.settingsJson())
                }
                for (path in files) {
                    if (!isSyncablePath(path)) continue
                    val file = File(context.filesDir, path)
                    if (!file.isFile) continue
                    addEntry(zip, file, path)
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

    // ---- 整份归档（协议 1 与版本不一致时） ----

    private suspend fun pullFull(
        peer: Peer,
        options: RestoreOptions,
        onProgress: (Progress) -> Unit,
    ): RestoreStageResult {
        onProgress(Progress.Packing)
        val extras = Bundle().apply {
            putBoolean(SyncBridge.KEY_INCLUDE_DATABASE, RestoreItem.CHAT_RECORDS in options.items)
            putBoolean(SyncBridge.KEY_INCLUDE_FILES, RestoreItem.FILES in options.items)
        }
        val token = call(peer, SyncBridge.METHOD_EXPORT, extras = extras)
            ?.getString(SyncBridge.KEY_TOKEN)
            ?: throw Failure.Remote("The other build did not start an export")

        val archive = File.createTempFile("version-sync-", ".zip", context.cacheDir)
        return try {
            val size = awaitExport(peer, token)
            onProgress(Progress.Copying(0, size))
            copyArchive(peer, token, archive, size, onProgress)
            onProgress(Progress.Importing)
            // 同设备 IPC：归档不压缩、也不整库 integrity_check
            backupManager.stageRestore(archive = archive, options = options, verify = false)
        } finally {
            runCatching { call(peer, SyncBridge.METHOD_CLEANUP, arg = token) }
            archive.delete()
        }
    }

    private suspend fun pushFull(
        peer: Peer,
        options: RestoreOptions,
        onProgress: (Progress) -> Unit,
    ): RestoreStageResult {
        onProgress(Progress.Packing)
        val archive = backupManager.createBackup(
            includeDatabase = RestoreItem.CHAT_RECORDS in options.items,
            includeFiles = RestoreItem.FILES in options.items,
            compress = false,
        )
        try {
            onProgress(Progress.Copying(0, archive.length()))
            val result = withArchiveDescriptor(archive, options) { extras ->
                onProgress(Progress.Importing)
                call(peer, SyncBridge.METHOD_IMPORT, extras = extras)
            } ?: throw Failure.Remote("The other build did not answer")
            return RestoreStageResult(
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

    private fun ensureNoPending() {
        if (PendingRestore.hasPending(context)) throw Failure.LocalBusy()
        if (PendingDelta.hasPending(context)) throw Failure.LocalBusy()
    }

    private fun checkPeer(peer: Peer) {
        if (!peer.supported) throw Failure.Unsupported(peer.packageName)
        // 对方有一份恢复在等重启时不能再塞一份：两边都只认一个待安装目录
        if (peer.pendingRestore) throw Failure.PeerBusy(peer.packageName)
        val local = localSchemaVersion()
        // 对方库比本机新时先拦住：Room 的迁移报错太技术，而且提示不了「该升级哪一端」
        if (local > 0 && peer.schemaVersion > local) {
            throw Failure.NewerSchema(peer.schemaVersion, local)
        }
    }

    private fun localSchemaVersion(): Int = runCatching {
        database.openHelper.readableDatabase.version
    }.getOrDefault(0)

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
        // 测试通道（Canary / Nightly）在 UI 上都按预览版标注
        val isPreview = packageName.endsWith(".canary") || packageName.endsWith(".debug")
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
            protocol = ping.getInt(SyncBridge.KEY_PROTOCOL, 1),
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

    private companion object {
        const val EXPORT_POLL_INTERVAL = 400L
        const val PROGRESS_STEP = 1024L * 1024L

        /** 大归档（资源文件几百 MB）打包要几分钟，超过这个时长就当对端卡死了 */
        const val EXPORT_TIMEOUT_MS = 10 * 60 * 1000L
    }
}
