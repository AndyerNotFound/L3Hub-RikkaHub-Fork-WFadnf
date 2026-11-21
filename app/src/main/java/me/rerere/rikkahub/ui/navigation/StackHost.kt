package me.rerere.rikkahub.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.arkivanov.decompose.Child
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.animation.StackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.androidPredictiveBackAnimatableV1
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.predictiveBackAnimation
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.essenty.backhandler.BackHandler
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.hooks.LocalNavEntryPopped
import me.rerere.rikkahub.ui.hooks.usesContainerTransform
import me.rerere.rikkahub.ui.theme.AlphaEasing
import me.rerere.rikkahub.ui.theme.PointToPointEasing
import me.rerere.rikkahub.ui.theme.rememberSlideSpec

/** 滑入页进场 / 退场的缩放端点，与源工程 decompose `scale` 的默认值一致。 */
private const val PAGE_SCALE = 0.9f

/**
 * 页内 hero 元素（`sharedBounds` / `sharedElement`）需要的动画 scope。
 *
 * decompose 默认的 `Children` + `stackAnimation` 用 Box + Modifier 手算动画，
 * 不提供任何 AnimatedVisibilityScope；这里由 [HeroStackAnimation] 给每个 child
 * 套一层 `AnimatedVisibility` 后提供，hero 层因此不用换 API。
 */
val LocalNavAnimatedVisibilityScope: ProvidableCompositionLocal<AnimatedVisibilityScope> =
    staticCompositionLocalOf {
        error("No nav animation scope provided — pages must be rendered inside HeroStackAnimation")
    }

/**
 * 页面转场引擎（接 decompose `Children` 的自定义 [StackAnimation]）。
 *
 * 与 nav3 `NavDisplay` 的全局 scene 簿记不同，每个页面持有自己的一份
 * `AnimatedVisibility`：前进转场进行中再按返回、返回手势中途取消、快速连续切换，
 * 都只是各页把自己的进出动画 retarget 一下，没有全局「前进 / 返回」分类可以出错，
 * 也就没有打断后硬切的那一类 bug。
 *
 * 条目三态：
 * - [Mode.ENTER] 当前栈顶：进场（返回揭出时进场为 None，原地出现）；
 * - [Mode.COVERED] 前进时被盖住的上一页：原地不动，等新页落定后直接移出组合；
 * - [Mode.EXIT] 返回时离场的页：播完退出动画再移除。
 *
 * 转场规格与旧 `NavTransitions.kt` 同源：
 * - 按 `Screen.usesContainerTransform()` 分两类：容器变换页 display 级只有淡入淡出，
 *   位移交给页内 sharedBounds；滑入页是 fade + slide + scale 三件套，返回是逆动画；
 * - 被盖住的页面原地不动；三条曲线的时长都跟着设置里的「动画速度」走。
 */
