package com.l3hub.database.wfadnf.core

import android.content.Context
import com.l3hub.syncproto.BundleFormat
import com.l3hub.syncproto.DeltaSync
import io.requery.android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * 增量层在数据中心的落地。
 *
 * SQL 与 L3Hub 共用 `:syncproto` 的那一份，这里只负责用自己的一套 SQLite 打开库（带 FTS5 分词扩展），
 * 以及从 `.l3b` 里取出层与条目。
 */
object HubDelta {
    fun export(context: Context, database: File, destination: File, since: Long): DeltaSync.Summary {
        val connection = HubSqlite.openReadWrite(context, database)
        try {
            return DeltaSync.export(HubSession(connection), destination, since)
        } finally {
            connection.close()
        }
    }

    fun apply(context: Context, target: File, delta: File): DeltaSync.Summary {
        val connection = HubSqlite.openReadWrite(context, target)
        try {
            return DeltaSync.apply(HubSession(connection), delta)
        } finally {
            connection.close()
        }
    }

    /** 取出包里唯一一层的 payload（一个 zip），返回这一层的头部 */
    fun extractLayer(bundle: File, payload: File): BundleFormat.Ref {
        val layers = BundleFormat.readIndex(bundle).refs
        require(layers.isNotEmpty()) { "备份包里没有可用的层" }
        val ref = layers.single()
        BundleFormat.extractPayload(bundle, ref, payload)
        return ref
    }

    /** 从 payload zip 里取出一个固定条目 */
    fun extractEntry(payload: File, name: String, destination: File) {
        destination.parentFile?.mkdirs()
        ZipFile(payload).use { zip ->
            val entry = zip.getEntry(name) ?: error("备份层里没有 $name")
            zip.getInputStream(entry).use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            }
        }
    }
}

/** 把 requery 的连接适配成 [DeltaSync.Session]；事务用三件套表达 */
private class HubSession(private val database: SQLiteDatabase) : DeltaSync.Session {
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

    private fun wrap(cursor: android.database.Cursor): DeltaSync.Cursor = object : DeltaSync.Cursor {
        override fun moveToNext(): Boolean = cursor.moveToNext()
        override fun moveToFirst(): Boolean = cursor.moveToFirst()
        override fun int(column: Int): Int = cursor.getInt(column)
        override fun long(column: Int): Long = cursor.getLong(column)
        override fun string(column: Int): String? = cursor.getString(column)
        override fun close() = cursor.close()
    }
}
