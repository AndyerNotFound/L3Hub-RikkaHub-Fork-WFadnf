package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

/**
 * 合入上游 3.3.0 的数据库改动：
 *
 * 1. message_node 的索引从 (conversation_id) 换成 (conversation_id, node_index)。加载会话时按
 *    node_index 排序分页读取，只有 conversation_id 索引时每页都要把整个会话的行取出来排序，
 *    耗时随消息数平方增长。只建索引、删索引，不改动数据。
 * 2. workspaces 加 storage_mounts：挂进 Rootfs 的共享存储目录列表（JSON），只对本地工作区生效。
 *
 * 上游把这两步放在 32→33 与 33→34；Fork 的数据库版本号早已走到 37，33–35 的 schema 与上游
 * 同名但内容不同，因此不并入上游的迁移文件，统一在 37→38 补齐。
 */
val Migration_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(37, 38)
        try {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_message_node_conversation_id_node_index` " +
                    "ON `message_node` (`conversation_id`, `node_index`)"
            )
            db.execSQL("DROP INDEX IF EXISTS `index_message_node_conversation_id`")
            db.execSQL("ALTER TABLE `workspaces` ADD COLUMN `storage_mounts` TEXT NOT NULL DEFAULT '[]'")
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
