package me.rerere.rikkahub.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import me.rerere.rikkahub.data.datastore.AnimationSpeed
import me.rerere.rikkahub.data.datastore.MotionBlurSetting

/** 当前这一帧该用哪一档动画速度，由主题层（RikkahubTheme）写入；没有主题包着时按 SYSTEM 走。 */
val LocalAnimationSpeed = compositionLocalOf { AnimationSpeed.SYSTEM }

/** 当前生效的动态模糊参数，由主题层写入；没有主题包着时按默认值走。 */
val LocalMotionBlur = compositionLocalOf { MotionBlurSetting() }

/**
 * 动效是否开启，由主题层写入。
 *
 * [LocalAnimationSpeed] 把「关闭」压成了 SYSTEM，读档位的人分不出「跟随 Material」和「用户关了动效」，
 * 手写的 spec 于是照常播完——侧边栏滑了 300ms 才停下。开关单独放一个 local，手写动画先读它。
 */
val LocalAnimationsEnabled = compositionLocalOf { true }

/**
 * 模糊自己的收放曲线：时长跟设置里的「过渡时长」走，和位移动画各用各的档位。
 *
 * 各处带模糊的过渡原先各拿各的 spec，用户调不了节奏；统一到这里以后，
 * 设置页一改，卡片收放、滚动过渡、消息入场的模糊一起变快或变慢。
 * 关了动态模糊时直接 snap，模糊不会拖在收放后面。
 *
 * 进出共用这一条：退场不再比进场收紧，两端的模糊同时长、同曲线。
 */
@Composable
fun rememberMotionBlurSpec(): FiniteAnimationSpec<Float> {
    val setting = LocalMotionBlur.current
    if (!setting.enabled) return snap()
    return tween(
        durationMillis = setting.durationMillis.coerceAtLeast(1),
        easing = FastOutSlowInEasing,
    )
}

/**
 * 档位到 MotionScheme 的映射：material3 自己的动效（底部抽屉的收放、菜单、对话框遮罩、
 * 拖拽吸附）只读 [MaterialTheme.motionScheme]，不走下面这几个自定义 helper。
 *
 * 只挂 [androidx.compose.material3.MotionScheme.expressive] 的话，「动画速度」调成快 / 缓
 * 只影响我们手写的位移动画，弹窗还按原来的时长慢慢滑——所以三档在这里各给一套。
 * 数值与 [rememberSpatialSpec] / [rememberFastSpatialSpec] / [rememberEffectsSpec] 同源，
 * 这几个 helper 现在直接读当前 scheme，改这里等于一起改。
 */
private object FastMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.9f, stiffness = 900f)

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.95f, stiffness = 1400f)

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.9f, stiffness = 600f)

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 120)

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 90)

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 180)
}

private object GentleMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = 220f)

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = 420f)

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.75f, stiffness = 150f)

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 320)

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 240)

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 420)
}

/** 当前档位对应的 MotionScheme：SYSTEM 用 Material 的 expressive，其余两档用上面那两套。 */
fun motionSchemeOf(speed: AnimationSpeed): MotionScheme = when (speed) {
    AnimationSpeed.SYSTEM -> MotionScheme.expressive()
    AnimationSpeed.FAST -> FastMotionScheme
    AnimationSpeed.GENTLE -> GentleMotionScheme
}

/**
 * 页面转场、列表展开这类"位移"动画统一从这里取曲线。
 *
 * 直接读当前 [MotionScheme]：档位已经在 [motionSchemeOf] 里换成对应的 scheme，
 * material3 自己的动画与手写的位移取到的是同一组参数。
 * 关了动效时上层把 motionScheme 换成全 snap，这里不用再单独判断。
 */
@Composable
fun <T> rememberSpatialSpec(): FiniteAnimationSpec<T> {
    return MaterialTheme.motionScheme.defaultSpatialSpec()
}

/**
 * 淡入淡出、透明度变化这类"不位移"的动画统一从这里取。
 *
 * 和 [rememberSpatialSpec] 用同一套档位，免得用户把动画调快以后，
 * 位移已经收住了、淡入还按默认时长慢慢走。
 */
@Composable
fun <T> rememberEffectsSpec(): FiniteAnimationSpec<T> {
    return MaterialTheme.motionScheme.defaultEffectsSpec()
}

/**
 * 比默认更紧的位移动画：吸附、原位复位这类单向补位从这里取。
 *
 * 不用于进出场——收放与切换的退场一律与进场同参，两端取不同档位就是硬过渡。
 */
@Composable
fun <T> rememberFastSpatialSpec(): FiniteAnimationSpec<T> {
    return MaterialTheme.motionScheme.fastSpatialSpec()
}

/**
 * 侧边栏这类整块滑入滑出的位移动画：只做平移，不要 expressive 弹簧在落位时的那一下回弹。
 * 档位和页面转场共用，关掉动效时直接跳变。
 */
@Composable
fun <T> rememberSlideSpec(animated: Boolean = true): FiniteAnimationSpec<T> {
    if (!animated || !LocalAnimationsEnabled.current) return snap()
    return when (LocalAnimationSpeed.current) {
        AnimationSpeed.SYSTEM -> tween(durationMillis = 300, easing = FastOutSlowInEasing)
        AnimationSpeed.FAST -> tween(durationMillis = 180, easing = FastOutSlowInEasing)
        AnimationSpeed.GENTLE -> tween(durationMillis = 420, easing = FastOutSlowInEasing)
    }
}
