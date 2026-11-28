package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.WorkspaceEntity
import me.rerere.rikkahub.service.BackgroundShellTask
import me.rerere.rikkahub.ui.components.ai.BackgroundTasksSheet
import me.rerere.rikkahub.ui.components.ai.ChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ai.ConversationUsageDetails
import me.rerere.rikkahub.ui.components.message.AskUserSheet
import me.rerere.rikkahub.ui.components.ui.AppModalSheet
import kotlin.uuid.Uuid

/**
 * 选择菜单浮层：从聊天页上「挑一个东西」的几层。
 *
 * 它们都是纯接线 —— 打开条件、数据来源、回调全在页面层算好，这里只负责摆位置。
 * 单独成文件是因为这批东西最多，混在 ChatPage 里会把真正的布局骨架淹掉。
 *
 * ## 为什么都在同一个 Box 里平铺
 *
 * 这些弹层互斥（同一时刻只会开一个），而 [AppModalSheet] / [BackgroundTasksSheet]
 * 之类自带遮罩与进出场，叠着写比塞进一个 when 更好读 —— 各自管各自的 visible。
 *
 * ## 与「其他内容浮层」的分界
 *
 * 这里的每一层都是「选完就关」的选择器；需要确认、输入或展示长内容的
 * （重命名、删除确认、压缩确认、文件夹创建）在 `ChatContentLayers.kt`。
 *
 * 模型列表、ask_user 抽屉、后台任务在横屏都改走右侧栏，所以这里统一带
 * `!threePane` 条件；右栏那份由 `ChatSidePanes` 接管。
 */
@Composable
internal fun ChatSelectionLayers(
    state: ChatPageUiState,
    sheets: ChatSelectionSheetState,
    callbacks: ChatSelectionCallbacks,
) {
    val threePane = state.threePane

    BackgroundTasksSheet(
        visible = sheets.backgroundTasks && !threePane,
        tasks = callbacks.backgroundTasks,
        onStop = callbacks.onStopBackgroundTask,
        onDismiss = callbacks.onDismissBackgroundTasks,
    )

    // 用量明细：只有竖屏走弹层，横屏是右栏的一个标签页
    AppModalSheet(
        visible = sheets.usage && !threePane,
        onDismissRequest = callbacks.onDismissUsage,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_message_usage_title),
                style = MaterialTheme.typography.titleMedium,
            )
            ConversationUsageDetails(
                messages = state.conversationState.value.currentMessages,
                contextLength = state.currentChatModel?.effectiveContextWindow,
            )
        }
    }

    // 子代理任务树
    SubAgentTaskSheet(
        visible = sheets.taskTree && !threePane,
        conversationId = state.conversationId,
        onDismiss = callbacks.onDismissTaskTree,
        onOpenConversation = callbacks.onOpenSubAgentConversation,
    )

    // 附件 / 工作区文件：竖屏是弹层，横屏进右栏
    ChatFilesPickerSheet(
        visible = sheets.files && !threePane,
        inputState = state.inputState,
        setting = state.setting,
        conversationState = state.conversationState,
        folderWorkspaceName = state.folderWorkspaceName,
        assistant = state.assistant,
        chatModel = state.currentChatModel,
        vm = state.vm,
        attachmentPickerActions = callbacks.attachmentPickerActions,
        onStartVoiceMode = callbacks.onStartVoiceMode,
        onDismiss = callbacks.onDismissFiles,
    )

    // 终端：绑了多个可用工作区时先选一个
    TerminalWorkspacePickerSheet(
        visible = sheets.terminalPicker && !threePane,
        workspaces = callbacks.terminalWorkspaces,
        onSelect = callbacks.onSelectTerminalWorkspace,
        onDismiss = callbacks.onDismissTerminalPicker,
    )

    // 模型列表：横屏用右侧栏里的那份，这里只在竖屏组合
    if (!threePane) {
        ModelListSheet(state = callbacks.chatModelListState) { model ->
            callbacks.onSelectModel(model)
        }
    }

    // ask_user 问答抽屉：作答与任务清单提示的入口都汇到这里
    AskUserSheet(
        tool = callbacks.askUserTool,
        onDismissRequest = callbacks.onDismissAskUser,
        onSubmit = callbacks.onSubmitAskUser,
    )
}

/**
 * 选择菜单浮层的显隐状态。
 *
 * 这几个布尔值原来散在 ChatPage 的局部变量里，和输入框、消息区的状态混在一起，
 * 看不出哪些是一组的。收进一个 data class 之后，「这些是同一批弹层」是写在类型上的。
 */
internal data class ChatSelectionSheetState(
    val backgroundTasks: Boolean = false,
    val usage: Boolean = false,
    val taskTree: Boolean = false,
    val files: Boolean = false,
    val terminalPicker: Boolean = false,
)

/**
 * 选择菜单浮层的回调与数据。
 *
 * 每个字段都有默认值：页面层只填自己真的要用的那几个，没接的槽位保持空实现。
 * 这样加一层新弹层时，不用回头改所有调用点。
 */
internal data class ChatSelectionCallbacks(
    val backgroundTasks: List<BackgroundShellTask> = emptyList(),
    // BackgroundTasksSheet 的 onStop 是 suspend：它要等 shell 真的停下来
    val onStopBackgroundTask: suspend (String) -> Unit = {},
    val onDismissBackgroundTasks: () -> Unit = {},
    val onDismissUsage: () -> Unit = {},
    val onDismissTaskTree: () -> Unit = {},
    val onOpenSubAgentConversation: (Uuid) -> Unit = {},
    val attachmentPickerActions: ChatAttachmentPickerActions,
    val onStartVoiceMode: () -> Unit = {},
    val onDismissFiles: () -> Unit = {},
    val terminalWorkspaces: List<WorkspaceEntity> = emptyList(),
    val onSelectTerminalWorkspace: (String) -> Unit = {},
    val onDismissTerminalPicker: () -> Unit = {},
    val chatModelListState: ModelListState,
    val onSelectModel: (me.rerere.ai.provider.Model) -> Unit = {},
    val askUserTool: UIMessagePart.Tool? = null,
    val onDismissAskUser: () -> Unit = {},
    val onSubmitAskUser: (toolCallId: String, answerJson: String) -> Unit = { _, _ -> },
)
