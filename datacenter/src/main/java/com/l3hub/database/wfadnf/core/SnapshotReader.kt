package com.l3hub.database.wfadnf.core

import android.content.Context
import io.requery.android.database.sqlite.SQLiteDatabase
import java.io.File

/** 读快照库的概况：版本、页大小、体积与几张主表的行数，用于界面上区分各版本的数据量 */
object SnapshotReader {
    val COUNTED_TABLES = listOf(
        "ConversationEntity",
        "message_node",
        "conversation_folder",
        "favorites",
        "memory_file",
        "workspaces",
        "usage_record",
    )

    fun read(context: Context, file: File): DbStats = HubSqlite.openReadOnly(context, file).use { database ->
        val pageSize = pragma(database, "page_size")
        val pageCount = pragma(database, "page_count")
        DbStats(
            schemaVersion = pragma(database, "user_version").toInt(),
            pageSize = pageSize.toInt(),
            pageCount = pageCount,
            databaseBytes = file.length().takeIf { it > 0 } ?: (pageSize * pageCount),
            counts = COUNTED_TABLES.associateWith { table ->
                if (tableExists(database, "main", table)) count(database, "main", table) else 0L
            },
            identityHash = identityHash(database),
            lastUpdateAt = lastUpdateAt(database),
        )
    }

    fun pragma(database: SQLiteDatabase, name: String): Long =
        database.rawQuery("PRAGMA $name", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }

    fun identityHash(database: SQLiteDatabase): String? = runCatching {
        database.rawQuery("SELECT identity_hash FROM room_master_table WHERE id = 42", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    fun tableExists(database: SQLiteDatabase, schema: String, table: String): Boolean =
        database.rawQuery(
            "SELECT 1 FROM $schema.sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table),
        ).use { it.moveToFirst() }

    fun count(database: SQLiteDatabase, schema: String, table: String): Long =
        database.rawQuery("SELECT COUNT(*) FROM $schema.`$table`", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }

    private fun lastUpdateAt(database: SQLiteDatabase): Long {
        if (!tableExists(database, "main", "ConversationEntity")) return 0L
        return runCatching {
            database.rawQuery("SELECT MAX(update_at) FROM `ConversationEntity`", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            }
        }.getOrDefault(0L)
    }
}
