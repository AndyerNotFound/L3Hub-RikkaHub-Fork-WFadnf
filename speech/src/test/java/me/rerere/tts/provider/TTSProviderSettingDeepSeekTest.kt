package me.rerere.tts.provider

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TTSProviderSettingDeepSeekTest {
    @Test
    fun deepseek_defaults_are_expected() {
        val setting = TTSProviderSetting.DeepSeek()

        assertEquals("DeepSeek TTS", setting.name)
        assertEquals("https://api.deepseek.com/v1", setting.baseUrl)
        assertEquals("stella", setting.voiceId)
        assertEquals("wav", setting.format)
        assertTrue(setting.filterMarkdown)
        assertEquals("", setting.apiKey)
    }

    @Test
    fun deepseek_is_registered_and_round_trips() {
        assertTrue(TTSProviderSetting.Types.contains(TTSProviderSetting.DeepSeek::class))

        val original: TTSProviderSetting = TTSProviderSetting.DeepSeek(apiKey = "sk-test", voiceId = "tide")
        val json = Json.encodeToString(TTSProviderSetting.serializer(), original)
        assertTrue(json.contains("\"type\":\"deepseek\""))
        assertEquals(original, Json.decodeFromString(TTSProviderSetting.serializer(), json))
    }

    @Test
    fun deepseek_copy_provider_preserves_extra_fields() {
        val original = TTSProviderSetting.DeepSeek(
            apiKey = "sk-test",
            baseUrl = "https://example.com/v1",
            voiceId = "tide",
            format = "pcm",
            filterMarkdown = false,
        )
        val copied = original.copyProvider(id = original.id, name = "renamed")

        assertTrue(copied is TTSProviderSetting.DeepSeek)
        val deepseek = copied as TTSProviderSetting.DeepSeek
        assertEquals("renamed", deepseek.name)
        assertEquals("sk-test", deepseek.apiKey)
        assertEquals("tide", deepseek.voiceId)
        assertEquals("pcm", deepseek.format)
        assertEquals(false, deepseek.filterMarkdown)
    }
}
