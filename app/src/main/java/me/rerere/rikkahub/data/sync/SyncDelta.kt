package me.rerere.rikkahub.data.sync

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.database.sqlite.SQLiteDatabaseConfiguration
import me.rerere.rikkahub.data.db.SQLiteConfiguration
import java.io.File

/** 一次增量导出或应用的结果，用于界面与日志 */
data class SyncDeltaSummary(
    val conversations: Int = 0,
    val nodes: Int = 0,
)

/**
 * 增量同步的数据层。
 *
 * 导出的增量是一个独立的 SQLite 库：按会话的 update_at 水位抄出整条会话与它的节点、搜索行，
 * 再加上这段时间内的删除墓碑、整表很小的分组与收藏、以及有 updated_at 的记忆与工作区。
 * 会话本身还是整条搬——节点没有自己的时间戳，一条会话里改一个字段也要整份重写，
 * 所以在节点级别切分换不来体积收益。
 *
 * 应用侧与 [DatabaseMerge] 同一套判断：对端更新才整条替换，本地缺的才补，节点与搜索行只跟着这两类
 * 会话走。整个应用是一个事务，失败由 SQLite 回滚，不会留下半份数据。两边库版本必须一致：
 * 增量库没有完整的表结构，不能像整份备份那样交给 Room 迁移，版本不同的组合在调用方就应退回整份归档。
 */
object SyncDelta {
    const val FILE_NAME = "sync_delta.db"

    private const val TAG = "SyncDelta"

