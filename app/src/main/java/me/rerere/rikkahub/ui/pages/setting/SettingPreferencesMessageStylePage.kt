package me.rerere.rikkahub.ui.pages.setting

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.BubbleChat
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.AdvancedAppearanceSetting
import me.rerere.rikkahub.data.datastore.BackgroundSurfaceStyle
import me.rerere.rikkahub.data.datastore.ChatBubbleStyle
import me.rerere.rikkahub.data.datastore.ChatBubbleStyleTarget
import me.rerere.rikkahub.data.datastore.applyDisplaySettingEdit
import me.rerere.rikkahub.data.datastore.finiteAppearanceValue
import me.rerere.rikkahub.data.datastore.ChatBubbleStyleOverrides
import me.rerere.rikkahub.data.datastore.ChatMessageBackgroundStyle
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.components.message.messageBubbleMaterial
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.CardGroupValue
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.LocalTopBarHazeState
import me.rerere.rikkahub.ui.components.ui.NumberSlider
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.components.ui.resolveBubbleStyle
import me.rerere.rikkahub.ui.components.ui.resolvedLiveAppearanceRadius
import me.rerere.rikkahub.ui.components.ui.LocalAdvancedAppearanceBusy
import me.rerere.rikkahub.ui.components.ui.LocalAdvancedAppearancePowerSave
import me.rerere.rikkahub.ui.style.TopAppBar
import me.rerere.rikkahub.data.model.usesMaterial3Style
import me.rerere.rikkahub.ui.navigation.appearancePageContainerColor
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.ui.components.LocalSeparateWindow
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPadding
import me.rerere.rikkahub.utils.plus
import me.rerere.ui.components.AnimatedAlertDialog
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

/** 消息样式设置页：旧路由入口，只负责脚手架，内容在 [MessageBubbleStyleSettingsSection]。 */
@Composable
fun SettingPreferencesMessageStylePage(
    vm: SettingVM = koinViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val topBarHazeState = LocalTopBarHazeState.current

    Scaffold(
        containerColor = appearancePageContainerColor(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.message_style_page_title)) },
                navigationIcon = { BackButton() },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(topBarHazeState?.let { Modifier.hazeSource(it, zIndex = 1f) } ?: Modifier)
                .adaptiveHorizontalPadding(0.dp),
            contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                MessageBubbleStyleSettingsSection(
                    displaySetting = settings.displaySetting,
                    advancedAppearance = settings.advancedAppearanceSetting,
                    onDisplaySettingChange = { change ->
                        vm.updateSettings { latest -> latest.applyDisplaySettingEdit(change) }
                    },
                )
            }
        }
    }
}

/**
 * 消息气泡样式分段：三档样式、助手参数与重置对话框；保留其它角色的旧数据。
 *
 * 直接以 Column + CardGroup 输出，不带 Scaffold，也不自建 LazyColumn，
 * 供 advanced appearance 页的 chat item 直接调用。
 * DisplaySetting 由调用方持有；这里只把差量结果回传，不在本地保存整份快照。
 */
