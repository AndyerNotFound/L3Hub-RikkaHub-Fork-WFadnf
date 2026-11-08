package me.rerere.rikkahub.ui.pages.log

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import me.rerere.common.android.LogEntry
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导出文件是给人看、也给脚本读的，结构要稳。这里钉住四件事：
 * 顶层带 logs 数组、请求日志的字段原样带出、凭据类请求头在文件里被遮掉、没有日志时也能导出一个合法文件。
 */
class LogExportTest {
    private val requestLog = LogEntry.RequestLog(
        tag = "HTTP",
        timestamp = 1_700_000_000_000,
        url = "https://api.example.com/v1/chat/completions",
        method = "POST",
        requestHeaders = mapOf(
            "X-Trace-Id" to "trace-1",
            "Authorization" to "Bearer sk-live-123",
        ),
        requestBody = "{\"model\":\"m\"}",
        responseCode = 200,
        responseHeaders = mapOf(
            "Content-Type" to "application/json",
            "Set-Cookie" to "session=abc",
        ),
        durationMs = 42,
    )

    @Test
    fun `request log fields survive the export`() {
        val root = JsonInstant.parseToJsonElement(LogExport.toJson(listOf(requestLog))).jsonObject
        assertEquals(1, root["version"]!!.jsonPrimitive.int)

        val logs = root["logs"]!!.jsonArray
        assertEquals(1, logs.size)

        val exported = logs.single().jsonObject
        assertEquals("POST", exported["method"]!!.jsonPrimitive.content)
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            exported["url"]!!.jsonPrimitive.content
        )
        assertEquals("HTTP", exported["tag"]!!.jsonPrimitive.content)
        assertEquals(200, exported["responseCode"]!!.jsonPrimitive.int)
        assertEquals(42L, exported["durationMs"]!!.jsonPrimitive.long)
        assertEquals(
            "trace-1",
            exported["requestHeaders"]!!.jsonObject["X-Trace-Id"]!!.jsonPrimitive.content
        )
    }

    @Test
    fun `credential headers are redacted in the file`() {
        val json = LogExport.toJson(listOf(requestLog))
        assertFalse(json.contains("sk-live-123"))
        assertFalse(json.contains("session=abc"))

        val exported = JsonInstant.parseToJsonElement(json).jsonObject["logs"]!!.jsonArray.single().jsonObject
        val requestHeaders = exported["requestHeaders"]!!.jsonObject
        assertEquals("██", requestHeaders["Authorization"]!!.jsonPrimitive.content)
        assertEquals("trace-1", requestHeaders["X-Trace-Id"]!!.jsonPrimitive.content)

        val responseHeaders = exported["responseHeaders"]!!.jsonObject
        assertEquals("██", responseHeaders["Set-Cookie"]!!.jsonPrimitive.content)
        assertEquals("application/json", responseHeaders["Content-Type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `export stamps the time it was taken`() {
        val root = JsonInstant
            .parseToJsonElement(LogExport.toJson(emptyList(), exportedAt = 1_700_000_000_000))
            .jsonObject
        assertEquals(1_700_000_000_000, root["exportedAt"]!!.jsonPrimitive.long)
        assertTrue(root["logs"]!!.jsonArray.isEmpty())
    }

    @Test
    fun `file name is a timestamped json`() {
        assertTrue(LogExport.fileName().matches(Regex("""l3hub-logs-\d{8}-\d{6}\.json""")))
    }
}
