package me.rerere.rikkahub.ui.context

import androidx.navigation3.runtime.NavKey
import me.rerere.rikkahub.Screen
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 导航出入口的三条判据：重复入栈只认一次、不同入口都不吞、连按返回每一下都算数。
 *
 * 时间源注入，不碰 android.os.SystemClock。
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
        navigator.transitionInFlight = true
        navigator.popBackStack()
        assertEquals(2, stack.size)
        assertEquals(1, navigator.pendingPops)
    }

    @Test
    fun back_during_seek_pops_immediately() {
        navigator.navigate(Screen.Setting)
        now += 500
        navigator.transitionInFlight = true
        navigator.transitionSeekingPop = true
        navigator.popBackStack()
        assertEquals(1, stack.size)
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
}
