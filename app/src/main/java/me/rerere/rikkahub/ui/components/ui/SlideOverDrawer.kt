package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import me.rerere.rikkahub.R

private val DefaultSlideSpec = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)

/**
 * 从侧边整块滑进来的遮罩式抽屉，替代 Material 的 ModalNavigationDrawer。
 *
 * 和 Material 那套的区别只在动效：开关走固定时长的平移，不用 expressive 的弹簧，
 * 落位时不会回弹一下；外侧按滑动进度压一层暗色遮罩，收起时一起淡掉。
 * 手势照旧：向右拖拉开、向左拖动收起、点外侧收起，收起状态只接向右的拖动，
 * 向左留给会话里自己的左滑操作。
 */
@Composable
internal fun SlideOverDrawer(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = true,
    scrimColor: Color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f),
    animationSpec: AnimationSpec<Float> = DefaultSlideSpec,
    sheet: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerWidth = with(density) { maxWidth.toPx() }
        val closeLabel = stringResource(R.string.close)
        val currentOnOpenChange by rememberUpdatedState(onOpenChange)
        val currentSpec by rememberUpdatedState(animationSpec)
        // 抽屉宽度量出来之前按整屏算，保证第一帧就停在屏幕外
        var sheetWidth by remember { mutableFloatStateOf(0f) }
        val travel = if (sheetWidth > 0f) sheetWidth else containerWidth
        var offset by remember { mutableFloatStateOf(if (open) 0f else -travel) }
        var dragging by remember { mutableStateOf(false) }
        var animating by remember { mutableStateOf(false) }

        LaunchedEffect(open, travel, dragging) {
            // 拖动时位置由手势给，别让动画抢
            if (dragging) return@LaunchedEffect
            val target = if (open) 0f else -travel
            if (offset == target) return@LaunchedEffect
            animating = true
            try {
                animate(
                    initialValue = offset,
                    targetValue = target,
                    animationSpec = currentSpec,
                ) { value, _ ->
                    offset = value
                }
            } finally {
                animating = false
            }
        }

        val dragGesture = if (gesturesEnabled) {
            Modifier.pointerInput(open, travel) {
                val flingThreshold = 400.dp.toPx()
                val velocityTracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    velocityTracker.resetTracking()
                    var total = 0f
                    var started = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed || change.isConsumed) break
                        val dx = change.position.x - change.previousPosition.x
                        total += dx
                        if (!started) {
                            if (abs(total) < viewConfiguration.touchSlop) continue
                            if (!open && total < 0f) break
                            started = true
                            dragging = true
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            offset = (offset + total).coerceIn(-travel, 0f)
                            change.consume()
                            continue
                        }
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        offset = (offset + dx).coerceIn(-travel, 0f)
                        change.consume()
                    }
                    if (started) {
                        dragging = false
                        // 甩得够快按方向定，否则看有没有拖过一半
                        val velocity = velocityTracker.calculateVelocity().x
                        val shouldOpen = if (abs(velocity) > flingThreshold) {
                            velocity > 0f
                        } else {
                            offset > -travel / 2f
                        }
                        currentOnOpenChange(shouldOpen)
                    }
                }
            }
        } else {
            Modifier
        }

        // 遮罩一直画，透明度按抽屉滑开的进度来：收起动画期间它跟着一起淡掉，
        // 不会出现内容先亮起来的问题；只有抽屉露面时才挂上拦截指针和语义
        val blocking = open || dragging || animating
        Box(modifier = Modifier.fillMaxSize().then(dragGesture)) {
            content()

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        if (travel <= 0f) return@drawBehind
                        val openness = ((offset + travel) / travel).coerceIn(0f, 1f)
                        drawRect(color = scrimColor, alpha = scrimColor.alpha * openness)
                    }
                    .then(
                        if (blocking) {
                            Modifier
                                .semantics {
                                    contentDescription = closeLabel
                                    onClick(label = closeLabel) {
                                        currentOnOpenChange(false)
                                        true
                                    }
                                }
                                .pointerInput(open) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        // 先吃掉按下，抽屉开着时外侧不能再透到下面的内容
                                        down.consume()
                                        var moved = false
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            val distance = (change.position - down.position).getDistance()
                                            if (!moved && distance > viewConfiguration.touchSlop) {
                                                moved = true
                                            }
                                        }
                                        // 有位移的交给外层拖动，这里只管点一下外侧
                                        if (!moved) currentOnOpenChange(false)
                                    }
                                }
                        } else {
                            Modifier
                        },
                    ),
            )

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .offset { IntOffset(offset.roundToInt(), 0) }
                    .onSizeChanged { size ->
                        val width = size.width.toFloat()
                        if (width <= 0f || width == sheetWidth) return@onSizeChanged
                        sheetWidth = width
                        // 收起状态直接对齐实际宽度，别在屏幕外多补一段动画
                        if (!open) offset = -width
                    },
            ) {
                sheet()
            }
        }
    }
}
