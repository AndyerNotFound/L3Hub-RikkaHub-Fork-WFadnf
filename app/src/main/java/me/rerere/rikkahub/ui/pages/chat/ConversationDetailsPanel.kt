package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.CloudServer
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.subagent.dispatchableSubAgents
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.ExecutionPermission
import me.rerere.rikkahub.ui.components.ai.label
import me.rerere.rikkahub.ui.components.ui.canGlassPane
import me.rerere.rikkahub.ui.components.ui.glassSurface
import me.rerere.rikkahub.ui.pages.extensions.workspace.statusLabel
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.workspace.WorkspaceShellStatus

private val DetailsPanelShape = RoundedCornerShape(22.dp)
private val DetailsPanelMaxHeight = 340.dp
private val DetailsLabelWidth = 52.dp

/**
 * 顶栏下方展开的会话详情：当前会话固定下来的模型与推理等级、
 * 实际注册进这次会话的工具（本地工具、记忆、搜索、工作区、技能、MCP），以及绑定的工作区。
 *
 * 这里只做展示，改动仍走各自原来的入口。
 */
@Composable
internal fun ConversationDetailsPanel(
    assistant: Assistant,
    model: Model?,
    settings: Settings,
    workspaces: List<WorkspaceEntity>,
    folderWorkspaceName: String?,
    contextTokens: Int?,
    messageCount: Int,
    lorebookInjectionCount: Int,
    hazeState: HazeState,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    // 嵌在横屏右侧栏里时不再套一层底板：外面已经有玻璃底和关法，
    // 这里只把内容铺满，标题行的收起图标换成关闭
    embedded: Boolean = false,
    // 右栏的标签条已经写着标题，内容里不再重复一行（关闭也在标签上的 ×）
    showHeader: Boolean = true,
) {
    // 面板最多占半屏，超出的部分在面板内部滚动，不去挤聊天列表
    val maxHeight = minOf(DetailsPanelMaxHeight, LocalConfiguration.current.screenHeightDp.dp * 0.4f)
    val boundWorkspaces = boundWorkspacesOf(workspaces, assistant)
    // 只剩真正会注册工具的服务器：全部工具被关掉的服务器在 McpManager 里不会产出任何工具
    val mcpServers = settings.mcpServers
        .filter { it.commonOptions.enable && it.id in assistant.mcpServers }
        .mapNotNull { server ->
            val enabledTools = server.commonOptions.tools.count { it.enable }
            if (enabledTools == 0) {
                null
            } else {
                server.commonOptions.name.ifBlank { "MCP" } to enabledTools
            }
        }
    val usableWorkspace = boundWorkspaces.any { it.shellStatus == WorkspaceShellStatus.READY.name }
    val subAgentDispatchable =
        dispatchableSubAgents(assistant, settings.assistants).isNotEmpty()
    val toolTags = buildToolTags(
        assistant = assistant,
        model = model,
        mcpServers = mcpServers,
        usableWorkspace = usableWorkspace,
        subAgentDispatchable = subAgentDispatchable,
    )

    val provider = model?.findProvider(settings.providers)
    val modelName = model?.displayName?.ifBlank { model.modelId }
        ?: stringResource(R.string.chat_page_details_no_model)
    val contextLimit = model?.maxContextTokens
    val contextText = if (contextLimit != null && contextLimit > 0) {
        stringResource(
            R.string.chat_page_context_meter_absolute,
            (contextTokens ?: 0).formatNumber(),
            contextLimit.formatNumber(),
        )
    } else {
        contextTokens?.formatNumber() ?: "—"
    }

    Surface(
        modifier = if (embedded) {
            modifier.fillMaxSize()
        } else {
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                // 面板只是把顶栏下面的内容往下推，底下没有正文，浓度基本交给「玻璃透明度」；
                // 留 0.5 的下限是因为这里全是小字，压在彩色背景上再透就看不清了
                .glassSurface(
                    shape = DetailsPanelShape,
                    baseColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    hazeState = hazeState,
                    radius = 16.dp,
                    minAlpha = 0.5f,
                )
        },
        shape = if (embedded) RectangleShape else DetailsPanelShape,
        color = if (embedded || canGlassPane(hazeState)) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = (if (embedded) Modifier.fillMaxSize() else Modifier.heightIn(max = maxHeight))
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showHeader) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.chat_page_details_title),
                            style = MaterialTheme.typography.titleSmallEmphasized,
                        )
                        if (provider != null) {
                            Text(
                                text = provider.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = onCollapse, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (embedded) HugeIcons.Cancel01 else HugeIcons.ArrowUp01,
                            contentDescription = stringResource(
                                if (embedded) R.string.close else R.string.chat_page_details_collapse,
                            ),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            DetailsSection(title = stringResource(R.string.chat_page_details_session)) {
                DetailLine(
                    label = stringResource(R.string.chat_page_details_assistant),
                    value = assistant.name.ifBlank { stringResource(R.string.chat_page_details_no_assistant) },
                )
                DetailLine(label = stringResource(R.string.chat_page_details_model), value = modelName)
                DetailLine(
                    label = stringResource(R.string.chat_page_details_reasoning),
                    value = assistant.reasoningLevel.label(),
                )
                DetailLine(
                    label = stringResource(R.string.chat_page_details_context),
                    value = contextText,
                )
                DetailLine(
                    label = stringResource(R.string.chat_page_details_messages),
                    value = stringResource(R.string.chat_page_message_count, messageCount),
                )
            }

            DetailsSection(title = stringResource(R.string.chat_page_details_tools)) {
                if (toolTags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.chat_page_details_no_tools),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        toolTags.forEach { tag -> ToolTag(tag) }
                    }
                }
            }

            DetailsSection(
                title = stringResource(R.string.chat_page_details_workspaces),
                trailing = boundWorkspaces.size.takeIf { it > 0 }?.toString(),
            ) {
                if (boundWorkspaces.isEmpty()) {
                    Text(
                        text = stringResource(R.string.chat_page_details_no_workspace),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    boundWorkspaces.forEach { workspace ->
                        WorkspaceLine(workspace = workspace)
                    }
                }
                if (folderWorkspaceName != null) {
                    Text(
                        text = if (folderWorkspaceName.isBlank()) {
                            stringResource(R.string.chat_page_details_workspace_folder_unknown)
                        } else {
                            stringResource(R.string.chat_page_details_workspace_folder, folderWorkspaceName)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (assistant.modeInjectionIds.isNotEmpty() || assistant.lorebookIds.isNotEmpty() || lorebookInjectionCount > 0) {
                DetailsSection(title = stringResource(R.string.chat_page_details_injection)) {
                    Text(
                        text = stringResource(
                            R.string.chat_page_details_injection_value,
                            assistant.modeInjectionIds.size,
                            assistant.lorebookIds.size,
                            lorebookInjectionCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 工作区列表按助手绑定的顺序给出，主工作区排在最前。 */
private fun boundWorkspacesOf(
    workspaces: List<WorkspaceEntity>,
    assistant: Assistant,
): List<WorkspaceEntity> {
    val byId = workspaces.associateBy { it.id }
    val primary = assistant.primaryWorkspaceId?.toString()
    return assistant.allWorkspaceIds
        .mapNotNull { byId[it.toString()] }
        .sortedBy { if (it.id == primary) 0 else 1 }
}

/** 这次会话会注册哪些工具，和 ChatToolFactory 的装配顺序对应。 */
@Composable
private fun buildToolTags(
    assistant: Assistant,
    model: Model?,
    mcpServers: List<Pair<String, Int>>,
    usableWorkspace: Boolean,
    subAgentDispatchable: Boolean,
): List<String> {
    val tags = mutableListOf<String>()
    if (assistant.enableMemory) {
        tags += stringResource(
            if (assistant.enableMemoryWrite) {
                R.string.chat_page_details_tool_memory
            } else {
                R.string.chat_page_details_tool_memory_read_only
            }
        )
    }
    // createWorkspaceTools 只接受 shell 就绪的工作区，其余 id 一个工具都不会注册
    if (assistant.allWorkspaceIds.isNotEmpty() && usableWorkspace) {
        val permission = if (assistant.planMode) {
            stringResource(R.string.assistant_page_execution_permission_read_only)
        } else {
            assistant.executionPermission.displayLabel()
        }
        tags += stringResource(R.string.chat_page_details_tool_workspace, permission)
        tags += stringResource(R.string.chat_page_details_tool_todo)
    }
    assistant.localTools.forEach { option ->
        // 派发工具还要有可派发的目标，光勾了开关并不生成 dispatch_sub_agent
        if (option == LocalToolOption.SubAgent && !subAgentDispatchable) return@forEach
        tags += option.displayLabel()
    }
    if (assistant.enableWebSearch && model?.tools?.contains(BuiltInTools.Search) != true) {
        tags += stringResource(R.string.chat_page_details_tool_web_search)
    }
    model?.tools?.forEach { tool ->
        tags += stringResource(
            when (tool) {
                BuiltInTools.Search -> R.string.chat_page_details_tool_builtin_search
                BuiltInTools.UrlContext -> R.string.chat_page_details_tool_builtin_url
                BuiltInTools.ImageGeneration -> R.string.chat_page_details_tool_builtin_image
            }
        )
    }
    if (assistant.enableRecentChatsReference) {
        tags += stringResource(R.string.chat_page_details_tool_recent_chats)
    }
    assistant.enabledSkills.sorted().forEach { skill ->
        tags += stringResource(R.string.chat_page_details_tool_skill, skill)
    }
    // 计数是服务器里实际启用的工具数，列表已经滤掉 0 的服务器
    mcpServers.forEach { (name, count) ->
        tags += stringResource(R.string.chat_page_details_tool_mcp, name) + " ($count)"
    }
    return tags
}

@Composable
private fun LocalToolOption.displayLabel(): String = stringResource(
    when (this) {
        LocalToolOption.JavascriptEngine -> R.string.assistant_page_local_tools_javascript_engine_title
        LocalToolOption.TimeInfo -> R.string.assistant_page_local_tools_time_info_title
        LocalToolOption.Clipboard -> R.string.assistant_page_local_tools_clipboard_title
        LocalToolOption.Tts -> R.string.assistant_page_local_tools_tts_title
        LocalToolOption.AskUser -> R.string.assistant_page_local_tools_ask_user_title
        LocalToolOption.ScreenTime -> R.string.assistant_page_local_tools_screen_time_title
        LocalToolOption.Calendar -> R.string.assistant_page_local_tools_calendar_title
        LocalToolOption.ChartDisplay -> R.string.assistant_page_local_tools_chart_display_title
        LocalToolOption.PhoneUse -> R.string.assistant_page_local_tools_phone_use_title
        LocalToolOption.SubAgent -> R.string.assistant_page_local_tools_sub_agent_title
    }
)

@Composable
private fun ExecutionPermission.displayLabel(): String = stringResource(
    when (this) {
        ExecutionPermission.READ_ONLY -> R.string.assistant_page_execution_permission_read_only
        ExecutionPermission.READ_WRITE -> R.string.assistant_page_execution_permission_read_write
        ExecutionPermission.APPROVAL -> R.string.assistant_page_execution_permission_approval
        ExecutionPermission.FULL -> R.string.assistant_page_execution_permission_full
    }
)

@Composable
private fun DetailsSection(
    title: String,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (trailing != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        content()
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(DetailsLabelWidth),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WorkspaceLine(workspace: WorkspaceEntity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (workspace.isSsh()) HugeIcons.CloudServer else HugeIcons.Folder01,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = workspace.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 本地已就绪的工作区只给路径；未就绪的补一句状态，说明为什么没有工作区工具
            val status = workspace.statusLabel()
            val showStatus = workspace.isSsh() || workspace.shellStatus != WorkspaceShellStatus.READY.name
            Text(
                text = if (showStatus) "${workspace.workspaceDir()} · $status" else workspace.workspaceDir(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ToolTag(text: String) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
        )
    }
}
