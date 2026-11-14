package me.rerere.rikkahub.ui.components.message

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.core.sum
import me.rerere.ai.provider.PriceRuleContext
import me.rerere.ai.provider.UsageCost
import me.rerere.ai.provider.estimateCost
import me.rerere.ai.provider.priceUnitTokens
import me.rerere.ai.ui.UIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.Layers01
import me.rerere.hugeicons.stroke.MoneyBag02
import me.rerere.hugeicons.stroke.Repeat
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.model.effectiveContextWindow
import me.rerere.rikkahub.data.model.TokenBreakdown
import me.rerere.rikkahub.data.model.TokenBucket
import me.rerere.rikkahub.data.model.TokenDestination
import me.rerere.rikkahub.data.model.buildTokenBreakdown
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.modifier.rememberMotionBlurSteps
import me.rerere.rikkahub.ui.theme.rememberEffectsSpec
import me.rerere.rikkahub.ui.theme.rememberFastSpatialSpec
import me.rerere.rikkahub.ui.theme.rememberMotionBlurSpec
import me.rerere.rikkahub.ui.theme.rememberSpatialSpec
import me.rerere.rikkahub.utils.formatCost
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.rikkahub.utils.toDp
import me.rerere.rikkahub.utils.toFixed
import java.text.NumberFormat
import java.time.Duration
import kotlin.math.roundToInt

