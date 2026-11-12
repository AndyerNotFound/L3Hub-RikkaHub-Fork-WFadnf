package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.tools.local.ASK_USER_TOOL_NAME
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.repository.memoryId
import me.rerere.rikkahub.data.skills.SkillManager
import me.rerere.rikkahub.service.BackgroundShellManager
import me.rerere.workspace.WorkspaceShellStatus
import java.io.File
import kotlin.uuid.Uuid

private const val TAG = "ChatToolFactory"

/**
 * 子代理会话没有人在旁边确认，工具调用一律按完全权限直接执行。
 * `ask_user` 需要真人回答，子代理只能自己决定，这里直接给模型回一条提示。
 */
internal fun Tool.withAutoApproval(): Tool {
    if (name == ASK_USER_TOOL_NAME) {
        return copy(
            needsApproval = { false },
            execute = {
                listOf(
                    UIMessagePart.Text(
                        "This is a sub-agent conversation, so there is no user to answer questions. Decide on your own from the information you already have and do not call ask_user."
                    )
                )
            },
        )
    }
    return copy(needsApproval = { false })
}

private const val MAX_TOOL_OUTPUT_CHARS = 32 * 1024
private const val TOOL_OUTPUT_PREVIEW_CHARS = 4 * 1024

// 搜索结果的体积由用户设置的结果数决定，且结果列表 UI 与引用跳转都依赖完整的 JSON 结构，不参与截断
private val TOOLS_WITHOUT_OUTPUT_TRUNCATION = setOf("search_web")

internal fun shouldUseExternalWebSearch(assistant: Assistant, model: Model): Boolean {
    return assistant.enableWebSearch && BuiltInTools.Search !in model.tools
}

class InvalidMcpServerNamesException(val names: List<String>) :
    IllegalStateException("Invalid MCP server names: ${names.joinToString(", ")}")

