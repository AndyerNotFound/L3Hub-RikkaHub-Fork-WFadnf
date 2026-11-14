package me.rerere.rikkahub.ui.pages.setting.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderGuard
import me.rerere.ai.provider.ProviderLockRepeat
import me.rerere.ai.provider.ProviderLockSchedule
import me.rerere.ai.provider.ProviderQuotaMetric
import me.rerere.ai.provider.ProviderQuotaWindow
import me.rerere.ai.provider.ProviderQuotaWindowUnit
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.RequestOverride
import me.rerere.ai.provider.validate
import me.rerere.ai.provider.variableHint
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.ProviderGate
import me.rerere.rikkahub.data.ai.ProviderGateStatus
import me.rerere.rikkahub.data.ai.ProviderWindowUsage
import me.rerere.rikkahub.data.ai.formatGuardDuration
import me.rerere.rikkahub.data.ai.formatGuardTime
import me.rerere.rikkahub.data.ai.label
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.WeekdayPickerDialog
import me.rerere.rikkahub.ui.components.ui.glassSegmentedButtonColors
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.components.ui.weekdayLabel
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.terminal.Switch
import me.rerere.rikkahub.ui.terminal.TextButton
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.utils.formatCost
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.ui.components.AnimatedAlertDialog
import org.koin.compose.koinInject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

private val LOCK_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val COST_INPUT_REGEX = Regex("""^\d{0,9}(\.\d{0,6})?$""")
private val LOCK_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val LOCK_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm")

/**
 * 提供商高级设置里的请求调度：频率限制、自定义时间段限额、自动锁定、余额刷新和 cURL 覆写。
 *
 * 所有改动都通过 [ProviderSetting.copyProvider] 写回 [ProviderGuard]，随提供商一起保存和分享。
 */
