package me.rerere.rikkahub.ui.components.message.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolTextPresentationTest {
    @Test
    fun `json text is pretty printed`() {
        val layout = ToolTextPresentation.of("""{"path":"/tmp/a.md","limit":20}""")

        assertEquals(ToolTextFormat.Json, layout.format)
        assertEquals("json", layout.language)
        assertTrue(layout.text.contains("\"path\": \"/tmp/a.md\""))
        assertTrue(layout.text.contains("\n"))
    }

    @Test
    fun `empty json stays plain`() {
        assertEquals(ToolTextFormat.Plain, ToolTextPresentation.of("{}").format)
        assertEquals(ToolTextFormat.Plain, ToolTextPresentation.of("[]").format)
    }

    @Test
    fun `markdown file strips frontmatter for rich text only`() {
        val content = """
            ---
            name: profile
            title: Profile
            ---
            # Heading

            Body text
        """.trimIndent()

        val layout = ToolTextPresentation.forPath("/profile.md", content)

        assertEquals(ToolTextFormat.Markdown, layout.format)
        assertEquals(content, layout.text)
        assertTrue(layout.richText.startsWith("# Heading"))
        assertFalse(layout.richText.contains("name: profile"))
    }

    @Test
    fun `broken frontmatter is kept in rich text`() {
        val content = """
            ---
            name: profile: broken
            ---
            Body
        """.trimIndent()

        val layout = ToolTextPresentation.forPath("/profile.md", content)

        assertEquals(ToolTextFormat.Markdown, layout.format)
        assertEquals(content, layout.richText)
    }

    @Test
    fun `fenced code block is markdown`() {
        val layout = ToolTextPresentation.of(
            """
            ```kotlin
            val answer = 42
            ```
            """.trimIndent()
        )

        assertEquals(ToolTextFormat.Markdown, layout.format)
    }

    @Test
    fun `table is markdown`() {
        val layout = ToolTextPresentation.of(
            """
            | Name | Value |
            | --- | --- |
            | a | 1 |
            """.trimIndent()
        )

        assertEquals(ToolTextFormat.Markdown, layout.format)
    }

    @Test
    fun `single heading in shell output is not markdown`() {
        val layout = ToolTextPresentation.of("# apt update\nreading package lists\nbuilding dependency tree")

        assertEquals(ToolTextFormat.Plain, layout.format)
    }

    @Test
    fun `two loose markers are enough for markdown`() {
        val layout = ToolTextPresentation.of("**important**\n\n- first\n- second")

        assertEquals(ToolTextFormat.Markdown, layout.format)
    }

    @Test
    fun `explicit plaintext hint disables content detection`() {
        val layout = ToolTextPresentation.of("**important**\n\n- first\n- second", languageHint = "plaintext")

        assertEquals(ToolTextFormat.Code, layout.format)
        assertEquals("plaintext", layout.language)
    }

    @Test
    fun `kotlin file uses syntax highlighting`() {
        val layout = ToolTextPresentation.forPath("/tmp/Main.kt", "fun main() {}\n")

        assertEquals(ToolTextFormat.Code, layout.format)
        assertEquals("kotlin", layout.language)
        assertEquals("fun main() {}\n", layout.text)
    }

    @Test
    fun `json file is formatted`() {
        val layout = ToolTextPresentation.forPath("/tmp/data.json", """{"a":1}""")

        assertEquals(ToolTextFormat.Json, layout.format)
        assertTrue(layout.text.contains("\"a\": 1"))
    }

    @Test
    fun `plain log stays plain text`() {
        val layout = ToolTextPresentation.of("12:00:00 INFO boot\n12:00:01 WARN slow response")

        assertEquals(ToolTextFormat.Plain, layout.format)
        assertTrue(layout.text.startsWith("12:00:00"))
    }
}
