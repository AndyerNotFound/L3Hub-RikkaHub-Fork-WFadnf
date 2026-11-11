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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import me.rerere.rikkahub.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

private val DefaultSlideSpec = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)

// 收起时只认从左边缘起手，避免把聊天页自己的横向手势当成开抽屉
private val EdgeSwipeWidth = 24.dp
// 松手时甩动速度达到这个值，直接按甩动方向收/开；否则看位移是否过半
private val IntentSwipeVelocity = 350.dp

/**
 * 从侧边整块滑进来的遮罩式抽屉，替代 Material 的 ModalNavigationDrawer。
 *
 * 拖动跟手：按住拖动时，抽屉位置只写进一个只被绘制阶段读取的 offset 状态，组合读不到它，
 * 所以跟手的每一帧只更新这一层的平移，抽屉里再重的列表也不会重新测量或重组；
 * 松手后位移过半或者甩动速度够格，就从手指停下的位置继续播完开/关动画。
 *
 * 收起状态只接从左边缘起手的右滑，向左留给会话里自己的左滑操作；展开后左滑收起，点外侧收起。
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
        val scope = rememberCoroutineScope()
        val containerWidth = with(density) { maxWidth.toPx() }
        val closeLabel = stringResource(R.string.close)
        val currentOnOpenChange by rememberUpdatedState(onOpenChange)
        val currentSpec by rememberUpdatedState(animationSpec)
        // 抽屉宽度量出来之前按整屏算，保证第一帧就停在屏幕外
        var sheetWidth by remember { mutableFloatStateOf(0f) }
        val travel = if (sheetWidth > 0f) sheetWidth else containerWidth
        var offset by remember { mutableFloatStateOf(if (open) 0f else -travel) }
        var animating by remember { mutableStateOf(false) }
        var dragging by remember { mutableStateOf(false) }
        var settleJob by remember { mutableStateOf<Job?>(null) }

        LaunchedEffect(open, travel) {
            settleJob = coroutineContext.job
            // 手指正按在抽屉上，别用动画把位置抢回去
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
                val edgeWidth = EdgeSwipeWidth.toPx()
                val flingVelocity = IntentSwipeVelocity.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val opening = !open
                    // 收起时只认左边缘起手；展开时不限起手位置
                    if (opening && down.position.x > edgeWidth) return@awaitEachGesture
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                    val startOffset = offset
                    var horizontal = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (horizontal) {
                            change.consume()
                        } else if (change.isConsumed) {
                            break
                        }
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        if (!horizontal) {
                            if (abs(dx) < viewConfiguration.touchSlop) continue
                            if (abs(dx) <= abs(dy) * 1.5f) break
                            if (opening != (dx > 0f)) break
                            horizontal = true
                            // 拖上了就把正在播放的开/关动画掐掉，位置改由手指说话
                            settleJob?.cancel()
                            settleJob = null
                            dragging = true
                            change.consume()
                        }
                        // 位置只写进 offset：组合不读它，跟手期间只重绘、不重组
                        offset = (startOffset + dx).coerceIn(-travel, 0f)
                    }
                    if (!horizontal) return@awaitEachGesture
                    dragging = false
                    val velocity = velocityTracker.calculateVelocity().x
                    val targetOpen = when {
                        abs(velocity) >= flingVelocity -> velocity > 0f
                        else -> offset + travel >= travel / 2f
                    }
                    if (targetOpen != open) {
                        // 状态一变，动画从手指停下的位置接着播完
                        currentOnOpenChange(targetOpen)
                    } else {
                        // 松手弹回原状态。awaitEachGesture 是受限挂起作用域，不能直接调 animate，
                        // 交给组合作用域起一个协程，手势结束也不影响它播完
                        animating = true
                        settleJob = scope.launch {
                            try {
                                animate(
                                    initialValue = offset,
                                    targetValue = if (open) 0f else -travel,
                                    animationSpec = currentSpec,
                                ) { value, _ ->
                                    offset = value
                                }
                            } finally {
                                animating = false
                            }
                        }
                    }
                }
            }
        } else {
            Modifier
        }

        // 遮罩一直画，透明度按抽屉滑开的进度来：收起动画期间它跟着一起淡掉，
        // 不会出现内容先亮起来的问题；只有抽屉露面时才挂上拦截指针和语义
        val blocking = open || animating
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
                                        // 有位移的交给外层手势，这里只管点一下外侧
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
                    // 平移只发生在绘制阶段：整块抽屉不会因为动画或拖动重新测量或重组
                    .graphicsLayer { translationX = offset }
                    .onSizeChanged { size ->
                        val width = size.width.toFloat()
                        if (width <= 0f || width == sheetWidth) return@onSizeChanged
                        sheetWidth = width
                        // 收起状态直接对齐实际宽度，别在屏幕外多补一段动画
                        if (!open && !animating) offset = -width
                    },
            ) {
                sheet()
            }
        }
    }
}
