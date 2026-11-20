package com.l3hub.database.wfadnf.core

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.content.pm.PackageInfoCompat
import com.l3hub.syncproto.L3HubSyncProtocol
import com.l3hub.syncproto.PeerManifest
import com.l3hub.syncproto.PeerSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class PeerException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 与 L3Hub 各构建的 IPC 客户端。
 *
 * 发现走两条线索：包名在已知列表里，或者该包导出了 `<包名>.sync` 这个 provider。后者让任意改过包名
 * 的分支也能被找到，只要它沿用同一套同步协议。找到之后用 `ping` 确认协议版本，再用 `schema`
 * 取库结构；老构建没有 `schema`，这时仍可拉快照，但跨版本投递会被拒绝。
 *
 * 「导出同名 provider」这条线索不能单独当证据：ColorOS 的 `com.oplus.remotecontrol` 就恰好导出了
 * 一个 `<包名>.sync`。未知包必须真的答上 `ping`，并且自报的包名与查询的包名一致，才算一个构建；
 * 已知包名即便没装也照旧列出，界面才能显示「装一个再回来」。
 */
class PeerClient(private val context: Context) {
    data class BuildInfo(
        val packageName: String,
        val label: String,
        val versionName: String = "",
        val versionCode: Long = 0,
        val updateTime: Long = 0,
        val debuggable: Boolean = false,
        val signature: String? = null,
        val apkBytes: Long = 0,
    )

    sealed interface Build {
        val info: BuildInfo

        data class Ready(
            override val info: BuildInfo,
            val protocol: Int,
            val schemaVersion: Int,
            val pendingRestore: Boolean,
            val schema: PeerSchema?,
            /** 对端 `ping` 自报的包名；用来确认拨的确实是 L3Hub，而不是恰好同后缀的 provider */
            val reportedPackage: String = info.packageName,
        ) : Build

        data class Unavailable(
            override val info: BuildInfo,
            val reason: String,
        ) : Build
    }

