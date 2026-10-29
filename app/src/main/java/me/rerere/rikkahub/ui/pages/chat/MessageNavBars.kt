package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** 一次最多画这么多横条，消息再多就靠边缘继续滑 */
private const val MAX_BARS = 24

/**
 * Codex 风格的消息导航：滚动时边缘出现一列小横条，每根对应一条消息。
 *
 * 点或滑到某根横条就跳到那条消息；消息超过 [MAX_BARS] 条时只展示一段窗口，
 * 触点在顶部/底部时窗口跟着移动，相当于继续往上/下翻。静默一段时间自动隐藏。
 */
@Composable
fun MessageNavBars(
    messageCount: Int,
    firstMessageItemIndex: Int,
    state: LazyListState,
    onLeft: Boolean,
    modifier: Modifier = Modifier,
) {
    if (messageCount < 2) return

    val barsCount = minOf(MAX_BARS, messageCount)
    val windowStart = remember { mutableIntStateOf(0) }
    var interacting by remember { mutableStateOf(false) }
    var idleVisible by remember { mutableStateOf(false) }

    val currentOrdinal by rememberUpdatedState(
        (state.firstVisibleItemIndex - firstMessageItemIndex).coerceIn(0, messageCount - 1)
    )
    val maxWindowStart = (messageCount - barsCount).coerceAtLeast(0)

    LaunchedEffect(currentOrdinal, interacting) {
        if (!interacting) {
            windowStart.intValue = (currentOrdinal - barsCount / 2).coerceIn(0, maxWindowStart)
        }
    }

    LaunchedEffect(state.isScrollInProgress, interacting) {
        if (state.isScrollInProgress || interacting) {
            idleVisible = true
        } else {
            delay(900)
            idleVisible = false
        }
    }

    fun jumpTo(ordinal: Int) {
        state.requestScrollToItem((firstMessageItemIndex + ordinal).coerceAtLeast(0))
    }

    fun scrub(y: Float, height: Float) {
        if (barsCount <= 0) return
        val slot = (height / barsCount).coerceAtLeast(1f)
        val index = (y / slot).toInt().coerceIn(0, barsCount - 1)
        when {
            index == 0 && windowStart.intValue > 0 -> {
                windowStart.intValue = (windowStart.intValue - 1).coerceAtLeast(0)
                jumpTo(windowStart.intValue)
            }

            index == barsCount - 1 && windowStart.intValue < maxWindowStart -> {
                windowStart.intValue = (windowStart.intValue + 1).coerceAtMost(maxWindowStart)
                jumpTo(windowStart.intValue + barsCount - 1)
            }

            else -> jumpTo(windowStart.intValue + index)
        }
    }

    AnimatedVisibility(
        visible = idleVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(26.dp)
                .pointerInput(messageCount, barsCount) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        interacting = true
                        scrub(down.position.y, size.height.toFloat())
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) {
                                change.consume()
                                break
                            }
                            if (change.positionChanged()) {
                                change.consume()
                                scrub(change.position.y, size.height.toFloat())
                            }
                        }
                        interacting = false
                    }
                },
            contentAlignment = if (onLeft) Alignment.CenterStart else Alignment.CenterEnd,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 16.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = if (onLeft) Alignment.Start else Alignment.End,
            ) {
                repeat(barsCount) { index ->
                    val ordinal = windowStart.intValue + index
                    val active = ordinal == currentOrdinal
                    Box(
                        modifier = Modifier
                            .width(if (active) 22.dp else 14.dp)
                            .height(if (active) 4.dp else 3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (active) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                }
                            ),
                    )
                }
            }
        }
    }
}