class HeroStackAnimation<C : Any, T : Any>(
    private val screenOf: (T) -> Screen?,
) : StackAnimation<C, T> {

    private enum class Mode { ENTER, EXIT, COVERED }

    private class Item<C : Any, T : Any>(
        val key: Any,
        val child: Child.Created<C, T>,
        val mode: Mode,
        val pop: Boolean,
    )

    @Composable
    override fun invoke(
        stack: ChildStack<C, T>,
        modifier: Modifier,
        content: @Composable (child: Child.Created<C, T>) -> Unit,
    ) {
        val items = remember { mutableStateMapOf<Any, Item<C, T>>() }
        var previousActiveKey by remember { mutableStateOf<Any?>(null) }
        var previousSize by remember { mutableStateOf(0) }
        var previousBackKeys by remember { mutableStateOf<Set<Any>>(emptySet()) }

        val activeKey = stack.active.key
        if (activeKey != previousActiveKey) {
            val isPop = stack.items.size < previousSize && activeKey in previousBackKeys
            val previousActive = previousActiveKey?.let(items::get)
            // 正在播退出 / 被盖住的条目留在组合里把该做的事做完；
            // 回归到仍在组合里的 key（快速返回 / 快速前进）时原地复活，不重播进场
            val kept = items.values.filter { it.key != activeKey && it.key != previousActiveKey }
            val next = buildMap {
                kept.forEach { put(it.key, it) }
                previousActive?.let {
                    if (isPop) {
                        put(it.key, Item(it.key, it.child, Mode.EXIT, pop = true))
                    } else {
                        put(it.key, Item(it.key, it.child, Mode.COVERED, pop = false))
                    }
                }
                put(activeKey, Item(activeKey, stack.active, Mode.ENTER, pop = isPop))
            }
            items.clear()
            items.putAll(next)
        }
        previousActiveKey = activeKey
        previousSize = stack.items.size
        previousBackKeys = stack.backStack.mapTo(HashSet()) { it.key }

        Box(modifier = modifier) {
            // 绘制顺序：被盖住的原页 < 栈顶 < 离场页（返回时离场页盖在上面滑走）
            items.values
                .sortedBy { item ->
                    when (item.mode) {
                        Mode.COVERED -> 0
                        Mode.ENTER -> 1
                        Mode.EXIT -> 2
                    }
                }
                .forEach { item ->
                    key(item.key) {
                        AnimatedChild(
                            item = item,
                            onExited = { items.remove(item.key) },
                            onSettled = {
                                val coveredKeys = items.entries
                                    .filter { it.value.mode == Mode.COVERED }
                                    .map { it.key }
                                coveredKeys.forEach(items::remove)
                            },
                            content = content,
                        )
                    }
                }

            // 转场期间挡掉指针事件（对应 decompose stackAnimation 的
            // disableInputDuringAnimation 默认行为）：离场页不再吃点击；
            // 预测性返回手势走 Activity 的 dispatcher，不受这层影响
            if (items.size > 1) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .zIndex(3f)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial)
                                        .changes
                                        .forEach { it.consume() }
                                }
                            }
                        },
                )
            }
        }
    }

    @Composable
    private fun AnimatedChild(
        item: Item<C, T>,
        onExited: () -> Unit,
        onSettled: () -> Unit,
        content: @Composable (child: Child.Created<C, T>) -> Unit,
    ) {
        val screen = remember(item.child.instance) { screenOf(item.child.instance) }
        val container = screen != null && screen.usesContainerTransform()
        val fromTop = (screen as? Screen.Chat)?.enterFromTop == true

        // 曲线在转场开始的那一刻才读：改「动画速度」不重播正在进行的转场
        val slideSpec = rememberSlideSpec<IntOffset>()
        val fadeSpec = rememberSlideSpec<Float>(easing = AlphaEasing)
        val scaleSpec = rememberSlideSpec<Float>(easing = PointToPointEasing)

        val enter: EnterTransition = when {
            item.mode != Mode.ENTER -> EnterTransition.None
            item.pop -> EnterTransition.None
            container -> fadeIn(fadeSpec)
            else ->
                fadeIn(fadeSpec) +
                    slideInVertically(slideSpec) { if (fromTop) -it else it } +
                    scaleIn(scaleSpec, initialScale = PAGE_SCALE)
        }
        val exit: ExitTransition = when {
            item.mode != Mode.EXIT -> ExitTransition.None
            container -> fadeOut(fadeSpec)
            else ->
                fadeOut(fadeSpec) +
                    slideOutVertically(slideSpec) { if (fromTop) -it else it } +
                    scaleOut(scaleSpec, targetScale = PAGE_SCALE)
        }

        val visible = remember(item.key) {
            MutableTransitionState(item.mode != Mode.EXIT).apply {
                targetState = item.mode != Mode.EXIT
            }
        }
        LaunchedEffect(item.mode) {
            visible.targetState = item.mode != Mode.EXIT
        }

        AnimatedVisibility(
            visibleState = visible,
            modifier = Modifier.zIndex(item.zOrder()),
            enter = enter,
            exit = exit,
        ) {
            CompositionLocalProvider(
                LocalNavAnimatedVisibilityScope provides this,
                LocalNavEntryPopped provides (item.mode == Mode.EXIT),
            ) {
                content(item.child)
            }
        }

        // 退出动画播完才移除；栈顶落定后把被盖住的页面移出组合
        LaunchedEffect(item.mode, visible.isIdle) {
            when (item.mode) {
                Mode.EXIT -> if (visible.isIdle && !visible.targetState) onExited()
                Mode.ENTER -> if (visible.isIdle && visible.targetState) onSettled()
                Mode.COVERED -> Unit
            }
        }
    }

    private fun Item<C, T>.zOrder(): Float = when (mode) {
        Mode.COVERED -> 0f
        Mode.ENTER -> 1f
        Mode.EXIT -> 2f
    }
}

/**
 * 预测性返回 + 逐页转场的总装（ImageToolbox 的 `toolboxPredictiveBackAnimation` 同款）：
 *
 * 手势进行中由 decompose 的 predictiveBackAnimation 直接吃进度（系统 V1 手感），
 * 松手完成/取消后交给 fallback——[HeroStackAnimation] 播各自的进出。
 */
@OptIn(ExperimentalDecomposeApi::class)
fun <C : Any, T : Any> l3HubStackAnimation(
    backHandler: BackHandler,
    onBack: () -> Unit,
    screenOf: (T) -> Screen?,
): StackAnimation<C, T> = predictiveBackAnimation(
    backHandler = backHandler,
    onBack = onBack,
    fallbackAnimation = HeroStackAnimation(screenOf),
    selector = { backEvent, _, _ -> androidPredictiveBackAnimatableV1(backEvent) },
)
