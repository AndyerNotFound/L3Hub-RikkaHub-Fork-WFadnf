package me.rerere.rikkahub.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import com.arkivanov.essenty.backhandler.BackCallback
import me.rerere.rikkahub.nav.BackStep
import me.rerere.rikkahub.nav.RootComponent
import me.rerere.rikkahub.nav.nextBackStep

/**
 * 返回键的唯一主人。
 *
 * 右列还有下钻页时由这里收右列；其余情况这个回调保持关闭，让
 * [l3HubStackAnimation] 的预测性返回回调去弹根栈；两层到底时两者都关闭，
 * 返回交回系统退出应用。判据只有 [nextBackStep] 一个来源（见 nav/NavPolicy.kt）。
 *
 * 优先级取 1：关闭时不影响优先级 0 的预测性返回回调，开启时压过它，
 * 手势进度不会被演到根栈上（右列收页与根栈弹页是两件事）。
 */
@Composable
fun NavigationBackHandler(
    component: RootComponent,
    rootDepth: Int,
    detailDepth: Int,
) {
    val next = nextBackStep(rootDepth = rootDepth, detailDepth = detailDepth)
    val callback = remember(component) {
        BackCallback(isEnabled = false, priority = 1) {
            when (
                nextBackStep(
                    rootDepth = component.rootStack.size,
                    detailDepth = component.detailDestinations.size,
                )
            ) {
                BackStep.PopDetail -> component.popDetail()
                BackStep.PopRoot -> component.popRoot()
                BackStep.ExitApp -> Unit
            }
        }
    }
    DisposableEffect(component) {
        component.backHandler.register(callback)
        onDispose { component.backHandler.unregister(callback) }
    }
    SideEffect { callback.isEnabled = next == BackStep.PopDetail }
}
