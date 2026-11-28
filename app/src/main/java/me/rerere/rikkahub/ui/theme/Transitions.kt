package me.rerere.rikkahub.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset

/**
 * 转场表：这一层回答的是「这是什么转场」，不是「多快、什么曲线」。
 *
 * [MotionSpecs.kt] 只管时长与曲线（`spatialSpec` / `effectsSpec` / `sizeSpec`）；
 * 这里是它们的语义封装。调用点写 `enter = revealEnter()`，
 * 而不是自己拼 `fadeIn(rememberEffectsSpec()) + expandVertically(rememberSizeSpec())`。
 *
 * ## 为什么单独一个文件
 *
 * 这套 helper 原来散在 [MotionSpecs.kt] 末尾，结果全仓只有 5 个调用点，
 * 而手写 `fadeIn(...)` + `fadeOut(...)` 的配对有二十多处 ——
 * 因为没人会去「时长曲线表」里找转场。名字带 `remember` 也让它读起来像状态而非过渡。
 *
 * ## 规则
 *
 * chat 包与 `ui/components/ai` 里不再直接写 `fadeIn` / `fadeOut` / `expandVertically` /
 * `shrinkVertically` / `slideInHorizontally` 这类组合，一律从这里取。
 * 需要新的过渡形态时往这个文件加一条，不要就地拼。
 *
 * ## 四条约束（与 [MotionSpecs.kt] 一致）
 *
 * - 进出同参：退场不得比进场短，两端取同一个 spec。
 * - 收放同侧：[revealEnter] 的 `expandFrom` 与 [revealExit] 的 `shrinkTowards`
 *   必须取同一侧。`expandVertically` / `shrinkVertically` 自己的默认值是 Bottom / Top，
 *   直接配对就会「展开从底边长、收起往顶上缩」，两端看着是两个动画。
 * - 不跟档位的手感（固定毫秒）已经按「统一跟随档位」的约定并入这里，
 *   调用点不再单独传 `rememberFixedSpec`。
 * - 关闭动效时主题把 scheme 换成全 snap，这一层不用重复判断开关。
 */

/**
 * 收放式显隐的进场：淡入 + 高度展开。
 *
 * 用于「这张卡 / 这块面板要出现了」：会话详情浮层、限额看板、列表分区、
 * 附件选择器的分组。不需要改变高度的显隐用 [fadeEnter]，不要用这个。
 *
 * @param expandFrom 从哪一侧长出来，必须与 [revealExit] 的 `shrinkTowards` 同侧
 */
@Composable
fun revealEnter(
    expandFrom: Alignment.Vertical = Alignment.Top,
): EnterTransition = fadeIn(rememberEffectsSpec()) +
    expandVertically(rememberSizeSpec(), expandFrom)

/** 收放式显隐的退场，与 [revealEnter] 同参同侧。 */
@Composable
fun revealExit(
    shrinkTowards: Alignment.Vertical = Alignment.Top,
): ExitTransition = fadeOut(rememberEffectsSpec()) +
    shrinkVertically(rememberSizeSpec(), shrinkTowards)

/** 横向收放的进场：侧栏、抽屉这类从左右进来的面板。与 [revealExitHorizontal] 同侧。 */
@Composable
fun revealEnterHorizontal(
    expandFrom: Alignment.Horizontal = Alignment.Start,
): EnterTransition = fadeIn(rememberEffectsSpec()) +
    expandHorizontally(rememberSizeSpec(), expandFrom)

/** 横向收放的退场，与 [revealEnterHorizontal] 同参同侧。 */
@Composable
fun revealExitHorizontal(
    shrinkTowards: Alignment.Horizontal = Alignment.Start,
): ExitTransition = fadeOut(rememberEffectsSpec()) +
    shrinkHorizontally(rememberSizeSpec(), shrinkTowards)

/**
 * 纯淡入：不改变尺寸的显隐 —— 菜单、提示条、遮罩、贴角按钮。
 *
 * 与 [revealEnter] 的区别只有一条：它不动高度。
 * 一块内容要「长出来」时用 [revealEnter]，否则用这个。
 */
@Composable
fun fadeEnter(): EnterTransition = fadeIn(rememberEffectsSpec())

