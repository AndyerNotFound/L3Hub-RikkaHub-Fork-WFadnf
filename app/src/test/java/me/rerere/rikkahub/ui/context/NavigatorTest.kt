package me.rerere.rikkahub.ui.context

import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.rerere.rikkahub.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * 导航出入口的三条判据：重复入栈只认一次、不同入口都不吞、连按返回每一下都算数；
 * 外加排队返回（[Navigator.drainPendingPops]）的消费规则。
 *
 * 时间源与两段等待上限都注入，不碰 android.os.SystemClock，也不真等满 1200ms。
 */
class NavigatorTest {
    private var now = 1_000L
    private val stack = mutableListOf<NavKey>(Screen.Chat("root"))
    private val navigator = Navigator(stack) { now }

    @Test
    fun double_tap_same_entry_pushes_once() {
        navigator.navigate(Screen.Setting)
        now += 80
        navigator.navigate(Screen.Setting)
        assertEquals(listOf<NavKey>(Screen.Chat("root"), Screen.Setting), stack.toList())
    }

    @Test
    fun different_entries_both_land() {
        navigator.navigate(Screen.Setting)
        now += 30
        navigator.navigate(Screen.History)
        assertEquals(3, stack.size)
    }

    @Test
    fun consecutive_back_pops_each_layer() {
        navigator.navigate(Screen.Setting)
        now += 500
        navigator.navigate(Screen.History)
        now += 500
        navigator.popBackStack()
        now += 60
        navigator.popBackStack()
        assertEquals(1, stack.size)
    }

    @Test
    fun back_during_enter_transition_is_queued() {
        navigator.navigate(Screen.Setting)
        now += 500
        navigator.reportTransition(Screen.Setting, inFlight = true, seekingPop = false)
        navigator.popBackStack()
        assertEquals(2, stack.size)
        assertEquals(1, navigator.pendingPops)
    }

    @Test
    fun back_during_seek_pops_immediately() {
        navigator.navigate(Screen.Setting)
        now += 500
        navigator.reportTransition(Screen.Setting, inFlight = true, seekingPop = true)
        navigator.popBackStack()
        assertEquals(1, stack.size)
    }

    /** 转场里的两页各报各的：一页落定不能把另一页还在播的那条盖掉。 */
    @Test
    fun settled_page_does_not_clear_animating_page() {
        navigator.reportTransition(Screen.Setting, inFlight = true, seekingPop = false)
        navigator.reportTransition(Screen.History, inFlight = false, seekingPop = false)
        assertTrue(navigator.transitionInFlight)
        assertFalse(navigator.transitionSeekingPop)
    }

    /** 页面销毁时把它的那条摘掉，别让一条 true 永远留着。 */
    @Test
    fun forgotten_page_takes_its_flags_with_it() {
        navigator.reportTransition(Screen.Setting, inFlight = true, seekingPop = true)
        navigator.forgetTransition(Screen.Setting)
        assertFalse(navigator.transitionInFlight)
        assertFalse(navigator.transitionSeekingPop)
    }

    @Test
    fun push_after_pop_is_not_treated_as_double_tap() {
        navigator.navigate(Screen.Setting)
        now += 500
        navigator.popBackStack()
        now += 60
        navigator.navigate(Screen.Setting)
        assertEquals(listOf<NavKey>(Screen.Chat("root"), Screen.Setting), stack.toList())
    }

    // ---- 排队返回的消费端：一次一层、遇 seek 立刻出栈、被新导航清空、超时兜底 ----

    /** 一段转场只退一层：第二层要等下一段退场起来（或等满 start 超时）再弹。 */
    @Test
    fun drain_pops_one_layer_per_settled_transition() = runTest {
        val localStack = mutableListOf<NavKey>(Screen.Chat("root"))
        var localNow = 1_000L
        val navigator = Navigator(
            backStack = localStack,
            clock = { localNow },
            transitionStartTimeoutMs = 50,
            transitionSettleTimeoutMs = 500,
        )
        navigator.navigate(Screen.Setting)
        localNow += 500
        navigator.navigate(Screen.History)

        // 进场还在播：两下返回都只进队列（第二下要过 50ms 的重复回调窗口）
        navigator.reportTransition(Screen.History, inFlight = true, seekingPop = false)
        navigator.popBackStack()
        localNow += 60
        navigator.popBackStack()
        assertEquals(2, navigator.pendingPops)
        assertEquals(3, localStack.size)

        // 这一段演完了：消费端开始出栈
        navigator.reportTransition(Screen.History, inFlight = false, seekingPop = false)
        Snapshot.sendApplyNotifications()
        backgroundScope.launch { navigator.drainPendingPops() }
        runCurrent()
        assertEquals(2, localStack.size)
        assertEquals(1, navigator.pendingPops)

        // 退场没有真的起来（关掉动效就是这样）：等满 start 超时后接着退下一层
        advanceTimeBy(51.milliseconds)
        runCurrent()
        assertEquals(1, localStack.size)
        assertEquals(0, navigator.pendingPops)
    }

