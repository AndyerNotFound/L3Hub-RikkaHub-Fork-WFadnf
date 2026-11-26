package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.Model
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getSelectedASRProvider
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.components.ai.ChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ai.FilesPicker
import me.rerere.rikkahub.ui.components.ai.SearchMode
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.ui.components.AppModalSheet

@Composable
internal fun ChatFilesPickerSheet(
    visible: Boolean,
    inputState: ChatInputState,
    setting: Settings,
    conversationState: State<Conversation>,
    folderWorkspaceName: String?,
    assistant: Assistant,
    chatModel: Model?,
    vm: ChatVM,
    attachmentPickerActions: ChatAttachmentPickerActions,
    onStartVoiceMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppModalSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.files_picker_title),
    ) {
        ChatFilesPickerContent(
            inputState = inputState,
            setting = setting,
            conversationState = conversationState,
            folderWorkspaceName = folderWorkspaceName,
            assistant = assistant,
            chatModel = chatModel,
            vm = vm,
            attachmentPickerActions = attachmentPickerActions,
            onStartVoiceMode = onStartVoiceMode,
            onDismiss = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 附件、工作区、搜索、扩展这些入口的本体：不含外层弹层，横屏时整块放进右侧栏 */
@Composable
internal fun ChatFilesPickerContent(
    inputState: ChatInputState,
    setting: Settings,
    conversationState: State<Conversation>,
    folderWorkspaceName: String?,
    assistant: Assistant,
    chatModel: Model?,
    vm: ChatVM,
    attachmentPickerActions: ChatAttachmentPickerActions,
    onStartVoiceMode: () -> Unit,
    onDismiss: () -> Unit,
    // 弹层里保持 wrap：sheet 的高度跟着内容走，别被撑成整屏；侧栏里传 fillMaxSize
    modifier: Modifier = Modifier,
) {
    val conversation = conversationState.value
    val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showCompressDialog by remember { mutableStateOf(false) }

    fun dismissAll() {
        showCompressDialog = false
        onDismiss()
    }

    Box(modifier = modifier) {
        FilesPicker(
            conversation = conversation,
            folderWorkspaceName = folderWorkspaceName,
            state = inputState,
            assistant = assistant,
            chatModel = chatModel,
            mcpManager = vm.mcpManager,
            onCompressContext = vm::handleCompressContext,
            onUpdateAssistant = vm::updateAssistant,
            onUpdateWorkspaceCwd = vm::updateWorkspaceCwd,
            onUpdateSearchMode = { mode ->
                vm.updateSearch(
                    enableWebSearch = mode == SearchMode.LOCAL,
                    builtInSearch = mode == SearchMode.BUILT_IN,
                )
            },
            onToggleSearchService = { id ->
                vm.updateSettings { latest ->
                    val defaultId = latest.searchServices
                        .getOrNull(latest.searchServiceSelected)
                        ?.id
                        ?.toString()
                    val current = latest.enabledSearchServiceIds.ifEmpty {
                        if (defaultId != null) setOf(defaultId) else emptySet()
                    }
                    val next = when {
                        id !in current -> current + id
                        current.size > 1 -> current - id
                        else -> current
                    }
                    latest.copy(enabledSearchServiceIds = next)
                }
            },
            showCompressDialog = showCompressDialog,
            onShowCompressDialogChange = { showCompressDialog = it },
            onDismiss = { dismissAll() },
            onTakePic = attachmentPickerActions.onTakePicture,
            onPickImage = attachmentPickerActions.onPickImage,
            onPickVideo = attachmentPickerActions.onPickVideo,
            onPickAudio = attachmentPickerActions.onPickAudio,
            onPickFile = attachmentPickerActions.onPickFile,
            onSketch = attachmentPickerActions.onSketch,
            onStartVoiceMode = if (
                setting.getSelectedASRProvider()?.supportsServerVadVoiceMode == true &&
                voiceState.phase == VoicePhase.Off
            ) {
                {
                    dismissAll()
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onStartVoiceMode()
                }
            } else null,
        )
    }
}

/** 悬浮栏「终端」槽位：绑定了多个工作区时，先让用户挑一个再进终端。 */
@Composable
internal fun TerminalWorkspacePickerSheet(
    visible: Boolean,
    workspaces: List<WorkspaceEntity>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AppModalSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.floating_bar_item_terminal),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 外层已有 16dp 横向边距（标题和分组共用），CardGroup 不带外距
            CardGroup {
                workspaces.forEach { workspace ->
                    item(
                        onClick = { onSelect(workspace.id) },
                        leadingContent = { CardGroupIcon(HugeIcons.ComputerTerminal01) },
                        headlineContent = { Text(workspace.name) },
                        supportingContent = { Text(workspace.workspaceDir()) },
                    )
                }
            }
        }
    }
}
