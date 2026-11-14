package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import me.rerere.ai.provider.ProviderLockState
import me.rerere.ai.provider.ProviderQuota
import me.rerere.ai.provider.ProviderQuotaMetric
import me.rerere.ai.provider.ProviderQuotaWindow
import me.rerere.ai.provider.ProviderQuotaWindowUnit
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.isBalanceLow
import me.rerere.ai.provider.supportsQuota
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert02
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.DashboardSpeed02
import me.rerere.hugeicons.stroke.MoneyBag02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.ProviderGate
import me.rerere.rikkahub.data.ai.ProviderGateStatus
import me.rerere.rikkahub.data.ai.ProviderWindowUsage
import me.rerere.rikkahub.data.ai.formatGuardTime
import me.rerere.rikkahub.data.ai.label
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.effectiveBackgroundEffect
import me.rerere.rikkahub.ui.theme.LocalFullWidthComponents
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPaddingValue
import me.rerere.rikkahub.ui.theme.appButtonShadow
import me.rerere.rikkahub.ui.theme.rememberEffectsSpec
import me.rerere.rikkahub.ui.theme.rememberFastSpatialSpec
import me.rerere.rikkahub.ui.theme.rememberSpatialSpec
import me.rerere.rikkahub.utils.formatCost
import me.rerere.rikkahub.utils.formatNumber
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/** 折叠条每 5 秒刷新一次本地用量，和高级设置里的状态卡一致 */
private const val STATUS_REFRESH_MILLIS = 5_000L

/** 展开后的看板最高占多少高度，内容更多时在看板内部滚动，不能把聊天区挤没 */
private val DashboardMaxHeight = 280.dp

/**
 * 顶栏下方的限额折叠条。
 *
 * 收起时是一行摘要（提供商名 + 最近一分钟与各时间窗的用量），点击后在下方展开看板：
 * 先是 [ProviderSetting.guard] 里的 RPM / TPM / 自定义时间段限额，随后是提供商返回的
 * 订阅用量（[ProviderQuota]）；没配限流、也没额度可查时给一条空状态说明。
 *
 * 哪些渠道有东西可看由提供商自己的配置决定：高级设置里没开限流、也没有额度查询时，
 * 这里只显示空状态，不会凭空造出数字。
 */
