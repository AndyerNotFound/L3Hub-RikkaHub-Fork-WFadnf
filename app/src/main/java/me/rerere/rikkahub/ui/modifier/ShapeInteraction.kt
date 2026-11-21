package me.rerere.rikkahub.ui.modifier

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.rikkahub.ui.theme.LocalCornerThrottle
import me.rerere.rikkahub.ui.theme.motionFastFloatAsState

/**
 * 按下时在 [shape] 与 [pressedShape] 之间插值，松手弹回。取自 ImageToolbox 的
 * `shapeByInteraction`（T8RIN，Apache-2.0），去掉了它那份 settings 节流：
 * 这里的按压态只是一次按下 / 松开，不值得为它加一层 `throttleLatest`。
 *
 * 只对「圆角矩形类」形状做逐角插值——`Outline.Rounded` 之间可以按四个角半径线性过渡。
 * 遇到不是圆角矩形的形状（`CircleShape`、自定义 Path）就整块切换，不做路径变形：
 * 路径插值没有公共实现，硬做会在按下第一帧闪一下。
 *
 * 与 [PressScale] 的分工：缩放管"大小"，这个管"轮廓"。两个 modifier 可以叠在同一个组件上，
 * 一个走 `graphicsLayer.scaleX/Y`，一个走 `graphicsLayer.shape`。
 */
@Composable
fun rememberShapeByInteraction(
    shape: Shape,
    pressedShape: Shape,
    interactionSource: InteractionSource?,
    enabled: Boolean = true,
    throttleMs: Int = LocalCornerThrottle.current,
): Shape {
    if (!enabled || interactionSource == null) return shape
    val pressed = rememberPressed(interactionSource, throttleMs)
    val progress by motionFastFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        label = "shapeByInteraction",
    )
    return when {
        progress <= 0f -> shape
        progress >= 1f -> pressedShape
        else -> MorphShape(shape, pressedShape, progress)
    }
}

/**
 * 按住或获得焦点时返回 true；焦点也算按下，键盘 / 手柄操作才有同样的轮廓反馈。
 *
 * [throttleMs] 是设置里的「边角动画节流」：每个状态变化先立刻生效，随后 [throttleMs] 内的
 * 连续变化只在窗口结束时补最后那一个（`Channel.CONFLATED` 天然只留最新值）。
 * 连点时轮廓不会每一下都弹一遍，但也不会漏掉松开那一下。
 */
@Composable
private fun rememberPressed(interactionSource: InteractionSource, throttleMs: Int): Boolean {
    var pressed by remember(interactionSource) { mutableStateOf(false) }
    LaunchedEffect(interactionSource, throttleMs) {
        val changes = Channel<Boolean>(Channel.CONFLATED)
        launch {
            val presses = mutableSetOf<PressInteraction.Press>()
            var focusCount = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses.add(interaction)
                    is PressInteraction.Release -> presses.remove(interaction.press)
                    is PressInteraction.Cancel -> presses.remove(interaction.press)
                    is FocusInteraction.Focus -> focusCount++
                    is FocusInteraction.Unfocus -> focusCount = (focusCount - 1).coerceAtLeast(0)
                    else -> Unit
                }
                changes.trySend(presses.isNotEmpty() || focusCount > 0)
            }
        }
        for (next in changes) {
            if (next != pressed) pressed = next
            if (throttleMs > 0) delay(throttleMs.toLong())
        }
    }
    return pressed
}

/**
 * 两个形状之间按 [progress] 插值出来的形状。
 *
 * [progress] 会被夹到 0..1：按压曲线是 spring（`fastSpatialSpec`），尾段会过冲，
 * 过冲后的负半径会让 `RoundRect` 画出反向圆角。
 */
class MorphShape(
    private val from: Shape,
    private val to: Shape,
    progress: Float,
) : Shape {
    private val t: Float = progress.coerceIn(0f, 1f)

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val start = from.createOutline(size, layoutDirection, density)
        val end = to.createOutline(size, layoutDirection, density)
        if (start is Outline.Rounded && end is Outline.Rounded) {
            return Outline.Rounded(lerpRoundRect(start.roundRect, end.roundRect, t))
        }
        return if (t < 0.5f) start else end
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MorphShape) return false
        return from == other.from && to == other.to && t == other.t
    }

    override fun hashCode(): Int {
        var result = from.hashCode()
        result = 31 * result + to.hashCode()
        result = 31 * result + t.hashCode()
        return result
    }

    override fun toString(): String = "MorphShape(t=$t)"
}

private fun lerpRoundRect(start: RoundRect, end: RoundRect, t: Float): RoundRect = RoundRect(
    left = lerpFloat(start.left, end.left, t),
    top = lerpFloat(start.top, end.top, t),
    right = lerpFloat(start.right, end.right, t),
    bottom = lerpFloat(start.bottom, end.bottom, t),
    topLeftCornerRadius = lerpOffset(start.topLeftCornerRadius, end.topLeftCornerRadius, t),
    topRightCornerRadius = lerpOffset(start.topRightCornerRadius, end.topRightCornerRadius, t),
    bottomRightCornerRadius = lerpOffset(start.bottomRightCornerRadius, end.bottomRightCornerRadius, t),
    bottomLeftCornerRadius = lerpOffset(start.bottomLeftCornerRadius, end.bottomLeftCornerRadius, t),
)

private fun lerpFloat(start: Float, stop: Float, t: Float): Float = start + (stop - start) * t

private fun lerpOffset(start: Offset, stop: Offset, t: Float): Offset = Offset(
    x = lerpFloat(start.x, stop.x, t),
    y = lerpFloat(start.y, stop.y, t),
)
