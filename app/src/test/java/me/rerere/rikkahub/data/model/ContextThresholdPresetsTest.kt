package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.jsonObject
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextThresholdPresetsTest {
    @Test
    fun `fills the same default MAX for large or unknown windows`() {
        for (window in listOf(0, 1_000_000, Int.MAX_VALUE)) {
            val model = Model(modelId = "unknown-test-model", contextLength = window)
            assertEquals(524_288, model.withContextMaxPreset().maxContextTokens)
        }
    }

    @Test
    fun `new presets leave headroom inside a small model window`() {
        val model = Model(contextLength = 128_000).withContextMaxPreset()
        assertEquals(115_200, model.maxContextTokens)
        assertEquals(1, Model(contextLength = 1).withContextMaxPreset().maxContextTokens)
    }

    @Test
    fun `a saved MAX is not rewritten even when automatic presets are enabled`() {
        val model = Model(contextLength = 128_000, maxContextTokens = 101_234)
        assertEquals(model, model.withContextMaxPreset())
    }

    @Test
    fun `clearing MAX keeps it unset until the preset is explicitly restored`() {
        val model = Model(contextLength = 128_000, contextThresholdsAuto = false, enforceMaxContext = false)
        assertNull(model.withContextMaxPreset().maxContextTokens)

        val restored = model.applyContextMaxPreset()
        assertEquals(115_200, restored.maxContextTokens)
        assertTrue(restored.contextThresholdsAuto)
        assertFalse(restored.enforceMaxContext)
    }

    @Test
    fun `legacy model settings keep the original MAX and no longer write WARN`() {
        val model = JsonInstant.decodeFromString<Model>(
            """{"contextLength":128000,"warnContextTokens":393216,"maxContextTokens":524288}""",
        ).withContextMaxPreset()
        assertEquals(524_288, model.maxContextTokens)
        val saved = JsonInstant.encodeToJsonElement(Model.serializer(), model).jsonObject
        assertFalse("warnContextTokens" in saved)
    }
}
