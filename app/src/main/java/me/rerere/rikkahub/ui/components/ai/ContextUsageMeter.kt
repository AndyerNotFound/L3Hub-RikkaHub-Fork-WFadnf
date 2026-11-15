package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.ContextMeterStyle
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.contextUsageFraction
import me.rerere.rikkahub.data.model.isContextLimitReached
import me.rerere.rikkahub.ui.components.ui.WaveProgressBar
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.modifier.rememberMotionBlurSteps
import me.rerere.rikkahub.ui.theme.rememberFastSpatialSpec
import me.rerere.rikkahub.utils.formatNumber
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 占用超过门限的这个比例后, 进度条转为提醒色 */
private const val ALERT_FRACTION = 0.8f

/**
 * 上下文预警进度条
 *
 * 两条进度条共用当前占用, 但分母不同:
 * - WARN 条填满 = 到达性能最佳点, 之后回复质量可能开始下滑
 * - MAX 条填满 = 到达性能骤降点, 此时禁止继续发送消息
 *
 * 同时显示绝对值(数字/数字)和百分比，百分比不封顶，可以超过 100%。
 * 点按整块区域打开会话的 Usage 看板。
 */
@Composable
fun ContextUsageMeter(
    tokens: Int?,
    warnTokens: Int?,
    maxTokens: Int?,
    onShowUsage: () -> Unit,
    modifier: Modifier = Modifier,
    // 对话还在生成：进度条显示为流动的波浪，停下来后落成静态直条
    running: Boolean = false,
    // 速度指示（车速仪表盘）：内容由调用方给，位置固定在整条的右端
    speedIndicator: (@Composable () -> Unit)? = null,
) {
    if (warnTokens == null && maxTokens == null) return

    val used = (tokens ?: 0).coerceAtLeast(0)
    val limitReached = isContextLimitReached(tokens, maxTokens)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onShowUsage)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (warnTokens != null) {
                ContextMeterRow(
                    label = stringResource(R.string.chat_page_context_meter_warn),
                    used = used,
                    limit = warnTokens,
                    hardLimit = false,
                    running = running,
                )
            }
            if (maxTokens != null) {
                ContextMeterRow(
                    label = stringResource(R.string.chat_page_context_meter_max),
                    used = used,
                    limit = maxTokens,
                    hardLimit = true,
                    running = running,
                )
            }
            if (limitReached) {
                Text(
                    text = stringResource(R.string.chat_page_context_limit_reached),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        speedIndicator?.invoke()
    }
}

@Composable
private fun ContextMeterRow(
    label: String,
    used: Int,
    limit: Int,
    hardLimit: Boolean,
    running: Boolean,
) {
    val fraction = contextUsageFraction(used, limit)
    val fillColor = contextMeterColor(fraction, hardLimit)
    val textColor = if (fraction >= ALERT_FRACTION) {
        fillColor
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.widthIn(min = 34.dp),
        )
        WaveProgressBar(
            fraction = fraction,
            running = running,
            modifier = Modifier.weight(1f),
            color = fillColor,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
        )
        Text(
            text = contextMeterText(used, limit),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = textColor,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 128.dp),
        )
    }
}

@Composable
private fun contextMeterText(used: Int, limit: Int): String {
    val percent = contextPercentValue(used, limit)
    val binaryUnits = LocalSettings.current.displaySetting.binaryUnits
    return stringResource(
        R.string.chat_page_context_meter_absolute,
        used.formatNumber(binaryUnits),
        limit.formatNumber(binaryUnits),
    ) + " · $percent%"
}

private fun contextPercent(used: Int, limit: Int?): String =
    "${contextPercentValue(used, limit)}%"

private fun contextPercentValue(used: Int, limit: Int?): Int =
    if (limit != null && limit > 0) (used.toFloat() / limit * 100).roundToInt() else 0

@Composable
private fun contextMeterColor(fraction: Float, hardLimit: Boolean): Color {
    val colors = MaterialTheme.colorScheme
    return when {
        fraction >= 1f -> if (hardLimit) colors.error else colors.tertiary
        fraction >= ALERT_FRACTION -> colors.tertiary
        else -> colors.primary
    }
}

