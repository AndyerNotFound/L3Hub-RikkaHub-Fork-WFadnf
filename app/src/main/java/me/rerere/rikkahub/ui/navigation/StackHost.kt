package me.rerere.rikkahub.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.FaultyDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.animation.Direction
import com.arkivanov.decompose.extensions.compose.stack.animation.StackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.StackAnimator
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.plus
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.androidPredictiveBackAnimatableV1
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.predictiveBackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.scale
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimator
import com.arkivanov.essenty.backhandler.BackHandler
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.theme.AlphaEasing
import me.rerere.rikkahub.ui.theme.FancyTransitionEasing
import me.rerere.rikkahub.ui.theme.PointToPointEasing
import me.rerere.rikkahub.ui.theme.rememberEffectsSpec
import me.rerere.rikkahub.ui.theme.rememberSpatialSpec
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 页面转场引擎，ImageToolbox 的 `toolboxPredictiveBackAnimation` 一比一：
 *
 * - 预测性返回手势进行中由 decompose 直接吃进度（系统 V1 手感），松手完成/取消后交给 fallback；
 * - fallback 是 decompose 内置 stackAnimation 的 fade + slide + scale 三件套，
 *   曲线取 MotionEasing 的三条基线：透明度 Alpha、位移 FancyTransition（尾段轻微过冲）、
 *   变形 PointToPoint。时长由调用侧传「动画速度」档位，三条同时开始、同时结束。
 *
 * 这里没有任何逐页簿记和全局前进/返回分类：转场打断、快速连续切换全部由
 * decompose 内置实现处理，行为与 ImageToolbox 完全一致。
 *
 * 唯一的例外是侧边栏：[Screen.Sidebar] 与聊天页之间走 [sharedAxisX]，理由见那个函数。
 */
@OptIn(ExperimentalDecomposeApi::class, FaultyDecomposeApi::class)
fun <C : Any, T : Any> l3HubStackAnimation(
    backHandler: BackHandler,
    onBack: () -> Unit,
    durationMillis: Int = 300,
    /**
     * 这一对页面是否走 Shared Axis X 而不是默认的 fade + slide + scale。
     *
     * 判据留在调用侧（那里才知道哪两个配置是聊天页与侧边栏），这里只负责把选中的
     * 动画交给 Decompose。入参是（新页配置、旧页配置、方向）。
     */
    useSharedAxisX: (C, C, Direction) -> Boolean = { _, _, _ -> false },
): StackAnimation<C, T> = predictiveBackAnimation(
    backHandler = backHandler,
    onBack = onBack,
    // 两参 selector 才能同时看到这一对页面，单参那个分不出「聊天↔侧边栏」和
    // 「聊天↔设置」。它带 FaultyDecomposeApi：内部用 movableContentOf，
    // Compose 那边有已知缺陷；这里只用来选动画，不搬动内容，风险与原来一致。
    fallbackAnimation = stackAnimation(
        disableInputDuringAnimation = true,
        selector = { child, otherChild, direction ->
            if (useSharedAxisX(child.configuration, otherChild.configuration, direction)) {
                sharedAxisXAnimator(durationMillis = durationMillis)
            } else {
                // 默认三件套：透明度 Alpha、位移 FancyTransition、变形 PointToPoint
                fade(tween(durationMillis, easing = AlphaEasing)) +
                    slide(tween(durationMillis, easing = FancyTransitionEasing)) +
                    scale(tween(durationMillis, easing = PointToPointEasing))
            }
        },
    ),
    selector = { backEvent, _, _ -> androidPredictiveBackAnimatableV1(backEvent) },
)

/**
 * 设置右列详情栈用：同一套 fade + slide + scale，但不接预测性返回——
 * 右列的返回键由 RouteActivity 的 BackCallback 接管，手势进度不走栈动画。
 */
@OptIn(ExperimentalDecomposeApi::class)
fun <C : Any, T : Any> l3HubDetailAnimation(
    durationMillis: Int = 300,
): StackAnimation<C, T> = stackAnimation(
    fade(tween(durationMillis, easing = AlphaEasing)) +
        slide(tween(durationMillis, easing = FancyTransitionEasing)) +
        scale(tween(durationMillis, easing = PointToPointEasing)),
)

