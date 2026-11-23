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
 * Kelivo 的 blurSigma（逻辑像素）到 haze blurRadius（dp）的强度换算系数。
 *
 * haze 的模糊会先按质量档位缩减采样（Balanced 约 0.79），再乘屏幕密度换算成
 * RenderEffect 半径，视觉强度约为直接模糊实现的六成；BlurView 一类实现用
 * radius × scaleFactor（默认 4）放大。取 1.5 让默认 14 sigma 的实际强度
 * 对齐「明显可感知的磨砂」，又不至于让上限 30 失控。
 */
const val BLUR_SIGMA_TO_DP_SCALE = 1.5f

/**
 * 把「消息样式 + 覆盖项 + 当前主题」解析成气泡实际绘制的参数，
 * 移植自 Kelivo 的 `resolveBubbleStyle`。
 *
 * 默认值对齐 Kelivo：frosted 透明度 0.66、solid 1.0、边框透明度
 * frosted 0.14 / solid 0.16、边框宽 0.8dp、圆角 16dp、模糊半径 14 sigma；
 * 颜色跟随当前主题（surfaceContainerHigh / outlineVariant / onSurface）。
 */
fun resolveBubbleStyle(
    colorScheme: ColorScheme,
    style: ChatMessageBackgroundStyle,
    overrides: ChatBubbleStyleOverrides,
): ResolvedBubbleStyle {
    // 颜色跟随当前主题（surfaceContainerHigh / outlineVariant / onSurface），
    // 覆盖项里的颜色字段不再读取；保留字段仅为兼容旧数据。
    val opacity = when (style) {
        ChatMessageBackgroundStyle.FROSTED -> overrides.frostedOpacity ?: 0.66f
        ChatMessageBackgroundStyle.SOLID,
        ChatMessageBackgroundStyle.DEFAULT,
        -> overrides.solidOpacity ?: 1.0f
    }
    val borderOpacity = overrides.borderOpacity
        ?: if (style == ChatMessageBackgroundStyle.FROSTED) 0.14f else 0.16f

    return ResolvedBubbleStyle(
        background = colorScheme.surfaceContainerHigh.copy(alpha = opacity.coerceIn(0f, 1f)),
        border = colorScheme.outlineVariant.copy(alpha = borderOpacity.coerceIn(0f, 1f)),
        text = colorScheme.onSurface,
        borderWidth = (overrides.borderWidth ?: 0.8f).dp,
        radius = (overrides.cornerRadius ?: 16f).dp,
        blurRadius = ((overrides.blurSigma ?: 14f) * BLUR_SIGMA_TO_DP_SCALE).dp,
    )
}
