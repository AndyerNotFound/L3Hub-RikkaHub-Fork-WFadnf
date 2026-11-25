package me.rerere.rikkahub.ui.components.richtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePreviewTest {
    @Test
    fun `raw html is always escaped`() {
        val html = renderOfflineMarkdown("<script>alert(1)</script>")
        assertTrue(html.contains("&lt;script&gt;"))
        assertFalse(html.contains("<script>"))
    }

    @Test
    fun `image with event handler cannot become markup`() {
        val html = renderOfflineMarkdown("<img src=x onerror=alert(1)>")
        assertFalse(html.contains("<img"))
        assertTrue(html.contains("&lt;img"))
    }

    @Test
    fun `script like url never produces a link`() {
        val html = renderOfflineMarkdown("[x](javascript:alert(1))")
        assertFalse(html.contains("href"))
        // 内容原样可见，不会被悄悄删掉
        assertTrue(html.contains("javascript:alert(1)"))
    }

    @Test
    fun `web and email addresses are copyable text not navigation`() {
        val html = renderOfflineMarkdown("[Web](https://example.com/?a=1&b=2) [Mail](mailto:a@b.c)")
        assertEquals(
            "<p><span class=\"link\">Web</span> (https://example.com/?a=1&amp;b=2) " +
                "<span class=\"link\">Mail</span> (mailto:a@b.c)</p>",
            html,
        )
        assertFalse(html.contains("href="))
    }

    @Test
    fun `unsupported and malformed addresses keep their source syntax`() {
        listOf(
            "data:text/html;base64,PHN2Zz4=", "file:///example", "content://media/1", "//example.com", "",
            "https://a\\b", "https://a\nb", "https://a\u0000b", "https://example.com/" + "x".repeat(2048),
        ).forEach { address ->
            val source = "[label]($address)"
            assertEquals("<p>$source</p>", renderOfflineMarkdown(source))
        }
    }

    @Test
    fun `nested parentheses in url are handled`() {
        val html = renderOfflineMarkdown("[w](https://en.wikipedia.org/wiki/A_(b))")
        assertEquals("<p><span class=\"link\">w</span> (https://en.wikipedia.org/wiki/A_(b))</p>", html)
    }

    @Test
    fun `valid link survives a stray opening bracket on a long line`() {
        val prefix = "[".repeat(25_000) + " literal "
        assertEquals(
            "<p>$prefix<span class=\"link\">label</span> (https://example.com/) &lt;end&gt;</p>",
            renderOfflineMarkdown(prefix + "[label](https://example.com/) <end>"),
        )
    }

    @Test
    fun `headings and emphasis render`() {
        assertEquals("<h1>T</h1>", renderOfflineMarkdown("# T"))
        assertEquals("<h6>T</h6>", renderOfflineMarkdown("###### T"))
        assertEquals("<p><strong>b</strong></p>", renderOfflineMarkdown("**b**"))
        assertEquals("<p><em>i</em></p>", renderOfflineMarkdown("*i*"))
    }

    @Test
    fun `inline code escapes its content`() {
        assertEquals("<p><code>x&lt;y</code></p>", renderOfflineMarkdown("`x<y`"))
    }

    @Test
    fun `fenced code is escaped and labelled`() {
        val html = renderOfflineMarkdown("```Kotlin\nval x = \"<b>\"\n```")
        assertTrue(html.contains("<span class=\"lang\">kotlin</span>"))
        assertTrue(html.contains("&lt;b&gt;"))
        assertFalse(html.contains("<b>"))
    }

    @Test
    fun `lists and rules render`() {
        assertEquals("<ul><li>a</li><li>b</li></ul>", renderOfflineMarkdown("- a\n- b"))
        assertEquals("<ol><li>a</li><li>b</li></ol>", renderOfflineMarkdown("1. a\n2. b"))
        assertEquals("<hr>", renderOfflineMarkdown("---"))
    }

    @Test
    fun `table rows render as cells`() {
        val html = renderOfflineMarkdown("| A | B |\n| --- | --- |\n| 1 | 2 |")
        assertTrue(html.contains("<th>A</th>"))
        assertTrue(html.contains("<td>1</td>"))
        assertTrue(html.contains("<td>2</td>"))
    }

    @Test
    fun `wide tables fall back to escaped text and trailing rows stay visible`() {
        val header = List(65) { "header" }.joinToString("|", "|", "|")
        val divider = List(65) { "---" }.joinToString("|", "|", "|")
        val wide = renderOfflineMarkdown("$header\n$divider\n|<tag>|")
        assertFalse(wide.contains("<table>"))
        assertTrue(wide.contains("|&lt;tag&gt;|"))

        val rows = (1..130).joinToString("\n") { "| row $it |" }
        val tall = renderOfflineMarkdown("| Header |\n| --- |\n$rows")
        assertTrue(tall.contains("<td>row 128</td>"))
        assertFalse(tall.contains("<td>row 129</td>"))
        assertTrue(tall.contains("| row 130 |"))
    }

    @Test
    fun `deep quotes stop nesting without dropping content`() {
        val html = renderOfflineMarkdown(">".repeat(64) + " <end>")
        assertEquals(8, Regex("<blockquote>").findAll(html).count())
        assertEquals(8, Regex("</blockquote>").findAll(html).count())
        assertTrue(html.contains("&gt; &lt;end&gt;"))
    }

    @Test
    fun `shared structure budget falls back to escaped text`() {
        val html = renderOfflineMarkdown("- item\n".repeat(9000) + "<end>")
        assertTrue(html.contains("<p class=\"verbatim\">"))
        assertTrue(html.contains("&lt;end&gt;"))
        assertFalse(html.contains("<end>"))
    }

    @Test
    fun `prose with a pipe is not a table`() {
        assertEquals("<p>a | b</p>", renderOfflineMarkdown("a | b"))
    }

    @Test
    fun `page has no script and no remote resources`() {
        val page = buildOfflinePreviewHtml("# hi", dark = false)
        assertTrue(page.contains("script-src 'none'"))
        assertTrue(page.contains("connect-src 'none'"))
        assertTrue(page.contains("img-src 'none'"))
        assertFalse(page.contains("<script"))
        assertFalse(page.contains("http://"))
        assertFalse(page.contains("https://"))
    }

    @Test
    fun `oversized message truncates without cutting a unicode pair`() {
        val text = "a".repeat(MAX_OFFLINE_PREVIEW_CHARS - 1) + "\uD83D\uDE42trailing"
        val page = buildOfflinePreviewHtml(text, dark = true)
        assertTrue(page.contains("预览已截断"))
        assertFalse(page.contains('\uD83D'))
        assertFalse(page.contains("trailing"))
    }

    @Test
    fun `oversized code block truncates without swallowing later prose`() {
        val code = "x".repeat(19_999) + "\uD83D\uDE42code tail"
        val page = buildOfflinePreviewHtml("```text\n$code\n```\n# After", dark = false)
        assertTrue(page.contains("预览已截断"))
        assertTrue(page.contains("</code></pre><h1>After</h1>"))
        assertFalse(page.contains('\uD83D'))
        assertFalse(page.contains("code tail"))
    }

    @Test
    fun `dark theme uses its own palette`() {
        val dark = buildOfflinePreviewHtml("x", dark = true)
        val light = buildOfflinePreviewHtml("x", dark = false)
        assertTrue(dark != light)
        assertTrue(dark.contains("#121316"))
        assertTrue(light.contains("#ffffff"))
    }
}
