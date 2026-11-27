/*
 * Compose bridge for the native chat input card (NativeChatComposer / l3_native_chat_composer.xml).
 * The card is ported from the "Neko chat demo" sample (MIT, Copyright (c) 2026
 * 潇桦桉・洛｜WetherFlar); the full permission notice lives in the project's license appendix.
 */
package me.rerere.rikkahub.ui.components.ai

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.distinctUntilChanged
import me.rerere.ai.provider.Model
import me.rerere.asr.ASRState
import me.rerere.asr.ASRStatus
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionProvider
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.theme.LocalAnimationsEnabled
import me.rerere.rikkahub.ui.views.chat.NativeChatComposer as NativeChatComposerView
import me.rerere.rikkahub.ui.views.chat.NativeChatComposer.Listener as NativeChatComposerListener
import me.rerere.rikkahub.ui.views.chat.nativeChatTheme
import me.rerere.rikkahub.ui.views.chat.rememberNativeChatContext
import me.rerere.ui.toast.ToastType
import org.koin.compose.koinInject

/**
 * 原生输入卡的 Compose 宿主：把 Java/XML 的 [NativeChatComposerView] 挂进 ChatInput，
 * 并在它和 [ChatInputState] 之间做双向同步。
 *
 * 约定：
 * - [ChatInputState.textContent]（TextFieldState）仍然是唯一真相。原生侧只上报
 *   「文字 + 选区」，这里再写回 state；state 变化后再推回原生侧，两边都不自己留副本。
 * - 推回原生侧时文字相同就只补选区，不 setText：那是光标被重置、输入法 composing 被打断的来源。
 * - 所有下发都走 [AndroidView] 的 `update`：factory 只负责造视图，listener / 主题 / 状态
 *   每次重组都重推一遍。之前把它们留在 factory 里，切换主题或重组之后视图拿到的是第一次那份。
 * - `key(themedContext)`：配置、深色模式、density/fontScale 变化时 themed context 会换实例，
 *   视图跟着重建（旧实例的 tint、sp 尺寸都对不上了）；没变化时不重建，输入法的 composing
 *   和光标都不受扰动。
 * - `onRelease` 里 [NativeChatComposerView.release] 掉 listener 与输入法连接：视图被换掉之后
 *   不会再有回调打到已经过期的闭包上。
 *
 * 刻意留在 Compose、没有搬进原生卡的能力：
 * - 全屏编辑仍用共享的 [FullScreenEditor]（Compose），原生卡只负责「打开」。
 * - 快捷消息菜单、模型/推理面板、附件面板、消息队列、todos、语音模式行、悬浮栏全部保持 Compose。
 * - 内联补全面板挂在卡片外面（见 [CompletionPopup]），采集逻辑与 Compose 输入框是同一份
 *   [rememberChatCompletions]，点项也还是 `state.applyCompletion`，原生模式不会静默丢补全。
 */
