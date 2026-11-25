package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import dev.chrisbanes.haze.HazeState
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.datastore.AdvancedAppearanceSetting
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.BackgroundSurfaceStyle
import me.rerere.rikkahub.data.datastore.ChatBubbleStyle
import me.rerere.rikkahub.data.datastore.ChatMessageBackgroundStyle
import me.rerere.rikkahub.data.datastore.effectiveBackgroundEffect
import me.rerere.rikkahub.ui.components.ai.chatInputHaze
import me.rerere.rikkahub.ui.components.ui.AppearanceMaterial
import me.rerere.rikkahub.ui.components.ui.ComponentEffectBlurRadius
import me.rerere.rikkahub.ui.components.ui.advancedMaterial
import me.rerere.rikkahub.ui.components.ui.resolveBubbleStyle
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.modifier.motionStretchHeight
import me.rerere.rikkahub.ui.theme.LocalFullWidthComponents
import me.rerere.rikkahub.ui.theme.adaptiveCornerRadius

private val BubbleTailCorner = 6.dp
private val BubblePressedCorner = 12.dp

// 同组相邻气泡接缝处的圆角：上一条的下圆角与下一条的上圆角都收成这个值，两条气泡看着是连着的
private val BubbleJoinedCorner = 8.dp

internal fun AdvancedAppearanceSetting.messageBubbleMaterial(opacity: Float, streaming: Boolean): AppearanceMaterial? {
    if (!enableAdvancedAppearance) return null
    val material = when (chatBubbleStyle) {
        ChatBubbleStyle.FROSTED -> AppearanceMaterial(BackgroundSurfaceStyle.FROSTED, .46f + opacity * .36f, 18f)
        ChatBubbleStyle.OUTLINED -> AppearanceMaterial(BackgroundSurfaceStyle.TRANSLUCENT, .16f + opacity * .12f)
        ChatBubbleStyle.LIQUID_GLASS -> AppearanceMaterial(BackgroundSurfaceStyle.LIQUID_GLASS, .62f + opacity * .24f, 24f)
    }
    return material.copy(effects = enableBubblePerformanceEffects && !streaming)
}

/**
 * 消息气泡：大圆角，靠头像一侧的顶角收紧；可点击时按下其余圆角也跟着收紧。
 * 与同组相邻气泡相接的那条边统一收成 [BubbleJoinedCorner]，看上去是一条连线。
 */