/** 纯淡出，与 [fadeEnter] 同参。 */
@Composable
fun fadeExit(): ExitTransition = fadeOut(rememberEffectsSpec())

/**
 * `AnimatedContent` 的默认内容切换：进出同一条曲线、同一时长。
 *
 * 官方默认是 `fadeIn() togetherWith fadeOut()`，两侧各取 `tween(220)` 的常量，
 * 不认档位；关闭动效时也会淡入淡出。
 *
 * 注意它**不是** fade through：两段在时间上重叠，就是普通的交叉淡入。
 * 需要「先淡出、再淡入」的中间空档，用 navigation 包里的 `fadeThroughTransition()`。
 */
@Composable
fun contentSwitch(): ContentTransform =
    fadeIn(rememberEffectsSpec()) togetherWith fadeOut(rememberEffectsSpec())

/**
 * 「这一层出现了」的轻推：淡入 + 从下方挪进一小段（默认高度的 1/8）。
 *
 * 用在 Tab 切换时输入框的显隐 —— 它不是换页，整块滑入会看成翻页，
 * 只推一小段才像「这一层回来了」。位移只在进场有，退场只淡出：
 * 退场带位移就成了「收起来」，而这里要表达的是「换掉」。
 *
 * @param shiftDivisor 位移量 = 内容高度 / 这个值。8 是一档很轻的推动，
 *   2 就接近整块滑入了
 */
@Composable
fun nudgeEnter(
    shiftDivisor: Int = 8,
): EnterTransition = fadeIn(rememberEffectsSpec()) +
    slideInVertically(rememberSpatialSpec()) { it / shiftDivisor }

/**
 * 遮罩层：只淡，不位移，也不改变尺寸。
 *
 * 与 [fadeEnter] 的差别只在语义 —— 遮罩永远铺满、永远压在内容下面，
 * 单独起个名字是为了让调用点读得出「这是遮罩，不是一块内容」。
 */
@Composable
fun scrimEnter(): EnterTransition = fadeIn(rememberEffectsSpec())

/** 遮罩淡出，与 [scrimEnter] 同参。 */
@Composable
fun scrimExit(): ExitTransition = fadeOut(rememberEffectsSpec())

/**
 * 「换掉一个小控件」：淡入淡出 + 轻微缩放。
 *
 * 用在按钮在两种身份之间切换（发送 / 停止、麦克风 / 波形）这类不改变占位的地方。
 * 位移会很怪 —— 它原地换，只是换了内容。
 *
 * @param scale 起止缩放。0.96 是一档很轻的收放，1.0 就等于纯淡入
 */
@Composable
fun swapEnter(
    scale: Float = 0.96f,
): EnterTransition = fadeIn(rememberEffectsSpec()) +
    scaleIn(rememberSpatialSpec(), initialScale = scale)

/** 与 [swapEnter] 同参的退场。 */
@Composable
fun swapExit(
    scale: Float = 0.96f,
): ExitTransition = fadeOut(rememberEffectsSpec()) +
    scaleOut(rememberSpatialSpec(), targetScale = scale)

/**
 * 卡片内部一行的进出：滑走 + 淡 + 轻微缩放。
 *
 * 用在输入卡里语音行、附件行、编辑横幅这类「卡片高度不变、行内换内容」的地方。
 * 它比 [revealEnter] 多一段位移和缩放，因为卡片本身的高度由外层的
 * `motionContentSize` 负责，这一层只做行内过渡 —— 两边都改高度会打架。
 *
 * 位移方向：进场从下方滑进、退场往下方滑走，进出同侧，看起来是「换了一行」。
 */
@Composable
fun rowEnter(): EnterTransition {
    val slide = rememberSlideSpec<IntOffset>()
    val scale = rememberSlideSpec<Float>()
    return slideInVertically(slide) { it / 2 } +
        fadeIn(rememberEffectsSpec()) +
        scaleIn(scale, initialScale = 0.96f)
}

/** 卡片内部一行的退场，与 [rowEnter] 同参。 */
@Composable
fun rowExit(): ExitTransition {
    val slide = rememberSlideSpec<IntOffset>()
    val scale = rememberSlideSpec<Float>()
    return slideOutVertically(slide) { it / 2 } +
        fadeOut(rememberEffectsSpec()) +
        scaleOut(scale, targetScale = 0.96f)
}