/**
 * 显示消息的技术统计信息（如 token 使用量），点击查看详情
 *
 * 上下文取最后一次请求；多步工具调用时行内还会显示这一轮累计的 token 和费用，明细在详情里。
 * 行内固定两行：生成指标在上，token 与上下文在下，超宽时末尾省略，避免边生成边折行。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMessageNerdLine(
    message: UIMessage,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
) {
    val settings = LocalSettings.current
    val usage = message.usage
    // 重新生成/切分支时 usage 会先变成 null，收起的动画里还得有上一份数字可画，
    // 否则统计行会在退场的第一帧塌成空白
    var lastUsage by remember(message.id) { mutableStateOf(usage) }
    SideEffect { if (usage != null) lastUsage = usage }
    val shownUsage = usage ?: lastUsage
    val visible = settings.displaySetting.showTokenUsage && usage != null
    // 与英雄卡同一套收放：先按可见性滚动过渡，等动画收完再把这一行整体摘掉，
    // 不然它会在气泡下面留一段 4dp 的空档
    val visibility = remember(message.id) { MutableTransitionState(visible) }
    LaunchedEffect(visible) { visibility.targetState = visible }
    val keepLine = visible || visibility.currentState || !visibility.isIdle
    if (shownUsage == null || !keepLine) return

    val stats = remember(message, shownUsage) { message.nerdStats(shownUsage) }
    // 消息里的模型快照不带上下文窗口，只能从设置里按 ID 找；模型被删了就不显示窗口大小
    val contextLength = remember(settings.providers, message.modelId) {
        settings.findModelById(message.modelId)?.effectiveContextWindow
    }
    val binaryUnits = settings.displaySetting.binaryUnits
    // 优先用每步入账时缓存的费用；旧消息没有快照时才按当前价目回退估算
    val cost = remember(
        settings.providers,
        message.modelId,
        message.createdAt,
        message.cost,
        message.costTracked,
        shownUsage,
        stats.lastUsage,
        stats.steps,
        binaryUnits,
    ) {
        message.cost ?: if (message.costTracked) {
            UsageCost()
        } else {
            message.estimatedCost(settings, binaryUnits)
        }
    }
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()

    // 生成结束拿到 usage 的一刻，这一行和加载图标的退场同时发生：
    // 数字带模糊渐显、行高从上往下展开；重新生成清掉 usage 时反向收起
    val blurSteps = rememberMotionBlurSteps(maxRadius = 6.dp)
    val enterSize = rememberSpatialSpec<IntSize>()
    val exitSize = rememberFastSpatialSpec<IntSize>()
    val fadeSpec = rememberEffectsSpec<Float>()
    // 模糊跟着动态模糊设置里的时长走，位移仍按动画速度档位
    val enterBlurSpec = rememberMotionBlurSpec()
    val exitBlurSpec = rememberMotionBlurSpec(scale = 0.75f)
    AnimatedVisibility(
        visibleState = visibility,
        modifier = modifier,
        enter = expandVertically(animationSpec = enterSize) + fadeIn(fadeSpec),
        exit = shrinkVertically(animationSpec = exitSize) + fadeOut(fadeSpec),
        label = "nerdLine",
    ) {
        val blur by transition.animateFloat(
            transitionSpec = {
                if (targetState == EnterExitState.Visible) enterBlurSpec else exitBlurSpec
            },
            label = "nerdLineBlur",
        ) { state ->
            if (state == EnterExitState.Visible) 0f else 1f
        }
        Box(
            modifier = Modifier.graphicsLayer { renderEffect = blurSteps.at(blur) },
        ) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = {
                    RichTooltip(
                        title = { Text(stringResource(R.string.chat_message_usage_title)) },
                    ) {
                        UsageDetails(stats, contextLength, cost)
                    }
                },
                state = tooltipState,
                enableUserInput = false,
            ) {
                // 等宽数字：流式生成时数值逐位变长，同一行里的其他项不会跟着左右挪
                val statsTextStyle = MaterialTheme.typography.labelSmall.copy(
                    color = color,
                    fontFeatureSettings = "tnum",
                )
                // 统计行固定两行，某一行暂时没有内容（速度、费用要到生成结束才有）也占住行高，
                // 否则整块统计会在生成过程中反复长高、塌陷
                val lineHeight = statsTextStyle.lineHeight
                    .takeOrElse { statsTextStyle.fontSize * 1.4f }
                    .toDp()
                ProvideTextStyle(statsTextStyle) {
                    CompositionLocalProvider(LocalContentColor provides color) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { scope.launch { tooltipState.show() } }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            StatsLine(minHeight = lineHeight) {
                                if (stats.steps > 1) {
                                    StatsItem(
                                        icon = { StatsIcon(HugeIcons.Repeat, stringResource(R.string.chat_message_usage_step_count)) },
                                        content = { StatsText(stringResource(R.string.chat_message_usage_steps, stats.steps)) },
                                    )
                                }
                                if (stats.tps != null) {
                                    StatsItem(
                                        icon = { StatsIcon(HugeIcons.Zap, stringResource(R.string.chat_message_usage_speed)) },
                                        content = { StatsText("${stats.tps.toFixed(1)} tok/s") },
                                    )
                                }
                                if (cost.total > 0) {
                                    StatsItem(
                                        icon = { StatsIcon(HugeIcons.MoneyBag02, stringResource(R.string.chat_message_usage_cost)) },
                                        content = { StatsText(cost.total.formatCost()) },
                                    )
                                }
                            }
                            StatsLine(minHeight = lineHeight) {
                                StatsItem(
                                    icon = { StatsIcon(HugeIcons.Layers01, stringResource(R.string.chat_message_usage_context)) },
                                    content = {
                                        StatsText(
                                            stringResource(
                                                R.string.chat_message_usage_context_short,
                                                stats.contextTokens.formatNumber(binaryUnits),
                                            )
                                        )
                                        val cached = stats.lastUsage.cachedTokens
                                        if (cached > 0) {
                                            StatsText(
                                                stringResource(
                                                    R.string.chat_message_usage_cached_short,
                                                    cached.formatNumber(binaryUnits),
                                                )
                                            )
                                        }
                                    },
                                )
                                if (stats.steps > 1) {
                                    StatsItem(
                                        icon = { StatsIcon(HugeIcons.ChartColumn, stringResource(R.string.chat_message_usage_total)) },
                                        content = {
                                            StatsText(
                                                stringResource(
                                                    R.string.chat_message_usage_total_tokens,
                                                    (stats.total.promptTokens + stats.total.completionTokens)
                                                        .formatNumber(binaryUnits),
                                                )
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageDetails(stats: NerdStats, contextLength: Int?, cost: UsageCost) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        val total = stats.total
        ContextUsageBar(
            lastUsage = stats.lastUsage,
            contextLength = contextLength,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (stats.steps > 1) {
            UsageDetailRow(stringResource(R.string.chat_message_usage_step_count), stats.steps.toString())
            UsageDetailRow(stringResource(R.string.chat_message_usage_total_input), total.promptTokens.exact())
            if (total.cachedTokens > 0) UsageDetailRow(stringResource(R.string.chat_message_usage_total_cached), total.cachedTokens.exact())
            UsageDetailRow(stringResource(R.string.chat_message_usage_total_output), total.completionTokens.exact())
        }
        UsageCostRows(cost)
        if (stats.tps != null) UsageDetailRow(stringResource(R.string.chat_message_usage_speed), "${stats.tps.toFixed(1)} tok/s")
        if (stats.durationMillis != null) UsageDetailRow(stringResource(R.string.chat_message_usage_duration), "${(stats.durationMillis / 1000f).toFixed(1)}s")
    }
}

/**
 * 会话 Usage 看板：上下文取最近一次请求，其余数字按整段会话累计。
 */
