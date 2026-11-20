package com.l3hub.database.wfadnf.core

import android.content.Context
import android.database.Cursor
import io.requery.android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** 库里的一个非表对象：索引、触发器或视图 */
data class DbObjectInfo(
    val type: String,
    val name: String,
    val sql: String,
)

data class DbColumnInfo(
    val name: String,
    val type: String,
    val notNull: Boolean,
    val primaryKey: Boolean,
)

data class DbTableInfo(
    val name: String,
    val rows: Long,
    val columns: List<DbColumnInfo>,
    val sql: String,
)

/** 一份快照库的概况：版本、页大小、体积、全部表与其余对象 */
data class DbSummary(
    val schemaVersion: Int,
    val pageSize: Int,
    val pageCount: Long,
    val bytes: Long,
    val identityHash: String?,
    val tables: List<DbTableInfo>,
    val others: List<DbObjectInfo>,
)

data class DbConversation(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val createdAt: Long,
    val nodes: Int,
    val pinned: Boolean,
)

data class DbMessage(
    val role: String,
    val text: String,
    val attachments: List<String>,
)

data class DbAttachment(
    val name: String,
    val path: String,
    val mime: String,
    val bytes: Long,
)

/** 表里的一页数据；[columns] 是游标给的列名，[rows] 已全部转成可显示的字符串 */
data class DbTablePage(
    val columns: List<String>,
    val rows: List<List<String>>,
)

/** 镜像目录里的一个子目录（upload、skills、fonts 之类） */
data class MirrorFolder(
    val name: String,
    val files: Int,
    val bytes: Long,
)

/**
 * 只读地拆开一份快照库，给「数据库」页用。
 *
 * 全部查询都跑在副本上（快照本来就是脱离原应用的整份库），不写任何一行；表名与列名都从
 * `sqlite_master` / `pragma_table_info` 现取，跨版本、跨分支都不会因为写死列名而崩。
 */
object DbInspector {
    private const val CELL_LIMIT = 400
    private const val MESSAGE_LIMIT = 500

