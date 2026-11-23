package me.rerere.rikkahub.ui.pages.setting

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moriafly.salt.ui.ItemContainer
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.AnimationSpeed
import me.rerere.rikkahub.data.datastore.ChatFontFamily
import me.rerere.rikkahub.data.datastore.ContextMeterStyle
import me.rerere.rikkahub.data.datastore.DisplayMode
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FileUtils
import me.rerere.rikkahub.data.model.DEFAULT_FLOATING_BAR_ITEMS
import me.rerere.rikkahub.data.model.FloatingBarItem
import me.rerere.rikkahub.data.model.MAX_FLOATING_BAR_ITEMS
import me.rerere.rikkahub.ui.components.ai.FloatingBarSizing
import me.rerere.rikkahub.ui.components.ai.icon
import me.rerere.rikkahub.ui.components.ai.label
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.AppSlider
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.NumberSlider
import me.rerere.rikkahub.ui.components.ui.PercentNumberSlider
import me.rerere.rikkahub.ui.components.ui.SaltGroup
import me.rerere.rikkahub.ui.components.ui.SaltIconRow
import me.rerere.rikkahub.ui.components.ui.SaltSwitchRow
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.modifier.MotionExpandCollapse
import me.rerere.rikkahub.ui.style.IconButton
import me.rerere.rikkahub.ui.style.TextButton
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPadding
import me.rerere.rikkahub.ui.theme.rememberChatFontFamily
import me.rerere.rikkahub.utils.plus
import me.rerere.ui.components.AnimatedAlertDialog
import me.rerere.ui.components.SaltModalSheet
import me.rerere.ui.toast.ToastType
import me.rerere.workspace.HostPaths
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingPreferencesUIPage(embedded: Boolean = false, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val chatFontFamily = rememberChatFontFamily(displaySetting)

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings { it.copy(displaySetting = setting) }
    }

    var showFloatingBarAddSheet by rememberSaveable { mutableStateOf(false) }
    // 拖动大小滑块时只改草稿，松手才写设置：每帧落盘要把整份 DisplaySetting 重新编码一遍
    var draftFloatingBarScale by remember(displaySetting.floatingBarScale) {
        mutableFloatStateOf(FloatingBarSizing.clampScale(displaySetting.floatingBarScale))
    }

    fun moveFloatingBarItem(from: Int, to: Int) {
        val items = displaySetting.floatingBarItems.toMutableList()
        if (from !in items.indices || to !in items.indices) return
        items.add(to, items.removeAt(from))
        updateDisplaySetting(displaySetting.copy(floatingBarItems = items))
    }

    // 自定义字体回退链：优先用列表；旧配置只有单字体时退化为单元素列表
    val customFontPaths = if (displaySetting.chatCustomFontPaths.isNotEmpty()) {
        displaySetting.chatCustomFontPaths
    } else {
        listOf(displaySetting.chatCustomFontPath).filter { it.isNotBlank() }
    }
    val customFontNames = if (displaySetting.chatCustomFontPaths.isNotEmpty()) {
        displaySetting.chatCustomFontNames
    } else {
        listOf(displaySetting.chatCustomFontName).filter { it.isNotBlank() }
    }

    val importSuccessMsg = stringResource(R.string.setting_display_page_custom_font_import_success)
    val importFailedMsg = stringResource(R.string.setting_display_page_custom_font_import_failed)
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    importCustomChatFontInternal(context, uri)
                }
            }.onSuccess { importedFont ->
                // 追加到回退链尾部：旧配置单字体迁移到列表首位；names 与 paths 按索引对齐（缺失补空串，UI 有兜底显示）
                val basePaths = if (displaySetting.chatCustomFontPaths.isNotEmpty()) displaySetting.chatCustomFontPaths
                    else listOf(displaySetting.chatCustomFontPath).filter { it.isNotBlank() }
                val baseNames = basePaths.mapIndexed { i, _ -> displaySetting.chatCustomFontNames.getOrNull(i) ?: "" }
                val newPaths = basePaths + importedFont.relativePath
                val newNames = baseNames + importedFont.displayName
                updateDisplaySetting(
                    displaySetting.copy(
                        chatFontFamily = ChatFontFamily.CUSTOM,
                        chatCustomFontPaths = newPaths,
                        chatCustomFontNames = newNames,
                        // 单字段同步为新首位，避免导入/删除后悬挂引用
                        chatCustomFontPath = newPaths.first(),
                        chatCustomFontName = newNames.first(),
                    )
                )
                toaster.show(importSuccessMsg, type = ToastType.Success)
            }.onFailure { error ->
                toaster.show(importFailedMsg.format(error.message.orEmpty()), type = ToastType.Error)
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 横向内距交给 Salt 的 RoundedColumn（自带 16dp），这里只留全宽模式的刘海安全区
                .adaptiveHorizontalPadding(0.dp)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 屏幕方向：点一下从四个模式里选
            var showDisplayModeDialog by remember { mutableStateOf(false) }
            RoundedColumn {
                ItemContainer {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = SaltTheme.dimens.item)
                            .clickable(onClick = { showDisplayModeDialog = true }),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.setting_display_page_display_mode_title))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = displaySetting.displayMode.description, color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                        }
                        ValuePill(displaySetting.displayMode.label)
                    }
                }
            }
            if (showDisplayModeDialog) {
                DisplayModePickerDialog(
                    selected = displaySetting.displayMode,
                    onSelect = { mode ->
                        updateDisplaySetting(displaySetting.copy(displayMode = mode))
                        showDisplayModeDialog = false
                    },
                    onDismiss = { showDisplayModeDialog = false },
                )
            }

            SaltGroup(title = stringResource(R.string.setting_page_message_display_settings)) {
                SaltSwitchRow(
                    checked = displaySetting.showUserAvatar,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showUserAvatar = it)) },
                    headline = stringResource(R.string.setting_display_page_show_user_avatar_title),
                    supporting = stringResource(R.string.setting_display_page_show_user_avatar_desc),
                )
                SaltSwitchRow(
                    checked = displaySetting.showAssistantBubble,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showAssistantBubble = it)) },
                    headline = stringResource(R.string.setting_display_page_show_assistant_bubble_title),
                    supporting = stringResource(R.string.setting_display_page_show_assistant_bubble_desc),
                )
                if (displaySetting.showAssistantBubble) {
                    SaltSwitchRow(
                        checked = displaySetting.splitAssistantBubbleByParagraph,
                        onCheckedChange = {
                            updateDisplaySetting(displaySetting.copy(splitAssistantBubbleByParagraph = it))
                        },
                        headline = stringResource(R.string.setting_display_page_split_assistant_bubble_title),
                        supporting = stringResource(R.string.setting_display_page_split_assistant_bubble_desc),
                    )
                }
                ItemContainer {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.setting_display_page_empty_slogan_title))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = stringResource(R.string.setting_display_page_empty_slogan_desc), color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = displaySetting.emptyChatSlogan,
                            onValueChange = {
                                updateDisplaySetting(displaySetting.copy(emptyChatSlogan = it))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.chat_empty_default_slogan)) },
                            singleLine = true,
                        )
                    }
                }
                ItemContainer {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.setting_display_page_bubble_opacity_title))
                            }
                            ValuePill("${(displaySetting.bubbleOpacity * 100).roundToInt()}%")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        AppSlider(
                            value = displaySetting.bubbleOpacity,
                            onValueChange = {
                                updateDisplaySetting(displaySetting.copy(bubbleOpacity = it))
                            },
                            valueRange = 0f..1.0f,
                            steps = 19,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            RoundedColumn {
                SaltSwitchRow(
                    checked = displaySetting.showModelIcon,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showModelIcon = it)) },
                    headline = stringResource(R.string.setting_display_page_chat_list_model_icon_title),
                    supporting = stringResource(R.string.setting_display_page_chat_list_model_icon_desc),
                )
                SaltSwitchRow(
                    checked = displaySetting.showModelName,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showModelName = it)) },
                    headline = stringResource(R.string.setting_display_page_show_model_name_title),
                    supporting = stringResource(R.string.setting_display_page_show_model_name_desc),
                )
                SaltSwitchRow(
                    checked = displaySetting.buttonShadow,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(buttonShadow = it)) },
                    headline = stringResource(R.string.setting_display_page_button_shadow_title),
                    supporting = stringResource(R.string.setting_display_page_button_shadow_desc),
                )
                SaltSwitchRow(
                    checked = displaySetting.hideCompletedTodoCard,
                    onCheckedChange = {
                        updateDisplaySetting(displaySetting.copy(hideCompletedTodoCard = it))
                    },
                    headline = stringResource(R.string.setting_display_page_hide_completed_todo_title),
                    supporting = stringResource(R.string.setting_display_page_hide_completed_todo_desc),
                )
                SaltSwitchRow(
                    checked = displaySetting.showDateTimeInMessage,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showDateTimeInMessage = it)) },
                    headline = stringResource(R.string.setting_display_page_show_datetime_in_message_title),
                    supporting = stringResource(R.string.setting_display_page_show_datetime_in_message_desc),
                )
                SaltSwitchRow(
                    checked = displaySetting.useL3HubReasoningStyle,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(useL3HubReasoningStyle = it)) },
                    headline = stringResource(R.string.setting_display_page_use_l3hub_reasoning_title),
                    supporting = stringResource(R.string.setting_display_page_use_l3hub_reasoning_desc),
                )
                SaltSwitchRow(
                    checked = displaySetting.showTokenUsage,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showTokenUsage = it)) },
                    headline = stringResource(R.string.setting_display_page_show_token_usage_title),
                    supporting = stringResource(R.string.setting_display_page_show_token_usage_desc),
                )
                SaltSwitchRow(
                    checked = displaySetting.showQuotaBar,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showQuotaBar = it)) },
                    headline = stringResource(R.string.setting_display_page_quota_bar_title),
                    supporting = stringResource(R.string.setting_display_page_quota_bar_desc),
                )
                SaltSwitchRow(
                    checked = displaySetting.binaryUnits,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(binaryUnits = it)) },
                    headline = stringResource(R.string.setting_display_page_binary_units_title),
                    supporting = stringResource(R.string.setting_display_page_binary_units_desc),
                )
                SaltSwitchRow(
                    checked = displaySetting.usagePieChart,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(usagePieChart = it)) },
                    headline = stringResource(R.string.setting_display_page_usage_pie_title),
                    supporting = stringResource(R.string.setting_display_page_usage_pie_desc),
                )
                SaltSwitchRow(
                    checked = displaySetting.contextMeterStyle != ContextMeterStyle.OFF,
                    onCheckedChange = { enabled ->
                        updateDisplaySetting(
                            displaySetting.copy(
                                contextMeterStyle = if (enabled) ContextMeterStyle.BAR else ContextMeterStyle.OFF
                            )
                        )
                    },
                    headline = stringResource(R.string.setting_display_page_context_meter_switch_title),
                    supporting = stringResource(R.string.setting_display_page_context_meter_switch_desc),
                )
            }

            RoundedColumn {
                SaltSwitchRow(
                    checked = displaySetting.showToolCallOutput,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showToolCallOutput = it)) },
                    headline = stringResource(R.string.setting_display_page_show_tool_call_output_title),
                    supporting = stringResource(R.string.setting_display_page_show_tool_call_output_desc),
                )
                ItemContainer {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.setting_display_page_ui_corner_radius_title))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = stringResource(R.string.setting_display_page_ui_corner_radius_desc, displaySetting.uiCornerRadius), color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        NumberSlider(
                            value = displaySetting.uiCornerRadius,
                            onValueChange = {
                                updateDisplaySetting(displaySetting.copy(uiCornerRadius = it))
                            },
                            valueRange = 0..32,
                        )
                    }
                }
                ItemContainer {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.setting_display_page_input_padding_title))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = stringResource(
                                        R.string.setting_display_page_input_padding_desc,
                                        displaySetting.inputPadding,
                                    ), color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        AppSlider(
                            value = displaySetting.inputPadding.toFloat(),
                            onValueChange = {
                                updateDisplaySetting(displaySetting.copy(inputPadding = it.roundToInt()))
                            },
                            valueRange = 0f..16f,
                            steps = 15,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                SaltSwitchRow(
                    checked = displaySetting.disableAnimations,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(disableAnimations = it)) },
                    headline = stringResource(R.string.setting_display_page_disable_animations_title),
                    supporting = stringResource(R.string.setting_display_page_disable_animations_desc),
                )
                // 关掉动效以后这一项没有意义，跟着开关一起收起
                if (!displaySetting.disableAnimations) {
                    AnimationSpeed.entries.forEach { speed ->
                        ItemContainer {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = SaltTheme.dimens.item)
                                    .clickable(onClick = {
                                        updateDisplaySetting(displaySetting.copy(animationSpeed = speed))
                                }),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = speed.label)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = speed.description, color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                                }
                                RadioButton(
                                    selected = displaySetting.animationSpeed == speed,
                                    onClick = null,
                                )
                            }
                        }
                    }
                }
                SaltSwitchRow(
                    checked = displaySetting.showThinkingContent,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showThinkingContent = it)) },
                    headline = stringResource(R.string.setting_display_page_show_thinking_content_title),
                    supporting = stringResource(R.string.setting_display_page_show_thinking_content_desc),
                )
                SaltSwitchRow(
                    checked = displaySetting.autoCloseThinking,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(autoCloseThinking = it)) },
                    headline = stringResource(R.string.setting_display_page_auto_collapse_thinking_title),
                    supporting = stringResource(R.string.setting_display_page_auto_collapse_thinking_desc),
                )
                SaltSwitchRow(
                    checked = displaySetting.showThinkingSummary,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showThinkingSummary = it)) },
                    headline = stringResource(R.string.setting_display_page_show_thinking_summary_title),
                    supporting = stringResource(R.string.setting_display_page_show_thinking_summary_desc),
                )
                SaltSwitchRow(
                    checked = displaySetting.enableLatexRendering,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(enableLatexRendering = it)) },
                    headline = stringResource(R.string.setting_display_page_enable_latex_rendering_title),
                    supporting = stringResource(R.string.setting_display_page_enable_latex_rendering_desc),
                )
                SaltSwitchRow(
                    checked = displaySetting.enableQuoteHighlight,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(enableQuoteHighlight = it)) },
                    headline = stringResource(R.string.setting_display_page_enable_quote_highlight_title),
                    supporting = stringResource(R.string.setting_display_page_enable_quote_highlight_desc),
                )
            }

            // 动画速度下面紧跟动态模糊：这两项调的都是过渡手感，放在一起才找得到。
            // 三项数值对所有带模糊的过渡一起生效，不需要一个效果一个效果地改
            // 整张卡也跟着「关闭动效」收放：if 掉的话一帧消失，卡片圆角却还在缓动
            MotionExpandCollapse(visible = !displaySetting.disableAnimations) {
                val motionBlur = displaySetting.motionBlur
                SaltGroup(title = stringResource(R.string.setting_display_page_motion_blur_title)) {
                    SaltSwitchRow(
                        checked = motionBlur.enabled,
                        onCheckedChange = {
                            updateDisplaySetting(
                                displaySetting.copy(
                                    motionBlur = displaySetting.motionBlur.copy(enabled = it)
                                )
                            )
                        },
                        headline = stringResource(R.string.setting_display_page_motion_blur_switch),
                        supporting = stringResource(R.string.setting_display_page_motion_blur_desc),
                    )
                    if (motionBlur.enabled) {
                        SaltSwitchRow(
                            checked = motionBlur.scrollBlur,
                            onCheckedChange = {
                                updateDisplaySetting(
                                    displaySetting.copy(
                                        motionBlur = displaySetting.motionBlur.copy(scrollBlur = it)
                                    )
                                )
                            },
                            headline = stringResource(R.string.setting_display_page_motion_blur_scroll_switch),
                            supporting = stringResource(R.string.setting_display_page_motion_blur_scroll_desc),
                        )
                        ItemContainer {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = stringResource(R.string.setting_display_page_motion_blur_radius_title))
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = stringResource(
                                                R.string.setting_display_page_motion_blur_radius_desc,
                                                motionBlur.radius,
                                            ), color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                NumberSlider(
                                    value = motionBlur.radius,
                                    onValueChange = {
                                        updateDisplaySetting(
                                            displaySetting.copy(
                                                motionBlur = displaySetting.motionBlur.copy(radius = it)
                                            )
                                        )
                                    },
                                    valueRange = 0..12,
                                    suffix = "dp",
                                )
                            }
                        }
                        ItemContainer {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = stringResource(R.string.setting_display_page_motion_blur_duration_title))
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = stringResource(
                                                R.string.setting_display_page_motion_blur_duration_desc,
                                                motionBlur.durationMillis,
                                            ), color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                NumberSlider(
                                    value = motionBlur.durationMillis,
                                    onValueChange = {
                                        updateDisplaySetting(
                                            displaySetting.copy(
                                                motionBlur = displaySetting.motionBlur.copy(durationMillis = it)
                                            )
                                        )
                                    },
                                    valueRange = 80..600,
                                    suffix = "ms",
                                    // 跨度过大，轨道上不画刻度
                                    steps = 0,
                                )
                            }
                        }
                        ItemContainer {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = stringResource(R.string.setting_display_page_motion_blur_travel_title))
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = stringResource(
                                                R.string.setting_display_page_motion_blur_travel_desc,
                                                motionBlur.travel,
                                            ), color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                NumberSlider(
                                    value = motionBlur.travel,
                                    onValueChange = {
                                        updateDisplaySetting(
                                            displaySetting.copy(
                                                motionBlur = displaySetting.motionBlur.copy(travel = it)
                                            )
                                        )
                                    },
                                    valueRange = 8..64,
                                    suffix = "dp",
                                )
                            }
                        }
                    }
                }
            }

            SaltGroup(title = stringResource(R.string.setting_display_page_floating_bar_title)) {
                SaltSwitchRow(
                    checked = displaySetting.enableFloatingBar,
                    onCheckedChange = { enabled ->
                        updateDisplaySetting(
                            displaySetting.copy(
                                enableFloatingBar = enabled,

                                floatingBarItems = if (enabled && displaySetting.floatingBarItems.isEmpty()) {
                                    DEFAULT_FLOATING_BAR_ITEMS
                                } else {
                                    displaySetting.floatingBarItems
                                },
                            )
                        )
                    },
                    headline = stringResource(R.string.setting_display_page_floating_bar_switch),
                    supporting = stringResource(R.string.setting_display_page_floating_bar_desc),
                )
                if (displaySetting.enableFloatingBar) {
                    SaltSwitchRow(
                        checked = displaySetting.floatingBarAutoHide,
                        onCheckedChange = { autoHide ->
                            updateDisplaySetting(displaySetting.copy(floatingBarAutoHide = autoHide))
                        },
                        headline = stringResource(R.string.setting_display_page_floating_bar_auto_hide_title),
                        supporting = stringResource(R.string.setting_display_page_floating_bar_auto_hide_desc),
                    )
                }
                if (displaySetting.enableFloatingBar) {
                    ItemContainer {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = stringResource(R.string.setting_display_page_floating_bar_size_title))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = stringResource(R.string.setting_display_page_floating_bar_size_desc), color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                                }
                                ValuePill(FloatingBarSizing.percentLabel(draftFloatingBarScale))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            PercentNumberSlider(
                                value = draftFloatingBarScale,
                                onValueChange = { ratio ->
                                    draftFloatingBarScale = FloatingBarSizing.clampScale(ratio)
                                },
                                onValueChangeFinished = {
                                    updateDisplaySetting(
                                        displaySetting.copy(floatingBarScale = draftFloatingBarScale)
                                    )
                                },
                                valueRange = FloatingBarSizing.MIN_SCALE..FloatingBarSizing.MAX_SCALE,
                            )
                        }
                    }
                }
                displaySetting.floatingBarItems.forEachIndexed { index, barItem ->
                    if (displaySetting.enableFloatingBar) {
                        SaltIconRow(
                            icon = barItem.icon,
                            headline = barItem.label(),
                            supporting = stringResource(
                                R.string.setting_display_page_floating_bar_slot,
                                index + 1,
                            ),
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (index > 0) {
                                        IconButton(onClick = { moveFloatingBarItem(index, index - 1) }) {
                                            Icon(
                                                HugeIcons.ArrowUp01,
                                                contentDescription = stringResource(
                                                    R.string.setting_display_page_floating_bar_move_up
                                                ),
                                            )
                                        }
                                    }
                                    if (index < displaySetting.floatingBarItems.lastIndex) {
                                        IconButton(onClick = { moveFloatingBarItem(index, index + 1) }) {
                                            Icon(
                                                HugeIcons.ArrowDown01,
                                                contentDescription = stringResource(
                                                    R.string.setting_display_page_floating_bar_move_down
                                                ),
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            updateDisplaySetting(
                                                displaySetting.copy(
                                                    floatingBarItems = displaySetting.floatingBarItems
                                                        .filterIndexed { i, _ -> i != index },
                                                )
                                            )
                                        }
                                    ) {
                                        Icon(
                                            HugeIcons.Delete02,
                                            contentDescription = stringResource(
                                                R.string.setting_display_page_floating_bar_clear
                                            ),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
                if (displaySetting.enableFloatingBar &&
                    displaySetting.floatingBarItems.size < MAX_FLOATING_BAR_ITEMS) {
                    SaltIconRow(
                        icon = HugeIcons.Add01,
                        headline = stringResource(R.string.setting_display_page_floating_bar_add_slot),
                        supporting = stringResource(
                            R.string.setting_display_page_floating_bar_slots_count,
                            displaySetting.floatingBarItems.size,
                            MAX_FLOATING_BAR_ITEMS,
                        ),
                        onClick = { showFloatingBarAddSheet = true },
                        trailing = {
                            Icon(HugeIcons.ArrowRight01, contentDescription = null)
                        },
                    )
                }
            }

            RoundedColumn {
                ItemContainer {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.setting_display_page_chat_font_family_title))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ConnectedChoiceButtons(
                            options = ChatFontFamily.entries,
                            selected = displaySetting.chatFontFamily,
                            onSelect = { family ->
                                if (family == ChatFontFamily.CUSTOM &&
                                    displaySetting.chatCustomFontPaths.isEmpty() &&
                                    displaySetting.chatCustomFontPath.isBlank()
                                ) {
                                    fontPickerLauncher.launch(CustomFontMimeTypesUI)
                                } else {
                                    updateDisplaySetting(displaySetting.copy(chatFontFamily = family))
                                }
                            },
                            minHeight = 64.dp,
                        ) { family, _ ->
                            // 每个按钮用对应的字体写一个样字
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Aa",
                                    fontFamily = family.toFontFamilyUI(chatFontFamily),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = family.labelUI(),
                                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 11.sp),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
                SaltSwitchRow(
                    checked = displaySetting.fontApplyGlobal,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(fontApplyGlobal = it)) },
                    headline = stringResource(R.string.setting_display_page_font_global_title),
                    supporting = stringResource(R.string.setting_display_page_font_global_desc),
                    enabled = displaySetting.chatFontFamily != ChatFontFamily.DEFAULT,
                )
                ItemContainer {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = SaltTheme.dimens.item)
                            .clickable(onClick = { fontPickerLauncher.launch(CustomFontMimeTypesUI) }),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.setting_display_page_custom_font_title))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = if (customFontPaths.isEmpty()) {
                                    stringResource(R.string.setting_display_page_custom_font_not_imported)
                                } else {
                                    stringResource(R.string.setting_display_page_custom_font_chain_desc, customFontPaths.size)
                                }, color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                        }
                        IconButton(
                            onClick = { fontPickerLauncher.launch(CustomFontMimeTypesUI) }
                        ) {
                            Icon(
                                HugeIcons.FileImport,
                                contentDescription = stringResource(
                                    R.string.setting_display_page_custom_font_import
                                )
                            )
                        }
                    }
                }
                // 已导入字体回退链：每款一行，可上移调整优先级、单独删除（删除后链自动收拢）
                if (customFontPaths.isNotEmpty()) {
                    customFontPaths.forEachIndexed { index, path ->
                        ItemContainer {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = SaltTheme.dimens.item),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = customFontNames.getOrNull(index)?.takeIf { it.isNotBlank() }
                                            ?: stringResource(R.string.setting_display_page_custom_font_fallback_name, index + 1))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = stringResource(R.string.setting_display_page_custom_font_chain_position, index + 1), color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                                }
                                Row {
                                    if (index > 0) {
                                        IconButton(
                                            onClick = { updateDisplaySetting(moveChatFontUp(displaySetting, index)) }
                                        ) {
                                            Icon(
                                                HugeIcons.ArrowUp02,
                                                contentDescription = stringResource(
                                                    R.string.setting_display_page_custom_font_move_up
                                                )
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            deleteCustomChatFontInternal(context, path)
                                            updateDisplaySetting(removeCustomChatFontAt(displaySetting, index))
                                        }
                                    ) {
                                        Icon(
                                            HugeIcons.Delete02,
                                            contentDescription = stringResource(
                                                R.string.setting_display_page_custom_font_remove
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                ItemContainer {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.setting_display_page_ui_scale_title))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = stringResource(R.string.setting_display_page_ui_scale_desc), color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                            }
                            ValuePill("${(displaySetting.uiScale * 100).roundToInt()}%")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        AppSlider(
                            value = displaySetting.uiScale,
                            onValueChange = { ratio ->
                                // 5% 一档：0.5、0.55 … 2.0
                                val snapped = (ratio * 20f).roundToInt() / 20f
                                updateDisplaySetting(displaySetting.copy(uiScale = snapped))
                            },
                            valueRange = 0.5f..2f,
                            steps = 29,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                ItemContainer {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.setting_display_page_font_size_title))
                            }
                            ValuePill("${(displaySetting.fontSizeRatio * 100).toInt()}%")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        AppSlider(
                            value = displaySetting.fontSizeRatio,
                            onValueChange = { ratio ->
                                val snapped = (ratio * 20f).roundToInt() / 20f
                                updateDisplaySetting(displaySetting.copy(fontSizeRatio = snapped))
                            },
                            valueRange = 0.5f..2f,
                            steps = 29,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // 预览放在凹下去的底色里，和设置项本身区分开
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            MarkdownBlock(
                                content = stringResource(R.string.setting_display_page_font_size_preview),
                                modifier = Modifier.padding(12.dp),
                                style = LocalTextStyle.current.copy(
                                    fontSize = LocalTextStyle.current.fontSize * displaySetting.fontSizeRatio,
                                    lineHeight = LocalTextStyle.current.lineHeight * displaySetting.fontSizeRatio,
                                    fontFamily = chatFontFamily
                                )
                            )
                        }
                    }
                }
            }

            SaltGroup(title = stringResource(R.string.setting_page_code_display_settings)) {
                SaltSwitchRow(
                    checked = displaySetting.codeBlockAutoWrap,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(codeBlockAutoWrap = it)) },
                    headline = stringResource(R.string.setting_display_page_code_block_auto_wrap_title),
                    supporting = stringResource(R.string.setting_display_page_code_block_auto_wrap_desc),
                )
                SaltSwitchRow(
                    checked = displaySetting.codeBlockAutoCollapse,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(codeBlockAutoCollapse = it)) },
                    headline = stringResource(R.string.setting_display_page_code_block_auto_collapse_title),
                    supporting = stringResource(R.string.setting_display_page_code_block_auto_collapse_desc),
                )
                SaltSwitchRow(
                    checked = displaySetting.showLineNumbers,
                    onCheckedChange = { updateDisplaySetting(displaySetting.copy(showLineNumbers = it)) },
                    headline = stringResource(R.string.setting_display_page_show_line_numbers_title),
                    supporting = stringResource(R.string.setting_display_page_show_line_numbers_desc),
                )
            }

        }
    }
    FloatingBarAddSheet(
        visible = showFloatingBarAddSheet,
        configured = displaySetting.floatingBarItems,
        onAdd = { barItem ->
            if (displaySetting.floatingBarItems.size < MAX_FLOATING_BAR_ITEMS &&
                barItem !in displaySetting.floatingBarItems
            ) {
                updateDisplaySetting(
                    displaySetting.copy(
                        floatingBarItems = displaySetting.floatingBarItems + barItem,
                    )
                )
            }
            showFloatingBarAddSheet = false
        },
        onDismiss = { showFloatingBarAddSheet = false },
    )
    if (embedded) {
        content()
    } else {
        Scaffold(
            topBar = {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(stringResource(R.string.setting_page_preferences_ui))
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

/** 给悬浮栏挑一个新槽位；已经放上去的项目不再列出。 */
@Composable
private fun FloatingBarAddSheet(
    visible: Boolean,
    configured: List<FloatingBarItem>,
    onAdd: (FloatingBarItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val available = FloatingBarItem.entries.filter { it !in configured }
    SaltModalSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.setting_display_page_floating_bar_add_sheet_title),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoundedColumn {
                available.forEach { barItem ->
                    SaltIconRow(
                        icon = barItem.icon,
                        headline = barItem.label(),
                        onClick = { onAdd(barItem) },
                    )
                }
            }
        }
    }
}

private val CustomFontMimeTypesUI = arrayOf(
    "font/*",
    "application/x-font-ttf",
    "application/x-font-otf",
    "application/vnd.ms-opentype",
    "application/octet-stream",
    "*/*",
)

private val CustomFontExtensionsUI = setOf("ttf", "otf", "ttc")

private data class ImportedChatFontUI(
    val relativePath: String,
    val displayName: String,
)

@Composable
private fun ChatFontFamily.labelUI(): String = when (this) {
    ChatFontFamily.DEFAULT -> stringResource(R.string.setting_display_page_chat_font_family_default)
    ChatFontFamily.SERIF -> stringResource(R.string.setting_display_page_chat_font_family_serif)
    ChatFontFamily.MONOSPACE -> stringResource(R.string.setting_display_page_chat_font_family_monospace)
    ChatFontFamily.CUSTOM -> stringResource(R.string.setting_display_page_chat_font_family_custom)
}

private fun ChatFontFamily.toFontFamilyUI(customFontFamily: FontFamily): FontFamily = when (this) {
    ChatFontFamily.DEFAULT -> FontFamily.Default
    ChatFontFamily.SERIF -> FontFamily.Serif
    ChatFontFamily.MONOSPACE -> FontFamily.Monospace
    ChatFontFamily.CUSTOM -> customFontFamily
}

private fun importCustomChatFontInternal(context: Context, uri: Uri): ImportedChatFontUI {
    val displayName = FileUtils.getFileNameFromUri(context, uri)?.takeIf { it.isNotBlank() } ?: "custom_font"
    val extension = displayName.substringAfterLast('.', "")
        .lowercase()
        .takeIf { it in CustomFontExtensionsUI }
        ?: "ttf"
    val fontDir = File(context.filesDir, FileFolders.FONTS).apply { mkdirs() }
    val targetFile = File(fontDir, "chat_font.${UUID.randomUUID()}.$extension")
    val tempFile = File(fontDir, "chat_font_import.tmp")

    try {
        tempFile.delete()
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("Unable to open selected font")

        runCatching {
            Typeface.createFromFile(tempFile)
        }.onFailure { error ->
            throw IllegalArgumentException(error.message ?: "Invalid font file", error)
        }

        // 多字体回退链：文件名带时间戳天然唯一，直接落盘，不清理已有字体
        check(tempFile.renameTo(targetFile)) { "Unable to save selected font" }
    } catch (error: Throwable) {
        tempFile.delete()
        throw error
    }

    val relativePath = FileUtils.getRelativePathInFilesDir(context.filesDir, targetFile)
        ?: "${FileFolders.FONTS}/${targetFile.name}"
    return ImportedChatFontUI(relativePath = relativePath, displayName = displayName)
}

/** 从回退链中移除第 index 款字体；删光后回到默认字体并清空所有字体字段 */
private fun removeCustomChatFontAt(displaySetting: DisplaySetting, index: Int): DisplaySetting {
    // 旧配置单字体（列表为空）：删除即清空单字体字段并回到默认
    if (displaySetting.chatCustomFontPaths.isEmpty()) {
        return displaySetting.copy(
            chatFontFamily = ChatFontFamily.DEFAULT,
            chatCustomFontPath = "",
            chatCustomFontName = "",
        )
    }
    val newPaths = displaySetting.chatCustomFontPaths.filterIndexed { i, _ -> i != index }
    if (newPaths.isEmpty()) {
        return displaySetting.copy(
            chatFontFamily = ChatFontFamily.DEFAULT,
            chatCustomFontPaths = emptyList(),
            chatCustomFontNames = emptyList(),
            chatCustomFontPath = "",
            chatCustomFontName = "",
        )
    }
    // names 按原索引重映射（删除位置之后的项索引前移一位），与 paths 保持对齐
    val newNames = newPaths.mapIndexed { newIndex, _ ->
        val origIndex = if (newIndex < index) newIndex else newIndex + 1
        displaySetting.chatCustomFontNames.getOrNull(origIndex) ?: ""
    }
    return displaySetting.copy(
        chatCustomFontPaths = newPaths,
        chatCustomFontNames = newNames,
        // 单字段同步为新首位，避免悬挂引用
        chatCustomFontPath = newPaths.first(),
        chatCustomFontName = newNames.first(),
    )
}

/** 把第 index 款字体上移一位（越靠前优先级越高）；首行或非法索引时原样返回 */
private fun moveChatFontUp(displaySetting: DisplaySetting, index: Int): DisplaySetting {
    val paths = displaySetting.chatCustomFontPaths
    if (index <= 0 || index >= paths.size) return displaySetting
    val newPaths = paths.toMutableList().also { list ->
        val item = list.removeAt(index)
        list.add(index - 1, item)
    }
    val newNames = displaySetting.chatCustomFontNames.toMutableList().also { list ->
        if (index < list.size) {
            val item = list.removeAt(index)
            list.add(index - 1, item)
        }
    }
    return displaySetting.copy(
        chatCustomFontPaths = newPaths,
        chatCustomFontNames = newNames,
        // 首位变了，单字段同步
        chatCustomFontPath = newPaths.first(),
        chatCustomFontName = newNames.firstOrNull() ?: "",
    )
}

private fun deleteCustomChatFontInternal(context: Context, relativePath: String) {
    if (relativePath.isBlank()) return
    HostPaths.resolve(context.filesDir, relativePath)
        ?.takeIf { HostPaths.relativeTo(context.filesDir, it)?.isNotEmpty() == true }
        ?.delete()
}

private val AnimationSpeed.label: String
    @Composable get() = when (this) {
        AnimationSpeed.SYSTEM -> stringResource(R.string.setting_display_page_animation_speed_system)
        AnimationSpeed.FAST -> stringResource(R.string.setting_display_page_animation_speed_fast)
        AnimationSpeed.GENTLE -> stringResource(R.string.setting_display_page_animation_speed_gentle)
    }

private val AnimationSpeed.description: String
    @Composable get() = when (this) {
        AnimationSpeed.SYSTEM -> stringResource(R.string.setting_display_page_animation_speed_system_desc)
        AnimationSpeed.FAST -> stringResource(R.string.setting_display_page_animation_speed_fast_desc)
        AnimationSpeed.GENTLE -> stringResource(R.string.setting_display_page_animation_speed_gentle_desc)
    }

private val DisplayMode.label: String
    @Composable get() = stringResource(
        when (this) {
            DisplayMode.PORTRAIT -> R.string.setting_display_page_display_mode_portrait
            DisplayMode.LANDSCAPE -> R.string.setting_display_page_display_mode_landscape
            DisplayMode.AUTO -> R.string.setting_display_page_display_mode_auto
            DisplayMode.SYSTEM -> R.string.setting_display_page_display_mode_system
        }
    )

private val DisplayMode.description: String
    @Composable get() = stringResource(
        when (this) {
            DisplayMode.PORTRAIT -> R.string.setting_display_page_display_mode_portrait_desc
            DisplayMode.LANDSCAPE -> R.string.setting_display_page_display_mode_landscape_desc
            DisplayMode.AUTO -> R.string.setting_display_page_display_mode_auto_desc
            DisplayMode.SYSTEM -> R.string.setting_display_page_display_mode_system_desc
        }
    )

/** 显示模式四选一；每项带一句说明，「自动」和「跟随系统」的差别不写清楚容易选错 */
@Composable
private fun DisplayModePickerDialog(
    selected: DisplayMode,
    onSelect: (DisplayMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_display_page_display_mode_title)) },
        text = {
            Column {
                DisplayMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == selected, onClick = { onSelect(mode) })
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mode.label)
                            Text(
                                text = mode.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