@Composable
fun ConversationUsageDetails(
    messages: List<UIMessage>,
    contextLength: Int?,
    modifier: Modifier = Modifier,
) {
    val usageMessages = messages.filter { it.usage != null }
    if (usageMessages.isEmpty()) {
        Text(
            text = stringResource(R.string.chat_message_usage_empty),
            color = LocalContentColor.current.copy(alpha = 0.7f),
            modifier = modifier,
        )
        return
    }

    val settings = LocalSettings.current
    val binaryUnits = settings.displaySetting.binaryUnits
    val cost = remember(usageMessages, settings.providers, binaryUnits) {
        usageMessages.fold(UsageCost()) { acc, message ->
            val messageCost = message.cost ?: if (message.costTracked) {
                UsageCost()
            } else {
                message.estimatedCost(settings, binaryUnits)
            }
            acc + messageCost
        }
    }
    val lastMessage = usageMessages.last()
    val lastStats = remember(lastMessage) { lastMessage.nerdStats(lastMessage.usage!!) }
    val breakdown = remember(messages, lastMessage) {
        buildTokenBreakdown(messages, lastStats.lastUsage)
    }
    val total = usageMessages.fold(null as TokenUsage?) { acc, message -> acc.sum(message.usage) }
        ?: TokenUsage()
    val steps = usageMessages.sumOf { it.stepCount ?: 1 }
    val durations = usageMessages.mapNotNull { it.usageDurationMillis() }
    val durationMillis = durations.takeIf { it.isNotEmpty() }?.sum()
    val tps = durationMillis?.takeIf { it > 0 }?.let {
        total.completionTokens.toFloat() / it * 1000
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.padding(vertical = 4.dp),
    ) {
        ContextUsageBar(
            lastUsage = lastStats.lastUsage,
            contextLength = contextLength,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (breakdown.hasUsage) {
            TokenDestinationSection(breakdown = breakdown)
        }
        UsageDetailRow(stringResource(R.string.chat_message_usage_step_count), steps.toString())
        UsageDetailRow(stringResource(R.string.chat_message_usage_total_input), total.promptTokens.exact())
        if (total.cachedTokens > 0) {
            UsageDetailRow(stringResource(R.string.chat_message_usage_total_cached), total.cachedTokens.exact())
        }
        UsageDetailRow(stringResource(R.string.chat_message_usage_total_output), total.completionTokens.exact())
        UsageCostRows(cost)
        if (tps != null) {
            UsageDetailRow(stringResource(R.string.chat_message_usage_speed), "${tps.toFixed(1)} tok/s")
        }
        if (durationMillis != null) {
            UsageDetailRow(stringResource(R.string.chat_message_usage_duration), "${(durationMillis / 1000f).toFixed(1)}s")
        }
    }
}

/** 最近一次请求的输出速度，和 Usage 看板里的 tok/s 一致 */
fun UIMessage.usageTokenSpeed(): Float? {
    val usage = usage ?: return null
    return nerdStats(usage).tps
}

private data class UsageSegment(val label: String, val tokens: Int, val color: Color)

/**
 * 最后一次请求的上下文占用：缓存命中、未命中的输入、输出依次排开，剩下的是窗口里的空余
 */
@Composable
private fun ContextUsageBar(
    lastUsage: TokenUsage,
    contextLength: Int?,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val segments = listOf(
        UsageSegment(stringResource(R.string.chat_message_usage_cached), lastUsage.cachedTokens, colorScheme.primary),
        UsageSegment(
            stringResource(R.string.chat_message_usage_input),
            (lastUsage.promptTokens - lastUsage.cachedTokens).coerceAtLeast(0),
            colorScheme.primary.copy(alpha = 0.45f),
        ),
        UsageSegment(stringResource(R.string.chat_message_usage_output), lastUsage.completionTokens, colorScheme.tertiary),
    ).filter { it.tokens > 0 }
    val contextTokens = lastUsage.promptTokens + lastUsage.completionTokens
    // 窗口未知或已经超出时，占用本身铺满整条
    val capacity = maxOf(contextLength ?: 0, contextTokens, 1)
    val hasFreeSpace = capacity > contextTokens
    val trackColor = colorScheme.surfaceContainerHighest
    val displaySetting = LocalSettings.current.displaySetting
    val pieChart = displaySetting.usagePieChart
    val binaryUnits = displaySetting.binaryUnits

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.chat_message_usage_context),
                color = LocalContentColor.current.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
            )
            if (contextLength != null) {
                val fraction = contextTokens.toFloat() / contextLength
                Text(
                    text = "${contextTokens.formatNumber(binaryUnits)} / ${contextLength.formatNumber(binaryUnits)} · ${(fraction * 100).toFixed(0)}%",
                    color = if (fraction >= 0.9f) colorScheme.error else Color.Unspecified,
                )
            } else {
                Text(text = contextTokens.exact())
            }
        }
        if (pieChart) {
            UsagePie(
                segments = segments,
                capacity = capacity,
                contextTokens = contextTokens,
                hasFreeSpace = hasFreeSpace,
                trackColor = trackColor,
            )
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            ) {
                val gap = 2.dp.toPx()
                val radius = CornerRadius(size.height / 2)
                // 每段之间留出间隔；还有空余时最后一段和空余之间也要留
                val gaps = if (hasFreeSpace) segments.size else segments.size - 1
                val available = size.width - gap * gaps
                var x = 0f
                segments.forEach { segment ->
                    if (x >= size.width) return@forEach
                    // 太小的段至少画成一个圆点，不然看不出来
                    val width = (segment.tokens.toFloat() / capacity * available).coerceAtLeast(size.height)
                    drawRoundRect(
                        color = segment.color,
                        topLeft = Offset(x, 0f),
                        size = Size(width.coerceAtMost(size.width - x), size.height),
                        cornerRadius = radius,
                    )
                    x += width + gap
                }
                if (hasFreeSpace && x < size.width) {
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(x, 0f),
                        size = Size(size.width - x, size.height),
                        cornerRadius = radius,
                    )
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            segments.forEach { segment ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(segment.color, CircleShape)
                    )
                    Text(
                        text = "${segment.label} ${segment.tokens.exact()}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

/**
 * 上下文占用饼图：缓存命中、未命中输入、输出各占一段，剩余为窗口空余。
 */
@Composable
private fun UsagePie(
    segments: List<UsageSegment>,
    capacity: Int,
    contextTokens: Int,
    hasFreeSpace: Boolean,
    trackColor: Color,
) {
    val total = capacity.toFloat().coerceAtLeast(1f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(76.dp)) {
            val strokeWidth = 12.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            var start = -90f
            segments.forEach { segment ->
                val sweep = 360f * segment.tokens / total
                drawArc(
                    color = segment.color,
                    startAngle = start,
                    sweepAngle = (sweep - 1.5f).coerceAtLeast(1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth),
                )
                start += sweep
            }
            if (hasFreeSpace) {
                val sweep = 360f * (capacity - contextTokens) / total
                drawArc(
                    color = trackColor,
                    startAngle = start,
                    sweepAngle = (sweep - 1.5f).coerceAtLeast(1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth),
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.weight(1f),
        ) {
            segments.forEach { segment ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(modifier = Modifier.size(6.dp).background(segment.color, CircleShape))
                    Text(
                        text = segment.label,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = segment.tokens.exact(), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (hasFreeSpace) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(modifier = Modifier.size(6.dp).background(trackColor, CircleShape))
                    Text(
                        text = stringResource(R.string.chat_message_usage_free),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = (capacity - contextTokens).exact(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

/**
 * Token 去向：输入侧按内容分类估算后缩放到真实 promptTokens，输出侧拆思考与正文。
 */
@Composable
private fun TokenDestinationSection(
    breakdown: TokenBreakdown,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.chat_message_usage_destination),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = "${breakdown.promptTotal.exact()} in / ${breakdown.completionTotal.exact()} out",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = LocalContentColor.current.copy(alpha = 0.6f),
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        if (breakdown.promptBuckets.isNotEmpty()) {
            Text(
                text = "${stringResource(R.string.chat_message_usage_destination_input)} · " +
                    breakdown.promptTotal.exact(),
                style = MaterialTheme.typography.labelSmall,
                color = LocalContentColor.current.copy(alpha = 0.6f),
            )
            TokenStackBar(
                buckets = breakdown.promptBuckets,
                total = breakdown.promptTotal,
            )
            breakdown.promptBuckets.forEach { bucket ->
                UsageDetailRow(
                    label = bucket.destination.label(),
                    value = "${bucket.tokens.exact()} · ${bucket.percentOf(breakdown.promptTotal)}%",
                )
            }
            if (breakdown.cachedTokens > 0) {
                UsageDetailRow(
                    label = stringResource(R.string.chat_message_usage_destination_cached),
                    value = "${breakdown.cachedTokens.exact()} · " +
                        "${(breakdown.cachedTokens * 100f / breakdown.promptTotal).roundToInt()}%",
                )
            }
        }
        if (breakdown.completionBuckets.isNotEmpty()) {
            Text(
                text = "${stringResource(R.string.chat_message_usage_destination_output)} · " +
                    breakdown.completionTotal.exact(),
                style = MaterialTheme.typography.labelSmall,
                color = LocalContentColor.current.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp),
            )
            TokenStackBar(
                buckets = breakdown.completionBuckets,
                total = breakdown.completionTotal,
            )
            breakdown.completionBuckets.forEach { bucket ->
                UsageDetailRow(
                    label = bucket.destination.label(),
                    value = "${bucket.tokens.exact()} · ${bucket.percentOf(breakdown.completionTotal)}%",
                )
            }
        }
        Text(
            text = stringResource(R.string.chat_message_usage_destination_hint),
            style = MaterialTheme.typography.labelSmall,
            color = LocalContentColor.current.copy(alpha = 0.5f),
        )
    }
}

private fun TokenBucket.percentOf(total: Int): Int =
    if (total > 0) (tokens * 100f / total).roundToInt() else 0

@Composable
private fun TokenDestination.label(): String = stringResource(
    when (this) {
        TokenDestination.SYSTEM -> R.string.chat_message_usage_destination_system
        TokenDestination.INPUT -> R.string.chat_message_usage_destination_user
        TokenDestination.HISTORY -> R.string.chat_message_usage_destination_history
        TokenDestination.TOOL -> R.string.chat_message_usage_destination_tool
        TokenDestination.REASONING -> R.string.chat_message_usage_destination_reasoning
        TokenDestination.OUTPUT -> R.string.chat_message_usage_destination_reply
    }
)

@Composable
private fun destinationColor(destination: TokenDestination): Color {
    val colors = MaterialTheme.colorScheme
    return when (destination) {
        TokenDestination.SYSTEM -> colors.outline
        TokenDestination.INPUT -> colors.primary
        TokenDestination.HISTORY -> colors.primary.copy(alpha = 0.5f)
        TokenDestination.TOOL -> colors.tertiary
        TokenDestination.REASONING -> colors.secondary
        TokenDestination.OUTPUT -> colors.tertiary
    }
}

@Composable
private fun TokenStackBar(
    buckets: List<TokenBucket>,
    total: Int,
) {
    if (total <= 0 || buckets.isEmpty()) return
    val colors = buckets.map { destinationColor(it.destination) }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(3.dp)),
    ) {
        var x = 0f
        buckets.forEachIndexed { i, bucket ->
            val width = size.width * bucket.tokens / total
            drawRect(
                color = colors[i],
                topLeft = Offset(x, 0f),
                size = Size((width - 1.dp.toPx()).coerceAtLeast(1f), size.height),
            )
            x += width
        }
    }
}

@Composable
private fun UsageCostRows(cost: UsageCost) {
    if (cost.total <= 0) return
    UsageDetailRow(stringResource(R.string.chat_message_usage_cost), cost.total.formatCost())
    if (cost.input > 0) {
        UsageDetailRow(stringResource(R.string.setting_provider_page_price_input), cost.input.formatCost())
    }
    if (cost.output > 0) {
        UsageDetailRow(stringResource(R.string.setting_provider_page_price_output), cost.output.formatCost())
    }
    if (cost.cacheHit > 0) {
        UsageDetailRow(stringResource(R.string.setting_provider_page_price_cache_hit), cost.cacheHit.formatCost())
    }
    if (cost.cacheWrite > 0) {
        UsageDetailRow(stringResource(R.string.setting_provider_page_price_cache_write), cost.cacheWrite.formatCost())
    }
    if (cost.cacheRead > 0) {
        UsageDetailRow(stringResource(R.string.setting_provider_page_price_cache_read), cost.cacheRead.formatCost())
    }
    if (cost.perCall > 0) {
        UsageDetailRow(stringResource(R.string.setting_provider_page_price_per_call), cost.perCall.formatCost())
    }
}

@Composable
private fun UsageDetailRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.Start),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            color = LocalContentColor.current.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f),
        )
        Text(text = value)
    }
}

