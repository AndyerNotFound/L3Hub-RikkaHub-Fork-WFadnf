package me.rerere.rikkahub.data.sync

import android.content.Context
import com.l3hub.syncproto.BundleFormat
import java.io.File

/** 增量层要带的附件：变化/新增的路径，以及链上有、本地已经没有的路径 */
data class BundleFilePlan(val paths: List<String>, val deleted: List<String>)

/**
 * 远端（WebDAV / S3）上的一个分层备份集。
 *
 * 远端不能原地追加，所以一个备份集是「base 对象 + 若干层对象」：链首 `backup_x.l3b` 带文件头，
 * 之后每次备份只上传一个裸记录 `backup_x.l3b.d001`。恢复时按顺序拼成一份完整的包，删除时整套一起删。
 *
 * 这一层是 base 还是 delta 由 [BackupManager.shouldStartNewChain] 判定：换设备、结构版本变了、
 * 链太长或太旧都重开一条链，绝不把别处的增量接在本机链后面。
 */
class RemoteBundleBackup(
    private val context: Context,
    private val backupManager: BackupManager,
    private val targetKey: String,
) {
    data class Plan(
        val base: Boolean,
        val baseName: String,
        val layerName: String,
        val since: Long,
        val state: BundleChainStore.ChainState?,
        val files: BundleFilePlan,
    )

    data class Layer(
        val baseName: String,
        val name: String,
        val file: File,
        val layer: BundleFormat.Layer,
        val previous: BundleChainStore.ChainState?,
    )

    /** 依据远端现有对象与本地链状态，决定这一层是什么、叫什么名字、带哪些附件 */
    fun plan(
        remoteNames: Set<String>,
        includeDatabase: Boolean,
        includeFiles: Boolean,
        timestamp: String,
    ): Plan {
        var state = BundleChainStore.load(context, targetKey)
        if (state != null && state.baseName !in remoteNames) {
            // 远端那份已经不在了（被删、换了目录）：本地状态作废，重开一条链
            BundleChainStore.clear(context, targetKey)
            state = null
        }
        val base = backupManager.shouldStartNewChain(state?.summary(), includeDatabase)
        if (base) {
            return Plan(
                base = true,
                baseName = "backup_$timestamp.l3b",
                layerName = "backup_$timestamp.l3b",
                since = 0L,
                state = null,
                files = BundleFilePlan(emptyList(), emptyList()),
            )
        }
        val current = checkNotNull(state)
        val local = if (includeFiles) {
            collectSyncableFiles(context.filesDir).associate { it.path to it.size }
        } else {
            emptyMap()
        }
        val known = current.files
        return Plan(
            base = false,
            baseName = current.baseName,
            layerName = "${current.baseName}.d%03d".format(current.layerCount + 1),
            since = BackupManager.bundleWatermark(current.lastCreatedAt),
            state = current,
            files = BundleFilePlan(
                paths = if (includeFiles) {
                    local.filter { (path, size) -> known[path] != size }.keys.toList()
                } else {
                    emptyList()
                },
                deleted = if (includeFiles) known.keys.filter { it !in local } else emptyList(),
            ),
        )
    }

    /** 生成待上传的那一份文件：base 带文件头，增量层是裸记录 */
    suspend fun prepare(
        plan: Plan,
        includeDatabase: Boolean,
        includeFiles: Boolean,
        compress: Boolean = true,
    ): Layer {
        val prepared = backupManager.prepareBundleLayer(
            BundleRequest(
                includeDatabase = includeDatabase,
                includeFiles = includeFiles,
                base = plan.base,
                includeSettings = true,
                since = plan.since,
                paths = if (plan.base) null else plan.files.paths,
                deletedFiles = plan.files.deleted,
                previousSettingsHash = plan.state?.settingsHash,
                compress = compress,
            )
        )
        val file = File.createTempFile("remote-layer-", ".l3b", context.cacheDir)
        val stored = try {
            backupManager.appendBundle(file, prepared, withMagic = plan.base)
        } finally {
            prepared.payload.delete()
        }
        return Layer(
            baseName = plan.baseName,
            name = plan.layerName,
            file = file,
            layer = stored,
            previous = plan.state,
        )
    }

    /** 上传成功之后推进链状态；失败时调用 [discard]，下次仍然按旧状态再试一遍 */
    fun commit(layer: Layer) {
        BundleChainStore.record(context, targetKey, layer.baseName, layer.layer, layer.previous)
    }

    fun discard(layer: Layer) {
        layer.file.delete()
    }
}
