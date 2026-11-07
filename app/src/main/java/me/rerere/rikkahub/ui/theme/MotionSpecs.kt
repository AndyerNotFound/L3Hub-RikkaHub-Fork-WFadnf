package me.rerere.rikkahub.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import me.rerere.rikkahub.data.datastore.AnimationSpeed

/** 当前这一帧该用哪一档动画速度，由设置页写入、RouteActivity 提供。 */
val LocalAnimationSpeed = compositionLocalOf { AnimationSpeed.SYSTEM }

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
