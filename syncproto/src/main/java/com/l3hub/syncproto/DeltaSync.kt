package com.l3hub.syncproto

import java.io.File

/**
 * 增量库的导出与合并。
 *
 * 导出：按会话的 `update_at` 水位抄出整条会话与它的节点、搜索行，再加上这段时间内的删除墓碑、
 * 整表很小的分组与收藏、以及有时间戳的记忆、工作区、附件登记、媒体创作与用量流水。
 * 会话本身整条搬——节点没有自己的时间戳，一条会话里改一个字段也要整份重写，在节点级别切分换不来
 * 体积收益。
 *
 * 合并：对端更新才整条替换，本地缺的才补，节点与搜索行只跟着这两类会话走。整个应用是一个事务，
 * 失败由 SQLite 回滚，不会留下半份数据。两边库版本必须一致：增量库没有完整的表结构，不能交给
 * Room 迁移，版本对不上的组合在调用方就应退回整份归档。
 *
 * 这份 SQL 由 L3Hub 与数据中心共用：两边打开数据库的方式不同（各自的 SQLite 配置与分词扩展），
 * 所以只约定一个最小的 [Session] 接口，SQL 本身只写一遍，避免两处慢慢漂移。
 */
object DeltaSync {
    const val TABLE_TOMBSTONE = "sync_tombstone"

    const val KIND_CONVERSATION = "conversation"
    const val KIND_FOLDER = "folder"
    const val KIND_MEMORY = "memory"
    const val KIND_WORKSPACE = "workspace"

    /** 记忆表的复合主键在墓碑里拼成一个字符串，路径本身不会出现换行 */
    const val MEMORY_SEPARATOR = "\n"

    data class Summary(val conversations: Int = 0, val nodes: Int = 0)

    /**
     * 一条数据库连接的最小抽象。
     *
     * [exec] 与 [query] 的参数按 SQLite 的位置参数绑定；实现方负责把占位符与参数交给底层驱动。
     *
     * ATTACH 只对执行它的那条连接可见，池化实现必须让同一段 SQL 走在同一条连接上，否则 `delta.*`
     * 会报 unknown database delta。
     */
    interface Session {
        fun exec(sql: String, args: Array<Any?>? = null)
        fun query(sql: String, args: Array<Any?>? = null): Cursor
        fun begin()
        fun commit()
        fun rollback()
    }

    /** 只读游标；只依赖 JVM 的 [java.io.Closeable]，两端都能用 `use {}` 收尾 */
    interface Cursor : java.io.Closeable {
        fun moveToNext(): Boolean
        fun moveToFirst(): Boolean
        fun int(column: Int): Int
        fun long(column: Int): Long
        fun string(column: Int): String?
    }

