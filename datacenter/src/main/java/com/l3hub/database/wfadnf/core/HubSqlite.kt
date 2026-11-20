package com.l3hub.database.wfadnf.core

import android.content.Context
import io.requery.android.database.sqlite.SQLiteCustomExtension
import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.database.sqlite.SQLiteDatabaseConfiguration
import java.io.File

/**
 * 数据中心里的所有 SQLite 都从这里打开。
 *
 * L3Hub 的消息索引是 FTS5 加自定义 simple 分词器，分词器在 `libsimple.so` 里；不带这份扩展就打不开
 * 带索引的库，跨版本搬运时也建不出 `message_fts`。APK 里打包了应用用的同一份 .so（见 build.gradle.kts
 * 的 jniLibs 配置），这里按需挂上。
 */
object HubSqlite {
    private const val TOKENIZER_LIBRARY = "libsimple"

    fun open(context: Context, file: File, flags: Int): SQLiteDatabase {
        val configuration = SQLiteDatabaseConfiguration(file.absolutePath, flags)
        configuration.customExtensions.add(
            SQLiteCustomExtension(
                context.applicationInfo.nativeLibraryDir + "/" + TOKENIZER_LIBRARY,
                null,
            )
        )
        return SQLiteDatabase.openDatabase(configuration, null) {
            error("无法打开数据库：${file.name}")
        }
    }

    fun openReadOnly(context: Context, file: File): SQLiteDatabase =
        open(context, file, SQLiteDatabase.OPEN_READONLY)

    fun openReadWrite(context: Context, file: File): SQLiteDatabase =
        open(context, file, SQLiteDatabase.OPEN_READWRITE)

    fun openOrCreate(context: Context, file: File): SQLiteDatabase =
        open(context, file, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY)
}
