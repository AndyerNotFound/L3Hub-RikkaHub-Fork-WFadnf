package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlin.uuid.Uuid
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Guidance
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.effectiveContextWindow
import me.rerere.rikkahub.service.BackgroundShellTask
import me.rerere.rikkahub.ui.components.ai.BackgroundTasksContent
import me.rerere.rikkahub.ui.components.ai.ChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ai.ModelListContent
import me.rerere.rikkahub.ui.components.ai.ModelListState
import me.rerere.rikkahub.ui.components.message.ConversationUsageDetails
import me.rerere.rikkahub.ui.components.message.EditedFilesList
import me.rerere.rikkahub.ui.components.ui.ColumnResizeHandle
import me.rerere.rikkahub.ui.hooks.ChatInputState

/**
 * 横屏右栏的标签页状态：开着哪几个、当前看哪一个。
 *
 * 这两份状态原来直接摆在 ChatPageContent 里，开 / 关 / 自动补标签页的读写散在五处；
 * 收到这里以后，「标签条顺序就是打开顺序」「关掉当前页就落到最后一个」各只有一份实现。
 * 存续仍走原来的 rememberSaveable：转屏、进程重建以后标签页还在。
 *
 * 判断都读 names，不缓存 openPanes：对标签的操作要读最新值，连点两下时上一帧还是旧的。
 */
@Stable
internal class ChatPaneTabs(
    openNames: List<String>,
    selectedName: String?,
) {
    var openNames by mutableStateOf(openNames)
        private set
    var selectedName by mutableStateOf(selectedName)
        private set

    val openPanes: List<ChatSidePane>
        get() = openNames.mapNotNull { name -> ChatSidePane.entries.firstOrNull { it.name == name } }

    val selected: ChatSidePane?
        get() = selectedName?.let { name -> openPanes.firstOrNull { it.name == name } }

    /** 打开并切过去 */
    fun open(pane: ChatSidePane) {
        if (openNames.none { it == pane.name }) {
            openNames = openNames + pane.name
        }
        selectedName = pane.name
    }

    /** 只打开，不抢当前看板：自动补「本轮改动」「清单与引导」时用 */
    fun ensureOpen(pane: ChatSidePane) {
        if (openNames.none { it == pane.name }) {
            openNames = openNames + pane.name
        }
    }

    fun select(pane: ChatSidePane) {
        selectedName = pane.name
    }

    fun close(pane: ChatSidePane) {
        val remaining = openNames.filterNot { it == pane.name }
        openNames = remaining
        if (selectedName == pane.name || selectedName !in remaining) {
            selectedName = remaining.lastOrNull()
        }
    }

    /** 收掉当前标签页；一个都没选中时按标签条第一个关 */
    fun closeSelected() {
        val name = selectedName?.takeIf { it in openNames } ?: openNames.firstOrNull()
        name?.let { target -> ChatSidePane.entries.firstOrNull { it.name == target } }?.let(::close)
    }
}

