package me.rerere.rikkahub.data.ai.subagent

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.isAwaitingToolApproval
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.ChatService
import kotlin.uuid.Uuid

const val SUB_AGENT_STATE_RUNNING = "running"
const val SUB_AGENT_STATE_COMPLETED = "completed"
const val SUB_AGENT_STATE_FAILED = "failed"

private const val TAG = "SubAgentRunner"
private const val MAX_DEPTH = 4

/**
 * 把任务交给一个普通助手会话，复用 ChatService 的会话、流式状态和生成生命周期。
 * 会话通过 [Conversation.parentConversationId] 挂在父会话下面，任务树据此渲染。
 */
class SubAgentRunner(
    private val settingsStore: SettingsStore,
    private val conversationRepository: ConversationRepository,
    private val chatServiceProvider: () -> ChatService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun dispatch(
        parentConversationId: Uuid,
        target: String,
        task: String,
        mode: SubAgentDispatchMode,
    ): String {
        val settings = settingsStore.awaitLoaded()
        val candidate = settings.assistants.firstOrNull { assistant ->
            assistant.isSubAgent && (
                assistant.id.toString().equals(target.trim(), ignoreCase = true) ||
                    assistant.name.equals(target.trim(), ignoreCase = true)
                )
        } ?: return "没有找到名为「$target」的子代理，请使用可用列表里的名称。"

        val parent = conversationRepository.getConversationById(parentConversationId)
            ?: return "父会话不存在，无法派发子代理。"

        if (settings.getAssistantOf(parent).id == candidate.id) {
            return "不能把任务派发给当前助手自己，请换一个子代理。"
        }

        val depth = depthOf(parentConversationId)
        if (depth >= MAX_DEPTH) {
            return "子代理层级已达上限（$MAX_DEPTH 层），请直接在当前会话完成任务。"
        }

        val child = Conversation(
            assistantId = candidate.id,
            title = task.trim().lineSequence().firstOrNull()?.take(40)?.ifBlank { "子代理任务" }
                ?: "子代理任务",
            messageNodes = emptyList(),
            parentConversationId = parent.id,
            subAgentTask = task,
            subAgentState = SUB_AGENT_STATE_RUNNING,
        )
        conversationRepository.insertConversation(child)

        val chatService = chatServiceProvider()
        try {
            chatService.sendMessage(
                conversationId = child.id,
                content = listOf(UIMessagePart.Text(task)),
                answer = true,
            )
        } catch (e: Exception) {
            Log.w(TAG, "start sub agent failed", e)
            markFailed(chatService, child.id)
            return "子代理「${candidate.name}」执行失败：${e.message ?: e::class.simpleName}"
        }

        return when (mode) {
            SubAgentDispatchMode.WAIT -> finishChild(chatService, candidate, child)
            SubAgentDispatchMode.BACKGROUND -> {
                scope.launch {
                    val result = finishChild(chatService, candidate, child)
                    runCatching { chatService.deliverSubAgentResult(parent.id, result) }
                        .onFailure { Log.w(TAG, "deliver sub agent result failed", it) }
                }
                "子代理「${candidate.name}」已在后台开始执行，完成后会自动把结果送回当前会话；也可以打开会话 ${child.id} 查看进度。"
            }
        }
    }

    private suspend fun finishChild(
        chatService: ChatService,
        candidate: Assistant,
        child: Conversation,
    ): String {
        val conversation = try {
            chatService.awaitGeneration(child.id)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                markFailed(chatService, child.id)
            }
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "sub agent run failed", e)
            markFailed(chatService, child.id)
            return "子代理「${candidate.name}」执行失败：${e.message ?: e::class.simpleName}"
        }

        val awaitingApproval = conversation.isAwaitingToolApproval
        val answer = conversation.currentMessages
            .lastOrNull { it.role == MessageRole.ASSISTANT }
            ?.toText()
            ?.trim()
            .orEmpty()

        return if (awaitingApproval) {
            updateState(chatService, child.id, SUB_AGENT_STATE_FAILED)
            "子代理「${candidate.name}」停在了需要用户确认的工具上，请打开会话 ${child.id} 审批后继续。"
        } else if (answer.isBlank()) {
            updateState(chatService, child.id, SUB_AGENT_STATE_FAILED)
            "子代理「${candidate.name}」没有产生回复，会话 ${child.id} 可在任务树里查看。"
        } else {
            updateState(chatService, child.id, SUB_AGENT_STATE_COMPLETED)
            buildString {
                appendLine("子代理「${candidate.name}」已完成，会话 ID：${child.id}")
                appendLine("完整过程可以在那个会话里查看，历史会话和任务树都能找到。")
                appendLine()
                append(answer)
            }
        }
    }

    private suspend fun markFailed(chatService: ChatService, conversationId: Uuid) {
        updateState(chatService, conversationId, SUB_AGENT_STATE_FAILED)
    }

    private suspend fun updateState(chatService: ChatService, conversationId: Uuid, state: String) {
        runCatching {
            chatService.updateSubAgentState(conversationId, state)
        }.onFailure {
            Log.w(TAG, "update sub agent state failed", it)
        }
    }

    private suspend fun depthOf(conversationId: Uuid): Int {
        var current = conversationRepository.getConversationById(conversationId)
        var depth = 0
        while (depth < 32) {
            val parentId = current?.parentConversationId ?: break
            depth++
            current = conversationRepository.getConversationById(parentId)
        }
        return depth
    }
}
