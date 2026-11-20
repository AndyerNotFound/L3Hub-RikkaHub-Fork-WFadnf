package me.rerere.rikkahub.data.sync

import androidx.sqlite.db.SupportSQLiteDatabase
import com.l3hub.syncproto.PeerSchema
import me.rerere.rikkahub.data.sync.bridge.SyncBridge

/** 统计这几张表的行数；都是同步真正会搬动的内容，其余表要么是索引影子表，要么没有参考价值 */
private val COUNTED_TABLES = listOf(
    "ConversationEntity",
    "message_node",
    "conversation_folder",
    "favorites",
    "memory_file",
    "workspaces",
    "usage_record",
)

/**
 * 把当前库的结构交给调用方（数据中心）。
 *
 * 直接读运行中的库而不是编译期导出的 schema 文件：fork 的 migrations 各自演进，
 * 导出文件未必和线上库一致；`sqlite_master` 里的就是 Room 实际建好的样子。
 * 建表、索引、触发器、视图全部带上，数据中心据此重建一个同结构的库。
 */
internal fun readDeviceSchema(database: SupportSQLiteDatabase): PeerSchema {
    val objects = mutableListOf<PeerSchema.SchemaObject>()
    database.query(
        "SELECT type, name, sql FROM sqlite_master " +
            "WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' " +
            "AND type IN ('table','index','trigger','view') " +
            "ORDER BY CASE type WHEN 'table' THEN 0 WHEN 'index' THEN 1 " +
            "WHEN 'trigger' THEN 2 ELSE 3 END, name",
    ).use { cursor ->
        while (cursor.moveToNext()) {
            objects += PeerSchema.SchemaObject(
                type = cursor.getString(0),
                name = cursor.getString(1),
                sql = cursor.getString(2),
            )
        }
    }
    val pageSize = pragma(database, "page_size")
    val pageCount = pragma(database, "page_count")
    return PeerSchema(
        protocol = SyncBridge.PROTOCOL_VERSION,
        schemaVersion = database.version,
        identityHash = identityHash(database),
        pageSize = pageSize.toInt(),
        databaseBytes = pageSize * pageCount,
        counts = COUNTED_TABLES.associateWith { table -> countRows(database, table) },
        objects = objects,
    )
}

/** Room 用这张表校验结构；重建目标库时必须原样写回，否则一打开就报数据完整性不符 */
private fun identityHash(database: SupportSQLiteDatabase): String? = runCatching {
    database.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()

private fun pragma(database: SupportSQLiteDatabase, name: String): Long = runCatching {
    database.query("PRAGMA $name").use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else 0L
    }
}.getOrDefault(0L)

private fun countRows(database: SupportSQLiteDatabase, table: String): Long = runCatching {
    database.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else 0L
    }
}.getOrDefault(0L)
