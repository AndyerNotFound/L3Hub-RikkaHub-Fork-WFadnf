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
 * 判据只有 [nextBackStep] 一个来源（见 nav/NavPolicy.kt），横竖屏一致：历史还有上一层就弹一格。
 *
 * [ownsBack] 说的是这一下由不由这里接：主列之上还压着右列时接过来——右列的收页不会让主列
 * 变栈，预测性返回的进度会被演到主列上，所以这一段手势不给它。右列空着时交回
 * [l3HubStackAnimation] 的预测性返回，弹主列带进度；到底时两者都关，返回交回系统。
 *
 * 优先级取 1：关闭时不影响优先级 0 的预测性返回回调，开启时压过它。
 */
@Composable
fun NavigationBackHandler(
    component: RootComponent,
    depth: Int,
    ownsBack: Boolean,
) {
    val owns = ownsBack && nextBackStep(depth = depth) == BackStep.Pop
    val callback = remember(component) {
        BackCallback(isEnabled = false, priority = 1) {
            if (nextBackStep(component.depth) == BackStep.Pop) component.pop()
        }
    }
    DisposableEffect(component) {
        component.backHandler.register(callback)
        onDispose { component.backHandler.unregister(callback) }
    }
    SideEffect { callback.isEnabled = owns }
}
