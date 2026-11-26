package me.rerere.rikkahub.data.sync

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.database.sqlite.SQLiteDatabaseConfiguration
import me.rerere.rikkahub.data.db.SQLiteConfiguration
import java.io.File

internal object DatabaseBackup {
    const val ARCHIVE_DATABASE = "rikka_hub.db"
    const val WAL = "rikka_hub-wal"
    const val SHM = "rikka_hub-shm"

    /** VACUUM INTO includes committed WAL contents in a consistent, standalone snapshot. */
    fun createSnapshot(database: SupportSQLiteDatabase, destination: File) {
        check(!destination.exists()) { "Backup snapshot already exists" }
        database.execSQL("VACUUM main INTO ?", arrayOf(destination.absolutePath))
    }

    /** 不经过 Room，直接从库文件拍快照，供 Room 打不开时的安全模式导出使用。 */
    fun createFileSnapshot(context: Context, databaseFile: File, destination: File) {
        check(!destination.exists()) { "Backup snapshot already exists" }
        require(databaseFile.isFile) { "Database file not found" }
        val configuration = SQLiteConfiguration.configure(
            context,
            SQLiteDatabaseConfiguration(databaseFile.absolutePath, SQLiteDatabase.OPEN_READONLY)
        )
        SQLiteDatabase.openDatabase(configuration, null) {
            error("Database is corrupt")
        }.use { database ->
            database.execSQL("VACUUM main INTO ?", arrayOf(destination.absolutePath))
        }
    }

    /**
     * Only opens the staged copy. The caller must place its matching WAL beside it first.
     *
     * [verify] 关闭时不做整库 `integrity_check`：IPC 同步的归档在解包时由 zip 的 CRC 校验过字节，
     * 而把 1.2 GB 的库整读一遍要几十秒；坏库仍然会在 Room 打开时报出来。
     */
    fun normalize(context: Context, databaseFile: File, verify: Boolean = true) {
        require(databaseFile.isFile && databaseFile.length() > 0) { "Backup database is missing or empty" }
        val configuration = SQLiteConfiguration.configure(
            context,
            SQLiteDatabaseConfiguration(databaseFile.absolutePath, SQLiteDatabase.OPEN_READWRITE)
        )
        SQLiteDatabase.openDatabase(configuration, null) {
            error("Backup database is corrupt")
        }.use { database ->
            checkpoint(database)
            if (verify) {
                database.query("PRAGMA integrity_check").use { cursor ->
                    check(cursor.moveToFirst() && cursor.getString(0) == "ok" && !cursor.moveToNext()) {
                        "Backup database failed its integrity check"
                    }
                }
            }
        }
        removeSidecars(databaseFile)
    }

    fun checkpoint(database: SupportSQLiteDatabase) {
        database.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
            check(cursor.moveToFirst() && cursor.getInt(0) == 0) {
                "Could not checkpoint the backup database"
            }
        }
    }

    fun removeSidecars(databaseFile: File) {
        // All connections must be closed and committed WAL data checkpointed before removal.
        check(File(databaseFile.path + "-wal").length() == 0L) { "Backup WAL was not fully checkpointed" }
        for (suffix in listOf("-wal", "-shm")) {
            val sidecar = File(databaseFile.path + suffix)
            check(!sidecar.exists() || sidecar.delete()) { "Could not remove staged database sidecar" }
        }
    }
}
