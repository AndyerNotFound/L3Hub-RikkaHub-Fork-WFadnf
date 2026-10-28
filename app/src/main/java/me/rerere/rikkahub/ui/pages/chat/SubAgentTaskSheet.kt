package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_STATE_COMPLETED
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_STATE_FAILED
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_STATE_RUNNING
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private data class TaskNode(
    val conversation: Conversation,
    val children: List<TaskNode>,
)

@Composable
fun SubAgentTaskSheet(
    conversationId: Uuid,
    onDismiss: () -> Unit,
    onOpenConversation: (Uuid) -> Unit,
) {
    val repository: ConversationRepository = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settingsFlow.collectAsState()
    var tree by remember { mutableStateOf<TaskNode?>(null) }

    LaunchedEffect(conversationId) {
        while (true) {
            tree = runCatching { loadTree(repository, conversationId) }.getOrNull()
            delay(1000)
        }
    }

    val rows = remember(tree) {
        buildList {
            tree?.let { flatten(it, 0, this) }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.task_tree_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (rows.isEmpty()) {
                Text(
                    text = stringResource(R.string.task_tree_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                    items(rows, key = { it.first.conversation.id.toString() + it.second }) { (node, depth) ->
                        TaskRow(
                            node = node,
                            depth = depth,
                            assistantName = settings.assistants
                                .firstOrNull { it.id == node.conversation.assistantId }
                                ?.name
                                .orEmpty(),
                            onOpen = { onOpenConversation(node.conversation.id) },
                        )
                    }
                }
            }
        }
    }
}

private fun flatten(node: TaskNode, depth: Int, out: MutableList<Pair<TaskNode, Int>>) {
    out.add(node to depth)
    node.children.forEach { child -> flatten(child, depth + 1, out) }
}

private suspend fun loadTree(
    repository: ConversationRepository,
    id: Uuid,
    depth: Int = 0,
): TaskNode? = withContext(Dispatchers.IO) {
    val conversation = repository.getConversationById(id) ?: return@withContext null
    val children = if (depth >= 6) {
        emptyList()
    } else {
        repository.getChildConversations(id).mapNotNull { child -> loadTree(repository, child.id, depth + 1) }
    }
    TaskNode(conversation = conversation, children = children)
}

@Composable
private fun TaskRow(
    node: TaskNode,
    depth: Int,
    assistantName: String,
    onOpen: () -> Unit,
) {
    val conversation = node.conversation
    var expanded by remember(conversation.id) { mutableStateOf(false) }
    val tools = conversation.currentMessages.flatMap { message ->
        message.parts.filterIsInstance<UIMessagePart.Tool>()
    }
    val isSubAgent = conversation.parentConversationId != null
    val subAgentLabel = stringResource(R.string.task_tree_sub_agent)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (12 + depth * 16).dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        if (isSubAgent) append(subAgentLabel + " · ")
                        append(assistantName.ifBlank { "?" })
                        if (conversation.title.isNotBlank()) append(" · ${conversation.title}")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isSubAgent && !conversation.subAgentTask.isNullOrBlank()) {
                    Text(
                        text = conversation.subAgentTask.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            StateBadge(state = conversation.subAgentState)
        }

        if (tools.isNotEmpty()) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    text = stringResource(R.string.task_tree_tools) + " (${tools.size})",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    tools.forEach { tool ->
                        val output = tool.output.filterIsInstance<UIMessagePart.Text>()
                            .joinToString("\n") { it.text }
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = tool.toolName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (tool.input.isNotBlank()) {
                                Text(
                                    text = tool.input,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (output.isNotBlank()) {
                                Text(
                                    text = output,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        } else if (isSubAgent) {
            Text(
                text = stringResource(R.string.task_tree_no_tools),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isSubAgent) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.task_tree_open),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable(onClick = onOpen)
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StateBadge(state: String?) {
    val (label, color) = when (state) {
        SUB_AGENT_STATE_RUNNING -> stringResource(R.string.task_tree_state_running) to MaterialTheme.colorScheme.primary
        SUB_AGENT_STATE_COMPLETED -> stringResource(R.string.task_tree_state_completed) to MaterialTheme.colorScheme.tertiary
        SUB_AGENT_STATE_FAILED -> stringResource(R.string.task_tree_state_failed) to MaterialTheme.colorScheme.error
        else -> return
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(start = 8.dp),
    )
}
