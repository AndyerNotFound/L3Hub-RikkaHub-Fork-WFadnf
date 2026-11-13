package me.rerere.tts.provider.providers

import org.junit.Assert.assertEquals
import org.junit.Test

class DeepSeekToolsUrlTest {
    @Test
    fun keeps_existing_v1_prefix() {
        assertEquals(
            "https://api.deepseek.com/v1/tools/tts",
            deepSeekToolsUrl("https://api.deepseek.com/v1", "tts"),
        )
    }

    @Test
    fun adds_v1_when_missing() {
        assertEquals(
            "https://api.deepseek.com/v1/tools/asr",
            deepSeekToolsUrl("https://api.deepseek.com/", "asr"),
        )
    }
}
