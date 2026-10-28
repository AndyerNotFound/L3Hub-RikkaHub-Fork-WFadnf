package me.rerere.rikkahub.data.ai.subagent

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import me.rerere.ai.agent.AgentEvent
import me.rerere.ai.agent.runAgent
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.ChatRequestFactory
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.repository.ConversationRepository
import java.time.Instant
import kotlin.uuid.Uuid

const val SUB_AGENT_STATE_RUNNING = "running"
const val SUB_AGENT_STATE_COMPLETED = "completed"
const val SUB_AGENT_STATE_FAILED = "failed"

private const val TAG = "SubAgentRunner"
private const val MAX_DEPTH = 4
private const val SAVE_INTERVAL_MS = 700L

/**
 * 在后台把任务交给一个子代理助手跑完一整轮，产物作为独立会话保存在数据库里，
 * 通过 [Conversation.parentConversationId] 挂在父会话下面，任务树据此渲染。
 */
class SubAgentRunner(
    private val settingsStore: SettingsStore,
    private val conversationRepository: ConversationRepository,
    private val requestFactory: () -> ChatRequestFactory,
) {
    suspend fun run(
        parentConversationId: Uuid,
        target: String,
        task: String,
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

        val conversation = Conversation(
            assistantId = candidate.id,
            title = task.trim().lineSequence().firstOrNull()?.take(40)?.ifBlank { "子代理任务" }
                ?: "子代理任务",
            messageNodes = listOf(
                MessageNode(
                    messages = listOf(
                        UIMessage(
                            role = MessageRole.USER,
                            parts = listOf(UIMessagePart.Text(task)),
                        )
                    )
                )
            ),
            parentConversationId = parentConversationId,
            subAgentTask = task,
            subAgentState = SUB_AGENT_STATE_RUNNING,
        )
        conversationRepository.insertConversation(conversation)

        var latest = conversation
        var lastSaveAt = 0L
        var completed = false
        var awaitingApproval = false

        suspend fun save() {
            latest = latest.copy(updateAt = Instant.now())
            runCatching { conversationRepository.updateConversation(latest) }
                .onFailure { Log.w(TAG, "save sub agent conversation failed", it) }
            lastSaveAt = System.currentTimeMillis()
        }

        try {
            val factory = requestFactory()
            val turn = factory.createContext(settings, latest)
            val replyNodeId = Uuid.random()
            val (request, hooks) = factory.createAgent(
                turn = turn,
                messages = latest.currentMessages,
                processingStatus = MutableStateFlow<String?>(null),
            )
            runAgent(request, hooks).flowOn(Dispatchers.IO).collect { event ->
                when (event) {
                    is AgentEvent.MessageUpdated -> {
                        latest = latest.upsertMessage(replyNodeId, event.message)
                        if (System.currentTimeMillis() - lastSaveAt > SAVE_INTERVAL_MS) {
                            save()
                        }
                    }

                    is AgentEvent.Retrying -> Unit

                    is AgentEvent.Finished -> {
                        latest = latest.upsertMessage(replyNodeId, event.message)
                        completed = true
                        awaitingApproval = event.reason is me.rerere.ai.agent.AgentFinishReason.AwaitingApproval
                    }
                }
            }
            latest = latest.copy(subAgentState = SUB_AGENT_STATE_COMPLETED)
        } catch (e: Exception) {
            Log.w(TAG, "sub agent run failed", e)
            latest = latest.copy(subAgentState = SUB_AGENT_STATE_FAILED)
            save()
            return "子代理「${candidate.name}」执行失败：${e.message ?: e::class.simpleName}"
        }

        if (awaitingApproval) {
            latest = latest.copy(subAgentState = SUB_AGENT_STATE_FAILED)
            save()
            return "子代理「${candidate.name}」停在了需要用户确认的工具上，当前预览版还不能替它审批；会话 ${latest.id} 可在任务树里查看。"
        }

        save()
        if (!completed) {
            latest = latest.copy(subAgentState = SUB_AGENT_STATE_FAILED)
            save()
            return "子代理「${candidate.name}」没有产生回复（可能停留在工具审批），会话 ${latest.id} 可在任务树里查看。"
        }

        val answer = latest.currentMessages.lastOrNull()?.toText()?.trim().orEmpty()
        return buildString {
            appendLine("子代理「${candidate.name}」已完成，会话 ID：${latest.id}")
            appendLine("完整过程可在聊天页右上角的任务树里查看。")
            appendLine()
            append(answer.ifBlank { "（子代理没有输出文本）" })
        }
    }

    private suspend fun depthOf(conversationId: Uuid): Int {
        var current = conversationRepository.getConversationById(conversationId)
        var depth = 0
        while (current?.parentConversationId != null && depth < 32) {
            depth++
            current = conversationRepository.getConversationById(current.parentConversationId!!)
        }
        return depth
    }
}
