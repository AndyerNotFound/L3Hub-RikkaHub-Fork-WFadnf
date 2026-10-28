package me.rerere.rikkahub.data.model

import me.rerere.ai.provider.Model
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextThresholdPresetsTest {
    private fun preset(id: String) = ContextThresholdPresets.resolve(id)

    @Test
    fun `matches the documented presets`() {
        assertEquals(262_144 to 327_680, preset("kimi-k3").pair())
        assertEquals(172_032 to 221_184, preset("deepseek-v4.1").pair())
        assertEquals(196_608 to 262_144, preset("deepseek-v4").pair())
        assertEquals(131_072 to 131_072, preset("deepseek-v3.2").pair())
        assertEquals(147_456 to 221_184, preset("glm-5-flash").pair())
        assertEquals(196_608 to 262_144, preset("qwen3-235b-a22b").pair())
        assertEquals(196_608 to 262_144, preset("gpt-5.2").pair())
        assertEquals(147_456 to 221_184, preset("claude-sonnet-4-5").pair())
        assertEquals(221_184 to 327_680, preset("claude-opus-5").pair())
        assertEquals(147_456 to 221_184, preset("step-3").pair())
        assertEquals(196_608 to 327_680, preset("stepfun-5").pair())
        assertEquals(131_072 to 196_608, preset("nemotron-4-340b").pair())
        assertEquals(147_456 to 221_184, preset("muse-alpha").pair())
        assertEquals(65_536 to 131_072, preset("gpt-oss-120b").pair())
    }

    @Test
    fun `falls back for unknown or older models`() {
        assertEquals(ContextThresholdPresets.fallback.pair(), preset("llama-3-70b").pair())
        assertEquals(ContextThresholdPresets.fallback.pair(), preset("deepseek-chat").pair())
        // 3.5 是 Claude 3 代，不能按 5 处理
        assertEquals(ContextThresholdPresets.fallback.pair(), preset("claude-3-5-sonnet").pair())
        assertEquals(ContextThresholdPresets.fallback.pair(), preset("qwen2.5-72b").pair())
        // -32b 是参数量，不是 Qwen 3
        assertEquals(ContextThresholdPresets.fallback.pair(), preset("deepseek-r1-distill-qwen-32b").pair())
        assertEquals(ContextThresholdPresets.fallback.pair(), preset("glm-4.5").pair())
    }

    @Test
    fun `auto mode fills only untouched models`() {
        val untouched = Model(modelId = "claude-opus-5")
        val filled = untouched.withContextPresets()
        assertEquals(221_184, filled.warnContextTokens)
        assertEquals(327_680, filled.maxContextTokens)

        val manual = Model(modelId = "claude-opus-5", warnContextTokens = 1, maxContextTokens = 2)
        assertEquals(manual, manual.withContextPresets())

        val optedOut = Model(modelId = "claude-opus-5", contextThresholdsAuto = false)
        assertEquals(null, optedOut.withContextPresets().warnContextTokens)
    }

    private fun ContextThresholdPresets.Preset.pair() = warn to max
}
