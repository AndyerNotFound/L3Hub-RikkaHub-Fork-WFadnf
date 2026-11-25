package me.rerere.rikkahub.ui.components.richtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeFenceLanguageTest {
    @Test
    fun `normalizes common language spellings`() {
        assertEquals("json", normalizeCodeFenceLanguage("application/json"))
        assertEquals("json", normalizeCodeFenceLanguage("{.json}"))
        assertEquals("kotlin", normalizeCodeFenceLanguage("language-Kotlin"))
        assertEquals("kotlin", normalizeCodeFenceLanguage("kt"))
        assertEquals("python", normalizeCodeFenceLanguage("python title=main.py"))
        assertEquals("svg", normalizeCodeFenceLanguage("image/svg+xml"))
        assertEquals("geojson", normalizeCodeFenceLanguage("application/geo+json"))
        assertEquals("", normalizeCodeFenceLanguage("   "))
    }

    @Test
    fun `declared diff aliases are recognized`() {
        assertTrue(isDiffFenceLanguage("diff"))
        assertTrue(isDiffFenceLanguage("patch"))
        assertTrue(isDiffFenceLanguage("Git-Diff"))
        assertFalse(isDiffFenceLanguage("kotlin"))
    }

    @Test
    fun `standalone patch is detected but prose is not`() {
        val patch = """
            --- a/Main.kt
            +++ b/Main.kt
            @@ -1,2 +1,2 @@
            -val a = 1
            +val a = 2
        """.trimIndent()
        assertTrue(looksLikeUnifiedDiff(patch))
        assertTrue(looksLikeStandaloneUnifiedDiff(patch))

        val prose = """
            下面是修改说明：
            - 第一点
            + 第二点
        """.trimIndent()
        assertFalse(looksLikeStandaloneUnifiedDiff(prose))
        assertFalse(shouldRenderAsDiff("", prose))
    }

    @Test
    fun `declared diff renders even without headers`() {
        assertTrue(shouldRenderAsDiff("diff", "-old\n+new"))
        assertFalse(shouldRenderAsDiff("kotlin", "-old\n+new"))
    }

    @Test
    fun `oversized source never switches renderer`() {
        val huge = "+line\n".repeat(MAX_DIFF_SOURCE_CHARS)
        assertFalse(shouldRenderAsDiff("diff", huge))
    }

    @Test
    fun `line stats ignore file headers`() {
        val patch = "--- a\n+++ b\n@@ -1 +1 @@\n-a\n+b\n+b2"
        assertEquals(2 to 1, diffLineStats(patch))
    }
}
