package me.rerere.rikkahub.ui.components.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.ChatBubbleStyleOverrides
import me.rerere.rikkahub.data.datastore.ChatMessageBackgroundStyle

/** 消息样式解析结果：气泡实际绘制用的全部参数。 */
data class ResolvedBubbleStyle(
    val background: Color,
    val border: Color,
    val text: Color,
    val borderWidth: Dp,
    val radius: Dp,
    val blurRadius: Dp,
)

/**
 * 把「消息样式 + 覆盖项 + 当前主题」解析成气泡实际绘制的参数，
 * 移植自 Kelivo 的 `resolveBubbleStyle`。
 *
 * 默认值对齐 Kelivo：frosted 透明度 0.66、solid 1.0、边框透明度
 * frosted 0.14 / solid 0.16、边框宽 0.8dp、圆角 16dp、模糊半径 14dp；
 * 背景 / 边框 / 文字未覆盖时跟随主题角色色。
 */
fun resolveBubbleStyle(
    colorScheme: ColorScheme,
    isDark: Boolean,
    style: ChatMessageBackgroundStyle,
    overrides: ChatBubbleStyleOverrides,
): ResolvedBubbleStyle {
    val bgArgb = if (isDark) overrides.backgroundArgbDark else overrides.backgroundArgbLight
    val borderArgb = if (isDark) overrides.borderArgbDark else overrides.borderArgbLight
    val textArgb = if (isDark) overrides.textArgbDark else overrides.textArgbLight

    val opacity = when (style) {
        ChatMessageBackgroundStyle.FROSTED -> overrides.frostedOpacity ?: 0.66f
        ChatMessageBackgroundStyle.SOLID,
        ChatMessageBackgroundStyle.DEFAULT,
        -> overrides.solidOpacity ?: 1.0f
    }
    val borderOpacity = overrides.borderOpacity
        ?: if (style == ChatMessageBackgroundStyle.FROSTED) 0.14f else 0.16f

    return ResolvedBubbleStyle(
        background = (bgArgb?.let(::Color) ?: colorScheme.surfaceContainerHigh)
            .copy(alpha = opacity.coerceIn(0f, 1f)),
        border = (borderArgb?.let(::Color) ?: colorScheme.outlineVariant)
            .copy(alpha = borderOpacity.coerceIn(0f, 1f)),
        text = textArgb?.let(::Color) ?: colorScheme.onSurface,
        borderWidth = (overrides.borderWidth ?: 0.8f).dp,
        radius = (overrides.cornerRadius ?: 16f).dp,
        blurRadius = (overrides.blurSigma ?: 14f).dp,
    )
}