/**
 * Shared Axis X：两页沿同一条水平轴一起移动，用位移表达「它们在同一条层级序列上」。
 *
 * 和 Material 的 `MaterialSharedAxis(X, forward)` 是同一条定义。侧边栏在聊天页左边，
 * 所以打开它是 backward——侧边栏从左侧进入、聊天页向右退。
 *
 * 为什么手写而不直接用 material3 的 `HorizontalSharedAxisNavigationTransitions`：
 * 那个返回 `ContentTransform`（给 `AnimatedContent` 用的），而 Decompose 的
 * `Children(animation = ...)` 要 `StackAnimator`；并且它在 material3 的
 * `1.5.0-beta01` API 快照里还没有，本仓库锁的是 `1.5.0-rc01`，不能赌。
 *
 * 方向不用判：Decompose 把方向编码进了 factor 的符号——
 * `ENTER_FRONT` 1→0、`EXIT_FRONT` 0→1、`ENTER_BACK` -1→0、`EXIT_BACK` 0→-1。
 * 对左侧页面整体取负即可：进场从左边来、退场往左边走，另一页反向。
 */
private fun sharedAxisXAnimator(durationMillis: Int): StackAnimator =
    stackAnimator(animationSpec = tween(durationMillis, easing = FancyTransitionEasing)) { factor, _, content ->
        content(Modifier.sharedAxisOffset(factor = -factor))
    }

/**
 * 按 factor 横向平移 + 跟随透明度。
 *
 * factor 为 0 时完全在位、完全不透明；为 ±1 时整页移出一个屏宽、完全透明。
 * 位移在 layout 阶段算，透明度在绘制阶段算，两者都不进组合。
 */
private fun Modifier.sharedAxisOffset(factor: Float): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(x = (placeable.width * factor).roundToInt(), y = 0)
        }
    }.graphicsLayer {
        alpha = (1f - abs(factor)).coerceIn(0f, 1f)
    }

/** 这一对页面里只要有一边是侧边栏，就走 Shared Axis X。 */
internal fun isSidebarPair(newScreen: Screen, oldScreen: Screen): Boolean =
    newScreen is Screen.Sidebar || oldScreen is Screen.Sidebar

/**
 * Fade through：旧内容先淡出，新内容再淡入，两段不重叠。
 *
 * 用在「同一层里换内容」的场合——四个 Tab 之间没有前后方向关系，
 * 用横向或竖向位移会暗示一条并不存在的序列。fade through 只表达「这块内容换掉了」。
 *
 * 两段时长按 M3 的比例：淡出短（约 90ms）、淡入长（约 210ms），
 * 中间有一小段两边都不可见的空档，这样不会看成一次交叉淡入。
 *
 * 不用 @Composable：AnimatedContent 的 transitionSpec 是普通 lambda，
 * 里面调不了 @Composable；这里的两条 spec 是固定时长，本来也不需要读主题。
 */
internal fun fadeThroughTransition(): ContentTransform {
    // 两段的时长按 M3 的 fade through 比例：淡出 90ms、淡入 210ms，
    // 淡入用 delay 压到淡出结束之后才开始，中间留一小段两边都不可见的空档。
    // 不延迟的话两个淡变会在时间上重叠，看起来就是普通的交叉淡入。
    val outSpec = tween<Float>(durationMillis = 90, easing = LinearOutSlowInEasing)
    val inSpec = tween<Float>(durationMillis = 210, delayMillis = 90, easing = FastOutSlowInEasing)
    // togetherWith 的接收者是进场那一段，写反了类型就对不上
    return fadeIn(inSpec) togetherWith fadeOut(outSpec)
}

/**
 * Shared Axis Y：新内容从下方进来、旧内容往上方退。
 *
 * 和 [sharedAxisX] 同一条定义，只是轴换成竖向——用于「同一个页面里换内容」，
 * 比如切换会话时只换消息区。Z 轴不动，所以两段内容不会互相遮挡，
 * 观感是「这一层内容换掉了，层级没变」。
 *
 * 方向按 Material 的 SharedAxis：
 * - 新内容从下方进入（`slideInVertically` 起点 +1/4 屏高），旧内容往上方退（-1/4）；
 * - 两者都同时淡入淡出，位移和透明度用同一条曲线。
 *
 * 位移量取 1/4 而不是整屏：整屏位移在消息这种长列表上会显得太快、
 * 像翻页而不是换内容；1/4 是 Material 里 fade through / shared axis 的常规幅度。
 *
 * 两条 spec 由调用侧从主题取好再传进来：`transitionSpec` 是普通 lambda，
 * 里面调不了 @Composable，档位只能在外面读。
 */
internal fun sharedAxisYTransition(
    spatialSpec: FiniteAnimationSpec<IntOffset>,
    effectsSpec: FiniteAnimationSpec<Float>,
): ContentTransform {
    return (
        slideInVertically(spatialSpec) { height -> height / 4 } + fadeIn(effectsSpec)
    ) togetherWith (
        slideOutVertically(spatialSpec) { height -> -height / 4 } + fadeOut(effectsSpec)
    )
}
