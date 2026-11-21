package me.rerere.rikkahub.ui.hooks

import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import me.rerere.rikkahub.ui.context.Navigator

/**
 * 转场相关的两个装饰器合成一份，两个 `NavDisplay` 都从这里取，别各挂一套。
 *
 * 交互面只暴露两个结论给 [Navigator]：「转场还在不在播」与「是不是手势正倒向下一层」，
 * 出栈该不该排队由 Navigator 决定，这里不判断，也不改栈。
 */
@Composable
fun <T : Any> rememberNavInteractionDecorator(navigator: Navigator): NavEntryDecorator<T> =
    remember(navigator) {
        NavEntryDecorator<T> { entry ->
            // 两个子组合分开挂：转场状态每变一次只该重组它自己，不能连带整页重新调用一遍
            NavTransitionReport(navigator, entry)
            NavPointerGuard(navigator, entry)
        }
    }

/**
 * 转场期间不让离场的那一页吃掉点击。
 *
 * navigation3 1.2.0 的 NavDisplay 在 exit transition 结束前，会把离场的 entry 留在
 * AnimatedContent 里继续组合，而且不拦它的指针输入。于是：
 *
 * - pop 时离场页的 zIndex 更高（栈顶那页拿到的是 initialZIndex - 1f），
 *   淡出早就结束了、位移还没收尾，它看不见却盖在上面，点哪里都算点它；
 * - forward 时新页还没盖满屏幕，露出来的那块区域点下去会落到下面那一页。
 *
 * 这里给「目标状态不是 Visible」的 entry 盖一层兄弟节点，在 Initial 阶段消费掉指针事件。
 * 目标状态是 Visible 的是正在进场的那一页，不拦；它自身的位移是 graphicsLayer 做的，
 * 命中区域会跟着视觉位置走，所以进场途中照样能点。
 *
 * 这一层是「吞掉」，不是「让过去」：Compose 的命中测试命中第一个同级节点就停，
 * 被盖住的那一页根本进不了命中链。所以离场期间点到的是空的，不会触发下面那一页的按钮，
 * 也不会触发已经离场那一页的按钮。
 *
 * 判定是 per-scene 的：同一 scene 里的多个 entry 共用同一个 transition。多窗格布局里，
 * 目标 scene 内的每个 entry 都是 Visible，不会因为「不是栈顶」被误拦；被拦的只有离场的那个 scene。
 */
@Composable
private fun <T : Any> NavPointerGuard(navigator: Navigator, entry: NavEntry<T>) {
    val leaving = LocalNavAnimatedContentScope.current.transition.targetState !=
        EnterExitState.Visible
    // 离场有两种：被新页盖住（还在栈里）与被弹出（已经不在栈里）。页内共享元素只在前一种情况下
    // 要留着源那一半，返回时摘掉才不会「整页收回条目 + 元素飞回」两套过渡一起演
    val popped = leaving && !isInBackStack(navigator, entry)
    // 必须自己撑满：这一层如果按内容包裹，父级给的是 0..max 的约束，
    // 页面里那些 fillMaxSize 的子节点会跟着缩，转场途中布局会跳一下
    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalNavEntryPopped provides popped) {
            entry.Content()
        }
        if (leaving) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                // 走 Initial：子节点还没拿到事件就吃掉，
                                // 否则 clickable 在 Main 阶段照样会响应
                                awaitPointerEvent(PointerEventPass.Initial)
                                    .changes
                                    .forEach { it.consume() }
                            }
                        }
                    }
            )
        }
    }
}

/**
 * 把这一页的转场状态回报给 [Navigator]，返回的排队与 seek 收尾靠它判断。
 *
 * 判定：`transition.currentState != transition.targetState` 就是还在演。这里的 `transition` 是
 * **这一页自己的** EnterExitState 子转场（`AnimatedContent` 给每个 content 单独建
 * `createChildTransition`），同一段转场里离场页与露出的那页会得出不同的值 —— 所以按页各记一条，
 * 谁也不能把谁盖掉。页面销毁时还要摘掉自己那条，见下面的 `DisposableEffect`。
 * 只做回报，不碰布局也不碰输入。
 */
@Composable
private fun <T : Any> NavTransitionReport(
    navigator: Navigator,
    entry: NavEntry<T>,
) {
    val transition = LocalNavAnimatedContentScope.current.transition
    val inFlight = transition.currentState != transition.targetState
    // 进场 scene 不是当前栈顶 = 栈还没变、转场正倒向下一层：预测性返回手势的 seek 就是这个形态。
    // 普通 pop 一发生栈顶就已经换成那一页了，所以不会误判。
    val entering = transition.targetState == EnterExitState.Visible
    // nav3 1.2 起 NavEntry.key 是私有的，公开的只剩 contentKey，而 contentKey 的默认值
    // 是库自己从 key 推出来的（1.2.0 起是 Pair(key.toString(), key::class)）。
    // 这里用同一个构造函数把栈顶 key 的 contentKey 算出来再比：判据与原先的
    // 「进场 scene 的 key 不是栈顶」逐字等价，不是近似，所以不会把闪回那一版带回来。
    // 按 key 记住，一个栈顶只造一次，不在每帧分配。
    val topContentKey = navigator.topKey?.let { key -> remember(key) { NavEntry(key) {}.contentKey } }
    val seekingPop = entering && inFlight && entry.contentKey != topContentKey
    // 按页回报：这一页只写自己那一条，别的页在不在播与它无关
    val page = entry.contentKey
    SideEffect {
        navigator.reportTransition(page, inFlight = inFlight, seekingPop = seekingPop)
    }
    // 离场那一页演完就销毁，不会再重组一次来写 false；不摘掉它那条 true，
    // 之后每次返回都要等满 TRANSITION_SETTLE_TIMEOUT_MS 才动
    DisposableEffect(page) {
        onDispose { navigator.forgetTransition(page) }
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