    /**
     * 把 [since] 之后的变化写成一个独立的增量库。
     *
     * [destination] 必须不存在：出现同名文件说明上一次没清干净，宁可直接失败也不要往里叠加数据。
     */
    fun export(session: Session, destination: File, since: Long): Summary {
        destination.parentFile?.mkdirs()
        if (destination.exists()) check(destination.delete()) { "Cannot replace the delta database" }
        val schemaVersion = userVersion(session, "main")
        val tableToTombstone = tableExists(session, "main", TABLE_TOMBSTONE)
        var conversations = 0
        var nodes = 0
        var attached = false
        // ATTACH 必须和后面的语句落在同一条连接上：SQLite 把 ATTACH 判成只读语句，池化连接会把它交给
        // 一条只读连接，紧接着的 CREATE TABLE delta.* 走写连接就报 unknown database delta。
        // 事务期间会话独占写连接，ATTACH、建表、版本号与统计都在它上面做完。
        session.begin()
        try {
            session.exec("ATTACH DATABASE ? AS delta", arrayOf<Any?>(destination.absolutePath))
            attached = true
            session.exec(
                "CREATE TABLE delta.`ConversationEntity` AS " +
                    "SELECT * FROM main.`ConversationEntity` WHERE update_at > $since"
            )
            session.exec(
                "CREATE TABLE delta.`message_node` AS SELECT * FROM main.`message_node` " +
                    "WHERE conversation_id IN (SELECT id FROM delta.`ConversationEntity`)"
            )
            session.exec(
                "CREATE TABLE delta.`message_fts` AS " +
                    "SELECT text, node_id, message_id, conversation_id FROM main.`message_fts` " +
                    "WHERE conversation_id IN (SELECT id FROM delta.`ConversationEntity`)"
            )
            if (tableToTombstone) {
                session.exec(
                    "CREATE TABLE delta.`$TABLE_TOMBSTONE` AS " +
                        "SELECT * FROM main.`$TABLE_TOMBSTONE` WHERE deleted_at > $since"
                )
            }
            // 分组与收藏行数很少，且没有可靠的更新时间，整表带上比漏掉改名更划算
            session.exec("CREATE TABLE delta.`conversation_folder` AS SELECT * FROM main.`conversation_folder`")
            session.exec("CREATE TABLE delta.`favorites` AS SELECT * FROM main.`favorites`")
            session.exec(
                "CREATE TABLE delta.`memory_file` AS SELECT * FROM main.`memory_file` " +
                    "WHERE updated_at > $since"
            )
            session.exec(
                "CREATE TABLE delta.`memory_consolidation` AS " +
                    "SELECT * FROM main.`memory_consolidation` WHERE consolidated_at > $since"
            )
            session.exec(
                "CREATE TABLE delta.`workspaces` AS SELECT * FROM main.`workspaces` " +
                    "WHERE updated_at > $since"
            )
            // 用量流水没有可判重的自然键，只带本次变更会话名下的；应用侧再按「本地新会话」过滤一次
            session.exec(
                "CREATE TABLE delta.`usage_record` AS SELECT * FROM main.`usage_record` " +
                    "WHERE conversation_id IN (SELECT id FROM delta.`ConversationEntity`)"
            )
            // 附件登记与媒体创作在整份备份里是「有就带」的普通表，增量层按时间戳带上变化的部分，
            // 否则从链恢复之后这些表会停在 base 那一刻，新导入的附件不再被登记
            if (tableExists(session, "main", "managed_files")) {
                session.exec(
                    "CREATE TABLE delta.`managed_files` AS SELECT * FROM main.`managed_files` " +
                        "WHERE updated_at > $since"
                )
            }
            if (tableExists(session, "main", "GenMediaEntity")) {
                session.exec(
                    "CREATE TABLE delta.`GenMediaEntity` AS SELECT * FROM main.`GenMediaEntity` " +
                        "WHERE create_at > $since"
                )
            }
            if (tableExists(session, "main", "media_creation_session")) {
                session.exec(
                    "CREATE TABLE delta.`media_creation_session` AS " +
                        "SELECT * FROM main.`media_creation_session` WHERE update_at > $since"
                )
                session.exec(
                    "CREATE TABLE delta.`media_creation_node` AS SELECT * FROM main.`media_creation_node` " +
                        "WHERE session_id IN (SELECT id FROM delta.`media_creation_session`)"
                )
                session.exec(
                    "CREATE TABLE delta.`media_creation_record` AS " +
                        "SELECT * FROM main.`media_creation_record` WHERE update_at > $since"
                )
            }
            if (tableExists(session, "main", "translation_history")) {
                session.exec(
                    "CREATE TABLE delta.`translation_history` AS " +
                        "SELECT * FROM main.`translation_history` WHERE create_at > $since"
                )
            }
            // 版本号必须落在增量库里，应用侧靠它拒绝列结构不一致的包
            session.exec("PRAGMA delta.user_version = $schemaVersion")
            // 统计要在 DETACH 之前做完，脱离之后 delta 这个 schema 就查不到了
            conversations = count(session, "delta", "ConversationEntity")
            nodes = count(session, "delta", "message_node")
            session.commit()
        } catch (e: Throwable) {
            runCatching { session.rollback() }
            if (attached) runCatching { detach(session) }
            throw e
        }
        detach(session)
        return Summary(conversations = conversations, nodes = nodes)
    }

