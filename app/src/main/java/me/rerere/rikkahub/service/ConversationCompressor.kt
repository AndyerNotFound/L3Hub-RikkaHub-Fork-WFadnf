package me.rerere.rikkahub.service

import android.app.Application
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.ChatRequestFactory
import me.rerere.rikkahub.data.ai.prompts.buildCompressPrompt
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.event.ChatStatus
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.autoCompressTriggerTokens
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getChatModelOf
import me.rerere.rikkahub.data.model.insertContextCheckpoint
import me.rerere.rikkahub.data.model.lastContextTokens

private const val TAG = "ConversationCompressor"

/**
 * 压缩对话历史：手动与自动压缩的任务编排、门限判定与检查点写入。
 * 从 ChatService 拆出，任务槽与生成共用。
 */
internal class ConversationCompressor(
    private val context: Application,
    private val settingsStore: SettingsStore,
    private val requestFactory: ChatRequestFactory,
    private val appEventBus: AppEventBus,
    private val reportError: (Throwable, Uuid?, String?, ChatErrorSolution?) -> Unit,
    private val launchJob: (Uuid, Boolean, SessionActivity, suspend () -> Unit) -> Job,
) {
    private fun addError(
        error: Throwable,
        conversationId: Uuid? = null,
        title: String? = null,
        solution: ChatErrorSolution? = null,
    ) = reportError(error, conversationId, title, solution)

    private fun launchGenerationJob(
        conversationId: Uuid,
        keepAliveInBackground: Boolean = true,
        activity: SessionActivity = SessionActivity.Generating,
        block: suspend () -> Unit,
    ): Job = launchJob(conversationId, keepAliveInBackground, activity, block)

    /**
     * 回复结束后上下文占用达到助手的门限时开一个任务自动压缩，返回这个任务；不需要压缩时返回 null。
     * 上一轮失败或被停止时队列是暂停的，这时不压缩，等用户恢复队列、下一轮结束后再看。
     */
    internal fun launchAutoCompressIfNeeded(session: ConversationSession): Job? {
        synchronized(session) {
            val settings = settingsStore.settingsFlow.value
            if (!session.isInitialized) return null
            if (!session.canStartNextTurn || session.inbox.state.value.paused) return null
            val conversation = session.state.value
            val assistant = settings.getAssistantOf(conversation)
            if (!assistant.autoCompress) return null
            val reply = conversation.currentMessages.lastOrNull { it.role == MessageRole.ASSISTANT } ?: return null
            if (reply.id == session.autoCompressedReplyId) return null
            // 已经压缩过的话检查点在回复之后，这里取到的是 null
            val model = settings.getChatModelOf(conversation) ?: return null
            if (!reachesAutoCompressThreshold(model, assistant, conversation.lastContextTokens())) return null

            session.autoCompressedReplyId = reply.id
            return launchCompressionJob(
                session = session,
                additionalPrompt = "",
                keepRecentMessages = 0,
                // 建议是这一轮刚生成的，自动压缩不清掉
                clearSuggestions = false,
            ) { result ->
                result.onFailure { e ->
                    Log.w(TAG, "Auto compression failed", e)
                    // 这一轮的回复已经完成，压缩失败不暂停队列；取消时 addError 会忽略
                    addError(e, session.id, title = context.getString(R.string.chat_page_auto_compress_failed))
                }
            }
        }
    }

    /**
     * 开一个压缩任务占住任务槽，调用方须持有会话锁并确认槽是空的。
     *
     * 和生成共用任务槽：期间界面显示加载中，新消息照常排队、压缩完再发，停止生成能取消它，
     * 重新生成会取消它，审批会排在它后面。[onResult] 恰好调用一次，取消时收到 CancellationException。
     * 失败只交给 [onResult]，任务本身正常结束，免得被当成生成失败暂停收件箱。
     */
    internal fun launchCompressionJob(
        session: ConversationSession,
        additionalPrompt: String,
        keepRecentMessages: Int,
        clearSuggestions: Boolean,
        onResult: (Result<Unit>) -> Unit,
    ): Job {
        // 成功回调之后、任务结束之前被取消的话完成回调还会再报一次取消，只认第一次
        val delivered = AtomicBoolean(false)
        val deliver = { result: Result<Unit> -> if (delivered.compareAndSet(false, true)) onResult(result) }
        val statusSender = settingsStore.settingsFlow.value
            .getAssistantOf(session.state.value)
            .name.ifEmpty { context.getString(R.string.assistant_page_default_assistant) }
        val job = launchGenerationJob(conversationId = session.id, activity = SessionActivity.Compressing) {
            session.processingStatus.value = context.getString(R.string.chat_page_compressing)
            appEventBus.tryEmit(AppEvent.ChatGenerationStatus(session.id, statusSender, ChatStatus.COMPRESSING))
            try {
                insertCompressionCheckpoint(session, additionalPrompt, keepRecentMessages, clearSuggestions)
                deliver(Result.success(Unit))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                deliver(Result.failure(e))
            } finally {
                session.processingStatus.value = null
                // 压缩任务结束时后面不一定还有事件来更新这条通知，直接收掉
                appEventBus.tryEmit(AppEvent.ChatGenerationEnded(session.id, statusSender, null))
            }
        }
        // 包括任务还没进入函数体就被取消的情况
        job.invokeOnCompletion { cause -> if (cause != null) deliver(Result.failure(cause)) }
        session.setJob(job, activity = SessionActivity.Compressing)
        return job
    }

    /** 上下文占用是否已经达到这个助手的自动压缩门限 */
    internal fun reachesAutoCompressThreshold(model: Model, assistant: Assistant, contextTokens: Int?): Boolean {
        if (!assistant.autoCompress || contextTokens == null) return false
        val limit = model.autoCompressTriggerTokens(assistant) ?: return false
        return contextTokens >= limit
    }

    /**
     * 一轮回复的中途压缩上下文：这时回复里的工具都已经执行完，把到它为止的全部消息压成检查点。
     * 在生成任务里直接做，任务槽和前台服务一直占着，停止生成同样能取消。
     *
     * @return 是否压缩成功；失败时报告错误，这一轮照常继续
     */
    internal suspend fun compressMidTurn(session: ConversationSession): Boolean {
        val reply = session.state.value.currentMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
        session.autoCompressedReplyId = reply?.id
        // 压缩前把已经生成的内容存下来
        session.finishGeneration()
        session.setActivity(SessionActivity.Compressing)
        session.processingStatus.value = context.getString(R.string.chat_page_compressing)
        val statusSender = settingsStore.settingsFlow.value
            .getAssistantOf(session.state.value)
            .name.ifEmpty { context.getString(R.string.assistant_page_default_assistant) }
        appEventBus.tryEmit(AppEvent.ChatGenerationStatus(session.id, statusSender, ChatStatus.COMPRESSING))
        return try {
            insertCompressionCheckpoint(
                session = session,
                additionalPrompt = "",
                keepRecentMessages = 0,
                clearSuggestions = false,
                midTurn = true,
            )
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Mid-turn compression failed", e)
            addError(e, session.id, title = context.getString(R.string.chat_page_auto_compress_failed))
            false
        } finally {
            session.processingStatus.value = null
            appEventBus.tryEmit(AppEvent.ChatGenerationStatus(session.id, statusSender, null))
            // 被取消时任务槽可能已经换成别的任务，不能改它的状态
            if (currentCoroutineContext().isActive) session.setActivity(SessionActivity.Generating)
        }
    }

    /**
     * 把上一个检查点之后、最近 [keepRecentMessages] 条之前的消息压缩成摘要检查点。
     * 只在占着任务槽的任务里调用：期间别的生成开始时会先取消它，提交时不用再检查是否在生成。
     */
    private suspend fun insertCompressionCheckpoint(
        session: ConversationSession,
        additionalPrompt: String,
        keepRecentMessages: Int,
        clearSuggestions: Boolean,
        midTurn: Boolean = false,
    ) {
        val conversation = session.state.value
        val nodes = conversation.messageNodes
        val checkpointIndex = nodes.indexOfLast { it.currentMessage.isContextCheckpoint }
        // 压缩范围是 (checkpointIndex, endIndex)，之后的节点原样保留
        val endIndex = nodes.size - keepRecentMessages.coerceAtLeast(0)
        // 上一个检查点之后没有可压缩的消息
        if (endIndex <= checkpointIndex + 1) {
            throw IllegalStateException(
                context.getString(
                    if (keepRecentMessages > 0) {
                        R.string.chat_page_compress_not_enough_messages
                    } else {
                        R.string.chat_page_compress_nothing_to_compress
                    }
                )
            )
        }
        // 未完成的工具调用没有结果，请求会被服务端拒绝；压到检查点之前也再不会执行。
        // 保留区里的不发给压缩请求，压缩后照常留在检查点之后，不用检查
        check(nodes.subList(checkpointIndex + 1, endIndex).none { node ->
            node.currentMessage.getTools().any { it.isPending || it.canResumeExecution }
        }) { context.getString(R.string.chat_page_compress_handle_pending_tools) }

        // 必须和对话用同一个模型、同一套请求组装，才能命中提示词缓存
        val turn = requestFactory.createContext(settingsStore.awaitLoaded(), conversation)
        // 上一个检查点本身要进摘要输入，否则上次压缩之前的信息全丢；
        // 但它之前的原文已经由它替代，再发一遍既重复付费，也可能把压缩请求顶出模型窗口
        val checkpointMessage = nodes.getOrNull(checkpointIndex)?.currentMessage
        val summaryMessages = buildList {
            checkpointMessage?.let { add(it) }
            addAll(conversation.currentMessages.subList(checkpointIndex + 1, endIndex))
        }
        val summary = requestFactory.generateCompressionSummary(
            turn = turn,
            messages = summaryMessages,
            compressPrompt = buildCompressPrompt(
                additionalPrompt = additionalPrompt,
                locale = Locale.getDefault().displayName,
                midTurn = midTurn,
            ),
        )

        // 原消息原样保留，摘要插在压缩范围的最后一条消息之后。摘要生成期间对话可能已变化，
        // 因此按节点定位插入到最新状态。
        session.update { latest ->
            val updated = latest.insertContextCheckpoint(
                afterNodeId = nodes[endIndex - 1].id,
                summary = summary,
            ) ?: throw IllegalStateException(
                context.getString(R.string.chat_page_compress_conversation_changed)
            )
            if (clearSuggestions) updated.copy(chatSuggestions = emptyList()) else updated
        }
        session.flush()
    }
}
