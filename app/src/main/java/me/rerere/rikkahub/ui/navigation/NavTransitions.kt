package me.rerere.rikkahub.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.rememberNavInteractionDecorator
import me.rerere.rikkahub.ui.theme.rememberSpatialSpec

/**
 * 一整套页面转场规格：中列、右列两个 `NavDisplay` 共用同一份，参数不会各写各的。
 *
 * 三条规矩，写在类型里而不是散在调用点上：
 *
 * - 进场按栈顶那一页的 `Screen.Chat.enterFromTop` 决定从上方还是下方落下，被盖住的页面原地不动；
 * - 返回只有一种：整页向下滑出，底下那页原地露出，不淡入也不淡出；
 * - 手势返回跟手后的收尾与普通返回同参（[predictivePop] 直接委托 [pop]）。
 */
@Immutable
class NavTransitionSpecs internal constructor(
    /** 进场：新页从一侧滑入，底下那页不动。 */
    val enter: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform,
    /** 返回：整页下滑出。 */
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
    // 页面滑入/滑出用 expressive 的空间曲线（有加速有减速），默认那个弹簧偏匀速，看起来像线性
    val slideSpec = rememberSpatialSpec<IntOffset>()
    val currentSlideSpec = rememberUpdatedState(slideSpec)
    return remember {
        // 返回只有一种：整页向下滑出，底下的页面原地露出，不淡入也不淡出
        val pop: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
            EnterTransition.None togetherWith
                slideOutVertically(animationSpec = currentSlideSpec.value) { it }
        }
        NavTransitionSpecs(
            enter = {
                // nav3 1.2 起 target 包在 Scene 里，页面 key 在 Scene.key——
                // 直接 `targetState as? Screen` 永远是 null，enterFromTop 会整个失效
                val target = (targetState as? Scene<*>)?.key as? Screen
                val enterFromTop = (target as? Screen.Chat)?.enterFromTop
                // 侧边栏点进来的会话：抽屉先收回去，会话页从点击项那一侧落下来
                slideInVertically(animationSpec = currentSlideSpec.value) {
                    if (enterFromTop == true) -it else it
                } togetherWith ExitTransition.None
            },
            pop = pop,
        )
    }
}

/**
 * 两个 `NavDisplay` 的 entry 装饰器清单，一处实现：状态保存、ViewModel 存储、
 * 转场期间的输入拦截与状态回写（见 `NavInputGuard.kt`）。
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
    // 转场期间的点击拦截与状态回写合在一处：离场页仍然组合着、也仍然吃点击
    // （nav3 1.2.0 的已知问题），同时把「转场还在不在播」回写给 Navigator，返回据此排队
    val interaction: NavEntryDecorator<NavKey> = rememberNavInteractionDecorator(navigator)
    return remember(saveableState, viewModelStore, interaction) {
        listOf(saveableState, viewModelStore, interaction)
    }
}
