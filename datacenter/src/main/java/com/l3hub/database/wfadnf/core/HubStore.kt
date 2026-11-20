package com.l3hub.database.wfadnf.core

import android.content.Context
import com.l3hub.syncproto.PeerSchema
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 数据中心的仓库：每个包一份目录，快照与附件镜像各占一层。
 *
 * ```
 * files/hub/
 *   index.json                     全部元数据
 *   packages/<包名>/snapshots/<id>/rikka_hub.db + schema.json
 *   packages/<包名>/mirror/<相对路径>
 * ```
 *
 * 快照按包名分开存，正是「各包的数据库各留一份」：跨版本同步时先落到这里的源库与目标库，
 * 换算与合并都在副本上做，任何一边的真实数据都不会被中途改动。
 */
class HubStore(private val context: Context) {
    private val mutex = Mutex()
    private val state = MutableStateFlow(HubIndex())

    val index: StateFlow<HubIndex> = state.asStateFlow()

    val root: File get() = File(context.filesDir, "hub")
    private val tempDir: File get() = File(root, "temp")
    private val indexPath: File get() = File(root, "index.json")

    suspend fun load() = mutex.withLock {
        runCatching {
            if (indexPath.isFile) HubJson.decodeFromString<HubIndex>(indexPath.readText()) else HubIndex()
        }.getOrDefault(HubIndex()).also { state.value = it }
    }

    fun newTempFile(name: String): File {
        check(tempDir.isDirectory || tempDir.mkdirs()) { "无法创建中转目录" }
        return File(tempDir, name)
    }

    fun packageDir(packageName: String): File = File(root, "packages/$packageName")

    fun snapshotsDir(packageName: String): File = File(packageDir(packageName), "snapshots")

    fun snapshotDir(packageName: String, id: String): File = File(snapshotsDir(packageName), id)

    fun databaseFile(packageName: String, id: String): File =
        File(snapshotDir(packageName, id), ArchiveTool.DATABASE_ENTRY)

    fun schemaFile(packageName: String, id: String): File = File(snapshotDir(packageName, id), "schema.json")

    fun mirrorDir(packageName: String): File = File(packageDir(packageName), "mirror")

    fun readSchema(packageName: String, id: String): PeerSchema? = runCatching {
        val file = schemaFile(packageName, id)
        if (!file.isFile) null else HubJson.decodeFromString<PeerSchema>(file.readText())
    }.getOrNull()

    suspend fun recordBuild(
        packageName: String,
        label: String,
        versionName: String,
        versionCode: Long,
        stats: DbStats? = null,
        schema: PeerSchema? = null,
    ) = mutex.withLock {
        update { index ->
            val previous = index.packages[packageName]
            val record = (previous ?: PackageRecord(packageName = packageName)).copy(
                packageName = packageName,
                label = label,
                versionName = versionName,
                versionCode = versionCode,
                schemaVersion = schema?.schemaVersion ?: stats?.schemaVersion ?: previous?.schemaVersion ?: 0,
                pageSize = schema?.pageSize ?: stats?.pageSize ?: previous?.pageSize ?: 0,
                databaseBytes = schema?.databaseBytes ?: stats?.databaseBytes ?: previous?.databaseBytes ?: 0,
                counts = stats?.counts ?: schema?.counts ?: previous?.counts ?: emptyMap(),
                lastSeenAt = System.currentTimeMillis(),
            )
            index.copy(packages = index.packages + (packageName to record))
        }
    }

