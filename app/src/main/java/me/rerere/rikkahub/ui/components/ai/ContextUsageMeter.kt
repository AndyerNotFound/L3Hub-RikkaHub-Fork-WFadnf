package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
