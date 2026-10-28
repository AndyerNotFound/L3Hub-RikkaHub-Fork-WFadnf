package me.rerere.rikkahub.data.sync

import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/**
 * 备份里的附件用绝对路径记录：`file:///data/user/0/<包名>/files/upload/xxx.png`。
 *
 * 换了 applicationId 之后，这些路径指向原应用的私有目录，新应用没有读权限，
 * 于是头像、背景图和聊天附件全部显示不出来——文件本身其实已经解包到了同样的相对位置。
 * 恢复时把前缀统一改写成当前应用的 filesDir。
 */
internal object BackupPaths {
    /** 同时覆盖 `file://` URI 和裸路径(字体只存路径)，兼容 /data/user/N 与 /data/data 两种写法 */
    private val FILES_PREFIX = Regex("""(file://)?/data/(?:user/\d+|data)/[^/"]+/files/""")

    /** 一次恢复里出现的不同来源包名不会太多，留点余量防止反复扫描 */
    private const val MAX_PREFIXES = 4

    /** 当前应用的附件路径前缀，结尾带分隔符 */
    fun filesPrefix(filesDir: File): String = filesDir.absolutePath.trimEnd('/') + "/"

    /** 把文本里指向「某个应用私有 files 目录」的路径改写到当前应用 */
    fun rewrite(text: String, filesDir: File): String {
        val target = filesPrefix(filesDir)
        return FILES_PREFIX.replace(text) { match -> match.groupValues[1] + target }
    }

    /** 取出文本里第一个附件前缀，数据库里用它做精确替换 */
    fun firstPrefix(text: String): String? = FILES_PREFIX.find(text)?.value

    /** 改写备份数据库里所有文本列中的附件路径 */
    fun rewriteDatabase(database: SupportSQLiteDatabase, filesDir: File) {
        val target = filesPrefix(filesDir)
        for (table in textTables(database)) {
            for (column in textColumns(database, table)) {
                rewriteColumn(database, table, column, target)
            }
        }
    }

    private fun textTables(database: SupportSQLiteDatabase): List<String> =
        database.query(
            """
            SELECT name FROM sqlite_master
            WHERE type = 'table'
              AND name NOT LIKE 'sqlite_%'
              AND name NOT LIKE 'android_%'
              AND name NOT LIKE 'message_fts%'
            """.trimIndent()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    private fun textColumns(database: SupportSQLiteDatabase, table: String): List<String> =
        database.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val typeIndex = cursor.getColumnIndexOrThrow("type")
            buildList {
                while (cursor.moveToNext()) {
                    val type = cursor.getString(typeIndex)?.uppercase().orEmpty()
                    if (type.contains("CHAR") || type.contains("TEXT") || type.contains("CLOB")) {
                        add(cursor.getString(nameIndex))
                    }
                }
            }
        }

    private fun rewriteColumn(
        database: SupportSQLiteDatabase,
        table: String,
        column: String,
        target: String,
    ) {
        val prefixes = linkedSetOf<String>()
        database.query(
            "SELECT `$column` FROM `$table` WHERE `$column` LIKE '%/files/%' LIMIT 500"
        ).use { cursor ->
            while (cursor.moveToNext() && prefixes.size < MAX_PREFIXES) {
                firstPrefix(cursor.getString(0) ?: continue)?.let(prefixes::add)
            }
        }
        for (prefix in prefixes) {
            if (prefix == target) continue
            database.execSQL(
                "UPDATE `$table` SET `$column` = REPLACE(`$column`, ?, ?) WHERE `$column` LIKE ?",
                arrayOf(prefix, target, "%$prefix%"),
            )
        }
    }
}