@Composable
internal fun MessageBubbleStyleSettingsSection(
    displaySetting: DisplaySetting,
    advancedAppearance: AdvancedAppearanceSetting,
    onDisplaySettingChange: (DisplaySetting.() -> DisplaySetting) -> Unit,
) {
    val latestOnChange by rememberUpdatedState(onDisplaySettingChange)
    // Keep drag updates visible immediately while SettingsStore persists them asynchronously.
    // The parent callback still applies the same transform to its latest DisplaySetting.
    var editingDisplaySetting by remember(displaySetting) { mutableStateOf(displaySetting) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    /** 以最新 DisplaySetting 为基准做差量修改，再交给调用方落库。 */
    fun update(transform: DisplaySetting.() -> DisplaySetting) {
        editingDisplaySetting = editingDisplaySetting.transform()
        latestOnChange(transform)
    }

    val style = editingDisplaySetting.chatMessageBackgroundStyle
    val customizeCards = editingDisplaySetting.customizeThinkingToolCards
    // This section only edits assistant bubbles; legacy overrides for other targets remain stored.
    val overrides = editingDisplaySetting.assistantChatBubbleStyleOverrides

    fun updateOverrides(transform: ChatBubbleStyleOverrides.() -> ChatBubbleStyleOverrides) {
        update {
            val current = assistantChatBubbleStyleOverrides
            val next = current.transform()
            copy(
                assistantChatBubbleStyleOverrides = next,
            )
        }
    }

    fun resetAssistantStyle() {
        update {
            copy(
                chatMessageBackgroundStyle = ChatMessageBackgroundStyle.DEFAULT,
                assistantChatBubbleStyleOverrides = ChatBubbleStyleOverrides(),
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 样式选择
        CardGroup {
            formItem(
                headlineContent = { Text(text = stringResource(R.string.message_style_page_style_title)) },
                supportingContent = { Text(stringResource(R.string.message_style_page_style_desc)) },
            ) {
                ConnectedChoiceButtons(
                    options = ChatMessageBackgroundStyle.entries,
                    selected = style,
                    onSelect = { next -> update { copy(chatMessageBackgroundStyle = next) } },
                ) { option, selected ->
                    ConnectedChoiceLabel(
                        text = when (option) {
                            ChatMessageBackgroundStyle.DEFAULT ->
                                stringResource(R.string.message_style_page_style_default)
                            ChatMessageBackgroundStyle.FROSTED ->
                                stringResource(R.string.message_style_page_style_frosted)
                            ChatMessageBackgroundStyle.SOLID ->
                                stringResource(R.string.message_style_page_style_solid)
                        },
                        selected = selected,
                    )
                }
            }
        }

        // Only offer the legacy toggle when there is a saved style to use (or to turn off).
        // Its parameters remain read-only, and users without legacy data see assistant controls only.
        if (customizeCards || !editingDisplaySetting.thinkingToolCardStyleOverrides.isDefault) {
            CardGroup {
                switchItem(
                    checked = customizeCards,
                    onCheckedChange = { next -> update { copy(customizeThinkingToolCards = next) } },
                    leadingContent = { CardGroupIcon(HugeIcons.BubbleChat) },
                    supportingContent = { Text(stringResource(R.string.message_style_page_customize_thinking_desc)) },
                    headlineContent = { Text(stringResource(R.string.message_style_page_customize_thinking)) },
                )
            }
        }

        if (style != ChatMessageBackgroundStyle.DEFAULT) {
            // Parameters are fixed to assistant; no role switch is exposed here.
            StyleParamsGroup(
                style = style,
                displaySetting = editingDisplaySetting,
                overrides = overrides,
                advancedAppearance = advancedAppearance,
                onOverridesChange = ::updateOverrides,
            )
        } else {
            CardGroup {
                formItem {
                    Text(
                        text = stringResource(R.string.message_style_page_default_hint),
                        color = LocalContentColor.current.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // 重置入口：分段被嵌进 advanced 页时没有页级 actions，入口放在分段末尾
        CardGroup {
            item(
                onClick = { showResetDialog = true },
                leadingContent = { CardGroupIcon(HugeIcons.Refresh03) },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.message_style_page_reset),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
            )
        }
    }

    if (showResetDialog) {
        AnimatedAlertDialog(
            onDismissRequest = { showResetDialog = false },
            confirmButton = {},
            title = { Text(stringResource(R.string.message_style_page_reset_confirm)) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.message_style_page_cancel),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showResetDialog = false }
                            .padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.message_style_page_reset),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable {
                                showResetDialog = false
                                resetAssistantStyle()
                            }
                            .padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            },
        )
    }
}

/** Parameters reflect the actual assistant material and its inherited values. */
@Composable
private fun StyleParamsGroup(
    style: ChatMessageBackgroundStyle,
    displaySetting: DisplaySetting,
    overrides: ChatBubbleStyleOverrides,
    advancedAppearance: AdvancedAppearanceSetting,
    onOverridesChange: (ChatBubbleStyleOverrides.() -> ChatBubbleStyleOverrides) -> Unit,
) {
    val resolved = resolveBubbleStyle(
        colorScheme = MaterialTheme.colorScheme,
        style = style,
        overrides = overrides,
    )
    val advancedOn = advancedAppearance.enableAdvancedAppearance
    val overridesBlur = overrides.blurSigma
        ?.takeIf { style == ChatMessageBackgroundStyle.FROSTED }
        ?.let { finiteAppearanceValue(it, 0f, 30f, 14f) }
    val resolvedForMaterial = resolved.takeIf { style != ChatMessageBackgroundStyle.DEFAULT }
    val actualMaterial = when {
        style == ChatMessageBackgroundStyle.SOLID -> null
        else -> advancedAppearance.messageBubbleMaterial(
            displaySetting, ChatBubbleStyleTarget.ASSISTANT, resolvedForMaterial, streaming = false,
        )
    }
    val inheritedRadius = when {
        actualMaterial != null -> actualMaterial.radius
        !advancedOn -> resolved.blurRadius.value
        else -> advancedAppearance.chatBubbleBlurRadius ?: when (advancedAppearance.chatBubbleStyle) {
            ChatBubbleStyle.FROSTED -> 18f
            ChatBubbleStyle.OUTLINED -> 0f
            ChatBubbleStyle.LIQUID_GLASS -> 24f
        }
    }
    val overrideOpacity = when (style) {
        ChatMessageBackgroundStyle.FROSTED -> overrides.frostedOpacity
        ChatMessageBackgroundStyle.SOLID -> overrides.solidOpacity
        ChatMessageBackgroundStyle.DEFAULT -> null
    }
    val effectiveOpacity = finiteAppearanceValue(
        overrideOpacity ?: actualMaterial?.opacity ?: resolved.background.alpha,
        0f,
        1f,
        if (style == ChatMessageBackgroundStyle.FROSTED) .66f else 1f,
    )
    val sigmaRangeMax = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) 8 else 26
    val effectiveRadius = overridesBlur?.let { it * 1.5f } ?: inheritedRadius
    val effectiveSigma = overridesBlur ?: (inheritedRadius / 1.5f)
    val blurSigma = effectiveSigma.roundToInt().coerceIn(0, sigmaRangeMax)
    val powerSave = LocalAdvancedAppearancePowerSave.current
    val busy = LocalAdvancedAppearanceBusy.current
    val standardRenderer = advancedAppearance.liquidGlass.renderer == me.rerere.rikkahub.data.model.LiquidGlassRenderer.STANDARD
    val actualSurfaceStyle = actualMaterial?.style
    val radiusApplicable = style == ChatMessageBackgroundStyle.FROSTED && actualSurfaceStyle != null &&
        actualSurfaceStyle.usesBlur &&
        !(actualSurfaceStyle == BackgroundSurfaceStyle.LIQUID_GLASS &&
            (advancedAppearance.liquidGlass.usesMaterial3Style() || standardRenderer))
    val blurLiveAllowed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !powerSave &&
        !(busy && advancedAppearance.liquidGlass.adaptivePerformance) && !LocalSeparateWindow.current &&
        (!advancedOn || advancedAppearance.enableBubblePerformanceEffects)
    val shownBlurRadius = resolvedLiveAppearanceRadius(
        effectiveRadius,
        Build.VERSION.SDK_INT,
        effects = blurLiveAllowed,
    )
    // Rendering gates affect whether the current frame can show blur, not whether the user
    // may edit and persist the per-role sigma.
    val blurEnabled = radiusApplicable

    fun update(transform: ChatBubbleStyleOverrides.() -> ChatBubbleStyleOverrides) {
        onOverridesChange(transform)
    }

    CardGroup(title = { Text(stringResource(R.string.message_style_page_role_assistant)) }) {
        if (radiusApplicable) {
            paramSliderRow(
                headlineContent = { Text(stringResource(R.string.message_style_page_blur)) },
                trailingContent = {
                    Text(stringResource(R.string.aa_bubble_blur_value, blurSigma, shownBlurRadius))
                },
                child = {
                    NumberSlider(
                        value = blurSigma,
                        onValueChange = { update { copy(blurSigma = it.toFloat()) } },
                        valueRange = 0..sigmaRangeMax,
                        enabled = blurEnabled,
                        preserveFractionalDrag = true,
                    )
                },
            )
            if (!blurLiveAllowed) {
                formItem {
                    Text(
                        text = stringResource(R.string.message_style_page_blur_hint),
                        color = LocalContentColor.current.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        paramSliderRow(
            headlineContent = { Text(stringResource(R.string.message_style_page_background_opacity)) },
            trailingContent = { CardGroupValue("${(effectiveOpacity * 100).roundToInt()}%") },
            child = {
                NumberSlider(
                    value = (effectiveOpacity * 100).roundToInt().coerceIn(0, 100),
                    onValueChange = {
                        update {
                            if (style == ChatMessageBackgroundStyle.FROSTED) {
                                copy(frostedOpacity = it / 100f)
                            } else {
                                copy(solidOpacity = it / 100f)
                            }
                        }
                    },
                    valueRange = 0..100,
                    preserveFractionalDrag = true,
                )
            },
        )
        paramSliderRow(
            headlineContent = { Text(stringResource(R.string.message_style_page_border_opacity)) },
            trailingContent = { CardGroupValue("${(resolved.border.alpha * 100).roundToInt()}%") },
            child = {
                NumberSlider(
                    value = (resolved.border.alpha * 100).roundToInt().coerceIn(0, 100),
                    onValueChange = { update { copy(borderOpacity = it / 100f) } },
                    valueRange = 0..100,
                    preserveFractionalDrag = true,
                )
            },
        )
        paramSliderRow(
            headlineContent = { Text(stringResource(R.string.message_style_page_border_width)) },
            trailingContent = { CardGroupValue(resolved.borderWidth.value.toString()) },
            child = {
                NumberSlider(
                    value = (resolved.borderWidth.value * 10).roundToInt().coerceIn(0, 30),
                    onValueChange = { update { copy(borderWidth = it / 10f) } },
                    valueRange = 0..30,
                    preserveFractionalDrag = true,
                )
            },
        )
        paramSliderRow(
            headlineContent = { Text(stringResource(R.string.message_style_page_corner_radius)) },
            trailingContent = { CardGroupValue(resolved.radius.value.roundToInt().toString()) },
            child = {
                NumberSlider(
                    value = resolved.radius.value.roundToInt().coerceIn(0, 28),
                    onValueChange = { update { copy(cornerRadius = it.toFloat()) } },
                    valueRange = 0..28,
                    preserveFractionalDrag = true,
                )
            },
        )
    }
}

/** 参数行：标题在左、取值在右，下面放滑杆。 */
private fun CardGroupScope.paramSliderRow(
    headlineContent: @Composable () -> Unit,
    trailingContent: @Composable () -> Unit,
    child: @Composable () -> Unit,
) = formItem(
    headlineContent = headlineContent,
    trailingContent = trailingContent,
) {
    child()
}
