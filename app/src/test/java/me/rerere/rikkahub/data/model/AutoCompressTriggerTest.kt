package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.provider.Model
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCompressTriggerTest {
    @Test
    fun `trigger is MAX scaled by the configured ratio`() {
        val model = Model(maxContextTokens = 110_000)
        assertEquals(55_000, model.autoCompressTriggerTokens(Assistant(autoCompressRatio = 0.5f)))
        assertEquals(88_000, model.autoCompressTriggerTokens(Assistant()))
        assertEquals(104_500, model.autoCompressTriggerTokens(Assistant(autoCompressRatio = 0.95f)))
    }

    @Test
    fun `the context window does not change the saved MAX for compression`() {
        val model = Model(contextLength = 128_000, maxContextTokens = 524_288)
        val assistant = Assistant(autoCompressRatio = 0.75f)
        assertEquals(393_216, model.autoCompressTriggerTokens(assistant))
        assertEquals(393_216, model.copy(contextThresholdsAuto = false).autoCompressTriggerTokens(assistant))
    }

    @Test
    fun `a missing or invalid MAX does not fall back to the context window`() {
        val model = Model(contextLength = 128_000)
        val assistant = Assistant(autoCompress = true)
        assertNull(model.autoCompressTriggerTokens(assistant))
        assertNull(model.copy(maxContextTokens = 0).autoCompressTriggerTokens(assistant))
        assertNull(model.copy(maxContextTokens = -1).autoCompressTriggerTokens(assistant))
    }

    @Test
    fun `trigger is rounded to whole tokens`() {
        val model = Model(maxContextTokens = 9)
        assertEquals(5, model.autoCompressTriggerTokens(Assistant(autoCompressRatio = 0.5f)))
    }

    @Test
    fun `legacy line selection is ignored while the switch and ratio are kept`() {
        val json = Json { encodeDefaults = true }
        val model = Model(maxContextTokens = 100_000)
        for (line in listOf("WARN", "MAX")) {
            val assistant = json.decodeFromString<Assistant>(
                """{"autoCompress":true,"autoCompressRatio":0.75,"autoCompressThreshold":"$line"}""",
            )
            assertTrue(assistant.autoCompress)
            assertEquals(0.75f, assistant.autoCompressRatio, 0f)
            assertEquals(75_000, model.autoCompressTriggerTokens(assistant))
            val saved = json.encodeToJsonElement(Assistant.serializer(), assistant).jsonObject
            assertFalse("autoCompressThreshold" in saved)
        }
    }
}
