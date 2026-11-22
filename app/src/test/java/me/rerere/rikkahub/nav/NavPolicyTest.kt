package me.rerere.rikkahub.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class NavPolicyTest {

    @Test
    fun `a deeper history pops one step`() {
        assertEquals(BackStep.Pop, nextBackStep(depth = 3))
        assertEquals(BackStep.Pop, nextBackStep(depth = 2))
    }

    // 栈底那一页是退出边界：返回交回系统，不再弹
    @Test
    fun `the bottom of the history exits`() {
        assertEquals(BackStep.ExitApp, nextBackStep(depth = 1))
    }
}
