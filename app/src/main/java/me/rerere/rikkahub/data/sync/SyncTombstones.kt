package me.rerere.rikkahub.data.sync

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 删除墓碑：增量同步只传「变更了什么」，没有墓碑就无法表达「对方删掉了什么」，删掉的会话会在下一次
 * 同步时被对方重新带回来。
 *
 * 表不进 Room 的实体列表，和 message_fts 一样在每次打开数据库时按 `IF NOT EXISTS` 建好：多一张表
 * 不参与 Room 的 schema 校验，也就不必为了它把数据库版本推到 39、再补一份缺失的 schema 文件。
 * 备份合并（DatabaseMerge）会把它当普通表按主键补，墓碑因此也能随备份走。
 */
object SyncTombstones {
    const val TABLE = "sync_tombstone"

    const val KIND_CONVERSATION = "conversation"
    const val KIND_FOLDER = "folder"
    const val KIND_MEMORY = "memory"
    const val KIND_WORKSPACE = "workspace"

    /** 记忆表的复合主键在墓碑里拼成一个字符串，路径本身不会出现换行 */
    const val MEMORY_SEPARATOR = "\n"

    /** 墓碑只用于增量同步，留半年足够覆盖任何一次正常的跨版本同步 */
    private const val RETENTION_MS = 180L * 24 * 60 * 60 * 1000

    fun createTable(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `$TABLE` (
                `kind` TEXT NOT NULL,
                `target_id` TEXT NOT NULL,
                `deleted_at` INTEGER NOT NULL,
                PRIMARY KEY(`kind`, `target_id`)
            )
            """.trimIndent()
        )
        database.execSQL(
            "DELETE FROM `$TABLE` WHERE `deleted_at` < ?",
            arrayOf(System.currentTimeMillis() - RETENTION_MS),
        )
    }

    fun tableExists(database: SupportSQLiteDatabase): Boolean =
        database.query(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(TABLE),
        ).use { it.moveToFirst() }

    fun mark(
        database: SupportSQLiteDatabase,
        kind: String,
        targetId: String,
        deletedAt: Long = System.currentTimeMillis(),
    ) {
        if (!tableExists(database)) return
        database.execSQL(
            "INSERT OR REPLACE INTO `$TABLE`(`kind`, `target_id`, `deleted_at`) VALUES (?, ?, ?)",
            arrayOf<Any?>(kind, targetId, deletedAt),
        )
    }

    fun markAll(
        database: SupportSQLiteDatabase,
        kind: String,
        targetIds: Collection<String>,
        deletedAt: Long = System.currentTimeMillis(),
    ) {
        if (targetIds.isEmpty() || !tableExists(database)) return
        targetIds.forEach { targetId ->
            database.execSQL(
                "INSERT OR REPLACE INTO `$TABLE`(`kind`, `target_id`, `deleted_at`) VALUES (?, ?, ?)",
                arrayOf<Any?>(kind, targetId, deletedAt),
            )
        }
    }
}
