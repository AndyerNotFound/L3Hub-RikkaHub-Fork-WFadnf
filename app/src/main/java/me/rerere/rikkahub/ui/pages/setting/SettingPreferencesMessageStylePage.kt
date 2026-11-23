package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import me.rerere.rikkahub.ui.style.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.BubbleChat
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.ChatBubbleStyleOverrides
import me.rerere.rikkahub.data.datastore.ChatBubbleStyleTarget
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.ChatMessageBackgroundStyle
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.effectiveBackgroundEffect
import me.rerere.rikkahub.data.datastore.overridesFor
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.CardGroupValue
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.NumberSlider
import me.rerere.rikkahub.ui.components.ui.SaltGlassStyles
import me.rerere.rikkahub.ui.components.ui.resolveBubbleStyle
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPadding
import me.rerere.ui.components.AnimatedAlertDialog
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

/** 消息样式设置页：气泡背景三档，按角色分别定制。 */
@Composable
fun SettingPreferencesMessageStylePage(
    embedded: Boolean = false,
    vm: SettingVM = koinViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    var editingTarget by remember { mutableStateOf(ChatBubbleStyleTarget.ASSISTANT) }
    var showResetDialog by remember { mutableStateOf(false) }

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings { it.copy(displaySetting = setting) }
    }

    val style = displaySetting.chatMessageBackgroundStyle
    val customizeCards = displaySetting.customizeThinkingToolCards
    val target = if (
        !customizeCards && editingTarget == ChatBubbleStyleTarget.THINKING_TOOL_CARD
    ) {
        ChatBubbleStyleTarget.ASSISTANT
    } else {
        editingTarget
    }
    val overrides = displaySetting.overridesFor(target)

    fun updateOverrides(value: ChatBubbleStyleOverrides) {
        updateDisplaySetting(
            displaySetting.copy(
                userChatBubbleStyleOverrides = if (target == ChatBubbleStyleTarget.USER) {
                    value
                } else {
                    displaySetting.userChatBubbleStyleOverrides
                },
                assistantChatBubbleStyleOverrides = if (target == ChatBubbleStyleTarget.ASSISTANT) {
                    value
                } else {
                    displaySetting.assistantChatBubbleStyleOverrides
                },
                thinkingToolCardStyleOverrides = if (target == ChatBubbleStyleTarget.THINKING_TOOL_CARD) {
                    value
                } else {
                    displaySetting.thinkingToolCardStyleOverrides
                },
            ),
        )
    }

    fun resetAll() {
        updateDisplaySetting(
            displaySetting.copy(
                chatMessageBackgroundStyle = ChatMessageBackgroundStyle.DEFAULT,
                userChatBubbleStyleOverrides = ChatBubbleStyleOverrides(),
                assistantChatBubbleStyleOverrides = ChatBubbleStyleOverrides(),
                thinkingToolCardStyleOverrides = ChatBubbleStyleOverrides(),
                customizeThinkingToolCards = false,
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.message_style_page_title)) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(
                            imageVector = HugeIcons.Refresh03,
                            contentDescription = stringResource(R.string.message_style_page_reset),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .adaptiveHorizontalPadding(0.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 样式选择
            item {
                CardGroup {
                    formItem(
                        headlineContent = { Text(text = stringResource(R.string.message_style_page_style_title)) },
                        supportingContent = { Text(stringResource(R.string.message_style_page_style_desc)) },
                    ) {
                        ConnectedChoiceButtons(
                            options = ChatMessageBackgroundStyle.entries,
                            selected = style,
                            onSelect = {
                                updateDisplaySetting(displaySetting.copy(chatMessageBackgroundStyle = it))
                            },
                        ) { item, selected ->
                            ConnectedChoiceLabel(
                                text = when (item) {
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
            }

            // 布局开关
            item {
                CardGroup {
                    switchItem(
                        checked = customizeCards,
                        onCheckedChange = {
                            updateDisplaySetting(displaySetting.copy(customizeThinkingToolCards = it))
                        },
                        leadingContent = { CardGroupIcon(HugeIcons.BubbleChat) },
                        supportingContent = { Text(stringResource(R.string.message_style_page_customize_thinking_desc)) },
                        headlineContent = { Text(stringResource(R.string.message_style_page_customize_thinking)) },
                    )
                }
            }

            if (style != ChatMessageBackgroundStyle.DEFAULT) {
                // 角色分段
                item {
                    CardGroup {
                        formItem(
                            headlineContent = { Text(text = stringResource(R.string.message_style_page_target_title)) },
                        ) {
                            ConnectedChoiceButtons(
                                options = if (customizeCards) {
                                    ChatBubbleStyleTarget.entries
                                } else {
                                    listOf(
                                        ChatBubbleStyleTarget.USER,
                                        ChatBubbleStyleTarget.ASSISTANT,
                                    )
                                },
                                selected = target,
                                onSelect = { editingTarget = it },
                            ) { item, selected ->
                                ConnectedChoiceLabel(
                                    text = when (item) {
                                        ChatBubbleStyleTarget.USER ->
                                            stringResource(R.string.message_style_page_role_user)
                                        ChatBubbleStyleTarget.ASSISTANT ->
                                            stringResource(R.string.message_style_page_role_assistant)
                                        ChatBubbleStyleTarget.THINKING_TOOL_CARD ->
                                            stringResource(R.string.message_style_page_role_thinking)
                                    },
                                    selected = selected,
                                )
                            }
                        }
                    }
                }

                // 预览
                item {
                    MessageStylePreview(
                        style = style,
                        editingTarget = target,
                        customizeCards = customizeCards,
                        displaySetting = displaySetting,
                    )
                }

                // 参数组
                item {
                    StyleParamsGroup(
                        style = style,
                        overrides = overrides,
                        onOverridesChange = ::updateOverrides,
                    )
                }
            } else {
                item {
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
            }
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
                                resetAll()
                            }
                            .padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            },
        )
    }
}

/** 预览面板：模拟聊天背景 + 用户/思考链/助手三个气泡，按当前编辑目标高亮。 */
@Composable
private fun MessageStylePreview(
    style: ChatMessageBackgroundStyle,
    editingTarget: ChatBubbleStyleTarget,
    customizeCards: Boolean,
    displaySetting: DisplaySetting,
) {
    val cs = MaterialTheme.colorScheme
    val isDark = LocalDarkMode.current
    val userResolved = resolveBubbleStyle(
        colorScheme = cs,
        style = style,
        overrides = displaySetting.userChatBubbleStyleOverrides,
    )
    val assistantResolved = resolveBubbleStyle(
        colorScheme = cs,
        style = style,
        overrides = displaySetting.assistantChatBubbleStyleOverrides,
    )
    val cardResolved = if (customizeCards) {
        resolveBubbleStyle(
            colorScheme = cs,
            style = style,
            overrides = displaySetting.thinkingToolCardStyleOverrides,
        )
    } else {
        assistantResolved
    }
    val dim = style != ChatMessageBackgroundStyle.DEFAULT
    val frosted = style == ChatMessageBackgroundStyle.FROSTED
    // 预览的毛玻璃源：背景（渐变 + 光斑）注册为 Haze 源，气泡用 hazeBlur 真实模糊它，
    // 与聊天界面的渲染路径一致（对齐 Kelivo 预览里的 ChatFrostedBackdrop）
    val previewHaze = rememberHazeState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, cs.outlineVariant.copy(alpha = if (isDark) 0.12f else 0.10f), RoundedCornerShape(16.dp)),
    ) {
        // 清晰的背景源：渐变 + 光斑，毛玻璃气泡透过它显示模糊
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(previewHaze)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            cs.surfaceContainerLow,
                            cs.surfaceContainerHigh,
                        ),
                    ),
                ),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 36.dp, y = 24.dp)
                    .size(56.dp)
                    .background(cs.primary.copy(alpha = 0.30f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = -24.dp, y = -16.dp)
                    .size(72.dp)
                    .background(cs.tertiary.copy(alpha = 0.22f), CircleShape),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            PreviewBubble(
                resolved = userResolved,
                text = stringResource(R.string.message_style_page_preview_user),
                modifier = Modifier.align(Alignment.End),
                dimmed = dim && editingTarget != ChatBubbleStyleTarget.USER,
                frosted = frosted,
                hazeState = previewHaze,
            )
            PreviewBubble(
                resolved = cardResolved,
                text = stringResource(R.string.message_style_page_preview_thinking),
                dimmed = dim &&
                    editingTarget != ChatBubbleStyleTarget.THINKING_TOOL_CARD &&
                    editingTarget != ChatBubbleStyleTarget.ASSISTANT,
                compact = true,
                frosted = frosted,
                hazeState = previewHaze,
            )
            PreviewBubble(
                resolved = assistantResolved,
                text = stringResource(R.string.message_style_page_preview_assistant),
                dimmed = dim && editingTarget != ChatBubbleStyleTarget.ASSISTANT,
                frosted = frosted,
                hazeState = previewHaze,
            )
        }
    }
}

