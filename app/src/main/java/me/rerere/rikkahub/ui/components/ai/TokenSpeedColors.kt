package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 生成速度的三档配色：极慢灰、中等冰蓝、最快红。
 *
 * 两个显示位置共用这套颜色，但归一化的基准不一样：
 * - 加载图标看的是本轮实时速度，峰值取自本轮，比例由 [tokenSpeedRatioColor] 取色
 * - 上下文预警里的圆点看的是单次请求的平均速度，没有实时峰值可比，按 [tokenSpeedColor] 的绝对档位取色
 */
internal val TokenSpeedSlowColor = Color(0xFF9E9E9E)
internal val TokenSpeedMediumColor = Color(0xFF8DDDF2)
internal val TokenSpeedFastColor = Color(0xFFFF4D4F)

// 相对本轮峰值的档位：≤15% 停在灰色，50% 是冰蓝，≥85% 转红
private const val SLOW_RATIO = 0.15f
private const val MEDIUM_RATIO = 0.50f
private const val FAST_RATIO = 0.85f

// 单次请求平均速度（tok/s）的档位：10 以下算极慢，30 上下算中等，60 以上算快
private const val SLOW_SPEED = 10f
private const val MEDIUM_SPEED = 30f
private const val FAST_SPEED = 60f

/** 按「当前速度占据本轮峰值的比例」取色 */
internal fun tokenSpeedRatioColor(peakRatio: Float): Color =
    lerpThrough(peakRatio, SLOW_RATIO, MEDIUM_RATIO, FAST_RATIO)

/** 按绝对速度（tok/s）取色 */
internal fun tokenSpeedColor(tokensPerSecond: Float): Color =
    lerpThrough(tokensPerSecond, SLOW_SPEED, MEDIUM_SPEED, FAST_SPEED)

/** 三档之间线性插值：低段灰→冰蓝，高段冰蓝→红 */
private fun lerpThrough(value: Float, slow: Float, medium: Float, fast: Float): Color = when {
    value <= slow -> TokenSpeedSlowColor
    value < medium -> lerp(
        TokenSpeedSlowColor,
        TokenSpeedMediumColor,
        (value - slow) / (medium - slow),
    )

    value < fast -> lerp(
        TokenSpeedMediumColor,
        TokenSpeedFastColor,
        (value - medium) / (fast - medium),
    )

    else -> TokenSpeedFastColor
}

/**
 * 速度圆点：颜色是速度的唯一提示，不再写 tok/s 文字。
 *
 * 位置一直占住（只有颜色和透明度变化），速度还没来时不画，避免第一次请求结束时
 * 圆点出现把旁边的数字挤动一下；颜色变化走同一档过渡，不会硬切。
 */
@Composable
internal fun TokenSpeedDot(
    speed: Float?,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    var lastSpeed by remember { mutableStateOf<Float?>(null) }
    SideEffect { if (speed != null) lastSpeed = speed }
    val shown = speed ?: lastSpeed
    val dotColor by animateColorAsState(
        targetValue = shown?.let(::tokenSpeedColor) ?: TokenSpeedSlowColor,
        animationSpec = tween(durationMillis = 420),
        label = "tokenSpeedDotColor",
    )
    val dotAlpha by animateFloatAsState(
        targetValue = if (shown == null) 0f else 1f,
        animationSpec = tween(durationMillis = 420),
        label = "tokenSpeedDotAlpha",
    )
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { alpha = dotAlpha }
            .background(color = dotColor, shape = CircleShape),
    )
}
