package com.l3hub.database.wfadnf.core

import android.content.Context
import io.requery.android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * 把一份换算到目标结构的库合并进目标快照。
 *
 * 规则与 L3Hub 自己的增量合并一致：删除墓碑先落地，对端较新的会话整条替换，本地缺的补进来，
 * 节点与搜索行只跟着这两类会话走；分组、收藏只补缺，工作区与记忆按 updated_at 取新，
 * 用量流水只补本地新会话名下的，避免统计翻倍。两份库此时已经是同一个结构，所以可以直接
 * `INSERT ... SELECT *`。
 *
 * 与源端的区别只有一个：这里是整库对整库，不做水位切分——水位属于两版之间的增量同步，
 * 数据中心保的是「某一边的全部数据」。
 */
object HubMerge {
    private const val TOMBSTONE_TABLE = "sync_tombstone"
    private const val KIND_CONVERSATION = "conversation"
    private const val KIND_FOLDER = "folder"
    private const val KIND_MEMORY = "memory"
    private const val KIND_WORKSPACE = "workspace"

    data class MergeSummary(
        val restoredConversations: Int = 0,
        val refreshedConversations: Int = 0,
        val nodes: Int = 0,
    )

    fun merge(context: Context, target: File, incoming: File): MergeSummary {
        val database = HubSqlite.openReadWrite(context, target)
        try {
            database.execSQL("ATTACH DATABASE ? AS delta", arrayOf(incoming.absolutePath))
            try {
                val targetVersion = SnapshotReader.pragma(database, "main.user_version")
                val incomingVersion = SnapshotReader.pragma(database, "delta.user_version")
                check(targetVersion == incomingVersion) {
                    "两份库的结构版本不一致（$targetVersion / $incomingVersion），不能直接合并"
                }
                database.execSQL("PRAGMA foreign_keys = OFF")
                try {
                    database.beginTransaction()
                    try {
                        applyTombstones(database)
                        val restored = collectConversationIds(database)
                        val refreshed = count(database, "temp", "refreshed_conversations")
                        dropRefreshedConversations(database)
                        database.execSQL(
                            "INSERT OR IGNORE INTO main.`ConversationEntity` " +
                                "SELECT * FROM delta.`ConversationEntity`",
                        )
                        database.execSQL(
                            "INSERT OR IGNORE INTO main.`message_node` SELECT * FROM delta.`message_node` " +
                                "WHERE conversation_id IN (SELECT id FROM temp.restored_conversations " +
                                "UNION SELECT id FROM temp.refreshed_conversations)",
                        )
                        mergeSearchIndex(database)
                        mergeTable(database, "conversation_folder")
                        mergeTable(database, "favorites")
                        mergeWorkspaces(database)
                        mergeMemoryFiles(database)
                        mergeMemoryConsolidation(database)
                        mergeUsageRecords(database)
                        mergeTombstones(database)
                        database.setTransactionSuccessful()
                        val nodes = count(database, "delta", "message_node")
                        return MergeSummary(restored, refreshed, nodes)
                    } finally {
                        database.endTransaction()
                    }
                } finally {
                    database.execSQL("PRAGMA foreign_keys = ON")
                }
            } finally {
                database.execSQL("DETACH DATABASE delta")
            }
        } finally {
            database.close()
        }
    }

    private fun applyTombstones(database: SQLiteDatabase) {
        if (!tableExists(database, "delta", TOMBSTONE_TABLE)) return
        val table = "delta.`$TOMBSTONE_TABLE`"
        database.execSQL(
            "DELETE FROM main.`message_node` WHERE conversation_id IN (" +
                "SELECT target_id FROM $table WHERE kind = '$KIND_CONVERSATION')",
        )
        database.execSQL(
            "DELETE FROM main.`message_fts` WHERE rowid IN (" +
                "SELECT fts_rowid FROM main.`message_fts_rowid` WHERE conversation_id IN (" +
                "SELECT target_id FROM $table WHERE kind = '$KIND_CONVERSATION'))",
        )
        database.execSQL(
            "DELETE FROM main.`message_fts_rowid` WHERE conversation_id IN (" +
                "SELECT target_id FROM $table WHERE kind = '$KIND_CONVERSATION')",
        )
        database.execSQL(
            "DELETE FROM main.`ConversationEntity` WHERE id IN (" +
                "SELECT target_id FROM $table WHERE kind = '$KIND_CONVERSATION')",
        )
        database.execSQL(
            "DELETE FROM main.`conversation_folder` WHERE id IN (" +
                "SELECT target_id FROM $table WHERE kind = '$KIND_FOLDER')",
        )
        database.execSQL(
            "DELETE FROM main.`workspaces` WHERE id IN (" +
                "SELECT target_id FROM $table WHERE kind = '$KIND_WORKSPACE')",
        )
        database.execSQL(
            "DELETE FROM main.`memory_file` WHERE EXISTS (" +
                "SELECT 1 FROM $table t WHERE t.kind = '$KIND_MEMORY' " +
                "AND t.target_id = main.`memory_file`.`assistant_id` || char(10) || main.`memory_file`.`path`)",
        )
    }

