package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.datastore.ChatBubbleStyleTarget
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.ChatMessageBackgroundStyle
import me.rerere.rikkahub.data.datastore.finiteAppearanceValue
import me.rerere.rikkahub.ui.components.ui.ResolvedBubbleStyle
import me.rerere.rikkahub.ui.components.ui.resolveMessageStyle
import me.rerere.rikkahub.ui.modifier.motionStretchHeight
import me.rerere.rikkahub.ui.shape.styledShape
import me.rerere.rikkahub.ui.theme.LocalCornerScale
import me.rerere.rikkahub.ui.theme.LocalCornerStyle
import me.rerere.rikkahub.ui.theme.LocalFullWidthComponents
import me.rerere.rikkahub.ui.theme.LocalUiSlots
import me.rerere.rikkahub.ui.theme.SquareShapes
import kotlin.math.roundToInt

// 边框宽度上限：再宽就会盖住正文
private const val MAX_MESSAGE_STYLE_BORDER_WIDTH = 8f

/** 消息样式解析出的边框宽度：非有限值退回 1dp，上限见 [MAX_MESSAGE_STYLE_BORDER_WIDTH]。 */
internal fun resolvedMessageBorderWidth(width: Dp): Dp =
    finiteAppearanceValue(width.value, 0f, MAX_MESSAGE_STYLE_BORDER_WIDTH, 1f).dp

internal data class ThinkingBubbleVisual(
    val resolvedStyle: ResolvedBubbleStyle?,
    val baseColor: Color,
    val border: BorderStroke?,
    val radius: Dp,
    val contentColor: Color,
    val customized: Boolean,
    val cardContainerColor: Color,
)

@Composable
internal fun resolveThinkingBubbleVisual(
    display: DisplaySetting,
): ThinkingBubbleVisual {
    val customized = display.customizeThinkingToolCards &&
        display.chatMessageBackgroundStyle != ChatMessageBackgroundStyle.DEFAULT
    val resolved = if (customized) resolveMessageStyle(
        colorScheme = MaterialTheme.colorScheme,
        display = display,
        target = ChatBubbleStyleTarget.THINKING_TOOL_CARD,
    ) else null
    val base = resolved?.background ?: MaterialTheme.colorScheme.surfaceContainerHigh
    return ThinkingBubbleVisual(
        resolvedStyle = resolved,
        baseColor = base,
        border = resolved?.let { BorderStroke(resolvedMessageBorderWidth(it.borderWidth), it.border) },
        radius = resolved?.radius ?: 20.dp,
        contentColor = resolved?.text ?: MaterialTheme.colorScheme.onSurface,
        customized = customized,
        cardContainerColor = if (customized) Color.Transparent
            else base.copy(alpha = display.bubbleOpacity.coerceIn(0f, 1f)),
    )
}

/**
 * demo/m3 的用户气泡与助手正文：用户按内容收缩，最多占行宽的 84%，
 * 助手保持无底色排版。圆角只裁剪独立的水波纹层，正文和触摸区域不经过
 * 非对称 Path 图层，避免超长消息触发平台图层尺寸限制。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun ChatMessageBubble(
    role: MessageRole,
    color: Color,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    // 正文的弹性伸展与折叠：高度追着内容走
    stretchHeight: Boolean = false,
    streaming: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    popup: (@Composable () -> Unit)? = null,
    joinedToPrevious: Boolean = false,
    joinedToNext: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val fullWidth = LocalFullWidthComponents.current
    val isUser = role == MessageRole.USER
    val interactive = onClick != null || onLongClick != null
    val userShape = if (fullWidth) SquareShapes.small else styledShape(
        style = LocalCornerStyle.current,
        topStart = CornerSize(22.dp),
        topEnd = CornerSize(7.dp),
        bottomEnd = CornerSize(if (joinedToNext) 7.dp else 22.dp),
        bottomStart = CornerSize(22.dp),
        scale = LocalCornerScale.current,
    )
    val userWidth = if (isUser && !fullWidth) {
        Modifier.layout { measurable, constraints ->
            val maxWidth = if (constraints.hasBoundedWidth) {
                (constraints.maxWidth * .84f).roundToInt()
            } else constraints.maxWidth
            val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = maxWidth))
            val width = constraints.constrainWidth(placeable.width)
            layout(width, placeable.height) {
                placeable.placeRelative(width - placeable.width, 0)
            }
        }
    } else Modifier
    Box(
        modifier = modifier
            // 全宽模式下贴到边，其余情况按内容收缩
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .then(userWidth)
            .then(if (isUser) Modifier.background(color, userShape) else Modifier)
            .then(
                if (interactive) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = if (isUser) null else ripple(),
                        onLongClickLabel = onLongClickLabel,
                        onLongClick = onLongClick,
                        onClick = { onClick?.invoke() },
                    )
                } else {
                    Modifier
                },
            )
            .then(if (stretchHeight) Modifier.motionStretchHeight() else Modifier),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (isUser) {
                LocalUiSlots.current.userBubbleText
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ) {
            Box(
                // MarkdownBlock 自带 4dp 水平内边距，用户正文合计为 16dp / 12dp。
                modifier = if (isUser) Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                else Modifier.padding(vertical = 2.dp),
            ) { content() }
            if (isUser && interactive) {
                // 仅装饰层参与圆角裁剪；它不接收触摸，也不改变气泡的测量结果。
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(userShape)
                        .indication(interactionSource, ripple()),
                )
            }
        }
        popup?.invoke()
    }
}
