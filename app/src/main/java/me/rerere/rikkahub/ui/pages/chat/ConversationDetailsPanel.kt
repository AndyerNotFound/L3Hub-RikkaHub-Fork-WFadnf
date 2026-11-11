package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.CloudServer
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.ChatRequestPreview
import me.rerere.rikkahub.data.ai.ChatRequestPreviewInjection
import me.rerere.rikkahub.data.ai.ChatRequestPreviewTool
import me.rerere.rikkahub.data.ai.subagent.dispatchableSubAgents
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.ExecutionPermission
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.ui.components.ai.label
import me.rerere.rikkahub.ui.components.ui.JsonTree
import me.rerere.rikkahub.ui.components.ui.glassSurface
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.pages.extensions.workspace.statusLabel
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.workspace.WorkspaceShellStatus

private val DetailsPanelShape = RoundedCornerShape(22.dp)
private const val DetailsPanelHeightFraction = 0.75f
private val DetailsLabelWidth = 52.dp

/** 会话详情里新加的三块内容：工具定义、工具说明、当前提示词；由 ChatPage 按需加载 */
internal sealed interface RequestPreviewUiState {
    data object Idle : RequestPreviewUiState
    data object Loading : RequestPreviewUiState
    data class Ready(val preview: ChatRequestPreview) : RequestPreviewUiState
    data class Failed(val message: String) : RequestPreviewUiState
}

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
    previewState: RequestPreviewUiState,
    hazeState: HazeState,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    // 嵌在横屏右侧栏里时不再套一层底板：外面已经有玻璃底和关法，
    // 这里只把内容铺满，标题行的收起图标换成关闭
    embedded: Boolean = false,
    // 右栏的标签条已经写着标题，内容里不再重复一行（关闭也在标签上的 ×）
    showHeader: Boolean = true,
) {
    // 竖屏浮层固定占全屏高度的 75%，内容在面板内部滚动，不再挤压聊天区
    val panelHeight = LocalConfiguration.current.screenHeightDp.dp * DetailsPanelHeightFraction
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
    val previewInjections = (previewState as? RequestPreviewUiState.Ready)?.preview?.injections

    Surface(
        modifier = if (embedded) {
            modifier.fillMaxSize()
        } else {
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                // 浮层现在压在聊天内容上，聊天正文不在 haze 源里，太透会和面板小字糊在一起；
                // 下限提到 0.88，和选字浮层一致，留一点玻璃质感但保证正文读得清
                .glassSurface(
                    shape = DetailsPanelShape,
                    baseColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    hazeState = hazeState,
                    minAlpha = 0.88f,
                )
        },
        shape = if (embedded) RectangleShape else DetailsPanelShape,
        color = if (embedded || LocalSettings.current.displaySetting.enableBlurEffect) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = (if (embedded) Modifier.fillMaxSize() else Modifier.height(panelHeight))
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

            DetailsSection(
                title = stringResource(R.string.chat_page_details_session),
                // 面板有标题行时用一条分割线把标题和内容分开；嵌在右栏时顶部直接开始，不画
                showDivider = showHeader,
            ) {
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

            DetailsSection(
                title = stringResource(R.string.chat_page_details_workspaces),
                trailing = boundWorkspaces.size.takeIf { it > 0 }?.toString(),
            ) {
                if (boundWorkspaces.isEmpty()) {
                    PlaceholderText(stringResource(R.string.chat_page_details_no_workspace))
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

            DetailsSection(title = stringResource(R.string.chat_page_details_tools)) {
                if (toolTags.isEmpty()) {
                    PlaceholderText(stringResource(R.string.chat_page_details_no_tools))
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

            RequestPreviewSections(previewState)

            if (previewInjections != null &&
                (previewInjections.isNotEmpty() ||
                    assistant.modeInjectionIds.isNotEmpty() ||
                    assistant.lorebookIds.isNotEmpty())
            ) {
                DetailsSection(
                    title = stringResource(R.string.chat_page_details_injection),
                    trailing = previewInjections.size.takeIf { it > 0 }?.toString(),
                ) {
                    if (previewInjections.isEmpty()) {
                        PlaceholderText(stringResource(R.string.chat_page_details_injection_empty))
                    } else {
                        previewInjections.forEach { injection ->
                            key(injection.name, injection.position, injection.content) {
                                InjectionRow(injection)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 工具定义 / 工具说明 / 当前提示词三块；加载失败时只报一次，不重复三遍 */
@Composable
private fun RequestPreviewSections(state: RequestPreviewUiState) {
    when (state) {
        RequestPreviewUiState.Idle,
        RequestPreviewUiState.Loading -> DetailsSection(
            title = stringResource(R.string.chat_page_details_tool_definitions),
        ) {
            PlaceholderText(stringResource(R.string.chat_page_details_preview_loading))
        }

        is RequestPreviewUiState.Failed -> DetailsSection(
            title = stringResource(R.string.chat_page_details_tool_definitions),
        ) {
            PlaceholderText(
                stringResource(R.string.chat_page_details_preview_failed, state.message),
            )
        }

        is RequestPreviewUiState.Ready -> {
            val preview = state.preview
            DetailsSection(
                title = stringResource(R.string.chat_page_details_tool_definitions),
                trailing = preview.tools.size.takeIf { it > 0 }?.toString(),
            ) {
                if (preview.tools.isEmpty()) {
                    PlaceholderText(stringResource(R.string.chat_page_details_tool_definitions_empty))
                } else {
                    preview.tools.forEach { tool ->
                        key(tool.name) { ToolDefinitionRow(tool) }
                    }
                }
            }

            DetailsSection(
                title = stringResource(R.string.chat_page_details_tool_description),
                trailing = preview.tools.size.takeIf { it > 0 }?.toString(),
            ) {
                if (preview.tools.isEmpty()) {
                    PlaceholderText(stringResource(R.string.chat_page_details_tool_description_empty))
                } else {
                    preview.tools.forEach { tool ->
                        key(tool.name) { ToolDescriptionRow(tool) }
                    }
                }
            }

            DetailsSection(
                title = stringResource(R.string.chat_page_details_prompt),
                trailing = preview.systemPrompt.length
                    .takeIf { it > 0 }
                    ?.let { stringResource(R.string.chat_page_details_prompt_length, it.formatNumber()) },
            ) {
                if (preview.systemPrompt.isBlank()) {
                    PlaceholderText(stringResource(R.string.chat_page_details_prompt_empty))
                } else {
                    SelectionContainer {
                        Text(
                            text = preview.systemPrompt,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetbrainsMono,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolDefinitionRow(tool: ChatRequestPreviewTool) {
    var expanded by rememberSaveable(tool.name) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ExpandRowHeader(
            expanded = expanded,
            title = tool.name,
            onClick = { expanded = !expanded },
        )
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 20.dp, top = 2.dp)) {
                val parameters = tool.parameters
                if (parameters == null) {
                    PlaceholderText(stringResource(R.string.chat_page_details_tool_no_parameters))
                } else {
                    JsonTree(
                        json = parameters,
                        initialExpandLevel = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolDescriptionRow(tool: ChatRequestPreviewTool) {
    var expanded by rememberSaveable(tool.name) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ExpandRowHeader(
            expanded = expanded,
            title = tool.name,
            onClick = { expanded = !expanded },
        )
        Text(
            text = tool.description.ifBlank {
                stringResource(R.string.chat_page_details_tool_no_description)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 20.dp),
        )
        AnimatedVisibility(visible = expanded && tool.instructions.isNotBlank()) {
            Column(
                modifier = Modifier.padding(start = 20.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.chat_page_details_tool_instructions),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = tool.instructions,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = JetbrainsMono,
                )
            }
        }
    }
}

@Composable
private fun InjectionRow(injection: ChatRequestPreviewInjection) {
    var expanded by rememberSaveable(injection.name, injection.position, injection.content) {
        mutableStateOf(false)
    }
    val source = stringResource(
        if (injection.isLorebook) {
            R.string.chat_page_details_injection_source_lorebook
        } else {
            R.string.chat_page_details_injection_source_mode
        }
    )
    val position = injection.position.displayLabel(injection.depth)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ExpandRowHeader(
            expanded = expanded,
            title = injection.name.ifBlank {
                stringResource(R.string.chat_page_details_injection_untitled)
            },
            trailing = "$source · $position",
            onClick = { expanded = !expanded },
        )
        AnimatedVisibility(visible = expanded) {
            Text(
                text = injection.content.ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = JetbrainsMono,
                modifier = Modifier.padding(start = 20.dp),
            )
        }
    }
}

@Composable
private fun ExpandRowHeader(
    expanded: Boolean,
    title: String,
    onClick: () -> Unit,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) HugeIcons.ArrowDown01 else HugeIcons.ArrowRight01,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            fontFamily = JetbrainsMono,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InjectionPosition.displayLabel(depth: Int): String = when (this) {
    InjectionPosition.BEFORE_SYSTEM_PROMPT ->
        stringResource(R.string.chat_page_details_injection_position_before_system)
    InjectionPosition.AFTER_SYSTEM_PROMPT -> stringResource(R.string.chat_page_details_injection_position_after_system)
    InjectionPosition.TOP_OF_CHAT -> stringResource(R.string.chat_page_details_injection_position_top_of_chat)
    InjectionPosition.BOTTOM_OF_CHAT -> stringResource(R.string.chat_page_details_injection_position_bottom_of_chat)
    InjectionPosition.AT_DEPTH -> stringResource(R.string.chat_page_details_injection_position_at_depth, depth)
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
    showDivider: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(12.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
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

@Composable
private fun PlaceholderText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
