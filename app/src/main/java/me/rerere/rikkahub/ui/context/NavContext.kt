package me.rerere.rikkahub.ui.context

import android.os.SystemClock
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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

        // 新的导航盖掉排队中的返回，否则那一下会弹掉刚打开的页面
        pendingPops = 0
        lastNavigateAt = now
        backStack.add(screen)
    }

    fun clearAndNavigate(screen: Screen) {
        val now = SystemClock.uptimeMillis()
        if (!throttle(now, lastNavigateAt)) return
        pendingPops = 0
        lastNavigateAt = now
        backStack.clear()
        backStack.add(screen)
    }

    /**
     * 转场是否还在播，由 `rememberNavTransitionReporter()` 从 scene 里回写。
     *
     * 同一段转场里所有 scene 共用同一个 Transition，算出来的值必然一致，
     * 所以这里不需要引用计数，谁最后写都一样。
     *
     * 可观察：排队中的返回要等它落下。
     */
    var transitionInFlight: Boolean by mutableStateOf(false)

    /**
     * 转场里按下的返回，等这一段转场演完再出栈。
     *
     * 直接出栈会让 nav3 把这次 pop 当成「打断正在播的进场」重定向到同一个 Transition 上，
     * 而 Compose 的 AnimatedContent 给每个 content 缓存的进场参数（`specOnEnter`，连带其中的
     * `targetContentZIndex`）只在目标变化时重算：重定向后上一页拿到的是上一段进场的参数，
     * 会重播一遍进场，并且 zIndex 与离场页打平、又排在绘制顺序末尾，盖在离场页之上——
     * 观感就是「返回作用到了上一页」。排队以后 pop 一律走正常路径，不等同于防抖吞掉。
     */
    var pendingPops: Int by mutableStateOf(0)
        private set

    fun popBackStack() {
        val now = SystemClock.uptimeMillis()
        // 同一帧里的重复回调只认第一次：nav3 的 onBack 按「待弹条数」回调多次，
        // 第二次进来时栈还没刷新，放过去会把栈弹空。
        if (now - lastPopAt < SAME_FRAME_MS) return
        // 转场里不直接出栈：这一下是「动画没播完就想再退一层」的明确意图，先记下来，
        // 等这一段转场落下再走正常 pop，交给 drainPendingPops 消费
        if (transitionInFlight) {
            // 同一帧里的重复回调同样要拦：排队不改栈，靠时间戳挡住第二次
            lastPopAt = now
            pendingPops++
            return
        }
        // 空闲时的连点仍按 250ms 拦掉
        if (!throttle(now, lastPopAt)) return
        popTop(now)
    }

    private fun popTop(now: Long) {
        if (backStack.size > 1) {
            lastPopAt = now
            backStack.removeLastOrNull()
        }
    }

    /**
     * 排队返回的出口，由 `RouteActivity` 里的一条 `LaunchedEffect` 常驻消费。
     *
     * 一次只退一层，并且等这一层自己的退场真正起来、落下之后再继续：两次出栈挤在同一段转场里，
     * 第二次还是会被重定向。
     */
    suspend fun drainPendingPops() {
        while (true) {
            if (pendingPops == 0) {
                snapshotFlow { pendingPops }.first { it > 0 }
            }
            if (transitionInFlight) {
                // 转场状态万一卡在 true（回写的那一层没了），不能把返回一直扣着
                withTimeoutOrNull(TRANSITION_SETTLE_TIMEOUT_MS) {
                    snapshotFlow { transitionInFlight }.first { !it }
                }
            }
            // 这段等待里可能被新的导航清掉了，那就别再出栈，否则弹的是刚打开的页面
            if (pendingPops == 0) continue
            pendingPops--
            popTop(SystemClock.uptimeMillis())
            // 等这一层的退场起来再等它落下；关掉动效时它不会起来，超时就当已经结束
            val started = withTimeoutOrNull(TRANSITION_START_TIMEOUT_MS) {
                snapshotFlow { transitionInFlight }.first { it }
            }
            if (started != null) {
                snapshotFlow { transitionInFlight }.first { !it }
            }
        }
    }
}

private const val NAVIGATION_THROTTLE_MS = 250L

/** 同一帧内的重复回调窗口：转场在不在播都要拦，否则一次返回会弹掉两层。 */
private const val SAME_FRAME_MS = 16L

/** 出栈后等这段退场「起来」的上限：关掉动效时它根本不会起来，不能无限等。 */
private const val TRANSITION_START_TIMEOUT_MS = 150L

/** 等转场落下的上限：回写那一层没了的时候状态会停在 true，不能一直扣着返回。 */
private const val TRANSITION_SETTLE_TIMEOUT_MS = 1200L

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
