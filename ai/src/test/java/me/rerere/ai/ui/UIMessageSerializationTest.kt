package me.rerere.ai.ui

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class UIMessageSerializationTest {

    @Test
    fun `synthetic marker is not serialized`() {
        val message = UIMessage.user("internal").copy(isSynthetic = true)

        val encoded = Json.encodeToString(message)
        val decoded = Json.decodeFromString<UIMessage>(encoded)

        assertTrue(message.isSynthetic)
        assertFalse(encoded.contains("isSynthetic"))
        assertFalse(decoded.isSynthetic)
    }

    @Test
    fun `sub agent notice marker survives serialization and defaults to false`() {
        val notice = UIMessage.user("result").copy(isSubAgentNotice = true)
        assertTrue(Json.decodeFromString<UIMessage>(Json.encodeToString(notice)).isSubAgentNotice)

        val plain = Json.decodeFromString<UIMessage>(Json.encodeToString(UIMessage.user("hi")))
        assertFalse(plain.isSubAgentNotice)
    }

    @Test
    fun `context checkpoint marker survives serialization`() {
        val message = UIMessage.user("summary").copy(isContextCheckpoint = true)

        val decoded = Json.decodeFromString<UIMessage>(Json.encodeToString(message))

        assertTrue(decoded.isContextCheckpoint)
    }

    @Test
    fun `context split metadata survives serialization`() {
        val messageId = Uuid.random()
        val message = UIMessage.user("summary").copy(
            isContextCheckpoint = true,
            contextStartMessageId = messageId,
            contextStartPartIndex = 2,
        )

        val decoded = Json.decodeFromString<UIMessage>(Json.encodeToString(message))

        assertEquals(messageId, decoded.contextStartMessageId)
        assertEquals(2, decoded.contextStartPartIndex)
    }

    @Test
    fun `messages without context split metadata decode with no split`() {
        val legacy = """{"id":"00000000-0000-0000-0000-000000000001","role":"user","parts":[]}"""

        val decoded = Json.decodeFromString<UIMessage>(legacy)

        assertNull(decoded.contextStartMessageId)
        assertEquals(0, decoded.contextStartPartIndex)
    }

    @Test
    fun `messages stored before checkpoints existed decode as ordinary messages`() {
        val legacy = """{"id":"00000000-0000-0000-0000-000000000001","role":"user","parts":[]}"""

        assertFalse(Json.decodeFromString<UIMessage>(legacy).isContextCheckpoint)
    }

    @Test
    fun `model snapshot survives serialization`() {
        val modelId = Uuid.random()
        val message = UIMessage.assistant("hi").copy(
            modelId = modelId,
            modelSnapshot = ModelSnapshot(modelId = "gpt-test", displayName = "GPT Test"),
        )

        val shown = Json.decodeFromString<UIMessage>(Json.encodeToString(message)).snapshotModel()

        assertEquals(modelId, shown?.id)
        assertEquals("gpt-test", shown?.modelId)
        assertEquals("GPT Test", shown?.displayName)
    }

    @Test
    fun `messages stored before model snapshots existed decode without one`() {
        val legacy = """{"role":"assistant","parts":[],"modelId":"00000000-0000-0000-0000-000000000001"}"""

        assertNull(Json.decodeFromString<UIMessage>(legacy).snapshotModel())
    }
}
