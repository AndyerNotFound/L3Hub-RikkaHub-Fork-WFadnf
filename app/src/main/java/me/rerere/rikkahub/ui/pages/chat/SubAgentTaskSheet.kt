package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_STATE_COMPLETED
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_STATE_FAILED
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_STATE_RUNNING
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.utils.JsonInstantPretty
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private data class TaskNode(
    val conversation: Conversation,
    val children: List<TaskNode>,
)

private const val MAX_TREE_DEPTH = 6

@Composable
fun SubAgentTaskSheet(
    conversationId: Uuid,
    onDismiss: () -> Unit,
    onOpenConversation: (Uuid) -> Unit,
) {
    val repository: ConversationRepository = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settingsFlow.collectAsState()
    val assistantNames = remember(settings.assistants) {
        settings.assistants.associate { it.id to it.name }
    }
    var tree by remember { mutableStateOf<TaskNode?>(null) }

    LaunchedEffect(conversationId) {
        while (true) {
            tree = runCatching { loadTree(repository, conversationId) }.getOrNull()
            delay(1000)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.task_tree_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            val root = tree
            if (root == null || (root.children.isEmpty() && root.conversation.currentMessages.isEmpty())) {
                Text(
                    text = stringResource(R.string.task_tree_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 560.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    item(key = "root") {
                        TaskTreeNode(
                            node = root,
                            prefix = emptyList(),
                            isLast = true,
                            isRoot = true,
                            assistantNames = assistantNames,
                            onOpen = onOpenConversation,
                        )
                    }
                }
            }
        }
    }
}

private suspend fun loadTree(
    repository: ConversationRepository,
    id: Uuid,
    depth: Int = 0,
): TaskNode? = withContext(Dispatchers.IO) {
    val conversation = repository.getConversationById(id) ?: return@withContext null
    val children = if (depth >= MAX_TREE_DEPTH) {
        emptyList()
    } else {
        repository.getChildConversations(id).mapNotNull { child -> loadTree(repository, child.id, depth + 1) }
    }
    TaskNode(conversation = conversation, children = children)
}

/**
 * 一个代理节点：标题行 + 工具叶子 + 子代理。
 *
 * [prefix] 记录每一层祖先是否还有后续兄弟，用于绘制竖向的连接线；
 * 根节点不画连接线，只显示一个圆点。
 */
@Composable
private fun TaskTreeNode(
    node: TaskNode,
    prefix: List<Boolean>,
    isLast: Boolean,
    isRoot: Boolean,
    assistantNames: Map<Uuid, String>,
    onOpen: (Uuid) -> Unit,
) {
    val conversation = node.conversation
    var expanded by remember(conversation.id) { mutableStateOf(true) }
    val tools = remember(conversation.currentMessages) {
        conversation.currentMessages.flatMap { message ->
            message.parts.filterIsInstance<UIMessagePart.Tool>()
        }
    }
    val assistantName = assistantNames[conversation.assistantId].orEmpty()
    val isSubAgent = conversation.parentConversationId != null
    val subAgentLabel = stringResource(R.string.task_tree_sub_agent)
    val hasChildren = tools.isNotEmpty() || node.children.isNotEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = hasChildren) { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TreeGuide(prefix = prefix, isLast = isLast, isRoot = isRoot)
            Surface(
                color = if (isRoot) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                    if (hasChildren) {
                        Icon(
                            imageVector = if (expanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (expanded) {
            tools.forEachIndexed { index, tool ->
                val lastTool = index == tools.lastIndex && node.children.isEmpty()
                ToolLeaf(
                    tool = tool,
                    prefix = prefix + isLast,
                    isLast = lastTool,
                )
            }
            node.children.forEachIndexed { index, child ->
                TaskTreeNode(
                    node = child,
                    prefix = prefix + isLast,
                    isLast = index == node.children.lastIndex,
                    isRoot = false,
                    assistantNames = assistantNames,
                    onOpen = onOpen,
                )
            }
            if (tools.isEmpty() && node.children.isEmpty() && isSubAgent) {
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    TreeGuide(prefix = prefix + isLast, isLast = true, isRoot = false)
                    Text(
                        text = stringResource(R.string.task_tree_no_tools),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isSubAgent) {
                Row(
                    modifier = Modifier
                        .padding(start = (prefix.size + 1) * 14.dp)
                        .clickable { onOpen(conversation.id) }
                        .padding(vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.task_tree_open),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** 连接线 + 分支符，等宽字体保证每一层的竖线对齐 */
@Composable
private fun TreeGuide(prefix: List<Boolean>, isLast: Boolean, isRoot: Boolean) {
    val text = buildString {
        prefix.forEach { hasNext ->
            if (hasNext) append("│  ") else append("   ")
        }
        when {
            isRoot -> append("● ")
            isLast -> append("└─ ")
            else -> append("├─ ")
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.outlineVariant,
        maxLines = 1,
    )
}

@Composable
private fun ToolLeaf(
    tool: UIMessagePart.Tool,
    prefix: List<Boolean>,
    isLast: Boolean,
) {
    var open by remember(tool.toolCallId) { mutableStateOf(false) }
    val input = remember(tool.input) { formatToolInput(tool.input) }
    val output = remember(tool.output) { formatToolOutput(tool.output) }
    val running = !tool.isExecuted

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TreeGuide(prefix = prefix, isLast = isLast, isRoot = false)
            Icon(
                imageVector = HugeIcons.Tools,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(14.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = tool.toolName,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(
                    if (running) R.string.task_tree_state_running else R.string.task_tree_state_completed
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) {
            Column(
                modifier = Modifier.padding(start = (prefix.size + 1) * 14.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (input.isNotBlank()) {
                    CodeBlock(text = input, maxLines = 12)
                }
                if (output.isNotBlank()) {
                    CodeBlock(text = output, maxLines = 16, emphasized = true)
                }
                if (input.isBlank() && output.isBlank()) {
                    Text(
                        text = stringResource(R.string.task_tree_no_tools),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
