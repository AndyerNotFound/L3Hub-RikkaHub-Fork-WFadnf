package me.rerere.rikkahub.ui.pages.setting

import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import me.rerere.rikkahub.ui.components.ui.AppSettingsCard
import me.rerere.rikkahub.ui.components.ui.AppSettingsRow
import me.rerere.rikkahub.ui.style.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import kotlin.math.roundToInt
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.AppNavItem
import me.rerere.rikkahub.ui.components.ui.AppSettingsGroup
import me.rerere.rikkahub.ui.components.ui.AppSwitchRow

import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.NumberSlider
import me.rerere.rikkahub.ui.components.ui.PercentNumberSlider
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionNotification
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.floating.FloatingPanelSizing
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPadding
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.utils.toLocalDateTime
import org.koin.androidx.compose.koinViewModel

private val UPDATE_PAUSE_DAY_OPTIONS = listOf(7, 14, 21)
private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1_000L

@Composable
fun SettingPreferencesNotificationPage(embedded: Boolean = false, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    var showUpdatePauseDialog by remember { mutableStateOf(false) }
    var showFloatingIconPicker by remember { mutableStateOf(false) }
    var selectedUpdatePauseDays by remember { mutableStateOf(UPDATE_PAUSE_DAY_OPTIONS.first()) }

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings { it.copy(displaySetting = setting) }
    }

    // 滑块拖动时只改这三份草稿，松手才写设置：每帧落盘要把整份 DisplaySetting 重新编码一遍
    var draftPanelWidth by remember(displaySetting.floatingPanelWidthRatio) {
        mutableFloatStateOf(FloatingPanelSizing.clampWidthRatio(displaySetting.floatingPanelWidthRatio))
    }
    var draftPanelHeight by remember(displaySetting.floatingPanelHeightRatio) {
        mutableFloatStateOf(FloatingPanelSizing.clampHeightRatio(displaySetting.floatingPanelHeightRatio))
    }
    var draftPreviewHeight by remember(displaySetting.floatingPreviewMaxHeight) {
        mutableFloatStateOf(
            FloatingPanelSizing.clampPreviewHeight(displaySetting.floatingPreviewMaxHeight).toFloat()
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val updateChecksEnabled =
        BuildConfig.UPDATE_CHECK_ENABLED &&
            displaySetting.updateCheckDisabledUntilEpochMillis <= System.currentTimeMillis()

    val permissionState = rememberPermissionState(
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setOf(
            PermissionNotification
        ) else emptySet(),
    )
    PermissionManager(permissionState = permissionState)

    val context = LocalContext.current
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    // 从系统设置页授权回来后刷新
    LifecycleResumeEffect(Unit) {
        canDrawOverlays = Settings.canDrawOverlays(context)
        onPauseOrDispose {}
    }

    fun requestOverlayPermission() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
        )
    }

    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    var ignoringBatteryOptimizations by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true)
    }
    LifecycleResumeEffect(Unit) {
        ignoringBatteryOptimizations = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        onPauseOrDispose {}
    }

    fun requestIgnoreBatteryOptimizations() {
        // 有的系统没有这个弹窗，退回电池优化列表页
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            "package:${context.packageName}".toUri(),
        )
        runCatching { context.startActivity(intent) }
            .onFailure { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }

    

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 横向内距由 AppSettingsCard 自带（12dp 横向外距），这里只留全宽模式的刘海安全区
                .adaptiveHorizontalPadding(0.dp)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Play 版由商店负责更新，不提供更新提醒
            if (BuildConfig.UPDATE_CHECK_ENABLED && !BuildConfig.IS_PLAY_STORE) {
                AppSettingsCard {
                    AppNavItem(
                        onClick = {
                            selectedUpdatePauseDays = UPDATE_PAUSE_DAY_OPTIONS.first()
                            showUpdatePauseDialog = true
                        },
                        text = stringResource(R.string.setting_display_page_show_updates_title),
                        sub = if (updateChecksEnabled) {
                            stringResource(R.string.setting_update_reminder_enabled)
                        } else {
                            stringResource(
                                R.string.setting_update_reminder_paused_until,
                                Instant.ofEpochMilli(displaySetting.updateCheckDisabledUntilEpochMillis)
                                    .toLocalDateTime(),
                            )
                        },
                    )
                }
            
            }

            AppSettingsGroup(title = stringResource(R.string.setting_page_preferences_notification_background_generation)) {
                AppSwitchRow(
                    checked = displaySetting.enableNotificationOnMessageGeneration,
                    onCheckedChange = {
                        if (it && !permissionState.allPermissionsGranted) {
                            permissionState.requestPermissions()
                        }
                        updateDisplaySetting(displaySetting.copy(enableNotificationOnMessageGeneration = it))
                    },
                    headline = stringResource(R.string.setting_display_page_notification_message_generated),
                    supporting = stringResource(R.string.setting_display_page_notification_message_generated_desc),
                )
                if (displaySetting.enableNotificationOnMessageGeneration) {
                    AppSwitchRow(
                        checked = displaySetting.enableLiveUpdateNotification,
                        onCheckedChange = {
                            updateDisplaySetting(displaySetting.copy(enableLiveUpdateNotification = it))
                        },
                        headline = stringResource(R.string.setting_display_page_live_update_notification),
                        supporting = stringResource(R.string.setting_display_page_live_update_notification_desc),
                    )
                }
                AppSwitchRow(
                    checked = displaySetting.enableFloatingChatWindow,
                    onCheckedChange = {
                        if (it && !canDrawOverlays) requestOverlayPermission()
                        updateDisplaySetting(displaySetting.copy(enableFloatingChatWindow = it))
                    },
                    headline = stringResource(R.string.setting_page_preferences_notification_floating_window),
                    supporting = stringResource(R.string.setting_page_preferences_notification_floating_window_desc),
                )
                if (displaySetting.enableFloatingChatWindow) {
                    AppSettingsRow {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable(onClick = { showFloatingIconPicker = true }),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                FloatingBubbleIconThumb(
                                    icon = displaySetting.floatingBubbleIcon,
                                    size = 20.dp,
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.floating_bubble_icon_title))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = stringResource(R.string.floating_bubble_icon_desc), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(HugeIcons.ArrowRight01, contentDescription = null)
                        }
                    }
                }
                if (displaySetting.enableFloatingChatWindow) {
                    AppSettingsRow {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = stringResource(R.string.floating_panel_width))
                                }
                                ValuePill(FloatingPanelSizing.percentLabel(draftPanelWidth))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            PercentNumberSlider(
                                value = draftPanelWidth,
                                onValueChange = { draftPanelWidth = FloatingPanelSizing.clampWidthRatio(it) },
                                onValueChangeFinished = {
                                    updateDisplaySetting(displaySetting.copy(floatingPanelWidthRatio = draftPanelWidth))
                                },
                                valueRange = FloatingPanelSizing.MIN_WIDTH_RATIO..FloatingPanelSizing.MAX_WIDTH_RATIO,
                            )
                        }
                    }
                }
                if (displaySetting.enableFloatingChatWindow) {
                    AppSettingsRow {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = stringResource(R.string.floating_panel_height))
                                }
                                ValuePill(FloatingPanelSizing.percentLabel(draftPanelHeight))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            PercentNumberSlider(
                                value = draftPanelHeight,
                                onValueChange = { draftPanelHeight = FloatingPanelSizing.clampHeightRatio(it) },
                                onValueChangeFinished = {
                                    updateDisplaySetting(displaySetting.copy(floatingPanelHeightRatio = draftPanelHeight))
                                },
                                valueRange = FloatingPanelSizing.MIN_HEIGHT_RATIO..FloatingPanelSizing.MAX_HEIGHT_RATIO,
                            )
                        }
                    }
                }
                if (displaySetting.enableFloatingChatWindow) {
                    AppSettingsRow {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = stringResource(R.string.floating_preview_height))
                                }
                                ValuePill(
                                    stringResource(R.string.floating_size_dp, draftPreviewHeight.roundToInt())
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            NumberSlider(
                                value = draftPreviewHeight.roundToInt(),
                                onValueChange = { draftPreviewHeight = it.toFloat() },
                                onValueChangeFinished = {
                                    updateDisplaySetting(
                                        displaySetting.copy(
                                            floatingPreviewMaxHeight =
                                            FloatingPanelSizing.clampPreviewHeight(draftPreviewHeight.roundToInt()),
                                        )
                                    )
                                },
                                valueRange = FloatingPanelSizing.MIN_PREVIEW_HEIGHT_DP..
                                FloatingPanelSizing.MAX_PREVIEW_HEIGHT_DP,
                                suffix = "dp",
                                steps = 0,
                            )
                        }
                    }
                }
                if (displaySetting.enableFloatingChatWindow && !canDrawOverlays) {
                    AppNavItem(
                        onClick = ::requestOverlayPermission,
                        text = stringResource(R.string.setting_page_preferences_notification_overlay_permission),
                        sub = stringResource(R.string.setting_page_preferences_notification_overlay_permission_desc),
                    )
                }
                AppSettingsRow {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(onClick = {
                                if (!ignoringBatteryOptimizations) requestIgnoreBatteryOptimizations()
                        }),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.setting_page_preferences_notification_battery_optimization))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = stringResource(
                                    if (ignoringBatteryOptimizations) {
                                        R.string.setting_page_preferences_notification_battery_optimization_granted
                                    } else {
                                        R.string.setting_page_preferences_notification_battery_optimization_desc
                                    }
                                ), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                        }
                        if (!ignoringBatteryOptimizations) {
                            Icon(HugeIcons.ArrowRight01, contentDescription = null)
                        }
                    }
                }
            }

        }
    }
    FloatingBubbleIconPicker(
        visible = showFloatingIconPicker,
        current = displaySetting.floatingBubbleIcon,
        onSelect = { icon ->
            // 旧图片由 FilesManager.saveBubbleIcon 自己在写入成功之后清掉，这里只管存设置
            updateDisplaySetting(displaySetting.copy(floatingBubbleIcon = icon))
        },
        onDismiss = { showFloatingIconPicker = false },
    )
    if (embedded) {
        content()
    } else {
        Scaffold(
            topBar = {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(stringResource(R.string.setting_page_preferences_notification))
                    },
                    navigationIcon = {
                        BackButton()
                    },
                    scrollBehavior = scrollBehavior,
                    colors = CustomColors.topBarColors
                )
            },
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = CustomColors.topBarColors.containerColor
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding),
            ) {
                content()
            }
        }
    }
}

/** 行右侧的取值小标签，胶囊观感与迁移前的取值标签一致。 */
@Composable
private fun ValuePill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
    }
}
