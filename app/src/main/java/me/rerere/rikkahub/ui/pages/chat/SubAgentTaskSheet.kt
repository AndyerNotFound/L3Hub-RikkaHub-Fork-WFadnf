package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_STATE_COMPLETED
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_STATE_FAILED
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_STATE_RUNNING
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
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
            .padding(start = (12 + depth * 14).dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        if (isSubAgent) append(subAgentLabel).append(" · ")
                        append(assistantName.ifBlank { "?" })
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = conversation.title.ifBlank { conversation.subAgentTask.orEmpty() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StateBadge(state = conversation.subAgentState)
        }

        if (tools.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (expanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.task_tree_tools) + " (${tools.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tools.forEach { tool -> ToolCallCard(tool) }
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
private fun ToolCallCard(tool: UIMessagePart.Tool) {
    var open by remember(tool.toolCallId) { mutableStateOf(false) }
    val input = remember(tool.input) { formatToolInput(tool.input) }
    val output = remember(tool.output) { formatToolOutput(tool.output) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tool.toolName,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { open = !open },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (open) R.string.task_tree_collapse else R.string.task_tree_expand
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (input.isNotBlank()) {
                CodeBlock(
                    text = input,
                    maxLines = if (open) 40 else 3,
                )
            }
            if (output.isNotBlank()) {
                CodeBlock(
                    text = output,
                    maxLines = if (open) 40 else 4,
                    emphasized = true,
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(text: String, maxLines: Int, emphasized: Boolean = false) {
    Surface(
        color = if (emphasized) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        )
    }
}

private fun formatToolInput(raw: String): String {
    if (raw.isBlank()) return ""
    val element = runCatching { Json.parseToJsonElement(raw) }.getOrNull() ?: return raw
    return runCatching {
        JsonInstantPretty.encodeToString(JsonElement.serializer(), element)
    }.getOrDefault(raw)
}

private fun formatToolOutput(parts: List<UIMessagePart>): String {
    val text = parts.filterIsInstance<UIMessagePart.Text>()
        .joinToString("\n") { it.text }
        .trim()
    if (text.isEmpty()) return ""
    val obj = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return text
    val exitCode = (obj["exitCode"] as? JsonPrimitive)?.contentOrNull
    val stdout = (obj["stdout"] as? JsonPrimitive)?.contentOrNull
    if (stdout != null) {
        return buildString {
            if (exitCode != null) appendLine("exit $exitCode")
            append(stdout.trim())
        }.trim()
    }
    return formatToolInput(text)
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
