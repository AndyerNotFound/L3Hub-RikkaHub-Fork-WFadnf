package com.l3hub.database.wfadnf.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

data class BuildUi(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val schemaVersion: Int,
    val protocol: Int,
    val identityHash: String?,
    val pendingRestore: Boolean,
    val installed: Boolean,
    val supported: Boolean,
    val reason: String?,
    val counts: Map<String, Long>,
    val databaseBytes: Long,
    val mirrorFiles: Int,
    val mirrorBytes: Long,
    val snapshots: List<SnapshotRecord>,
    val lastSeenAt: Long,
)

data class HubUiState(
    val loading: Boolean = true,
    val scanning: Boolean = false,
    val builds: List<BuildUi> = emptyList(),
    val busyLabel: String? = null,
    val log: List<String> = emptyList(),
    val message: String? = null,
    val error: String? = null,
    val storageBytes: Long = 0,
)

/**
 * 界面与数据层之间唯一的调度点。
 *
 * 所有耗时动作都排在一个协程里，同一时刻只允许一个：同时拉两份快照会把对端的中转目录与
 * 数据中心的临时文件搅在一起。状态用 StateFlow 往外抛，界面只读。
 */
class HubController private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = HubStore(context)
    private val peers = PeerClient(context)
    private val engine = SyncEngine(context, peers, store)
    private val _state = MutableStateFlow(HubUiState())
    private val busy = AtomicBoolean(false)

    @Volatile
    private var discovered: List<PeerClient.Build> = emptyList()

    val state: StateFlow<HubUiState> = _state.asStateFlow()

    init {
        scope.launch {
            store.index.collect { publish() }
        }
    }

    fun start() {
        scope.launch {
            store.load()
            store.clearTemp()
            publish()
            _state.update { it.copy(loading = false) }
            scan()
        }
    }

    fun scan() {
        if (!busy.compareAndSet(false, true)) return
        scope.launch {
            _state.update { it.copy(scanning = true, error = null) }
            try {
                discovered = peers.discover()
                discovered.forEach { build ->
                    if (build is PeerClient.Build.Ready) {
                        store.recordBuild(
                            packageName = build.info.packageName,
                            label = build.info.label,
                            versionName = build.info.versionName,
                            versionCode = build.info.versionCode,
                            schema = build.schema,
                        )
                    }
                }
            } catch (error: Throwable) {
                _state.update { it.copy(error = error.message ?: "扫描失败") }
            } finally {
                busy.set(false)
                _state.update { it.copy(scanning = false) }
                publish()
            }
        }
    }

    fun pull(packageName: String, includeFiles: Boolean) = run("拉取快照") {
        val build = readyBuild(packageName)
        val report = engine.pull(
            build = build,
            includeFiles = includeFiles,
            kind = SnapshotKind.MANUAL,
            note = "",
            onLog = ::log,
        )
        "已保存 ${build.info.label} 的快照：会话 ${report.snapshot.conversations} 条" +
            if (report.filesIncluded) "，附件 ${report.files} 个" else ""
    }

    fun push(
        sourcePackage: String,
        snapshotId: String,
        targetPackage: String,
        mode: SyncMode,
        includeFiles: Boolean,
    ) = run("同步数据") {
        val record = store.index.value.packages[sourcePackage]?.snapshots
            ?.firstOrNull { it.id == snapshotId }
            ?: throw PeerException("找不到这份快照，可能已被清理")
        val database = store.databaseFile(sourcePackage, snapshotId)
        if (!database.isFile) throw PeerException("快照文件缺失")
        val target = readyBuild(targetPackage)
        val report = engine.push(
            snapshot = SnapshotRef(
                packageName = sourcePackage,
                record = record,
                database = database,
                schema = store.readSchema(sourcePackage, snapshotId),
            ),
            target = target,
            mode = mode,
            includeFiles = includeFiles,
            onLog = ::log,
        )
        val action = if (mode == SyncMode.MERGE) "合并" else "替换"
        "已向 ${target.info.label} $action ${report.merge?.restoredConversations ?: record.conversations} 条会话，" +
            "重启该应用后生效"
    }

    fun rollback(packageName: String, snapshotId: String) = run("回滚快照") {
        val record = store.index.value.packages[packageName]?.snapshots
            ?.firstOrNull { it.id == snapshotId }
            ?: throw PeerException("找不到这份快照")
        val database = store.databaseFile(packageName, snapshotId)
        if (!database.isFile) throw PeerException("快照文件缺失")
        val target = readyBuild(packageName)
        engine.push(
            snapshot = SnapshotRef(packageName, record, database, store.readSchema(packageName, snapshotId)),
            target = target,
            mode = SyncMode.OVERWRITE,
            includeFiles = record.filesIncluded,
            onLog = ::log,
        )
        "已把 ${record.createdAt.formatTime()} 的快照投递给 ${target.info.label}，重启后生效"
    }

    fun deleteSnapshot(packageName: String, snapshotId: String) {
        scope.launch { store.deleteSnapshot(packageName, snapshotId) }
    }

    fun clearMirror(packageName: String) {
        scope.launch {
            store.clearMirror(packageName)
            _state.update { it.copy(message = "已清空 ${packageName} 的附件镜像") }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null, error = null) }
    }

    private fun readyBuild(packageName: String): PeerClient.Build.Ready {
        val build = discovered.firstOrNull { it.info.packageName == packageName }
            ?: throw PeerException("$packageName 没有响应，先重新扫描")
        return build as? PeerClient.Build.Ready
            ?: throw PeerException((build as PeerClient.Build.Unavailable).reason)
    }

    private fun run(label: String, block: suspend () -> String) {
        if (!busy.compareAndSet(false, true)) return
        scope.launch {
            _state.update {
                it.copy(busyLabel = label, log = emptyList(), error = null, message = null)
            }
            try {
                val result = block()
                _state.update { it.copy(message = result) }
            } catch (error: PeerException) {
                _state.update { it.copy(error = error.message ?: "同步失败") }
            } catch (error: Throwable) {
                _state.update { it.copy(error = error.message ?: error.javaClass.simpleName) }
            } finally {
                busy.set(false)
                _state.update { it.copy(busyLabel = null) }
                publish()
            }
        }
    }

    private fun log(line: String) {
        _state.update { it.copy(log = (it.log + line).takeLast(200)) }
    }

    private fun publish() {
        val index = store.index.value
        val byName = discovered.associateBy { it.info.packageName }
        val names = (byName.keys + index.packages.keys).sorted()
        val builds = names.map { packageName ->
            val build = byName[packageName]
            val record = index.packages[packageName]
            val ready = build as? PeerClient.Build.Ready
            BuildUi(
                packageName = packageName,
                label = build?.info?.label ?: record?.label ?: packageName,
                versionName = build?.info?.versionName ?: record?.versionName.orEmpty(),
                versionCode = build?.info?.versionCode ?: record?.versionCode ?: 0,
                schemaVersion = ready?.schemaVersion ?: record?.schemaVersion ?: 0,
                protocol = ready?.protocol ?: 0,
                identityHash = ready?.schema?.identityHash ?: record?.snapshots?.firstOrNull()?.identityHash,
                pendingRestore = ready?.pendingRestore ?: false,
                installed = build != null,
                supported = ready != null,
                reason = (build as? PeerClient.Build.Unavailable)?.reason,
                counts = record?.counts ?: ready?.schema?.counts.orEmpty(),
                databaseBytes = record?.databaseBytes ?: ready?.schema?.databaseBytes ?: 0,
                mirrorFiles = record?.mirrorFiles ?: 0,
                mirrorBytes = record?.mirrorBytes ?: 0,
                snapshots = record?.snapshots.orEmpty(),
                lastSeenAt = record?.lastSeenAt ?: 0,
            )
        }
        val storage = builds.sumOf { build ->
            build.snapshots.sumOf { it.databaseBytes } + build.mirrorBytes
        }
        _state.update { it.copy(builds = builds, storageBytes = storage) }
    }

    companion object {
        @Volatile
        private var instance: HubController? = null

        fun of(context: Context): HubController = instance ?: synchronized(this) {
            instance ?: HubController(context.applicationContext).also { instance = it }
        }
    }
}

internal fun Long.formatTime(): String = runCatching {
    java.time.Instant.ofEpochMilli(this)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))
}.getOrDefault("--")
