package me.rerere.rikkahub.ui.modifier

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 速度达到这一档就吃满最大半径；手滑、甩动都在这之上，模糊负责把速度讲出来。 */
private const val SCROLL_BLUR_FULL_SPEED = 1400f

/** 低于这个速度不算滚动。布局取整带来的 ±1px 抖动不该让整块文字发虚。 */
private const val SCROLL_BLUR_MIN_SPEED = 40f

/** 滚动时至少压上的一层薄雾。流式跟随每帧只挪几个像素，纯按速度换算根本看不见。 */
private const val SCROLL_BLUR_BASE = 0.2f

/** 停下后先等一小段再散焦：相邻两次刷新之间要是立刻变清晰，看起来就是一直在闪。 */
private const val SCROLL_BLUR_SETTLE_MILLIS = 90L

/** 散焦每帧的衰减系数，约八帧落回零。 */
private const val SCROLL_BLUR_DECAY = 0.84f

/** 模糊量的量化档数，与 [rememberMotionBlurSteps] 的档位对齐。 */
private const val SCROLL_BLUR_STEPS = 16

/**
 * 滚动速度驱动的动态模糊：列表滚得越快，整块内容糊得越重，停下来就重新聚焦。
 *
 * 聊天页的流式输出每来一段文本，列表都要往下贴一截。这些位移小、频率高、又没有动画，
 * 看上去就是一格一格地窜。给位移本身叠一层随速度收放的模糊，这一段才读得出「内容在往上走」，
 * 而不是「文字忽然换了位置」。
 *
 * 采样跟着滚动事件走，不常驻帧循环：静止的聊天页一帧都不多算。模糊量按档位量化后才写进状态，
 * 落到零就不再产生失效，整块列表不会因为一个趋近于零的小数一直重绘。
 * API 31 以下没有 RenderEffect，退化成完全不模糊，滚动本身不受影响。
 */
@Composable
fun Modifier.scrollMotionBlur(
    state: LazyListState,
    enabled: Boolean = true,
    maxRadius: Dp = 4.dp,
): Modifier {
    val blurSteps = rememberMotionBlurSteps(maxRadius = maxRadius, steps = SCROLL_BLUR_STEPS)
    var applied by remember(state) { mutableFloatStateOf(0f) }

    LaunchedEffect(state, enabled) {
        if (!enabled) {
            applied = 0f
            return@LaunchedEffect
        }
        // 位移要跨 item 才连得上：翻过首项的那一帧 index 变了，只比 scrollOffset 会漏掉一整项的高度
        var lastIndex = state.firstVisibleItemIndex
        var lastOffset = state.firstVisibleItemScrollOffset
        var lastSize = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
        var lastNanos = System.nanoTime()
        var amount = 0f
        var settle: Job? = null

        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val now = System.nanoTime()
                val itemSize = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: lastSize
                // 掉帧时 dt 会偏大，夹一下，免得一帧卡顿被当成一次高速滚动
                val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(1f / 240f, 0.1f)
                val delta = scrollDelta(
                    oldIndex = lastIndex,
                    oldOffset = lastOffset,
                    oldSize = lastSize,
                    newIndex = index,
                    newOffset = offset,
                    newSize = itemSize,
                    viewportHeight = state.layoutInfo.viewportSize.height,
                )
                lastIndex = index
                lastOffset = offset
                lastSize = itemSize
                lastNanos = now

                val speed = abs(delta) / dt
                if (speed >= SCROLL_BLUR_MIN_SPEED) {
                    val ramp = ((speed - SCROLL_BLUR_MIN_SPEED) /
                        (SCROLL_BLUR_FULL_SPEED - SCROLL_BLUR_MIN_SPEED)).coerceIn(0f, 1f)
                    // 开方：低速段才拉得开，不然只有甩到底才看得出变化
                    val target = SCROLL_BLUR_BASE + (1f - SCROLL_BLUR_BASE) * sqrt(ramp)
                    // 一帧到位会跟着刷新节奏闪；收着走，滚动持续时很快追上，停下也不会立刻消失
                    amount += (target - amount) * 0.55f
                    applied = quantizeBlur(amount)
                }

                // 每次位移都把散焦推后：滚动还在继续时不该有时间收敛到零。
                // 已经完全不糊时不再起协程，静止的页面连一个等待中的延时都不留
                settle?.cancel()
                settle = if (amount > 0f) {
                    launch {
                        delay(SCROLL_BLUR_SETTLE_MILLIS)
                        while (amount > 0f) {
                            withFrameNanos { }
                            amount *= SCROLL_BLUR_DECAY
                            if (amount < 0.5f / SCROLL_BLUR_STEPS) amount = 0f
                            applied = quantizeBlur(amount)
                        }
                    }
                } else {
                    null
                }
            }
    }

    return this.graphicsLayer {
        renderEffect = blurSteps.at(applied)
    }
}

private fun quantizeBlur(amount: Float): Float =
    (amount * SCROLL_BLUR_STEPS).roundToInt() / SCROLL_BLUR_STEPS.toFloat()

/**
 * 两次采样之间内容向上移动的像素数，向下为负。
 *
 * 首项没换时就是 [LazyListState.firstVisibleItemScrollOffset] 的差；
 * 换首项的那一帧要把旧首项露在视口外的那一截和新首项被推出顶部的偏移接起来算，
 * 中间跨过去的 item 已经不可见，按可视高度估一个量级，只用于判断速度档位。
 */
private fun scrollDelta(
    oldIndex: Int,
    oldOffset: Int,
    oldSize: Int,
    newIndex: Int,
    newOffset: Int,
    newSize: Int,
    viewportHeight: Int,
): Float = when {
    newIndex == oldIndex -> (newOffset - oldOffset).toFloat()
    newIndex > oldIndex ->
        (oldSize - oldOffset + newOffset).toFloat() +
            (newIndex - oldIndex - 1) * viewportHeight * 0.6f
    else ->
        -(newSize - newOffset + oldOffset).toFloat() -
            (oldIndex - newIndex - 1) * viewportHeight * 0.6f
}
