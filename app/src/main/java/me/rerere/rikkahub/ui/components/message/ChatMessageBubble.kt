package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import dev.chrisbanes.haze.HazeState
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.datastore.AdvancedAppearanceSetting
import me.rerere.rikkahub.data.datastore.BackgroundSurfaceStyle
import me.rerere.rikkahub.data.datastore.ChatBubbleStyle
import me.rerere.rikkahub.data.datastore.ChatBubbleStyleTarget
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.ChatMessageBackgroundStyle
import me.rerere.rikkahub.data.datastore.finiteAppearanceValue
import me.rerere.rikkahub.data.datastore.overridesFor
import me.rerere.rikkahub.data.model.usesMaterial3Style
import me.rerere.rikkahub.ui.components.richtext.outline
import me.rerere.rikkahub.ui.components.richtext.richContentMaterial
import me.rerere.rikkahub.ui.components.ui.AppearanceMaterial
import me.rerere.rikkahub.ui.components.ui.ResolvedBubbleStyle
import me.rerere.rikkahub.ui.components.ui.resolveMessageStyle
import me.rerere.rikkahub.ui.components.ui.advancedMaterial
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.modifier.motionStretchHeight
import me.rerere.rikkahub.ui.theme.LocalFullWidthComponents
import me.rerere.rikkahub.ui.theme.adaptiveCornerRadius

private val BubbleTailCorner = 6.dp
private val BubblePressedCorner = 12.dp

// 同组相邻气泡接缝处的圆角：上一条的下圆角与下一条的上圆角都收成这个值，两条气泡看着是连着的
private val BubbleJoinedCorner = 8.dp

// 消息样式给出的模糊半径最多到这里。AdvancedMaterial 内部还会按 SDK 与实时档位再夹一次
// （resolvedLiveAppearanceRadius：TIRAMISU 以下 12dp、以上 40dp），这里只挡住明显越界与非法值。
private const val MAX_MESSAGE_STYLE_MATERIAL_RADIUS = 40f

// 边框宽度上限：再宽就会盖住气泡里的正文
private const val MAX_MESSAGE_STYLE_BORDER_WIDTH = 8f

internal fun AdvancedAppearanceSetting.messageBubbleMaterial(
    display: DisplaySetting,
    target: ChatBubbleStyleTarget,
    resolved: ResolvedBubbleStyle?,
    streaming: Boolean,
): AppearanceMaterial? {
    val messageStyle = display.chatMessageBackgroundStyle
    if (messageStyle == ChatMessageBackgroundStyle.SOLID) return null
    if (!enableAdvancedAppearance) {
        if (messageStyle != ChatMessageBackgroundStyle.FROSTED) return null
        val role = display.overridesFor(target)
        return AppearanceMaterial(
            style = BackgroundSurfaceStyle.FROSTED,
            opacity = role.frostedOpacity ?: resolved?.background?.alpha ?: .66f,
            radius = role.blurSigma?.let { finiteAppearanceValue(it, 0f, 30f, 14f) * 1.5f }
                ?: resolved?.blurRadius?.value ?: 21f,
            effects = !streaming,
        )
    }
    // DEFAULT follows the advanced material preset and never reads per-role message overrides.
    val roleOverrides = if (messageStyle == ChatMessageBackgroundStyle.FROSTED) display.overridesFor(target)
        else me.rerere.rikkahub.data.datastore.ChatBubbleStyleOverrides()
    val style = when (chatBubbleStyle) {
        ChatBubbleStyle.FROSTED -> BackgroundSurfaceStyle.FROSTED
        ChatBubbleStyle.OUTLINED -> BackgroundSurfaceStyle.TRANSLUCENT
        ChatBubbleStyle.LIQUID_GLASS -> BackgroundSurfaceStyle.LIQUID_GLASS
    }
    val opacity = finiteAppearanceValue(
        when {
            messageStyle == ChatMessageBackgroundStyle.FROSTED ->
                roleOverrides.frostedOpacity ?: chatBubbleSurfaceOpacity ?: (.46f + display.bubbleOpacity * .36f)
            else -> chatBubbleSurfaceOpacity ?: when (chatBubbleStyle) {
                ChatBubbleStyle.FROSTED -> .46f + display.bubbleOpacity * .36f
                ChatBubbleStyle.OUTLINED -> .16f + display.bubbleOpacity * .12f
                ChatBubbleStyle.LIQUID_GLASS -> .62f + display.bubbleOpacity * .24f
            }
        },
        0f, 1f, .66f,
    )
    val radius = when {
        style == BackgroundSurfaceStyle.LIQUID_GLASS && liquidGlass.usesMaterial3Style() -> 16f
        messageStyle == ChatMessageBackgroundStyle.FROSTED && roleOverrides.blurSigma != null ->
            finiteAppearanceValue(roleOverrides.blurSigma, 0f, 30f, 14f) * 1.5f
        chatBubbleBlurRadius != null -> finiteAppearanceValue(chatBubbleBlurRadius, 0f, MAX_MESSAGE_STYLE_MATERIAL_RADIUS, 18f)
        // An empty per-role override inherits the advanced material default (or migrated radius),
        // never resolveBubbleStyle's unrelated 14-sigma fallback.
        style == BackgroundSurfaceStyle.LIQUID_GLASS -> 24f
        style == BackgroundSurfaceStyle.FROSTED -> 18f
        else -> 0f
    }
    return AppearanceMaterial(
        style = style,
        opacity = opacity,
        radius = radius,
        effects = enableBubblePerformanceEffects && !streaming,
    )
}