    /**
     * 把一份数据库落成快照。
     *
     * [source] 是同一个文件系统上的中转文件，直接改名进仓库：整库少复制一遍（1.2 GB 的库省下
     * 一次读写），调用方事后再删原路径是个空操作。
     */
    suspend fun saveSnapshot(
        packageName: String,
        source: File,
        stats: DbStats,
        schema: PeerSchema?,
        kind: SnapshotKind,
        note: String,
        filesIncluded: Boolean,
    ): SnapshotRecord {
        val id = "s-${System.currentTimeMillis()}"
        val directory = snapshotDir(packageName, id)
        check(directory.isDirectory || directory.mkdirs()) { "无法创建快照目录" }
        val target = File(directory, ArchiveTool.DATABASE_ENTRY)
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
        schema?.let { schemaFile(packageName, id).writeText(HubJson.encodeToString(it)) }
        val record = SnapshotRecord(
            id = id,
            createdAt = System.currentTimeMillis(),
            kind = kind,
            schemaVersion = stats.schemaVersion,
            databaseBytes = target.length(),
            conversations = stats.counts["ConversationEntity"] ?: 0,
            messages = stats.counts["message_node"] ?: 0,
            identityHash = stats.identityHash,
            note = note,
            filesIncluded = filesIncluded,
        )
        mutex.withLock {
            update { index ->
                val previous = index.packages[packageName] ?: PackageRecord(packageName = packageName)
                index.copy(packages = index.packages + (packageName to previous.withSnapshot(record)))
            }
        }
        return record
    }

    suspend fun markFilesIncluded(packageName: String, id: String) = mutex.withLock {
        update { index ->
            val previous = index.packages[packageName] ?: return@update index
            index.copy(
                packages = index.packages + (
                    packageName to previous.copy(
                        snapshots = previous.snapshots.map {
                            if (it.id == id) it.copy(filesIncluded = true) else it
                        },
                    )
                    ),
            )
        }
    }

    suspend fun writeSchema(packageName: String, id: String, schema: PeerSchema) {
        schemaFile(packageName, id).writeText(HubJson.encodeToString(schema))
    }

    suspend fun deleteSnapshot(packageName: String, id: String) = mutex.withLock {
        update { index ->
            val previous = index.packages[packageName] ?: return@update index
            index.copy(
                packages = index.packages + (
                    packageName to previous.copy(snapshots = previous.snapshots.filterNot { it.id == id })
                    ),
            )
        }
        snapshotDir(packageName, id).deleteRecursively()
        Unit
    }

    /**
     * 每包只留最近 [keep] 份，但最近的同步前备份始终保留，回滚点不能被挤掉。
     *
     * 每份快照都是一整份库，1.2 GB 的库按 8 份留就是 9.6 GB；留 3 份够回滚，也够比出上一版。
     */
    suspend fun prune(packageName: String, keep: Int = 3) = mutex.withLock {
        val previous = state.value.packages[packageName] ?: return@withLock
        val sorted = previous.snapshots.sortedByDescending { it.createdAt }
        val preSync = sorted.firstOrNull { it.kind == SnapshotKind.PRE_SYNC }
        val keepIds = (sorted.take(keep) + listOfNotNull(preSync)).map { it.id }.toSet()
        val dropped = sorted.filterNot { it.id in keepIds }
        if (dropped.isEmpty()) return@withLock
        update { index ->
            index.copy(
                packages = index.packages + (
                    packageName to previous.copy(snapshots = previous.snapshots.filter { it.id in keepIds })
                    ),
            )
        }
        dropped.forEach { snapshotDir(packageName, it.id).deleteRecursively() }
    }

    suspend fun refreshMirror(packageName: String) = mutex.withLock {
        val (count, bytes) = ArchiveTool.countFiles(mirrorDir(packageName))
        update { index ->
            val previous = index.packages[packageName] ?: return@update index
            index.copy(
                packages = index.packages + (
                    packageName to previous.copy(mirrorFiles = count, mirrorBytes = bytes)
                    ),
            )
        }
    }

    suspend fun clearMirror(packageName: String) {
        mirrorDir(packageName).deleteRecursively()
        refreshMirror(packageName)
    }

    suspend fun clearTemp() {
        tempDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun update(transform: (HubIndex) -> HubIndex) {
        val next = transform(state.value)
        state.value = next
        runCatching {
            check(root.isDirectory || root.mkdirs()) { "无法创建数据中心目录" }
            val temporary = File(root, "index.json.tmp")
            temporary.writeText(HubJson.encodeToString(next))
            Files.move(
                temporary.toPath(),
                indexPath.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }
    }
}
