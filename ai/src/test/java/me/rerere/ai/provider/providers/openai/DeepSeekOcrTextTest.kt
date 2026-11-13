package me.rerere.ai.provider.providers.openai

import org.junit.Assert.assertEquals
import org.junit.Test

class DeepSeekOcrTextTest {
    @Test
    fun removesLayoutTokensAndBoxes() {
        assertEquals(
            "标题",
            cleanDeepSeekOcrText("<|ref|>标题<|/ref|><|box|>[[52, 40, 948, 96]]<|/box|>"),
        )
    }

    @Test
    fun removesFullWidthLayoutTokens() {
        assertEquals(
            "正文",
            cleanDeepSeekOcrText("<｜ref｜>正文<｜/ref｜><｜box｜>[[0, 0, 10, 10]]<｜/box｜>"),
        )
    }

    @Test
    fun keepsLinesAndCollapsesBlankRuns() {
        assertEquals(
            "line one\n\nline two",
            cleanDeepSeekOcrText("  line one\n\n\n\nline two  "),
        )
    }

    @Test
    fun plainTextIsReturnedTrimmed() {
        assertEquals("plain text", cleanDeepSeekOcrText("\n  plain text \n"))
    }
}
