package me.rerere.rikkahub.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextOverflowMessageTest {
    @Test
    fun `detects provider and gateway overflow messages`() {
        assertTrue(isContextOverflowMessage("This model's maximum context length is 128000 tokens"))
        assertTrue(isContextOverflowMessage("{\"error\":{\"code\":\"context_length_exceeded\"}}"))
        assertTrue(isContextOverflowMessage("Request too large for model"))
        assertTrue(isContextOverflowMessage("上下文长度超出限制"))
    }

    @Test
    fun `does not treat unrelated errors as overflow`() {
        assertFalse(isContextOverflowMessage("429 rate limit exceeded"))
        assertFalse(isContextOverflowMessage("Connection timed out"))
    }
}
