package me.rerere.rikkahub.data.model

import me.rerere.ai.provider.Model
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoCompressTriggerTest {
    @Test
    fun `ratio fires first when it is below the warning line`() {
        val model = Model(
            contextLength = 128_000,
            warnContextTokens = 100_000,
            maxContextTokens = 110_000,
            contextThresholdsAuto = false,
        )
        val assistant = Assistant(autoCompressRatio = 0.5f)

        assertEquals(64_000, model.autoCompressTriggerTokens(assistant))
    }

    @Test
    fun `warning line fires first when the ratio is above it`() {
        val model = Model(
            contextLength = 128_000,
            warnContextTokens = 100_000,
            maxContextTokens = 110_000,
            contextThresholdsAuto = false,
        )

        assertEquals(100_000, model.autoCompressTriggerTokens(Assistant(autoCompressRatio = 0.95f)))
        assertEquals(
            110_000,
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
        // 按窗口 75% 收窄会变成 192000，这里必须仍是手填的 200000 先到
        assertEquals(200_000, model.autoCompressTriggerTokens(Assistant(autoCompressRatio = 0.95f)))
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
    fun `window without thresholds falls back to the preset percentage`() {
        val model = Model(contextLength = 128_000)

        assertEquals(96_000, model.autoCompressTriggerTokens(Assistant(autoCompressRatio = 0.8f)))
    }

    @Test
    fun `no window and no thresholds means no trigger`() {
        val model = Model(contextLength = 0)

        assertNull(model.autoCompressTriggerTokens(Assistant()))
    }
}