    /**
     * 脱离增量库。
     *
     * DETACH 在事务里会被 delta 自己锁住，只能另开一个空事务把写连接拿回来再脱开：池化实现前后两次
     * 拿到的仍是同一条写连接，ATTACH 留下的状态还在上面。
     */
    private fun detach(session: Session) {
        session.begin()
        try {
            session.exec("DETACH DATABASE delta")
            session.commit()
        } catch (e: Throwable) {
            runCatching { session.rollback() }
            throw e
        }
    }

    /**
     * 把一份增量库合并进当前连接指向的库。
     *
     * 这里的 [Session] 必须是单连接实现（恢复与合并都直接开一条 requery 连接）：ATTACH 之后的每次
     * 读写都要落在同一条连接上，还必须在事务外先关掉外键约束，没法像 [export] 那样用事务把连接钉住。
     */
    fun apply(session: Session, deltaFile: File): Summary {
        session.exec("ATTACH DATABASE ? AS delta", arrayOf<Any?>(deltaFile.absolutePath))
        try {
            check(userVersion(session, "main") == userVersion(session, "delta")) {
                "Delta database schema does not match the live database"
            }
            // 关掉外键约束后补表不用考虑父子顺序；级联删除因此也不会发生，删除会话时要自己删节点
            session.exec("PRAGMA foreign_keys = OFF")
            try {
                session.begin()
                try {
                    applyTombstones(session)
                    val restored = collectConversationIds(session)
                    dropRefreshedConversations(session)
                    session.exec(
                        "INSERT OR IGNORE INTO main.`ConversationEntity` SELECT * FROM delta.`ConversationEntity`"
                    )
                    session.exec(
                        "INSERT OR IGNORE INTO main.`message_node` SELECT * FROM delta.`message_node` " +
                            "WHERE conversation_id IN (SELECT id FROM temp.restored_conversations " +
                            "UNION SELECT id FROM temp.refreshed_conversations)"
                    )
                    mergeSearchIndex(session)
                    mergeFolders(session)
                    mergeFavorites(session)
                    mergeWorkspaces(session)
                    mergeMemoryFiles(session)
                    mergeMemoryConsolidation(session)
                    mergeUsageRecords(session)
                    mergeManagedFiles(session)
                    mergeGenMedia(session)
                    mergeMediaCreation(session)
                    mergeTranslationHistory(session)
                    mergeTombstones(session)
                    session.commit()
                    val nodes = count(session, "delta", "message_node")
                    return Summary(conversations = restored, nodes = nodes)
                } catch (e: Throwable) {
                    runCatching { session.rollback() }
                    throw e
                }
            } finally {
                session.exec("PRAGMA foreign_keys = ON")
            }
        } finally {
            session.exec("DETACH DATABASE delta")
        }
    }

    private fun applyTombstones(session: Session) {
        if (!tableExists(session, "delta", TABLE_TOMBSTONE)) return
        val table = "delta.`$TABLE_TOMBSTONE`"
        val conversations = "SELECT target_id FROM $table WHERE kind = '$KIND_CONVERSATION'"

        // 会话：节点与搜索行没有外键级联可用（foreign_keys 已关），按会话删干净
        session.exec("DELETE FROM main.`message_node` WHERE conversation_id IN ($conversations)")
        session.exec(
            "DELETE FROM main.`message_fts` WHERE rowid IN (" +
                "SELECT fts_rowid FROM main.`message_fts_rowid` WHERE conversation_id IN ($conversations))"
        )
        session.exec("DELETE FROM main.`message_fts_rowid` WHERE conversation_id IN ($conversations)")
        session.exec("DELETE FROM main.`ConversationEntity` WHERE id IN ($conversations)")
        session.exec(
            "DELETE FROM main.`conversation_folder` WHERE id IN (" +
                "SELECT target_id FROM $table WHERE kind = '$KIND_FOLDER')"
        )
        session.exec(
            "DELETE FROM main.`workspaces` WHERE id IN (" +
                "SELECT target_id FROM $table WHERE kind = '$KIND_WORKSPACE')"
        )
        session.exec(
            "DELETE FROM main.`memory_file` WHERE EXISTS (" +
                "SELECT 1 FROM $table t WHERE t.kind = '$KIND_MEMORY' " +
                "AND t.target_id = main.`memory_file`.`assistant_id` || char(10) || main.`memory_file`.`path`)"
        )
    }

