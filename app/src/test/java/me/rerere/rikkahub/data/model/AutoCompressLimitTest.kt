package me.rerere.rikkahub.data.model

import me.rerere.ai.provider.Model
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoCompressLimitTest {
    @Test
    fun `warn falls back to max then legacy context length`() {
        assertEquals(
            64_000,
            Model(warnContextTokens = null, maxContextTokens = 64_000)
                .autoCompressLimit(AutoCompressThreshold.WARN),
        )
        assertEquals(
            32_000,
            Model(warnContextTokens = null, maxContextTokens = null, contextLength = 32_000)
                .autoCompressLimit(AutoCompressThreshold.WARN),
        )
    }

    @Test
    fun `max keeps its own threshold`() {
        assertEquals(
            128_000,
            Model(warnContextTokens = 96_000, maxContextTokens = 128_000)
                .autoCompressLimit(AutoCompressThreshold.MAX),
        )
    }
}
