package me.rerere.rikkahub.ui.pages.assistant.detail

import android.Manifest
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import me.rerere.rikkahub.ui.components.ui.animatedItem
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.style.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Calendar03
import me.rerere.hugeicons.stroke.ChartLineData01
import me.rerere.hugeicons.stroke.ClipboardPaste
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Flowchart01
import me.rerere.hugeicons.stroke.JavaScript
import me.rerere.hugeicons.stroke.MessageQuestion
import me.rerere.hugeicons.stroke.SmartPhone01
import me.rerere.hugeicons.stroke.Tap01
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.subagent.subAgentLabel
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.phoneuse.PhoneUseManager
import me.rerere.rikkahub.phoneuse.isFloatingChatWindowOn
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.permission.PermissionInfo
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.hasUsageStatsPermission
import me.rerere.rikkahub.utils.openAccessibilitySettings
import me.rerere.rikkahub.utils.openUsageAccessSettings
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.ui.toast.ToastType
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

@Composable
fun AssistantLocalToolPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_local_tools))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        AssistantLocalToolContent(
            innerPadding = innerPadding,
            assistant = assistant,
            assistants = settings.assistants,
            onUpdate = { vm.update(it) }
        )
    }
}

@Composable
private fun AssistantLocalToolContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    assistants: List<Assistant>,
    onUpdate: (Assistant) -> Unit
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    var showSubAgentPicker by remember { mutableStateOf(false) }
    val subAgentCandidates = assistants.filter { it.isSubAgent && it.id != assistant.id }
    val allowedSubAgentCount = subAgentCandidates.count { it.id in assistant.allowedSubAgentIds }
    val navController = LocalNavController.current
    val settingsStore: SettingsStore = koinInject()
    val phoneUseManager: PhoneUseManager = koinInject()
    val phoneUseServiceOn = phoneUseManager.controller.collectAsStateWithLifecycle().value != null
    val permissionRequiredText =
        stringResource(R.string.assistant_page_local_tools_screen_time_permission_required)
    val floatingWindowRequiredText =
        stringResource(R.string.assistant_page_local_tools_phone_use_floating_window_required)
    val enableServiceText = stringResource(R.string.assistant_page_local_tools_phone_use_enable_service)

    val calendarPermissionState = rememberPermissionState(
        permissions = setOf(
            PermissionInfo(
                permission = Manifest.permission.READ_CALENDAR,
                displayName = { Text(stringResource(R.string.permission_calendar_read)) },
                usage = { Text(stringResource(R.string.permission_calendar_read_desc)) },
                required = true
            ),
            PermissionInfo(
                permission = Manifest.permission.WRITE_CALENDAR,
                displayName = { Text(stringResource(R.string.permission_calendar_write)) },
                usage = { Text(stringResource(R.string.permission_calendar_write_desc)) },
                required = true
            ),
        )
    )
    PermissionManager(permissionState = calendarPermissionState)

    fun toggleLocalTool(option: LocalToolOption, enabled: Boolean) {
        // 没有悬浮窗就看不到 AI 在做什么、也不方便随时停下，先去把它打开，这次不开启工具
        if (enabled && option == LocalToolOption.PhoneUse &&
            !isFloatingChatWindowOn(context, settingsStore.settingsFlow.value)
        ) {
            toaster.show(message = floatingWindowRequiredText, type = ToastType.Warning)
            navController.navigate(Screen.SettingPreferencesNotification)
            return
        }
        // 无障碍服务还没开时带用户去系统设置，开关照常打开，开完服务就能用
        if (enabled && option == LocalToolOption.PhoneUse && !phoneUseServiceOn) {
            toaster.show(message = enableServiceText, type = ToastType.Warning)
            context.openAccessibilitySettings()
        }
        if (enabled && option == LocalToolOption.ScreenTime && !context.hasUsageStatsPermission()) {
            toaster.show(message = permissionRequiredText, type = ToastType.Warning)
            context.openUsageAccessSettings()
        }
        if (enabled && option == LocalToolOption.Calendar && !calendarPermissionState.allPermissionsGranted) {
            calendarPermissionState.requestPermissions()
            return
        }
        val newLocalTools = if (enabled) {
            assistant.localTools + option
        } else {
            assistant.localTools - option
        }
        onUpdate(assistant.copy(localTools = newLocalTools))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(innerPadding)
            .padding(bottom = 16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CardGroup {
            localToolSwitchItem(
                checked = LocalToolOption.JavascriptEngine in assistant.localTools,
                onCheckedChange = { toggleLocalTool(LocalToolOption.JavascriptEngine, it) },
                icon = HugeIcons.JavaScript,
                title = R.string.assistant_page_local_tools_javascript_engine_title,
                description = R.string.assistant_page_local_tools_javascript_engine_desc,
            )
            localToolSwitchItem(
                checked = LocalToolOption.TimeInfo in assistant.localTools,
                onCheckedChange = { toggleLocalTool(LocalToolOption.TimeInfo, it) },
                icon = HugeIcons.Clock01,
                title = R.string.assistant_page_local_tools_time_info_title,
                description = R.string.assistant_page_local_tools_time_info_desc,
            )
            localToolSwitchItem(
                checked = LocalToolOption.Clipboard in assistant.localTools,
                onCheckedChange = { toggleLocalTool(LocalToolOption.Clipboard, it) },
                icon = HugeIcons.ClipboardPaste,
                title = R.string.assistant_page_local_tools_clipboard_title,
                description = R.string.assistant_page_local_tools_clipboard_desc,
            )
            localToolSwitchItem(
                checked = LocalToolOption.Tts in assistant.localTools,
                onCheckedChange = { toggleLocalTool(LocalToolOption.Tts, it) },
                icon = HugeIcons.VolumeHigh,
                title = R.string.assistant_page_local_tools_tts_title,
                description = R.string.assistant_page_local_tools_tts_desc,
            )
            localToolSwitchItem(
                checked = LocalToolOption.AskUser in assistant.localTools,
                onCheckedChange = { toggleLocalTool(LocalToolOption.AskUser, it) },
                icon = HugeIcons.MessageQuestion,
                title = R.string.assistant_page_local_tools_ask_user_title,
                description = R.string.assistant_page_local_tools_ask_user_desc,
            )
            localToolSwitchItem(
                checked = LocalToolOption.ScreenTime in assistant.localTools,
                onCheckedChange = { toggleLocalTool(LocalToolOption.ScreenTime, it) },
                icon = HugeIcons.SmartPhone01,
                title = R.string.assistant_page_local_tools_screen_time_title,
                description = R.string.assistant_page_local_tools_screen_time_desc,
            )
            localToolSwitchItem(
                checked = LocalToolOption.Calendar in assistant.localTools,
                onCheckedChange = { toggleLocalTool(LocalToolOption.Calendar, it) },
                icon = HugeIcons.Calendar03,
                title = R.string.assistant_page_local_tools_calendar_title,
                description = R.string.assistant_page_local_tools_calendar_desc,
            )
            localToolSwitchItem(
                checked = LocalToolOption.ChartDisplay in assistant.localTools,
                onCheckedChange = { toggleLocalTool(LocalToolOption.ChartDisplay, it) },
                icon = HugeIcons.ChartLineData01,
                title = R.string.assistant_page_local_tools_chart_display_title,
                description = R.string.assistant_page_local_tools_chart_display_desc,
            )
            // 只有 github 渠道的 Android 11 及以上才有
            if (PhoneUseManager.isSupported) {
                phoneUseSwitchItem(
                    checked = LocalToolOption.PhoneUse in assistant.localTools,
                    onCheckedChange = { toggleLocalTool(LocalToolOption.PhoneUse, it) },
                    icon = HugeIcons.Tap01,
                    title = R.string.assistant_page_local_tools_phone_use_title,
                ) {
                    Text(stringResource(R.string.assistant_page_local_tools_phone_use_desc))
                    // 开了工具但服务没开时模型调用会直接报错，在这里说明原因
                    if (LocalToolOption.PhoneUse in assistant.localTools && !phoneUseServiceOn) {
                        Text(
                            text = stringResource(R.string.assistant_page_local_tools_phone_use_service_off),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            localToolSwitchItem(
                checked = LocalToolOption.SubAgent in assistant.localTools,
                onCheckedChange = { toggleLocalTool(LocalToolOption.SubAgent, it) },
                icon = HugeIcons.Flowchart01,
                title = R.string.assistant_page_local_tools_sub_agent_title,
                description = R.string.assistant_page_local_tools_sub_agent_desc,
            )
            // 开了派发工具，或者它自己是子代理（子代理会话会自动拿到派发工具）时，
            // 这个范围都可能生效
                animatedItem(
                    visible = { assistant.localTools.contains(LocalToolOption.SubAgent) || assistant.isSubAgent },
                    onClick = if (subAgentCandidates.isNotEmpty() || assistant.allowedSubAgentIds.isNotEmpty()) {
                        { showSubAgentPicker = true }
                    } else {
                        null
                    },
                    leadingContent = {
                        CardGroupIcon(
                            icon = HugeIcons.Flowchart01,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    },
                    headlineContent = { Text(stringResource(R.string.assistant_page_allowed_sub_agents)) },
                    supportingContent = {
                        Text(
                            when {
                                subAgentCandidates.isEmpty() -> stringResource(
                                    R.string.assistant_page_allowed_sub_agents_empty
                                )
                                assistant.allowedSubAgentIds.isEmpty() -> stringResource(
                                    R.string.assistant_page_allowed_sub_agents_summary_all
                                )
                                allowedSubAgentCount == 0 -> stringResource(
                                    R.string.assistant_page_allowed_sub_agents_missing
                                )
                                else -> stringResource(
                                    R.string.assistant_page_allowed_sub_agents_summary_selected,
                                    allowedSubAgentCount,
                                )
                            }
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = HugeIcons.ArrowRight01,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
        }
    }

    SubAgentAllowlistDialog(
        show = showSubAgentPicker,
        assistant = assistant,
        candidates = subAgentCandidates,
        onDismiss = { showSubAgentPicker = false },
        onConfirm = { ids ->
            // 只写回仍然可用的目标：设置被并发改过时也不会留下看不见的失效项
            val validIds = subAgentCandidates.mapTo(mutableSetOf()) { it.id }
            onUpdate(assistant.copy(allowedSubAgentIds = ids.intersect(validIds)))
            showSubAgentPicker = false
        },
    )
}

/** 多选派发范围；一项都不勾表示不限制 */
@Composable
private fun SubAgentAllowlistDialog(
    show: Boolean,
    assistant: Assistant,
    candidates: List<Assistant>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Uuid>) -> Unit,
) {
    if (!show) return
    var selected by remember(show, assistant.allowedSubAgentIds) {
        mutableStateOf(assistant.allowedSubAgentIds)
    }

    RikkaConfirmDialog(
        show = show,
        title = stringResource(R.string.assistant_page_allowed_sub_agents),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = { onConfirm(selected) },
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.assistant_page_allowed_sub_agents_dialog_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            candidates.forEach { candidate ->
                val checked = candidate.id in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = { enabled ->
                                selected = if (enabled) {
                                    selected + candidate.id
                                } else {
                                    selected - candidate.id
                                }
                            },
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Text(
                        text = subAgentLabel(candidate),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/**
 * 本地工具的开关项：图标底色跟着开关状态走（沿用旧版圆形底图标的配色），
 * 开关仍走 switchItem，「开关类型」可配置功能原样保留。
 */
private fun CardGroupScope.localToolSwitchItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes description: Int,
) = switchItem(
    checked = checked,
    onCheckedChange = onCheckedChange,
    leadingContent = {
        CardGroupIcon(
            icon = icon,
            containerColor = if (checked) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (checked) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    },
    headlineContent = { Text(stringResource(title)) },
    supportingContent = { Text(stringResource(description)) },
)

/**
 * 屏幕使用那项的说明要在服务没开时补一行错误，说明位得吃可组合内容，
 * 所以说明整块由调用方给，行本身仍走 switchItem。
 */
private fun CardGroupScope.phoneUseSwitchItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    @StringRes title: Int,
    supporting: @Composable () -> Unit,
) = switchItem(
    checked = checked,
    onCheckedChange = onCheckedChange,
    leadingContent = {
        CardGroupIcon(
            icon = icon,
            containerColor = if (checked) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (checked) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    },
    headlineContent = { Text(stringResource(title)) },
    supportingContent = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            supporting()
        }
    },
)
