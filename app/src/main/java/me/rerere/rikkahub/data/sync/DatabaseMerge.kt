package me.rerere.rikkahub.data.sync

import android.content.Context
import android.util.Log
import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.database.sqlite.SQLiteDatabaseConfiguration
import me.rerere.rikkahub.data.db.SQLiteConfiguration
import java.io.File

/**
 * 合并模式下的聊天记录恢复：把备份库里当前缺少的行补进现有库，不替换整份数据库。
 *
 * 恢复发生在启动的最早阶段，Koin 与 Room 都还没初始化，可以直接用原生连接操作数据库文件。
 * 整个合并是一个事务：中途失败时 SQLite 回滚，不会留下半份数据；重复执行也是安全的，
 * 已经补进去的行会按主键跳过。
 */
internal object DatabaseMerge {
    private const val TAG = "DatabaseMerge"

    // 不能整表按主键合并的表：消息节点要跟着新会话走，搜索索引另有规则，
    // 目录缓存会在启动时按文件系统重建，用量流水没有可判重的自然键
    private val SPECIAL_TABLES = setOf(
        "message_node",
        "message_fts",
        "message_fts_rowid",
        "managed_files",
        "usage_record",
        "GenMediaEntity",
        "room_master_table",
        "android_metadata",
    )

    fun merge(context: Context, liveDatabase: File, backupDatabase: File) {
        val configuration = SQLiteConfiguration.configure(
            context,
            SQLiteDatabaseConfiguration(liveDatabase.absolutePath, SQLiteDatabase.OPEN_READWRITE),
        )
        SQLiteDatabase.openDatabase(configuration, null) {
            error("Cannot open the live database for merging")
        }.use { database ->
            database.execSQL("ATTACH DATABASE ? AS backup", arrayOf(backupDatabase.absolutePath))
            try {
                // 关掉外键约束后补表不用考虑父子顺序；备份本身是完整快照，补进去的引用仍然成立
                database.execSQL("PRAGMA foreign_keys = OFF")
                database.beginTransaction()
                try {
                    mergeTables(database)
                    database.setTransactionSuccessful()
                } finally {
                    database.endTransaction()
                }
            } finally {
                database.execSQL("PRAGMA foreign_keys = ON")
                database.execSQL("DETACH DATABASE backup")
            }
        }
    }

    private fun mergeTables(database: SQLiteDatabase) {
        val backupTables = tableNames(database, "backup")
        val liveTables = tableNames(database, "main")
        // 两边都经过当前版本的迁移才会列结构一致；版本不同时宁可整体失败也不做列对不上的拷贝
        check(userVersion(database, "main") == userVersion(database, "backup")) {
            "Live database schema does not match the backup"
        }

        // 先记下备份里有、当前没有的会话，消息节点和搜索索引只补这些会话的
        database.execSQL("DROP TABLE IF EXISTS temp.restored_conversations")
        database.execSQL("CREATE TEMP TABLE restored_conversations (id TEXT PRIMARY KEY)")
        // 两边都有、但对端更新的会话：整条换成对端那份，用于「在另一版本里继续聊过」的情况
        database.execSQL("DROP TABLE IF EXISTS temp.refreshed_conversations")
        database.execSQL("CREATE TEMP TABLE refreshed_conversations (id TEXT PRIMARY KEY)")
        if ("ConversationEntity" in backupTables && "ConversationEntity" in liveTables) {
            database.execSQL(
                """
                INSERT OR IGNORE INTO temp.restored_conversations(id)
                SELECT id FROM backup.`ConversationEntity`
                WHERE id NOT IN (SELECT id FROM main.`ConversationEntity`)
                """.trimIndent()
            )
            // update_at 由本应用自己写，两版共用同一台设备的时钟，可以直接比大小
            database.execSQL(
                """
                INSERT OR IGNORE INTO temp.refreshed_conversations(id)
                SELECT b.id FROM backup.`ConversationEntity` AS b
                JOIN main.`ConversationEntity` AS m ON m.id = b.id
                WHERE b.update_at > m.update_at
                """.trimIndent()
            )
            dropRefreshedConversations(database, liveTables)
        }

        var mergedTables = 0
        for (table in backupTables) {
            if (table !in liveTables) continue
            if (table in SPECIAL_TABLES) continue
            if (table.startsWith("message_fts") || table.startsWith("sqlite_")) continue
            database.execSQL("INSERT OR IGNORE INTO main.`$table` SELECT * FROM backup.`$table`")
            mergedTables++
        }

        if ("message_node" in backupTables && "message_node" in liveTables) {
            database.execSQL(
                """
                INSERT OR IGNORE INTO main.`message_node`
                SELECT * FROM backup.`message_node`
                WHERE conversation_id IN (
                    SELECT id FROM temp.restored_conversations
                    UNION SELECT id FROM temp.refreshed_conversations
                )
                """.trimIndent()
            )
        }

        if ("usage_record" in backupTables && "usage_record" in liveTables) {
            // 用量流水没有可判重的自然键，只补新会话名下的那些，避免统计翻倍
            database.execSQL(
                """
                INSERT INTO main.`usage_record`(created_at, source, provider_id, model_id, model_name,
                                                assistant_id, conversation_id, prompt_tokens,
                                                completion_tokens, cached_tokens)
                SELECT created_at, source, provider_id, model_id, model_name,
                       assistant_id, conversation_id, prompt_tokens, completion_tokens, cached_tokens
                FROM backup.`usage_record`
                WHERE conversation_id IN (SELECT id FROM temp.restored_conversations)
                """.trimIndent()
            )
        }

        if ("GenMediaEntity" in backupTables && "GenMediaEntity" in liveTables) {
            // 自增 id 在两边会重号，按文件路径判重
            database.execSQL(
                """
                INSERT INTO main.`GenMediaEntity`(path, model_id, prompt, create_at, type, source_paths)
                SELECT path, model_id, prompt, create_at, type, source_paths FROM backup.`GenMediaEntity`
                WHERE path NOT IN (SELECT path FROM main.`GenMediaEntity`)
                """.trimIndent()
            )
        }

        mergeSearchIndex(database, backupTables, liveTables)
        Log.i(TAG, "Merged $mergedTables tables")
    }

