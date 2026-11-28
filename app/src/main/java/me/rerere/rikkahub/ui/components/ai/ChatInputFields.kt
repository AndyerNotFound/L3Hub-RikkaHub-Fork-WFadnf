package me.rerere.rikkahub.ui.components.ai

import android.graphics.Rect

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Attachment01
import me.rerere.hugeicons.stroke.Fullscreen
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.pages.chat.rememberSelectMenuAnchor
import me.rerere.rikkahub.ui.pages.chat.rememberNativeSelectMenu
import me.rerere.rikkahub.data.datastore.getQuickMessagesOfAssistant
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionContext
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionItem
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionList
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionProvider
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.style.IconButton
import me.rerere.rikkahub.ui.style.TextButton
import me.rerere.rikkahub.ui.theme.LocalFullWidthComponents
import me.rerere.rikkahub.ui.theme.adaptiveShape
import me.rerere.rikkahub.ui.theme.rememberEffectsSpec
import me.rerere.ui.components.AnimatedDialog
import org.koin.compose.koinInject

internal data class SingleLineExpansion(
    val progress: State<Float>,
    val collapsedStart: Dp,
    val collapsedEnd: Dp,
    val bottom: Dp,
)

@Composable
internal fun TextInputRow(
    state: ChatInputState,
    assistant: Assistant,
    completionProviders: List<ChatCompletionProvider>,
    onSendMessage: () -> Unit,
    modifier: Modifier = Modifier,
    showQuickMessageButton: Boolean = true,
    fullscreenOnOverflow: Boolean = false,
    guideMessages: List<QuickMessage> = emptyList(),
    onAddGuidance: (QuickMessage) -> Unit = {},
    onSendAsGuidance: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    // 单行布局的内边距随展开进度逐帧变化。把进度放在这里读，
    // 重组范围只到输入框，不会把整个 ChatInput（清单/悬浮栏/按钮行）每帧重组
    singleLineExpansion: SingleLineExpansion? = null,
    placeholder: (@Composable () -> Unit)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    onMeasure: ((fieldHeight: Int, oneLineHeight: Int) -> Unit)? = null,
) {
    val settings = LocalSettings.current
    val fullWidth = LocalFullWidthComponents.current
    val filesManager: FilesManager = koinInject()
    val quickMessages = remember(settings.quickMessages, assistant.quickMessageIds) {
        settings.getQuickMessagesOfAssistant(assistant)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        var isFocused by remember { mutableStateOf(false) }
        var isFullScreen by remember { mutableStateOf(false) }
        var fieldHeight by remember { mutableIntStateOf(0) }
        var oneLineHeight by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        val bodyLineHeight = MaterialTheme.typography.bodyLarge.lineHeight
        val lineHeightPx = with(density) {
            try {
                bodyLineHeight.toPx()
            } catch (_: Exception) {
                24.dp.toPx()
            }
        }
        val inputOverflowed = oneLineHeight > 0 && fieldHeight >= oneLineHeight + lineHeightPx * 3.5f
        val receiveContentListener = remember(
            settings.displaySetting.pasteLongTextAsFile, settings.displaySetting.pasteLongTextThreshold
        ) {
            ReceiveContentListener { transferableContent ->
                when {
                    transferableContent.hasMediaType(MediaType.Image) -> {
                        transferableContent.consume { item ->
                            val uri = item.uri
                            if (uri != null) {
                                state.addImages(
                                    filesManager.createChatFilesByContents(
                                        listOf(uri)
                                    )
                                )
                            }
                            uri != null
                        }
                    }

                    settings.displaySetting.pasteLongTextAsFile && transferableContent.hasMediaType(MediaType.Text) -> {
                        transferableContent.consume { item ->
                            val text = item.text?.toString()
                            if (text != null && text.length > settings.displaySetting.pasteLongTextThreshold) {
                                val document = filesManager.createChatTextFile(text)
                                state.addFiles(listOf(document))
                                true
                            } else {
                                false
                            }
                        }
                    }

                    else -> transferableContent
                }
            }
        }

        // 补全收集与应用走共享实现：原生输入卡（NativeChatComposerHost）用的是同一份，
        // 只是「有焦点」这个信号来自 native 的 EditText 而不是 Compose 的 TextField。
        val completions = rememberChatCompletions(
            state = state,
            completionProviders = completionProviders,
            enabled = isFocused,
        )

        completions.visible?.let { list ->
            CompletionPopup(
                completionList = list,
                onItemClick = { item -> completions.apply(state, item) },
            )
        }

        val fieldContentPadding = singleLineExpansion?.let { expansion ->
            val progress = expansion.progress.value
            PaddingValues(
                start = lerp(expansion.collapsedStart, 4.dp, progress),
                end = lerp(expansion.collapsedEnd, 4.dp, progress),
                bottom = expansion.bottom * progress,
            )
        } ?: contentPadding
        val maxFieldHeight = with(density) { (lineHeightPx * 5f).toDp() } + 24.dp
        val defaultPlaceholder: @Composable () -> Unit = {
            Text(
                text = stringResource(R.string.chat_input_placeholder),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextField(
            state = state.textContent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(fieldContentPadding)
                .heightIn(max = maxFieldHeight)
                .testTag("chat_input")
                .onSizeChanged { size ->
                    fieldHeight = size.height
                    // 展开动画进行中不量单行高度：这段里量到的是「一行 + 当时的取整下边距」，
                    // 高度和下边距各自取整，差 1px 就会回喂给 expansionHeight，卡片、槽位和
                    // 按钮行的下边距于是逐帧抖一档。只在动画停到两端时量：那时下边距不是 0
                    // 就是满档，扣掉它得到的才是真正的一行高度
                    val expandProgressValue = singleLineExpansion?.progress?.value ?: 1f
                    val expansionAtRest = expandProgressValue <= 0f || expandProgressValue >= 1f
                    if (state.textContent.text.isEmpty() && expansionAtRest) {
                        val expansionBottomPx = singleLineExpansion?.let { expansion ->
                            with(density) {
                                (expansion.bottom * expandProgressValue).roundToPx()
                            }
                        } ?: 0
                        oneLineHeight = (size.height - expansionBottomPx).coerceAtLeast(0)
                    }
                    onMeasure?.invoke(fieldHeight, oneLineHeight)
                }
                .contentReceiver(receiveContentListener)
                .onFocusChanged {
                    isFocused = it.isFocused
                    onFocusChange?.invoke(it.isFocused)
                },
            shape = if (fullWidth) {
                RoundedCornerShape(0.dp)
            } else {
                MaterialTheme.shapes.largeIncreased
            },
            placeholder = placeholder ?: defaultPlaceholder,
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (settings.displaySetting.sendOnEnter) ImeAction.Send else ImeAction.Default
            ),
            onKeyboardAction = {
                if (settings.displaySetting.sendOnEnter && !state.isEmpty()) {
                    onSendMessage()
                }
            },
            colors = TextFieldDefaults.colors().copy(
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // 只要有内容就给全屏按钮，不必等超出三行
                    if (state.textContent.text.isNotEmpty() ||
                        (if (fullscreenOnOverflow) inputOverflowed else isFocused)
                    ) {
                        IconButton(
                            onClick = {
                                isFullScreen = !isFullScreen
                            }) {
                            Icon(HugeIcons.Fullscreen, null)
                        }
                    }
                }
            },
            leadingIcon = if (showQuickMessageButton) {
                {
                    QuickMessageButton(
                        quickMessages = quickMessages,
                        guideMessages = guideMessages,
                        state = state,
                        onAddGuidance = onAddGuidance,
                        onSendAsGuidance = onSendAsGuidance,
                    )
                }
            } else null,
        )
        if (isFullScreen) {
            FullScreenEditorDialog(
                textState = state.textContent,
                placeholder = stringResource(R.string.chat_input_placeholder),
                onDone = { isFullScreen = false },
            )
        }
    }
}

