package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Guidance
import me.rerere.rikkahub.ui.components.message.AskUserPendingSummary
import me.rerere.rikkahub.ui.components.message.ConversationTodoCard
import me.rerere.rikkahub.ui.components.message.ConversationUsageDetails
import me.rerere.rikkahub.ui.components.message.TodoReminderUi
import me.rerere.rikkahub.ui.style.FilledTonalButton
import me.rerere.rikkahub.ui.style.Card
import me.rerere.rikkahub.ui.style.TextButton

/** Phase-one hybrid: native navigation, existing rich task/approval widgets and real bindings. */
@Composable
internal fun ChatMissionTab(
    assistant: Assistant,
    todos: List<TodoItem>,
    guidances: List<Guidance>,
    pendingQuestions: List<AskUserPendingSummary>,
    workspaces: List<WorkspaceEntity>,
    workspaceCwds: Map<String, String>,
    folderWorkspaceName: String?,
    running: Boolean,
    backgroundTaskCount: Int,
    onTodosChange: (List<TodoItem>) -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
    onGuidanceToggleMode: (Guidance) -> Unit,
    onGuidanceRemove: (Guidance) -> Unit,
    onPendingQuestionClick: (String) -> Unit,
    onShowFiles: () -> Unit,
    onShowTaskTree: () -> Unit,
    onShowBackgroundTasks: () -> Unit,
    onOpenWorkspace: (String) -> Unit,
    onOpenWorkspaces: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bound = remember(assistant.allWorkspaceIds, workspaces) {
        assistant.allWorkspaceIds.mapNotNull { id -> workspaces.firstOrNull { it.id == id.toString() } }
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.l3_native_mission_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.l3_native_mission_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (todos.isEmpty() && guidances.isEmpty() && pendingQuestions.isEmpty()) {
            Text(
                stringResource(R.string.chat_page_status_empty),
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // Completed tasks remain inspectable in Mission even when the composer hides them.
            ConversationTodoCard(
                todos = todos,
                initiallyExpanded = true,
                onTodosChange = onTodosChange,
                reminder = if (todos.isEmpty()) null else TodoReminderUi(
                    assistant.todoReminderEnabled, assistant.todoReminderInterval,
                ),
                onReminderChange = { reminder ->
                    onUpdateAssistant(assistant.copy(
                        todoReminderEnabled = reminder.enabled,
                        todoReminderInterval = reminder.interval,
                    ))
                },
                guidances = guidances,
                onGuidanceToggleMode = onGuidanceToggleMode,
                onGuidanceRemove = onGuidanceRemove,
                pendingQuestions = pendingQuestions,
                onPendingQuestionClick = onPendingQuestionClick,
                running = running,
            )
        }
        Text(stringResource(R.string.chat_page_details_workspaces), style = MaterialTheme.typography.titleMedium)
        folderWorkspaceName?.let { name ->
            Text(
                if (name.isBlank()) stringResource(R.string.chat_page_details_workspace_folder_unknown)
                else stringResource(R.string.chat_page_details_workspace_folder, name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (bound.isEmpty()) {
            Text(stringResource(R.string.chat_page_workspace_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        bound.forEach { workspace ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(workspace.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        workspaceCwds[workspace.id] ?: workspace.workspaceDir(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    // No fabricated Git branch, free space, elapsed time or progress percentage.
                    TextButton(onClick = { onOpenWorkspace(workspace.id) }) {
                        Text(stringResource(R.string.l3_native_mission_open_workspace))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onShowFiles) {
                Text(stringResource(R.string.l3_native_mission_bindings))
            }
            TextButton(onClick = onOpenWorkspaces) {
                Text(stringResource(R.string.l3_native_mission_workspaces))
            }
        }
        FilledTonalButton(onClick = onShowTaskTree, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.chat_task_tree_button))
        }
        FilledTonalButton(onClick = onShowBackgroundTasks, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.l3_native_mission_background_tasks, backgroundTaskCount))
        }
    }
}

/** Keep per-tab scrolling, but observe per-token usage only inside this narrow reader. */
@Composable
internal fun ChatUsageTab(
    conversationState: State<Conversation>,
    contextLength: Int?,
    onShowStatistics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.chat_message_usage_title), style = MaterialTheme.typography.headlineSmall)
        ConversationUsageDetails(
            messages = conversationState.value.currentMessages,
            contextLength = contextLength,
        )
        TextButton(onClick = onShowStatistics) {
            Text(stringResource(R.string.l3_native_usage_all_statistics))
        }
    }
}
