package me.rerere.rikkahub.ui.context

import android.os.SystemClock
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation3.runtime.NavKey
import me.rerere.rikkahub.Screen

class Navigator(private val backStack: MutableList<NavKey>) {
    // 转场大约 300ms。进入和退出要分开计时：共用一个窗口的话，
    // 刚进页面时按返回会被上一条 navigate 拦掉，看起来就是「点了没反应」。
    private var lastNavigateAt = 0L
    private var lastPopAt = 0L

    private fun throttle(now: Long, last: Long) = now - last >= NAVIGATION_THROTTLE_MS

    fun navigate(screen: Screen, builder: NavigateOptionsBuilder.() -> Unit = {}) {
        val now = SystemClock.uptimeMillis()
        if (!throttle(now, lastNavigateAt)) return
        val options = NavigateOptionsBuilder().apply(builder)

        options.popUpToScreen?.let { target ->
            val targetIndex = backStack.indexOfLast { it == target }
            if (targetIndex != -1) {
                val removeFromIndex = if (options.popUpToInclusive) targetIndex else targetIndex + 1
                repeat(backStack.size - removeFromIndex) {
                    backStack.removeLastOrNull()
                }
            }
        }

        // 空操作不占窗口，否则紧接着的第二次点击会被白白吞掉
        if (options.launchSingleTop && backStack.lastOrNull() == screen) {
            return
        }

        lastNavigateAt = now
        backStack.add(screen)
    }

    fun clearAndNavigate(screen: Screen) {
        val now = SystemClock.uptimeMillis()
        if (!throttle(now, lastNavigateAt)) return
        lastNavigateAt = now
        backStack.clear()
        backStack.add(screen)
    }

    /**
     * 转场是否还在播，由 `rememberNavTransitionReporter()` 从 scene 里回写。
     *
     * 同一段转场里所有 scene 共用同一个 Transition，算出来的值必然一致，
     * 所以这里不需要引用计数，谁最后写都一样。
     */
    var transitionInFlight: Boolean = false

    fun popBackStack() {
        val now = SystemClock.uptimeMillis()
        // 同一帧里的重复回调只认第一次：nav3 的 onBack 按「待弹条数」回调多次，
        // 第二次进来时栈还没刷新，放过去会把栈弹空。
        if (now - lastPopAt < SAME_FRAME_MS) return
        // 转场还在播时不防抖：这一下是「动画没播完就想再退一层」的明确意图，
        // 交给 nav3 去重定向，代价是连点两下会一次退两层；空闲时的连点仍按 250ms 拦掉。
        if (!transitionInFlight && !throttle(now, lastPopAt)) return
        if (backStack.size > 1) {
            lastPopAt = now
            backStack.removeLastOrNull()
        }
    }
}

private const val NAVIGATION_THROTTLE_MS = 250L

/** 同一帧内的重复回调窗口：转场在不在播都要拦，否则一次返回会弹掉两层。 */
private const val SAME_FRAME_MS = 16L

class NavigateOptionsBuilder {
    internal var popUpToScreen: Screen? = null
    internal var popUpToInclusive: Boolean = false
    var launchSingleTop: Boolean = false

    fun popUpTo(screen: Screen, builder: PopUpToBuilder.() -> Unit = {}) {
        val options = PopUpToBuilder().apply(builder)
        popUpToScreen = screen
        popUpToInclusive = options.inclusive
    }
}

class PopUpToBuilder {
    var inclusive: Boolean = false
}

val LocalNavController = compositionLocalOf<Navigator> {
    error("No Navigator provided")
}