@Composable
internal fun NativeChatComposerHost(
    state: ChatInputState,
    chatModel: Model?,
    loading: Boolean,
    // 内联 workspace 补全：与 TextInputRow 共用同一份 provider 列表与弹窗实现
    completionProviders: List<ChatCompletionProvider>,
    asrState: ASRState,
    // 语音模式（VoicePhase != Off）开着的时候原生麦克风要藏起来：
    // 两条录音通路同时存在会各自抢麦克风
    voiceActive: Boolean,
    onSend: () -> Unit,
    onSendLong: () -> Unit,
    onAttachClick: () -> Unit,
    onQuickClick: () -> Unit,
    onQuickLongClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    onModelClick: () -> Unit,
    onAsrClick: () -> Unit,
    onInterjectClick: () -> Unit,
    onEditChipClose: () -> Unit,
    // 原生卡里没有 EditingBanner，思考内容的编辑入口挂在「正在编辑」chip 上
    onEditReasoningClick: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = LocalSettings.current
    val themedContext = rememberNativeChatContext()
    val theme = nativeChatTheme()
    val animationsEnabled = LocalAnimationsEnabled.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val filesManager: FilesManager = koinInject()
    val toaster = LocalToaster.current
    // 视图实例只用来在有焦点时收键盘；它不是 Compose state，写它不会触发重组
    val viewRef = remember { NativeComposerRef() }
    var fieldFocused by remember { mutableStateOf(false) }
    val editChipLabel = stringResource(R.string.l3_native_composer_edit_chip)
    val editReasoningChipLabel = stringResource(R.string.l3_native_composer_edit_chip_reasoning)
    val modelFallbackLabel = stringResource(R.string.l3_native_composer_model_default)
    // 下面两个只在 listener 里用：listener 是 remember(state) 的，locale 变化不会重建它，
    // 所以文案和 toast 渠道都过一遍 rememberUpdatedState，避免拿到旧语言的那一份
    val pasteFailedNotice = rememberUpdatedState(
        PasteFailureNotice(
            message = stringResource(R.string.l3_native_composer_paste_failed),
            show = { message: String -> toaster.show(message = message, type = ToastType.Error) },
        )
    )

    // 回调每次重组都是新的闭包，捕获的 loading/state 会过期；包一层 rememberUpdatedState，
    // 原生视图永远调最新的那一份，避免「按了停止却按上一帧的 loading 走发送」。
    val latestActions = rememberUpdatedState(
        NativeComposerActions(
            onSend = onSend,
            onSendLong = onSendLong,
            onAttachClick = onAttachClick,
            onQuickClick = onQuickClick,
            onQuickLongClick = onQuickLongClick,
            onFullscreenClick = onFullscreenClick,
            onModelClick = onModelClick,
            onAsrClick = onAsrClick,
            onInterjectClick = onInterjectClick,
            onEditChipClose = onEditChipClose,
            onEditReasoningClick = onEditReasoningClick,
            onFocusChange = onFocusChange,
            sendOnEnter = settings.displaySetting.sendOnEnter,
        )
    )

    // Compose's FocusManager does not own the EditText focus. Only react to an actual
    // visible -> hidden IME transition, not the initial hidden frame (hardware keyboards).
    LaunchedEffect(imeInsets, density) {
        var wasVisible = false
        snapshotFlow { imeInsets.getBottom(density) > 0 }
            .distinctUntilChanged()
            .collect { visible ->
                if (wasVisible && !visible) {
                    viewRef.view?.clearInputFocus()
                    fieldFocused = false
                    latestActions.value.onFocusChange(false)
                }
                wasVisible = visible
            }
    }

    val listener = remember(state) {
        object : NativeChatComposerListener {
            override fun onComposerTextChanged(text: String, selectionStart: Int, selectionEnd: Int) {
                applyNativeTextEdit(state, text, selectionStart, selectionEnd)
            }

            override fun onComposerSelectionChanged(selectionStart: Int, selectionEnd: Int) {
                applyNativeSelectionEdit(state, selectionStart, selectionEnd)
            }

            override fun onSendClick() {
                viewRef.view?.clearInputFocus()
                latestActions.value.onSend()
            }

            override fun onSendLongClick() {
                viewRef.view?.clearInputFocus()
                latestActions.value.onSendLong()
            }

            override fun onAttachClick() {
                latestActions.value.onAttachClick()
            }

            override fun onQuickClick() {
                latestActions.value.onQuickClick()
            }

            override fun onQuickLongClick() {
                latestActions.value.onQuickLongClick()
            }

            override fun onFullscreenClick() {
                viewRef.view?.clearInputFocus()
                latestActions.value.onFullscreenClick()
            }

            override fun onModelClick() {
                viewRef.view?.clearInputFocus()
                latestActions.value.onModelClick()
            }

            override fun onAsrClick() {
                latestActions.value.onAsrClick()
            }

            override fun onInterjectClick() {
                latestActions.value.onInterjectClick()
            }

            override fun onEditChipClose() {
                latestActions.value.onEditChipClose()
            }

            override fun onEditChipClick() {
                // chip 只有在思考可编辑时才可点（Java 侧 setClickable）
                viewRef.view?.clearInputFocus()
                latestActions.value.onEditReasoningClick()
            }

            override fun onInputFocusChange(focused: Boolean) {
                fieldFocused = focused
                latestActions.value.onFocusChange(focused)
            }

            override fun onEditorSubmit() {
                val actions = latestActions.value
                if (!actions.sendOnEnter || state.isEmpty()) return
                viewRef.view?.clearInputFocus()
                actions.onSend()
            }

            // 粘贴 / 拖拽 / 输入法贴图都汇到这两个出口。URI 一律先复制进受管目录
            // （filesManager.createChatFilesByContents），输入框里只留 file:// 的受管副本，
            // 不会把 content:// 临时授权带进消息里。异常不往上抛：文件读写失败只提示一次，
            // 不能让 UI 线程崩在 listener 里。
            override fun onPasteImages(uris: List<Uri>) {
                if (uris.isEmpty()) return
                val created = runCatching { filesManager.createChatFilesByContents(uris) }.getOrNull()
                if (created == null || created.size != uris.size) {
                    val notice = pasteFailedNotice.value
                    notice.show(notice.message)
                }
                if (!created.isNullOrEmpty()) state.addImages(created)
            }

            override fun onPasteLongText(text: String) {
                if (text.isEmpty()) return
                val document = runCatching { filesManager.createChatTextFile(text) }.getOrNull()
                if (document == null) {
                    val notice = pasteFailedNotice.value
                    notice.show(notice.message)
                    return
                }
                state.addFiles(listOf(document))
            }
        }
    }

    // 原生输入框有焦点时才采补全：和 Compose 侧 isFocused 的判断同义
    val completions = rememberChatCompletions(
        state = state,
        completionProviders = completionProviders,
        enabled = fieldFocused,
    )

    // 这些值必须在组合里读：AndroidView 的 update 在 apply 阶段跑，那里读 snapshot state
    // 不会订阅重组，文字变了也不会再推一次。
    val text = state.textContent.text
    val selection = state.textContent.selection
    val empty = state.isEmpty()
    val reasoningEditable = state.editingReasoning != null
    val snapshot = NativeComposerSnapshot(
        text = text.toString(),
        selectionStart = selection.start,
        selectionEnd = selection.end,
        showStop = loading && empty,
        sendEnabled = (loading && empty) || !empty,
        editing = state.isEditing(),
        reasoningEditable = reasoningEditable,
        modelLabel = chatModel?.displayName ?: modelFallbackLabel,
        interjectVisible = loading && settings.enableMessageQueue && !empty,
        asrVisible = !voiceActive && (asrState.isAvailable || asrState.isRecording),
        asrRecording = asrState.isRecording,
        // connecting/stopping 里再按一次只会把同一次录音开第二遍，禁用掉
        asrEnabled = asrState.status != ASRStatus.Connecting && asrState.status != ASRStatus.Stopping,
        fullscreenVisible = text.isNotEmpty() || fieldFocused,
        sendOnEnter = settings.displaySetting.sendOnEnter,
        pasteAsFile = settings.displaySetting.pasteLongTextAsFile,
        pasteThreshold = settings.displaySetting.pasteLongTextThreshold,
    )
    val chipLabel = if (reasoningEditable) editReasoningChipLabel else editChipLabel

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 补全面板放在卡片上方：和 TextInputRow 里「弹窗在输入框上面」同一处观感，
        // 面板本体不复制，点项仍走 state.applyCompletion
        completions.visible?.let { list ->
            CompletionPopup(
                completionList = list,
                onItemClick = { item -> completions.apply(state, item) },
            )
        }

        // themed context 变了（配置/深色/density/fontScale）就重建视图；
        // 没变时保留同一个实例，输入法 composing 与光标都不动
        key(themedContext) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { NativeChatComposerView(themedContext) },
                onRelease = { released ->
                    released.release()
                    if (viewRef.view === released) {
                        viewRef.view = null
                        fieldFocused = false
                        latestActions.value.onFocusChange(false)
                    }
                },
                update = { view ->
                    viewRef.view = view
                    // listener 与主题每次都推：factory 只跑一次，旧实现把这两样锁在第一次的闭包里
                    view.setListener(listener)
                    view.applyTheme(theme)
                    view.setAnimationsEnabled(animationsEnabled)
                    view.bindText(snapshot.text, snapshot.selectionStart, snapshot.selectionEnd)
                    view.setEditing(snapshot.editing, chipLabel, snapshot.reasoningEditable)
                    view.setSendState(snapshot.showStop, snapshot.sendEnabled)
                    view.setInterjectVisible(snapshot.interjectVisible)
                    view.setAsrState(
                        snapshot.asrVisible,
                        snapshot.asrRecording,
                        snapshot.asrEnabled,
                    )
                    view.setModelLabel(snapshot.modelLabel)
                    view.setFullscreenVisible(snapshot.fullscreenVisible)
                    view.setSendOnEnter(snapshot.sendOnEnter)
                    view.setPasteLongTextAsFile(snapshot.pasteAsFile, snapshot.pasteThreshold)
                },
            )
        }
    }
}

