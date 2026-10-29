package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.TerminalPalette
import kotlin.math.roundToInt

/**
 * 会话列表右边缘的滚动条：按住拖动可以快速定位，滚动结束后短暂显示再淡出。
 */
@Composable
fun ChatScrollHandle(
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    val settings = LocalSettings.current
    val terminal = settings.displaySetting.terminalStyle
    val scope = rememberCoroutineScope()

    var dragging by remember { mutableStateOf(false) }
    var idleVisible by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    val viewport = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
    val averageItemSize = if (visibleItems.isEmpty()) {
        0f
    } else {
        visibleItems.sumOf { it.size }.toFloat() / visibleItems.size
    }
    val estimatedContent = (averageItemSize * totalItems).coerceAtLeast(viewport)
    val thumbFraction = if (estimatedContent > 0f) {
        (viewport / estimatedContent).coerceIn(0.12f, 1f)
    } else {
        1f
    }
    val firstVisible = visibleItems.firstOrNull()
    val positionFraction = if (firstVisible == null || totalItems <= 1) {
        0f
    } else {
        val consumed = if (averageItemSize > 0f) {
            (-firstVisible.offset / averageItemSize).coerceIn(0f, 1f)
        } else {
            0f
        }
        ((firstVisible.index + consumed) / (totalItems - 1)).coerceIn(0f, 1f)
    }
    val canScroll = totalItems > 1 && viewport > 0f && estimatedContent > viewport * 1.02f

    val currentPosition = rememberUpdatedState(positionFraction)
    val currentTotal = rememberUpdatedState(totalItems)

    LaunchedEffect(state.isScrollInProgress, dragging) {
        if (state.isScrollInProgress || dragging) {
            idleVisible = true
        } else {
            delay(700)
            idleVisible = false
        }
    }

    val trackColor: Color = if (terminal) {
        TerminalPalette.Border.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    val thumbColor: Color = if (terminal) {
        TerminalPalette.GreenDim.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    }

    AnimatedVisibility(
        visible = canScroll && (dragging || idleVisible),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxHeight()
                .width(18.dp)
                .padding(vertical = 8.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragging = true
                            dragFraction = currentPosition.value
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val track = size.height.toFloat().coerceAtLeast(1f)
                            dragFraction = (dragFraction + dragAmount.y / track).coerceIn(0f, 1f)
                            val lastIndex = currentTotal.value - 1
                            if (lastIndex >= 0) {
                                val target = (dragFraction * lastIndex).roundToInt().coerceIn(0, lastIndex)
                                scope.launch { state.scrollToItem(target) }
                            }
                        },
                    )
                },
        ) {
            val thumbHeight = maxOf(28.dp, maxHeight * thumbFraction)
            val travel = (maxHeight - thumbHeight).coerceAtLeast(0.dp)
            val thumbOffset = travel * (if (dragging) dragFraction else positionFraction)
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(trackColor),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = thumbOffset)
                        .width(5.dp)
                        .height(thumbHeight)
                        .clip(RoundedCornerShape(50))
                        .background(thumbColor),
                )
            }
        }
    }
}
