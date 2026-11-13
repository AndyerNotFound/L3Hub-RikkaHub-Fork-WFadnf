package me.rerere.ai.provider

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurlCommandTest {
    @Test
    fun `parses a post command with headers and json body`() {
        val command = CurlCommand.parse(
            """
            curl 'https://api.example.com/v1/chat/completions' \
              -X POST \
              -H 'Authorization: Bearer sk-test' \
              -H "Content-Type: application/json" \
              -d '{"model":"gpt-test","stream":true}'
            """.trimIndent()
        )

        assertEquals("https://api.example.com/v1/chat/completions", command.url)
        assertEquals("POST", command.method)
        assertEquals(
            listOf("Authorization: Bearer sk-test", "Content-Type: application/json"),
            command.headers.map { "${it.name}: ${it.value}" },
        )
        assertEquals("""{"model":"gpt-test","stream":true}""", command.body)
    }

    @Test
    fun `get is the default and basic auth becomes a header`() {
        val command = CurlCommand.parse("curl -u user:pass https://api.example.com/models")

        assertEquals("GET", command.method)
        assertEquals("https://api.example.com/models", command.url)
        assertTrue(command.headers.single().value.startsWith("Basic "))
        assertNull(command.body)
    }

    @Test
    fun `alternative data and cookie flags are supported`() {
        val command = CurlCommand.parse(
            "curl --url https://example.com --data-raw a=1 -b session=abc --json '{}'"
        )

        assertEquals("POST", command.method)
        assertEquals("https://example.com", command.url)
        assertEquals("a=1&{}", command.body)
        assertEquals("session=abc", command.headers.first { it.name == "Cookie" }.value)
    }

    @Test
    fun `template variables are substituted before parsing`() {
        val template = """
            curl '{{baseUrl}}/chat' \
              -H 'Authorization: Bearer {{apiKey}}' \
              -d '{"model":"{{model}}","stream":{{stream}},"messages":{{messages}}}'
        """.trimIndent()
        val variables = mapOf(
            "baseUrl" to "https://proxy.example.com/v1",
            "apiKey" to "sk-live",
            "model" to "m1",
            "stream" to "true",
            "messages" to """[{"role":"user","content":"hi"}]""",
        )
        val rendered = renderCurlTemplate(template) { variables[it] }
        val command = CurlCommand.parse(rendered)

        assertEquals("https://proxy.example.com/v1/chat", command.url)
        assertEquals("Bearer sk-live", command.headers.single().value)
        assertEquals(
            """{"model":"m1","stream":true,"messages":[{"role":"user","content":"hi"}]}""",
            command.body,
        )
    }

    @Test
    fun `chrome ansi-c quoted body and cmd line continuation are supported`() {
        val bash = CurlCommand.parse(
            "curl 'https://example.com' -d $'{\\n  \\\"model\\\": \\\"m1\\\"\\n}'"
        )
        assertEquals("POST", bash.method)
        assertEquals("{\n  \"model\": \"m1\"\n}", bash.body)

        val cmd = CurlCommand.parse("curl 'https://example.com' ^\n  -X POST ^\n  -d '{}'")
        assertEquals("POST", cmd.method)
        assertEquals("{}", cmd.body)
    }

    @Test
    fun `unknown variables are rejected`() {
        val error = runCatching {
            renderCurlTemplate("curl {{missing}}") { null }
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error!!.message!!.contains("missing"))
    }

    @Test
    fun `validate reports malformed commands and accepts good ones`() {
        assertNull(RequestOverride(enabled = true, curl = "curl https://example.com").validate())
        assertNotNull(RequestOverride(enabled = true, curl = "curl ftp://example.com").validate())
        assertNotNull(RequestOverride(enabled = true, curl = "curl 'https://example.com").validate())
        assertNull(RequestOverride(enabled = false, curl = "not a curl").validate())
    }

    @Test
    fun `apply override replaces url method headers and body`() {
        val original = Request.Builder()
            .url("https://api.example.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer sk-original")
            .addHeader("X-Keep", "yes")
            .get()
            .build()
        val body = buildJsonObject {
            put("model", "m1")
            put("messages", kotlinx.serialization.json.JsonArray(emptyList()))
        }
        val guard = ProviderGuard(
            requestOverride = RequestOverride(
                enabled = true,
                curl = """
                    curl '{{baseUrl}}/custom/chat' \
                      -X POST \
                      -H 'Authorization: Bearer {{apiKey}}' \
                      -H 'X-Extra: 1' \
                      -d '{{body}}'
                """.trimIndent(),
            )
        )

        val request = original.applyRequestOverride(
            guard = guard,
            apiKey = "sk-new",
            baseUrl = "https://proxy.example.com/v1",
            model = "m1",
            body = body,
            stream = true,
        )

        assertEquals("https://proxy.example.com/v1/custom/chat", request.url.toString())
        assertEquals("POST", request.method)
        assertEquals("Bearer sk-new", request.header("Authorization"))
        assertEquals("yes", request.header("X-Keep"))
        assertEquals("1", request.header("X-Extra"))
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        assertEquals(body.toString(), buffer.readUtf8())
    }

    @Test
    fun `variables expose body messages and tools`() {
        val body: JsonObject = buildJsonObject {
            put("model", "m1")
            put("stream", true)
            put("messages", kotlinx.serialization.json.JsonArray(emptyList()))
            put("tools", kotlinx.serialization.json.JsonArray(emptyList()))
        }

        val variables = requestOverrideVariables(
            apiKey = "sk",
            baseUrl = "https://example.com/v1",
            model = "m1",
            body = body,
            stream = true,
        )

        assertEquals("m1", variables["model"])
        assertEquals("true", variables["stream"])
        assertEquals("[]", variables["messages"])
        assertEquals("[]", variables["tools"])
        assertEquals(body.toString(), variables["body"])
        assertTrue(variables["body"]!!.contains(JsonPrimitive("m1").toString()))
    }
}