/** 原生卡的视图句柄。用普通字段而不是 Compose state：写它不需要重组。 */
private class NativeComposerRef {
    var view: NativeChatComposerView? = null
}

/** 贴图/长文本落盘失败时的一句提示。放在 rememberUpdatedState 里，listener 拿到的是当前语言。 */
private class PasteFailureNotice(
    val message: String,
    val show: (String) -> Unit,
)

/** 原生卡要用的回调集合，整体走一次 rememberUpdatedState。 */
private class NativeComposerActions(
    val onSend: () -> Unit,
    val onSendLong: () -> Unit,
    val onAttachClick: () -> Unit,
    val onQuickClick: () -> Unit,
    val onQuickLongClick: () -> Unit,
    val onFullscreenClick: () -> Unit,
    val onModelClick: () -> Unit,
    val onAsrClick: () -> Unit,
    val onInterjectClick: () -> Unit,
    val onEditChipClose: () -> Unit,
    val onEditReasoningClick: () -> Unit,
    val onFocusChange: (Boolean) -> Unit,
    val sendOnEnter: Boolean,
)

/**
 * 推给原生视图的那一份状态快照。
 *
 * 它不再充当 `LaunchedEffect` 的 key（下发改走 `AndroidView.update`），留着是给同一个
 * 重组帧里的值一个名字：这些值必须在组合阶段读出来，update 里读不到重组订阅。
 */
