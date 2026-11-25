package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import me.rerere.bettertext.latex.LatexBlock
import me.rerere.bettertext.latex.LatexInline
import me.rerere.bettertext.latex.LatexLayout
import me.rerere.bettertext.latex.LatexSettings
import me.rerere.bettertext.markdown.MarkdownInlineScope
import me.rerere.bettertext.text.BetterInline
import me.rerere.bettertext.text.ComposableInline

private const val MAX_LATEX_RENDER_CHARS = 4096
private val displayMathSettings = LatexSettings(displayMode = true)

/** 块级公式，居中显示，太宽时横向滚动；字号和颜色默认跟随当前文字样式 */
@Composable
fun MathBlock(
    latex: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified
) {
    val style = LocalTextStyle.current
    val context = LocalContext.current.applicationContext
    val layout = remember(latex, context) {
        firstRenderableLatex(latex) { candidate ->
            LatexLayout.create(candidate, displayMathSettings, context).takeIf { it.isUsable() }
        }
    }
    val size = fontSize.takeOrElse { style.fontSize.takeOrElse { 16.sp } }
    val color = style.color.takeOrElse { LocalContentColor.current }
    val padded = modifier.padding(8.dp)
    if (layout != null) {
        LatexBlock(layout = layout, modifier = padded, fontSize = size, color = color)
    } else {
        Text(
            text = remember(latex) { readableLatexOrSource(latex) },
            modifier = padded,
            fontSize = size,
            color = color,
            fontFamily = FontFamily.Monospace,
        )
    }
}

internal fun MarkdownInlineScope.renderInlineLatex(latex: String): BetterInline? {
    // A null slot value asks BetterText to show the source; don't run more parsers on oversized formulas.
    if (latex.length > MAX_LATEX_RENDER_CHARS || latex.isBlank()) return null
    firstRenderableLatex(latex) { candidate ->
        LatexInline.cached(candidate, context.applicationContext).takeIf { it.layout.isUsable() }
    }?.let { return it }
    val readable = readableLatexOrSource(latex)
    return ComposableInline {
        Text(text = readable, style = style, color = style.color.takeOrElse { contentColor }, fontFamily = FontFamily.Monospace)
    }
}

private fun LatexLayout.isUsable(): Boolean = error == null && (chunkCount > 0 || tagWidth > 0f)

private inline fun <T : Any> firstRenderableLatex(latex: String, render: (String) -> T?): T? {
    if (latex.length > MAX_LATEX_RENDER_CHARS) return null
    val source = latex.trim().stripLatexDelimiters()
    if (source.isBlank()) return null
    return try {
        // Preserve successful BetterText output; don't rewrite macros it already understands.
        render(source) ?: latexRenderCandidates(source).firstNotNullOfOrNull { candidate ->
            if (candidate == source) null else render(candidate)
        }
    } catch (_: RuntimeException) {
        null
    } catch (_: StackOverflowError) {
        // A deeply nested expression can exhaust the renderer's recursion before it reports a parse error.
        null
    }
}

private fun readableLatexOrSource(latex: String): String =
    if (latex.length > MAX_LATEX_RENDER_CHARS) latex else latexReadableFallback(latex).ifBlank { latex }