private val ChatPaneTabsSaver = listSaver<ChatPaneTabs, Any?>(
    save = { listOf(it.openNames, it.selectedName) },
    restore = { saved ->
        ChatPaneTabs(
            openNames = (saved[0] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            selectedName = saved[1] as? String,
        )
    },
)

@Composable
internal fun rememberChatPaneTabs(): ChatPaneTabs = rememberSaveable(saver = ChatPaneTabsSaver) {
    ChatPaneTabs(
        openNames = listOf(ChatSidePane.Details.name),
        selectedName = ChatSidePane.Details.name,
    )
}

/**
 * 横屏右栏：分隔条 + 标签条 + 各个看板。
 *
 * 装配原本是 ChatPageContent 里的一段 when，连同面板要的二十几个参数整段搬到这里。
 * 行为没变：标签条顺序、关页落点、每个面板拿到的回调都与改前一一对应，
 * 只有「收起/关闭」从闭包换成了 [onClosePane]、打开子代理会话换成了 [onOpenConversation]。
 */
@Composable
internal fun ChatSidePanes(
    panes: List<ChatSidePane>,
    selected: ChatSidePane?,
    onSelect: (ChatSidePane) -> Unit,
    onOpen: (ChatSidePane) -> Unit,
    onClose: (ChatSidePane) -> Unit,
    onClosePane: () -> Unit,
    onOpenConversation: (Uuid) -> Unit,
    onResize: (Dp) -> Unit,
    hazeState: HazeState,
    conversationState: State<Conversation>,
    conversationId: Uuid,
    assistant: Assistant,
    chatModel: Model?,
    setting: Settings,
    workspaces: List<WorkspaceEntity>,
    folderWorkspaceName: String?,
    contextTokens: Int?,
    messageCount: Int,
    requestPreviewState: RequestPreviewUiState,
    turnHasChanges: Boolean,
    todos: List<TodoItem>,
    guidances: List<Guidance>,
    hideCompletedTodoCard: Boolean,
    running: Boolean,
    inputState: ChatInputState,
    attachmentPickerActions: ChatAttachmentPickerActions,
    onStartVoiceMode: () -> Unit,
    chatModelListState: ModelListState,
    backgroundTasks: List<BackgroundShellTask>,
    vm: ChatVM,
    modifier: Modifier = Modifier,
) {
    ColumnResizeHandle(onDrag = onResize)
    ChatSidePanelColumn(
        panes = panes,
        selected = selected,
        onSelect = onSelect,
        onOpen = onOpen,
        onClose = onClose,
        // 宽度与内边距由调用点在 Row 里给（weight 只有 RowScope 里有）
        modifier = modifier,
        // 右栏背后是页面背景，取背景那份 Haze 源；聊天内容的源只覆盖左边
        hazeState = hazeState,
    ) { pane ->
        when (pane) {
            ChatSidePane.Details -> ConversationDetailsPanel(
                assistant = assistant,
                model = chatModel,
                settings = setting,
                workspaces = workspaces,
                folderWorkspaceName = folderWorkspaceName,
                contextTokens = contextTokens,
                messageCount = messageCount,
                previewState = requestPreviewState,
                hazeState = hazeState,
                onCollapse = onClosePane,
                embedded = true,
                // 标签条已经有标题，内容里不再重复一行
                showHeader = false,
            )

            ChatSidePane.TaskTree -> SubAgentTaskTree(
                conversationId = conversationId,
                // 关闭当前标签由 ChatPage 的 onOpenConversation 统一处理，不能在这里再关一层
                onOpenConversation = onOpenConversation,
                modifier = Modifier.fillMaxSize(),
                showTitle = false,
            )

            ChatSidePane.Status -> ConversationStatusPane(
                todos = todos,
                guidances = guidances,
                assistant = assistant,
                hideCompletedTodos = hideCompletedTodoCard,
                onTodosChange = vm::updateTodos,
                onUpdateAssistant = vm::updateAssistant,
                onGuidanceToggleMode = vm::toggleGuidanceMode,
                onGuidanceRemove = vm::removeGuidance,
                running = running,
                modifier = Modifier.fillMaxSize(),
            )

            ChatSidePane.Changes -> TurnChangesPane(
                parts = conversationState.value.currentMessages
                    .lastOrNull { it.role == MessageRole.ASSISTANT }?.parts.orEmpty(),
                assistant = assistant,
                conversationId = conversationId,
                hasChanges = turnHasChanges,
                modifier = Modifier.fillMaxSize(),
            )

            ChatSidePane.Usage -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ConversationUsageDetails(
                    messages = conversationState.value.currentMessages,
                    contextLength = chatModel?.effectiveContextWindow,
                )
            }

            ChatSidePane.Files -> ChatFilesPickerContent(
                inputState = inputState,
                setting = setting,
                conversationState = conversationState,
                folderWorkspaceName = folderWorkspaceName,
                assistant = assistant,
                chatModel = chatModel,
                vm = vm,
                attachmentPickerActions = attachmentPickerActions,
                onStartVoiceMode = onStartVoiceMode,
                onDismiss = onClosePane,
                modifier = Modifier.fillMaxSize(),
            )

            ChatSidePane.Workspace -> WorkspaceFilesPane(
                workspaceIds = assistant.allWorkspaceIds.map { it.toString() },
                workspaces = workspaces,
                modifier = Modifier.fillMaxSize(),
            )

            ChatSidePane.Terminal -> WorkspaceTerminalPane(
                workspaceIds = assistant.allWorkspaceIds.map { it.toString() },
                workspaces = workspaces,
                modifier = Modifier.fillMaxSize(),
            )

            ChatSidePane.ModelPicker -> ModelListContent(
                state = chatModelListState,
                onSelect = { model -> vm.setChatModel(model) },
                onDismiss = onClosePane,
                modifier = Modifier.fillMaxSize(),
            )

            ChatSidePane.BackgroundTasks -> BackgroundTasksContent(
                tasks = backgroundTasks,
                onStop = vm::stopBackgroundTask,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            )
        }
    }
}

/** 右栏的「本轮改动」标签页：和消息下面那一行同源，只是换了个位置 */
@Composable
private fun TurnChangesPane(
    parts: List<UIMessagePart>,
    assistant: Assistant,
    conversationId: Uuid,
    hasChanges: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!hasChanges) {
        Box(
            modifier = modifier.padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.chat_page_turn_changes_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 16.dp),
    ) {
        EditedFilesList(
            parts = parts,
            assistant = assistant,
            conversationId = conversationId,
            isLastMessage = true,
            initiallyExpanded = true,
        )
    }
}
