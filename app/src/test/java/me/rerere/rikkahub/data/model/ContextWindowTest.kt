package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextWindowTest {
    @Test
    fun `fraction is clamped and handles missing limits`() {
        assertEquals(0f, contextUsageFraction(used = 5_000, limit = null), 0f)
        assertEquals(0f, contextUsageFraction(used = 5_000, limit = 0), 0f)
        assertEquals(0.25f, contextUsageFraction(used = 50_000, limit = 200_000), 0.0001f)
        assertEquals(1f, contextUsageFraction(used = 300_000, limit = 200_000), 0f)
    }

    @Test
    fun `limit is only enforced when max is set`() {
        assertFalse(isContextLimitReached(tokens = 200_000, maxTokens = null))
        assertFalse(isContextLimitReached(tokens = 199_999, maxTokens = 200_000))
        assertTrue(isContextLimitReached(tokens = 200_000, maxTokens = 200_000))
        assertTrue(isContextLimitReached(tokens = 250_000, maxTokens = 200_000))
    }

    @Test
    fun `unknown usage never blocks sending`() {
        assertFalse(isContextLimitReached(tokens = null, maxTokens = 200_000))
    }
}
