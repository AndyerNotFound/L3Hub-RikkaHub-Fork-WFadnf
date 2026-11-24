package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.http.jsonObjectOrNull
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.TODO_WRITE_TOOL_NAME
import me.rerere.rikkahub.data.ai.tools.WORKSPACE_GIT_TOOL_NAME
import me.rerere.rikkahub.data.ai.tools.local.SUB_AGENT_AWAIT_TOOL_NAME
import me.rerere.rikkahub.data.ai.tools.local.SUB_AGENT_LIST_TOOL_NAME
import me.rerere.rikkahub.data.ai.tools.local.SUB_AGENT_TOOL_NAME
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.richtext.ZoomableAsyncImage
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import me.rerere.ui.components.FormItem

/**
 * 工具调用的渲染上下文, 预解析好工具入参与输出, 避免各渲染器重复解析
 */
data class ToolUIContext(
    val tool: UIMessagePart.Tool,
    /** 工具入参 ([UIMessagePart.Tool.input] 的 JSON 解析结果) */
    val arguments: JsonElement,
    /** 输出文本部件解析出的 JSON, 工具未执行时为 null */
    val content: JsonElement?,
    /** 该工具调用是否在生成中 */
    val loading: Boolean,
    /** 发起该工具调用的助手, 拿不到时为 null */
    val assistant: Assistant? = null,
) {
    /**
     * 入参解析出的单列字段, 供各渲染器展示参数用
     *
     * 解析结果跟着 context 一起被 remember 缓存 (见 ChatMessageTools), 不会每帧重算;
     * 纯文本入参、数组入参或非法 JSON 时为 null, 调用方回落到原有展示
     */
    val argFields: List<ToolArgField>? by lazy {
        ToolArgsPresentation.parse(tool.toolName, tool.input)
    }
}

/**
 * 单个工具的 UI 渲染器
 *
 * 在 [ToolUIRegistry] 注册后, 聊天消息中对应的工具调用将使用该渲染器展示;
 * 未注册的工具沿用默认渲染：已知内置工具显示本地化标题，其余显示工具原名。
 */
/**
 * 详情内容的高度。
 *
 * BottomSheet 里留 20% 给手势区；右侧看板里没有这个顾虑，铺满更好看。
 */
@Composable
fun Modifier.toolPreviewSize(): Modifier =
    if (LocalToolPreviewFullHeight.current) fillMaxSize() else fillMaxHeight(0.8f)

interface ToolUIRenderer {
    /** 渲染器对应的工具名 */
    val toolName: String

    /** 折叠步骤的图标 */
    fun icon(context: ToolUIContext): ImageVector = HugeIcons.Tools

    /** 折叠步骤的标题 */
    @Composable
    fun title(context: ToolUIContext): String {
        val name = context.tool.toolName
        val titleRes = when (name) {
            "conversation_read" -> R.string.chat_message_tool_conversation_read
            "eval_javascript" -> R.string.chat_message_tool_eval_javascript
            TODO_WRITE_TOOL_NAME -> R.string.chat_message_tool_todo_write
            SUB_AGENT_TOOL_NAME -> R.string.assistant_page_local_tools_sub_agent_title
            SUB_AGENT_AWAIT_TOOL_NAME -> R.string.chat_message_tool_await_sub_agent
            SUB_AGENT_LIST_TOOL_NAME -> R.string.chat_message_tool_list_agents
            "workspace_list" -> R.string.workspace_detail_tool_list
            "workspace_glob" -> R.string.workspace_detail_tool_glob
            "workspace_grep" -> R.string.workspace_detail_tool_grep
            "workspace_repo_map" -> R.string.workspace_detail_tool_repo_map
            WORKSPACE_GIT_TOOL_NAME -> R.string.chat_message_tool_workspace_git
            "workspace_shell_output" -> R.string.chat_message_tool_workspace_shell_output
            "workspace_shell_kill" -> R.string.chat_message_tool_workspace_shell_kill
            else -> null
        }
        return if (titleRes != null) {
            stringResource(titleRes)
        } else {
            stringResource(R.string.chat_message_tool_call_generic, name)
        }
    }