    /** 建 restored / refreshed 两张临时表，返回本地缺的会话数 */
    private fun collectConversationIds(session: Session): Int {
        // 本端删掉、对端还留着的会话不能再被拉回来：本地墓碑优先于对端的清单
        val skipLocalTombstones = if (tableExists(session, "main", TABLE_TOMBSTONE)) {
            " AND id NOT IN (SELECT target_id FROM main.`$TABLE_TOMBSTONE` WHERE kind = '$KIND_CONVERSATION')"
        } else {
            ""
        }
        session.exec("DROP TABLE IF EXISTS temp.restored_conversations")
        session.exec("CREATE TEMP TABLE restored_conversations (id TEXT PRIMARY KEY)")
        session.exec(
            "INSERT OR IGNORE INTO temp.restored_conversations(id) " +
                "SELECT id FROM delta.`ConversationEntity` " +
                "WHERE id NOT IN (SELECT id FROM main.`ConversationEntity`)" + skipLocalTombstones
        )
        session.exec("DROP TABLE IF EXISTS temp.refreshed_conversations")
        session.exec("CREATE TEMP TABLE refreshed_conversations (id TEXT PRIMARY KEY)")
        // update_at 由本应用自己写，两版共用同一台设备的时钟，可以直接比大小
        session.exec(
            "INSERT OR IGNORE INTO temp.refreshed_conversations(id) " +
                "SELECT d.id FROM delta.`ConversationEntity` AS d " +
                "JOIN main.`ConversationEntity` AS m ON m.id = d.id " +
                "WHERE d.update_at > m.update_at" + skipLocalTombstones.replace("id NOT IN", "d.id NOT IN")
        )
        return count(session, "temp", "restored_conversations")
    }

    /**
     * 对端较新的会话整条换成对端那份：整行、节点、搜索行一起删，下面按主键补缺时会把它当缺行重新插入。
     */
    private fun dropRefreshedConversations(session: Session) {
        val has = session.query("SELECT 1 FROM temp.refreshed_conversations LIMIT 1").use { it.moveToFirst() }
        if (!has) return
        session.exec(
            "DELETE FROM main.`ConversationEntity` WHERE id IN (SELECT id FROM temp.refreshed_conversations)"
        )
        session.exec(
            "DELETE FROM main.`message_node` WHERE conversation_id IN (SELECT id FROM temp.refreshed_conversations)"
        )
        session.exec(
            "DELETE FROM main.`message_fts` WHERE rowid IN (" +
                "SELECT fts_rowid FROM main.`message_fts_rowid` " +
                "WHERE conversation_id IN (SELECT id FROM temp.refreshed_conversations))"
        )
        session.exec(
            "DELETE FROM main.`message_fts_rowid` WHERE conversation_id IN (SELECT id FROM temp.refreshed_conversations)"
        )
    }

    private fun mergeSearchIndex(session: Session) {
        if (!tableExists(session, "delta", "message_fts")) return
        session.exec(
            "INSERT INTO main.`message_fts`(text, node_id, message_id, conversation_id) " +
                "SELECT text, node_id, message_id, conversation_id FROM delta.`message_fts` " +
                "WHERE conversation_id IN (SELECT id FROM temp.restored_conversations " +
                "UNION SELECT id FROM temp.refreshed_conversations)"
        )
        session.exec(
            "INSERT INTO main.`message_fts_rowid`(fts_rowid, conversation_id) " +
                "SELECT f.rowid, f.conversation_id FROM main.`message_fts` AS f " +
                "WHERE f.rowid NOT IN (SELECT fts_rowid FROM main.`message_fts_rowid`)"
        )
    }

    private fun mergeFolders(session: Session) {
        if (!tableExists(session, "delta", "conversation_folder")) return
        session.exec("INSERT OR IGNORE INTO main.`conversation_folder` SELECT * FROM delta.`conversation_folder`")
    }

    private fun mergeFavorites(session: Session) {
        if (!tableExists(session, "delta", "favorites")) return
        session.exec("INSERT OR IGNORE INTO main.`favorites` SELECT * FROM delta.`favorites`")
    }