@Composable
fun ChatQuotaStrip(
    provider: ProviderSetting,
    displaySetting: DisplaySetting,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val gate = koinInject<ProviderGate>()
    val guard = provider.guard
    var expanded by rememberSaveable(provider.id.toString()) { mutableStateOf(false) }

    var status by remember(provider.id) { mutableStateOf<ProviderGateStatus?>(null) }
    var lockState by remember(provider.id) { mutableStateOf(gate.lockState(provider)) }
    LaunchedEffect(provider.id, guard) {
        while (true) {
            lockState = gate.lockState(provider)
            if (guard.hasRateLimit) {
                status = runCatching { gate.status(provider) }.getOrNull()
            }
            delay(STATUS_REFRESH_MILLIS)
        }
    }

    // 委托属性的读取每次都会走 getter，先取成局部值，下面既少读几次，也能正常做空判断
    val gateStatus = status
    val quotaResult = if (provider.supportsQuota) {
        rememberProviderQuota(provider, guard.quotaRefreshSeconds * 1_000L)
    } else {
        null
    }
    val quota = quotaResult?.getOrNull()

    val quotaLabel = stringResource(R.string.chat_quota_bar_quota)
    val quotaFailureLabel = stringResource(R.string.setting_provider_page_usage_failed)
    val summaryParts = buildList {
        if (guard.rpmLimit > 0) add("RPM ${percentOf(gateStatus?.rpmUsed, guard.rpmLimit)}")
        if (guard.tpmLimit > 0) add("TPM ${percentOf(gateStatus?.tpmUsed, guard.tpmLimit)}")
        for (window in guard.windows) {
            if (!window.isEnabled) continue
            val usage = gateStatus?.windows?.firstOrNull { it.window.id == window.id }
            val percent = when (window.metric) {
                ProviderQuotaMetric.REQUESTS, ProviderQuotaMetric.TOKENS ->
                    percentOf(usage?.used, window.limit)

                ProviderQuotaMetric.COST -> percentOf(usage?.costUsed, window.costLimit)
            }
            add("${window.compactLabel()} $percent")
        }
        when (quota) {
            is ProviderQuota.UsageLimits -> for (window in quota.windows) {
                add("${window.shortLabel().ifBlank { quotaLabel }} ${window.usedPercent.roundToInt()}%")
            }

            is ProviderQuota.Balance -> add(quota.value)
            null -> if (provider.supportsQuota && quotaResult?.isFailure == true) {
                add(quotaFailureLabel)
            }
        }
    }

    val lockedUntil = lockState.lockedUntil
    val nextLockAt = lockState.nextStart
    // 折叠条只看颜色：锁定、本机限流到顶、渠道额度到顶或余额低于预警线时整条转提醒色
    val rateExhausted = (guard.rpmLimit > 0 && gateStatus != null && gateStatus.rpmUsed >= guard.rpmLimit) ||
        (guard.tpmLimit > 0 && gateStatus != null && gateStatus.tpmUsed >= guard.tpmLimit) ||
        guard.windows.any { window ->
            window.isEnabled && window.isFull(
                gateStatus?.windows?.firstOrNull { usage -> usage.window.id == window.id }
            )
        }
    val quotaExhausted = (quota as? ProviderQuota.UsageLimits)?.let { limits ->
        limits.limitReached || limits.windows.any { it.usedPercent >= 100 }
    } == true
    val lowBalance = (quota as? ProviderQuota.Balance)?.let { balanceIsLow(provider, it) } == true
    val warning = lockedUntil != null || rateExhausted || quotaExhausted || lowBalance
    val summary = when {
        lockedUntil != null -> stringResource(
            R.string.setting_provider_guard_lock_current,
            formatGuardTime(context, lockedUntil),
        )

        summaryParts.isNotEmpty() -> summaryParts.take(4).joinToString(" · ")
        quotaResult == null && provider.supportsQuota ->
            stringResource(R.string.setting_provider_page_usage_loading)

        quota is ProviderQuota.UsageLimits ->
            stringResource(R.string.setting_provider_page_usage_none)

        nextLockAt != null -> stringResource(
            R.string.setting_provider_guard_lock_next,
            formatGuardTime(context, nextLockAt),
        )

        else -> stringResource(R.string.chat_quota_bar_empty)
    }

    val shape: RoundedCornerShape = if (LocalFullWidthComponents.current) {
        RoundedCornerShape(0.dp)
    } else {
        RoundedCornerShape(20.dp)
    }
    val effect = displaySetting.effectiveBackgroundEffect
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = rememberSpatialSpec(),
        label = "ChatQuotaStripChevron",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = adaptiveHorizontalPaddingValue(12.dp),
                end = adaptiveHorizontalPaddingValue(12.dp),
                bottom = 6.dp,
            )
            .appButtonShadow(shape, 2.dp)
            .clip(shape)
            .background(if (effect == BackgroundEffectType.OFF) containerColor else Color.Transparent)
            .chatInputHaze(
                hazeState = hazeState,
                displaySetting = displaySetting,
                containerColor = containerColor,
                shape = shape,
            )
            // 描边画在模糊层之后，否则会被糊掉
            .then(
                if (effect == BackgroundEffectType.BLUR) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = shape,
                    )
                } else {
                    Modifier
                }
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (warning) HugeIcons.Alert02 else HugeIcons.DashboardSpeed02,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (warning) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = HugeIcons.ArrowDown01,
                contentDescription = stringResource(
                    if (expanded) R.string.chat_quota_bar_collapse else R.string.chat_quota_bar_expand
                ),
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = rememberSpatialSpec(),
            ) + fadeIn(animationSpec = rememberEffectsSpec()),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = rememberFastSpatialSpec(),
            ) + fadeOut(animationSpec = rememberEffectsSpec()),
        ) {
            QuotaDashboard(
                provider = provider,
                binaryUnits = displaySetting.binaryUnits,
                status = gateStatus,
                lockState = lockState,
                quotaResult = quotaResult,
            )
        }
    }
}

