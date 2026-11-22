package me.rerere.rikkahub.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class NavPolicyTest {

    @Test
    fun `right column unwinds before the root stack`() {
        assertEquals(BackStep.PopDetail, nextBackStep(rootDepth = 2, detailDepth = 3))
        assertEquals(BackStep.PopDetail, nextBackStep(rootDepth = 2, detailDepth = 2))
    }

    @Test
    fun `root stack pops once the right column is empty`() {
        assertEquals(BackStep.PopRoot, nextBackStep(rootDepth = 2, detailDepth = 1))
        assertEquals(BackStep.PopRoot, nextBackStep(rootDepth = 5, detailDepth = 1))
    }

    @Test
    fun `stack bottom hands the back press back to the system`() {
        assertEquals(BackStep.ExitApp, nextBackStep(rootDepth = 1, detailDepth = 1))
    }

    // 连按返回时的完整路径：右列两层 → 根栈两层 → 交给系统，一步都不跳
    @Test
    fun `repeated back walks one layer at a time`() {
        var root = 3
        var detail = 3
        val steps = buildList {
            while (true) {
                val step = nextBackStep(rootDepth = root, detailDepth = detail)
                if (step == BackStep.ExitApp) {
                    add(step)
                    break
                }
                add(step)
                when (step) {
                    BackStep.PopDetail -> detail--
                    BackStep.PopRoot -> root--
                    BackStep.ExitApp -> Unit
                }
            }
        }

        assertEquals(
            listOf(
                BackStep.PopDetail,
                BackStep.PopDetail,
                BackStep.PopRoot,
                BackStep.PopRoot,
                BackStep.ExitApp,
            ),
            steps,
        )
    }
}