    private fun mergeWorkspaces(session: Session) {
        if (!tableExists(session, "delta", "workspaces")) return
        session.exec("DROP TABLE IF EXISTS temp.refreshed_workspaces")
        session.exec("CREATE TEMP TABLE refreshed_workspaces (id TEXT PRIMARY KEY)")
        session.exec(
            "INSERT OR IGNORE INTO temp.refreshed_workspaces(id) " +
                "SELECT d.id FROM delta.`workspaces` AS d " +
                "JOIN main.`workspaces` AS m ON m.id = d.id WHERE d.updated_at > m.updated_at"
        )
        session.exec("DELETE FROM main.`workspaces` WHERE id IN (SELECT id FROM temp.refreshed_workspaces)")
        session.exec("INSERT OR IGNORE INTO main.`workspaces` SELECT * FROM delta.`workspaces`")
    }

    private fun mergeMemoryFiles(session: Session) {
        if (!tableExists(session, "delta", "memory_file")) return
        session.exec("DROP TABLE IF EXISTS temp.refreshed_memory")
        session.exec("CREATE TEMP TABLE refreshed_memory (assistant_id TEXT, path TEXT)")
        session.exec(
            "INSERT INTO temp.refreshed_memory(assistant_id, path) " +
                "SELECT d.assistant_id, d.path FROM delta.`memory_file` AS d " +
                "JOIN main.`memory_file` AS m ON m.assistant_id = d.assistant_id AND m.path = d.path " +
                "WHERE d.updated_at > m.updated_at"
        )
        session.exec(
            "DELETE FROM main.`memory_file` WHERE EXISTS (" +
                "SELECT 1 FROM temp.refreshed_memory r " +
                "WHERE r.assistant_id = main.`memory_file`.`assistant_id` " +
                "AND r.path = main.`memory_file`.`path`)"
        )
        session.exec("INSERT OR IGNORE INTO main.`memory_file` SELECT * FROM delta.`memory_file`")
    }

    private fun mergeMemoryConsolidation(session: Session) {
        if (!tableExists(session, "delta", "memory_consolidation")) return
        session.exec(
            "DELETE FROM main.`memory_consolidation` WHERE conversation_id IN (" +
                "SELECT d.conversation_id FROM delta.`memory_consolidation` AS d " +
                "JOIN main.`memory_consolidation` AS m ON m.conversation_id = d.conversation_id " +
                "WHERE d.consolidated_at > m.consolidated_at)"
        )
        session.exec("INSERT OR IGNORE INTO main.`memory_consolidation` SELECT * FROM delta.`memory_consolidation`")
    }

    private fun mergeUsageRecords(session: Session) {
        if (!tableExists(session, "delta", "usage_record")) return
        // 自增 id 两边会重号，不抄 id；只补本地新会话名下的流水，避免统计翻倍
        session.exec(
            "INSERT INTO main.`usage_record`(created_at, source, provider_id, model_id, model_name, " +
                "assistant_id, conversation_id, prompt_tokens, completion_tokens, cached_tokens, cost) " +
                "SELECT created_at, source, provider_id, model_id, model_name, assistant_id, " +
                "conversation_id, prompt_tokens, completion_tokens, cached_tokens, cost " +
                "FROM delta.`usage_record` WHERE conversation_id IN (SELECT id FROM temp.restored_conversations)"
        )
    }

    /** 附件登记按 unique 的 relative_path 判重：对端更新的整行替换，本地缺的补上 */
    private fun mergeManagedFiles(session: Session) {
        if (!tableExists(session, "delta", "managed_files")) return
        session.exec(
            "DELETE FROM main.`managed_files` WHERE relative_path IN (" +
                "SELECT d.relative_path FROM delta.`managed_files` AS d " +
                "JOIN main.`managed_files` AS m ON m.relative_path = d.relative_path " +
                "WHERE d.updated_at > m.updated_at)"
        )
        session.exec(
            "INSERT OR IGNORE INTO main.`managed_files`(folder, relative_path, display_name, mime_type, " +
                "size_bytes, created_at, updated_at) SELECT folder, relative_path, display_name, mime_type, " +
                "size_bytes, created_at, updated_at FROM delta.`managed_files`"
        )
    }

