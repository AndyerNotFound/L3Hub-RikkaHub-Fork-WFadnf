package me.rerere.rikkahub.data.ai.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpConfigJsonTest {
    private fun sse(
        name: String = "search",
        enable: Boolean = true,
    ) = McpServerConfig.SseTransportServer(
        commonOptions = McpCommonOptions(
            enable = enable,
            name = name,
            headers = listOf("Authorization" to "Bearer token"),
            tools = listOf(McpTool(name = "web_search", enable = false, needsApproval = true)),
        ),
        url = "https://example.com/sse",
    )

    @Test
    fun `export and import round trip keeps server fields`() {
        val restored = parseMcpServersFromJson(exportMcpServersToJson(listOf(sse())))

        assertEquals(1, restored.size)
        val server = restored.single()
        assertTrue(server is McpServerConfig.SseTransportServer)
        assertEquals("search", server.commonOptions.name)
        assertEquals("https://example.com/sse", server.serverUrl)
        assertTrue(server.commonOptions.enable)
        assertEquals(listOf("Authorization" to "Bearer token"), server.commonOptions.headers)
        assertEquals(listOf("web_search"), server.commonOptions.tools.map { it.name })
        assertFalse(server.commonOptions.tools.single().enable)
        assertTrue(server.commonOptions.tools.single().needsApproval)
    }

    @Test
    fun `export keeps streamable http transport`() {
        val server = McpServerConfig.StreamableHTTPServer(
            commonOptions = McpCommonOptions(name = "http"),
            url = "https://example.com/mcp",
        )

        val restored = parseMcpServersFromJson(exportMcpServersToJson(listOf(server))).single()

        assertTrue(restored is McpServerConfig.StreamableHTTPServer)
        assertEquals("https://example.com/mcp", restored.serverUrl)
    }

    @Test
    fun `export keeps disabled servers and duplicate names`() {
        val exported = exportMcpServersToJson(listOf(sse(name = "dup"), sse(name = "dup", enable = false)))

        val restored = parseMcpServersFromJson(exported)
        assertEquals(listOf("dup", "dup"), restored.map { it.commonOptions.name })
        assertEquals(listOf(true, false), restored.map { it.commonOptions.enable })
    }

    @Test
    fun `export merges duplicate headers and drops blank names`() {
        val server = sse().copy(
            commonOptions = sse().commonOptions.copy(
                headers = listOf("X-Tag" to "a", "X-Tag" to "b", "" to "junk"),
            )
        )

        val restored = parseMcpServersFromJson(exportMcpServersToJson(listOf(server))).single()

        assertEquals(listOf("X-Tag" to "a, b"), restored.commonOptions.headers)
    }

    @Test
    fun `export drops tools without a name`() {
        val server = sse().copy(
            commonOptions = sse().commonOptions.copy(
                tools = listOf(McpTool(name = ""), McpTool(name = "ok")),
            )
        )

        val restored = parseMcpServersFromJson(exportMcpServersToJson(listOf(server))).single()

        assertEquals(listOf("ok"), restored.commonOptions.tools.map { it.name })
    }

    @Test
    fun `export never writes oauth credentials`() {
        val oauth = McpOAuthState(
            enabled = true,
            clientSecret = "client-secret",
            accessToken = "access-token",
            refreshToken = "refresh-token",
        )
        val server = sse().copy(commonOptions = sse().commonOptions.copy(oauth = oauth))

        val exported = exportMcpServersToJson(listOf(server))

        assertFalse(exported.contains("client-secret"))
        assertFalse(exported.contains("access-token"))
        assertFalse(exported.contains("refresh-token"))
    }

    @Test
    fun `empty export imports as empty list`() {
        assertEquals(
            emptyList<McpServerConfig>(),
            parseMcpServersFromJson(exportMcpServersToJson(emptyList())),
        )
    }

    @Test
    fun `import accepts standard mcpServers object`() {
        val json = """
            {
              "mcpServers": {
                "demo": {
                  "type": "sse",
                  "url": "https://example.com/sse",
                  "headers": {"X-API-Key": "key"}
                }
              }
            }
        """.trimIndent()

        val server = parseMcpServersFromJson(json).single()

        assertTrue(server is McpServerConfig.SseTransportServer)
        assertEquals("demo", server.commonOptions.name)
        assertEquals("https://example.com/sse", server.serverUrl)
        assertEquals(listOf("X-API-Key" to "key"), server.commonOptions.headers)
        assertTrue(server.commonOptions.enable)
        assertTrue(server.commonOptions.tools.isEmpty())
    }

    @Test
    fun `import skips entries without url`() {
        val arrayJson = """
            {
              "mcpServers": [
                {"name": "no-url"},
                {"name": "ok", "url": "https://example.com/mcp"}
              ]
            }
        """.trimIndent()
        val objectJson = """{"mcpServers": {"no-url": {"type": "sse"}}}"""

        val restored = parseMcpServersFromJson(arrayJson)

        assertEquals(listOf("ok"), restored.map { it.commonOptions.name })
        assertTrue(restored.single() is McpServerConfig.StreamableHTTPServer)
        assertTrue(parseMcpServersFromJson(objectJson).isEmpty())
    }

    @Test
    fun `import returns empty when root or mcpServers is not a container`() {
        assertTrue(parseMcpServersFromJson("[]").isEmpty())
        assertTrue(parseMcpServersFromJson("""{"foo": 1}""").isEmpty())
        assertTrue(parseMcpServersFromJson("""{"mcpServers": "nope"}""").isEmpty())
    }

    @Test
    fun `import rejects malformed json`() {
        val error = runCatching { parseMcpServersFromJson("{") }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