    /** 建 restored / refreshed 两张临时表，返回本地缺的会话数 */
    private fun collectConversationIds(database: SQLiteDatabase): Int {
        val skipLocalTombstones = if (tableExists(database, "main", TOMBSTONE_TABLE)) {
            " AND id NOT IN (SELECT target_id FROM main.`$TOMBSTONE_TABLE` " +
                "WHERE kind = '$KIND_CONVERSATION')"
        } else {
            ""
        }
        database.execSQL("DROP TABLE IF EXISTS temp.restored_conversations")
        database.execSQL("CREATE TEMP TABLE restored_conversations (id TEXT PRIMARY KEY)")
        database.execSQL(
            "INSERT OR IGNORE INTO temp.restored_conversations(id) " +
                "SELECT id FROM delta.`ConversationEntity` " +
                "WHERE id NOT IN (SELECT id FROM main.`ConversationEntity`)" + skipLocalTombstones,
        )
        database.execSQL("DROP TABLE IF EXISTS temp.refreshed_conversations")
        database.execSQL("CREATE TEMP TABLE refreshed_conversations (id TEXT PRIMARY KEY)")
        database.execSQL(
            "INSERT OR IGNORE INTO temp.refreshed_conversations(id) " +
                "SELECT d.id FROM delta.`ConversationEntity` AS d " +
                "JOIN main.`ConversationEntity` AS m ON m.id = d.id " +
                "WHERE d.update_at > m.update_at" + skipLocalTombstones.replace("id NOT IN", "d.id NOT IN"),
        )
        return count(database, "temp", "restored_conversations").toInt()
    }

    private fun dropRefreshedConversations(database: SQLiteDatabase) {
        val has = database.rawQuery("SELECT 1 FROM temp.refreshed_conversations LIMIT 1", null)
            .use { it.moveToFirst() }
        if (!has) return
        database.execSQL(
            "DELETE FROM main.`ConversationEntity` " +
                "WHERE id IN (SELECT id FROM temp.refreshed_conversations)",
        )
        database.execSQL(
            "DELETE FROM main.`message_node` " +
                "WHERE conversation_id IN (SELECT id FROM temp.refreshed_conversations)",
        )
        database.execSQL(
            "DELETE FROM main.`message_fts` WHERE rowid IN (" +
                "SELECT fts_rowid FROM main.`message_fts_rowid` " +
                "WHERE conversation_id IN (SELECT id FROM temp.refreshed_conversations))",
        )
        database.execSQL(
            "DELETE FROM main.`message_fts_rowid` " +
                "WHERE conversation_id IN (SELECT id FROM temp.refreshed_conversations)",
        )
    }

    private fun mergeSearchIndex(database: SQLiteDatabase) {
        if (!tableExists(database, "delta", "message_fts")) return
        database.execSQL(
            "INSERT INTO main.`message_fts`(text, node_id, message_id, conversation_id) " +
                "SELECT text, node_id, message_id, conversation_id FROM delta.`message_fts` " +
                "WHERE conversation_id IN (SELECT id FROM temp.restored_conversations " +
                "UNION SELECT id FROM temp.refreshed_conversations)",
        )
        database.execSQL(
            "INSERT INTO main.`message_fts_rowid`(fts_rowid, conversation_id) " +
                "SELECT f.rowid, f.conversation_id FROM main.`message_fts` AS f " +
                "WHERE f.rowid NOT IN (SELECT fts_rowid FROM main.`message_fts_rowid`)",
        )
    }

    private fun mergeTable(database: SQLiteDatabase, table: String) {
        if (!tableExists(database, "delta", table)) return
        database.execSQL("INSERT OR IGNORE INTO main.`$table` SELECT * FROM delta.`$table`")
    }

