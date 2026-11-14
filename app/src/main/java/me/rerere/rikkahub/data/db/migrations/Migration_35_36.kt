package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

/**
 * 文件夹绑定工作区：conversation_folder 加 workspace_id / workspace_ids。
 *
 * 原来用的是 AutoMigration，但仓库里没有提交 36.json；数据库版本再往上走时 Room
 * 会要求补上旧的 schema 文件。这里改成手写迁移，效果与自动生成的一致。
 */
val Migration_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(35, 36)
        try {
            db.execSQL("ALTER TABLE `conversation_folder` ADD COLUMN `workspace_id` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `conversation_folder` ADD COLUMN `workspace_ids` TEXT NOT NULL DEFAULT ''")
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
