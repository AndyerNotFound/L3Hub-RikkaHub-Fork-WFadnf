package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.combinedClickable
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
import me.rerere.rikkahub.ui.modifier.motionStretchHeight
import me.rerere.rikkahub.ui.theme.LocalFullWidthComponents

// 消息样式给出的模糊半径最多到这里。AdvancedMaterial 内部还会按 SDK 与实时档位再夹一次
// （resolvedLiveAppearanceRadius：TIRAMISU 以下 12dp、以上 40dp），这里只挡住明显越界与非法值。
private const val MAX_MESSAGE_STYLE_MATERIAL_RADIUS = 40f

// 边框宽度上限：再宽就会盖住正文
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
 * 消息正文：不画底色、不画边框、不做圆角。
 *
 * 朴素外观里一条消息就是一个文本块，角色靠头像与对齐方式区分：
 * 用户消息在调用点按 End 对齐，助手消息按 Start 对齐，两者之间只有纵向间距。
 * [color]、[hazeState]、[joinedToPrevious]、[joinedToNext]、[streaming] 保留在签名里，
 * 是因为调用点还在传；它们已经不影响绘制。
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
    Box(
        modifier = modifier
            // 全宽模式下贴到边，其余情况按内容收缩
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .then(
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
            .then(if (stretchHeight) Modifier.motionStretchHeight() else Modifier)
            // MarkdownBlock 自带 4dp 的水平内边距，这里只留一点点纵向呼吸，不再留气泡内边距
            .padding(vertical = 2.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() }
        popup?.invoke()
    }
}
