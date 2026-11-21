// Ported from ImageToolbox (https://github.com/T8RIN/ImageToolbox), Apache-2.0, Copyright (c) T8RIN (Malik Mukhametzyanov).
package me.rerere.rikkahub.ui.shape

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.StiffnessLow
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.CornerStyle
import me.rerere.rikkahub.ui.theme.LocalCornerScale
import me.rerere.rikkahub.ui.theme.LocalCornerStyle
import me.rerere.rikkahub.ui.theme.motionAnimationsEnabled

/**
 * 会在两个 [CornerBasedShape] 之间做插值的形状——源工程 `ShapeDefaults.AnimatedShape`。
 *
 * 与逐角 [MorphShape]（瞬时插值形状，靠一个进度值驱动）不同，这个类自己持有四个角的
 * [Animatable]，目标一变就各自 `animateTo`：卡片分组插入 / 删除条目、整组换轮廓、
 * 全宽开关切换时，四个角按各自的剩余距离收放，不会像换形状那样硬跳。
 *
 * 半径按像素存：`CornerSize` 可能是百分比，按当前尺寸算成 px 才能插值。
 */
@Stable
class AnimatedShape(
    initialShape: CornerBasedShape,
    private val style: CornerStyle,
    private val scale: Float,
    private val density: Density,
    private val animationSpec: FiniteAnimationSpec<Float>,
) : Shape {

    private var size: Size = Size(
        width = with(density) { 48.dp.toPx() },
        height = with(density) { 48.dp.toPx() },
    )

    private val halfHeight: Float get() = size.height / 2f

    private val topStart = Animatable(initialShape.topStart.toPx())
    private val topEnd = Animatable(initialShape.topEnd.toPx())
    private val bottomStart = Animatable(initialShape.bottomStart.toPx())
    private val bottomEnd = Animatable(initialShape.bottomEnd.toPx())

    private inline fun CornerSize.toPx() = toPx(size, density)

    private suspend inline fun ShapeAnimatable.animateTo(cornerSize: CornerSize) = animateTo(
        targetValue = cornerSize.toPx(),
        animationSpec = animationSpec,
    )

    // 弹簧会过冲：负半径画出来是反向圆角，超过半高会变成整圆以外的畸形
    private inline fun ShapeAnimatable.boundedValue() = value.fastCoerceIn(0f, halfHeight)

    suspend fun animateTo(targetShape: CornerBasedShape) = coroutineScope {
        launch { topStart.animateTo(targetShape.topStart) }
        launch { topEnd.animateTo(targetShape.topEnd) }
        launch { bottomStart.animateTo(targetShape.bottomStart) }
        launch { bottomEnd.animateTo(targetShape.bottomEnd) }
    }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        if (size.width > 1f && size.height > 1f) {
            this.size = size
        }
        return styledShape(
            style = style,
            topStart = CornerSize(topStart.boundedValue()),
            topEnd = CornerSize(topEnd.boundedValue()),
            bottomEnd = CornerSize(bottomEnd.boundedValue()),
            bottomStart = CornerSize(bottomStart.boundedValue()),
            // 强度在按下/交互那一层已经缩放过一次，这里按 1f 画，免得与外形叠加两次
            scale = 1f,
        ).createOutline(size, layoutDirection, density)
    }

    override fun toString(): String = "AnimatedShape(style=$style, scale=$scale)"
}

private typealias ShapeAnimatable = Animatable<Float, AnimationVector1D>

/**
 * 跟随 [currentShape] 变化的形状。
 *
 * 目标一变就把四个角各 animate 一段（弹簧，阻尼 0.4 / `StiffnessLow`，与源工程一致）；
 * 关掉动效时直接 `snap`，不会留一段几百毫秒的收角。
 *
 * 目标值放 [Channel.CONFLATED] 里再消费：连续重组（比如拖滑杆、连续插值出来的形状）
 * 只取最后一个，不会排队播完一串中间形状。
 */
@Composable
fun rememberAnimatedShape(
    currentShape: CornerBasedShape,
    style: CornerStyle = LocalCornerStyle.current,
    scale: Float = LocalCornerScale.current,
): AnimatedShape {
    val density = LocalDensity.current
    val animationSpec = if (motionAnimationsEnabled()) {
        remember { spring<Float>(dampingRatio = 0.4f, stiffness = StiffnessLow) }
    } else {
        remember { snap<Float>() }
    }

    val state = remember(style, scale, density, animationSpec) {
        AnimatedShape(
            initialShape = currentShape,
            style = style,
            scale = scale,
            density = density,
            animationSpec = animationSpec,
        )
    }

    val channel = remember { Channel<CornerBasedShape>(Channel.CONFLATED) }

    SideEffect { channel.trySend(currentShape) }

    // 前后台切换时补一次：在后台错过的目标值回来以后要追上
    LifecycleResumeEffect(Unit) {
        channel.trySend(currentShape)
        onPauseOrDispose { channel.trySend(currentShape) }
    }

    LaunchedEffect(state, channel) {
        for (target in channel) {
            val newTarget = channel.tryReceive().getOrNull() ?: target
            launch { state.animateTo(newTarget) }
        }
    }

    return state
}

/** 源工程 `ShapeDefaults.animateShape`：动画到 [targetValue]，不额外做交互跟踪。 */
@Composable
fun animateShape(
    targetValue: CornerBasedShape,
    style: CornerStyle = LocalCornerStyle.current,
    scale: Float = LocalCornerScale.current,
): Shape = rememberAnimatedShape(currentShape = targetValue, style = style, scale = scale)