/** Creates the complete tool set for one generation run, including approval resumption. */
class ChatToolFactory(
    private val context: Context,
    private val memoryRepository: MemoryRepository,
    private val conversationRepository: ConversationRepository,
    private val localTools: LocalTools,
    private val mcpManager: McpManager,
    private val skillManager: SkillManager,
    private val workspaceRepository: WorkspaceRepository,
    private val backgroundShellManager: BackgroundShellManager,
) {
    suspend fun createTools(
        settings: Settings,
        assistant: Assistant,
        model: Model,
        workspaceCwds: Map<String, String> = emptyMap(),
        conversationId: Uuid? = null,
        isSubAgentConversation: Boolean = false,
        autoApproveTools: Boolean = false,
    ): List<Tool> {
        val workspaceTools = createWorkspaceTools(
            workspaceIds = assistant.allWorkspaceIds.map { it.toString() },
            workspaceRepository = workspaceRepository,
            cwds = workspaceCwds,
            conversationId = conversationId,
            planMode = assistant.planMode,
            executionPermission = assistant.executionPermission,
            backgroundShellManager = backgroundShellManager,
        )
        val tools = buildList {
            if (assistant.enableMemory) {
                addAll(
                    buildMemoryTools(
                        repository = memoryRepository,
                        assistantId = assistant.memoryId,
                        allowWrite = assistant.enableMemoryWrite,
                    )
                )
            }
            if (shouldUseExternalWebSearch(assistant, model)) {
                addAll(createSearchTools(settings))
            }
            addAll(
                localTools.getTools(
                    // 派发能力只由两个独立的东西决定：助手本地工具里勾了「派发子代理」，或者这个会话自己就是子代理会话
                    // （子代理可以继续派发下一层，不用它自己再开一次）。助手上的 isSubAgent 只表示「能被别人派发」，
                    // 这种助手也常被直接当主助手聊天，不能因此白送 dispatch_sub_agent
                    options = if (isSubAgentConversation) {
                        (assistant.localTools + LocalToolOption.SubAgent).distinct()
                    } else {
                        assistant.localTools
                    },
                    conversationId = conversationId,
                    assistants = settings.assistants,
                    dispatcher = assistant,
                )
            )
            if (assistant.enableRecentChatsReference) {
                addAll(createConversationTools(conversationRepository, assistant.id))
            }
            addAll(workspaceTools)
            // 任务清单是 Coding Agent 的东西：只有绑了工作区才注册，
            // 纯聊天不该凭空多出一个 todo 工具让模型给自己派活
            if (conversationId != null && assistant.allWorkspaceIds.isNotEmpty()) {
                addAll(createTodoTools(conversationRepository, conversationId))
            }
            if (assistant.enabledSkills.isNotEmpty()) {
                addAll(
                    createSkillTools(
                        enabledSkills = assistant.enabledSkills,
                        allSkills = skillManager.listSkills(),
                    )
                )
            }

            val mcpTools = mcpManager.getAllAvailableTools(assistant)
            val invalidNames = mcpTools
                .map { it.second }
                .distinct()
                .filter { name -> name.isEmpty() || !name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' } }
            if (invalidNames.isNotEmpty()) {
                throw InvalidMcpServerNamesException(invalidNames)
            }
            mcpTools.forEach { (serverId, serverName, tool) ->
                add(
                    Tool(
                        name = "mcp__${serverName}__${tool.name}",
                        description = tool.description ?: "",
                        parameters = { tool.inputSchema },
                        needsApproval = { tool.needsApproval },
                        execute = { mcpManager.callTool(serverId, tool.name, it.jsonObject) },
                    )
                )
            }
        }
        // 过长的输出存成文件让模型用 shell 按需读取；没有 shell 时模型读不到文件，只能原样返回。
        // 文件放在本机, 只有本地 Rootfs 挂载了 /tool_outputs, 远程工作区同样读不到
        val canReadToolOutputs = workspaceTools.isNotEmpty() && assistant.allWorkspaceIds.any { workspaceId ->
            workspaceRepository.getById(workspaceId.toString())?.capabilities()?.rootfs == true
        }
        val truncated = if (canReadToolOutputs) tools.map(::withOutputTruncation) else tools
        val overridden = applyToolOverrides(truncated, settings.toolOverrides)
        return if (autoApproveTools) overridden.map { it.withAutoApproval() } else overridden
    }

    /**
     * 「工具覆写」页的目录：不要求某个助手真的启用了这些工具，按应用当前配置把能生成的工具定义列全。
     * 返回的是定义快照，不含执行入口，也不会连接 MCP 服务器。
     */
    suspend fun createToolCatalog(settings: Settings): List<ToolCatalogEntry> {
        val assistant = settings.assistants.firstOrNull { it.id == settings.assistantId }
            ?: settings.assistants.firstOrNull()
            ?: return emptyList()
        val catalogConversationId = Uuid.random()
        val catalogModel = Model()
        val readyWorkspaces = workspaceRepository.listFlow().first()
            .filter { it.shellStatus == WorkspaceShellStatus.READY.name }
        val workspaceTools = if (readyWorkspaces.isEmpty()) {
            createWorkspaceCatalogTools(
                workspaceRepository = workspaceRepository,
                backgroundShellManager = backgroundShellManager,
            )
        } else {
            createWorkspaceTools(
                workspaceIds = readyWorkspaces.map { it.id },
                workspaceRepository = workspaceRepository,
                conversationId = catalogConversationId,
                backgroundShellManager = backgroundShellManager,
            )
        }

        return buildList {
            localTools.getCatalogTools(
                conversationId = catalogConversationId,
                assistants = settings.assistants,
                dispatcher = assistant,
            ).forEach { add(it.toCatalogEntry(ToolCatalogGroup.LOCAL, catalogModel)) }

            buildMemoryTools(
                repository = memoryRepository,
                assistantId = assistant.memoryId,
                allowWrite = true,
            ).forEach { add(it.toCatalogEntry(ToolCatalogGroup.MEMORY, catalogModel)) }

            createSearchTools(settings).forEach {
                add(it.toCatalogEntry(ToolCatalogGroup.SEARCH, catalogModel))
            }

            createConversationTools(
                conversationRepo = conversationRepository,
                assistantId = assistant.id,
            ).forEach { add(it.toCatalogEntry(ToolCatalogGroup.CONVERSATION, catalogModel)) }

            workspaceTools.forEach { add(it.toCatalogEntry(ToolCatalogGroup.WORKSPACE, catalogModel)) }

            createTodoTools(
                conversationRepository = conversationRepository,
                conversationId = catalogConversationId,
            ).forEach { add(it.toCatalogEntry(ToolCatalogGroup.TODO, catalogModel)) }

            add(
                createSkillToolForCatalog(skillManager.listSkills())
                    .toCatalogEntry(ToolCatalogGroup.SKILLS, catalogModel)
            )

            // MCP 的定义来自上次同步到配置里的快照，不再触发连接
            settings.mcpServers
                .filter { it.commonOptions.enable }
                .forEach { server ->
                    val serverName = server.commonOptions.name.ifBlank { "MCP" }
                    server.commonOptions.tools
                        .filter { it.enable && it.name.isNotBlank() }
                        .forEach { tool ->
                            add(
                                ToolCatalogEntry(
                                    name = "mcp__${server.commonOptions.name}__${tool.name}",
                                    description = tool.description.orEmpty(),
                                    parameters = tool.inputSchema?.let { schema ->
                                        ToolOverrideJson.encodeToJsonElement(InputSchema.serializer(), schema)
                                    },
                                    instructions = "",
                                    group = ToolCatalogGroup.MCP,
                                    groupDetail = serverName,
                                )
                            )
                        }
                }
        }.distinctBy { it.name }
    }

    private fun Tool.toCatalogEntry(
        group: ToolCatalogGroup,
        model: Model,
    ): ToolCatalogEntry {
        val schema = parameters()
        return ToolCatalogEntry(
            name = name,
            description = description,
            parameters = schema?.let { ToolOverrideJson.encodeToJsonElement(InputSchema.serializer(), it) },
            instructions = systemPrompt(model, emptyList()).trim(),
            group = group,
        )
    }

    private fun withOutputTruncation(tool: Tool): Tool {
        if (tool.name in TOOLS_WITHOUT_OUTPUT_TRUNCATION) return tool
        val execute = tool.execute
        return tool.copy(execute = { truncateOutput(execute(it)) })
    }

    private fun truncateOutput(output: List<UIMessagePart>): List<UIMessagePart> {
        val textParts = output.filterIsInstance<UIMessagePart.Text>()
        val totalChars = textParts.sumOf { it.text.length }
        if (totalChars <= MAX_TOOL_OUTPUT_CHARS) return output

        val fileName = "${Uuid.random()}.txt"
        Log.i(TAG, "truncateOutput: saving $totalChars chars to $fileName")
        val fullText = textParts.joinToString("\n") { it.text }
        val outputDir = File(context.filesDir, FileFolders.TOOL_OUTPUTS).apply { mkdirs() }
        File(outputDir, fileName).writeText(fullText)

        return listOf(
            UIMessagePart.Text(
                buildString {
                    appendLine("[Tool output truncated: $totalChars characters total]")
                    appendLine("Full output saved to: /tool_outputs/$fileName")
                    appendLine("Use shell to read: `cat /tool_outputs/$fileName`")
                    appendLine("Use shell to search: `grep \"pattern\" /tool_outputs/$fileName`")
                    appendLine()
                    append(fullText.take(TOOL_OUTPUT_PREVIEW_CHARS))
                }
            )
        ) + output.filter { it !is UIMessagePart.Text }
    }
}
