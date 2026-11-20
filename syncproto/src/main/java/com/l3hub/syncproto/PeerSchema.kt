package com.l3hub.syncproto

import kotlinx.serialization.Serializable

/**
 * 一个 L3Hub 构建的数据库结构描述，由 [L3HubSyncProtocol.METHOD_SCHEMA] 返回。
 *
 * 结构与统计直接取自运行中的库（`sqlite_master` 与几张大表），不是编译期导出的 schema 文件，
 * 因此对任何 fork、任何分支都成立。数据中心用它把别处的数据换算成目标库的结构：
 * 建表语句、Room 的 identity_hash、页大小都要和目标一致，Room 打开时才不会判定结构不符。
 */
@Serializable
data class PeerSchema(
    val protocol: Int = 0,
    val schemaVersion: Int = 0,
    /** Room 的 identity_hash；目标库重建时必须原样写回，否则 Room 拒绝打开 */
    val identityHash: String? = null,
    val pageSize: Int = 0,
    val databaseBytes: Long = 0,
    /** 只统计关心的几张表，键为表名 */
    val counts: Map<String, Long> = emptyMap(),
    val objects: List<SchemaObject> = emptyList(),
) {
    /** 参与建库的对象：表、索引、触发器、视图 */
    @Serializable
    data class SchemaObject(
        val type: String,
        val name: String,
        val sql: String,
    )
}
