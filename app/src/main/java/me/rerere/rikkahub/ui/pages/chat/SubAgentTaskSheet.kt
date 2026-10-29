package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.JsonInstantPretty
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private data class TaskNode(
    val conversation: Conversation,
    val children: List<TaskNode>,
)

private const val MAX_TREE_DEPTH = 6
private val RailStep = 14.dp

@Composable
fun SubAgentTaskSheet(
    conversationId: Uuid,
    onDismiss: () -> Unit,
    onOpenConversation: (Uuid) -> Unit,
) {
    val repository: ConversationRepository = koinInject()
    val chatService: ChatService = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settingsFlow.collectAsState()
    val assistantNames = remember(settings.assistants) {
        settings.assistants.associate { it.id to it.name }
    }
    var tree by remember { mutableStateOf<TaskNode?>(null) }

    LaunchedEffect(conversationId) {
        while (true) {
            tree = runCatching { loadTree(repository, chatService, conversationId) }.getOrNull()
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
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    item(key = "root") {
                        AgentBranch(
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
    chatService: ChatService,
    id: Uuid,
    depth: Int = 0,
): TaskNode? = withContext(Dispatchers.IO) {
    val conversation = chatService.getConversation(id)
        ?: repository.getConversationById(id)
        ?: return@withContext null
    val children = if (depth >= MAX_TREE_DEPTH) {
        emptyList()
    } else {
        repository.getChildConversations(id).mapNotNull { child ->
            loadTree(repository, chatService, child.id, depth + 1)
        }
    }
    TaskNode(conversation = conversation, children = children)
}

/**
 * 一个代理分支：代理卡片 + 它调用的工具 + 它派发的子代理。
 *
 * [prefix] 逐层记录祖先后面是否还有兄弟节点，用来决定连接线画到哪里；
 * 同一个代理的工具和子代理都挂在它下面，缩进对齐。
 */
@Composable
private fun AgentBranch(
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
    val children = node.children
    val assistantName = assistantNames[conversation.assistantId].orEmpty()
    val isSubAgent = conversation.parentConversationId != null
    val hasChildren = tools.isNotEmpty() || children.isNotEmpty()
    val childPrefix = prefix + !isLast

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            TreeRail(prefix = prefix, isLast = isLast, isRoot = isRoot)
            Surface(
                onClick = { if (hasChildren) expanded = !expanded },
                enabled = hasChildren,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 3.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (isRoot) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (isSubAgent) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(6.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.task_tree_sub_agent),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                }
                            }
                            Text(
                                text = assistantName.ifBlank { "?" },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = conversation.title.ifBlank { conversation.subAgentTask.orEmpty() },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (hasChildren) {
                            Text(
                                text = stringResource(
                                    R.string.task_tree_tools_count,
                                    tools.size,
                                    children.size,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                    StateBadge(state = conversation.subAgentState)
                    if (hasChildren) {
                        Icon(
                            imageVector = if (expanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                            contentDescription = stringResource(
                                if (expanded) R.string.task_tree_collapse else R.string.task_tree_expand
                            ),
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
                ToolLeaf(
                    tool = tool,
                    prefix = childPrefix,
                    isLast = children.isEmpty() && index == tools.lastIndex,
                )
            }
            children.forEachIndexed { index, child ->
                AgentBranch(
                    node = child,
                    prefix = childPrefix,
                    isLast = index == children.lastIndex,
                    isRoot = false,
                    assistantNames = assistantNames,
                    onOpen = onOpen,
                )
            }
            if (tools.isEmpty() && children.isEmpty() && isSubAgent) {
                NoToolsRow(prefix = childPrefix)
            }
            if (isSubAgent) {
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    TreeRail(prefix = childPrefix, isLast = true, isRoot = false)
                    Text(
                        text = stringResource(R.string.task_tree_open),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onOpen(conversation.id) }
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoToolsRow(prefix: List<Boolean>) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        TreeRail(prefix = prefix, isLast = true, isRoot = false)
        Text(
            text = stringResource(R.string.task_tree_no_tools),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

/** 连接线：每个祖先一列竖线，末端画一个拐角接到内容 */
@Composable
private fun TreeRail(prefix: List<Boolean>, isLast: Boolean, isRoot: Boolean) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .width(RailStep * (prefix.size + 1).toFloat())
            .fillMaxHeight()
    ) {
        val step = RailStep.toPx()
        val midY = size.height / 2f
        val stroke = 1.dp.toPx()
        prefix.forEachIndexed { index, hasNext ->
            if (hasNext) {
                val x = index * step + step / 2f
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), stroke)
            }
        }
        val x = prefix.size * step + step / 2f
        if (isRoot) {
            drawCircle(lineColor, radius = 2.5.dp.toPx(), center = Offset(x, midY))
        } else {
            drawLine(lineColor, Offset(x, 0f), Offset(x, if (isLast) midY else size.height), stroke)
            drawLine(lineColor, Offset(x, midY), Offset(x + step * 0.55f, midY), stroke)
        }
    }
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

    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        TreeRail(prefix = prefix, isLast = isLast, isRoot = false)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp)
        ) {
            Surface(
                onClick = { open = !open },
                color = Color.Transparent,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.Tools,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
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
                        color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                    Icon(
                        imageVector = if (open) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            if (open) {
                Column(
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 6.dp),
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
    Spacer(Modifier.width(6.dp))
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}
