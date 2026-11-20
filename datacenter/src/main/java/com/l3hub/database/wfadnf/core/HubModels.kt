package com.l3hub.database.wfadnf.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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

/**
 * 一次「导出数据库」的请求。
 *
 * 放在核心层而不是界面里：选位置要经过系统文件选择器，界面可能在那期间被重建，
 * 请求记在 [HubController] 上才不会丢。
 */
data class ExportRequest(
    val packageName: String,
    val snapshotId: String?,
    val label: String,
) {
    fun fileName(): String {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val base = label.replace(NON_FILE_CHARS, "-").trim('-').ifBlank { "l3hub" }
        return "${base}_rikka_hub_$stamp.db"
    }

    private companion object {
        val NON_FILE_CHARS = Regex("[^A-Za-z0-9._-]+")
    }
}

internal val HubJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = true
}