    fun summary(context: Context, file: File): DbSummary = HubSqlite.openReadOnly(context, file).use { database ->
        val tables = mutableListOf<DbTableInfo>()
        database.rawQuery(
            "SELECT name, sql FROM main.sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0) ?: continue
                tables += DbTableInfo(
                    name = name,
                    rows = runCatching { SnapshotReader.count(database, "main", name) }.getOrDefault(0L),
                    columns = columns(database, name),
                    sql = cursor.getString(1).orEmpty(),
                )
            }
        }
        val others = mutableListOf<DbObjectInfo>()
        database.rawQuery(
            "SELECT type, name, sql FROM main.sqlite_master " +
                "WHERE type IN ('index', 'trigger', 'view') AND sql IS NOT NULL ORDER BY type, name",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                others += DbObjectInfo(
                    type = cursor.getString(0).orEmpty(),
                    name = cursor.getString(1).orEmpty(),
                    sql = cursor.getString(2).orEmpty(),
                )
            }
        }
        val pageSize = SnapshotReader.pragma(database, "page_size")
        val pageCount = SnapshotReader.pragma(database, "page_count")
        DbSummary(
            schemaVersion = SnapshotReader.pragma(database, "user_version").toInt(),
            pageSize = pageSize.toInt(),
            pageCount = pageCount,
            bytes = file.length().takeIf { it > 0 } ?: (pageSize * pageCount),
            identityHash = SnapshotReader.identityHash(database),
            tables = tables,
            others = others,
        )
    }

    /** 表的前 [limit] 行，用于「看一眼里面到底存了什么」 */
    fun tablePage(context: Context, file: File, table: String, limit: Int = 40): DbTablePage =
        HubSqlite.openReadOnly(context, file).use { database ->
            val safe = table.replace("`", "``")
            database.rawQuery("SELECT * FROM main.`$safe` LIMIT $limit", null).use { cursor ->
                val columns = cursor.columnNames.toList()
                val rows = mutableListOf<List<String>>()
                while (cursor.moveToNext()) {
                    rows += columns.indices.map { index -> cell(cursor, index) }
                }
                DbTablePage(columns = columns, rows = rows)
            }
        }

    fun conversations(context: Context, file: File, limit: Int = 300): List<DbConversation> =
        HubSqlite.openReadOnly(context, file).use { database ->
            if (!SnapshotReader.tableExists(database, "main", "ConversationEntity")) return@use emptyList()
            val nodes = if (SnapshotReader.tableExists(database, "main", "message_node")) {
                "(SELECT COUNT(*) FROM message_node n WHERE n.conversation_id = c.id)"
            } else {
                "0"
            }
            database.rawQuery(
                "SELECT c.id, c.title, c.update_at, c.create_at, c.is_pinned, $nodes " +
                    "FROM ConversationEntity c ORDER BY c.update_at DESC LIMIT $limit",
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            DbConversation(
                                id = cursor.getString(0).orEmpty(),
                                title = cursor.getString(1).orEmpty().ifBlank { "（无标题）" },
                                updatedAt = cursor.getLong(2),
                                createdAt = cursor.getLong(3),
                                pinned = runCatching { cursor.getInt(4) != 0 }.getOrDefault(false),
                                nodes = runCatching { cursor.getInt(5) }.getOrDefault(0),
                            )
                        )
                    }
                }
            }
        }

    /** 一个会话里的消息：角色、正文前几百字、引用的附件路径 */
    fun messages(context: Context, file: File, conversationId: String): List<DbMessage> =
        HubSqlite.openReadOnly(context, file).use { database ->
            if (!SnapshotReader.tableExists(database, "main", "message_node")) return@use emptyList()
            database.rawQuery(
                "SELECT messages FROM message_node WHERE conversation_id = ? ORDER BY node_index LIMIT $MESSAGE_LIMIT",
                arrayOf(conversationId),
            ).use { cursor ->
                val messages = mutableListOf<DbMessage>()
                while (cursor.moveToNext()) {
                    messages += parseMessages(cursor.getString(0).orEmpty())
                }
                messages
            }
        }

    /**
     * `message_node.messages` 是 `List<UIMessage>` 的 JSON。
     *
     * 不反序列化成模型：数据中心没有 L3Hub 的模型类，而 UIMessage 的字段还在演进。这里只按
     * 结构取 role、text part 的正文，以及任何 part 上的 url（图片、视频、音频、文档都带它）。
     */
    private fun parseMessages(json: String): List<DbMessage> {
        if (json.isBlank()) return emptyList()
        val array = runCatching { HubJson.parseToJsonElement(json).jsonArray }.getOrNull()
            ?: return listOf(DbMessage("?", "（消息 JSON 解析失败）", emptyList()))
        return array.mapNotNull { element ->
            val message = element as? JsonObject ?: return@mapNotNull null
            val role = message["role"].asText() ?: "?"
            val parts = (message["parts"] as? JsonArray).orEmpty()
            val text = parts.mapNotNull { part ->
                val partObject = part as? JsonObject ?: return@mapNotNull null
                if (partObject["type"].asText() != "text") return@mapNotNull null
                partObject["text"].asText()
            }.joinToString("\n").trim().take(CELL_LIMIT)
            val attachments = parts.mapNotNull { part ->
                (part as? JsonObject)?.get("url").asText()?.takeIf { it.isNotBlank() }
            }
            DbMessage(
                role = role,
                text = text.ifBlank { if (attachments.isEmpty()) "（无正文）" else "" },
                attachments = attachments,
            )
        }
    }

    /** `managed_files` 里登记过的附件；老库没有这张表时返回空 */
    fun attachments(context: Context, file: File, limit: Int = 500): List<DbAttachment> =
        HubSqlite.openReadOnly(context, file).use { database ->
            if (!SnapshotReader.tableExists(database, "main", "managed_files")) return@use emptyList()
            database.rawQuery(
                "SELECT display_name, relative_path, mime_type, size_bytes FROM managed_files " +
                    "ORDER BY updated_at DESC LIMIT $limit",
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            DbAttachment(
                                name = cursor.getString(0).orEmpty(),
                                path = cursor.getString(1).orEmpty(),
                                mime = cursor.getString(2).orEmpty(),
                                bytes = runCatching { cursor.getLong(3) }.getOrDefault(0L),
                            )
                        )
                    }
                }
            }
        }

    private fun columns(database: SQLiteDatabase, table: String): List<DbColumnInfo> =
        database.rawQuery(
            "SELECT name, type, `notnull`, pk FROM pragma_table_info(?)",
            arrayOf(table),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        DbColumnInfo(
                            name = cursor.getString(0).orEmpty(),
                            type = cursor.getString(1).orEmpty(),
                            notNull = runCatching { cursor.getInt(2) != 0 }.getOrDefault(false),
                            primaryKey = runCatching { cursor.getInt(3) != 0 }.getOrDefault(false),
                        )
                    )
                }
            }
        }

    private fun cell(cursor: Cursor, index: Int): String = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> "—"
        Cursor.FIELD_TYPE_BLOB -> "<二进制>"
        else -> cursor.getString(index).orEmpty().take(CELL_LIMIT)
    }

    private fun JsonElement?.asText(): String? = runCatching { this?.jsonPrimitive?.contentOrNull }.getOrNull()
}

/** 附件镜像的目录概况；数据中心不解析文件内容，只看每个目录有多少、占多大 */
object HubMirror {
    private const val ROOT_FOLDER = "（根目录）"

    fun folders(mirrorDir: File): List<MirrorFolder> {
        val children = mirrorDir.listFiles().orEmpty()
        val folders = children.mapNotNull { child ->
            when {
                child.isDirectory -> {
                    val (files, bytes) = ArchiveTool.countFiles(child)
                    if (files == 0) null else MirrorFolder(name = child.name, files = files, bytes = bytes)
                }

                else -> null
            }
        }
        val rootFiles = children.filter { it.isFile }
        val all = if (rootFiles.isEmpty()) {
            folders
        } else {
            folders + MirrorFolder(
                name = ROOT_FOLDER,
                files = rootFiles.size,
                bytes = rootFiles.sumOf { it.length() },
            )
        }
        return all.sortedByDescending { it.bytes }
    }

    fun files(mirrorDir: File, folder: String, limit: Int = 400): List<Pair<String, Long>> {
        val targets = if (folder == ROOT_FOLDER) {
            mirrorDir.listFiles().orEmpty().filter { it.isFile }.asSequence()
        } else {
            File(mirrorDir, folder).walkTopDown().filter { it.isFile }
        }
        return targets
            .take(limit)
            .map { file -> file.relativeTo(mirrorDir).invariantSeparatorsPath to file.length() }
            .toList()
    }
}