    suspend fun discover(): List<Build> = withContext(Dispatchers.IO) {
        val packages = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getInstalledPackages(PackageManager.GET_PROVIDERS)
        }.getOrDefault(emptyList())
        val known = L3HubSyncProtocol.BUILD_PACKAGES
        val candidates = packages.asSequence()
            .filter { it.packageName != context.packageName }
            .filter { info ->
                info.packageName in known ||
                    info.providers.orEmpty().any { it.authority == L3HubSyncProtocol.authority(info.packageName) }
            }
            .map { it.packageName }
            .distinct()
            .sorted()
            .toList()
        candidates.mapNotNull { packageName ->
            val build = probe(packageName)
            if (packageName in known) return@mapNotNull build
            // 未知包可能是别的应用恰好导出了同名后缀的 provider：只有答上 ping 且自报包名对得上才算数
            val ready = build as? Build.Ready ?: return@mapNotNull null
            // 老构建可能不在 ping 里报包名：留空放过，报了却对不上就不认
            val nameMatches = ready.reportedPackage.isBlank() || ready.reportedPackage == packageName
            build.takeIf { nameMatches && ready.protocol > 0 }
        }
    }

    suspend fun probe(packageName: String): Build = withContext(Dispatchers.IO) {
        val info = readBuildInfo(packageName)
        val ping = try {
            call(packageName, L3HubSyncProtocol.METHOD_PING)
                ?: return@withContext Build.Unavailable(info, "对端没有响应")
        } catch (error: Throwable) {
            return@withContext Build.Unavailable(info, describe(error))
        }
        val schema = runCatching { readSchema(packageName) }.getOrNull()
        Build.Ready(
            info = info,
            protocol = ping.getInt(L3HubSyncProtocol.KEY_PROTOCOL),
            schemaVersion = ping.getInt(L3HubSyncProtocol.KEY_SCHEMA_VERSION),
            pendingRestore = ping.getBoolean(L3HubSyncProtocol.KEY_PENDING_RESTORE),
            schema = schema,
            reportedPackage = ping.getString(L3HubSyncProtocol.KEY_PACKAGE).orEmpty(),
        )
    }

    suspend fun readSchema(packageName: String): PeerSchema {
        val bundle = call(packageName, L3HubSyncProtocol.METHOD_SCHEMA)
            ?: throw PeerException("对端没有返回库结构")
        val json = bundle.getString(L3HubSyncProtocol.KEY_SCHEMA)
            ?: throw PeerException("对端没有返回库结构")
        return HubJson.decodeFromString(json)
    }

    suspend fun manifest(packageName: String, includeFiles: Boolean, destination: File): PeerManifest {
        val extras = Bundle().apply {
            putBoolean(L3HubSyncProtocol.KEY_INCLUDE_FILES, includeFiles)
        }
        runJob(packageName, L3HubSyncProtocol.METHOD_MANIFEST, extras, destination) {}
        return HubJson.decodeFromString(destination.readText())
    }

    /** 整份备份：数据库与 settings.json 都在里面，调用方只取数据库 */
    suspend fun exportDatabase(packageName: String, destination: File, onLog: (String) -> Unit) {
        val extras = Bundle().apply {
            putBoolean(L3HubSyncProtocol.KEY_INCLUDE_DATABASE, true)
            putBoolean(L3HubSyncProtocol.KEY_INCLUDE_FILES, false)
        }
        runJob(packageName, L3HubSyncProtocol.METHOD_EXPORT, extras, destination, onLog)
    }

    /** 按相对路径要附件；[paths] 为空表示要白名单目录下的全部附件 */
    suspend fun exportFiles(
        packageName: String,
        paths: List<String>,
        destination: File,
        onLog: (String) -> Unit,
    ) {
        val extras = Bundle().apply {
            putBoolean(L3HubSyncProtocol.KEY_INCLUDE_DATABASE, false)
            putBoolean(L3HubSyncProtocol.KEY_INCLUDE_SETTINGS, false)
            putBoolean(L3HubSyncProtocol.KEY_INCLUDE_FILES, true)
            if (paths.isNotEmpty()) putStringArrayList(L3HubSyncProtocol.KEY_PATHS, ArrayList(paths))
        }
        runJob(packageName, L3HubSyncProtocol.METHOD_EXPORT_DELTA, extras, destination, onLog)
    }

    /** 投递备份归档；返回对端已暂存的类别 */
    suspend fun importArchive(
        packageName: String,
        archive: File,
        items: List<String>,
        mode: String,
    ): List<String> {
        val extras = Bundle().apply {
            putStringArrayList(L3HubSyncProtocol.KEY_ITEMS, ArrayList(items))
            putString(L3HubSyncProtocol.KEY_MODE, mode)
        }
        return callWithArchive(packageName, L3HubSyncProtocol.METHOD_IMPORT, archive, extras)
    }

    /** 投递只含附件的增量包 */
    suspend fun importFiles(packageName: String, archive: File): List<String> {
        val extras = Bundle().apply {
            putStringArrayList(L3HubSyncProtocol.KEY_ITEMS, arrayListOf(L3HubSyncProtocol.ITEM_FILES))
            putString(L3HubSyncProtocol.KEY_MODE, L3HubSyncProtocol.MODE_MERGE)
        }
        return callWithArchive(packageName, L3HubSyncProtocol.METHOD_IMPORT_DELTA, archive, extras)
    }

    private fun callWithArchive(
        packageName: String,
        method: String,
        archive: File,
        extras: Bundle,
    ): List<String> {
        ParcelFileDescriptor.open(archive, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            extras.putParcelable(L3HubSyncProtocol.KEY_ARCHIVE, descriptor)
            val result = try {
                call(packageName, method, null, extras)
            } catch (error: Throwable) {
                throw PeerException(describe(error), error)
            }
            if (result == null) throw PeerException("对端没有返回结果")
            return result.getStringArrayList(L3HubSyncProtocol.KEY_STAGED).orEmpty()
        }
    }

    private suspend fun runJob(
        packageName: String,
        method: String,
        extras: Bundle?,
        destination: File,
        onLog: (String) -> Unit,
    ) {
        val callResult = try {
            call(packageName, method, null, extras)
        } catch (error: Throwable) {
            throw PeerException(describe(error), error)
        }
        val token = callResult?.getString(L3HubSyncProtocol.KEY_TOKEN)
            ?: throw PeerException("对端没有返回任务令牌")
        try {
            awaitDone(packageName, token, onLog)
            copyExport(packageName, token, destination)
        } finally {
            runCatching { call(packageName, L3HubSyncProtocol.METHOD_CLEANUP, token) }
        }
    }

    private suspend fun awaitDone(packageName: String, token: String, onLog: (String) -> Unit) {
        val deadline = SystemClock.elapsedRealtime() + JOB_TIMEOUT_MS
        var lastState = ""
        while (true) {
            val result = call(packageName, L3HubSyncProtocol.METHOD_JOB, token)
                ?: throw PeerException("对端没有返回任务状态")
            val state = result.getString(L3HubSyncProtocol.KEY_STATE).orEmpty()
            if (state != lastState) {
                onLog(
                    when (state) {
                        L3HubSyncProtocol.STATE_DONE -> "对端已生成中转文件"
                        L3HubSyncProtocol.STATE_FAILED -> "对端打包失败"
                        else -> "对端正在打包"
                    }
                )
                lastState = state
            }
            when (state) {
                L3HubSyncProtocol.STATE_DONE -> return
                L3HubSyncProtocol.STATE_FAILED -> {
                    val detail = result.getString(L3HubSyncProtocol.KEY_ERROR).orEmpty()
                    throw PeerException(detail.ifBlank { "对端打包失败" })
                }
            }
            if (SystemClock.elapsedRealtime() > deadline) throw PeerException("等待对端打包超时")
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun copyExport(packageName: String, token: String, destination: File) {
        val uri = Uri.parse(L3HubSyncProtocol.exportUri(packageName, token))
        destination.parentFile?.mkdirs()
        val input = context.contentResolver.openInputStream(uri)
            ?: throw PeerException("读不到对端的中转文件")
        input.use { source ->
            destination.outputStream().use { sink -> source.copyTo(sink) }
        }
    }

    private fun call(packageName: String, method: String, arg: String? = null, extras: Bundle? = null): Bundle? =
        context.contentResolver.call(
            Uri.parse(L3HubSyncProtocol.endpoint(packageName)),
            method,
            arg,
            extras,
        )

    private fun describe(error: Throwable): String = when {
        error is SecurityException ->
            "该构建还没有允许数据中心访问，需要更新到包含数据中心的版本"
        error is PeerException -> error.message.orEmpty()
        error.message?.contains("Unknown method") == true ->
            "该构建的同步协议不认识这个操作"
        else -> error.message ?: error.javaClass.simpleName
    }

    private fun readBuildInfo(packageName: String): BuildInfo = runCatching {
        val packageManager = context.packageManager
        @Suppress("DEPRECATION")
        val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        val application = info.applicationInfo
        BuildInfo(
            packageName = packageName,
            label = application?.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() } ?: packageName,
            versionName = info.versionName.orEmpty(),
            versionCode = PackageInfoCompat.getLongVersionCode(info),
            updateTime = info.lastUpdateTime,
            debuggable = application?.let { it.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 } ?: false,
            signature = signatureOf(packageManager, packageName),
            apkBytes = application?.sourceDir?.let { File(it).length() } ?: 0L,
        )
    }.getOrElse {
        BuildInfo(packageName = packageName, label = packageName)
    }

    private fun signatureOf(packageManager: PackageManager, packageName: String): String? = runCatching {
        @Suppress("DEPRECATION")
        val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        val bytes = info.signatures?.firstOrNull()?.toByteArray() ?: return@runCatching null
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
            .take(16)
    }.getOrNull()

    private companion object {
        const val JOB_TIMEOUT_MS = 16 * 60 * 1000L
        const val POLL_INTERVAL_MS = 350L
    }
}
