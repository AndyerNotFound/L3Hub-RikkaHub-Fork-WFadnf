package com.l3hub.database.wfadnf.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 数据中心自己的索引；库文件都在 files/hub 下，索引只记元数据 */
@Serializable
data class HubIndex(
    val packages: Map<String, PackageRecord> = emptyMap(),
)

@Serializable
data class PackageRecord(
    val packageName: String,
    val label: String = "",
    val versionName: String = "",
    val versionCode: Long = 0,
    val schemaVersion: Int = 0,
    val pageSize: Int = 0,
    val databaseBytes: Long = 0,
    val counts: Map<String, Long> = emptyMap(),
    val lastSeenAt: Long = 0,
    val lastSyncAt: Long = 0,
    val snapshots: List<SnapshotRecord> = emptyList(),
    val mirrorFiles: Int = 0,
    val mirrorBytes: Long = 0,
) {
    fun withSnapshot(record: SnapshotRecord): PackageRecord {
        val others = snapshots.filterNot { it.id == record.id }
        return copy(
            snapshots = (others + record).sortedByDescending { it.createdAt },
            lastSyncAt = maxOf(lastSyncAt, record.createdAt),
        )
    }
}

/** 一次拉取的快照；[filesIncluded] 表示同一次拉取里把附件也镜像到了数据中心 */
@Serializable
data class SnapshotRecord(
    val id: String,
    val createdAt: Long,
    val kind: SnapshotKind,
    val schemaVersion: Int,
    val databaseBytes: Long,
    val conversations: Long = 0,
    val messages: Long = 0,
    val identityHash: String? = null,
    val note: String = "",
    val filesIncluded: Boolean = false,
)

@Serializable
enum class SnapshotKind {
    /** 打开某个构建时自动留的一份 */
    AUTO,

    /** 手动拉取 */
    MANUAL,

    /** 覆盖目标之前，先把目标现状存下来 */
    PRE_SYNC,

    /** 从别处导入的库，尚未投递 */
    IMPORTED,
}

/** 同步方式：合并保留目标数据，替换让目标与源一致 */
enum class SyncMode { MERGE, OVERWRITE }

data class DbStats(
    val schemaVersion: Int,
    val pageSize: Int,
    val pageCount: Long,
    val databaseBytes: Long,
    val counts: Map<String, Long>,
    val identityHash: String?,
    val lastUpdateAt: Long,
)

internal val HubJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = true
}
