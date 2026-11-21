package me.rerere.rikkahub.ui.modifier

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.AnimationSpeed
import me.rerere.rikkahub.ui.theme.LocalAnimationSpeed
import me.rerere.rikkahub.ui.theme.motionAnimationsEnabled

/**
 * 按压缩放的手感曲线。
 *
 * 不走 [androidx.compose.material3.MotionScheme]：它那几条 spring 阻尼都在 0.9 上下，
 * 落位时几乎不回弹，画面上只是「缩了一下」。手指底下要的是过冲再回来那一下，
 * 所以这里自己给阻尼——三档动画速度各一份，关掉动效时直接 snap。
 */
@Composable
fun rememberPressScaleSpec(): FiniteAnimationSpec<Float> {
    if (!motionAnimationsEnabled()) return snap()
    return when (LocalAnimationSpeed.current) {
        AnimationSpeed.SYSTEM -> spring(dampingRatio = 0.45f, stiffness = 750f)
        AnimationSpeed.FAST -> spring(dampingRatio = 0.55f, stiffness = 1200f)
        AnimationSpeed.GENTLE -> spring(dampingRatio = 0.4f, stiffness = 420f)
    }
}

/**
 * 按住时缩到 [scaleDown]，松手弹回 1。
 *
 * 只挂在父级：指针事件在 Main 阶段从子级往上传，子级的 clickable 先拿到，
 * 按下即触发的逻辑不会被打断；子级一旦开始滚动（消费了位移），这里的按压态跟着取消。
 *
 * [hapticFeedback] 默认关闭——震动是全局手感，不该由单个按钮私自决定。
 *
 * [restShape] / [pressedShape] 给轮廓也加一层按压反馈：按住时从前者插值到后者，松手弹回
 * （ImageToolbox 的 `shapeByInteraction`）。两个都给才会生效，且会顺带裁切到该形状——
 * 想把缩放和轮廓叠在一起，就不要再在调用点自己 `clip()`，否则裁在静止形状上，变形看不到。
 */
@Composable
fun Modifier.pressScale(
    scaleDown: Float = 0.94f,
    enabled: Boolean = true,
    hapticFeedback: Boolean = false,
    restShape: Shape? = null,
    pressedShape: Shape? = null,
): Modifier {
    val spec = rememberPressScaleSpec()
    val haptics = LocalHapticFeedback.current
    val onPress: (() -> Unit)? = remember(haptics, hapticFeedback) {
        if (hapticFeedback) {
            { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
        } else {
            null
        }
    }
    val scale = remember { Animatable(1f) }
    // 轮廓插值单独一条 Animatable：缩放与形状各按各的曲线跑，一个快了不会拖着另一个
    val morph = remember { Animatable(0f) }
    val morphEnabled = restShape != null && pressedShape != null
    val scope = rememberCoroutineScope()
    return this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            val rest = restShape
            val pressed = pressedShape
            if (rest != null && pressed != null) {
                // 0 时直接用静止形状，省掉每帧算一遍两个 Outline
                shape = if (morph.value <= 0f) rest else MorphShape(rest, pressed, morph.value)
                clip = true
            }
        }
        .pointerInput(enabled, scaleDown, spec) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    onPress?.invoke()
                    scope.launch { scale.animateTo(scaleDown, spec) }
                    if (morphEnabled) scope.launch { morph.animateTo(1f, spec) }
                    try {
                        tryAwaitRelease()
                    } finally {
                        // 按下期间被父级滚动抢走时这里会抛取消，复照样走一遍
                        scope.launch { scale.animateTo(1f, spec) }
                        if (morphEnabled) scope.launch { morph.animateTo(0f, spec) }
                    }
                }
            )
        }
}
