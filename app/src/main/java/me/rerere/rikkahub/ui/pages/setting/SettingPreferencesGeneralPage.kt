package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.CornerStyle
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.HapticsStrength
import me.rerere.rikkahub.data.datastore.InputLayoutStyle
import me.rerere.rikkahub.data.datastore.SliderType
import me.rerere.rikkahub.data.datastore.SwitchType
import me.rerere.rikkahub.data.datastore.effectiveBackgroundEffect
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AppSettingsCard
import me.rerere.rikkahub.ui.components.ui.AppSettingsGroup
import me.rerere.rikkahub.ui.components.ui.AppSettingsRow
import me.rerere.rikkahub.ui.components.ui.AppSlider
import me.rerere.rikkahub.ui.components.ui.AppSwitchRow
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.NumberSlider
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.rememberSharedPreferenceBoolean
import me.rerere.rikkahub.ui.style.LargeFlexibleTopAppBar
import me.rerere.rikkahub.ui.style.RadioButton
import me.rerere.rikkahub.ui.style.TextButton
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPadding
import me.rerere.rikkahub.utils.AppLanguage
import me.rerere.rikkahub.utils.getActivity
import me.rerere.rikkahub.utils.hasDeviceCredential
import me.rerere.rikkahub.utils.plus
import me.rerere.ui.components.AnimatedAlertDialog
import me.rerere.ui.toast.ToastType
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingPreferencesGeneralPage(embedded: Boolean = false, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val deviceLockRequired = stringResource(R.string.setting_app_lock_requires_device_lock)
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    var ttsPlaybackSpeed by remember(settings.defaultTTSPlaybackSpeed) {
        mutableFloatStateOf(settings.defaultTTSPlaybackSpeed)
    }

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings { it.copy(displaySetting = setting) }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 横向内距由 AppSettingsCard 自带（12dp），这里只留全宽模式的刘海安全区
                .adaptiveHorizontalPadding(0.dp)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            var showNicknameDialog by remember { mutableStateOf(false) }
            var nicknameDraft by remember(displaySetting.userNickname) {
                mutableStateOf(displaySetting.userNickname)
            }
            AppSettingsCard {
                AppSettingsRow {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(onClick = {
                                nicknameDraft = displaySetting.userNickname
                                showNicknameDialog = true
                        }),
                        verticalAlignment = Alignment.Top,
                    ) {
                        UIAvatar(
                            name = displaySetting.userNickname.ifBlank {
                                stringResource(R.string.user_default_name)
                            },
                            value = displaySetting.userAvatar,
                            avatarSize = 40.dp,
                            onUpdate = { avatar ->
                                updateDisplaySetting(displaySetting.copy(userAvatar = avatar))
                            },
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = displaySetting.userNickname.ifBlank {
                                    stringResource(R.string.user_default_name)
                            })
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = stringResource(R.string.setting_page_profile_desc), color = LocalContentColor.current.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (showNicknameDialog) {
                AnimatedAlertDialog(
                    onDismissRequest = { showNicknameDialog = false },
                    title = { Text(stringResource(R.string.chat_page_edit_nickname)) },
                    text = {
                        OutlinedTextField(
                            value = nicknameDraft,
                            onValueChange = { nicknameDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.chat_page_nickname_placeholder)) },
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                updateDisplaySetting(displaySetting.copy(userNickname = nicknameDraft.trim()))
                                showNicknameDialog = false
                            }
                        ) {
                            Text(stringResource(R.string.chat_page_save))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showNicknameDialog = false }) {
                            Text(stringResource(R.string.chat_page_cancel))
                        }
                    },
                )
            }

            var languageTag by remember { mutableStateOf(AppLanguage.currentTag(context)) }
            var showLanguageDialog by remember { mutableStateOf(false) }
            AppSettingsCard {
                AppSettingsRow {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(onClick = { showLanguageDialog = true }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.setting_page_language))
                        }
                        ValuePill(
                            if (languageTag.isBlank()) {
                                stringResource(R.string.setting_page_language_system)
                            } else {
                                AppLanguage.displayName(languageTag)
                            }
                        )
                    }
                }
            }
            if (showLanguageDialog) {
                LanguagePickerDialog(
                    selected = languageTag,
                    onSelect = { tag ->
                        AppLanguage.setTag(context, tag)
                        languageTag = tag
                        showLanguageDialog = false
                        context.getActivity()?.recreate()
                    },
                    onDismiss = { showLanguageDialog = false },
                )
            }

            var createNewConversationOnStart by rememberSharedPreferenceBoolean(
                "create_new_conversation_on_start",
                true
            )
            AppSettingsCard {
                AppSwitchRow(
                    checked = createNewConversationOnStart,
                    onCheckedChange = { createNewConversationOnStart = it },
                    headline = stringResource(R.string.setting_display_page_create_new_conversation_on_start_title),
                    supporting = stringResource(R.string.setting_display_page_create_new_conversation_on_start_desc),
                )
                AppSwitchRow(
                    checked = displaySetting.sendOnEnter,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(sendOnEnter = it)) },
                    headline = stringResource(R.string.setting_display_page_send_on_enter_title),
                    supporting = stringResource(R.string.setting_display_page_send_on_enter_desc),
                )
                AppSwitchRow(
                    checked = settings.enableMessageQueue,
                    onCheckedChange = { enabled ->
                        vm.updateSettings { it.copy(enableMessageQueue = enabled) }
                    },
                    headline = stringResource(R.string.setting_display_page_message_queue_title),
                    supporting = stringResource(R.string.setting_display_page_message_queue_desc),
                )
            }

            AppSettingsCard {
                AppSwitchRow(
                    checked = displaySetting.showMessageJumper,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showMessageJumper = it)) },
                    headline = stringResource(R.string.setting_display_page_show_message_jumper_title),
                    supporting = stringResource(R.string.setting_display_page_show_message_jumper_desc),
                )
                if (displaySetting.showMessageJumper) {
                    AppSwitchRow(
                        checked = displaySetting.messageJumperOnLeft,
                        onCheckedChange = { updateDisplaySetting(displaySetting.copy(messageJumperOnLeft = it)) },
                        headline = stringResource(R.string.setting_display_page_message_jumper_position_title),
                        supporting = stringResource(R.string.setting_display_page_message_jumper_position_desc),
                    )
                }
                AppSwitchRow(
                    checked = displaySetting.enableAutoScroll,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(enableAutoScroll = it)) },
                    headline = stringResource(R.string.setting_display_page_enable_auto_scroll_title),
                    supporting = stringResource(R.string.setting_display_page_enable_auto_scroll_desc),
                )
            }

            AppSettingsCard {
                AppSettingsRow {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.setting_display_page_background_effect_title))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = stringResource(R.string.setting_display_page_background_effect_desc), color = LocalContentColor.current.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ConnectedChoiceButtons(
                            options = BackgroundEffectType.entries,
                            selected = displaySetting.effectiveBackgroundEffect,
                            onSelect = { type ->
                                updateDisplaySetting(
                                    displaySetting.copy(
                                        backgroundEffectType = type,
                                        enableBlurEffect = type != BackgroundEffectType.OFF,
                                    )
                                )
                            },
                        ) { type, selected ->
                            ConnectedChoiceLabel(
                                text = when (type) {
                                    BackgroundEffectType.OFF -> stringResource(R.string.setting_display_page_background_effect_off)
                                    BackgroundEffectType.BLUR -> stringResource(R.string.setting_display_page_background_effect_blur)
                                    BackgroundEffectType.GLASS -> stringResource(R.string.setting_display_page_background_effect_glass)
                                },
                                selected = selected,
                            )
                        }
                    }
                }
                if (displaySetting.effectiveBackgroundEffect == BackgroundEffectType.BLUR) {
                    AppSettingsRow {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = stringResource(R.string.setting_display_page_blur_opacity_title))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = stringResource(R.string.setting_display_page_blur_opacity_desc), color = LocalContentColor.current.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                                }
                                ValuePill("${(displaySetting.blurOpacity * 100).roundToInt()}%")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            AppSlider(
                                value = displaySetting.blurOpacity,
                                onValueChange = {
                                    updateDisplaySetting(displaySetting.copy(blurOpacity = it))
                                },
                                valueRange = 0f..1f,
                                steps = 19,
                            )
                        }
                    }
                }
                if (displaySetting.effectiveBackgroundEffect == BackgroundEffectType.GLASS) {
                    AppSettingsRow {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = stringResource(R.string.setting_display_page_glass_opacity_title))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = stringResource(R.string.setting_display_page_glass_opacity_desc), color = LocalContentColor.current.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                                }
                                ValuePill("${(displaySetting.glassOpacity * 100).roundToInt()}%")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            AppSlider(
                                value = displaySetting.glassOpacity,
                                onValueChange = {
                                    updateDisplaySetting(displaySetting.copy(glassOpacity = it))
                                },
                                valueRange = 0f..1f,
                                steps = 19,
                            )
                        }
                    }
                }
                AppSwitchRow(
                    checked = displaySetting.fullWidthComponents,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(fullWidthComponents = it)) },
                    headline = stringResource(R.string.setting_display_page_full_width_title),
                    supporting = stringResource(R.string.setting_display_page_full_width_desc),
                )
                AppSettingsRow {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.setting_display_page_input_layout_title))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = stringResource(R.string.setting_display_page_input_layout_desc), color = LocalContentColor.current.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ConnectedChoiceButtons(
                            options = InputLayoutStyle.entries,
                            selected = displaySetting.inputLayoutStyle,
                            onSelect = { layout ->
                                updateDisplaySetting(displaySetting.copy(inputLayoutStyle = layout))
                            },
                        ) { layout, selected ->
                            ConnectedChoiceLabel(
                                text = when (layout) {
                                    InputLayoutStyle.SINGLE -> stringResource(R.string.setting_display_page_input_layout_single)
                                    InputLayoutStyle.DOUBLE -> stringResource(R.string.setting_display_page_input_layout_double)
                                },
                                selected = selected,
                            )
                        }
                    }
                }
                AppSwitchRow(
                    checked = displaySetting.enableMessageGenerationHapticEffect,
                    onCheckedChange = {
                        updateDisplaySetting(displaySetting.copy(enableMessageGenerationHapticEffect = it))
                    },
                    headline = stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_title),
                    supporting = stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_desc),
                )
                AppSettingsRow {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.setting_display_page_haptics_strength_title))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = stringResource(R.string.setting_display_page_haptics_strength_desc), color = LocalContentColor.current.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ConnectedChoiceButtons(
                            options = HapticsStrength.entries,
                            selected = displaySetting.hapticsStrength,
                            onSelect = { strength ->
                                updateDisplaySetting(displaySetting.copy(hapticsStrength = strength))
                            },
                        ) { strength, selected ->
                            ConnectedChoiceLabel(
                                text = when (strength) {
                                    HapticsStrength.SYSTEM -> stringResource(R.string.setting_display_page_haptics_strength_system)
                                    HapticsStrength.LIGHT -> stringResource(R.string.setting_display_page_haptics_strength_light)
                                    HapticsStrength.STRONG -> stringResource(R.string.setting_display_page_haptics_strength_strong)
                                },
                                selected = selected,
                            )
                        }
                    }
                }
                // 轮廓：形状类型 / 边角大小 / 按压轮廓的节流，取自 ImageToolbox 的「布局」分组
                AppSettingsRow {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.setting_display_page_corner_style_title))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = stringResource(R.string.setting_display_page_corner_style_desc), color = LocalContentColor.current.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CornerStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = displaySetting.cornerStyle == style,
                                    onClick = {
                                        updateDisplaySetting(displaySetting.copy(cornerStyle = style))
                                    },
                                    label = { Text(cornerStyleLabel(style)) },
                                )
                            }
                        }
                    }
                }
                AppSettingsRow {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.setting_display_page_corner_scale_title))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = stringResource(R.string.setting_display_page_corner_scale_desc), color = LocalContentColor.current.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                            }
                            ValuePill("${(displaySetting.cornerScale * 100).roundToInt()}%")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        AppSlider(
                            value = displaySetting.cornerScale,
                            onValueChange = { scale ->
                                updateDisplaySetting(displaySetting.copy(cornerScale = scale))
                            },
                            valueRange = 0f..2f,
                        )
                    }
                }
                AppSettingsRow {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.setting_display_page_corner_throttle_title))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = stringResource(R.string.setting_display_page_corner_throttle_desc), color = LocalContentColor.current.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                            }
                            ValuePill("${displaySetting.cornerAnimThrottle} ms")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        NumberSlider(
                            value = displaySetting.cornerAnimThrottle,
                            onValueChange = { throttle ->
                                updateDisplaySetting(displaySetting.copy(cornerAnimThrottle = throttle))
                            },
                            valueRange = 0..500,
                        )
                    }
                }
                AppSettingsRow {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.setting_display_page_switch_type_title))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = stringResource(R.string.setting_display_page_switch_type_desc), color = LocalContentColor.current.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SwitchType.entries.forEach { type ->
                                FilterChip(
                                    selected = displaySetting.switchType == type,
                                    onClick = {
                                        updateDisplaySetting(displaySetting.copy(switchType = type))
                                    },
                                    label = { Text(switchTypeLabel(type)) },
                                )
                            }
                        }
                    }
                }
                AppSettingsRow {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.setting_display_page_slider_type_title))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = stringResource(R.string.setting_display_page_slider_type_desc), color = LocalContentColor.current.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SliderType.entries.forEach { type ->
                                FilterChip(
                                    selected = displaySetting.sliderType == type,
                                    onClick = {
                                        updateDisplaySetting(displaySetting.copy(sliderType = type))
                                    },
                                    label = { Text(sliderTypeLabel(type)) },
                                )
                            }
                        }
                    }
                }
            }

            AppSettingsCard {
                AppSwitchRow(
                    checked = displaySetting.skipCropImage,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(skipCropImage = it)) },
                    headline = stringResource(R.string.setting_display_page_skip_crop_image_title),
                    supporting = stringResource(R.string.setting_display_page_skip_crop_image_desc),
                )
                AppSwitchRow(
                    checked = displaySetting.pasteLongTextAsFile,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(pasteLongTextAsFile = it)) },
                    headline = stringResource(R.string.setting_display_page_paste_long_text_as_file_title),
                    supporting = stringResource(R.string.setting_display_page_paste_long_text_as_file_desc),
                )
                if (displaySetting.pasteLongTextAsFile) {
                    AppSettingsRow {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = stringResource(R.string.setting_display_page_paste_long_text_threshold_title))
                                }
                                ValuePill("${displaySetting.pasteLongTextThreshold}")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            NumberSlider(
                                value = displaySetting.pasteLongTextThreshold,
                                onValueChange = {
                                    updateDisplaySetting(displaySetting.copy(pasteLongTextThreshold = it))
                                },
                                valueRange = 100..10_000,
                            )
                        }
                    }
                }
            }

            AppSettingsCard {
                AppSwitchRow(
                    checked = displaySetting.enableVolumeKeyScroll,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(enableVolumeKeyScroll = it)) },
                    headline = stringResource(R.string.setting_display_page_volume_key_scroll_title),
                    supporting = stringResource(R.string.setting_display_page_volume_key_scroll_desc),
                )
                if (displaySetting.enableVolumeKeyScroll) {
                    AppSettingsRow {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = stringResource(R.string.setting_display_page_volume_key_scroll_ratio))
                                }
                                ValuePill("${(displaySetting.volumeKeyScrollRatio * 100).toInt()}%")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            AppSlider(
                                value = displaySetting.volumeKeyScrollRatio,
                                onValueChange = {
                                    updateDisplaySetting(displaySetting.copy(volumeKeyScrollRatio = it))
                                },
                                valueRange = 0.25f..1.0f,
                                steps = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            AppSettingsCard {
                AppSettingsRow {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.setting_page_agent_title))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.setting_page_agent_desc),
                                    color = LocalContentColor.current.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            ValuePill("${settings.subAgentMaxParallel}")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        NumberSlider(
                            value = settings.subAgentMaxParallel,
                            onValueChange = { value ->
                                vm.updateSettings { it.copy(subAgentMaxParallel = value) }
                            },
                            valueRange = 1..8,
                        )
                    }
                }
            }

            AppSettingsGroup(title = stringResource(R.string.setting_page_tts_settings)) {
                AppSettingsRow {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.setting_tts_page_default_playback_speed))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = stringResource(R.string.setting_tts_page_default_playback_speed_description), color = LocalContentColor.current.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                            }
                            ValuePill("x${"%.1f".format(ttsPlaybackSpeed)}")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        AppSlider(
                            value = ttsPlaybackSpeed,
                            onValueChange = {
                                ttsPlaybackSpeed = (it * 10).roundToInt() / 10f
                            },
                            onValueChangeFinished = {
                                vm.updateSettings {
                                    it.copy(defaultTTSPlaybackSpeed = ttsPlaybackSpeed)
                                }
                            },
                            valueRange = 0.5f..2.0f,
                            steps = 14,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                AppSwitchRow(
                    checked = displaySetting.ttsOnlyReadQuoted,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(ttsOnlyReadQuoted = it)) },
                    headline = stringResource(R.string.setting_display_page_tts_only_read_quoted_title),
                    supporting = stringResource(R.string.setting_display_page_tts_only_read_quoted_desc),
                )
                AppSwitchRow(
                    checked = displaySetting.ttsOnlyReadOutsideBrackets,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(ttsOnlyReadOutsideBrackets = it)) },
                    headline = stringResource(R.string.setting_display_page_tts_read_outside_brackets_title),
                    supporting = stringResource(R.string.setting_display_page_tts_read_outside_brackets_desc),
                )
                AppSwitchRow(
                    checked = displaySetting.autoPlayTTSAfterGeneration,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(autoPlayTTSAfterGeneration = it)) },
                    headline = stringResource(R.string.setting_display_page_auto_play_tts_title),
                    supporting = stringResource(R.string.setting_display_page_auto_play_tts_desc),
                )
                AppSwitchRow(
                    checked = displaySetting.voiceModeListenWhileReplying,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(voiceModeListenWhileReplying = it)) },
                    headline = stringResource(R.string.setting_display_page_voice_listen_while_replying_title),
                    supporting = stringResource(R.string.setting_display_page_voice_listen_while_replying_desc),
                )
            }

            AppSettingsGroup(title = stringResource(R.string.setting_app_lock_title)) {
                AppSwitchRow(
                    checked = settings.appLockEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !context.hasDeviceCredential()) {
                            toaster.show(deviceLockRequired, ToastType.Warning)
                        } else {
                            vm.updateSettings { it.copy(appLockEnabled = enabled) }
                        }
                    },
                    headline = stringResource(R.string.setting_app_lock_switch),
                    supporting = stringResource(R.string.setting_app_lock_desc),
                )
                if (settings.appLockEnabled) {
                    AppSettingsRow {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = stringResource(R.string.setting_app_lock_timeout))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = stringResource(R.string.setting_app_lock_timeout_desc), color = LocalContentColor.current.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            ConnectedChoiceButtons(
                                options = listOf(0, 1, 5, 15),
                                selected = settings.appLockTimeoutMinutes,
                                onSelect = { minutes ->
                                    vm.updateSettings { it.copy(appLockTimeoutMinutes = minutes) }
                                },
                            ) { minutes, selected ->
                                ConnectedChoiceLabel(
                                    text = if (minutes == 0) {
                                        stringResource(R.string.setting_app_lock_immediately)
                                    } else {
                                        stringResource(R.string.setting_app_lock_minutes, minutes)
                                    },
                                    selected = selected,
                                )
                            }
                        }
                    }
                }
            }

        }
    }
    if (embedded) {
        content()
    } else {
        Scaffold(
            topBar = {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(stringResource(R.string.setting_page_preferences_general))
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

@Composable
private fun LanguagePickerDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_page_language)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                (listOf("") + AppLanguage.supportedTags).forEach { tag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(tag) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = tag == selected, onClick = { onSelect(tag) })
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (tag.isBlank()) {
                                stringResource(R.string.setting_page_language_system)
                            } else {
                                AppLanguage.displayName(tag)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

/** 滑块类型标签；只给设置页用。 */
@Composable
private fun sliderTypeLabel(type: SliderType): String = stringResource(
    when (type) {
        SliderType.MATERIAL_YOU -> R.string.setting_display_page_slider_type_material_you
        SliderType.FANCY -> R.string.setting_display_page_slider_type_fancy
        SliderType.MATERIAL -> R.string.setting_display_page_slider_type_material
        SliderType.HYPEROS -> R.string.setting_display_page_slider_type_hyperos
    }
)

/** 开关类型标签；只给设置页用。 */
@Composable
private fun switchTypeLabel(type: SwitchType): String = stringResource(
    when (type) {
        SwitchType.MATERIAL_YOU -> R.string.setting_display_page_switch_type_material_you
        SwitchType.COMPOSE -> R.string.setting_display_page_switch_type_compose
        SwitchType.PIXEL -> R.string.setting_display_page_switch_type_pixel
        SwitchType.FLUENT -> R.string.setting_display_page_switch_type_fluent
        SwitchType.CUPERTINO -> R.string.setting_display_page_switch_type_cupertino
        SwitchType.LIQUID_GLASS -> R.string.setting_display_page_switch_type_liquid_glass
        SwitchType.HYPEROS -> R.string.setting_display_page_switch_type_hyperos
        SwitchType.ONE_UI -> R.string.setting_display_page_switch_type_one_ui
    }
)

/** 形状类型的中文/英文标签；只给设置页用，别在别处重复一份。 */
@Composable
private fun cornerStyleLabel(style: CornerStyle): String = stringResource(
    when (style) {
        CornerStyle.ROUNDED -> R.string.setting_display_page_corner_style_rounded
        CornerStyle.CUT -> R.string.setting_display_page_corner_style_cut
        CornerStyle.SQUIRCLE -> R.string.setting_display_page_corner_style_squircle
        CornerStyle.SMOOTH -> R.string.setting_display_page_corner_style_smooth
        CornerStyle.WAVY -> R.string.setting_display_page_corner_style_wavy
        CornerStyle.SCOOP -> R.string.setting_display_page_corner_style_scoop
        CornerStyle.NOTCH -> R.string.setting_display_page_corner_style_notch
    }
)

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