    /** 状态卡在「还在播」不回写时，不能把返回一直扣着：settle 超时后照常出栈。 */
    @Test
    fun drain_gives_up_waiting_for_the_transition_after_timeout() = runTest {
        val localStack = mutableListOf<NavKey>(Screen.Chat("root"))
        var localNow = 1_000L
        val navigator = Navigator(
            backStack = localStack,
            clock = { localNow },
            transitionStartTimeoutMs = 50,
            transitionSettleTimeoutMs = 200,
        )
        navigator.navigate(Screen.Setting)
        localNow += 500
        navigator.reportTransition(Screen.Setting, inFlight = true, seekingPop = false)
        navigator.popBackStack()
        assertEquals(1, navigator.pendingPops)

        backgroundScope.launch { navigator.drainPendingPops() }
        runCurrent()
        // 还在等那段转场落下
        assertEquals(2, localStack.size)
        assertEquals(1, navigator.pendingPops)

        advanceTimeBy(201.milliseconds)
        runCurrent()
        assertEquals(1, localStack.size)
        assertEquals(0, navigator.pendingPops)
    }

    /** 不等超时：真实的「落定 → 出栈 → 下一段起来 → 再落定」也必须逐层推进。 */
    @Test
    fun drain_follows_real_transition_notifications() = runTest {
        val localStack = mutableListOf<NavKey>(Screen.Chat("root"), Screen.Setting, Screen.History)
        var localNow = 1_000L
        val navigator = Navigator(
            backStack = localStack,
            transitionStartTimeoutMs = 500,
            transitionSettleTimeoutMs = 500,
            clock = { localNow },
        )
        navigator.reportTransition(Screen.History, inFlight = true, seekingPop = false)
        Snapshot.sendApplyNotifications()
        navigator.popBackStack()
        localNow += 60
        navigator.popBackStack()
        assertEquals(2, navigator.pendingPops)

        backgroundScope.launch { navigator.drainPendingPops() }
        runCurrent()
        assertEquals(3, localStack.size)

        // 第一段落定：只出栈一层，然后等下一段真实开始
        navigator.reportTransition(Screen.History, inFlight = false, seekingPop = false)
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals(2, localStack.size)
        assertEquals(1, navigator.pendingPops)

        navigator.reportTransition(Screen.Setting, inFlight = true, seekingPop = false)
        Snapshot.sendApplyNotifications()
        runCurrent()
        advanceTimeBy(100.milliseconds)
        runCurrent()
        assertEquals(2, localStack.size)

        // 第二段落定才出第二层，不走 500ms 超时兜底
        navigator.reportTransition(Screen.Setting, inFlight = false, seekingPop = false)
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals(1, localStack.size)
        assertEquals(0, navigator.pendingPops)
    }

    /** 手势正倒向下一层：出栈就是这段 seek 的终点，立刻弹，不进队列。 */
    @Test
    fun seek_pops_immediately_and_leaves_the_queue_empty() {
        navigator.navigate(Screen.Setting)
        now += 500
        navigator.reportTransition(Screen.Setting, inFlight = true, seekingPop = true)
        navigator.popBackStack()
        assertEquals(1, stack.size)
        assertEquals(0, navigator.pendingPops)
    }

    /** 新的导航盖掉排队中的返回，否则那一下会弹掉刚打开的页面。 */
    @Test
    fun navigate_clears_queued_pops() {
        navigator.navigate(Screen.Setting)
        now += 500
        navigator.reportTransition(Screen.Setting, inFlight = true, seekingPop = false)
        navigator.popBackStack()
        assertEquals(1, navigator.pendingPops)

        navigator.navigate(Screen.History)
        assertEquals(0, navigator.pendingPops)
        assertEquals(
            listOf<NavKey>(Screen.Chat("root"), Screen.Setting, Screen.History),
            stack.toList(),
        )
    }

    /** 清栈导航同理：排队的返回不能落到清完之后的新栈上。 */
    @Test
    fun clear_and_navigate_clears_queued_pops() {
        navigator.navigate(Screen.Setting)
        now += 500
        navigator.reportTransition(Screen.Setting, inFlight = true, seekingPop = false)
        navigator.popBackStack()
        assertEquals(1, navigator.pendingPops)

        navigator.clearAndNavigate(Screen.Log)
        assertEquals(0, navigator.pendingPops)
        assertEquals(listOf<NavKey>(Screen.Log), stack.toList())
    }
}