    private fun mergeWorkspaces(database: SQLiteDatabase) {
        if (!tableExists(database, "delta", "workspaces")) return
        database.execSQL("DROP TABLE IF EXISTS temp.refreshed_workspaces")
        database.execSQL("CREATE TEMP TABLE refreshed_workspaces (id TEXT PRIMARY KEY)")
        database.execSQL(
            "INSERT OR IGNORE INTO temp.refreshed_workspaces(id) " +
                "SELECT d.id FROM delta.`workspaces` AS d " +
                "JOIN main.`workspaces` AS m ON m.id = d.id WHERE d.updated_at > m.updated_at",
        )
        database.execSQL(
            "DELETE FROM main.`workspaces` WHERE id IN (SELECT id FROM temp.refreshed_workspaces)",
        )
        database.execSQL("INSERT OR IGNORE INTO main.`workspaces` SELECT * FROM delta.`workspaces`")
    }

    private fun mergeMemoryFiles(database: SQLiteDatabase) {
        if (!tableExists(database, "delta", "memory_file")) return
        database.execSQL("DROP TABLE IF EXISTS temp.refreshed_memory")
        database.execSQL("CREATE TEMP TABLE refreshed_memory (assistant_id TEXT, path TEXT)")
        database.execSQL(
            "INSERT INTO temp.refreshed_memory(assistant_id, path) " +
                "SELECT d.assistant_id, d.path FROM delta.`memory_file` AS d " +
                "JOIN main.`memory_file` AS m ON m.assistant_id = d.assistant_id AND m.path = d.path " +
                "WHERE d.updated_at > m.updated_at",
        )
        database.execSQL(
            "DELETE FROM main.`memory_file` WHERE EXISTS (" +
                "SELECT 1 FROM temp.refreshed_memory r " +
                "WHERE r.assistant_id = main.`memory_file`.`assistant_id` " +
                "AND r.path = main.`memory_file`.`path`)",
        )
        database.execSQL("INSERT OR IGNORE INTO main.`memory_file` SELECT * FROM delta.`memory_file`")
    }

    private fun mergeMemoryConsolidation(database: SQLiteDatabase) {
        if (!tableExists(database, "delta", "memory_consolidation")) return
        database.execSQL(
            "DELETE FROM main.`memory_consolidation` WHERE conversation_id IN (" +
                "SELECT d.conversation_id FROM delta.`memory_consolidation` AS d " +
                "JOIN main.`memory_consolidation` AS m ON m.conversation_id = d.conversation_id " +
                "WHERE d.consolidated_at > m.consolidated_at)",
        )
        database.execSQL(
            "INSERT OR IGNORE INTO main.`memory_consolidation` SELECT * FROM delta.`memory_consolidation`",
        )
    }

    private fun mergeUsageRecords(database: SQLiteDatabase) {
        if (!tableExists(database, "delta", "usage_record")) return
        // 自增 id 两边会重号，不抄 id；只补本地新会话名下的流水，避免统计翻倍。
        // cost 是 v37 才有的列，老结构里没有，按实际列取交集再拼语句。
        val available = columnsOf(database, "usage_record")
        val wanted = listOf(
            "created_at", "source", "provider_id", "model_id", "model_name", "assistant_id",
            "conversation_id", "prompt_tokens", "completion_tokens", "cached_tokens", "cost",
        ).filter { it in available }
        if (wanted.isEmpty()) return
        val names = wanted.joinToString(", ") { "`$it`" }
        database.execSQL(
            "INSERT INTO main.`usage_record`($names) SELECT $names FROM delta.`usage_record` " +
                "WHERE conversation_id IN (SELECT id FROM temp.restored_conversations)",
        )
    }

    private fun columnsOf(database: SQLiteDatabase, table: String): Set<String> =
        database.rawQuery("PRAGMA main.table_info(\"$table\")", null).use { cursor ->
            val index = cursor.getColumnIndex("name")
            buildSet { while (cursor.moveToNext()) add(cursor.getString(index)) }
        }

    private fun mergeTombstones(database: SQLiteDatabase) {
        if (!tableExists(database, "main", TOMBSTONE_TABLE)) return
        if (!tableExists(database, "delta", TOMBSTONE_TABLE)) return
        database.execSQL(
            "INSERT OR REPLACE INTO main.`$TOMBSTONE_TABLE`(kind, target_id, deleted_at) " +
                "SELECT kind, target_id, deleted_at FROM delta.`$TOMBSTONE_TABLE`",
        )
    }

    private fun tableExists(database: SQLiteDatabase, schema: String, table: String): Boolean =
        database.rawQuery(
            "SELECT 1 FROM $schema.sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table),
        ).use { it.moveToFirst() }

    private fun count(database: SQLiteDatabase, schema: String, table: String): Long =
        database.rawQuery("SELECT COUNT(*) FROM $schema.`$table`", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
}
