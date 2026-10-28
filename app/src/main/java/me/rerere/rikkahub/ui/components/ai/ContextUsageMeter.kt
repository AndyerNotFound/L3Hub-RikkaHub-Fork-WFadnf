package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.contextUsageFraction
import me.rerere.rikkahub.data.model.isContextLimitReached
import me.rerere.rikkahub.utils.formatNumber
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
 * 点按整块区域在百分比和绝对值(数字/数字)之间切换。
 */
@Composable
fun ContextUsageMeter(
    tokens: Int?,
    warnTokens: Int?,
    maxTokens: Int?,
    showAbsolute: Boolean,
    onToggleDisplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (warnTokens == null && maxTokens == null) return

    val used = (tokens ?: 0).coerceAtLeast(0)
    val limitReached = isContextLimitReached(tokens, maxTokens)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onToggleDisplay)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (warnTokens != null) {
            ContextMeterRow(
                label = stringResource(R.string.chat_page_context_meter_warn),
                used = used,
                limit = warnTokens,
                showAbsolute = showAbsolute,
                hardLimit = false,
            )
        }
        if (maxTokens != null) {
            ContextMeterRow(
                label = stringResource(R.string.chat_page_context_meter_max),
                used = used,
                limit = maxTokens,
                showAbsolute = showAbsolute,
                hardLimit = true,
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

@Composable
private fun ContextMeterRow(
    label: String,
    used: Int,
    limit: Int,
    showAbsolute: Boolean,
    hardLimit: Boolean,
) {
    val fraction = contextUsageFraction(used, limit)
    val fillColor = contextMeterColor(fraction, hardLimit)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        label = "contextMeterFill",
    )
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
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFraction)
                    .clip(CircleShape)
                    .background(fillColor),
            )
        }
        Text(
            text = if (showAbsolute) {
                stringResource(
                    R.string.chat_page_context_meter_absolute,
                    used.formatNumber(),
                    limit.formatNumber(),
                )
            } else {
                "${(fraction * 100).roundToInt()}%"
            },
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = textColor,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 56.dp),
        )
    }
}

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
    showAbsolute: Boolean,
    onToggleDisplay: () -> Unit,
    modifier: Modifier = Modifier,
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
            .clickable(onClick = onToggleDisplay)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            if (warnTokens != null) {
                RingValueRow(
                    label = "WARN",
                    used = used,
                    limit = warnTokens,
                    showAbsolute = showAbsolute,
                    color = warnColor,
                )
            }
            if (maxTokens != null) {
                RingValueRow(
                    label = "MAX",
                    used = used,
                    limit = maxTokens,
                    showAbsolute = showAbsolute,
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
        Spacer(Modifier.width(10.dp))
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
                text = "${(mainFraction * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = mainColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RingValueRow(
    label: String,
    used: Int,
    limit: Int,
    showAbsolute: Boolean,
    color: Color,
) {
    val fraction = contextUsageFraction(used, limit)
    val text = if (showAbsolute) {
        stringResource(
            R.string.chat_page_context_meter_absolute,
            used.formatNumber(),
            limit.formatNumber(),
        )
    } else {
        "${(fraction * 100).roundToInt()}%"
    }
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
            color = if (fraction >= ALERT_FRACTION) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ContextUsageMeterPreview() {
    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ContextUsageMeter(
                tokens = 62_000,
                warnTokens = 64_000,
                maxTokens = 200_000,
                showAbsolute = false,
                onToggleDisplay = {},
            )
            ContextUsageMeter(
                tokens = 62_000,
                warnTokens = 64_000,
                maxTokens = 200_000,
                showAbsolute = true,
                onToggleDisplay = {},
            )
            ContextUsageMeter(
                tokens = 210_000,
                warnTokens = 64_000,
                maxTokens = 200_000,
                showAbsolute = true,
                onToggleDisplay = {},
            )
        }
    }
}
