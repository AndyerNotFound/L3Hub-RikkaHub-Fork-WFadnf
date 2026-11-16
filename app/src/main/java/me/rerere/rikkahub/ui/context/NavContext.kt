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

/**
 * 导航栈的唯一出入口。
 *
 * 交互层只有三个判据，各管一件事，互不兼职：
 *
 * 1. 同一入口的重复入栈（双击入口会连推两层相同页面）—— [DUPLICATE_PUSH_WINDOW_MS]；
 * 2. 同一帧里重复的 pop 回调（nav3 的 onBack 按「待弹条数」回调多次）—— [DUPLICATE_POP_WINDOW_MS]；
 * 3. 转场还在播时的返回 —— 进队列，等这一段演完再出栈，既不吞也不重定向。
 *
 * 以前这里是「所有导航共用一个 250ms 窗口 + 返回另有一个 250ms 窗口」：连点两个不同入口时
 * 第二下被静默吞掉，连按返回时第二下直接丢层，观感就是「点了没反应」。窗口现在只认
 * 「同一个页面」，不再一刀切。
 */
class Navigator(
    private val backStack: MutableList<NavKey>,
    // 时间源可注入：往返窗口是纯时间逻辑，单测里不该碰 android.os.SystemClock
    private val clock: () -> Long = SystemClock::uptimeMillis,
) {
    private var lastPushScreen: Screen? = null
    private var lastPushAt = 0L
    private var lastPopAt = 0L

    /** 当前栈顶，转场 reporter 用它区分「进场的是栈顶」与「转场正倒向下一层」。 */
    val topKey: NavKey? get() = backStack.lastOrNull()

    fun navigate(screen: Screen, builder: NavigateOptionsBuilder.() -> Unit = {}) {
        val now = clock()
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

        // 双击同一个入口只认第一次：第二次推的是同一个 Screen，再推就是两层一样的页面
        if (screen == lastPushScreen && now - lastPushAt < DUPLICATE_PUSH_WINDOW_MS) {
            return
        }

        // 新的导航盖掉排队中的返回，否则那一下会弹掉刚打开的页面
        pendingPops = 0
        lastPushScreen = screen
        lastPushAt = now
        backStack.add(screen)
    }

    fun clearAndNavigate(screen: Screen) {
        val now = clock()
        if (screen == lastPushScreen && now - lastPushAt < DUPLICATE_PUSH_WINDOW_MS) {
            return
        }
        pendingPops = 0
        lastPushScreen = screen
        lastPushAt = now
        backStack.clear()
        backStack.add(screen)
    }

    /**
     * 转场是否还在播，由 `rememberNavInteractionDecorator()` 从 scene 里回写。
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
     * 会重播一遍进场，并且 zIndex 与离场页打平、又排在绘制顺序末尾，盖在离场页之上——观感就是
     * 「返回作用到了上一页」。排队以后 pop 一律走正常路径，不等同于防抖吞掉。
     */
    var pendingPops: Int by mutableStateOf(0)
        private set

    /**
     * 预测性返回手势正把出场进度倒向下一层：这时栈还没变，进场中的 scene 不是当前栈顶。
     * 由 `rememberNavInteractionDecorator()` 回写，可观察。
     *
     * 只有「手势 seek」会命中：普通 pop 一发生栈顶就已经换了人，进场 scene 与栈顶一致。
     */
    var transitionSeekingPop: Boolean by mutableStateOf(false)

    fun popBackStack() {
        val now = clock()
        // 同一次返回的重复回调只认第一次：第二次进来时栈还没刷新，放过去会把栈弹空
        if (now - lastPopAt < DUPLICATE_POP_WINDOW_MS) return
        lastPopAt = now
        if (!transitionInFlight) {
            popTop(now)
            return
        }
        if (transitionSeekingPop) {
            // 出栈落地就是这段 seek 的终点，直接出栈让 seek 顺势收尾。排队的话 nav3 等不到出栈，
            // 会按「手势被取消」把 seek 倒放回当前页——页面退出后又闪回来
            popTop(now)
            return
        }
        // 进场还没落（动画没播完就想再退一层）：先记下来，等这一段转场落下再走正常 pop
        pendingPops++
    }

    private fun popTop(now: Long) {
        if (backStack.size <= 1) return
        lastPopAt = now
        // 出栈之后再入栈同名页面不是双击，别被重复入栈窗口吞掉
        lastPushScreen = null
        backStack.removeLastOrNull()
    }

    /**
     * 排队返回的出口，由 `RouteActivity` 里的一条 `LaunchedEffect` 常驻消费。
     *
     * 一次只退一层：等这一段转场落下再弹下一层，两次出栈挤在同一段转场里，第二次还是会被重定向。
     */
    suspend fun drainPendingPops() {
        while (true) {
            if (pendingPops == 0) {
                snapshotFlow { pendingPops }.first { it > 0 }
            }
            awaitTransitionSettled()
            // 这段等待里可能被新的导航清掉了，那就别再出栈，否则弹的是刚打开的页面
            if (pendingPops == 0) continue
            pendingPops--
            popTop(clock())
            // 等这一层的退场真的起来，再回到循环顶部等它落下；关掉动效时它不会起来，超时即可
            withTimeoutOrNull(TRANSITION_START_TIMEOUT_MS) {
                snapshotFlow { transitionInFlight }.first { it }
            }
        }
    }

    /** 等转场落下。状态万一卡在 true（回写那一层没了），不能把返回一直扣着。 */
    private suspend fun awaitTransitionSettled() {
        if (!transitionInFlight) return
        withTimeoutOrNull(TRANSITION_SETTLE_TIMEOUT_MS) {
            snapshotFlow { transitionInFlight }.first { !it }
        }
    }
}

/** 同一入口的重复入栈窗口：双击入口会连推两层相同页面。 */
private const val DUPLICATE_PUSH_WINDOW_MS = 400L

/**
 * 同一次返回的重复回调用窗口：预测性返回提交后系统可能再补一次 onBack，50ms 内只认一次。
 * 不动到 100ms 以上：人手连按最快也在 100ms 开外，窗口再大就是把真按吞掉。
 */
private const val DUPLICATE_POP_WINDOW_MS = 50L

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