    /**
     * 把对端较新的那份会话从现有库里摘掉：整行、它的消息节点与搜索行一起删。
     *
     * 删完下面按主键补缺的拷贝会把它当缺行重新插入，于是这条会话连同消息整份换成对端版本。
     * 不这么做的话，已经存在的会话在合并时拿不到对端新增的消息——节点只在补整条会话时才会抄。
     */
    private fun dropRefreshedConversations(database: SQLiteDatabase, liveTables: Set<String>) {
        val hasRefreshed = database.query(
            "SELECT 1 FROM temp.refreshed_conversations LIMIT 1"
        ).use { it.moveToFirst() }
        if (!hasRefreshed) return
        database.execSQL(
            "DELETE FROM main.`ConversationEntity` " +
                "WHERE id IN (SELECT id FROM temp.refreshed_conversations)"
        )
        if ("message_node" in liveTables) {
            database.execSQL(
                "DELETE FROM main.`message_node` " +
                    "WHERE conversation_id IN (SELECT id FROM temp.refreshed_conversations)"
            )
        }
        if ("message_fts" in liveTables && "message_fts_rowid" in liveTables) {
            // message_fts 的 conversation_id 是 UNINDEXED 列，按会话删要先经 rowid 映射表查出 rowid
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
    }

    /**
     * 搜索索引按会话增量补：只把新会话的消息抄进 message_fts，再给缺映射的索引行补上归属。
     * 不重建全部索引，长会话里重新分词很慢。
     */
    private fun mergeSearchIndex(
        database: SQLiteDatabase,
        backupTables: Set<String>,
        liveTables: Set<String>,
    ) {
        if ("message_fts" !in backupTables || "message_fts" !in liveTables) return
        if ("message_fts_rowid" !in liveTables) return
        database.execSQL(
            """
            INSERT INTO main.`message_fts`(text, node_id, message_id, conversation_id)
            SELECT text, node_id, message_id, conversation_id FROM backup.`message_fts`
            WHERE conversation_id IN (
                SELECT id FROM temp.restored_conversations
                UNION SELECT id FROM temp.refreshed_conversations
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO main.`message_fts_rowid`(fts_rowid, conversation_id)
            SELECT f.rowid, f.conversation_id FROM main.`message_fts` AS f
            WHERE f.rowid NOT IN (SELECT fts_rowid FROM main.`message_fts_rowid`)
            """.trimIndent()
        )
    }

    private fun userVersion(database: SQLiteDatabase, schema: String): Int =
        database.query("PRAGMA $schema.user_version").use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private fun tableNames(database: SQLiteDatabase, schema: String): Set<String> =
        database.query("SELECT name FROM $schema.sqlite_master WHERE type = 'table'").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
}
