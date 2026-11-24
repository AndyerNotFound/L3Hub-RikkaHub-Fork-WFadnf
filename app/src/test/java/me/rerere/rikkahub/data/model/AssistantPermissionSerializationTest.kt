package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AssistantPermissionSerializationTest {
    @Test
    fun `legacy plan mode takes precedence over the previous permission`() {
        listOf("READ_ONLY", "READ_WRITE", "APPROVAL", "FULL").forEach { previous ->
            val assistant = Json.decodeFromString<Assistant>(
                """{"planMode":true,"executionPermission":"$previous"}""",
            )
            assertEquals(previous, ExecutionPermission.READ_ONLY, assistant.executionPermission)
        }
        val assistant = Json.decodeFromString<Assistant>("""{"planMode":true}""")
        assertEquals(ExecutionPermission.READ_ONLY, assistant.executionPermission)
    }

    @Test
    fun `disabled legacy plan mode preserves the existing permission`() {
        val assistant = Json.decodeFromString<Assistant>("""{"planMode":false,"executionPermission":"FULL"}""")
        assertEquals(ExecutionPermission.FULL, assistant.executionPermission)
    }

    @Test
    fun `invalid legacy plan mode cannot restore full access`() {
        val assistant = Json.decodeFromString<Assistant>("""{"planMode":null,"executionPermission":"FULL"}""")
        assertEquals(ExecutionPermission.READ_ONLY, assistant.executionPermission)
    }

    @Test
    fun `missing permission and legacy read write use the default approval mode`() {
        val legacy = Json.decodeFromString<Assistant>("""{"executionPermission":"READ_WRITE"}""")
        assertEquals(ExecutionPermission.APPROVAL, legacy.executionPermission)
        assertEquals(ExecutionPermission.APPROVAL, Json.decodeFromString<Assistant>("{}").executionPermission)
    }

    @Test
    fun `new assistant data only writes the three supported permission names`() {
        val json = Json { encodeDefaults = true }
        assertEquals(listOf("READ_ONLY", "APPROVAL", "FULL"), ExecutionPermission.entries.map { it.name })
        for (permission in ExecutionPermission.entries) {
            val assistant = Assistant(executionPermission = permission)
            val encoded = json.encodeToJsonElement(Assistant.serializer(), assistant).jsonObject
            assertFalse("planMode" in encoded)
            assertEquals(JsonPrimitive(permission.name), encoded["executionPermission"])
            assertEquals(assistant, json.decodeFromJsonElement(Assistant.serializer(), encoded))
        }
    }
}
