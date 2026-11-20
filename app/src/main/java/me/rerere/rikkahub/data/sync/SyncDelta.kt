package me.rerere.rikkahub.data.sync

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import com.l3hub.syncproto.DeltaSync
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
 * 增量同步的数据层入口。
 *
 * SQL 本身在 `:syncproto` 的 [DeltaSync] 里，数据中心用的是同一份，只有打开数据库的方式不同：
 * 本应用带 `libsimple.so` 分词扩展，走 [SQLiteConfiguration]。这里只做适配与日志。
 */
object SyncDelta {
    const val FILE_NAME = "sync_delta.db"

    fun export(
        database: SupportSQLiteDatabase,
        destination: File,
        since: Long,
    ): SyncDeltaSummary =
        DeltaSync.export(SupportSession(database), destination, since).toSummary()

    fun apply(context: Context, liveDatabase: File, deltaFile: File): SyncDeltaSummary {
        val configuration = SQLiteConfiguration.configure(
            context,
            SQLiteDatabaseConfiguration(liveDatabase.absolutePath, SQLiteDatabase.OPEN_READWRITE),
        )
        SQLiteDatabase.openDatabase(configuration, null) {
            error("Cannot open the live database for the delta")
        }.use { database ->
            return DeltaSync.apply(RequerySession(database), deltaFile).toSummary()
        }
    }

    /** 已经打开的连接（恢复阶段用）；调用方负责关闭 */
    fun apply(database: SQLiteDatabase, deltaFile: File): SyncDeltaSummary =
        DeltaSync.apply(RequerySession(database), deltaFile).toSummary()

    private fun DeltaSync.Summary.toSummary() = SyncDeltaSummary(conversations = conversations, nodes = nodes)
}

private fun wrap(cursor: android.database.Cursor): DeltaSync.Cursor = object : DeltaSync.Cursor {
    override fun moveToNext(): Boolean = cursor.moveToNext()
    override fun moveToFirst(): Boolean = cursor.moveToFirst()
    override fun int(column: Int): Int = cursor.getInt(column)
    override fun long(column: Int): Long = cursor.getLong(column)
    override fun string(column: Int): String? = cursor.getString(column)
    override fun close() = cursor.close()
}

/** Room 的连接：事务用 beginTransaction / setTransactionSuccessful / endTransaction 三件套表达 */
private class SupportSession(private val database: SupportSQLiteDatabase) : DeltaSync.Session {
    override fun exec(sql: String, args: Array<Any?>?) {
        if (args == null) database.execSQL(sql) else database.execSQL(sql, args)
    }

    override fun query(sql: String, args: Array<Any?>?): DeltaSync.Cursor =
        wrap(if (args == null) database.query(sql) else database.query(sql, args))

    override fun begin() = database.beginTransaction()

    override fun commit() {
        database.setTransactionSuccessful()
        database.endTransaction()
    }

    override fun rollback() = database.endTransaction()
}

/** 恢复阶段直接打开的 requery 连接 */
private class RequerySession(private val database: SQLiteDatabase) : DeltaSync.Session {
    override fun exec(sql: String, args: Array<Any?>?) {
        if (args == null) database.execSQL(sql) else database.execSQL(sql, args)
    }

    override fun query(sql: String, args: Array<Any?>?): DeltaSync.Cursor =
        wrap(if (args == null) database.rawQuery(sql, null) else database.rawQuery(sql, args))

    override fun begin() = database.beginTransaction()

    override fun commit() {
        database.setTransactionSuccessful()
        database.endTransaction()
    }

    override fun rollback() = database.endTransaction()
}