@Composable
private fun QuotaDashboard(
    provider: ProviderSetting,
    binaryUnits: Boolean,
    status: ProviderGateStatus?,
    lockState: ProviderLockState,
    quotaResult: Result<ProviderQuota>?,
) {
    val context = LocalContext.current
    val guard = provider.guard
    val windows = guard.windows.filter { it.isEnabled }
    val locks = guard.locks.filter { it.enabled }
    val hasRateLimits = guard.rpmLimit > 0 || guard.tpmLimit > 0 || windows.isNotEmpty()
    val hasQuota = provider.supportsQuota
    val quota = quotaResult?.getOrNull()
    val gateStatus = status

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = DashboardMaxHeight)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        if (!hasRateLimits && !hasQuota && locks.isEmpty()) {
            Text(
                text = stringResource(R.string.chat_quota_bar_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (hasRateLimits) {
                SectionLabel(stringResource(R.string.chat_quota_bar_rate_limits))

                if (guard.rpmLimit > 0) {
                    LimitProgressRow(
                        label = stringResource(R.string.setting_provider_guard_rpm),
                        percent = percentOf(gateStatus?.rpmUsed, guard.rpmLimit),
                        detail = status?.let {
                            stringResource(
                                R.string.setting_provider_guard_used,
                                it.rpmUsed.formatNumber(binaryUnits),
                                guard.rpmLimit.formatNumber(binaryUnits),
                            )
                        },
                        fraction = fractionOf(status?.rpmUsed, guard.rpmLimit),
                        reached = status != null && status.rpmUsed >= guard.rpmLimit,
                    )
                }

                if (guard.tpmLimit > 0) {
                    LimitProgressRow(
                        label = stringResource(R.string.setting_provider_guard_tpm),
                        percent = percentOf(gateStatus?.tpmUsed, guard.tpmLimit),
                        detail = status?.let {
                            stringResource(
                                R.string.setting_provider_guard_used,
                                it.tpmUsed.formatNumber(binaryUnits),
                                guard.tpmLimit.formatNumber(binaryUnits),
                            )
                        },
                        fraction = fractionOf(status?.tpmUsed, guard.tpmLimit),
                        reached = status != null && status.tpmUsed >= guard.tpmLimit,
                    )
                }

                for (window in windows) {
                    val usage = gateStatus?.windows?.firstOrNull { it.window.id == window.id }
                    val isCost = window.metric == ProviderQuotaMetric.COST
                    val metric = stringResource(
                        when (window.metric) {
                            ProviderQuotaMetric.REQUESTS -> R.string.setting_provider_guard_metric_requests
                            ProviderQuotaMetric.TOKENS -> R.string.setting_provider_guard_metric_tokens
                            ProviderQuotaMetric.COST -> R.string.setting_provider_guard_window_cost_limit
                        }
                    )
                    val limitText = if (isCost) {
                        window.costLimit.formatCost()
                    } else {
                        window.limit.formatNumber(binaryUnits)
                    }
                    val usedText = when {
                        usage == null -> null
                        isCost -> stringResource(
                            R.string.setting_provider_guard_used,
                            usage.costUsed.formatCost(),
                            window.costLimit.formatCost(),
                        )

                        else -> stringResource(
                            R.string.setting_provider_guard_used,
                            usage.used.formatNumber(binaryUnits),
                            window.limit.formatNumber(binaryUnits),
                        )
                    }
                    val resetText = usage?.resetAt?.let { resetAt ->
                        stringResource(
                            R.string.setting_provider_guard_reset_at,
                            formatGuardTime(context, resetAt),
                        )
                    }
                    LimitProgressRow(
                        label = stringResource(
                            R.string.setting_provider_guard_window_summary,
                            window.label(context),
                            metric,
                            limitText,
                        ),
                        percent = when (window.metric) {
                            ProviderQuotaMetric.REQUESTS, ProviderQuotaMetric.TOKENS ->
                                percentOf(usage?.used, window.limit)

                            ProviderQuotaMetric.COST -> percentOf(usage?.costUsed, window.costLimit)
                        },
                        detail = listOfNotNull(usedText, resetText).joinToString(" · ")
                            .ifBlank { null },
                        fraction = when (window.metric) {
                            ProviderQuotaMetric.REQUESTS, ProviderQuotaMetric.TOKENS ->
                                fractionOf(usage?.used, window.limit)

                            ProviderQuotaMetric.COST -> fractionOf(usage?.costUsed, window.costLimit)
                        },
                        reached = usage != null && if (isCost) {
                            usage.costUsed >= window.costLimit
                        } else {
                            usage.used >= window.limit
                        },
                    )
                }
            }

            if (hasQuota) {
                SectionLabel(stringResource(R.string.chat_quota_bar_quota))
                when {
                    quotaResult == null -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ContainedLoadingIndicator(modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(R.string.setting_provider_page_usage_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    quota == null -> Text(
                        text = stringResource(R.string.setting_provider_page_usage_failed) +
                            quotaResult?.exceptionOrNull()?.message?.let { "：$it" }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    quota is ProviderQuota.Balance -> BalanceRow(provider = provider, balance = quota)

                    quota is ProviderQuota.UsageLimits -> if (quota.windows.isEmpty()) {
                        Text(
                            text = stringResource(R.string.setting_provider_page_usage_none),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        for (window in quota.windows) {
                            LimitProgressRow(
                                label = window.longLabel(),
                                percent = "${window.usedPercent.roundToInt()}%",
                                detail = window.resetsAt?.let { resetsAt ->
                                    stringResource(
                                        R.string.setting_provider_page_usage_resets,
                                        formatGuardTime(context, resetsAt),
                                    )
                                },
                                fraction = (window.usedPercent / 100).toFloat().coerceIn(0f, 1f),
                                reached = quota.limitReached || window.usedPercent >= 100,
                            )
                        }
                    }
                }
            }

            if (locks.isNotEmpty()) {
                LockStatusRow(lockState = lockState)
            }
        }
    }
}

@Composable
private fun BalanceRow(
    provider: ProviderSetting,
    balance: ProviderQuota.Balance,
) {
    val low = balanceIsLow(provider, balance)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (low) HugeIcons.Alert02 else HugeIcons.MoneyBag02,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.chat_quota_bar_balance),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = balance.value,
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = if (low) MaterialTheme.colorScheme.error else Color.Unspecified,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LockStatusRow(lockState: ProviderLockState) {
    val context = LocalContext.current
    val lockedUntil = lockState.lockedUntil
    val nextLockAt = lockState.nextStart
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = HugeIcons.Clock01,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (lockedUntil != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = stringResource(R.string.setting_provider_guard_locks),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = when {
                lockedUntil != null -> stringResource(
                    R.string.setting_provider_guard_lock_current,
                    formatGuardTime(context, lockedUntil),
                )

                nextLockAt != null -> stringResource(
                    R.string.setting_provider_guard_lock_next,
                    formatGuardTime(context, nextLockAt),
                )

                else -> stringResource(R.string.setting_provider_guard_lock_none)
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (lockedUntil != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun LimitProgressRow(
    label: String,
    percent: String,
    detail: String?,
    fraction: Float,
    reached: Boolean,
) {
    val indicatorColor = if (reached) {
        MaterialTheme.colorScheme.error
    } else {
        ProgressIndicatorDefaults.linearColor
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = percent,
                style = MaterialTheme.typography.labelMedium,
                color = if (reached) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        LinearWavyProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = indicatorColor,
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = if (reached) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun percentOf(used: Int?, limit: Int): String =
    if (used == null || limit <= 0) "…" else "${(used * 100.0 / limit).roundToInt()}%"

private fun fractionOf(used: Int?, limit: Int): Float =
    if (used == null || limit <= 0) 0f else (used.toFloat() / limit).coerceIn(0f, 1f)

private fun percentOf(used: Double?, limit: Double): String =
    if (used == null || limit <= 0.0) "…" else "${(used / limit * 100).roundToInt()}%"

private fun fractionOf(used: Double?, limit: Double): Float =
    if (used == null || limit <= 0.0) 0f else (used / limit).toFloat().coerceIn(0f, 1f)

private fun balanceIsLow(provider: ProviderSetting, balance: ProviderQuota.Balance): Boolean {
    val warning = (provider as? ProviderSetting.ApiKey)?.quotaQuery?.balanceWarning ?: return false
    if (warning.isBlank()) return false
    return isBalanceLow(balance.value, warning)
}

private fun ProviderQuotaWindow.isFull(usage: ProviderWindowUsage?): Boolean {
    usage ?: return false
    return when (metric) {
        ProviderQuotaMetric.REQUESTS, ProviderQuotaMetric.TOKENS -> usage.used >= limit
        ProviderQuotaMetric.COST -> usage.costUsed >= costLimit
    }
}

private fun ProviderQuotaWindow.compactLabel(): String {
    val count = amount.coerceAtLeast(1)
    return when (unit) {
        ProviderQuotaWindowUnit.MINUTE -> "${count}m"
        ProviderQuotaWindowUnit.HOUR -> "${count}h"
        ProviderQuotaWindowUnit.DAY -> "${count}d"
        ProviderQuotaWindowUnit.WEEK -> "${count}w"
        ProviderQuotaWindowUnit.MONTH -> "${count}mo"
    }
}