    /** 步骤展开时是否显示内联摘要 */
    fun hasSummary(context: ToolUIContext): Boolean = false

    /** 步骤展开时的内联摘要 */
    @Composable
    fun Summary(context: ToolUIContext) {
    }

    /** 点击步骤后的详情, 渲染在 BottomSheet 内 */
    @Composable
    fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        DefaultToolPreview(context = context)
    }
}

/** 未注册工具的内联摘要最多列出几条参数 */
private const val MAX_INLINE_FIELDS = 3

/** 未注册工具使用的默认渲染器: 内联摘要列出可解析的参数, 详情见 [DefaultToolPreview] */
private object DefaultToolUIRenderer : ToolUIRenderer {
    override val toolName: String get() = ""

    override fun hasSummary(context: ToolUIContext): Boolean = context.argFields != null

    @Composable
    override fun Summary(context: ToolUIContext) {
        val fields = context.argFields ?: return
        ToolArgsFieldList(fields = fields, compact = true, maxFields = MAX_INLINE_FIELDS)
    }
}

/**
 * 工具 UI 渲染器注册表, 为新工具定制渲染时在 [renderers] 中注册即可
 */
object ToolUIRegistry {
    private val renderers: Map<String, ToolUIRenderer> = listOf(
        SearchWebToolUI,
        ScrapeWebToolUI,
        GetTimeInfoToolUI,
        ClipboardToolUI,
        TextToSpeechToolUI,
        GetScreenTimeToolUI,
        CalendarQueryToolUI,
        CalendarCreateToolUI,
        ChartDisplayToolUI,
        UseSkillToolUI,
        RecentChatsToolUI,
        ConversationSearchToolUI,
        EditFileToolUI,
        ReadFileToolUI,
        WriteFileToolUI,
        ShellToolUI,
        ApplyPatchToolUI,
        WorkspaceTestToolUI,
        PhoneUseToolUI,
    ).plus(MemoryFileToolUI.all).associateBy { it.toolName }

    /** 查找工具对应的渲染器, 未注册时返回默认渲染器 */
    fun resolve(toolName: String): ToolUIRenderer = renderers[toolName] ?: DefaultToolUIRenderer
}

internal fun JsonElement?.getStringContent(key: String): String? =
    this?.jsonObjectOrNull?.get(key)?.jsonPrimitiveOrNull?.contentOrNull

private fun String.isJsonLike(): Boolean {
    val first = trimStart().firstOrNull()
    return first == '{' || first == '['
}

/**
 * 默认工具详情: 入参按单列字段展示 (解析不出字段时回落到 JSON 高亮),
 * 输出按内容识别: JSON 格式化、Markdown 渲染成富文本、纯文本可选中
 *
 * @param headerActions 标题栏右侧的附加操作区
 */
@Composable
fun DefaultToolPreview(
    context: ToolUIContext,
    headerActions: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .toolPreviewSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.chat_message_tool_call_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            headerActions?.invoke()
        }
        FormItem(
            label = {
                Text(stringResource(R.string.chat_message_tool_call_label, context.tool.toolName))
            }
        ) {
            val fields = context.argFields
            // 解析不出字段 (纯文本命令、非法 JSON) 时的兜底文本, 跟着入参一起缓存
            val fallback = context.tool.input
            if (fields != null) {
                ToolArgsFieldList(fields = fields)
            } else if (fallback.isNotBlank()) {
                ToolRichText(
                    text = fallback,
                    modifier = Modifier.fillMaxWidth(),
                    languageHint = if (fallback.isJsonLike()) "json" else "plaintext",
                )
            }
        }
        if (context.tool.output.isNotEmpty()) {
            FormItem(
                label = {
                    Text(stringResource(R.string.chat_message_tool_call_result))
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    context.tool.output.fastForEach { part ->
                        when (part) {
                            is UIMessagePart.Text -> if (part.text.isNotBlank()) {
                                ToolRichText(
                                    text = part.text,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            is UIMessagePart.Image -> ZoomableAsyncImage(
                                model = part.url,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            else -> {}
                        }
                    }
                }
            }
        }
    }
}
