package me.rerere.rikkahub.ui.components.richtext

import me.rerere.bettertext.markdown.MarkdownBlock
import me.rerere.bettertext.markdown.MarkdownInline
import me.rerere.bettertext.markdown.MarkdownStreamParser
import me.rerere.bettertext.markdown.parseMarkdown
import me.rerere.bettertext.markdown.plainText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexNormalizationTest {
    @Test
    fun `strips delimiters and normalizes unicode operators`() {
        assertEquals("a\\le b\\times c", latexRenderCandidates("\$a≤b×c\$").first())
    }

    @Test
    fun `offers compatible alternatives for common macros`() {
        val candidates = latexRenderCandidates("\\(\\dfrac{a}{b} + \\operatorname{sin}(x)\\)")

        assertEquals("\\dfrac{a}{b} + \\operatorname{sin}(x)", candidates.first())
        assertTrue("\\frac{a}{b} + \\mathrm{sin}(x)" in candidates)
    }

    @Test
    fun `removes unsupported equation metadata from environment fallback`() {
        val candidates = latexRenderCandidates(
            "\\begin{align}x &= 1 \\label{eq:x} \\\\ y &= 2 \\tag{A}\\end{align}"
        )

        assertEquals("x = 1   \\quad  y = 2", candidates.last())
    }

    @Test
    fun `normalizes multiline delimiters and display environments outside code`() {
        val markdown = """
            Before \(a +
            b\) after.

            \begin{align}
            x &= 1 \\
            y &= 2
            \end{align}

            ```kotlin
            val literal = "\(not math\)"
            ```
        """.trimIndent()
        val normalized = normalizeMarkdownLatex(markdown)

        assertTrue(normalized.contains("Before ${'$'}a + b${'$'} after."))
        assertTrue(normalized.contains("$$\\begin{align}"))
        assertTrue(normalized.contains("val literal = \"\\(not math\\)\""))
    }

    @Test
    fun `wraps a standalone latex macro line but leaves prose alone`() {
        assertEquals("$$\\frac{a}{b}$$", normalizeMarkdownLatex("\\frac{a}{b}"))
        assertEquals("Use \\frac in documentation", normalizeMarkdownLatex("Use \\frac in documentation"))
    }

    @Test
    fun `longer closing fence frees following prose but an open fence protects the rest`() {
        val fenced = "   ````text\r\n\\(inside\\)\r\n```\r\n\\[also inside\\]\r\n   `````\r\n"
        val open = "~~~text\n\\frac{kept}{literal}\n\\(also literal\\)"
        assertEquals(
            fenced + "After \$x\$.\n\n" + open,
            normalizeMarkdownLatex(fenced + "After \\(x\\).\n\n" + open),
        )
    }

    @Test
    fun `only matching whole backtick runs protect inline code`() {
        val longRun = "`".repeat(1024)
        val code = "$longRun\\(literal\\) `` $longRun"
        assertEquals(
            "Text $code ` stray then \$x\$.",
            normalizeMarkdownLatex("Text $code ` stray then \\(x\\)."),
        )
    }

    @Test
    fun `escaped delimiters and indented code stay literal`() {
        val literal = "escaped \\\\(x\\\\) and \\\\[y\\\\]\n    \\frac{a}{b}\n\t\\(indented\\)\n"
        assertEquals(literal + "\$z\$", normalizeMarkdownLatex(literal + "\\(z\\)"))
        assertEquals("prose \\` still \$x\$ then ` stray", normalizeMarkdownLatex("prose \\` still \\(x\\) then ` stray"))
    }

    @Test
    fun `an unmatched formula cannot consume later valid formulas or other paragraphs`() {
        val prefix = "Start " + "\\(unclosed ".repeat(4_000)
        val expectedPrefix = "Start " + "&#40;unclosed ".repeat(4_000)
        assertEquals(expectedPrefix + "\$x\$ end", normalizeMarkdownLatex(prefix + "\\(x\\) end"))
        val acrossParagraphs = "\\[unclosed\n\nNew paragraph\\]."
        assertEquals("&#91;unclosed\n\nNew paragraph\\].", normalizeMarkdownLatex(acrossParagraphs))
        assertEquals(
            "\\begin{align} unclosed \$\$\\begin{equation}a\\end{equation}\$\$",
            normalizeMarkdownLatex("\\begin{align} unclosed \\begin{equation}a\\end{equation}"),
        )
    }

    @Test
    fun `isolates existing dollar math and fenced code in list items`() {
        val literal = "\$\$a\\left\\[x\\right\\]\$\$\n\n- ~~~text\n  \\(code\\)\n\n  \\frac{a}{b}\n  ~~~\n\n"
        assertEquals(literal + "\$\$q\$\$", normalizeMarkdownLatex(literal + "\\[q\\]"))
    }

    @Test
    fun `keeps unfinished inline code literal while streaming`() {
        val source = "Before \\(x\\) then `\\(code\\)"
        assertEquals("Before \$x\$ then `\\(code\\)", normalizeMarkdownLatex(source, streaming = true))
        assertEquals("Before \$x\$ then `\$code\$", normalizeMarkdownLatex(source))
    }

    @Test
    fun `Both BetterText parsers receive one line display formulas and preserve TeX row breaks`() {
        val source = "\\[\n\\begin{aligned}\r\nx &= 1 \\\\\r\ny &= 2\n\\end{aligned}\n\\]"
        val expected = MarkdownInline.Math("\\begin{aligned} x &= 1 \\\\ y &= 2 \\end{aligned}", display = true)
        val display = preprocessMarkdownForDisplay(source, MarkdownRenderMode.STANDARD)
        val stream = MarkdownStreamParser()
        stream.parse(preprocessMarkdownForDisplay(source.dropLast(2), MarkdownRenderMode.STANDARD, streaming = true), true)

        for (document in listOf(parseMarkdown(display), stream.parse(display, true), stream.parse(display))) {
            val paragraph = document.blocks.single() as MarkdownBlock.Paragraph
            assertEquals(listOf(expected), paragraph.content)
        }
    }

    @Test
    fun `BetterText does not rejoin residual display delimiters across paragraphs`() {
        val source = "\\[not a formula\n\nSecond paragraph\\]. Then \\(x\\)."
        val display = preprocessMarkdownForDisplay(source, MarkdownRenderMode.STANDARD)
        for (document in listOf(parseMarkdown(display), MarkdownStreamParser().parse(display, true))) {
            val paragraphs = document.blocks.filterIsInstance<MarkdownBlock.Paragraph>()
            assertEquals(2, paragraphs.size)
            assertEquals("[not a formula", paragraphs[0].content.plainText())
            assertEquals(listOf(MarkdownInline.Math("x")), paragraphs[1].content.filterIsInstance<MarkdownInline.Math>())
        }
    }

    @Test
    fun `failed formulas have a readable fallback without raw macro names`() {
        val readable = latexReadableFallback("\\dfrac{\\alpha}{\\beta} \\le \\sqrt{\\mathrm{x}}")

        assertEquals("(α)/(β) ≤ √(x)", readable)
    }

    @Test
    fun `layout fallback handles word and punctuation commands without stripping prefixes`() {
        assertEquals("( x ) y z", latexReadableFallback("\\left( x \\right)\\,y\\;z\\!\\quad\\qquad"))
        assertEquals("leftover rightward quadric", latexReadableFallback("\\leftover \\rightward \\quadric"))
        assertEquals("← x →", latexReadableFallback("\\leftarrow x \\rightarrow"))
        assertEquals("a b c", latexReadableFallback("a\\,b\\;c"))
    }
}
