package com.l3hub.database.wfadnf.core

import android.content.Context
import com.l3hub.syncproto.PeerSchema
import io.requery.android.database.sqlite.SQLiteDatabase
import java.io.File

/** 一次跨结构换算的结果；界面只挑关键几项展示，完整内容进日志 */
data class TranslationReport(
    val sourceSchemaVersion: Int = 0,
    val targetSchemaVersion: Int = 0,
    val copied: Map<String, Long> = emptyMap(),
    val droppedColumns: List<String> = emptyList(),
    val filledColumns: List<String> = emptyList(),
    val skippedTables: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

/**
 * 把一个库换算成另一个构建的结构。
 *
 * 目标结构直接来自那个构建运行中的 `sqlite_master`（`METHOD_SCHEMA`），不依赖编译期导出的 schema
 * 文件——fork 的 migrations 各自演进，导出文件未必和线上库一致。做法是：按目标结构重建一个空库，
 * 再把源库逐表按列名取交集抄过去。目标多出来的列用它的 `DEFAULT`（没有默认值且非空时整张表跳过并报告），
 * 源里多出来的列丢弃但记入报告。SQLite 是动态类型，值原样搬运即可。
 *
 * 消息索引是 FTS5 虚表，行号被 `message_fts_rowid` 引用。FTS 行连同 rowid 一起抄，映射表则按抄完的
 * 索引重建，这样即便旧库的映射有缺口也不会把搜索指错会话。
 */
object SchemaTranslation {
    /** 不建的表：由 Android / SQLite 自己维护，建了反而冲突 */
    private val CREATE_SKIPPED = setOf("android_metadata", "sqlite_sequence")

    /** 建了但不抄数据的表：Room 的结构指纹单独写，映射表按抄完的索引重建 */
    private val DATA_SKIPPED = setOf("android_metadata", "sqlite_sequence", "room_master_table")
    private val DERIVED_TABLES = setOf("message_fts_rowid")
    private val FTS_SHADOW_SUFFIXES = listOf(
        "_content", "_segments", "_segdir", "_docsize", "_stat", "_data", "_idx", "_config",
    )

    fun translate(
        context: Context,
        source: File,
        target: PeerSchema,
        destination: File,
    ): TranslationReport {
        require(target.objects.isNotEmpty()) { "目标构建没有提供库结构，无法跨版本换算" }
        require(!target.identityHash.isNullOrBlank()) {
            "目标构建没有提供 Room 结构指纹，重建后的库会被它拒绝打开"
        }
        val workDirectory = requireNotNull(destination.parentFile) { "目标路径没有父目录" }
        check(workDirectory.isDirectory || workDirectory.mkdirs()) { "无法创建换算目录" }
        val sourceVersion = runCatching {
            HubSqlite.openReadOnly(context, source).use { SnapshotReader.pragma(it, "user_version").toInt() }
        }.getOrDefault(0)
        val work = File(workDirectory, "translate-${System.nanoTime()}.db")
        work.delete()
        destination.delete()
        try {
            // 快照只读使用：直接 ATTACH，不再先复制一份同样大小的源库（1.2 GB 的库要白写一遍）
            val report = build(context, work, source, target, sourceVersion)
            vacuum(context, work, destination, target.schemaVersion)
            return report
        } finally {
            work.delete()
        }
    }

    private fun build(
        context: Context,
        work: File,
        source: File,
        target: PeerSchema,
        sourceVersion: Int,
    ): TranslationReport {
        val virtualTables = target.objects
            .filter { it.type == "table" && it.sql.trimStart().startsWith("CREATE VIRTUAL TABLE", ignoreCase = true) }
            .map { it.name }
            .toSet()
        val shadowTables = virtualTables
            .flatMap { base -> FTS_SHADOW_SUFFIXES.map { base + it } }
            .toSet()
        val tables = target.objects.filter {
            it.type == "table" && it.name !in CREATE_SKIPPED && it.name !in shadowTables
        }
        val indices = target.objects.filter { it.type == "index" }
        val triggers = target.objects.filter { it.type == "trigger" }
        val views = target.objects.filter { it.type == "view" }

        val copied = linkedMapOf<String, Long>()
        val droppedColumns = mutableListOf<String>()
        val filledColumns = mutableListOf<String>()
        val skippedTables = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val database = HubSqlite.openOrCreate(context, work)
        try {
            // 页大小必须在建表之前定下来；取目标库的值，重建出来的文件与它一致
            if (target.pageSize > 0) {
                runCatching { database.execSQL("PRAGMA page_size = ${target.pageSize}") }
            }
            database.execSQL("PRAGMA foreign_keys = OFF")
            for (table in tables) {
                database.execSQL(table.sql)
            }
            for (view in views) {
                runCatching { database.execSQL(view.sql) }
                    .onFailure { warnings += "视图 ${view.name} 未能建立：${it.message}" }
            }
            // 索引先建：唯一索引冲突会在这里暴露，插入时再失败就说不清是哪一列的问题
            for (index in indices) {
                runCatching { database.execSQL(index.sql) }
                    .onFailure { warnings += "索引 ${index.name} 未能建立：${it.message}" }
            }
            database.execSQL("ATTACH DATABASE ? AS src", arrayOf(source.absolutePath))
            try {
                database.beginTransaction()
                try {
                    for (table in tables) {
                        if (table.name in DATA_SKIPPED || table.name in DERIVED_TABLES) continue
                        if (!SnapshotReader.tableExists(database, "src", table.name)) {
                            skippedTables += table.name
                            continue
                        }
                        val result = copyTable(
                            database = database,
                            table = table.name,
                            virtual = table.name in virtualTables,
                            droppedColumns = droppedColumns,
                            filledColumns = filledColumns,
                        )
                        when (result) {
                            is CopyResult.Copied -> copied[table.name] = result.rows
                            is CopyResult.Skipped -> {
                                skippedTables += table.name
                                warnings += result.reason
                            }
                        }
                    }
                    rebuildMessageIndex(database, warnings)
                    database.setTransactionSuccessful()
                } finally {
                    database.endTransaction()
                }
                database.rawQuery("PRAGMA foreign_key_check", null).use { cursor ->
                    var violations = 0
                    while (cursor.moveToNext()) violations++
                    if (violations > 0) warnings += "有 $violations 条外键指向不存在的行（目标应用会按空引用处理）"
                }
            } finally {
                database.execSQL("DETACH DATABASE src")
            }
            // 触发器最后建：抄数据时不触发，免得半途改写别的表
            for (trigger in triggers) {
                runCatching { database.execSQL(trigger.sql) }
                    .onFailure { warnings += "触发器 ${trigger.name} 未能建立：${it.message}" }
            }
            database.execSQL("PRAGMA main.user_version = ${target.schemaVersion}")
            if (SnapshotReader.tableExists(database, "main", "room_master_table")) {
                database.execSQL(
                    "INSERT OR REPLACE INTO room_master_table(id, identity_hash) VALUES(42, ?)",
                    arrayOf(target.identityHash),
                )
            }
        } finally {
            database.close()
        }
        return TranslationReport(
            sourceSchemaVersion = sourceVersion,
            targetSchemaVersion = target.schemaVersion,
            copied = copied,
            droppedColumns = droppedColumns.distinct(),
            filledColumns = filledColumns.distinct(),
            skippedTables = skippedTables,
            warnings = warnings,
        )
    }

    private sealed interface CopyResult {
        data class Copied(val rows: Long) : CopyResult
        data class Skipped(val reason: String) : CopyResult
    }

    private data class Column(
        val name: String,
        val notNull: Boolean,
        val defaultValue: String?,
    )

    private fun copyTable(
        database: SQLiteDatabase,
        table: String,
        virtual: Boolean,
        droppedColumns: MutableList<String>,
        filledColumns: MutableList<String>,
    ): CopyResult {
        val targetColumns = columns(database, "main", table)
        val sourceColumns = columns(database, "src", table).map { it.name }.toSet()
        if (targetColumns.isEmpty()) return CopyResult.Skipped("$table 在目标结构里没有列定义")

        val missing = targetColumns.filterNot { it.name in sourceColumns }
        val fills = mutableMapOf<String, String>()
        for (column in missing) {
            val default = column.defaultValue?.trim()
            when {
                !default.isNullOrEmpty() -> fills[column.name] = default
                !column.notNull -> fills[column.name] = "NULL"
                else -> return CopyResult.Skipped("$table.${column.name} 是新增的非空列且没有默认值，无法补齐")
            }
        }
        val selectList = targetColumns.map { column ->
            if (column.name in sourceColumns) quote(column.name) else fills.getValue(column.name)
        }
        val insertList = targetColumns.map { quote(it.name) }
        val prefix = if (virtual) listOf("rowid") else emptyList()
        val sql = "INSERT INTO main.${quote(table)} (" +
            (prefix + insertList).joinToString(", ") + ") SELECT " +
            (prefix + selectList).joinToString(", ") + " FROM src.${quote(table)}"
        database.execSQL(sql)
        for (column in missing) {
            if (fills[column.name] == "NULL") filledColumns += "$table.${column.name}"
        }
        sourceColumns.filterNot { it in targetColumns.map { column -> column.name } }
            .forEach { droppedColumns += "$table.$it" }
        return CopyResult.Copied(SnapshotReader.count(database, "main", table))
    }

    /**
     * 映射表按抄完的索引重建：FTS 的 rowid 在写入时可能被重新编号，
     * 沿用源库的映射会把搜索指向别的会话。
     */
    private fun rebuildMessageIndex(database: SQLiteDatabase, warnings: MutableList<String>) {
        if (!SnapshotReader.tableExists(database, "main", "message_fts")) return
        if (!SnapshotReader.tableExists(database, "main", "message_fts_rowid")) return
        runCatching {
            database.execSQL("DELETE FROM main.message_fts_rowid")
            database.execSQL(
                "INSERT INTO main.message_fts_rowid(fts_rowid, conversation_id) " +
                    "SELECT rowid, conversation_id FROM main.message_fts",
            )
        }.onFailure { warnings += "消息索引映射未能重建：${it.message}" }
    }

    private fun columns(database: SQLiteDatabase, schema: String, table: String): List<Column> =
        database.rawQuery("PRAGMA $schema.table_info(${quote(table)})", null).use { cursor ->
            val columns = mutableListOf<Column>()
            val nameIndex = cursor.getColumnIndex("name")
            val notNullIndex = cursor.getColumnIndex("notnull")
            val defaultIndex = cursor.getColumnIndex("dflt_value")
            while (cursor.moveToNext()) {
                columns += Column(
                    name = cursor.getString(nameIndex),
                    notNull = cursor.getInt(notNullIndex) != 0,
                    defaultValue = if (cursor.isNull(defaultIndex)) null else cursor.getString(defaultIndex),
                )
            }
            columns
        }

    private fun vacuum(context: Context, work: File, destination: File, schemaVersion: Int) {
        HubSqlite.openReadWrite(context, work).use { database ->
            database.execSQL("VACUUM main INTO ?", arrayOf(destination.absolutePath))
        }
        // 不指望 VACUUM 一定带上 user_version：Room 看到 0 会去找 0→N 的迁移，直接判定失败
        HubSqlite.openReadWrite(context, destination).use { database ->
            database.execSQL("PRAGMA user_version = $schemaVersion")
        }
    }

    private fun quote(name: String): String = "\"" + name.replace("\"", "\"\"") + "\""
}