    fun export(
        database: SupportSQLiteDatabase,
        destination: File,
        since: Long,
    ): SyncDeltaSummary {
        destination.parentFile?.mkdirs()
        if (destination.exists()) check(destination.delete()) { "Cannot replace the delta database" }
        val schemaVersion = userVersion(database, "main")
        val hasTombstones = SyncTombstones.tableExists(database)
        var conversations = 0
        var nodes = 0
        database.execSQL("ATTACH DATABASE ? AS delta", arrayOf(destination.absolutePath))
        try {
            database.beginTransaction()
            try {
                database.execSQL(
                    "CREATE TABLE delta.`ConversationEntity` AS " +
                        "SELECT * FROM main.`ConversationEntity` WHERE update_at > $since"
                )
                database.execSQL(
                    "CREATE TABLE delta.`message_node` AS SELECT * FROM main.`message_node` " +
                        "WHERE conversation_id IN (SELECT id FROM delta.`ConversationEntity`)"
                )
                database.execSQL(
                    "CREATE TABLE delta.`message_fts` AS " +
                        "SELECT text, node_id, message_id, conversation_id FROM main.`message_fts` " +
                        "WHERE conversation_id IN (SELECT id FROM delta.`ConversationEntity`)"
                )
                if (hasTombstones) {
                    database.execSQL(
                        "CREATE TABLE delta.`${SyncTombstones.TABLE}` AS " +
                            "SELECT * FROM main.`${SyncTombstones.TABLE}` WHERE deleted_at > $since"
                    )
                }
                // 分组与收藏行数很少，且没有可靠的更新时间，整表带上比漏掉改名更划算
                database.execSQL(
                    "CREATE TABLE delta.`conversation_folder` AS SELECT * FROM main.`conversation_folder`"
                )
                database.execSQL(
                    "CREATE TABLE delta.`favorites` AS SELECT * FROM main.`favorites`"
                )
                database.execSQL(
                    "CREATE TABLE delta.`memory_file` AS SELECT * FROM main.`memory_file` " +
                        "WHERE updated_at > $since"
                )
                database.execSQL(
                    "CREATE TABLE delta.`memory_consolidation` AS " +
                        "SELECT * FROM main.`memory_consolidation` WHERE consolidated_at > $since"
                )
                database.execSQL(
                    "CREATE TABLE delta.`workspaces` AS SELECT * FROM main.`workspaces` " +
                        "WHERE updated_at > $since"
                )
                // 用量流水没有可判重的自然键，只带本次变更会话名下的；应用侧再按「本地新会话」过滤一次
                database.execSQL(
                    "CREATE TABLE delta.`usage_record` AS SELECT * FROM main.`usage_record` " +
                        "WHERE conversation_id IN (SELECT id FROM delta.`ConversationEntity`)"
                )
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
            // 版本号必须落在增量库里，应用侧靠它拒绝列结构不一致的包
            database.execSQL("PRAGMA delta.user_version = $schemaVersion")
            // 统计要在 DETACH 之前做完，脱离之后 delta 这个 schema 就查不到了
            conversations = count(database, "delta", "ConversationEntity")
            nodes = count(database, "delta", "message_node")
        } finally {
            database.execSQL("DETACH DATABASE delta")
        }
        Log.i(TAG, "export: $conversations conversations, $nodes nodes since $since")
        return SyncDeltaSummary(conversations = conversations, nodes = nodes)
    }

    fun apply(context: Context, liveDatabase: File, deltaFile: File): SyncDeltaSummary {
        val configuration = SQLiteConfiguration.configure(
            context,
            SQLiteDatabaseConfiguration(liveDatabase.absolutePath, SQLiteDatabase.OPEN_READWRITE),
        )
        SQLiteDatabase.openDatabase(configuration, null) {
            error("Cannot open the live database for the delta")
        }.use { database ->
            database.execSQL("ATTACH DATABASE ? AS delta", arrayOf(deltaFile.absolutePath))
            try {
                check(userVersion(database, "main") == userVersion(database, "delta")) {
                    "Delta database schema does not match the live database"
                }
                // 关掉外键约束后补表不用考虑父子顺序；级联删除因此也不会发生，删除会话时要自己删节点
                database.execSQL("PRAGMA foreign_keys = OFF")
                try {
                    database.beginTransaction()
                    try {
                        applyTombstones(database)
                        val restored = collectConversationIds(database)
                        dropRefreshedConversations(database)
                        database.execSQL(
                            "INSERT OR IGNORE INTO main.`ConversationEntity` " +
                                "SELECT * FROM delta.`ConversationEntity`"
                        )
                        database.execSQL(
                            "INSERT OR IGNORE INTO main.`message_node` SELECT * FROM delta.`message_node` " +
                                "WHERE conversation_id IN (SELECT id FROM temp.restored_conversations " +
                                "UNION SELECT id FROM temp.refreshed_conversations)"
                        )
                        mergeSearchIndex(database)
                        mergeFolders(database)
                        mergeFavorites(database)
                        mergeWorkspaces(database)
                        mergeMemoryFiles(database)
                        mergeMemoryConsolidation(database)
                        mergeUsageRecords(database)
                        mergeTombstones(database)
                        database.setTransactionSuccessful()
                        val nodes = count(database, "delta", "message_node")
                        Log.i(TAG, "apply: $restored new conversations, $nodes nodes")
                        return SyncDeltaSummary(conversations = restored, nodes = nodes)
                    } finally {
                        database.endTransaction()
                    }
                } finally {
                    database.execSQL("PRAGMA foreign_keys = ON")
                }
            } finally {
                database.execSQL("DETACH DATABASE delta")
            }
        }
    }

    private fun applyTombstones(database: SQLiteDatabase) {
        if (!tableExists(database, "delta", SyncTombstones.TABLE)) return
        val table = "delta.`${SyncTombstones.TABLE}`"

        // 会话：节点与搜索行没有外键级联可用（foreign_keys 已关），按会话删干净
        database.execSQL(
            "DELETE FROM main.`message_node` WHERE conversation_id IN (" +
                "SELECT target_id FROM $table WHERE kind = '${SyncTombstones.KIND_CONVERSATION}')"
        )
        database.execSQL(
            "DELETE FROM main.`message_fts` WHERE rowid IN (" +
                "SELECT fts_rowid FROM main.`message_fts_rowid` WHERE conversation_id IN (" +
                "SELECT target_id FROM $table WHERE kind = '${SyncTombstones.KIND_CONVERSATION}'))"
        )
        database.execSQL(
            "DELETE FROM main.`message_fts_rowid` WHERE conversation_id IN (" +
                "SELECT target_id FROM $table WHERE kind = '${SyncTombstones.KIND_CONVERSATION}')"
        )
        database.execSQL(
            "DELETE FROM main.`ConversationEntity` WHERE id IN (" +
                "SELECT target_id FROM $table WHERE kind = '${SyncTombstones.KIND_CONVERSATION}')"
        )

        database.execSQL(
            "DELETE FROM main.`conversation_folder` WHERE id IN (" +
                "SELECT target_id FROM $table WHERE kind = '${SyncTombstones.KIND_FOLDER}')"
        )
        database.execSQL(
            "DELETE FROM main.`workspaces` WHERE id IN (" +
                "SELECT target_id FROM $table WHERE kind = '${SyncTombstones.KIND_WORKSPACE}')"
        )
        database.execSQL(
            "DELETE FROM main.`memory_file` WHERE EXISTS (" +
                "SELECT 1 FROM $table t WHERE t.kind = '${SyncTombstones.KIND_MEMORY}' " +
                "AND t.target_id = main.`memory_file`.`assistant_id` || char(10) || main.`memory_file`.`path`)"
        )
    }

    /** 建 restored / refreshed 两张临时表，返回本地缺的会话数 */
    private fun collectConversationIds(database: SQLiteDatabase): Int {
        // 本端删掉、对端还留着的会话不能再被拉回来：本地墓碑优先于对端的清单
        val skipLocalTombstones = if (tableExists(database, "main", SyncTombstones.TABLE)) {
            " AND id NOT IN (SELECT target_id FROM main.`${SyncTombstones.TABLE}` " +
                "WHERE kind = '${SyncTombstones.KIND_CONVERSATION}')"
        } else {
            ""
        }
        database.execSQL("DROP TABLE IF EXISTS temp.restored_conversations")
        database.execSQL("CREATE TEMP TABLE restored_conversations (id TEXT PRIMARY KEY)")
        database.execSQL(
            "INSERT OR IGNORE INTO temp.restored_conversations(id) " +
                "SELECT id FROM delta.`ConversationEntity` " +
                "WHERE id NOT IN (SELECT id FROM main.`ConversationEntity`)" + skipLocalTombstones
        )
        database.execSQL("DROP TABLE IF EXISTS temp.refreshed_conversations")
        database.execSQL("CREATE TEMP TABLE refreshed_conversations (id TEXT PRIMARY KEY)")
        // update_at 由本应用自己写，两版共用同一台设备的时钟，可以直接比大小
        database.execSQL(
            "INSERT OR IGNORE INTO temp.refreshed_conversations(id) " +
                "SELECT d.id FROM delta.`ConversationEntity` AS d " +
                "JOIN main.`ConversationEntity` AS m ON m.id = d.id " +
                "WHERE d.update_at > m.update_at" + skipLocalTombstones.replace("id NOT IN", "d.id NOT IN")
        )
        return count(database, "temp", "restored_conversations")
    }

    /**
     * 对端较新的会话整条换成对端那份：整行、节点、搜索行一起删，下面按主键补缺时会把它当缺行重新插入。
     * 与 [DatabaseMerge.dropRefreshedConversations] 同一手法，区别只是输入是增量库。
     */
    private fun dropRefreshedConversations(database: SQLiteDatabase) {
        val has = database.query("SELECT 1 FROM temp.refreshed_conversations LIMIT 1")
            .use { it.moveToFirst() }
        if (!has) return
        database.execSQL(
            "DELETE FROM main.`ConversationEntity` " +
                "WHERE id IN (SELECT id FROM temp.refreshed_conversations)"
        )
        database.execSQL(
            "DELETE FROM main.`message_node` " +
                "WHERE conversation_id IN (SELECT id FROM temp.refreshed_conversations)"
        )
        database.execSQL(
            "DELETE FROM main.`message_fts` WHERE rowid IN (" +
                "SELECT fts_rowid FROM main.`message_fts_rowid` " +
                "WHERE conversation_id IN (SELECT id FROM temp.refreshed_conversations))"
        )
        database.execSQL(
            "DELETE FROM main.`message_fts_rowid` " +
                "WHERE conversation_id IN (SELECT id FROM temp.refreshed_conversations)"
        )
    }

    private fun mergeSearchIndex(database: SQLiteDatabase) {
        if (!tableExists(database, "delta", "message_fts")) return
        database.execSQL(
            "INSERT INTO main.`message_fts`(text, node_id, message_id, conversation_id) " +
                "SELECT text, node_id, message_id, conversation_id FROM delta.`message_fts` " +
                "WHERE conversation_id IN (SELECT id FROM temp.restored_conversations " +
                "UNION SELECT id FROM temp.refreshed_conversations)"
        )
        database.execSQL(
            "INSERT INTO main.`message_fts_rowid`(fts_rowid, conversation_id) " +
                "SELECT f.rowid, f.conversation_id FROM main.`message_fts` AS f " +
                "WHERE f.rowid NOT IN (SELECT fts_rowid FROM main.`message_fts_rowid`)"
        )
    }

    private fun mergeFolders(database: SQLiteDatabase) {
        if (!tableExists(database, "delta", "conversation_folder")) return
        database.execSQL(
            "INSERT OR IGNORE INTO main.`conversation_folder` SELECT * FROM delta.`conversation_folder`"
        )
    }

    private fun mergeFavorites(database: SQLiteDatabase) {
        if (!tableExists(database, "delta", "favorites")) return
        database.execSQL("INSERT OR IGNORE INTO main.`favorites` SELECT * FROM delta.`favorites`")
    }

    private fun mergeWorkspaces(database: SQLiteDatabase) {
        if (!tableExists(database, "delta", "workspaces")) return
        database.execSQL("DROP TABLE IF EXISTS temp.refreshed_workspaces")
        database.execSQL("CREATE TEMP TABLE refreshed_workspaces (id TEXT PRIMARY KEY)")
        database.execSQL(
            "INSERT OR IGNORE INTO temp.refreshed_workspaces(id) " +
                "SELECT d.id FROM delta.`workspaces` AS d " +
                "JOIN main.`workspaces` AS m ON m.id = d.id WHERE d.updated_at > m.updated_at"
        )
        database.execSQL(
            "DELETE FROM main.`workspaces` WHERE id IN (SELECT id FROM temp.refreshed_workspaces)"
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
                "WHERE d.updated_at > m.updated_at"
        )
        database.execSQL(
            "DELETE FROM main.`memory_file` WHERE EXISTS (" +
                "SELECT 1 FROM temp.refreshed_memory r " +
                "WHERE r.assistant_id = main.`memory_file`.`assistant_id` " +
                "AND r.path = main.`memory_file`.`path`)"
        )
        database.execSQL("INSERT OR IGNORE INTO main.`memory_file` SELECT * FROM delta.`memory_file`")
    }

    private fun mergeMemoryConsolidation(database: SQLiteDatabase) {
        if (!tableExists(database, "delta", "memory_consolidation")) return
        database.execSQL(
            "DELETE FROM main.`memory_consolidation` WHERE conversation_id IN (" +
                "SELECT d.conversation_id FROM delta.`memory_consolidation` AS d " +
                "JOIN main.`memory_consolidation` AS m ON m.conversation_id = d.conversation_id " +
                "WHERE d.consolidated_at > m.consolidated_at)"
        )
        database.execSQL(
            "INSERT OR IGNORE INTO main.`memory_consolidation` SELECT * FROM delta.`memory_consolidation`"
        )
    }

    private fun mergeUsageRecords(database: SQLiteDatabase) {
        if (!tableExists(database, "delta", "usage_record")) return
        // 自增 id 两边会重号，不抄 id；只补本地新会话名下的流水，避免统计翻倍
        database.execSQL(
            "INSERT INTO main.`usage_record`(created_at, source, provider_id, model_id, model_name, " +
                "assistant_id, conversation_id, prompt_tokens, completion_tokens, cached_tokens, cost) " +
                "SELECT created_at, source, provider_id, model_id, model_name, assistant_id, " +
                "conversation_id, prompt_tokens, completion_tokens, cached_tokens, cost " +
                "FROM delta.`usage_record` WHERE conversation_id IN (SELECT id FROM temp.restored_conversations)"
        )
    }

    /** 墓碑随数据一起留下：本端之后再同步给第三处时，这次删除还要继续传下去 */
    private fun mergeTombstones(database: SQLiteDatabase) {
        if (!tableExists(database, "main", SyncTombstones.TABLE)) return
        if (!tableExists(database, "delta", SyncTombstones.TABLE)) return
        database.execSQL(
            "INSERT OR REPLACE INTO main.`${SyncTombstones.TABLE}`(kind, target_id, deleted_at) " +
                "SELECT kind, target_id, deleted_at FROM delta.`${SyncTombstones.TABLE}`"
        )
    }

    private fun userVersion(database: SQLiteDatabase, schema: String): Int =
        database.query("PRAGMA $schema.user_version").use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private fun userVersion(database: SupportSQLiteDatabase, schema: String): Int =
        database.query("PRAGMA $schema.user_version").use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private fun tableExists(database: SQLiteDatabase, schema: String, table: String): Boolean =
        database.rawQuery(
            "SELECT 1 FROM $schema.sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table),
        ).use { it.moveToFirst() }

    private fun count(database: SQLiteDatabase, schema: String, table: String): Int =
        database.query("SELECT COUNT(*) FROM $schema.`$table`").use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private fun count(database: SupportSQLiteDatabase, schema: String, table: String): Int =
        database.query("SELECT COUNT(*) FROM $schema.`$table`").use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
}
