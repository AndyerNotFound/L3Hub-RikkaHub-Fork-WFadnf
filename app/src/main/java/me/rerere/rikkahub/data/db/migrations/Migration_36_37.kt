package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

/**
 * 用量流水保存费用快照：usage_record 加可空的 cost 列。
 *
 * 旧流水保持 NULL，由统计页首次打开时按当前价目补算，之后价目和规则的改动不再回写历史。
 */
val Migration_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(36, 37)
        try {
            db.execSQL("ALTER TABLE `usage_record` ADD COLUMN `cost` REAL")
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
