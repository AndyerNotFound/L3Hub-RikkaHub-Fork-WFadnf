package me.rerere.rikkahub.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.LocalNavEntryPopped
import me.rerere.rikkahub.ui.hooks.usesContainerTransform
import me.rerere.rikkahub.ui.theme.rememberSlideSpec
import me.rerere.rikkahub.ui.theme.rememberSpatialSpec

/**
 * 一整套页面转场规格：中列、右列两个 `NavDisplay` 共用同一份，参数不会各写各的。
 *
 * 三条规矩，写在类型里而不是散在调用点上：
 *
 * - 页面按 `Screen.usesContainerTransform()` 分两类，各自的进出互为逆：
 *   容器变换页从卡片 / 选项卡展开，返回收回那一块，进出都不位移；
 *   其余页面（会话、设置首页）从下方（`Screen.Chat.enterFromTop` 时为上方）滑入，返回原路滑出；
 * - 被盖住的页面用 `ExitTransition.None`，原地不动；
 * - 手势返回跟手后的收尾与普通返回同参（[predictivePop] 直接委托 [pop]）。
 */
@Immutable
class NavTransitionSpecs internal constructor(
    /** 进场：新页从一侧滑入，底下那页不动。 */
    val enter: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform,
    /** 返回：容器变换页收回条目，滑入页原路滑出。 */
    val pop: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform,
) {
    /**
     * 预测性返回手势收尾：忽略系统传入的滑动边缘，直接委托 [pop]。
     *
     * nav3 的 predictive spec 比普通 pop 多一个 SwipeEdge Int 参数。这里保留相同的函数形状，
     * 实现仍只有 [pop] 一份，普通返回与手势收尾不会各写一套参数。
     */
    val predictivePop: AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform
        get() = { _ -> pop.invoke(this) }
}

/**
 * 造一份 [NavTransitionSpecs]。lambda 只造一次，曲线在转场开始的那一刻才读：
 * 用户改「动画速度」不需要重建 spec 对象，正在播的转场也不会中途换曲线。
 */
@Composable
fun rememberNavTransitionSpecs(): NavTransitionSpecs {
    // 页面滑入用当前档位的空间曲线（有加速有减速），默认那个弹簧偏匀速，看起来像线性
    val slideSpec = rememberSpatialSpec<IntOffset>()
    val currentSlideSpec = rememberUpdatedState(slideSpec)
    // 容器变换页的窗口时长：进出都不位移，但离场页要留到容器收回条目为止，进场也要留够
    // 被盖住那页的存活时间供 sharedBounds 配对。时长与「动画速度」三档同源。
    val fadeSpec = rememberSlideSpec<Float>()
    val currentFadeSpec = rememberUpdatedState(fadeSpec)
    return remember {
        val pop: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
            // 离场的那一页在 initialState 这一侧；nav3 1.2 起 key 包在 Scene 里
            val leaving = (initialState as? Scene<*>)?.key as? Screen
            if (leaving != null && leaving.usesContainerTransform()) {
                // 从卡片 / 选项卡展开进来的页面：退场原路收回那一块。位移交给页面自己的
                // sharedBounds，display 级只留一个不位移的窗口，给收回的容器变换留出播放时间
                EnterTransition.None togetherWith fadeOut(animationSpec = currentFadeSpec.value)
            } else {
                // 滑入的页面：从哪一侧进来就往哪一侧滑回去——进场动画的逆
                val fromTop = (leaving as? Screen.Chat)?.enterFromTop == true
                EnterTransition.None togetherWith slideOutVertically(
                    animationSpec = currentSlideSpec.value,
                ) { if (fromTop) -it else it }
            }
        }
        NavTransitionSpecs(
            enter = {
                // nav3 1.2 起 target 包在 Scene 里，页面 key 在 Scene.key——
                // 直接 `targetState as? Screen` 永远是 null，enterFromTop 会整个失效
                val target = (targetState as? Scene<*>)?.key as? Screen
                if (target != null && target.usesContainerTransform()) {
                    // 容器变换页：位移由页面自己的 sharedBounds 演（从卡片展开），
                    // display 级只给一段同时长的淡入——被盖住那页因此留在组合里能配上，
                    // 配对没成时也是淡入而不是硬切
                    fadeIn(animationSpec = currentFadeSpec.value) togetherWith ExitTransition.None
                } else {
                    // 侧边栏点进来的会话：抽屉先收回去，会话页从点击项那一侧落下来
                    val enterFromTop = (target as? Screen.Chat)?.enterFromTop
                    slideInVertically(animationSpec = currentSlideSpec.value) {
                        if (enterFromTop == true) -it else it
                    } togetherWith ExitTransition.None
                }
            },
            pop = pop,
        )
    }
}

/**
 * 告诉页面「自己正被弹出离场」。
 *
 * 只有一件事要知道：被弹出的那一页不再配对页内共享元素（见 [LocalNavEntryPopped]）。导航
 * 语义一点不碰——出栈规则全在 [Navigator] 里，这里只读栈内容来判断走的是「被新页盖住」还是
 * 「被弹出」。
 */
@Composable
private fun <T : Any> NavEntryPoppedDecorator(navigator: Navigator, entry: NavEntry<T>) {
    val leaving = LocalNavAnimatedContentScope.current.transition.targetState != EnterExitState.Visible
    val popped = leaving && !isInBackStack(navigator, entry)
    // 必须自己撑满：这一层如果按内容包裹，父级给的是 0..max 的约束，
    // 页面里那些 fillMaxSize 的子节点会跟着缩
    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalNavEntryPopped provides popped) {
            entry.Content()
        }
    }
}

/**
 * 这一页现在还在返回栈里吗。
 *
 * 离场那一页还在栈里 = 只是被新页盖住；不在栈里 = 被弹出（返回）。
 */
@Composable
private fun isInBackStack(navigator: Navigator, entry: NavEntry<*>): Boolean {
    val contentKey = entry.contentKey
    for (key in navigator.stackKeys) {
        if (remember(key) { NavEntry(key) {}.contentKey } == contentKey) return true
    }
    return false
}

/**
 * 两个 `NavDisplay` 的 entry 装饰器清单，一处实现：状态保存、ViewModel 存储、
 * 离场标记。
 *
 * 清单本身按 [navigator] 记住，重组不再重新装配；但**每个 `NavDisplay` 各调一次**，
 * 不共用同一份实例：`rememberSaveableStateHolderNavEntryDecorator` 内部是一个
 * `SaveableStateHolder`，两个 display 的栈在折叠/展开设置右列的那一帧可能同时持有同一个
 * contentKey，共用 holder 会让两边的页面状态互相顶掉。
 */
@Composable
fun rememberAppNavEntryDecorators(navigator: Navigator): List<NavEntryDecorator<NavKey>> {
    // 类型写在变量上而不是给调用加类型实参：这两个装饰器由 nav3 / lifecycle 提供，
    // 让 Kotlin 从期望类型推出元素类型，比猜它们的类型参数怎么写更稳
    val saveableState: NavEntryDecorator<NavKey> = rememberSaveableStateHolderNavEntryDecorator()
    val viewModelStore: NavEntryDecorator<NavKey> = rememberViewModelStoreNavEntryDecorator()
    val popped: NavEntryDecorator<NavKey> = remember(navigator) {
        NavEntryDecorator<NavKey> { entry -> NavEntryPoppedDecorator(navigator, entry) }
    }
    return remember(saveableState, viewModelStore, popped) {
        listOf(saveableState, viewModelStore, popped)
    }
}
