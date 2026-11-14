package me.rerere.rikkahub.ui.components.message.tools

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.View
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.richtext.HighlightCodeBlock
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.richtext.SelectableContent
import me.rerere.rikkahub.ui.modifier.onClick
import me.rerere.rikkahub.ui.theme.JetbrainsMono

/**
 * 工具详情里的文本块
 *
 * JSON 与代码走带复制/下载的代码块; Markdown 默认渲染成富文本, 右上角可切回源码;
 * 纯文本按正文排版, 可长按选择, 里面的链接可点.
 */
@Composable
fun ToolRichText(
    layout: ToolTextLayout,
    modifier: Modifier = Modifier,
) {
    when (layout.format) {
        ToolTextFormat.Markdown -> MarkdownRichTextBlock(layout = layout, modifier = modifier)
        ToolTextFormat.Plain -> PlainTextBlock(text = layout.text, modifier = modifier)
        ToolTextFormat.Json, ToolTextFormat.Code -> HighlightCodeBlock(
            code = layout.text,
            language = layout.language,
            modifier = modifier,
        )
    }
}

@Composable
fun ToolRichText(
    text: String,
    modifier: Modifier = Modifier,
    languageHint: String? = null,
) {
    val layout = remember(text, languageHint) { ToolTextPresentation.of(text, languageHint) }
    ToolRichText(layout = layout, modifier = modifier)
}

/** 文件正文: 按路径推断语言, Markdown 直接渲染 */
@Composable
fun ToolRichTextForPath(
    path: String?,
    text: String,
    modifier: Modifier = Modifier,
) {
    val layout = remember(path, text) { ToolTextPresentation.forPath(path, text) }
    ToolRichText(layout = layout, modifier = modifier)
}

@Composable
private fun MarkdownRichTextBlock(
    layout: ToolTextLayout,
    modifier: Modifier = Modifier,
) {
    var showSource by remember { mutableStateOf(false) }
    // 太大的文件不自动排版, 避免详情面板一次解析几万行; 想看源码本来也不需要排版
    val canRender = layout.text.length <= ToolTextPresentation.MARKDOWN_AUTO_RENDER_MAX_CHARS
    val rendered = canRender && !showSource
    if (!rendered) {
        HighlightCodeBlock(
            code = layout.text,
            language = layout.language,
            modifier = modifier,
            headerActions = if (canRender) {
                {
                    RichTextToggle(rendered = false, onToggle = { showSource = false })
                }
            } else {
                null
            },
        )
        return
    }

    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = layout.language,
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    fontFamily = JetbrainsMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Spacer(Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RichTextAction(
                        icon = HugeIcons.Copy01,
                        contentDescription = stringResource(R.string.code_block_copy),
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("markdown", layout.text))
                                )
                            }
                        },
                    )
                    RichTextToggle(rendered = true, onToggle = { showSource = true })
                }
            }
        }
        SelectableContent {
            MarkdownBlock(
                content = layout.richText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RichTextAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .onClick(onClick = onClick)
            .padding(4.dp)
            .size(16.dp),
    )
}

@Composable
private fun RichTextToggle(rendered: Boolean, onToggle: () -> Unit) {
    RichTextAction(
        icon = if (rendered) HugeIcons.Code else HugeIcons.View,
        contentDescription = stringResource(
            if (rendered) R.string.tool_ui_show_source else R.string.code_block_preview
        ),
        onClick = onToggle,
    )
}

@Composable
private fun PlainTextBlock(text: String, modifier: Modifier = Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) { linkify(text, linkColor) }
    SelectionContainer {
        Text(
            text = annotated,
            modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val URL_PATTERN = Regex("""https?://[^\s<>"']+""")
private val TRAILING_PUNCTUATION = setOf(
    '.', ',', ';', ':', ')', ']', '}', '!', '?',
    '。', '，', '；', '：', '！', '？',
)

/** 纯文本里的 URL 变成可点链接, 末尾标点留在正文里 */
private fun linkify(text: String, linkColor: androidx.compose.ui.graphics.Color): androidx.compose.ui.text.AnnotatedString =
    buildAnnotatedString {
        var cursor = 0
        URL_PATTERN.findAll(text).forEach { match ->
            val raw = match.value
            val url = raw.trimEnd { it in TRAILING_PUNCTUATION }
            if (url.length <= "https://".length) return@forEach
            append(text.substring(cursor, match.range.first))
            withLink(LinkAnnotation.Url(url)) {
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(url)
                }
            }
            append(raw.substring(url.length))
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }
