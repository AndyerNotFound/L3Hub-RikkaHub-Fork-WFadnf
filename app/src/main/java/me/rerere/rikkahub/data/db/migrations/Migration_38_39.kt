package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

/**
 * 新增 model_fingerprint_history：模型指纹检测的历史记录表。
 *
 * 只建新表，不碰任何已有表，旧数据原样保留。
 * 建表语句与 ModelFingerprintEntity 生成的 schema 逐字对应（列名、类型、NOT NULL、AUTOINCREMENT 主键、
 * index_model_fingerprint_history_created_at），Room 运行时校验才不会失败。
 */
val Migration_38_39 = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(38, 39)
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `model_fingerprint_history` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "`model_label` TEXT NOT NULL, " +
                    "`provider_label` TEXT NOT NULL, " +
                    "`result_label` TEXT, " +
                    "`candidates_json` TEXT NOT NULL, " +
                    "`confidence` REAL NOT NULL, " +
                    "`detector_version` TEXT NOT NULL, " +
                    "`status` TEXT NOT NULL, " +
                    "`error` TEXT)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_model_fingerprint_history_created_at` " +
                    "ON `model_fingerprint_history` (`created_at`)"
            )
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
