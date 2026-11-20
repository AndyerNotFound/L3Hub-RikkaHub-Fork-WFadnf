package com.l3hub.database.wfadnf.core

import android.content.Context
import com.l3hub.syncproto.L3HubSyncProtocol
import com.l3hub.syncproto.PeerSchema
import java.io.File

/** 一份可以投递的快照：记录、库文件与拉取时存下的结构 */
data class SnapshotRef(
    val packageName: String,
    val record: SnapshotRecord,
    val database: File,
    val schema: PeerSchema?,
)

data class PullReport(
    val snapshot: SnapshotRecord,
    val files: Int,
    val filesIncluded: Boolean,
)

data class PushReport(
    val staged: List<String>,
    val translation: TranslationReport?,
    val merge: HubMerge.MergeSummary?,
    val pushedBytes: Long,
    val files: Int,
)

/**
 * 拉取与投递。
 *
 * 拉取永远是「整份数据库 + 增量附件」：库只有整份才带得走全部表（设置、字体、技能这些也都靠它），
 * 附件按清单与大小差异只取缺的，重复拉取几乎不传附件。
 *
 * 投递按两边结构分两条路：结构一致直接搬；结构不同先用目标的结构重建（[SchemaTranslation]），
 * 再按合并或替换落到目标。合并与替换都在数据中心的副本上先做完，交付给目标的是一份结构正确、
 * 内容已定的库——目标端只需要在下次启动时整份换掉，不会执行到一半的合并。
 */