/**
 * 补全会话的持有者：collector 的输出、应用、关闭都在这里。
 *
 * 抽出来的原因是原生输入卡（NativeChatComposerHost）也要同一份补全：
 * 输入来源换成 Java 的 EditText 之后，[ChatInputState.textContent] 仍然是唯一真相，
 * 所以 context 的取样、多 provider 的合并规则、`state.applyCompletion` 的落点都不必分叉。
 */
internal class ChatCompletionController {
    // 只在这里写：对外只暴露 visible / apply / dismiss，避免两处各自维护一份「当前列表」
    private val currentState = mutableStateOf<ChatCompletionList?>(null)

    /** provider 返回空列表等同于没有补全：不弹一个空面板。 */
    val visible: ChatCompletionList?
        get() = currentState.value?.takeIf { it.items.isNotEmpty() }

    internal fun publish(list: ChatCompletionList?) {
        currentState.value = list
    }

    fun apply(chatInput: ChatInputState, item: ChatCompletionItem) {
        val list = currentState.value ?: return
        chatInput.applyCompletion(list.replacementRange, item)
        currentState.value = null
    }

    fun dismiss() {
        currentState.value = null
    }
}

/**
 * 补全收集器。[enabled] 为 false（输入框没焦点、原生卡没焦点）时立刻清空并停掉采样，
 * 避免面板留在一个已经没人打字的输入框旁边。
 */
