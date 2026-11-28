package me.rerere.rikkahub.ui.theme

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
        tween(durationMillis = 140, easing = FastOutSlowInEasing)

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
        tween(durationMillis = 100, easing = FastOutSlowInEasing)

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> =
        tween(durationMillis = 200, easing = FastOutSlowInEasing)

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 120)

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 90)

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 180)
}

private object GentleMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        tween(durationMillis = 320, easing = FastOutSlowInEasing)

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
        tween(durationMillis = 240, easing = FastOutSlowInEasing)

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> =
        tween(durationMillis = 420, easing = FastOutSlowInEasing)

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 320)

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 240)

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 420)
}

/**
 * 朴素档（默认）：不用弹簧，位移与淡入都是固定时长的缓动。
 *
 * Material 自带的 expressive scheme 带越界回弹，视觉上偏「活泼」；
 * 极简外观要的是位移到位就停，所以这里改成一档干脆的时长。
 */
private object PlainMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        tween(durationMillis = 200, easing = FastOutSlowInEasing)

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
        tween(durationMillis = 140, easing = FastOutSlowInEasing)

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> =
        tween(durationMillis = 280, easing = FastOutSlowInEasing)

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 160)
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 100)
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 220)
}

/** 当前档位对应的 MotionScheme：默认档用朴素曲线，其余两档只改时长。 */
fun motionSchemeOf(speed: AnimationSpeed): MotionScheme = when (speed) {
    AnimationSpeed.SYSTEM -> PlainMotionScheme
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
fun <T> rememberSlideSpec(
    animated: Boolean = true,
    // 曲线取 ImageToolbox 的 FancyTransitionEasing（尾段 3% 过冲）：整页滑到位以后
    // 再"顿"一下才停，这是那个工程最好认的手感。档位只管时长，三档共用同一条曲线。
    // 源工程那段转场是 fade + slide + scale 三条各一条曲线，所以曲线做成参数；
    // 时长仍由同一档给，三者同时开始、同时结束。
    easing: Easing = FancyTransitionEasing,
): FiniteAnimationSpec<T> {
    if (!animated || !LocalAnimationsEnabled.current) return snap()
    return tween(durationMillis = slideDurationMillis(), easing = easing)
}

/** 页面位移动画这一档的时长（毫秒）。fade / scale 跟它取同一个值。 */
@Composable
fun slideDurationMillis(): Int = when (LocalAnimationSpeed.current) {
    AnimationSpeed.SYSTEM -> 300
    AnimationSpeed.FAST -> 180
    AnimationSpeed.GENTLE -> 420
}

// ---------------------------------------------------------------------------
// 取用顺序
//
// 页面代码只认下面四层，任何一层都不自己写 tween / spring：
//
//   1. spec        —— 只描述「多快、什么曲线」：rememberSpatialSpec / rememberEffectsSpec /
//                     rememberFastSpatialSpec / rememberSizeSpec / rememberOffsetSpec /
//                     rememberFastOffsetSpec / rememberColorSpec / rememberSlideSpec
//   2. 固定节奏    —— 手感已经定死、不跟三档走的动画：rememberFixedSpec(duration, easing)；
//                     协程里 animateTo 用 fixedMotionSpec(enabled, duration, easing)
//   3. 状态动画    —— animate*AsState 的统一替代：motionFloatAsState / motionFastFloatAsState /
//                     motionDpAsState / motionColorAsState；容器高度用 Modifier.motionContentSize()
//   4. 组合过渡    —— 整块内容的进出场：见同目录的 Transitions.kt
//                     （revealEnter / revealExit、fadeEnter / fadeExit、contentSwitch 等）；
//                     带模糊的收放走 ui/modifier/MotionBlur.kt 里的 MotionExpandCollapse
//
// 四条约束：
//   - 档位统一从 MaterialTheme.motionScheme 读，关闭动效时主题把 scheme 换成全 snap，
//     spec 层因此不用重复判断开关。调用点直接读 scheme 也合法，与 helper 等价，
//     只是泛型要自己写——新代码优先用 helper。
//   - 固定节奏必须走第 2 层。裸写 tween 的地方关掉动效后照样播完，是最常见的一批漏网。
//   - 无限循环动画（呼吸、闪烁、扫光、自转）读 motionAnimationsEnabled()，关闭时连
//     rememberInfiniteTransition 一起不建：只不用它的值，它照样每帧申请下一帧。
//   - 进出同参、收放同侧：退场不得收紧，expandFrom 与 shrinkTowards 取同一侧。
// ---------------------------------------------------------------------------

/** 高度 / 宽度收放的空间曲线。省掉调用点写 `defaultSpatialSpec<IntSize>()` 这种泛型猜测。 */
@Composable
fun rememberSizeSpec(): FiniteAnimationSpec<IntSize> =
    MaterialTheme.motionScheme.defaultSpatialSpec()

/** 滑入滑出的位移曲线。 */
@Composable
fun rememberOffsetSpec(): FiniteAnimationSpec<IntOffset> =
    MaterialTheme.motionScheme.defaultSpatialSpec()

/** 比默认更紧的位移曲线：吸附、原位复位。不用于进出场。 */
@Composable
fun rememberFastOffsetSpec(): FiniteAnimationSpec<IntOffset> =
    MaterialTheme.motionScheme.fastSpatialSpec()

/**
 * 颜色过渡的曲线。
 *
 * 颜色不能走空间曲线：spring 在两端会过冲出不属于色板的颜色，深色主题上尤其明显。
 * 这里取档位的 effects 曲线，关闭动效时同样是 snap。
 */
@Composable
fun rememberColorSpec(): FiniteAnimationSpec<Color> =
    MaterialTheme.motionScheme.defaultEffectsSpec()

/**
 * 位移动画的统一入口，替代 `animateFloatAsState`。
 *
 * 官方默认值是 `spring()`，不认「动画速度」三档，关闭动效时也照样播完。
 */
@Composable
fun motionFloatAsState(
    targetValue: Float,
    label: String = "motion",
): State<Float> = animateFloatAsState(
    targetValue = targetValue,
    animationSpec = rememberSpatialSpec(),
    label = label,
)

/**
 * 比默认更紧的状态动画：选中项挤开、按压缩放这类手指底下的小位移。
 *
 * 与 [rememberFastSpatialSpec] 同档，别用 default：默认弹簧在 1.0 → 1.3 这种
 * 小跨度上会过冲一下，按钮看着像被弹了一下。
 */
@Composable
fun motionFastFloatAsState(
    targetValue: Float,
    label: String = "motion",
): State<Float> = animateFloatAsState(
    targetValue = targetValue,
    animationSpec = rememberFastSpatialSpec(),
    label = label,
)

/** 尺寸动画的统一入口，替代 `animateDpAsState`。 */
@Composable
fun motionDpAsState(
    targetValue: Dp,
    label: String = "motion",
): State<Dp> = animateDpAsState(
    targetValue = targetValue,
    animationSpec = rememberSpatialSpec(),
    label = label,
)

/** 颜色动画的统一入口，替代 `animateColorAsState`。 */
@Composable
fun motionColorAsState(
    targetValue: Color,
    label: String = "motion",
): State<Color> = animateColorAsState(
    targetValue = targetValue,
    animationSpec = rememberColorSpec(),
    label = label,
)

/**
 * [rememberFixedSpec] 的非组合版本：协程里 `animateTo(...)`、`snapTo(...)` 用。
 *
 * 协程里读不到 CompositionLocal，把 [LocalAnimationsEnabled] 在组合层读出来再传进来。
 */
fun <T> fixedMotionSpec(
    enabled: Boolean,
    durationMillis: Int,
    easing: Easing = FastOutSlowInEasing,
): FiniteAnimationSpec<T> = if (enabled) tween(durationMillis, easing = easing) else snap()

/**
 * 有固定节奏的动画：输入框展开、标题滚动、浮动球脉动这类手感已经定死的过渡。
 *
 * 时长与曲线照旧，只接通「关闭动效」这一道闸。原本直接写 `tween(...)` 的地方，
 * 关掉动效后照样播完——这些是最明显的一批漏网。
 */
@Composable
fun <T> rememberFixedSpec(
    durationMillis: Int,
    easing: Easing = FastOutSlowInEasing,
): FiniteAnimationSpec<T> = fixedMotionSpec(LocalAnimationsEnabled.current, durationMillis, easing)

/**
 * `Modifier.animateContentSize` 的统一入口。
 *
 * 官方默认是 `spring()`，不认「动画速度」三档，关闭动效时也照样播完。
 * 内容长高、缩短的容器都走这里，高度与内容刷新才不会各走各的曲线。
 */
@Composable
fun Modifier.motionContentSize(
    alignment: Alignment = Alignment.TopStart,
    finishedListener: ((initialValue: IntSize, targetValue: IntSize) -> Unit)? = null,
): Modifier = animateContentSize(
    animationSpec = rememberSizeSpec(),
    alignment = alignment,
    finishedListener = finishedListener,
)

/**
 * 当前是否允许播放动画。
 *
 * 有明确终点的动画不必读它——关闭动效时主题把 scheme 换成全 snap，曲线自己会停下。
 * 只有无限循环动画（呼吸、闪烁、波形）要读它把自己停住，否则关掉动效后它们还在转。
 */
@Composable
fun motionAnimationsEnabled(): Boolean = LocalAnimationsEnabled.current