class SyncEngine(
    private val context: Context,
    private val peers: PeerClient,
    private val store: HubStore,
) {
    suspend fun pull(
        build: PeerClient.Build.Ready,
        includeFiles: Boolean,
        kind: SnapshotKind,
        note: String,
        onLog: (String) -> Unit,
    ): PullReport {
        val packageName = build.info.packageName
        val stamp = System.currentTimeMillis()
        val archive = store.newTempFile("export-$stamp.zip")
        val database = store.newTempFile("export-$stamp.db")
        try {
            onLog("请求 ${build.info.label} 打包")
            peers.exportDatabase(packageName, archive, onLog)
            onLog("已取回归档 ${formatBytes(archive.length())}")
            ArchiveTool.extractDatabase(archive, database)
            val stats = SnapshotReader.read(context, database)
            val schema = build.schema ?: runCatching { peers.readSchema(packageName) }.getOrNull()
            onLog(
                "数据库 v${stats.schemaVersion}，会话 ${stats.counts["ConversationEntity"] ?: 0}，" +
                    "消息 ${stats.counts["message_node"] ?: 0}",
            )
            val record = store.saveSnapshot(
                packageName = packageName,
                source = database,
                stats = stats,
                schema = schema,
                kind = kind,
                note = note,
                filesIncluded = false,
            )
            store.recordBuild(
                packageName = packageName,
                label = build.info.label,
                versionName = build.info.versionName,
                versionCode = build.info.versionCode,
                stats = stats,
                schema = schema,
            )
            var files = 0
            if (includeFiles) {
                files = pullFiles(packageName, onLog)
                if (files >= 0) store.markFilesIncluded(packageName, record.id)
            }
            store.prune(packageName)
            return PullReport(
                snapshot = record.copy(filesIncluded = includeFiles),
                files = files.coerceAtLeast(0),
                filesIncluded = includeFiles,
            )
        } finally {
            archive.delete()
            database.delete()
        }
    }

    suspend fun push(
        snapshot: SnapshotRef,
        target: PeerClient.Build.Ready,
        mode: SyncMode,
        includeFiles: Boolean,
        onLog: (String) -> Unit,
    ): PushReport {
        val targetPackage = target.info.packageName
        if (target.pendingRestore) {
            throw PeerException("${target.info.label} 有一份数据等待安装，先打开它完成安装再同步")
        }
        val sourceStats = SnapshotReader.read(context, snapshot.database)
        // 目标可能在扫描之后升过级，投递前重新取一次结构；取不到再用扫描时那份
        val targetSchema = runCatching { peers.readSchema(targetPackage) }.getOrNull() ?: target.schema
        val sameStructure = targetSchema != null &&
            targetSchema.schemaVersion == sourceStats.schemaVersion &&
            (
                targetSchema.identityHash == null || sourceStats.identityHash == null ||
                    targetSchema.identityHash == sourceStats.identityHash
                )
        if (!sameStructure && targetSchema == null) {
            throw PeerException("${target.info.label} 不支持结构导出，无法接收 v${sourceStats.schemaVersion} 的数据")
        }

        val stamp = System.currentTimeMillis()
        val temporary = mutableListOf<File>()
        try {
            val incoming: File
            var translation: TranslationReport? = null
            if (sameStructure) {
                onLog("两边结构一致（v${sourceStats.schemaVersion}），直接搬运")
                incoming = store.newTempFile("incoming-$stamp.db").also {
                    snapshot.database.copyTo(it, overwrite = true)
                    temporary += it
                }
            } else {
                checkNotNull(targetSchema)
                onLog("结构不同：源 v${sourceStats.schemaVersion} → 目标 v${targetSchema.schemaVersion}，开始换算")
                incoming = store.newTempFile("incoming-$stamp.db").also { temporary += it }
                translation = SchemaTranslation.translate(context, snapshot.database, targetSchema, incoming)
                val rows = translation.copied.values.sum()
                onLog("换算完成：抄入 $rows 行，丢弃 ${translation.droppedColumns.size} 列")
                translation.skippedTables.forEach { onLog("跳过表 $it（结构无法补齐）") }
                translation.warnings.take(6).forEach { onLog("提示：$it") }
            }

            var mergeSummary: HubMerge.MergeSummary? = null
            val payload: File
            if (mode == SyncMode.MERGE) {
                onLog("先把目标现状拉一份，作为回滚点")
                val preSync = pull(target, includeFiles = false, kind = SnapshotKind.PRE_SYNC, note = "同步前备份", onLog = onLog)
                val merged = store.newTempFile("merged-$stamp.db").also { temporary += it }
                store.databaseFile(targetPackage, preSync.snapshot.id).copyTo(merged, overwrite = true)
                val summary = HubMerge.merge(context, merged, incoming)
                mergeSummary = summary
                onLog(
                    "合并完成：新增 ${summary.restoredConversations} 条会话，" +
                        "更新 ${summary.refreshedConversations} 条",
                )
                payload = merged
            } else {
                payload = incoming
            }

            var files = 0
            val attachments = mutableMapOf<String, File>()
            if (includeFiles && mode == SyncMode.OVERWRITE) {
                attachments.putAll(mirrorFiles(snapshot.packageName))
            } else if (includeFiles) {
                // 合并模式先把附件补进目标：增量包与整份恢复各占一个待安装目录，互不覆盖
                files = pushFiles(snapshot.packageName, targetPackage, onLog)
            }

            val archive = store.newTempFile("push-$stamp.zip").also { temporary += it }
            ArchiveTool.buildDatabaseArchive(payload, archive, attachments)
            val items = buildList {
                add(L3HubSyncProtocol.ITEM_CHAT_RECORDS)
                if (attachments.isNotEmpty()) add(L3HubSyncProtocol.ITEM_FILES)
            }
            onLog("投递 ${formatBytes(archive.length())}")
            val staged = peers.importArchive(
                packageName = targetPackage,
                archive = archive,
                items = items,
                mode = L3HubSyncProtocol.MODE_OVERWRITE,
            )
            store.recordBuild(
                packageName = targetPackage,
                label = target.info.label,
                versionName = target.info.versionName,
                versionCode = target.info.versionCode,
                schema = targetSchema,
            )
            return PushReport(
                staged = staged,
                translation = translation,
                merge = mergeSummary,
                pushedBytes = archive.length(),
                files = if (attachments.isNotEmpty()) attachments.size else files,
            )
        } finally {
            temporary.forEach { it.delete() }
        }
    }

    /** 把附件镜像成「相对路径到文件」的映射，用于镜像模式的完整投递 */
    fun mirrorFiles(packageName: String): Map<String, File> {
        val mirror = store.mirrorDir(packageName)
        if (!mirror.isDirectory) return emptyMap()
        return mirror.walkTopDown()
            .filter { it.isFile }
            .mapNotNull { file ->
                val path = file.relativeTo(mirror).invariantSeparatorsPath
                if (ArchiveTool.isSafeRelative(path)) path to file else null
            }
            .toMap()
    }

    private suspend fun pullFiles(packageName: String, onLog: (String) -> Unit): Int {
        val manifestFile = store.newTempFile("manifest-${System.currentTimeMillis()}.json")
        val mirror = store.mirrorDir(packageName)
        try {
            val manifest = peers.manifest(packageName, includeFiles = true, destination = manifestFile)
            val missing = manifest.files.filter { entry ->
                ArchiveTool.isSafeRelative(entry.path) && run {
                    val local = File(mirror, entry.path)
                    !local.isFile || local.length() != entry.size
                }
            }
            if (missing.isEmpty()) {
                store.refreshMirror(packageName)
                return 0
            }
            var fetched = 0
            missing.chunked(FILE_BATCH).forEachIndexed { index, chunk ->
                val zip = store.newTempFile("files-${System.currentTimeMillis()}-$index.zip")
                try {
                    onLog("拉取附件 ${index + 1}/${(missing.size + FILE_BATCH - 1) / FILE_BATCH}（${chunk.size} 个）")
                    peers.exportFiles(packageName, chunk.map { it.path }, zip, onLog)
                    ArchiveTool.extractFiles(zip, mirror)
                    fetched += chunk.size
                } finally {
                    zip.delete()
                }
            }
            store.refreshMirror(packageName)
            return fetched
        } finally {
            manifestFile.delete()
        }
    }

    private suspend fun pushFiles(sourcePackage: String, targetPackage: String, onLog: (String) -> Unit): Int {
        val local = mirrorFiles(sourcePackage)
        if (local.isEmpty()) return 0
        val manifestFile = store.newTempFile("manifest-${System.currentTimeMillis()}.json")
        val mirror = store.mirrorDir(sourcePackage)
        try {
            val manifest = peers.manifest(targetPackage, includeFiles = true, destination = manifestFile)
            val remote = manifest.files.associateBy { it.path }
            val missing = local.entries.filter { (path, file) -> remote[path]?.size != file.length() }
            if (missing.isEmpty()) return 0
            missing.chunked(FILE_BATCH).forEachIndexed { index, chunk ->
                val zip = store.newTempFile("files-push-${System.currentTimeMillis()}-$index.zip")
                try {
                    onLog("补附件 ${index + 1}/${(missing.size + FILE_BATCH - 1) / FILE_BATCH}（${chunk.size} 个）")
                    ArchiveTool.buildFilesArchive(mirror, chunk.map { it.key }, zip)
                    peers.importFiles(targetPackage, zip)
                } finally {
                    zip.delete()
                }
            }
            return missing.size
        } finally {
            manifestFile.delete()
        }
    }

    companion object {
        private const val FILE_BATCH = 400

        fun formatBytes(bytes: Long): String = when {
            bytes >= 1L shl 30 -> String.format("%.1f GB", bytes / (1L shl 30).toDouble())
            bytes >= 1L shl 20 -> String.format("%.1f MB", bytes / (1L shl 20).toDouble())
            bytes >= 1L shl 10 -> String.format("%.0f KB", bytes / (1L shl 10).toDouble())
            else -> "$bytes B"
        }
    }
}