@Composable
fun ProviderGuardSettings(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit,
) {
    val guard = provider.guard
    val gate = koinInject<ProviderGate>()
    val showQuotaRefresh = provider is ProviderSetting.ApiKey || provider is ProviderSetting.Account

    fun update(newGuard: ProviderGuard) {
        onEdit(provider.copyProvider(guard = newGuard))
    }

    var tick by remember(provider.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(provider.id) {
        while (true) {
            delay(5_000)
            tick = System.currentTimeMillis()
        }
    }
    var status by remember(provider.id) { mutableStateOf<ProviderGateStatus?>(null) }
    val statusTick = tick / 10_000
    LaunchedEffect(provider.id, guard.rpmLimit, guard.tpmLimit, guard.windows, guard.locks, statusTick) {
        status = runCatching { gate.status(provider) }.getOrNull()
    }
    val lockState = remember(guard.locks, tick / 5_000) {
        gate.lockState(provider, Instant.fromEpochMilliseconds(tick))
    }

    var editingWindow by remember { mutableStateOf<ProviderQuotaWindow?>(null) }
    var showWindowSheet by remember { mutableStateOf(false) }
    var editingLock by remember { mutableStateOf<ProviderLockSchedule?>(null) }
    var showLockSheet by remember { mutableStateOf(false) }

    ProviderGateStatusCard(
        status = status,
        lockedUntil = lockState.lockedUntil,
        nextLockAt = lockState.nextStart,
    )

    ProviderRateLimitCard(
        guard = guard,
        status = status,
        onGuardChange = ::update,
        onAddWindow = {
            editingWindow = null
            showWindowSheet = true
        },
        onEditWindow = { window ->
            editingWindow = window
            showWindowSheet = true
        },
    )

    ProviderLockCard(
        guard = guard,
        onGuardChange = ::update,
        onAddLock = {
            editingLock = null
            showLockSheet = true
        },
        onEditLock = { lock ->
            editingLock = lock
            showLockSheet = true
        },
    )

    if (showQuotaRefresh) {
        ProviderQuotaRefreshCard(
            guard = guard,
            onGuardChange = ::update,
        )
    }

    if (provider is ProviderSetting.ApiKey) {
        ProviderRequestOverrideCard(
            guard = guard,
            providerId = provider.id.toString(),
            onGuardChange = ::update,
        )
    }

    if (showWindowSheet) {
        QuotaWindowSheet(
            initial = editingWindow,
            onDismiss = { showWindowSheet = false },
            onConfirm = { window ->
                val exists = guard.windows.any { it.id == window.id }
                update(
                    guard.copy(
                        windows = if (exists) {
                            guard.windows.map { if (it.id == window.id) window else it }
                        } else {
                            guard.windows + window
                        }
                    )
                )
                showWindowSheet = false
            },
        )
    }

    if (showLockSheet) {
        LockScheduleSheet(
            initial = editingLock,
            onDismiss = { showLockSheet = false },
            onConfirm = { lock ->
                val exists = guard.locks.any { it.id == lock.id }
                update(
                    guard.copy(
                        locks = if (exists) {
                            guard.locks.map { if (it.id == lock.id) lock else it }
                        } else {
                            guard.locks + lock
                        }
                    )
                )
                showLockSheet = false
            },
        )
    }
}

@Composable
private fun ProviderGateStatusCard(
    status: ProviderGateStatus?,
    lockedUntil: Instant?,
    nextLockAt: Instant?,
) {
    val context = LocalContext.current
    val binaryUnits = LocalSettings.current.displaySetting.binaryUnits
    CardGroup {
        item(
            headlineContent = { Text(stringResource(R.string.setting_provider_guard_status)) },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val minuteText = if (status != null) {
                        stringResource(
                            R.string.setting_provider_guard_status_minute,
                            status.rpmUsed,
                            status.tpmUsed.formatNumber(binaryUnits),
                        )
                    } else {
                        "…"
                    }
                    Text(
                        text = minuteText,
                        style = MaterialTheme.typography.bodySmall,
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
                        style = MaterialTheme.typography.bodySmall,
                        color = if (lockedUntil != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun ProviderRateLimitCard(
    guard: ProviderGuard,
    status: ProviderGateStatus?,
    onGuardChange: (ProviderGuard) -> Unit,
    onAddWindow: () -> Unit,
    onEditWindow: (ProviderQuotaWindow) -> Unit,
) {
    CardGroup {
        formItem(
            headlineContent = { Text(stringResource(R.string.setting_provider_guard_rate_section)) },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RateField(
                    label = stringResource(R.string.setting_provider_guard_rpm),
                    value = guard.rpmLimit,
                    onValueChange = { onGuardChange(guard.copy(rpmLimit = it)) },
                )
                Text(
                    text = stringResource(R.string.setting_provider_guard_rpm_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(6.dp))
                RateField(
                    label = stringResource(R.string.setting_provider_guard_tpm),
                    value = guard.tpmLimit,
                    onValueChange = { onGuardChange(guard.copy(tpmLimit = it)) },
                )
                Text(
                    text = stringResource(R.string.setting_provider_guard_tpm_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        formItem(
            headlineContent = { Text(stringResource(R.string.setting_provider_guard_windows)) },
            supportingContent = {
                Text(
                    text = stringResource(R.string.setting_provider_guard_windows_desc),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (guard.windows.isEmpty()) {
                    Text(
                        text = stringResource(R.string.setting_provider_guard_window_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                guard.windows.forEach { window ->
                    WindowRow(
                        window = window,
                        usage = status?.windows?.firstOrNull { it.window.id == window.id },
                        onClick = { onEditWindow(window) },
                        onDelete = {
                            onGuardChange(guard.copy(windows = guard.windows.filterNot { it.id == window.id }))
                        },
                    )
                }
                TextButton(onClick = onAddWindow) {
                    Icon(HugeIcons.Add01, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.setting_provider_guard_window_add))
                }
            }
        }
    }
}

@Composable
private fun RateField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        NumberField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.width(110.dp),
        )
    }
}

@Composable
private fun WindowRow(
    window: ProviderQuotaWindow,
    usage: ProviderWindowUsage?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val binaryUnits = LocalSettings.current.displaySetting.binaryUnits
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(
                    R.string.setting_provider_guard_window_summary,
                    window.label(context),
                    metric,
                    limitText,
                ),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (window.isEnabled && usage != null) {
                val full = if (isCost) {
                    usage.costUsed >= window.costLimit
                } else {
                    usage.used >= window.limit
                }
                val usedText = if (isCost) {
                    stringResource(
                        R.string.setting_provider_guard_used,
                        usage.costUsed.formatCost(),
                        window.costLimit.formatCost(),
                    )
                } else {
                    stringResource(
                        R.string.setting_provider_guard_used,
                        usage.used.formatNumber(binaryUnits),
                        window.limit.formatNumber(binaryUnits),
                    )
                }
                val resetText = if (usage.resetAt != null) {
                    " · " + stringResource(
                        R.string.setting_provider_guard_reset_at,
                        formatGuardTime(context, usage.resetAt),
                    )
                } else {
                    ""
                }
                Text(
                    text = usedText + resetText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (full) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (!window.isEnabled) {
                Text(
                    text = stringResource(R.string.setting_provider_guard_window_disabled),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = HugeIcons.Delete01,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ProviderLockCard(
    guard: ProviderGuard,
    onGuardChange: (ProviderGuard) -> Unit,
    onAddLock: () -> Unit,
    onEditLock: (ProviderLockSchedule) -> Unit,
) {
    CardGroup {
        item(
            headlineContent = { Text(stringResource(R.string.setting_provider_guard_locks)) },
            supportingContent = {
                Text(
                    text = stringResource(R.string.setting_provider_guard_locks_desc),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
        )
        if (guard.locks.isEmpty()) {
            item(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.setting_provider_guard_lock_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
        guard.locks.forEach { lock ->
            item(
                onClick = { onEditLock(lock) },
                headlineContent = { Text(lockSummary(lock)) },
                supportingContent = { Text(lockSupportingText(lock), style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Switch(
                            checked = lock.enabled,
                            onCheckedChange = { enabled ->
                                onGuardChange(
                                    guard.copy(
                                        locks = guard.locks.map {
                                            if (it.id == lock.id) it.copy(enabled = enabled) else it
                                        }
                                    )
                                )
                            },
                        )
                        IconButton(
                            onClick = {
                                onGuardChange(guard.copy(locks = guard.locks.filterNot { it.id == lock.id }))
                            }
                        ) {
                            Icon(
                                imageVector = HugeIcons.Delete01,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        }
        item(
            onClick = onAddLock,
            headlineContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(HugeIcons.Add01, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.setting_provider_guard_lock_add))
                }
            },
        )
    }
}

@Composable
private fun lockSummary(lock: ProviderLockSchedule): String {
    val context = LocalContext.current
    val start = lock.parsedStart() ?: return "—"
    val duration = formatGuardDuration(context, lock.durationMinutes)
    return when (lock.repeat) {
        ProviderLockRepeat.DAILY -> stringResource(
            R.string.setting_provider_guard_lock_summary_daily,
            start.format(LOCK_TIME_FORMAT),
            duration,
        )

        ProviderLockRepeat.WEEKLY -> stringResource(
            R.string.setting_provider_guard_lock_summary_weekly,
            weekdayLabel(lock.weekdayOf(start)),
            start.format(LOCK_TIME_FORMAT),
            duration,
        )

        ProviderLockRepeat.ONCE -> stringResource(
            R.string.setting_provider_guard_lock_summary_once,
            start.format(LOCK_DATE_TIME_FORMAT),
            duration,
        )
    }
}

@Composable
private fun lockSupportingText(lock: ProviderLockSchedule): String {
    val start = lock.parsedStart() ?: return ""
    val firstDate = if (lock.repeat == ProviderLockRepeat.WEEKLY) {
        val weekday = lock.weekdayOf(start)
        (0L..6L).map { start.toLocalDate().plusDays(it) }.firstOrNull { it.dayOfWeek == weekday }
            ?: start.toLocalDate()
    } else {
        start.toLocalDate()
    }
    val end = LocalDateTime.of(firstDate, start.toLocalTime())
        .plusMinutes(lock.durationMinutes.coerceAtLeast(1).toLong())
    val endText = stringResource(
        R.string.setting_provider_guard_lock_end,
        end.format(LOCK_DATE_TIME_FORMAT),
    )
    val until = lock.parsedRepeatUntil()
    return if (lock.repeat != ProviderLockRepeat.ONCE && until != null) {
        endText + " · " + stringResource(
            R.string.setting_provider_guard_lock_until,
            until.format(DateTimeFormatter.ofPattern("MM-dd")),
        )
    } else {
        endText
    }
}

@Composable
private fun ProviderQuotaRefreshCard(
    guard: ProviderGuard,
    onGuardChange: (ProviderGuard) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val options = listOf(0, 30, 60, 120, 300, 900, 1800, 3600)
    CardGroup {
        item(
            headlineContent = { Text(stringResource(R.string.setting_provider_guard_refresh)) },
            supportingContent = {
                Text(
                    text = stringResource(R.string.setting_provider_guard_refresh_desc) +
                        " · " + intervalLabel(context, guard.quotaRefreshSeconds),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            onClick = { showDialog = true },
        )
    }
    if (showDialog) {
        AnimatedAlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.setting_provider_guard_refresh)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    options.forEach { seconds ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onGuardChange(guard.copy(quotaRefreshSeconds = seconds))
                                    showDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = guard.quotaRefreshSeconds == seconds,
                                onClick = {
                                    onGuardChange(guard.copy(quotaRefreshSeconds = seconds))
                                    showDialog = false
                                },
                            )
                            Text(intervalLabel(context, seconds))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun intervalLabel(context: Context, seconds: Int): String =
    when {
        seconds <= 0 -> context.getString(R.string.setting_provider_guard_refresh_off)
        seconds < 60 -> context.getString(R.string.setting_provider_guard_seconds, seconds)
        else -> formatGuardDuration(context, seconds / 60)
    }

@Composable
private fun ProviderRequestOverrideCard(
    guard: ProviderGuard,
    providerId: String,
    onGuardChange: (ProviderGuard) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val requestOverride = guard.requestOverride
    var curlText by remember(providerId) { mutableStateOf(requestOverride.curl) }
    LaunchedEffect(requestOverride.curl) {
        if (curlText != requestOverride.curl) curlText = requestOverride.curl
    }
    val error = if (requestOverride.enabled) requestOverride.validate() else null
    val updateOverride: (RequestOverride) -> Unit = { newOverride ->
        onGuardChange(guard.copy(requestOverride = newOverride))
    }
    CardGroup {
        switchItem(
            checked = requestOverride.enabled,
            onCheckedChange = { enabled -> updateOverride(requestOverride.copy(enabled = enabled)) },
            headlineContent = { Text(stringResource(R.string.setting_provider_guard_override)) },
            supportingContent = {
                Text(
                    text = stringResource(R.string.setting_provider_guard_override_desc),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
        )
        formItem(visible = requestOverride.enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = curlText,
                    onValueChange = { text ->
                        curlText = text
                        updateOverride(requestOverride.copy(curl = text))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
                    label = { Text("cURL") },
                    placeholder = {
                        Text("curl 'https://api.example.com/v1/chat/completions' \\\n  -H 'Authorization: Bearer {{apiKey}}' \\\n  -d '{\"model\":\"{{model}}\",\"messages\":{{messages}}}'")
                    },
                    minLines = 6,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetbrainsMono),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                when {
                    error != null -> Text(
                        text = stringResource(R.string.setting_provider_guard_override_invalid, error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    requestOverride.curl.isNotBlank() -> Text(
                        text = stringResource(R.string.setting_provider_guard_override_valid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val text = clipboard.getClipEntry()
                                    ?.clipData
                                    ?.getItemAt(0)
                                    ?.coerceToText(context)
                                    ?.toString()
                                if (!text.isNullOrBlank()) {
                                    curlText = text
                                    updateOverride(requestOverride.copy(enabled = true, curl = text))
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.setting_provider_guard_override_import))
                    }
                    TextButton(
                        onClick = {
                            curlText = ""
                            updateOverride(requestOverride.copy(curl = ""))
                        }
                    ) {
                        Text(stringResource(R.string.setting_provider_guard_override_clear))
                    }
                }
                Text(
                    text = stringResource(
                        R.string.setting_provider_guard_override_hint,
                        requestOverride.variableHint(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NumberField(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        if ((text.toIntOrNull() ?: 0) != value) text = value.toString()
    }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter { it.isDigit() }.take(9)
            text = digits
            onValueChange(digits.toIntOrNull() ?: 0)
        },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetbrainsMono),
    )
}

@Composable
private fun QuotaWindowSheet(
    initial: ProviderQuotaWindow?,
    onDismiss: () -> Unit,
    onConfirm: (ProviderQuotaWindow) -> Unit,
) {
    val base = initial ?: ProviderQuotaWindow()
    var amountText by remember { mutableStateOf(base.amount.coerceAtLeast(1).toString()) }
    var unit by remember { mutableStateOf(base.unit) }
    var metric by remember { mutableStateOf(base.metric) }
    var limitText by remember { mutableStateOf(base.limit.takeIf { it > 0 }?.toString().orEmpty()) }
    var costLimitText by remember {
        mutableStateOf(base.costLimit.takeIf { it > 0 }?.let { it.formatCost() }.orEmpty())
    }
    val amount = amountText.toIntOrNull() ?: 0
    val limit = limitText.toIntOrNull() ?: 0
    val costLimit = costLimitText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val isCost = metric == ProviderQuotaMetric.COST
    val context = LocalContext.current
    val binaryUnits = LocalSettings.current.displaySetting.binaryUnits

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(
                    if (initial == null) {
                        R.string.setting_provider_guard_window_add
                    } else {
                        R.string.setting_provider_guard_window_edit
                    }
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text(stringResource(R.string.setting_provider_guard_window_amount)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ProviderQuotaWindowUnit.entries.forEachIndexed { index, item ->
                    SegmentedButton(
                        colors = glassSegmentedButtonColors(),
                        selected = unit == item,
                        onClick = { unit = item },
                        shape = SegmentedButtonDefaults.itemShape(
                            index,
                            ProviderQuotaWindowUnit.entries.size,
                        ),
                    ) {
                        Text(
                            stringResource(
                                when (item) {
                                    ProviderQuotaWindowUnit.MINUTE -> R.string.setting_provider_guard_unit_minute
                                    ProviderQuotaWindowUnit.HOUR -> R.string.setting_provider_guard_unit_hour
                                    ProviderQuotaWindowUnit.DAY -> R.string.setting_provider_guard_unit_day
                                    ProviderQuotaWindowUnit.WEEK -> R.string.setting_provider_guard_unit_week
                                }
                            )
                        )
                    }
                }
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ProviderQuotaMetric.entries.forEachIndexed { index, item ->
                    SegmentedButton(
                        colors = glassSegmentedButtonColors(),
                        selected = metric == item,
                        onClick = { metric = item },
                        shape = SegmentedButtonDefaults.itemShape(
                            index,
                            ProviderQuotaMetric.entries.size,
                        ),
                    ) {
                        Text(
                            stringResource(
                                when (item) {
                                    ProviderQuotaMetric.REQUESTS -> R.string.setting_provider_guard_metric_requests
                                    ProviderQuotaMetric.TOKENS -> R.string.setting_provider_guard_metric_tokens
                                    ProviderQuotaMetric.COST -> R.string.setting_provider_guard_metric_cost
                                }
                            )
                        )
                    }
                }
            }
            if (isCost) {
                OutlinedTextField(
                    value = costLimitText,
                    onValueChange = { input ->
                        val candidate = input.replace(',', '.')
                        if (candidate.length <= 12 && COST_INPUT_REGEX.matches(candidate)) {
                            costLimitText = candidate
                        }
                    },
                    label = { Text(stringResource(R.string.setting_provider_guard_window_cost_limit)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text(
                    text = stringResource(R.string.setting_provider_guard_window_cost_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it.filter { ch -> ch.isDigit() }.take(12) },
                    label = { Text(stringResource(R.string.setting_provider_guard_window_limit)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Text(
                text = stringResource(R.string.setting_provider_guard_window_disabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (amount > 0 && (if (isCost) costLimit > 0 else limit > 0)) {
                Text(
                    text = stringResource(
                        R.string.setting_provider_guard_window_summary,
                        ProviderQuotaWindow(amount = amount, unit = unit).label(context),
                        stringResource(
                            when (metric) {
                                ProviderQuotaMetric.REQUESTS -> R.string.setting_provider_guard_metric_requests
                                ProviderQuotaMetric.TOKENS -> R.string.setting_provider_guard_metric_tokens
                                ProviderQuotaMetric.COST -> R.string.setting_provider_guard_window_cost_limit
                            }
                        ),
                        if (isCost) costLimit.formatCost() else limit.formatNumber(binaryUnits),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        onConfirm(
                            (initial ?: ProviderQuotaWindow()).copy(
                                amount = amount,
                                unit = unit,
                                metric = metric,
                                limit = limit,
                                costLimit = costLimit,
                            )
                        )
                    },
                    enabled = amount > 0 && (if (isCost) costLimit > 0 else limit > 0),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}

@Composable
private fun LockScheduleSheet(
    initial: ProviderLockSchedule?,
    onDismiss: () -> Unit,
    onConfirm: (ProviderLockSchedule) -> Unit,
) {
    val context = LocalContext.current
    val base = initial ?: ProviderLockSchedule(
        startDate = LocalDate.now().toString(),
        startTime = "23:00",
        durationMinutes = 8 * 60,
    )
    var startDate by remember {
        mutableStateOf(runCatching { LocalDate.parse(base.startDate) }.getOrDefault(LocalDate.now()))
    }
    var startTime by remember {
        mutableStateOf(runCatching { LocalTime.parse(base.startTime) }.getOrDefault(LocalTime.of(23, 0)))
    }
    var durationInHours by remember {
        mutableStateOf(base.durationMinutes >= 60 && base.durationMinutes % 60 == 0)
    }
    var durationText by remember {
        mutableStateOf(
            if (base.durationMinutes >= 60 && base.durationMinutes % 60 == 0) {
                (base.durationMinutes / 60).toString()
            } else {
                base.durationMinutes.coerceAtLeast(1).toString()
            }
        )
    }
    var repeat by remember { mutableStateOf(base.repeat) }
    var repeatUntil by remember { mutableStateOf(base.parsedRepeatUntil()) }
    var weekday by remember {
        mutableStateOf(
            base.repeatWeekday
                .takeIf { it in 1..7 }
                ?.let { DayOfWeek.of(it) }
                ?: startDate.dayOfWeek
        )
    }
    var weekdayPicked by remember {
        mutableStateOf(base.repeat == ProviderLockRepeat.WEEKLY && base.repeatWeekday in 1..7)
    }
    var showWeekdayPicker by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(base.enabled) }
    val durationValue = durationText.toIntOrNull() ?: 0
    val totalMinutes = (if (durationInHours) durationValue * 60 else durationValue).coerceAtLeast(0)
    // 每周重复时第一次触发不一定落在开始日期当天
    val firstStartDate = if (repeat == ProviderLockRepeat.WEEKLY) {
        (0L..6L).map { startDate.plusDays(it) }.firstOrNull { it.dayOfWeek == weekday } ?: startDate
    } else {
        startDate
    }

    if (showWeekdayPicker) {
        WeekdayPickerDialog(
            title = stringResource(R.string.setting_provider_guard_lock_weekday),
            selected = weekday,
            onDismiss = { showWeekdayPicker = false },
            onPick = { day ->
                weekday = day
                weekdayPicked = true
                showWeekdayPicker = false
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    if (initial == null) {
                        R.string.setting_provider_guard_lock_add
                    } else {
                        R.string.setting_provider_guard_lock_edit
                    }
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            PickerRow(
                label = stringResource(R.string.setting_provider_guard_lock_start_date),
                value = startDate.format(LOCK_DATE_FORMAT),
                onClick = {
                    pickDate(context, startDate) { picked ->
                        startDate = picked
                        if (!weekdayPicked) weekday = picked.dayOfWeek
                    }
                },
            )
            PickerRow(
                label = stringResource(R.string.setting_provider_guard_lock_start_time),
                value = startTime.format(LOCK_TIME_FORMAT),
                onClick = {
                    pickTime(context, startTime) { startTime = it }
                },
            )
            Text(
                text = stringResource(R.string.setting_provider_guard_lock_duration),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it.filter { ch -> ch.isDigit() }.take(6) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                SingleChoiceSegmentedButtonRow {
                    listOf(false, true).forEachIndexed { index, hours ->
                        SegmentedButton(
                            colors = glassSegmentedButtonColors(),
                            selected = durationInHours == hours,
                            onClick = {
                                val total = if (durationInHours) durationValue * 60 else durationValue
                                durationInHours = hours
                                durationText = if (hours) {
                                    (total / 60).coerceAtLeast(1).toString()
                                } else {
                                    total.coerceAtLeast(1).toString()
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                        ) {
                            Text(
                                stringResource(
                                    if (hours) {
                                        R.string.setting_provider_guard_unit_hour
                                    } else {
                                        R.string.setting_provider_guard_unit_minute
                                    }
                                )
                            )
                        }
                    }
                }
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ProviderLockRepeat.entries.forEachIndexed { index, item ->
                    SegmentedButton(
                        colors = glassSegmentedButtonColors(),
                        selected = repeat == item,
                        onClick = {
                            repeat = item
                            if (item == ProviderLockRepeat.ONCE) repeatUntil = null
                            if (item == ProviderLockRepeat.WEEKLY && base.repeat != ProviderLockRepeat.WEEKLY) {
                                weekday = startDate.dayOfWeek
                                weekdayPicked = false
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index,
                            ProviderLockRepeat.entries.size,
                        ),
                    ) {
                        Text(
                            stringResource(
                                when (item) {
                                    ProviderLockRepeat.ONCE -> R.string.setting_provider_guard_lock_once
                                    ProviderLockRepeat.DAILY -> R.string.setting_provider_guard_lock_daily
                                    ProviderLockRepeat.WEEKLY -> R.string.setting_provider_guard_lock_weekly
                                }
                            )
                        )
                    }
                }
            }
            if (repeat == ProviderLockRepeat.WEEKLY) {
                PickerRow(
                    label = stringResource(R.string.setting_provider_guard_lock_weekday),
                    value = weekdayLabel(weekday),
                    onClick = { showWeekdayPicker = true },
                )
            }
            if (repeat != ProviderLockRepeat.ONCE) {
                PickerRow(
                    label = stringResource(R.string.setting_provider_guard_lock_repeat_until),
                    value = repeatUntil?.format(LOCK_DATE_FORMAT)
                        ?: stringResource(R.string.setting_provider_guard_lock_forever),
                    onClick = {
                        val fallback = if (repeat == ProviderLockRepeat.WEEKLY) {
                            firstStartDate.plusWeeks(1)
                        } else {
                            firstStartDate.plusDays(1)
                        }
                        pickDate(context, repeatUntil ?: fallback) { repeatUntil = it }
                    },
                )
                if (repeatUntil != null) {
                    TextButton(onClick = { repeatUntil = null }) {
                        Text(stringResource(R.string.setting_provider_guard_lock_forever))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.setting_provider_guard_enabled),
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            if (totalMinutes > 0) {
                val start = LocalDateTime.of(firstStartDate, startTime)
                val end = start.plusMinutes(totalMinutes.toLong())
                Text(
                    text = stringResource(
                        R.string.setting_provider_guard_lock_preview,
                        start.format(LOCK_DATE_TIME_FORMAT),
                        end.format(LOCK_DATE_TIME_FORMAT),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = stringResource(R.string.setting_provider_guard_lock_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        onConfirm(
                            (initial ?: ProviderLockSchedule()).copy(
                                enabled = enabled,
                                startDate = startDate.format(LOCK_DATE_FORMAT),
                                startTime = startTime.format(LOCK_TIME_FORMAT),
                                durationMinutes = totalMinutes,
                                repeat = repeat,
                                repeatWeekday = weekday.value,
                                repeatUntil = if (repeat != ProviderLockRepeat.ONCE) {
                                    repeatUntil
                                        ?.takeIf { !it.isBefore(firstStartDate) }
                                        ?.format(LOCK_DATE_FORMAT)
                                        .orEmpty()
                                } else {
                                    ""
                                },
                            )
                        )
                    },
                    enabled = totalMinutes > 0,
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun pickDate(context: Context, initial: LocalDate, onPick: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, day -> onPick(LocalDate.of(year, month + 1, day)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}

private fun pickTime(context: Context, initial: LocalTime, onPick: (LocalTime) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPick(LocalTime.of(hour, minute)) },
        initial.hour,
        initial.minute,
        true,
    ).show()
}
