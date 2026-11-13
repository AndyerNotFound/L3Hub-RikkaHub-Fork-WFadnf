package me.rerere.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ASRProviderSettingDeepSeekTest {
    @Test
    fun deepseek_defaults_are_expected() {
        val setting = ASRProviderSetting.DeepSeek()

        assertEquals("DeepSeek ASR", setting.name)
        assertEquals("https://api.deepseek.com/v1", setting.baseUrl)
        assertEquals(16000, setting.sampleRate)
        assertEquals(30, setting.segmentDurationSec)
        assertEquals("", setting.apiKey)
    }

    @Test
    fun deepseek_is_registered_in_provider_types() {
        assertTrue(ASRProviderSetting.Types.contains(ASRProviderSetting.DeepSeek::class))
    }

    @Test
    fun deepseek_copy_provider_preserves_extra_fields() {
        val original = ASRProviderSetting.DeepSeek(
            apiKey = "sk-test",
            baseUrl = "https://example.com/v1",
            sampleRate = 24000,
            segmentDurationSec = 60,
        )
        val copied = original.copyProvider(id = original.id, name = "renamed")

        assertTrue(copied is ASRProviderSetting.DeepSeek)
        val deepseek = copied as ASRProviderSetting.DeepSeek
        assertEquals("renamed", deepseek.name)
        assertEquals("sk-test", deepseek.apiKey)
        assertEquals("https://example.com/v1", deepseek.baseUrl)
        assertEquals(24000, deepseek.sampleRate)
        assertEquals(60, deepseek.segmentDurationSec)
    }
}
