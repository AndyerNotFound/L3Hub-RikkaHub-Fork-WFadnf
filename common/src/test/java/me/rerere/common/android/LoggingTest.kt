package me.rerere.common.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 日志缓冲按体积算账：超了就从最旧的删，最新一条永远留着。
 *
 * 体积是估算值：一条文本日志算 64 字节固定开销 + 标签 + 正文，字符串按 2 字节/字符。
 * 这里按这个规则写死数字来断言，估算规则一改这些用例先红。
 */
class LoggingTest {
    // 64 + "T"(2) + 5000 字符正文(10000)
    private val textLogBytes = 64L + 2L + 10_000L

    @Before
    fun reset() {
        Logging.clear()
        Logging.setRequestLoggingEnabled(Logging.DEFAULT_REQUEST_LOGGING_ENABLED)
        Logging.setMaxLogBytes(Logging.DEFAULT_MAX_LOG_BYTES)
    }

    @Test
    fun `drops the oldest logs once the size limit is reached`() {
        Logging.setMaxLogBytes(Logging.MIN_MAX_LOG_BYTES)
        // 每条约 10066 字节，32 KB 放得下 3 条
        repeat(10) { Logging.log("T", "x".repeat(4_999) + it) }

        val logs = Logging.getTextLogs()
        assertEquals(3, logs.size)
        assertEquals('9', logs.first().message.last())
        assertEquals('7', logs.last().message.last())
    }

    @Test
    fun `keeps the newest log even when it alone exceeds the limit`() {
        Logging.setMaxLogBytes(Logging.MIN_MAX_LOG_BYTES)
        Logging.log("T", "y".repeat(40_000))

        val logs = Logging.getTextLogs()
        assertEquals(1, logs.size)
        assertEquals(40_000, logs.single().message.length)
    }

    @Test
    fun `lowering the limit trims what is already recorded`() {
        repeat(10) { Logging.log("T", "z".repeat(4_999) + it) }
        assertEquals(10, Logging.getTextLogs().size)

        Logging.setMaxLogBytes(Logging.MIN_MAX_LOG_BYTES)

        val logs = Logging.getTextLogs()
        assertEquals(3, logs.size)
        assertEquals('9', logs.first().message.last())
        assertTrue(textLogBytes * 3 <= Logging.MIN_MAX_LOG_BYTES)
    }

    @Test
    fun `request logs stay out while recording is off`() {
        Logging.setRequestLoggingEnabled(false)
        Logging.logRequest(LogEntry.RequestLog(tag = "HTTP", url = "https://example.com", method = "GET"))
        assertEquals(0, Logging.getRequestLogs().size)

        Logging.setRequestLoggingEnabled(true)
        Logging.logRequest(LogEntry.RequestLog(tag = "HTTP", url = "https://example.com", method = "GET"))
        assertEquals(1, Logging.getRequestLogs().size)
    }

    @Test
    fun `clearing resets the accounted size`() {
        Logging.setMaxLogBytes(Logging.MIN_MAX_LOG_BYTES)
        repeat(10) { Logging.log("T", "x".repeat(4_999) + it) }
        Logging.clear()

        // 清空之后体积账也要归零，否则新日志会被上一轮的旧账挤掉
        repeat(3) { Logging.log("T", "x".repeat(4_999) + it) }
        assertEquals(3, Logging.getTextLogs().size)
    }
}