@Composable
internal fun ChatMessageBubble(
    role: MessageRole,
    color: Color,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    // 正文气泡的弹性伸展与折叠：高度追着内容走
    stretchHeight: Boolean = false,
    // 这条正文还在流式输出：尺寸每来一段文本就变一次。模糊效果要按尺寸重新采样，
    // 等于每帧重算一遍背景，所以这一段退回静态底色，停止输出后恢复设置里的背景效果
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
    val pressProgress by animatePressProgress(interactionSource)
    val fullWidth = LocalFullWidthComponents.current
    val displaySetting = LocalSettings.current.displaySetting
    val appearance = LocalSettings.current.advancedAppearanceSetting
    val material = appearance.messageBubbleMaterial(displaySetting.bubbleOpacity, streaming)
    // 消息样式开启时（frosted / solid）用解析结果覆盖气泡参数；默认档保持现状
    val resolvedStyle = if (
        displaySetting.chatMessageBackgroundStyle != ChatMessageBackgroundStyle.DEFAULT
    ) {
        resolveBubbleStyle(
            colorScheme = MaterialTheme.colorScheme,
            style = displaySetting.chatMessageBackgroundStyle,
            overrides = when (role) {
                MessageRole.USER -> displaySetting.userChatBubbleStyleOverrides
                else -> displaySetting.assistantChatBubbleStyleOverrides
            },
        )
    } else {
        null
    }
    val radius = resolvedStyle?.radius ?: adaptiveCornerRadius(displaySetting.uiCornerRadius.dp)
    val corner = lerp(radius, minOf(radius, BubblePressedCorner), pressProgress)
    val tail = minOf(BubbleTailCorner, radius * 0.25f)
    // 接缝那一侧不能再留小尾巴，否则两条气泡之间会露一个豁口
    val join = minOf(BubbleJoinedCorner, corner)
    val shape = if (role == MessageRole.USER) {
        RoundedCornerShape(
            topStart = if (joinedToPrevious) join else corner,
            topEnd = if (joinedToPrevious) join else tail,
            bottomEnd = if (joinedToNext) join else corner,
            bottomStart = if (joinedToNext) join else corner,
        )
    } else {
        RoundedCornerShape(
            topStart = if (joinedToPrevious) join else tail,
            topEnd = if (joinedToPrevious) join else corner,
            bottomEnd = if (joinedToNext) join else corner,
            bottomStart = if (joinedToNext) join else corner,
        )
    }
    val effect = displaySetting.effectiveBackgroundEffect
    val useEffect = material == null && hazeState != null && effect != BackgroundEffectType.OFF && !streaming
    val hazeModifier = if (useEffect && hazeState != null) {
        Modifier.chatInputHaze(
            hazeState = hazeState,
            displaySetting = displaySetting,
            containerColor = resolvedStyle?.background ?: color,
            shape = shape,
            blurRadius = resolvedStyle?.blurRadius ?: ComponentEffectBlurRadius,
        )
    } else {
        Modifier
    }
    val scheme = MaterialTheme.colorScheme
    val advancedSurface = if (material != null) {
        Modifier.advancedMaterial(shape, resolvedStyle?.background ?: color, material, hazeState)
            .then(when (appearance.chatBubbleStyle) {
                ChatBubbleStyle.FROSTED -> Modifier.border(.75.dp, scheme.outlineVariant.copy(alpha = .4f), shape)
                ChatBubbleStyle.OUTLINED -> Modifier.border(
                    1.dp,
                    (if (role == MessageRole.USER) scheme.primary else scheme.outline).copy(alpha = .62f),
                    shape,
                )
                ChatBubbleStyle.LIQUID_GLASS -> Modifier
            })
    } else null
    val contentColor = resolvedStyle?.text ?: when {
        material == null -> contentColorFor(color)
        role == MessageRole.USER && appearance.chatBubbleStyle != ChatBubbleStyle.OUTLINED -> scheme.onPrimaryContainer
        else -> scheme.onSurface
    }
    Box(
        modifier = modifier
            // 全宽模式下气泡不再按内容收缩，左右边缘对齐父容器
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .then(if (useEffect) Modifier.graphicsLayer(shape = shape, clip = true) else Modifier)
            .clipDrawing(shape)
            .then(hazeModifier)
            // 特效开启时着色层由 chatInputHaze 叠在模糊之上，这里不再铺第二层
            .then(advancedSurface ?: if (useEffect) Modifier else Modifier.background(resolvedStyle?.background ?: color))
            .then(
                if (material == null && (resolvedStyle != null || (useEffect && effect == BackgroundEffectType.BLUR))) {
                    Modifier.border(
                        width = resolvedStyle?.borderWidth ?: 1.dp,
                        color = resolvedStyle?.border
                            ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            )
            .then(
                // 长按（用户消息的操作菜单）和点击（编辑）共用同一个交互源，
                // 按下时圆角收缩的反馈对两种手势都在
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onLongClickLabel = onLongClickLabel,
                        onLongClick = onLongClick,
                        onClick = { onClick?.invoke() },
                    )
                } else {
                    Modifier
                },
            )
            // 伸缩层放在气泡底色与形状裁剪的里侧：背景、圆角都按报告出去的高度画，
            // 内容按自然高度排版，超出的部分被上面的 clipDrawing 按当前形状裁掉
            .then(if (stretchHeight) Modifier.motionStretchHeight() else Modifier)
            // MarkdownBlock 自带 4dp 的水平内边距
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
        // 长按菜单挂在气泡的布局节点里，弹窗以气泡为锚点定位
        popup?.invoke()
    }
}

/**
 * 只裁剪绘制内容，避免把形状设成图层的触摸轮廓。
 *
 * 四个角半径不同时图层轮廓是 Path，Compose 判断触摸点是否落在轮廓内时用 Path 求交，
 * 坐标超过 8192px 后 float 精度不够，求交结果为空，长消息下半部分（例如展开的长代码块）就收不到触摸了。
 */
private fun Modifier.clipDrawing(shape: Shape): Modifier = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    onDrawWithContent {
        clipPath(path) { this@onDrawWithContent.drawContent() }
    }
}