/** 预览气泡：按解析结果画底色、边框、圆角与文字色。 */
@Composable
private fun PreviewBubble(
    resolved: me.rerere.rikkahub.ui.components.ui.ResolvedBubbleStyle,
    text: String,
    modifier: Modifier = Modifier,
    dimmed: Boolean,
    compact: Boolean = false,
    frosted: Boolean = false,
    hazeState: HazeState? = null,
) {
    val shape = RoundedCornerShape(resolved.radius)
    val base = modifier
        .widthIn(max = 240.dp)
        .height(if (compact) 34.dp else 44.dp)
        .alpha(if (dimmed) 0.45f else 1f)
        .clip(shape)
    // 毛玻璃档走真实 haze：先模糊预览背景源，再叠半透明底色与边框，
    // 与聊天界面的 effectSurface 路径一致
    val effect = if (frosted && hazeState != null) {
        base.hazeBlur(
            input = HazeInput.Sources(hazeState),
            style = SaltGlassStyles.blur(
                isDarkTheme = LocalDarkMode.current,
                radius = resolved.blurRadius,
            ),
        )
    } else {
        base
    }
    Box(
        modifier = effect
            .background(resolved.background)
            .border(resolved.borderWidth, resolved.border, shape)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            color = resolved.text,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 参数组：模糊、透明度、边框、圆角。 */
@Composable
private fun StyleParamsGroup(
    style: ChatMessageBackgroundStyle,
    overrides: ChatBubbleStyleOverrides,
    onOverridesChange: (ChatBubbleStyleOverrides) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val resolved = resolveBubbleStyle(
        colorScheme = cs,
        style = style,
        overrides = overrides,
    )
    val opacity = when (style) {
        ChatMessageBackgroundStyle.FROSTED -> overrides.frostedOpacity ?: 0.66f
        else -> overrides.solidOpacity ?: 1.0f
    }

    fun update(transform: ChatBubbleStyleOverrides.() -> ChatBubbleStyleOverrides) {
        onOverridesChange(overrides.transform())
    }

    val blurSigma = (overrides.blurSigma ?: 14f).roundToInt().coerceIn(0, 30)
    // 模糊半径只在全局特效 BLUR 档下生效：玻璃档走光学折射，关闭档没有模糊
    // 组合读取放在 CardGroup 外面：DSL 体不是组合作用域
    val blurEnabled =
        LocalSettings.current.displaySetting.effectiveBackgroundEffect ==
            BackgroundEffectType.BLUR

    CardGroup {
        if (style == ChatMessageBackgroundStyle.FROSTED) {
            paramSliderRow(
                label = stringResource(R.string.message_style_page_blur),
                valueText = blurSigma.toString(),
                child = {
                    NumberSlider(
                        value = blurSigma,
                        onValueChange = { update { copy(blurSigma = it.toFloat()) } },
                        valueRange = 0..30,
                        enabled = blurEnabled,
                    )
                },
            )
            if (!blurEnabled) {
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
            label = stringResource(R.string.message_style_page_background_opacity),
            valueText = "${(opacity * 100).roundToInt()}%",
            child = {
                NumberSlider(
                    value = (opacity * 100).roundToInt().coerceIn(0, 100),
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
                )
            },
        )
        paramSliderRow(
            label = stringResource(R.string.message_style_page_border_opacity),
            valueText = "${(resolved.border.alpha * 100).roundToInt()}%",
            child = {
                NumberSlider(
                    value = (resolved.border.alpha * 100).roundToInt().coerceIn(0, 100),
                    onValueChange = { update { copy(borderOpacity = it / 100f) } },
                    valueRange = 0..100,
                )
            },
        )
        paramSliderRow(
            label = stringResource(R.string.message_style_page_border_width),
            valueText = resolved.borderWidth.value.toString(),
            child = {
                NumberSlider(
                    value = (resolved.borderWidth.value * 10).roundToInt().coerceIn(0, 30),
                    onValueChange = { update { copy(borderWidth = it / 10f) } },
                    valueRange = 0..30,
                )
            },
        )
        paramSliderRow(
            label = stringResource(R.string.message_style_page_corner_radius),
            valueText = resolved.radius.value.roundToInt().toString(),
            child = {
                NumberSlider(
                    value = resolved.radius.value.roundToInt().coerceIn(0, 28),
                    onValueChange = { update { copy(cornerRadius = it.toFloat()) } },
                    valueRange = 0..28,
                )
            },
        )
    }
}

/** 参数行：标题在左、取值在右，下面放滑杆。 */
@Composable
private fun CardGroupScope.paramSliderRow(
    label: String,
    valueText: String,
    child: @Composable () -> Unit,
) = formItem(
    headlineContent = { Text(text = label) },
    trailingContent = { CardGroupValue(valueText) },
) {
    child()
}
