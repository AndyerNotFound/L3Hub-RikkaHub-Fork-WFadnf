package me.rerere.rikkahub.data.model

import me.rerere.ai.provider.Model
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoCompressTriggerTest {
    @Test
    fun `trigger is the selected line scaled by the ratio`() {
        val model = Model(
            contextLength = 128_000,
            warnContextTokens = 100_000,
            maxContextTokens = 110_000,
            contextThresholdsAuto = false,
        )

        assertEquals(50_000, model.autoCompressTriggerTokens(Assistant(autoCompressRatio = 0.5f)))
        assertEquals(
            104_500,
            model.autoCompressTriggerTokens(
                Assistant(
                    autoCompressRatio = 0.95f,
                    autoCompressThreshold = AutoCompressThreshold.MAX,
                )
            ),
        )
    }

    @Test
    fun `hand written thresholds are not narrowed by the window`() {
        val model = Model(
            contextLength = 256_000,
            warnContextTokens = 200_000,
            maxContextTokens = 220_000,
            contextThresholdsAuto = false,
        )

        assertEquals(200_000, model.autoCompressLimit(AutoCompressThreshold.WARN))
        // 触发点按预警线算：200000 × 0.95，而不是按窗口 256000 × 0.95 = 243200
        assertEquals(190_000, model.autoCompressTriggerTokens(Assistant(autoCompressRatio = 0.95f)))
    }

    @Test
    fun `thresholds beyond the window never fire below the line`() {
        // 门限比窗口还大时，旧行为会退到 窗口 × 比例 提前触发；现在只认预警线
        val model = Model(
            contextLength = 262_144,
            warnContextTokens = 352_000,
            maxContextTokens = 384_000,
            contextThresholdsAuto = false,
        )

        assertEquals(345_600, model.autoCompressTriggerTokens(Assistant(autoCompressRatio = 0.9f)))
    }

    @Test
    fun `auto filled thresholds are narrowed to the window`() {
        val model = Model(
            contextLength = 128_000,
            warnContextTokens = 393_216,
            maxContextTokens = 524_288,
            contextThresholdsAuto = true,
        )

        assertEquals(96_000, model.autoCompressLimit(AutoCompressThreshold.WARN))
        assertEquals(115_200, model.autoCompressLimit(AutoCompressThreshold.MAX))
    }

    @Test
    fun `window without thresholds scales the fallback line`() {
        val model = Model(contextLength = 128_000)

        // 兜底预警线 = 窗口 × 75%，比例再作用在上面：96000 × 0.8
        assertEquals(76_800, model.autoCompressTriggerTokens(Assistant(autoCompressRatio = 0.8f)))
    }

    @Test
    fun `no window and no thresholds means no trigger`() {
        val model = Model(contextLength = 0)

        assertNull(model.autoCompressTriggerTokens(Assistant()))
    }
}