@Composable
internal fun rememberChatCompletions(
    state: ChatInputState,
    completionProviders: List<ChatCompletionProvider>,
    enabled: Boolean,
): ChatCompletionController {
    val controller = remember(state) { ChatCompletionController() }
    LaunchedEffect(controller, completionProviders, enabled) {
        if (!enabled || completionProviders.isEmpty()) {
            controller.dismiss()
            return@LaunchedEffect
        }
        snapshotFlow {
            ChatCompletionContext(
                text = state.textContent.text.toString(),
                selection = state.textContent.selection,
            )
        }.collectLatest { context ->
            val lists = completionProviders.mapNotNull { provider ->
                try {
                    provider.complete(context)
                        ?.takeIf { it.items.isNotEmpty() }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
            val primary = lists.firstOrNull()
            controller.publish(primary?.let { list ->
                val mergedItems = lists
                    .filter { it.replacementRange == list.replacementRange }
                    .flatMap { it.items }
                    .distinctBy { it.label to it.insertText }
                    .sortedWith(
                        compareByDescending<ChatCompletionItem> { it.sortScore }
                            .thenBy { it.label.length }
                            .thenBy { it.label.lowercase() }
                    )
                    .take(8)
                list.copy(items = mergedItems)
            })
        }
    }
    return controller
}

@Composable
internal fun CompletionPopup(
    completionList: ChatCompletionList,
    onItemClick: (ChatCompletionItem) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        shape = adaptiveShape(18.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            items(
                items = completionList.items,
                key = { item -> "${item.label}:${item.insertText}" },
            ) { item ->
                Surface(
                    onClick = { onItemClick(item) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item.icon?.let { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.detail?.let { detail ->
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 把补全项落进 [ChatInputState]。原生输入卡点选项后也走这里（`state.applyCompletion`），
 * 两条输入路径不会各自写一套 replace + 选区逻辑。
 */
internal fun ChatInputState.applyCompletion(
    replacementRange: TextRange,
    item: ChatCompletionItem,
) {
    val textLength = textContent.text.length
    val start = replacementRange.min.coerceIn(0, textLength)
    val end = replacementRange.max.coerceIn(start, textLength)
    textContent.edit {
        replace(start, end, item.insertText)
        selection = TextRange(start + item.insertText.length)
    }
}

@Composable
internal fun QuickMessageButton(
    quickMessages: List<QuickMessage>,
    guideMessages: List<QuickMessage>,
    state: ChatInputState,
    onAddGuidance: (QuickMessage) -> Unit,
    onSendAsGuidance: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val anchor = rememberSelectMenuAnchor()
    Box {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .testTag("chat_quick_message_button")
                .then(anchor.modifier)
                .combinedClickable(
                    onClick = { expanded = !expanded },
                    // 长按：输入框里的内容不进这一轮的消息，直接作为引导插进正在跑的工具间隙。
                    // 输入框是空的时候退回成展开选单，别让长按变成什么都没发生
                    onLongClick = {
                        // 判的是文字而不是 isEmpty：只有附件时引导没有内容可取，退回成展开选单
                        if (state.textContent.text.isBlank()) {
                            expanded = !expanded
                        } else {
                            expanded = false
                            onSendAsGuidance()
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(HugeIcons.Zap, null, modifier = Modifier.size(18.dp))
        }
        QuickMessageMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            quickMessages = quickMessages,
            guideMessages = guideMessages,
            state = state,
            onAddGuidance = onAddGuidance,
            anchorBounds = anchor.value.value,
        )
    }
}

/**
 * 快捷消息下拉菜单。
 *
 * 从 [QuickMessageButton] 里抽出来，是因为原生输入卡（NativeChatComposerHost）的快捷按钮
 * 也要开同一张菜单：原生侧只负责把「打开」抛回来，菜单本身仍是这一份 Compose 实现，
 * 短按追加、长按替换、引导提示词点选这些行为不会因为换输入框而分叉。
 */
@Composable
internal fun QuickMessageMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    quickMessages: List<QuickMessage>,
    guideMessages: List<QuickMessage>,
    state: ChatInputState,
    onAddGuidance: (QuickMessage) -> Unit,
    anchorBounds: Rect? = null,
) {
    val hapticFeedback = LocalHapticFeedback.current

    // 文案在组合里读好：content lambda 在 LaunchedEffect 里跑，没有 Composition
    val emptyLabel = stringResource(R.string.quick_messages_page_empty_title)
    val guideSectionLabel = stringResource(R.string.quick_messages_guide_section)

    rememberNativeSelectMenu(
        expanded = expanded,
        onDismiss = onDismissRequest,
        anchorBounds = anchorBounds,
        icons = List(quickMessages.size + guideMessages.size) { null },
    ) {
        if (quickMessages.isEmpty() && guideMessages.isEmpty()) {
            // 空状态：一条不可点的说明
            addItem(emptyLabel, null, false, false, false) {}
        }
        quickMessages.forEach { quickMessage ->
            // 短按追加到输入框末尾，长按替换输入框内容 —— 双行项 + 长按
            addItem(
                quickMessage.title,
                quickMessage.content,
                null,
                /* selected = */ false,
                /* enabled = */ true,
                /* destructive = */ false,
                /* action = */ {
                    state.appendText(quickMessage.content)
                },
                /* longAction = */ {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.setMessageText(quickMessage.content)
                },
            )
        }
        // 引导提示词单独一区：点选后不进输入框，直接加进本轮的工具间隙
        if (guideMessages.isNotEmpty()) {
            startGroup()
            addHeader(guideSectionLabel)
            guideMessages.forEach { guide ->
                // 八参重载：末位是长按动作，这里只接短按，故显式传 null
                addItem(guide.title, guide.content, null, false, true, false, { onAddGuidance(guide) }, null)
            }
        }
    }
}

/**
 * 全屏输入编辑器的内容层：铺满、避让系统栏与输入法、顶上一个「完成」、右下角附件 FAB。
 *
 * 宿主由调用方挑：
 * - 页面层（ChatPage）直接调它，挂在 Scaffold 外面，能连顶栏和底栏一起盖住；
 * - 底栏里的入口（单行输入框、原生卡）用 [FullScreenEditorDialog]，
 *   底栏会把内容框在它自己的高度里，必须换一个窗口才铺得开。
 *
 * 两处共用这一份，编辑器不复制第二份。
 */
@Composable
internal fun FullScreenEditor(
    textState: TextFieldState,
    placeholder: String,
    onDone: () -> Unit,
    onAttach: (() -> Unit)? = null,
    // 对话框宿主用：那边 safeDrawing 只算系统栏，输入法要单独让一次；
    // 页面层的 safeDrawing 已经把输入法算进去了，再让一次会多出一条空白
    avoidIme: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // 自己吃掉返回键：全屏编辑时返回应该退出编辑，而不是退出页面。
    // Dialog 宿主那边窗口也会接一次，两边都指向 onDone，谁先接都一样。
    BackHandler(enabled = true) { onDone() }

    // 点进来就聚焦并弹键盘。入口那边点全屏时先把原生卡的焦点收了（否则两条输入法连接
    // 会互相踢），所以这里不主动要一次，覆盖层就是空的、键盘也不出来。
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        // 等一帧：requestFocus 要求节点已经挂上，组合刚跑完时布局还没落
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        shape = if (LocalFullWidthComponents.current) {
            RoundedCornerShape(0.dp)
        } else {
            RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        // 面板本身铺满（贴边才像一整屏），避让只加在内容上
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .then(if (avoidIme) Modifier.imePadding() else Modifier),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                // 顶栏：只有一个「完成」，退出编辑
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDone) {
                        Text(stringResource(R.string.chat_page_save))
                    }
                }
                TextField(
                    state = textState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    shape = adaptiveShape(16.dp),
                    placeholder = { Text(placeholder) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    colors = TextFieldDefaults.colors().copy(
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                )
            }

            // 附件 FAB：有内容才出现，用 Fade transition 显隐。
            // 空输入时没有东西可附带，露出来只是挡视线。
            AnimatedVisibility(
                visible = !textState.text.isBlank() && onAttach != null,
                enter = fadeIn(animationSpec = rememberEffectsSpec()),
                exit = fadeOut(animationSpec = rememberEffectsSpec()),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            ) {
                FloatingActionButton(
                    onClick = { onAttach?.invoke() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        imageVector = HugeIcons.Attachment01,
                        contentDescription = stringResource(R.string.chat_input_add_attachment),
                    )
                }
            }
        }
    }
}

/**
 * 对话框宿主：给挂在底栏里的入口用。
 *
 * 底栏（Scaffold 的 bottomBar）会把内容框在它自己的高度里，同窗口覆盖层在那儿
 * 铺不开，只能借一个窗口。窗口自己带返回键处理，不用再挂 BackHandler。
 *
 * 用 [AnimatedDialog] 而不是 BasicAlertDialog：后者会给内容套一层
 * `sizeIn(maxWidth = 560dp)`，整屏编辑器会被压成一条窄柱。
 */
@Composable
internal fun FullScreenEditorDialog(
    textState: TextFieldState,
    placeholder: String,
    onDone: () -> Unit,
    onAttach: (() -> Unit)? = null,
) {
    AnimatedDialog(
        onDismissRequest = onDone,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        FullScreenEditor(
            textState = textState,
            placeholder = placeholder,
            onDone = onDone,
            onAttach = onAttach,
            avoidIme = true,
        )
    }
}