    /** 生成的媒体没有稳定的判重键，按文件路径判重；id 是自增的，两边会重号，不抄 */
    private fun mergeGenMedia(session: Session) {
        if (!tableExists(session, "delta", "GenMediaEntity")) return
        session.exec(
            "INSERT INTO main.`GenMediaEntity`(path, model_id, prompt, create_at, type, source_paths) " +
                "SELECT path, model_id, prompt, create_at, type, source_paths FROM delta.`GenMediaEntity` " +
                "WHERE path NOT IN (SELECT path FROM main.`GenMediaEntity`)"
        )
    }

    private fun mergeMediaCreation(session: Session) {
        if (!tableExists(session, "delta", "media_creation_session")) return
        session.exec("DROP TABLE IF EXISTS temp.refreshed_media_sessions")
        session.exec("CREATE TEMP TABLE refreshed_media_sessions (id TEXT PRIMARY KEY)")
        session.exec(
            "INSERT OR IGNORE INTO temp.refreshed_media_sessions(id) " +
                "SELECT d.id FROM delta.`media_creation_session` AS d " +
                "JOIN main.`media_creation_session` AS m ON m.id = d.id WHERE d.update_at > m.update_at"
        )
        session.exec(
            "DELETE FROM main.`media_creation_session` WHERE id IN (SELECT id FROM temp.refreshed_media_sessions)"
        )
        session.exec("INSERT OR IGNORE INTO main.`media_creation_session` SELECT * FROM delta.`media_creation_session`")
        if (tableExists(session, "delta", "media_creation_node")) {
            session.exec(
                "INSERT OR IGNORE INTO main.`media_creation_node` SELECT * FROM delta.`media_creation_node`"
            )
        }
        if (tableExists(session, "delta", "media_creation_record")) {
            session.exec("DROP TABLE IF EXISTS temp.refreshed_media_records")
            session.exec("CREATE TEMP TABLE refreshed_media_records (id TEXT PRIMARY KEY)")
            session.exec(
                "INSERT OR IGNORE INTO temp.refreshed_media_records(id) " +
                    "SELECT d.id FROM delta.`media_creation_record` AS d " +
                    "JOIN main.`media_creation_record` AS m ON m.id = d.id WHERE d.update_at > m.update_at"
            )
            session.exec(
                "DELETE FROM main.`media_creation_record` WHERE id IN (SELECT id FROM temp.refreshed_media_records)"
            )
            session.exec(
                "INSERT OR IGNORE INTO main.`media_creation_record` SELECT * FROM delta.`media_creation_record`"
            )
        }
    }

    private fun mergeTranslationHistory(session: Session) {
        if (!tableExists(session, "delta", "translation_history")) return
        session.exec("INSERT OR IGNORE INTO main.`translation_history` SELECT * FROM delta.`translation_history`")
    }

    /** 墓碑随数据一起留下：本端之后再同步给第三处时，这次删除还要继续传下去 */
    private fun mergeTombstones(session: Session) {
        if (!tableExists(session, "main", TABLE_TOMBSTONE)) return
        if (!tableExists(session, "delta", TABLE_TOMBSTONE)) return
        session.exec(
            "INSERT OR REPLACE INTO main.`$TABLE_TOMBSTONE`(kind, target_id, deleted_at) " +
                "SELECT kind, target_id, deleted_at FROM delta.`$TABLE_TOMBSTONE`"
        )
    }

    fun tableExists(session: Session, schema: String, table: String): Boolean =
        session.query(
            "SELECT 1 FROM $schema.sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf<Any?>(table),
        ).use { it.moveToFirst() }

    fun userVersion(session: Session, schema: String): Int =
        session.query("PRAGMA $schema.user_version").use { cursor ->
            if (cursor.moveToFirst()) cursor.int(0) else 0
        }

    private fun count(session: Session, schema: String, table: String): Int =
        session.query("SELECT COUNT(*) FROM $schema.`$table`").use { cursor ->
            if (cursor.moveToFirst()) cursor.int(0) else 0
        }
}
