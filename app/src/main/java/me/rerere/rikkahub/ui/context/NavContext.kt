package me.rerere.rikkahub.ui.context

import android.os.SystemClock
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
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

    /**
     * 返回栈的当前内容（只读视图）。
     *
     * 转场装饰器用它区分「被弹出」与「被新页盖住」：还在栈里说明只是被盖住，页内的共享元素
     * 得留着源那一半，否则进场的元素动画没得配；不在栈里才是真的在离场。
     */
    val stackKeys: List<NavKey> get() = backStack

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
     * 每一页自己的转场状态，键是 `NavEntry.contentKey`，由 `rememberNavInteractionDecorator()` 回报。
     *
     * 不能收成一个布尔值：`LocalNavAnimatedContentScope.current.transition` 是**这一页自己的**子转场
     * （compose 的 `AnimatedContent` 给每个 content 单独 `createChildTransition`），同一段转场里
     * 「离场那页还在滑」与「露出的那页已经落定」会同时成立。收到一个变量里就是最后写的那页说了算：
     * pop 时露出页后组合、后写 false，整段转场被当成「没在播」——返回不排队、预测性返回的 seek
     * 也判不出来；某页在飞行中被销毁时它那条 true 还会留在表里，之后每次返回都要等满
     * [TRANSITION_SETTLE_TIMEOUT_MS]。按页记、销毁时摘掉，才是「转场还在不在播」。
     */
    private val inFlightByPage: SnapshotStateMap<Any, Boolean> = mutableStateMapOf()

    /** 同上按页记：只有「进场中且已经不是栈顶」的那一页把自己写成 true。 */
    private val seekingPopByPage: SnapshotStateMap<Any, Boolean> = mutableStateMapOf()

    /** 转场是否还在播：任意一页还在播就算。可观察，排队中的返回要等它落下。 */
    val transitionInFlight: Boolean get() = inFlightByPage.containsValue(true)

    /** 预测性返回手势是否正把出场进度倒向下一层。可观察。 */
    val transitionSeekingPop: Boolean get() = seekingPopByPage.containsValue(true)

    /** 一页回报自己的转场状态，只动自己那一条，不覆盖别人的。 */
    fun reportTransition(page: Any, inFlight: Boolean, seekingPop: Boolean) {
        if (inFlightByPage[page] != inFlight) inFlightByPage[page] = inFlight
        if (seekingPopByPage[page] != seekingPop) seekingPopByPage[page] = seekingPop
    }

    /** 页面销毁时摘掉它那一条，别把一条 true 永远留在表里。 */
    fun forgetTransition(page: Any) {
        inFlightByPage.remove(page)
        seekingPopByPage.remove(page)
    }

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