@Composable
private fun StatsIcon(imageVector: ImageVector, contentDescription: String) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = Modifier.size(12.dp)
    )
}

/** 统计行：固定占一行高度，内容放不下时交给最后一个元素省略，不换行 */
@Composable
private fun StatsLine(
    minHeight: Dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.heightIn(min = minHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** 统计行里的文字只占一行，超出宽度省略，数值变化不会把同行内容挤走 */
@Composable
private fun StatsText(text: String) {
    Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun StatsItem(
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        icon()
        content()
    }
}

private data class NerdStats(
    val total: TokenUsage,
    val lastUsage: TokenUsage,
    val steps: Int,
    val contextTokens: Int,
    val durationMillis: Long?,
    val tps: Float?,
)

// 旧消息没有记录生成耗时，退回用创建到完成的总时长估算
private fun UIMessage.usageDurationMillis(): Long? = finishedAt?.let { finishedAt ->
    generationTimeMs ?: Duration.between(
        createdAt.toJavaLocalDateTime(),
        finishedAt.toJavaLocalDateTime()
    ).toMillis()
}?.takeIf { it > 0 }

private fun UIMessage.nerdStats(usage: TokenUsage): NerdStats {
    val last = lastRequestUsage() ?: usage
    val durationMillis = usageDurationMillis()
    return NerdStats(
        total = usage,
        lastUsage = last,
        steps = stepCount ?: 1,
        contextTokens = last.promptTokens + last.completionTokens,
        durationMillis = durationMillis,
        tps = durationMillis?.let { usage.completionTokens.toFloat() / it * 1000 },
    )
}

private fun Int.exact(): String = NumberFormat.getIntegerInstance().format(this)

/**
 * 旧消息没有费用快照时的回退估算：按当前价目和最后一次请求的上下文算。
 * 新消息在 AgentLoop 里已经按每步入账，不应再走这里。
 */
private fun UIMessage.estimatedCost(settings: Settings, binaryUnits: Boolean): UsageCost {
    val messageUsage = usage ?: TokenUsage()
    return settings.findModelById(modelId)
        ?.pricing
        ?.estimateCost(
            usage = messageUsage,
            unitTokens = priceUnitTokens(binaryUnits),
            calls = stepCount ?: 1,
            context = priceRuleContext(lastUsage ?: messageUsage),
        )
        ?: UsageCost()
}

/** 变价规则按消息的生成时间和一次请求的 token 快照判断；多步消息传最后一次请求的 usage */
private fun UIMessage.priceRuleContext(usage: TokenUsage) = PriceRuleContext(
    atMillis = createdAt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
    promptTokens = usage.promptTokens.toLong(),
    completionTokens = usage.completionTokens.toLong(),
    cachedTokens = usage.cachedTokens.toLong(),
)
