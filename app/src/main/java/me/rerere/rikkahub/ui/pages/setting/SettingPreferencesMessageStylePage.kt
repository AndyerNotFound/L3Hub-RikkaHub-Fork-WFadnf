package me.rerere.rikkahub.ui.pages.setting

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
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.BubbleChat
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.applyDisplaySettingEdit
import me.rerere.rikkahub.data.datastore.finiteAppearanceValue
import me.rerere.rikkahub.data.datastore.ChatBubbleStyleOverrides
import me.rerere.rikkahub.data.datastore.ChatMessageBackgroundStyle
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.CardGroupValue
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.NumberSlider
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.components.ui.resolveBubbleStyle
import me.rerere.rikkahub.ui.style.TopAppBar
import me.rerere.rikkahub.ui.navigation.appearancePageContainerColor
import me.rerere.rikkahub.ui.components.nav.BackButton
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
                .adaptiveHorizontalPadding(0.dp),
            contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                MessageBubbleStyleSettingsSection(
                    displaySetting = settings.displaySetting,
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
 * 直接以 Column + CardGroup 输出，不带 Scaffold，也不自建 LazyColumn。
 * DisplaySetting 由调用方持有；这里只把差量结果回传，不在本地保存整份快照。
 */
@Composable
internal fun MessageBubbleStyleSettingsSection(
    displaySetting: DisplaySetting,
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
                overrides = overrides,
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

/** 参数只反映消息样式本身：气泡不采样背景，模糊相关的取值不再暴露给用户。 */
@Composable
private fun StyleParamsGroup(
    style: ChatMessageBackgroundStyle,
    overrides: ChatBubbleStyleOverrides,
    onOverridesChange: (ChatBubbleStyleOverrides.() -> ChatBubbleStyleOverrides) -> Unit,
) {
    val resolved = resolveBubbleStyle(
        colorScheme = MaterialTheme.colorScheme,
        style = style,
        overrides = overrides,
    )
    val overrideOpacity = when (style) {
        ChatMessageBackgroundStyle.FROSTED -> overrides.frostedOpacity
        ChatMessageBackgroundStyle.SOLID -> overrides.solidOpacity
        ChatMessageBackgroundStyle.DEFAULT -> null
    }
    val effectiveOpacity = finiteAppearanceValue(
        overrideOpacity ?: resolved.background.alpha,
        0f,
        1f,
        if (style == ChatMessageBackgroundStyle.FROSTED) .66f else 1f,
    )

    fun update(transform: ChatBubbleStyleOverrides.() -> ChatBubbleStyleOverrides) {
        onOverridesChange(transform)
    }

    CardGroup(title = { Text(stringResource(R.string.message_style_page_role_assistant)) }) {
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