@Composable
fun ContextUsageRing(
    tokens: Int?,
    warnTokens: Int?,
    maxTokens: Int?,
    onShowUsage: () -> Unit,
    modifier: Modifier = Modifier,
    // 速度指示（车速仪表盘）：内容由调用方给，位置固定在环形图右侧
    speedIndicator: (@Composable () -> Unit)? = null,
) {
    if (warnTokens == null && maxTokens == null) return

    val used = (tokens ?: 0).coerceAtLeast(0)
    val warnFraction = warnTokens?.let { contextUsageFraction(used, it) }
    val maxFraction = maxTokens?.let { contextUsageFraction(used, it) }
    val mainFraction = (maxFraction ?: warnFraction ?: 0f).coerceIn(0f, 1f)
    val limitReached = isContextLimitReached(tokens, maxTokens)
    val animatedMain by animateFloatAsState(
        targetValue = mainFraction,
        label = "contextRingMain",
    )
    val animatedWarn by animateFloatAsState(
        targetValue = (warnFraction ?: 0f).coerceIn(0f, 1f),
        label = "contextRingWarn",
    )
    val mainColor = contextMeterColor(mainFraction, maxTokens != null)
    val warnColor = contextMeterColor(warnFraction ?: 0f, false)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onShowUsage)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(46.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 4.dp.toPx()
                val warnStroke = 2.dp.toPx()
                val outerRadius = (size.minDimension - stroke) / 2
                val center = Offset(size.width / 2f, size.height / 2f)

                drawCircle(color = trackColor, radius = outerRadius, style = Stroke(stroke))
                drawArc(
                    color = mainColor,
                    startAngle = -90f,
                    sweepAngle = animatedMain * 360f,
                    useCenter = false,
                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                    size = Size(outerRadius * 2, outerRadius * 2),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )

                if (warnTokens != null && maxTokens != null) {
                    val innerRadius = outerRadius - stroke / 2f - 1.dp.toPx() - warnStroke / 2f
                    if (innerRadius > 0f) {
                        drawCircle(color = trackColor.copy(alpha = 0.6f), radius = innerRadius, style = Stroke(warnStroke))
                        drawArc(
                            color = warnColor,
                            startAngle = -90f,
                            sweepAngle = animatedWarn * 360f,
                            useCenter = false,
                            topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                            size = Size(innerRadius * 2, innerRadius * 2),
                            style = Stroke(width = warnStroke, cap = StrokeCap.Round),
                        )
                    }
                }
            }
            Text(
                text = contextPercent(used, maxTokens ?: warnTokens),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = mainColor,
                maxLines = 1,
            )
        }
        speedIndicator?.invoke()
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            if (warnTokens != null) {
                RingValueRow(
                    label = "WARN",
                    used = used,
                    limit = warnTokens,
                    color = warnColor,
                )
            }
            if (maxTokens != null) {
                RingValueRow(
                    label = "MAX",
                    used = used,
                    limit = maxTokens,
                    color = mainColor,
                )
            }
            if (limitReached) {
                Text(
                    text = stringResource(R.string.chat_page_context_limit_reached),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RingValueRow(
    label: String,
    used: Int,
    limit: Int,
    color: Color,
) {
    val fraction = contextUsageFraction(used, limit)
    val text = contextMeterText(used, limit)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (fraction >= ALERT_FRACTION) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 左右滑动切换两种视图的最小位移(px)，小于这个值当作点击 */
private const val CONTEXT_METER_SWIPE_THRESHOLD = 60f

/** 甩动的判定速度(px/s)：够快就不必拖够距离，按方向直接落位 */
private const val CONTEXT_METER_FLING_VELOCITY = 900f

/** 滑动中两块视图各自叠的最大模糊半径（相对强度，按设置里的基准半径缩放） */
private val ContextMeterSwipeBlur = 6.dp

/**
 * 上下文预警的两种视图（直条 / 环形）叠在同一块位置上，左右滑动时两层跟着手指横向平移，
 * 各自按「离落位还有多远」叠动态模糊：滑动中两块都是虚的，滑到哪一端，那一端重新聚焦。
 *
 * 位移一比一跟手，松手按位移与速度吸附到最近的一端；落位之后才把样式写回设置，
 * 中途被打断就停在当前进度上，不会留下半程状态。
 *
 * 两块视图同时参与布局，高度取两者较高的那一份并垂直居中，切换过程中底栏高度不变。
 */
@Composable
fun ContextMeterSwitcher(
    tokens: Int?,
    warnTokens: Int?,
    maxTokens: Int?,
    style: ContextMeterStyle,
    onStyleChange: (ContextMeterStyle) -> Unit,
    onShowUsage: () -> Unit,
    modifier: Modifier = Modifier,
    running: Boolean = false,
    speedIndicator: (@Composable () -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    // 0 = 直条在中间，1 = 环形在中间。拖动时直接改它、只在绘制阶段读：
    // 跟手的每一帧只重绘这一块，不带着输入区的其它部分一起重组
    var progress by remember {
        mutableFloatStateOf(if (style == ContextMeterStyle.RING) 1f else 0f)
    }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var widthPx by remember { mutableIntStateOf(0) }
    var dragStart by remember { mutableFloatStateOf(0f) }
    var dragTotal by remember { mutableFloatStateOf(0f) }
    val slideSpec = rememberFastSpatialSpec<Float>()
    val barBlur = rememberMotionBlurSteps(maxRadius = ContextMeterSwipeBlur)
    val ringBlur = rememberMotionBlurSteps(maxRadius = ContextMeterSwipeBlur)
    // 屏外那一份不必跟着生成中的波浪动画重绘：它整块都被裁掉了
    val barOnScreen by remember { derivedStateOf { progress < 0.999f } }

    // 收尾动画：被新的拖动或新的目标打断时停在当前值上，下一次从那里接着走
    fun settleTo(target: Float, onSettled: (() -> Unit)? = null) {
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(progress, target, animationSpec = slideSpec) { value, _ -> progress = value }
            onSettled?.invoke()
        }
    }

    // 别处改了样式（设置页、恢复备份）时把进度补过去；已经落位就不重播
    LaunchedEffect(style) {
        val target = if (style == ContextMeterStyle.RING) 1f else 0f
        if (abs(progress - target) > 0.001f) settleTo(target)
    }

    val dragState = rememberDraggableState { delta ->
        val width = widthPx.toFloat()
        if (width > 0f) {
            dragTotal += delta
            progress = (progress + delta / width).coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width }
            .clipToBounds()
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStarted = {
                    settleJob?.cancel()
                    // 上一段收尾还没走完就接手时，起点按当前进度就近取一端：
                    // 松手回「原位」不能停在一半的中间态
                    dragStart = if (progress >= 0.5f) 1f else 0f
                    dragTotal = 0f
                },
                onDragStopped = { velocity ->
                    val target = when {
                        velocity <= -CONTEXT_METER_FLING_VELOCITY -> 0f
                        velocity >= CONTEXT_METER_FLING_VELOCITY -> 1f
                        dragTotal <= -CONTEXT_METER_SWIPE_THRESHOLD -> 0f
                        dragTotal >= CONTEXT_METER_SWIPE_THRESHOLD -> 1f
                        // 没拖够，也没甩起来：回原位，样式不变
                        else -> dragStart
                    }
                    settleTo(target) {
                        val next = if (target >= 0.5f) {
                            ContextMeterStyle.RING
                        } else {
                            ContextMeterStyle.BAR
                        }
                        if (next != style) onStyleChange(next)
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 环形排在左边、直条排在右边：向右拖把左边那一份拉进来，落位方向与原来的手势一致
        Box(
            modifier = Modifier.graphicsLayer {
                val slide = progress.coerceIn(0f, 1f)
                translationX = -(1f - slide) * size.width
                renderEffect = ringBlur.at(1f - slide)
            },
            contentAlignment = Alignment.Center,
        ) {
            ContextUsageRing(
                tokens = tokens,
                warnTokens = warnTokens,
                maxTokens = maxTokens,
                onShowUsage = onShowUsage,
                modifier = Modifier.padding(horizontal = 4.dp),
                speedIndicator = speedIndicator,
            )
        }
        Box(
            modifier = Modifier.graphicsLayer {
                val slide = progress.coerceIn(0f, 1f)
                translationX = slide * size.width
                renderEffect = barBlur.at(slide)
            },
            contentAlignment = Alignment.Center,
        ) {
            ContextUsageMeter(
                tokens = tokens,
                warnTokens = warnTokens,
                maxTokens = maxTokens,
                onShowUsage = onShowUsage,
                modifier = Modifier.padding(horizontal = 4.dp),
                running = running && barOnScreen,
                speedIndicator = speedIndicator,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ContextUsageMeterPreview() {
    CompositionLocalProvider(LocalSettings provides Settings()) {
        MaterialTheme {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ContextUsageMeter(
                    tokens = 62_000,
                    warnTokens = 64_000,
                    maxTokens = 200_000,
                    onShowUsage = {},
                )
                ContextUsageMeter(
                    tokens = 62_000,
                    warnTokens = 64_000,
                    maxTokens = 200_000,
                    onShowUsage = {},
                )
                ContextUsageMeter(
                    tokens = 210_000,
                    warnTokens = 64_000,
                    maxTokens = 200_000,
                    onShowUsage = {},
                )
            }
        }
    }
}
