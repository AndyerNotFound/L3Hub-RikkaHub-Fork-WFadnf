package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import me.rerere.bettertext.highlight.CODE_FONT_FEATURES
import me.rerere.bettertext.text.BetterText
import me.rerere.rikkahub.ui.theme.JetbrainsMono

/**
 * 只读终端输出渲染：把 SGR 解析成 SpanStyle 交给 Compose，不经过 WebView。
 *
 * 没有输入、没有 PTY、没有超链接、没有图片协议，也就没有需要拦截的桥。
 * 太长的输出在解析阶段就截断，行数由外层代码块的折叠逻辑控制。
 *
 * 用 [BetterText] 而不是 Material 的 `Text`，是为了和外层消息共用同一个选择容器，
 * 与目标端其它代码块的选取行为保持一致。
 */
@Composable
internal fun AnsiCodeBlock(
    code: String,
    modifier: Modifier = Modifier,
) {
    val document = remember(code) { ansiDocument(code) }
    val defaultForeground = LocalContentColor.current
    val annotated = remember(document, defaultForeground) {
        buildAnsiText(document, defaultForeground.toArgb())
    }
    SelectableContent {
        BetterText(
            text = annotated,
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = JetbrainsMono,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontFeatureSettings = CODE_FONT_FEATURES,
            ),
        )
    }
}

/** 样式片段转 AnnotatedString；未设置的背景保持透明，避免反显时铺一层默认底色。 */
private fun buildAnsiText(document: AnsiDocument, defaultArgb: Int): AnnotatedString =
    buildAnnotatedString {
        document.segments.forEach { segment ->
            val start = length
            append(segment.text)
            val style = segment.style
            if (style == AnsiStyle.Default) return@forEach
            addStyle(
                SpanStyle(
                    // dim 用降低不透明度表达，不做任何颜色混合猜测。
                    color = Color(style.resolvedForeground(defaultArgb)).let {
                        if (style.dim) it.copy(alpha = it.alpha * 0.6f) else it
                    },
                    background = style.background?.let { Color(style.resolvedBackground(defaultArgb)) }
                        ?: Color.Unspecified,
                    fontWeight = if (style.bold) FontWeight.Bold else null,
                    fontStyle = if (style.italic) FontStyle.Italic else null,
                    textDecoration = when {
                        style.underline && style.strike -> TextDecoration.combine(
                            listOf(TextDecoration.Underline, TextDecoration.LineThrough)
                        )
                        style.underline -> TextDecoration.Underline
                        style.strike -> TextDecoration.LineThrough
                        else -> null
                    },
                ),
                start,
                length,
            )
        }
    }