/** 消息样式解析出的边框宽度：非有限值退回 1dp，上限见 [MAX_MESSAGE_STYLE_BORDER_WIDTH]。 */
internal fun resolvedMessageBorderWidth(width: Dp): Dp =
    finiteAppearanceValue(width.value, 0f, MAX_MESSAGE_STYLE_BORDER_WIDTH, 1f).dp

internal fun messageBubbleShape(
    role: MessageRole,
    radius: Dp,
    joinedToPrevious: Boolean = false,
    joinedToNext: Boolean = false,
    pressProgress: Float = 0f,
): RoundedCornerShape {
    val corner = lerp(radius, minOf(radius, BubblePressedCorner), pressProgress)
    val tail = minOf(BubbleTailCorner, radius * .25f)
    val join = minOf(BubbleJoinedCorner, corner)
    return if (role == MessageRole.USER) {
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
}

internal data class MessageBubbleVisual(
    val resolvedStyle: ResolvedBubbleStyle?,
    val background: Color,
    val contentColor: Color,
    val material: AppearanceMaterial?,
    val borderColor: Color?,
    val borderWidth: Dp,
    val radius: Dp,
)

@Composable
internal fun resolveMessageBubbleVisual(
    appearance: AdvancedAppearanceSetting,
    display: DisplaySetting,
    role: MessageRole,
    fallbackColor: Color,
    streaming: Boolean,
): MessageBubbleVisual {
    val target = if (role == MessageRole.USER) ChatBubbleStyleTarget.USER else ChatBubbleStyleTarget.ASSISTANT
    val scheme = MaterialTheme.colorScheme
    val resolved = resolveMessageStyle(scheme, display, target)
    val material = appearance.messageBubbleMaterial(display, target, resolved, streaming)
    val fallbackBorder = when {
        material == null -> null
        appearance.chatBubbleStyle == ChatBubbleStyle.FROSTED -> scheme.outlineVariant.copy(alpha = .4f)
        appearance.chatBubbleStyle == ChatBubbleStyle.OUTLINED ->
            (if (role == MessageRole.USER) scheme.primary else scheme.outline).copy(alpha = .62f)
        else -> null
    }
    val width = resolved?.let { resolvedMessageBorderWidth(it.borderWidth) }
        ?: if (appearance.chatBubbleStyle == ChatBubbleStyle.OUTLINED) 1.dp else .75.dp
    val content = resolved?.text ?: when {
        material == null -> contentColorFor(fallbackColor)
        role == MessageRole.USER && appearance.chatBubbleStyle != ChatBubbleStyle.OUTLINED -> scheme.onPrimaryContainer
        else -> scheme.onSurface
    }
    return MessageBubbleVisual(
        resolvedStyle = resolved,
        background = if (material != null) (resolved?.background ?: fallbackColor).copy(alpha = 1f)
            else resolved?.background ?: fallbackColor,
        contentColor = content,
        material = material,
        borderColor = resolved?.border ?: fallbackBorder,
        borderWidth = width,
        radius = adaptiveCornerRadius(resolved?.radius ?: display.uiCornerRadius.dp),
    )
}

internal data class ThinkingBubbleVisual(
    val resolvedStyle: ResolvedBubbleStyle?,
    val baseColor: Color,
    val material: AppearanceMaterial?,
    val border: BorderStroke?,
    val radius: Dp,
    val contentColor: Color,
    val customized: Boolean,
    val cardContainerColor: Color,
)

@Composable
internal fun resolveThinkingBubbleVisual(
    appearance: AdvancedAppearanceSetting,
    display: DisplaySetting,
    streaming: Boolean,
): ThinkingBubbleVisual {
    val customized = display.customizeThinkingToolCards &&
        display.chatMessageBackgroundStyle != ChatMessageBackgroundStyle.DEFAULT
    val resolved = if (customized) resolveMessageStyle(
        colorScheme = MaterialTheme.colorScheme,
        display = display,
        target = ChatBubbleStyleTarget.THINKING_TOOL_CARD,
    ) else null
    val resolvedBase = resolved?.background ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val material = appearance.thinkingBubbleMaterial(display, resolved, streaming)
    val base = if (material != null) resolvedBase.copy(alpha = 1f) else resolvedBase
    return ThinkingBubbleVisual(
        resolvedStyle = resolved,
        baseColor = base,
        material = material,
        border = when {
            resolved != null -> BorderStroke(resolvedMessageBorderWidth(resolved.borderWidth), resolved.border)
            material != null -> appearance.richContentStyle.outline()
            else -> null
        },
        radius = resolved?.radius ?: 20.dp,
        contentColor = resolved?.text ?: MaterialTheme.colorScheme.onSurface,
        customized = customized,
        cardContainerColor = if (material != null || customized) Color.Transparent
            else base.copy(alpha = display.bubbleOpacity.coerceIn(0f, 1f)),
    )
}

internal fun AdvancedAppearanceSetting.thinkingBubbleMaterial(
    display: DisplaySetting,
    resolved: ResolvedBubbleStyle?,
    streaming: Boolean,
): AppearanceMaterial? {
    val custom = display.customizeThinkingToolCards &&
        display.chatMessageBackgroundStyle != ChatMessageBackgroundStyle.DEFAULT
    if (custom && display.chatMessageBackgroundStyle == ChatMessageBackgroundStyle.SOLID) return null
    if (!enableAdvancedAppearance) {
        if (!custom || display.chatMessageBackgroundStyle != ChatMessageBackgroundStyle.FROSTED) return null
        val role = display.overridesFor(ChatBubbleStyleTarget.THINKING_TOOL_CARD)
        return AppearanceMaterial(
            style = BackgroundSurfaceStyle.FROSTED,
            opacity = role.frostedOpacity ?: resolved?.background?.alpha ?: .66f,
            radius = role.blurSigma?.let { finiteAppearanceValue(it, 0f, 30f, 14f) * 1.5f }
                ?: resolved?.blurRadius?.value ?: 21f,
            effects = !streaming,
        )
    }
    val material = richContentMaterial(streaming) ?: return null
    if (!custom) return material
    val role = display.overridesFor(ChatBubbleStyleTarget.THINKING_TOOL_CARD)
    return material.copy(
        // Thinking cards inherit rich-content opacity and radius; only explicit thinking-role
        // values override them. The body-bubble material slider must not leak into this surface.
        opacity = role.frostedOpacity ?: material.opacity,
        radius = when {
            material.style == BackgroundSurfaceStyle.LIQUID_GLASS && liquidGlass.usesMaterial3Style() -> 16f
            material.style.usesBlur && role.blurSigma != null -> finiteAppearanceValue(role.blurSigma, 0f, 30f, 14f) * 1.5f
            else -> material.radius
        },
    )
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
    val visual = resolveMessageBubbleVisual(appearance, displaySetting, role, color, streaming)
    val resolvedStyle = visual.resolvedStyle
    val material = visual.material
    val radius = visual.radius
    val shape = messageBubbleShape(role, radius, joinedToPrevious, joinedToNext, pressProgress)
    val resolvedBorder = visual.borderColor?.let { Modifier.border(visual.borderWidth, it, shape) } ?: Modifier
    val advancedSurface = material?.let {
        Modifier.advancedMaterial(shape, visual.background, it, hazeState).then(resolvedBorder)
    }
    Box(
        modifier = modifier
            // 全宽模式下气泡不再按内容收缩，左右边缘对齐父容器
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .clipDrawing(shape)
            .then(advancedSurface ?: Modifier.background(visual.background, shape))
            .then(if (material == null && resolvedStyle != null) resolvedBorder else Modifier)
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
        CompositionLocalProvider(LocalContentColor provides visual.contentColor) { content() }
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
