package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 两列之间的拖动手柄：10dp 的抓取区，中间一条短竖线。
 *
 * 拖动量按当前密度换算成 dp，横屏 0.75 缩放下也是 1:1：手指走多少像素，列宽就跟着移动多少像素。
 */
@Composable
internal fun ColumnResizeHandle(
    onDrag: (Dp) -> Unit,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(10.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(with(density) { dragAmount.toDp() })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(0.3f)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}
