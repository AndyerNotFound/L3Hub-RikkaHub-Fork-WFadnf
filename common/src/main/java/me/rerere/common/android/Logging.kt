package me.rerere.common.android

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

private const val MAX_RECENT_LOGS = 100

/** 一条日志除字符串内容外的粗略开销（对象头、时间戳、id 等），用来估体积。 */
private const val LOG_ENTRY_OVERHEAD_BYTES = 64L

@Serializable
sealed class LogEntry {
    abstract val id: Uuid
    abstract val timestamp: Long
    abstract val tag: String

    @Serializable
    data class TextLog(
        override val id: Uuid = Uuid.random(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val tag: String,
        val message: String
    ) : LogEntry()

    @Serializable
    data class RequestLog(
        override val id: Uuid = Uuid.random(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val tag: String,
        val url: String,
        val method: String,
        val requestHeaders: Map<String, String> = emptyMap(),
        val requestBody: String? = null,
        val responseCode: Int? = null,
        val responseHeaders: Map<String, String> = emptyMap(),
        val durationMs: Long? = null,
        val error: String? = null
    ) : LogEntry()
}

object Logging {
    /** 请求日志默认开着：真出问题时要先让用户去把开关打开再复现，这一趟就白跑了。 */
    const val DEFAULT_REQUEST_LOGGING_ENABLED = true

    /** 默认上限 4 MB：一次对话请求连请求体也就几十 KB，够放几十条，又不会把内存顶起来。 */
    const val DEFAULT_MAX_LOG_BYTES: Long = 4L * 1024 * 1024

    /** 上限的下限，和界面上滑块的最小一档对齐。 */
    const val MIN_MAX_LOG_BYTES: Long = 32L * 1024

    private val recentLogs = arrayListOf<LogEntry>()

    // 与 recentLogs 同下标：每条日志估算出来的体积，用来在不重新序列化的情况下算总量
    private val recentLogBytes = arrayListOf<Long>()
    private var totalBytes = 0L

    @Volatile
    private var requestLoggingEnabled = DEFAULT_REQUEST_LOGGING_ENABLED

    @Volatile
    private var maxLogBytes = DEFAULT_MAX_LOG_BYTES

    fun log(tag: String, message: String) {
        addLog(LogEntry.TextLog(tag = tag, message = message))
    }

    fun logRequest(entry: LogEntry.RequestLog) {
        if (!requestLoggingEnabled) return
        addLog(entry)
    }

    fun isRequestLoggingEnabled(): Boolean = requestLoggingEnabled

    fun setRequestLoggingEnabled(enabled: Boolean) {
        requestLoggingEnabled = enabled
    }

    fun getMaxLogBytes(): Long = maxLogBytes

    /** 改上限时顺手把已经超出的旧日志清掉，不必等下一次写入。 */
    fun setMaxLogBytes(bytes: Long) {
        maxLogBytes = bytes.coerceAtLeast(MIN_MAX_LOG_BYTES)
        synchronized(recentLogs) {
            trimToLimits()
        }
    }

    private fun addLog(entry: LogEntry) {
        val bytes = entry.estimatedBytes()
        synchronized(recentLogs) {
            recentLogs.add(0, entry)
            recentLogBytes.add(0, bytes)
            totalBytes += bytes
            trimToLimits()
        }
    }

    /**
     * 条数和体积两道闸，超了就从最旧的删起。
     *
     * 最新一条不删：上限调到 32 KB 时，一次带图片的请求自己就超了，把刚记下的那条删掉等于什么都没记。
     */
    private fun trimToLimits() {
        while (recentLogs.size > MAX_RECENT_LOGS || (totalBytes > maxLogBytes && recentLogs.size > 1)) {
            val index = recentLogs.lastIndex
            recentLogs.removeAt(index)
            totalBytes -= recentLogBytes.removeAt(index)
        }
    }

    fun getRecentLogs(): List<LogEntry> {
        synchronized(recentLogs) {
            return recentLogs.toList()
        }
    }

    fun getTextLogs(): List<LogEntry.TextLog> {
        synchronized(recentLogs) {
            return recentLogs.filterIsInstance<LogEntry.TextLog>()
        }
    }

    fun getRequestLogs(): List<LogEntry.RequestLog> {
        synchronized(recentLogs) {
            return recentLogs.filterIsInstance<LogEntry.RequestLog>()
        }
    }

    fun clear() {
        synchronized(recentLogs) {
            recentLogs.clear()
            recentLogBytes.clear()
            totalBytes = 0L
        }
    }
}

/**
 * 估一条日志占多少字节，用来按体积淘汰。
 *
 * 按 UTF-16 的 2 字节/字符算，是内存占用的上限而不是精确值：ASCII 在 ART 的紧凑字符串里
 * 只占 1 字节/字符，按 2 算偏保守——宁可早一点淘汰旧日志，也不要把堆顶起来。
 */
private fun String?.heapBytes(): Long = (this?.length ?: 0) * 2L

private fun Map<String, String>.headersBytes(): Long {
    var bytes = 0L
    forEach { (name, value) -> bytes += name.heapBytes() + value.heapBytes() }
    return bytes
}

private fun LogEntry.estimatedBytes(): Long = when (this) {
    is LogEntry.RequestLog -> LOG_ENTRY_OVERHEAD_BYTES +
        tag.heapBytes() + url.heapBytes() + method.heapBytes() +
        requestBody.heapBytes() + error.heapBytes() +
        requestHeaders.headersBytes() + responseHeaders.headersBytes()

    is LogEntry.TextLog -> LOG_ENTRY_OVERHEAD_BYTES + tag.heapBytes() + message.heapBytes()
}
