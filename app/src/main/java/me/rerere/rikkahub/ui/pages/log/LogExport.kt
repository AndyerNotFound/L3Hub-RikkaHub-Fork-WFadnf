package me.rerere.rikkahub.ui.pages.log

import kotlinx.serialization.Serializable
import me.rerere.common.android.LogEntry
import me.rerere.rikkahub.utils.JsonInstantPretty
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志导出文件的顶层结构。
 *
 * 里面装的就是界面上的 [LogEntry] 本身，不另建一层模型：界面显示的字段和文件里的字段永远一致，
 * 以后给 [LogEntry] 加字段也不用记着回来同步。类型判别由 sealed class 自带，
 * 单条导出只是 [logs] 里只有一条。
 */
@Serializable
private data class LogExportFile(
    val version: Int = 1,
    val exportedAt: Long,
    val logs: List<LogEntry>,
)

/** 请求日志页的导出：单条和全部走同一套格式，读的人只需要认一种。 */
object LogExport {
    fun fileName(timestamp: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(timestamp))
        return "l3hub-logs-$stamp.json"
    }

    fun toJson(logs: List<LogEntry>, exportedAt: Long = System.currentTimeMillis()): String {
        return JsonInstantPretty.encodeToString(
            LogExportFile.serializer(),
            LogExportFile(
                exportedAt = exportedAt,
                logs = logs,
            )
        )
    }
}
