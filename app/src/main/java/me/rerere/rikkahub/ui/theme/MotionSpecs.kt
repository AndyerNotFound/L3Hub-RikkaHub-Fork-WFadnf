package me.rerere.rikkahub.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import me.rerere.rikkahub.data.datastore.AnimationSpeed
import me.rerere.rikkahub.data.datastore.MotionBlurSetting

/** 当前这一帧该用哪一档动画速度，由设置页写入、RouteActivity 提供。 */
val LocalAnimationSpeed = compositionLocalOf { AnimationSpeed.SYSTEM }

/** 当前生效的动态模糊参数，由主题层写入；没有主题包着时按默认值走。 */
val LocalMotionBlur = compositionLocalOf { MotionBlurSetting() }

/**
 * 模糊自己的收放曲线：时长跟设置里的「过渡时长」走，和位移动画各用各的档位。
 *
 * 各处带模糊的过渡原先各拿各的 spec，用户调不了节奏；统一到这里以后，
 * 设置页一改，卡片收放、滚动过渡、消息入场的模糊一起变快或变慢。
 * 关了动态模糊时直接 snap，模糊不会拖在收放后面。
 *
 * [scale] 给退出方向用：退场通常比进场收紧一些，传 0.75 左右即可。
 */
@Composable
fun rememberMotionBlurSpec(scale: Float = 1f): FiniteAnimationSpec<Float> {
    val setting = LocalMotionBlur.current
    if (!setting.enabled) return snap()
    return tween(
        durationMillis = (setting.durationMillis * scale).toInt().coerceAtLeast(1),
        easing = FastOutSlowInEasing,
    )
}

/**
 * 页面转场、列表展开这类"位移"动画统一从这里取曲线。
 *
 * SYSTEM 直接用 Material 的 expressive 空间曲线；另外两档换成固定弹簧，
 * 差别只体现在松紧：FAST 收得快，GENTLE 拖得久。
 * 关了动效时上层会把 [LocalAnimationSpeed] 固定成 SYSTEM，
 * 而那时的 motionScheme 已经是 snap，所以这里不用再单独判断。
 */
@Composable
fun <T> rememberSpatialSpec(): FiniteAnimationSpec<T> {
    return when (LocalAnimationSpeed.current) {
        AnimationSpeed.SYSTEM -> MaterialTheme.motionScheme.defaultSpatialSpec()
        AnimationSpeed.FAST -> spring<T>(dampingRatio = 0.9f, stiffness = 900f)
        AnimationSpeed.GENTLE -> spring<T>(dampingRatio = 0.8f, stiffness = 220f)
    }
}

/**
 * 淡入淡出、透明度变化这类"不位移"的动画统一从这里取。
 *
 * 和 [rememberSpatialSpec] 用同一套档位，免得用户把动画调快以后，
 * 位移已经收住了、淡入还按默认时长慢慢走。
 */
@Composable
fun <T> rememberEffectsSpec(): FiniteAnimationSpec<T> {
    return when (LocalAnimationSpeed.current) {
        AnimationSpeed.SYSTEM -> MaterialTheme.motionScheme.defaultEffectsSpec()
        AnimationSpeed.FAST -> tween<T>(durationMillis = 120)
        AnimationSpeed.GENTLE -> tween<T>(durationMillis = 320)
    }
}

/**
 * 收起、退出这类"收回去"的位移统一从这里取：比 [rememberSpatialSpec] 更紧，
 * 档位同样跟设置走，用户把动画调快以后退出不会还按默认时长慢慢收。
 */
@Composable
fun <T> rememberFastSpatialSpec(): FiniteAnimationSpec<T> {
    return when (LocalAnimationSpeed.current) {
        AnimationSpeed.SYSTEM -> MaterialTheme.motionScheme.fastSpatialSpec()
        AnimationSpeed.FAST -> spring<T>(dampingRatio = 0.95f, stiffness = 1400f)
        AnimationSpeed.GENTLE -> spring<T>(dampingRatio = 0.8f, stiffness = 420f)
    }
}

/**
 * 侧边栏这类整块滑入滑出的位移动画：只做平移，不要 expressive 弹簧在落位时的那一下回弹。
 * 档位和页面转场共用，关掉动效时直接跳变。
 */
@Composable
fun <T> rememberSlideSpec(animated: Boolean = true): FiniteAnimationSpec<T> {
    if (!animated) return snap()
    return when (LocalAnimationSpeed.current) {
        AnimationSpeed.SYSTEM -> tween(durationMillis = 300, easing = FastOutSlowInEasing)
        AnimationSpeed.FAST -> tween(durationMillis = 180, easing = FastOutSlowInEasing)
        AnimationSpeed.GENTLE -> tween(durationMillis = 420, easing = FastOutSlowInEasing)
    }
}