private data class NativeComposerSnapshot(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val showStop: Boolean,
    val sendEnabled: Boolean,
    val editing: Boolean,
    val reasoningEditable: Boolean,
    val modelLabel: String,
    val interjectVisible: Boolean,
    val asrVisible: Boolean,
    val asrRecording: Boolean,
    val asrEnabled: Boolean,
    val fullscreenVisible: Boolean,
    val sendOnEnter: Boolean,
    val pasteAsFile: Boolean,
    val pasteThreshold: Int,
)

/**
 * 原生输入框 → TextFieldState。文字相同就只对齐选区，不重写文字：
 * 每次按键都 replace 一遍会让 Compose 侧的组合状态和输入法 composing 都对不上。
 *
 * 选区两端各自夹到 [0, length]，不按 start 去挤 end：反向选区（从后往前拖）的
 * start 会大于 end，`end.coerceIn(start, max)` 会把它掰成正向，选区长度和方向一起丢。
 */
internal fun applyNativeTextEdit(
    state: ChatInputState,
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
) {
    val currentText = state.textContent.text.toString()
    val currentSelection = state.textContent.selection
    val textChanged = currentText != text
    val selectionChanged = currentSelection.start != selectionStart ||
        currentSelection.end != selectionEnd
    if (!textChanged && !selectionChanged) return
    state.textContent.edit {
        if (textChanged) {
            replace(0, length, text)
        }
        val max = length
        val start = selectionStart.coerceIn(0, max)
        val end = selectionEnd.coerceIn(0, max)
        if (selection.start != start || selection.end != end) {
            selection = TextRange(start, end)
        }
    }
}

/** 只移动光标：不碰文字，避免把正在进行的输入打断。反向选区同样保留方向。 */
internal fun applyNativeSelectionEdit(
    state: ChatInputState,
    selectionStart: Int,
    selectionEnd: Int,
) {
    val current = state.textContent.selection
    if (current.start == selectionStart && current.end == selectionEnd) return
    state.textContent.edit {
        val max = length
        val start = selectionStart.coerceIn(0, max)
        val end = selectionEnd.coerceIn(0, max)
        if (selection.start != start || selection.end != end) {
            selection = TextRange(start, end)
        }
    }
}
