package me.rerere.rikkahub.service

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.agent.AgentEvent
import me.rerere.ai.agent.AgentFinishReason
import me.rerere.ai.agent.RetryAttempt
import me.rerere.ai.agent.runAgent
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.common.android.Logging
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.ChatRequestFactory
import me.rerere.rikkahub.data.ai.ChatRequestPreview
import me.rerere.rikkahub.data.ai.ChatTurnContext
import me.rerere.rikkahub.data.ai.ConversationMetaGenerator
import me.rerere.rikkahub.data.ai.MemoryConsolidationScheduler
import me.rerere.rikkahub.data.ai.TranslationHandler
import me.rerere.rikkahub.data.ai.guidanceToolPart
import me.rerere.rikkahub.data.ai.isInjectedTool
import me.rerere.rikkahub.data.ai.stepsSinceLastGuidance
import me.rerere.rikkahub.data.ai.stepsSinceLastTodoReminder
import me.rerere.rikkahub.data.ai.todoReminderToolPart
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_STATE_RUNNING
import me.rerere.rikkahub.data.ai.subagent.SubAgentResultStore
import me.rerere.rikkahub.data.ai.subagent.SubAgentWaits
import me.rerere.rikkahub.data.ai.tools.InvalidMcpServerNamesException
import me.rerere.rikkahub.data.ai.tools.TODO_UPDATE_TOOL_NAME
import me.rerere.rikkahub.data.ai.tools.TODO_WRITE_TOOL_NAME
import me.rerere.rikkahub.data.ai.tools.shouldUseExternalWebSearch
import me.rerere.rikkahub.data.ai.transformers.withOcrResults
import me.rerere.rikkahub.data.chat.DraftStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Guidance
import me.rerere.rikkahub.data.model.GuidanceMode
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.bindConfig
import me.rerere.rikkahub.data.model.cancelInterruptedTools
import me.rerere.rikkahub.data.model.deleteMessage
import me.rerere.rikkahub.data.model.hasActiveTodo
import me.rerere.rikkahub.data.model.editMessage
import me.rerere.rikkahub.data.model.fillModelSnapshots
import me.rerere.rikkahub.data.model.fork
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getChatModelOf
import me.rerere.rikkahub.data.model.getStoredAssistantOf
import me.rerere.rikkahub.data.model.isAwaitingToolApproval
import me.rerere.rikkahub.data.model.localFileUrls
import me.rerere.rikkahub.data.model.replaceUserRegexes
import me.rerere.rikkahub.data.model.selectBranch
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.model.withAssistantUpdate
import me.rerere.rikkahub.data.model.withNodeFavorite
import me.rerere.rikkahub.data.model.withToolApproval
import me.rerere.rikkahub.data.model.withTranslation
import me.rerere.rikkahub.data.model.withWorkspaceBinding
import me.rerere.rikkahub.data.model.withoutConversationFields
import me.rerere.rikkahub.data.model.withoutUnresumableToolCalls
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.web.BadRequestException
import me.rerere.rikkahub.web.NotFoundException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import kotlin.uuid.Uuid

private const val TAG = "ChatService"

class ChatService(
    private val context: Application,
    private val appScope: AppScope,
    private val appEventBus: AppEventBus,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val requestFactory: ChatRequestFactory,
    private val memoryConsolidationScheduler: MemoryConsolidationScheduler,
    private val translationHandler: TranslationHandler,
    private val metaGenerator: ConversationMetaGenerator,
    val mcpManager: McpManager,
    private val filesManager: FilesManager,
    private val folderRepository: FolderRepository,
    private val draftStore: DraftStore,
    private val subAgentResultStore: SubAgentResultStore,
    private val subAgentWaits: SubAgentWaits,
    private val backgroundShellManager: BackgroundShellManager,
) {
    /** 一次 agent 运行发给模型的消息，和回复要写进的节点 */
    private class ReplyTarget(val history: List<UIMessage>, val nodeId: Uuid) {
        /** 接着已有的助手消息写时是它的 id，新开回复时为空 */
        val continuedMessageId: Uuid? get() = history.lastOrNull()?.takeIf { it.role == MessageRole.ASSISTANT }?.id
    }

    private fun replyTargetOf(conversation: Conversation, regenerateNodeId: Uuid?): ReplyTarget {
        val nodes = conversation.messageNodes
        val history = if (regenerateNodeId != null) {
            val index = nodes.indexOfFirst { it.id == regenerateNodeId }
            check(index != -1) { "Message node $regenerateNodeId not found" }
            conversation.currentMessages.take(index)
        } else {
            conversation.currentMessages
        }
        val nodeId = when {
            history.lastOrNull()?.role == MessageRole.ASSISTANT -> nodes[history.lastIndex].id
            regenerateNodeId != null -> regenerateNodeId
            else -> Uuid.random()
        }
        return ReplyTarget(history, nodeId)
    }

    /** 一轮生成的结果 */
    private sealed interface TurnResult {
        data class Finished(val reason: AgentFinishReason) : TurnResult

        /** 没能完成：错误已经报告给用户，消息队列也已暂停 */
        data object Failed : TurnResult
    }

    private val conversationStore = object : ConversationStore {
        override suspend fun prepareWrite(): (Conversation) -> Conversation {
            // 会话落库即视为开始，此时把助手的配置固定到会话上
            val settings = settingsStore.awaitLoaded()
            return { it.bindConfig(settings).fillModelSnapshots(settings) }
        }

        override suspend fun write(previous: Conversation?, current: Conversation) {
            if (previous == null && !conversationRepo.existsConversationById(current.id)) {
                conversationRepo.insertConversation(current)
            } else {
                conversationRepo.updateConversation(current)
            }
            if (previous != null && previous.messageNodes !== current.messageNodes) {
                withContext(Dispatchers.IO) { deleteUnreferencedFiles(previous, current) }
                val currentNodeIds = current.messageNodes.mapTo(HashSet()) { it.id }
                val removedNodeIds = previous.messageNodes.map { it.id }.filterNot { it in currentNodeIds }
                if (removedNodeIds.isNotEmpty()) conversationRepo.deleteNodeFavorites(current.id, removedNodeIds)
            }
        }
    }

    /** 会话当前内存里的清单；会话没加载时返回 null，由调用方决定是否读库 */
    fun currentTodos(conversationId: Uuid): List<TodoItem>? =
        sessionManager.get(conversationId)?.state?.value?.todos

    /**
     * 任务清单的唯一写入口。
     *
     * 之前是「工具直接写库 + 另一次调用同步内存」，两条路没有顺序保证：库先写、内存后改，
     * 中间任何一次 flush 都会把旧清单整条写回去；而内存里算出来的归属节点要等下一次整条保存
     * 才落库，进程在这之间被杀就丢了归属（压缩恰好会删掉那条 todo_write 消息）。
     * 这里按会话的既有规矩走：先 update 再 flush。
     *
     * @param keepOwner 用户手动改清单时为 true。模型写的清单跟着那条 todo_write 消息走，
     *   手动改的清单不属于任何消息——沿用原归属。否则归属会被搬到最新一条消息上，
     *   之后重新生成或删除那条消息，会连用户自己写进去的任务一起清掉。
     */
    suspend fun updateTodos(
        conversationId: Uuid,
        todos: List<TodoItem>,
        keepOwner: Boolean = false,
    ) {
        val session = sessionManager.get(conversationId)
        if (session == null) {
            // 会话没加载（比如从终端/Web 入口触发），只能直接落库。
            // keepOwner 时得先把库里的归属读出来再写回去：传 null 等于把归属清掉，
            // 之后读出清单会退回「最后一个 todo_write 节点」的推断，重新生成那条消息照样会清掉手改的内容。
            val owner = if (keepOwner) {
                conversationRepo.getConversationSummary(conversationId)?.todosOwnerNodeId
            } else {
                null
            }
            conversationRepo.updateTodos(conversationId, todos, owner)
            return
        }
        session.update { current ->
            // 清单跟着写下它的那条消息走：记住写入时的最后一个节点
            val ownerNodeId = if (keepOwner) {
                current.todosOwnerNodeId
            } else {
                current.messageNodes.lastOrNull()?.id
            }
            if (current.todos == todos && current.todosOwnerNodeId == ownerNodeId) {
                current
            } else {
                current.copy(todos = todos, todosOwnerNodeId = ownerNodeId)
            }
        }
        session.flush()
    }

    private val sessionManager = ConversationSessionManager(
        scope = appScope,
        store = conversationStore,
        createInitialConversation = { id ->
            Conversation.ofId(id, assistantId = settingsStore.settingsFlow.value.getCurrentAssistant().id)
        },
        onGenerationFinished = ::onSessionGenerationFinished,
        onSessionRemoved = memoryConsolidationScheduler::onConversationLeft,
    )

    private val lifecycle = ConversationLifecycle(
        sessionManager = sessionManager,
        settingsStore = settingsStore,
        conversationRepo = conversationRepo,
        draftStore = draftStore,
        onFlushed = { conversationId -> dispatchInbox(conversationId) },
    )

    private val compressor = ConversationCompressor(
        context = context,
        settingsStore = settingsStore,
        requestFactory = requestFactory,
        appEventBus = appEventBus,
        reportError = { error, conversationId, title, solution ->
            addError(error, conversationId, title, solution)
        },
        launchJob = { conversationId, keepAliveInBackground, activity, block ->
            launchGenerationJob(conversationId, keepAliveInBackground, activity, block)
        },
    )

    private val gateway = SubAgentGateway(
        context = context,
        settingsStore = settingsStore,
        conversationRepo = conversationRepo,
        sessionManager = sessionManager,
        subAgentResultStore = subAgentResultStore,
        subAgentWaits = subAgentWaits,
        sendMessage = { conversationId, content, notice ->
            sendMessage(conversationId, content, answer = true, notice = notice)
        },
    )

    // 错误状态：写入口在 ChatErrorSink，这几个名字留给聊天页、悬浮窗与 Web 路由
    private val errorSink = ChatErrorSink(context)
    val errors: StateFlow<List<ChatError>> = errorSink.state

    fun addError(
        error: Throwable,
        conversationId: Uuid? = null,
        title: String? = null,
        solution: ChatErrorSolution? = null,
    ) = errorSink.report(error, conversationId, title, solution)

    fun dismissError(id: Uuid) = errorSink.dismiss(id)

    fun clearAllErrors() = errorSink.clearAll()

    // 生成完成流
    private val _generationDoneFlow = MutableSharedFlow<Uuid>()
    val generationDoneFlow: SharedFlow<Uuid> = _generationDoneFlow.asSharedFlow()

    fun cleanup() = runCatching { sessionManager.cleanup() }

    init {
        appScope.launch {
            backgroundShellManager.noticeEvents.collect { event ->
                when (event) {
                    is BackgroundShellNoticeEvent.Posted -> postNotice(event.conversationId, event.key, event.notice)
                    is BackgroundShellNoticeEvent.Withdrawn ->
                        sessionManager.get(event.conversationId)?.inbox?.withdraw(event.key)
                }
            }
        }
    }

    private fun onSessionGenerationFinished(
        session: ConversationSession,
        cause: Throwable?,
        activity: SessionActivity?,
    ) {
        // 取消只来自停止（已自行暂停）或删除，这里再暂停会盖掉停止后用户发送时恢复的队列
        if (cause != null && cause !is CancellationException) session.inbox.pause()
        if (session.state.value.isAwaitingToolApproval) {
            session.inbox.failReplyWaiters(context.getString(R.string.chat_page_voice_tool_approval))
        }
        appScope.launch {
            // 先回报这一轮（读到的还是刚跑完的状态），再放行下一条排队消息：
            // 否则新的一轮已经把用户消息追加进来，轮次和内容都会算错
            try {
                // 压缩任务也占任务槽，但它不是子代理的一轮，不能在这里回报
                if (activity != SessionActivity.Compressing) gateway.reportSubAgentRound(session, cause)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 回报失败不能拖住这个会话后面的排队消息和缓冲结果
                Log.w(TAG, "report sub agent round failed: ${session.id}", e)
            }
            // 这一轮里交给 await_sub_agent 的那几份，正常结束就算真的送到了（账当场已经 -1）；
            // 被打断的话工具输出可能压根没写进会话，把正文退回待回传，让这一份照常补投
            gateway.settleClaims(session.id, interrupted = cause != null)
            // 先压缩再处理收件箱：压缩任务结束时会再回到这里
            if (cause == null && compressor.launchAutoCompressIfNeeded(session) != null) {
                gateway.flushSubAgentResults(session.id)
                return@launch
            }
            dispatchInbox(session.id)
            gateway.flushSubAgentResults(session.id)
        }
    }

    // 保留 UI/Web 的入口，生命周期和状态查询统一交给 SessionManager。
    fun addConversationReference(conversationId: Uuid) {
        sessionManager.acquire(conversationId)
    }

    fun removeConversationReference(conversationId: Uuid) {
        sessionManager.release(conversationId)
    }

    fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> =
        sessionManager.getConversationFlow(conversationId)

    /** 会话的最新内容：已加载的以内存为准，否则读数据库 */
    suspend fun getConversation(conversationId: Uuid): Conversation? =
        sessionManager.get(conversationId)?.takeIf { it.isInitialized }?.state?.value
            ?: conversationRepo.getConversationById(conversationId)

    /** 会话详情里展示的请求快照：工具、系统提示词和本轮注入，按当前设置重新解析一次 */
    suspend fun buildRequestPreview(conversationId: Uuid): ChatRequestPreview =
        withContext(Dispatchers.IO) {
            val settings = settingsStore.awaitLoaded()
            val conversation = getConversation(conversationId)
                ?: error("Conversation not found: $conversationId")
            requestFactory.createPreview(settings, conversation)
        }

    fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?> =
        sessionManager.getGenerationJobStateFlow(conversationId)

    fun getProcessingStatusFlow(conversationId: Uuid): StateFlow<String?> =
        sessionManager.getProcessingStatusFlow(conversationId)

    fun getReplyMessageIdFlow(conversationId: Uuid): StateFlow<Uuid?> =
        sessionManager.getReplyMessageIdFlow(conversationId)

    fun getGenerationStartedAtFlow(conversationId: Uuid): StateFlow<Long?> =
        sessionManager.getGenerationStartedAtFlow(conversationId)

    fun getConversationJobs(): Flow<Map<Uuid, Job?>> = sessionManager.getConversationJobs()

    /** 任务槽里的任务在做什么，槽空着时是 null */
    fun getActivityFlow(conversationId: Uuid): Flow<SessionActivity?> =
        sessionManager.getActivityFlow(conversationId)

    private fun launchGenerationJob(
        conversationId: Uuid,
        keepAliveInBackground: Boolean = true,
        activity: SessionActivity = SessionActivity.Generating,
        block: suspend () -> Unit,
    ): Job {
        if (!keepAliveInBackground) return appScope.launch(start = CoroutineStart.LAZY) { block() }

        return appScope.launch(start = CoroutineStart.LAZY) {
            val generationId = Uuid.random()
            val foregroundStarted = ChatGenerationForegroundService.acquire(
                context = context,
                generationId = generationId,
                conversationId = conversationId,
                activity = activity,
            )
            try {
                block()
            } finally {
                if (foregroundStarted) {
                    ChatGenerationForegroundService.release(context, generationId)
                }
            }
        }
    }

    // ---- 初始化对话 ----

    suspend fun initializeConversation(conversationId: Uuid) {
        withLoadedSession(conversationId) { session ->
            // 子代理会话按普通会话列在历史里，打开时侧边栏跟着切到它自己的助手
            settingsStore.selectAssistant(session.state.value.assistantId)
            // 上一轮被进程杀掉时留下的草稿和没来得及收尾的思考，进页面时补一次
            lifecycle.recoverInterruptedTurn(session)
        }
        // 上一轮还在忙的时候攒下的子代理结果，等用户打开这个会话再投递
        gateway.flushSubAgentResults(conversationId)
    }

    private suspend fun ensureInitialized(session: ConversationSession) =
        lifecycle.ensureInitialized(session)

    /** 持有会话引用并确保已加载后执行 [block]；语义见 [ConversationLifecycle.withLoaded] */
    private suspend fun <T> withLoadedSession(
        conversationId: Uuid,
        createIfMissing: Boolean = true,
        block: suspend (ConversationSession) -> T,
    ): T = lifecycle.withLoaded(conversationId, createIfMissing, block)

    // 只删上次落库时引用、这次落库不再引用的文件；只在内存里出现过的文件宁可漏删也不误删
    private fun deleteUnreferencedFiles(previous: Conversation, current: Conversation) {
        val session = sessionManager.get(current.id)
        val queuedFiles = (session?.inbox?.state?.value?.messages.orEmpty() +
            listOfNotNull(session?.submittingMessage))
            .flatMap { it.parts }.localFileUrls().map { it.toUri() }
        val retainedFiles = (current.files + queuedFiles).toSet()
        val deletedFiles = previous.files.filter { it !in retainedFiles }
        if (deletedFiles.isNotEmpty()) {
            filesManager.deleteChatFiles(deletedFiles)
            Log.w(TAG, "deleteUnreferencedFiles: $deletedFiles")
        }
    }

    // ---- 发送消息 ----

    fun getInboxFlow(conversationId: Uuid): StateFlow<InboxState> =
        sessionManager.getInboxFlow(conversationId)

    /** 删除收件箱里的一项：用户输入连同只被它引用的附件一起删，通知直接丢弃 */
    fun removeInboxItem(conversationId: Uuid, itemId: Uuid) {
        (sessionManager.get(conversationId)?.inbox?.remove(itemId) as? QueuedMessage)
            ?.let(::cleanupQueuedAttachments)
        dispatchInbox(conversationId)
    }

    fun beginEditQueuedMessage(conversationId: Uuid, messageId: Uuid): QueuedMessage? =
        sessionManager.get(conversationId)?.inbox?.beginEdit(messageId)

    fun finishEditQueuedMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>? = null
    ) {
        sessionManager.get(conversationId)?.inbox?.finishEdit(messageId, parts)
            ?.let(::cleanupQueuedAttachments)
        dispatchInbox(conversationId)
    }

    private fun cleanupQueuedAttachments(previous: QueuedMessage) {
        val candidates = previous.parts.localFileUrls()
        if (candidates.isEmpty()) return
        appScope.launch {
            try {
                // 未打开的会话及未选中的分支也可能引用同一附件。
                val persistedReferences =
                    candidates.filter { conversationRepo.hasFileReference(it) }.toSet()
                // 数据库查询挂起期间队列可能已推进，删除前重新读取内存引用。
                val currentSessions = sessionManager.snapshot()
                val unusedFiles = unreferencedQueuedAttachmentUrls(
                    previous = previous,
                    conversations = currentSessions.map { it.state.value },
                    pendingMessages = currentSessions.flatMap {
                        it.inbox.state.value.messages + listOfNotNull(it.submittingMessage)
                    },
                ) - persistedReferences
                if (unusedFiles.isNotEmpty()) {
                    filesManager.deleteChatFiles(unusedFiles.map { it.toUri() })
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // 无法确认引用时保留文件，避免误删。
                Log.w(TAG, "Failed to clean queued attachments", e)
            }
        }
    }

    fun resumeInbox(conversationId: Uuid) {
        sessionManager.get(conversationId)?.inbox?.resume()
        // 先把积压的子代理结果排到队尾再开跑：只 dispatch 的话它要等下一轮结束才轮得到。
        // flush 抛异常也必须把队列开起来，否则 resume() 之后没有任何 job，界面像死了一样
        appScope.launch {
            try {
                gateway.flushSubAgentResults(conversationId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "flush sub agent results on resume failed: $conversationId", e)
            } finally {
                dispatchInbox(conversationId)
            }
        }
    }

    /**
     * @param interject 强制插话：不等当前回复结束，直接打断并把这条消息插到队首（只在消息队列开启时有意义，
     * 关闭后每条消息本来就按插话处理）
     * @return 消息是否已被受理。内容为空、上下文到达 MAX 又没开自动压缩、
     * 或者队列暂停又压着旧消息时返回 false；调用方据此决定要不要把内容留着下次再送
     * （子代理结果回投就是这么用的）
     */
    fun sendMessage(
        conversationId: Uuid,
        content: List<UIMessagePart>,
        answer: Boolean = true,
        interject: Boolean = false,
        notice: Boolean = false,
    ): Boolean {
        if (content.isEmptyInputMessage()) return false
        val session = sessionManager.getOrCreate(conversationId)
        return synchronized(session) {
            if (session.maxContextReached(settingsStore) && !autoCompressEnabled(settingsStore, session.state.value)) {
                errorSink.reportContextLimit(conversationId)
                return@synchronized false
            }
            // 关掉消息队列后，用户消息一条都不进队列：能发就当场发，发不了就拒绝，
            // 由调用方把内容留在输入框里（语音消息和系统回投也走这里，见 sendWithoutQueue）
            if (!settingsStore.settingsFlow.value.enableMessageQueue) {
                return@synchronized sendWithoutQueue(session, content, answer, notice)
            }
            if (interject) {
                session.inbox.enqueueFirst(content, answer, notice = notice)
                session.inbox.resume()
                val job = session.getJob()
                if (job != null) {
                    // 取消后 onSessionGenerationFinished 会立刻派发队首
                    job.cancel()
                } else {
                    dispatchInbox(conversationId)
                }
                return@synchronized true
            }
            if (session.inbox.state.value.messages.isEmpty()) session.inbox.resume()
            session.inbox.enqueue(content, answer, notice = notice)
            dispatchInbox(conversationId)
            return@synchronized true
        }
    }

    /**
     * 消息队列关闭时的发送：不排队等这一轮结束。
     *
     * - 正在生成或压缩上下文：打断当前任务，立刻发（压缩随这一轮一起取消）
     * - 等工具审批：同样打断，审批中的工具会标成取消
     * - 系统回投（子代理结果）：不是用户输入，忙时交回调用方保留，不为它打断任何东西
     * - 其余情况：立即派发
     */
    private fun sendWithoutQueue(
        session: ConversationSession,
        content: List<UIMessagePart>,
        answer: Boolean,
        notice: Boolean,
    ): Boolean {
        val conversationId = session.id
        val inbox = session.inbox.state.value
        // 队列暂停又还压着旧消息时新消息根本派发不出去（takeNext 直接返回 null）：
        // 拒绝并把内容留在输入框里，不能假装受理
        if (!notice && inbox.paused && inbox.messages.isNotEmpty()) {
            errorSink.reportRejected(conversationId, R.string.chat_page_voice_resume_queue)
            return false
        }
        return when (decideUnqueuedSend(
            notice = notice,
            hasJob = session.getJob() != null,
            awaitingApproval = session.state.value.isAwaitingToolApproval,
        )) {
            UnqueuedSend.DeferNotice -> false

            UnqueuedSend.Interrupt -> {
                // 排在队尾：关掉队列后连发两条时顺序不能反过来，旧消息也按提交顺序先走
                session.inbox.enqueue(
                    parts = content,
                    answer = answer,
                    notice = notice,
                    sentWithoutQueue = true,
                )
                session.inbox.resume()
                val job = session.getJob()
                if (job != null) {
                    // 取消后 onSessionGenerationFinished 会立刻派发队首
                    job.cancel()
                } else {
                    dispatchInbox(conversationId)
                }
                true
            }

            UnqueuedSend.Dispatch -> {
                if (session.inbox.state.value.messages.isEmpty()) session.inbox.resume()
                session.inbox.enqueue(content, answer, notice = notice)
                dispatchInbox(conversationId)
                true
            }
        }
    }

    /** 子代理结束时更新任务树状态；会话可能已经被回收，因此从数据库重新加载。 */
    suspend fun updateSubAgentState(conversationId: Uuid, state: String) {
        withLoadedSession(conversationId, createIfMissing = false) { session ->
            session.update { it.copy(subAgentState = state) }
            session.flush()
        }
    }

    // ---- background 子代理的结果投递 ----

    init {
        // 进程被杀时还有子代理结果没投递出去：启动后补投一次，否则整条子代理链会停在这一层。
        // 等设置装载完成再开始，避免在设置还是占位值时就开跑
        appScope.launch {
            settingsStore.awaitLoaded()
            gateway.resumePendingSubAgentResults()
        }
    }

    /**
     * background 子代理完成后调用：父会话空闲就立刻把结果当作一条用户消息投递；
     * 正在生成时由 [takeSubAgentNotices] 在下一个工具间隙插进当前这一轮；
     * 停在工具审批上或还没打开时先攒着，等这一轮结束或下次打开会话时合并投递。
     */
    suspend fun deliverSubAgentResult(
        parentConversationId: Uuid,
        childConversationId: Uuid,
        result: String,
    ) = gateway.deliverSubAgentResult(parentConversationId, childConversationId, result)

    /**
     * await_sub_agent 用：取走某条子会话最早一段还没回传的结果。
     *
     * 取走就算回传（这一段不再当通知投递，账同时 -1）。父回合在这之后被打断、工具输出没落库时，
     * 按交接账里这条记录把正文退回待回传，两头都不丢。
     */
    suspend fun takePendingSubAgentResult(
        parentConversationId: Uuid,
        childConversationId: Uuid,
    ): String? = gateway.takePendingSubAgentResult(parentConversationId, childConversationId)

    /** Enqueue immediately; the result belongs to this item even after edits or later turns. */
    fun enqueueVoiceMessage(conversationId: Uuid, text: String): Deferred<String?> {
        val session = sessionManager.getOrCreate(conversationId)
        val reply = CompletableDeferred<String?>()
        synchronized(session) {
            check(text.isNotBlank()) { context.getString(R.string.chat_page_voice_empty) }
            check(!session.inbox.state.value.paused || session.inbox.state.value.messages.isEmpty()) {
                context.getString(R.string.chat_page_voice_resume_queue)
            }
            check(!session.maxContextReached(settingsStore)) {
                context.getString(R.string.chat_page_context_limit_reached)
            }
            // 关闭队列时语音同样不许排队：生成中、等审批都和打字发送一样直接打断
            val queueEnabled = settingsStore.settingsFlow.value.enableMessageQueue
            if (!queueEnabled) {
                session.inbox.enqueue(
                    parts = listOf(UIMessagePart.Text(text)),
                    reply = reply,
                    sentWithoutQueue = true,
                )
                session.inbox.resume()
                val job = session.getJob()
                if (job != null) {
                    // 取消后 onSessionGenerationFinished 会立刻派发队首
                    job.cancel()
                } else {
                    dispatchInbox(conversationId)
                }
                return reply
            }
            check(!session.state.value.isAwaitingToolApproval) {
                context.getString(R.string.chat_page_voice_tools_before_resume)
            }
            if (session.inbox.state.value.messages.isEmpty()) session.inbox.resume()
            session.inbox.enqueue(listOf(UIMessagePart.Text(text)), reply = reply)
            dispatchInbox(conversationId)
        }
        return reply
    }

    /**
     * 放入一条运行时通知，例如后台命令结束。
     * 正在生成时这一轮在下一步请求模型前取走；空闲时主动开一轮，不用等用户再发消息。
     */
    fun postNotice(conversationId: Uuid, key: String, notice: UIMessagePart.Notice) {
        sessionManager.getOrCreate(conversationId).inbox.post(key, notice)
        dispatchInbox(conversationId)
    }

    /**
     * 加入一条引导。
     *
     * 单轮的在下一个工具间隙插一次就从收件箱删掉；多轮的每一批工具之后都会再插一次，直到 [removeGuidance]。
     * 生成中加进去的，这一轮的下一步就能看到；空闲时加进去的，等下一轮真正跑起工具再插——
     * 它管的是「跑到一半改方向」，不是「代替下一条消息」。
     */
    fun addGuidance(
        conversationId: Uuid,
        content: String,
        title: String = "",
        mode: GuidanceMode = GuidanceMode.SINGLE,
    ) {
        if (content.isBlank()) return
        sessionManager.getOrCreate(conversationId).inbox.addGuidance(
            Guidance(title = title.trim(), content = content.trim(), mode = mode),
        )
    }

    fun setGuidanceMode(conversationId: Uuid, guidanceId: Uuid, mode: GuidanceMode) {
        sessionManager.getOrCreate(conversationId).inbox.setGuidanceMode(guidanceId, mode)
    }

    fun removeGuidance(conversationId: Uuid, guidanceId: Uuid) {
        sessionManager.getOrCreate(conversationId).inbox.removeGuidance(guidanceId)
    }

    /**
     * 这一批要插进本轮的合成工具调用。
     *
     * 通知排在最前，清单居中，引导排在最后——模型最后读到的是用户新给的指令。
     * 清单每一批都补一份（模型这一批刚写过清单时跳过），不受引导间隔约束；
     * 它带的是当前进度，不是一次性指令。
     *
     * [current] 为 null 表示这是一轮的第一步，模型还没动过手。这时只放通知，不插引导和清单：
     * 否则回答开头会凭空出现一条工具调用，而引导本来该出现在「跑到一半」的时候。
     *
     * 引导按助手上的 [guidanceInterval] 隔步插：1 是每步都插，4 是每四步插一次。
     * 步号记在合成调用的参数里，按 [UIMessage.stepCount] 的差值算；还没插过的这一步立刻插。
     * 间隔只挡多轮引导，单轮的当批就插——它只有一次机会。
     */
    private suspend fun ConversationSession.takeInjectionParts(
        current: UIMessage?,
        assistant: Assistant,
    ): List<UIMessagePart> {
        val parts = mutableListOf<UIMessagePart>()
        parts += inbox.takeNotices()
        // 子代理结果也在这里进来：父会话正在跑的时候，结果不该等到这一轮收尾才被看到
        parts += gateway.takeSubAgentNotices(this)
        if (current == null) return parts
        // 上下文超限后这一步会重装，[appendPendingParts] 会被再调一次；末尾还是上一次插进去的合成调用，
        // 说明中间没有跑过新的工具批次，这一批已经在消息里了
        if (current.parts.lastOrNull()?.isInjectedTool() == true) return parts

        val step = current.stepCount ?: 0

        // 清单：开关关掉就整块跳过；间隔没到也跳过。只有「这一批刚写完清单」时不重复贴，
        // 那条工具结果里已经是同一份，紧接着再补一遍是回声
        if (assistant.todoReminderEnabled &&
            state.value.todos.hasActiveTodo() &&
            current.todoReminderDue(assistant.todoReminderInterval)
        ) {
            todoReminderToolPart(state.value.todos, step)?.let { parts += it }
        }

        // 引导按助手的插入间隔走；单轮的不等，它只有一次机会，当批就该生效
        val sinceLastGuidance = current.stepsSinceLastGuidance()
        val repeatingDue = sinceLastGuidance == null ||
            sinceLastGuidance >= assistant.guidanceInterval.coerceIn(1, 4)
        inbox.takeGuidances(includeRepeating = repeatingDue).forEach { guidance ->
            parts += guidanceToolPart(guidance, step)
        }
        return parts
    }

    /** 这一批该不该补清单：刚写完清单不补，间隔没到也不补 */
    private fun UIMessage.todoReminderDue(interval: Int): Boolean {
        val lastTool = (parts.lastOrNull() as? UIMessagePart.Tool)?.takeIf { it.isExecuted }
        if (lastTool != null &&
            (lastTool.toolName == TODO_WRITE_TOOL_NAME || lastTool.toolName == TODO_UPDATE_TOOL_NAME)
        ) {
            return false
        }
        val sinceLast = stepsSinceLastTodoReminder()
        return sinceLast == null || sinceLast >= interval.coerceIn(1, 4)
    }

    /** 会话空闲时从收件箱开始下一轮：先发用户输入，只剩通知时单独开一轮 */
    private fun dispatchInbox(conversationId: Uuid): Job? {
        val session = sessionManager.get(conversationId) ?: return null
        synchronized(session) {
            val canDispatch = canDispatchInbox(
                hasJob = session.getJob() != null,
                awaitingApproval = session.state.value.isAwaitingToolApproval,
                queueEnabled = settingsStore.settingsFlow.value.enableMessageQueue,
                hasUserMessage = session.inbox.state.value.messages.isNotEmpty(),
            )
            if (!canDispatch) return null
            // 队列里还有消息但已经到达 MAX: 暂停队列, 压缩上下文后可以手动恢复
            if (session.inbox.state.value.messages.isNotEmpty() &&
                session.maxContextReached(settingsStore) &&
                !autoCompressEnabled(settingsStore, session.state.value)
            ) {
                session.inbox.pause()
                errorSink.reportContextLimit(conversationId)
                return null
            }
            return when (val next = session.inbox.takeNext() ?: return null) {
                is InboxTurn.Message -> {
                    session.submittingMessage = next.message
                    sendQueuedMessage(session, next.message)
                }

                InboxTurn.Notices -> sendQueuedNotices(session)
            }
        }
    }

    private fun sendQueuedMessage(session: ConversationSession, queued: QueuedMessage): Job {
        val conversationId = session.id
        // 新消息开始初始化前就清掉旧回复，初始化失败或任务启动前取消也不能留下旧预览。
        session.replyMessageId.value = null
        val job = launchGenerationJob(
            conversationId = conversationId,
            keepAliveInBackground = queued.answer,
        ) {
            try {
                ensureInitialized(session)
                finishInterruptedPendingTools(session)

                val assistant = settingsStore.awaitLoaded().getStoredAssistantOf(session.state.value)
                val processedContent = queued.parts.replaceUserRegexes(assistant)

                // 添加消息到列表
                val previousIds = session.state.value.currentMessages.mapTo(HashSet()) { it.id }
                // 等待中的通知存成用户消息之前的节点：写进回复的话，重新生成时就丢了。
                // 不回复时留在收件箱，这一轮结束后单独开一轮
                val notices = if (queued.answer) session.inbox.takeNotices() else emptyList()
                session.update {
                    it.copy(
                        messageNodes = it.messageNodes +
                            listOfNotNull(notices.takeIf { n -> n.isNotEmpty() }?.toNoticeNode()) +
                            UIMessage(
                                role = MessageRole.USER,
                                parts = processedContent,
                                isSubAgentNotice = queued.notice,
                            ).toMessageNode(),
                        // 子代理会话每开一轮就回到 running：上一轮之后还会被回传的结果再触发一轮，任务树不该停在「已完成」
                        subAgentState = if (it.parentConversationId != null) SUB_AGENT_STATE_RUNNING else it.subAgentState,
                    )
                }
                session.flush()
                session.submittingMessage = null

                // 开始补全
                val result = if (queued.answer) runTurn(session) else null

                queued.reply?.completeWith(runCatching {
                    check(result != TurnResult.Failed) { context.getString(R.string.chat_page_voice_generation_failed) }
                    check((result as? TurnResult.Finished)?.reason !is AgentFinishReason.AwaitingApproval) {
                        context.getString(R.string.chat_page_voice_tool_approval)
                    }
                    session.state.value.currentMessages
                        .filter { it.id !in previousIds && it.role == MessageRole.ASSISTANT }
                        .joinToString("\n") { it.toText() }
                })
                // Voice owns playback, including when its observer has already left the page.
                // The ordinary autoplay collector must not read a late voice reply again.
                if (queued.reply == null) _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                queued.reply?.completeExceptionally(e)
                e.printStackTrace()
                reportFailure(session, e, R.string.error_title_send_message)
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause != null) queued.reply?.completeExceptionally(cause)
            synchronized(session) {
                if (session.submittingMessage?.id == queued.id) session.submittingMessage = null
            }
        }
        session.setJob(job)
        return job
    }

    /** 只有通知在等时开的一轮：通知存成一个 user 节点，模型的回复是它的下一个节点 */
    private fun sendQueuedNotices(session: ConversationSession): Job {
        val conversationId = session.id
        session.replyMessageId.value = null
        val job = launchGenerationJob(conversationId = conversationId) {
            try {
                // 通知来自已经开始过的会话；会话刚被删除时不能重新创建
                session.initialize(
                    load = { lifecycle.loadConversation(conversationId) },
                    create = { throw NotFoundException("Conversation not found") },
                )
                // 加载前不知道是否在等审批；审批后执行完工具，这一轮会自己取走通知
                if (session.state.value.isAwaitingToolApproval) return@launchGenerationJob
                val notices = session.inbox.takeNotices()
                if (notices.isEmpty()) return@launchGenerationJob
                // 存成 user 节点：重新生成和分支都和普通消息一样
                session.update { it.copy(messageNodes = it.messageNodes + notices.toNoticeNode()) }
                session.flush()
                runTurn(session)
                _generationDoneFlow.emit(conversationId)
            } catch (e: NotFoundException) {
                Log.w(TAG, "sendQueuedNotices: conversation $conversationId not found, dropping notices")
                session.inbox.takeNotices()
            } catch (e: Exception) {
                reportFailure(session, e, R.string.error_title_generation)
            }
        }
        session.setJob(job)
        return job
    }

    private fun List<UIMessagePart.Notice>.toNoticeNode() =
        UIMessage(role = MessageRole.USER, parts = this).toMessageNode()

    // 发送、重新生成和审批失败时报告错误并暂停队列，排队的消息等用户处理完再继续
    private fun reportFailure(session: ConversationSession, error: Exception, @StringRes title: Int) {
        if (error is CancellationException) throw error
        session.inbox.pause()
        addError(error, session.id, title = context.getString(title))
    }

    // ---- 重新生成消息 ----

    /** 重新生成用户消息时去掉它之后的节点再回复；重新生成助手消息时在它的节点上新开一个分支 */
    fun regenerateAtMessage(conversationId: Uuid, messageId: Uuid) {
        val session = sessionManager.getOrCreate(conversationId)
        synchronized(session) {
            val previousJob = session.getJob()
            val job = launchGenerationJob(conversationId = conversationId) {
                try {
                    previousJob?.join()
                    session.replyMessageId.value = null
                    ensureInitialized(session)
                    val node = session.state.value.getMessageNodeByMessageId(messageId)
                        ?: return@launchGenerationJob

                    if (node.role == MessageRole.USER) {
                        var todosCleared = false
                        session.tryUpdate { conversation ->
                            val index = conversation.messageNodes.indexOfFirst { it.id == node.id }
                            // 查找之后这条消息被删掉了
                            if (index == -1) return@tryUpdate null
                            val keptNodes = conversation.messageNodes.take(index + 1)
                            // 截掉的消息里就有清单的来源时，清单一起清掉
                            val ownerRemoved = conversation.todosOwnerNodeId?.let { owner ->
                                keptNodes.none { it.id == owner }
                            } == true
                            if (ownerRemoved) todosCleared = true
                            conversation.copy(
                                messageNodes = keptNodes,
                                todos = if (ownerRemoved) emptyList() else conversation.todos,
                                todosOwnerNodeId = if (ownerRemoved) null else conversation.todosOwnerNodeId,
                            )
                        } ?: return@launchGenerationJob
                        session.flush()
                        // 整条保存会把库里还在的清单当成内存没同步捡回来，清空要单独落库
                        if (todosCleared) conversationRepo.updateTodos(conversationId, emptyList())
                        runTurn(session)
                    } else {
                        runTurn(session, regenerateNodeId = node.id)
                    }

                    _generationDoneFlow.emit(conversationId)
                } catch (e: Exception) {
                    reportFailure(session, e, R.string.error_title_regenerate_message)
                }
            }

            session.setJob(job)
        }
    }

    // ---- 处理工具调用审批 ----

    fun handleToolApproval(
        conversationId: Uuid,
        toolCallId: String,
        approved: Boolean,
        reason: String = "",
        answer: String? = null,
    ) {
        val session = sessionManager.getOrCreate(conversationId)
        synchronized(session) {
            val previousJob = session.getJob()

            val hasOtherPendingTools = session.state.value.currentMessages.any { message ->
                message.getTools().any { it.isPending && it.toolCallId != toolCallId }
            }

            val job = launchGenerationJob(
                conversationId = conversationId,
                keepAliveInBackground = !hasOtherPendingTools,
            ) {
                try {
                    afterPreviousGeneration(previousJob) {
                        ensureInitialized(session)
                        val newApprovalState = when {
                            answer != null -> ToolApprovalState.Answered(answer)
                            approved -> ToolApprovalState.Approved
                            else -> ToolApprovalState.Denied(reason)
                        }

                        val updated = session.tryUpdate { it.withToolApproval(toolCallId, newApprovalState) }
                            ?: return@afterPreviousGeneration
                        session.flush()

                        // Only continue generation when all pending tools are handled
                        if (!updated.isAwaitingToolApproval) {
                            runTurn(session)
                        }

                        _generationDoneFlow.emit(conversationId)
                    }
                } catch (e: Exception) {
                    reportFailure(session, e, R.string.error_title_tool_approval)
                }
            }

            session.setJob(job, cancelPrevious = false)
        }
    }

    // ---- 生成回复 ----

    /**
     * 生成一轮回复：请求模型、执行工具，直到模型不再调用工具、有工具需要审批或达到步数上限。
     *
     * 默认接着当前分支的末尾回复；[regenerateNodeId] 不为空时只发送这个节点之前的消息，回复作为它的新分支。
     * 发送的最后一条是助手消息时从它继续（比如审批之后），回复写回那条消息。
     * 开了自动压缩时，工具执行完发现上下文占用达到阈值就在这里压缩，之后的步骤写进检查点后面新的一条回复。
     * 失败时报告错误、暂停消息队列并返回 [TurnResult.Failed]；取消时直接抛出。
     */
    private suspend fun runTurn(
        session: ConversationSession,
        regenerateNodeId: Uuid? = null,
    ): TurnResult {
        val conversationId = session.id
        // 先清理上一轮状态；确定历史后，仅为真正的续写恢复消息 ID。
        session.replyMessageId.value = null
        // 生成开始前失败时通知只用来取消 Live Update，用不到发送者名称
        var senderName = ""
        try {
            val settings = settingsStore.awaitLoaded()
            // 清掉上一轮的建议；没法继续执行的工具调用发给模型会被拒绝，先去掉。
            // 重新生成时只清理要发给模型的节点，被重新生成的节点保持原样，旧回复留作分支
            val conversation = session.update { latest ->
                val untilNode = regenerateNodeId
                    ?.let { nodeId -> latest.messageNodes.indexOfFirst { it.id == nodeId } }
                    ?.takeIf { it != -1 }
                    ?: latest.messageNodes.size
                latest.copy(chatSuggestions = emptyList()).withoutUnresumableToolCalls(untilNode)
            }

            var target = replyTargetOf(conversation, regenerateNodeId)
            // 接着上一条助手消息写时就是它，否则等第一次输出。要在 createContext 之前换掉，
            // 它会挂起也可能失败，期间或失败后不能还指着上一轮的回复
            session.replyMessageId.value = target.continuedMessageId
            // 中途压缩把检查点插在回复之后；重新生成较早的消息时后面还有别的节点，不压缩
            val canCompress = regenerateNodeId == null ||
                conversation.messageNodes.lastOrNull()?.id == regenerateNodeId

            val turn = requestFactory.createContext(settings, conversation)
            senderName = if (turn.assistant.useAssistantAvatar) {
                turn.assistant.name.ifEmpty { context.getString(R.string.assistant_page_default_assistant) }
            } else {
                turn.model.displayName
            }
            warnIfToolsUnavailable(turn, conversationId)

            // 清单和引导的开关、间隔按最新设置走：一轮可能跑很久，用户中途关掉插入要当步生效。
            // 其它字段仍用 turn 的快照，避免一轮里换模型或改系统提示词
            val liveAssistant: suspend () -> Assistant = {
                settingsStore.awaitLoaded().getAssistantOf(session.state.value)
            }
            var finishReason: AgentFinishReason? = null
            // 上一段是压缩后新开的；摘要加系统提示词本身就超过阈值时再压也没用，这一轮不再压缩
            var afterCompression = false
            var compressionUseless = false
            try {
                while (true) {
                    val replyNodeId = target.nodeId
                    val freshReply = afterCompression
                    val (request, hooks) = requestFactory.createAgent(
                        turn = turn,
                        messages = target.history,
                        processingStatus = session.processingStatus,
                        pauseAfterTools = pause@{ message ->
                            if (!canCompress || compressionUseless) return@pause false
                            // 同一条回复只试一次，压缩失败后接着它写时不再重试
                            if (message.id == session.autoCompressedReplyId) return@pause false
                            if (!compressor.reachesAutoCompressThreshold(turn.model, turn.assistant, message.contextTokens())) {
                                return@pause false
                            }
                            if (freshReply && (message.stepCount ?: 0) <= 1) {
                                compressionUseless = true
                                return@pause false
                            }
                            true
                        },
                        liveAssistant = liveAssistant,
                        // 价目表中途改了要当步生效：下一步请求的入账和消息费用都按新价目算
                        livePricing = { model ->
                            settingsStore.awaitLoaded().findModelById(model.id)?.pricing
                        },
                        // 全部完成或取消后不再贴，和轮次开始时的判断保持一致
                        liveTodos = {
                            session.state.value.todos.takeIf { it.hasActiveTodo() } ?: emptyList()
                        },
                        // 识别结果写回会话里的图片，随后落库：下一轮、重新生成、切模型后都不用再识别一次
                        onOcrResults = { results ->
                            if (session.tryUpdate { it.withOcrResults(results) } != null) {
                                session.flush()
                            }
                        },
                        // 视频识别失败这类输入错误直接弹到界面上，别只写进请求里等模型转述
                        onInputError = { error, title ->
                            addError(error, conversationId, title = title)
                        },
                        takePendingParts = { current ->
                            session.takeInjectionParts(current, liveAssistant())
                        },
                    )
                    finishReason = null
                    runAgent(request, hooks).flowOn(Dispatchers.IO).collect { event ->
                        when (event) {
                            is AgentEvent.MessageUpdated -> {
                                // 生成过程中只有网络重试的提示会留着，收到新内容说明重试已经成功
                                session.processingStatus.value = null
                                session.update { it.upsertMessage(replyNodeId, event.message) }
                                session.replyMessageId.value = event.message.id
                                // 内存更新完就把这一帧投给草稿文件：进程被杀时这一轮还能从这里恢复
                                draftStore.record(conversationId, replyNodeId, event.message)
                                // 通知等边缘副作用由 ChatNotificationManager 消费；
                                // tryEmit 不挂起，事件丢失只影响单次通知更新，不能反压生成链
                                appEventBus.tryEmit(
                                    AppEvent.ChatGenerationUpdate(conversationId, event.message, senderName)
                                )
                            }

                            is AgentEvent.Retrying -> {
                                logRetry(event.attempt)
                                session.processingStatus.value = retryStatus(event.attempt)
                            }

                            is AgentEvent.Finished -> {
                                Log.i(TAG, "runTurn: finished (${event.reason})")
                                finishReason = event.reason
                            }
                        }
                    }
                    if (finishReason != AgentFinishReason.Paused) break

                    afterCompression = compressor.compressMidTurn(session)
                    // 压缩成功：检查点在末尾，新开一条回复；失败：接着刚才那条回复写
                    target = replyTargetOf(session.state.value, regenerateNodeId = null)
                    session.replyMessageId.value = target.continuedMessageId
                }
            } finally {
                withContext(NonCancellable) {
                    session.processingStatus.value = null
                    // 可能被取消了，或者意外结束，兜底保存
                    val finished = session.finishGeneration()
                    // 成功、失败、取消都走这里：内容已经落库，草稿留着只会被下次打开当成更新的一份恢复出来
                    draftStore.clear(conversationId)
                    // 生成结束：取消 Live Update 通知，后台时发送完成通知
                    appEventBus.emit(
                        AppEvent.ChatGenerationEnded(
                            conversationId = conversationId,
                            senderName = senderName,
                            contentPreview = finished.currentMessages.lastOrNull()
                                ?.toText()?.take(50)?.trim() ?: "",
                        )
                    )
                }
            }

            val reason = checkNotNull(finishReason)
            // 等待审批时这一轮还没结束，标题、建议和记忆整理等审批之后再做
            if (reason !is AgentFinishReason.AwaitingApproval) {
                sessionManager.launchWithSession(conversationId) { generateTitle(conversationId) }
                sessionManager.launchWithSession(conversationId) { generateSuggestion(conversationId) }
                if (turn.assistant.enableMemory) memoryConsolidationScheduler.onTurnFinished(session.state.value)
            }
            return TurnResult.Finished(reason)
        } catch (e: CancellationException) {
            appEventBus.tryEmit(AppEvent.ChatGenerationEnded(conversationId, senderName, null))
            throw e
        } catch (e: InvalidMcpServerNamesException) {
            session.inbox.pause()
            addError(
                error = IllegalStateException(
                    context.getString(R.string.error_mcp_invalid_server_name, e.names.joinToString(", "))
                ),
                conversationId = conversationId,
            )
            return TurnResult.Failed
        } catch (e: Exception) {
            // 兜底取消 Live Update 通知（生成开始前失败时上面的 finally 不会执行）
            appEventBus.tryEmit(AppEvent.ChatGenerationEnded(conversationId, senderName, null))
            session.inbox.pause()
            e.printStackTrace()
            addError(e, conversationId, title = context.getString(R.string.error_title_generation))
            Logging.log(TAG, "runTurn: $e")
            Logging.log(TAG, e.stackTraceToString())
            return TurnResult.Failed
        }
    }

    // 模型不支持工具调用时，搜索和 MCP 工具都不会生效，提醒一下
    private fun warnIfToolsUnavailable(turn: ChatTurnContext, conversationId: Uuid) {
        if (ModelAbility.TOOL in turn.model.abilities) return
        if (shouldUseExternalWebSearch(turn.assistant, turn.model) ||
            mcpManager.getAllAvailableTools(turn.assistant).isNotEmpty()
        ) {
            addError(
                IllegalStateException(context.getString(R.string.tools_warning)),
                conversationId,
                title = context.getString(R.string.error_title_tool_unavailable)
            )
        }
    }

    private fun logRetry(attempt: RetryAttempt) {
        Log.w(
            TAG,
            "Provider connection failed, retrying in ${attempt.delay} (${attempt.retry}/${attempt.maxRetries})",
            attempt.cause,
        )
    }

    private fun retryStatus(attempt: RetryAttempt): String {
        val reason = when (attempt.cause) {
            is UnknownHostException -> R.string.chat_generation_network_unknown_host
            is SocketTimeoutException -> R.string.chat_generation_network_timeout
            is ConnectException, is NoRouteToHostException -> R.string.chat_generation_network_unreachable
            else -> R.string.chat_generation_network_disconnected
        }
        return context.getString(
            R.string.chat_generation_network_retrying,
            context.getString(reason),
            attempt.retry,
            attempt.maxRetries,
        )
    }

    private suspend fun finishInterruptedPendingTools(session: ConversationSession) {
        session.tryUpdate { it.cancelInterruptedTools() }
        session.flush()
    }

    // ---- 生成标题 ----

    suspend fun generateTitle(
        conversationId: Uuid,
        force: Boolean = false
    ): Unit = withContext(Dispatchers.IO) {
        withLoadedSession<Unit>(conversationId) { session ->
            val incarnation = session.incarnation
            val conversation = session.state.value
            if (!force && conversation.title.isNotBlank()) return@withLoadedSession

            val settings = settingsStore.awaitLoaded()
            // 关掉自动生成后只跳过对话结束时的自动调用，手动重新生成照常执行
            if (!force && !settings.enableAutoTitle) return@withLoadedSession

            runCatching {
                val title = metaGenerator.generateTitle(settings, conversation)
                // 只写标题这一列：这时可能已经在生成下一轮，不能把还没生成完的内容一起落库。
                // 生成期间会话被删除的话不写入
                session.updateMetadata(
                    transform = { it.copy(title = title) },
                    persist = { conversationRepo.updateTitle(conversationId, title) },
                    expectedIncarnation = incarnation,
                )
            }.onFailure {
                it.printStackTrace()
                addError(
                    error = it,
                    conversationId = conversationId,
                    title = context.getString(R.string.error_title_generate_title),
                    solution = ChatErrorSolution.CheckFastModelSettings,
                )
            }
        }
    }

    // ---- 生成建议 ----

    private suspend fun generateSuggestion(conversationId: Uuid): Unit = withContext(Dispatchers.IO) {
        sessionManager.withSession<Unit>(conversationId) { session ->
            runCatching {
                val settings = settingsStore.awaitLoaded()
                if (!settings.enableSuggestion) return@runCatching

                // 上一轮的建议在这一轮开始时已经清掉了
                ensureInitialized(session)
                val incarnation = session.incarnation
                val suggestions = metaGenerator.generateSuggestions(settings, session.state.value)
                    ?: return@runCatching

                // 只写建议这一列，理由同标题
                session.updateMetadata(
                    transform = { it.copy(chatSuggestions = suggestions) },
                    persist = { conversationRepo.updateChatSuggestions(conversationId, suggestions) },
                    expectedIncarnation = incarnation,
                )
            }.onFailure {
                it.printStackTrace()
            }
        }
    }

    // ---- 压缩对话历史 ----

    /**
     * 手动压缩：把上一个检查点之后、最近 [keepRecentMessages] 条之前的消息压成摘要检查点。
     *
     * [keepRecentMessages] 为 0 时压缩全部消息，请求和上一轮对话前缀一致，缓存命中最好；
     * 大于 0 时最近的这些消息不参与压缩、留在检查点之后，对话已经超出模型上下文、整段发不出去时用来缩小压缩请求。
     */
    suspend fun compressConversation(
        conversationId: Uuid,
        additionalPrompt: String,
        keepRecentMessages: Int = 0,
    ): Result<Unit> = try {
        withLoadedSession<Unit>(conversationId) { session ->
            val result = CompletableDeferred<Unit>()
            val job = synchronized(session) {
                // 生成中的回复还没写完，压缩不到它，检查点也不能插到它前面。
                // 等审批时允许压缩：保留区可以把待审批的工具留在检查点之后
                check(session.getJob() == null) { context.getString(R.string.chat_page_compress_blocked_generating) }
                compressor.launchCompressionJob(
                    session = session,
                    additionalPrompt = additionalPrompt,
                    keepRecentMessages = keepRecentMessages,
                    clearSuggestions = true,
                ) { outcome -> result.completeWith(outcome) }
            }
            try {
                result.await()
            } catch (e: CancellationException) {
                // 压缩弹窗取消时调用方被取消，压缩任务跟着取消
                job.cancel()
                throw e
            }
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ---- 聊天页配置 ----

    /**
     * 聊天页对助手的修改：会话持有的字段只改当前会话，其余写回助手设置。
     *
     * @param update 接收会话视角下的助手，返回修改后的助手
     */
    suspend fun updateChatAssistant(conversationId: Uuid, update: (Assistant) -> Assistant) {
        withLoadedSession(conversationId) { session ->
            val settings = settingsStore.awaitLoaded()
            val conversation = session.state.value
            val stored = settings.getStoredAssistantOf(conversation)
            val current = settings.getAssistantOf(conversation)
            // 文件夹接管的只是「这个分组用哪组工作区」，不是会话自己的绑定：
            // 界面里那份助手带着文件夹的覆盖，写回前还原，离开文件夹后会话才不会留着上一个分组的工作区
            val folderBinding = folderRepository.workspaceBindingOf(conversation)
            val updated = update(current.withWorkspaceBinding(folderBinding)).let { result ->
                if (folderBinding.isEmpty) {
                    result
                } else {
                    result.copy(workspaceId = current.workspaceId, workspaceIds = current.workspaceIds)
                }
            }
            val assistant = updated.withoutConversationFields(conversation, stored)
            session.updateMetadata(
                transform = { it.withAssistantUpdate(updated, settings) },
                persist = conversationRepo::updateConversationConfig,
            )
            if (assistant != stored) {
                settingsStore.update { latest ->
                    latest.copy(assistants = latest.assistants.map { if (it.id == assistant.id) assistant else it })
                }
            }
        }
    }

    /**
     * 切换搜索方式，传 null 的一项保持不变。
     * 模型内置搜索在会话开始前是模型自身的开关，开始后固定在会话上。
     */
    suspend fun updateChatSearch(
        conversationId: Uuid,
        enableWebSearch: Boolean? = null,
        builtInSearch: Boolean? = null,
    ) {
        withLoadedSession(conversationId) { session ->
            val conversation = session.state.value
            if (conversation.config != null) {
                session.updateMetadata(
                    transform = {
                        it.copy(
                            config = it.config?.let { config ->
                                config.copy(
                                    enableWebSearch = enableWebSearch ?: config.enableWebSearch,
                                    builtInSearch = builtInSearch ?: config.builtInSearch,
                                )
                            }
                        )
                    },
                    persist = conversationRepo::updateConversationConfig,
                )
                return@withLoadedSession
            }
            settingsStore.update { settings ->
                val assistant = settings.getAssistantOf(conversation)
                val model = settings.getChatModelOf(conversation)
                settings.copy(
                    assistants = if (enableWebSearch == null) {
                        settings.assistants
                    } else {
                        settings.assistants.map {
                            if (it.id == assistant.id) it.copy(enableWebSearch = enableWebSearch) else it
                        }
                    },
                    providers = if (builtInSearch == null || model == null) {
                        settings.providers
                    } else {
                        settings.providers.map { provider ->
                            provider.editModel(
                                model.copy(
                                    tools = if (builtInSearch) {
                                        model.tools + BuiltInTools.Search
                                    } else {
                                        model.tools - BuiltInTools.Search
                                    }
                                )
                            )
                        }
                    },
                )
            }
        }
    }

    // 工作目录和配置一起按列落库；还没落过库的新会话先留在内存里，发出第一条消息时一起写入
    suspend fun updateWorkspaceCwd(conversationId: Uuid, workspaceId: Uuid, workspaceCwd: String?) {
        withLoadedSession(conversationId) { session ->
            session.updateMetadata(
                transform = { conversation ->
                    val key = workspaceId.toString()
                    val cwds = if (workspaceCwd.isNullOrBlank()) {
                        conversation.workspaceCwds - key
                    } else {
                        conversation.workspaceCwds + (key to workspaceCwd)
                    }
                    conversation.copy(workspaceCwds = cwds)
                },
                persist = conversationRepo::updateConversationConfig,
            )
        }
    }

    suspend fun updateConversationSystemPrompt(conversationId: Uuid, systemPrompt: String?) {
        updateColumn(
            conversationId = conversationId,
            transform = { it.copy(customSystemPrompt = systemPrompt) },
            persist = { conversationRepo.updateCustomSystemPrompt(conversationId, systemPrompt) },
        )
    }

    suspend fun updateTitle(conversationId: Uuid, title: String) {
        updateColumn(
            conversationId = conversationId,
            transform = { it.copy(title = title) },
            persist = { conversationRepo.updateTitle(conversationId, title) },
        )
    }

    /**
     * 聊天页顶栏改会话文件夹：还没落过库的新会话只改内存，首次落库时一并写入；
     * 已落库的会话只更新这一列。
     */
    suspend fun updateConversationFolder(conversationId: Uuid, folderId: Uuid?) {
        updateColumn(
            conversationId = conversationId,
            transform = { it.copy(folderId = folderId) },
            persist = { conversationRepo.updateConversationFolderId(conversationId, folderId) },
        )
    }

    /**
     * 用户在聊天页改的单列字段：已落库的会话只写这一列，生成中也不会顺带写入还没生成完的回复；
     * 还没落过库的新会话整份落库，留下记录（没有内容的新会话仍然不写）。
     */
    private suspend fun updateColumn(
        conversationId: Uuid,
        transform: (Conversation) -> Conversation,
        persist: suspend () -> Unit,
    ) {
        withLoadedSession(conversationId) { session ->
            session.updateMetadata(transform, persist = { persist() })
            if (!session.existsInDatabase) session.flush()
        }
    }

    // ---- 会话元数据 ----

    private suspend fun updateConversationMetadata(
        conversationId: Uuid,
        transform: (Conversation) -> Conversation,
        persist: suspend (Conversation) -> Unit,
    ) {
        withLoadedSession(conversationId, createIfMissing = false) { session ->
            session.updateMetadata(transform, persist)
        }
    }

    suspend fun toggleConversationPinned(conversationId: Uuid) {
        updateConversationMetadata(
            conversationId = conversationId,
            transform = { it.copy(isPinned = !it.isPinned) },
            persist = { conversationRepo.updatePinStatus(conversationId, it.isPinned) },
        )
    }

    suspend fun moveConversationToAssistant(conversationId: Uuid, assistantId: Uuid) {
        updateConversationMetadata(
            conversationId = conversationId,
            // 文件夹属于助手，移动后清除原助手的文件夹归属。
            transform = { it.copy(assistantId = assistantId, folderId = null) },
            persist = { conversationRepo.updateConversationAssistant(conversationId, it.assistantId) },
        )
    }

    /**
     * 移动会话到文件夹（folderId 为 null 表示移出到未归类）。
     * 有 session 的会话先改内存，免得之后整份落库时写回旧的文件夹；没有的只改这一列，不必加载整个会话。
     */
    suspend fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?) {
        if (sessionManager.get(conversationId) == null) {
            conversationRepo.updateConversationFolderId(conversationId, folderId)
            // 写库期间页面可能刚好打开了这个会话，加载时读到的可能是旧值，再同步一次内存
            if (sessionManager.get(conversationId) == null) return
        }
        updateConversationMetadata(
            conversationId = conversationId,
            transform = { it.copy(folderId = folderId) },
            persist = { conversationRepo.updateConversationFolderId(conversationId, folderId) },
        )
    }

    /**
     * 还没开始的新会话跟随抽屉里选中的文件夹，首次落库时归入；已经落库的会话不受影响，移动走 [moveConversationToFolder]。
     */
    suspend fun updateDraftConversationFolder(conversationId: Uuid, folderId: Uuid?) {
        val folder = folderId?.let { folderRepository.getFolderById(it) }
        withLoadedSession(conversationId) { session ->
            session.updateDraft { latest ->
                // 文件夹属于助手：已被删除或不属于会话的助手时不归入
                val target = folder?.takeIf { it.assistantId == latest.assistantId }?.id
                if (latest.folderId == target) latest else latest.copy(folderId = target)
            }
        }
    }

    /**
     * 文件夹内是否存在正在生成回复的会话。
     * 仅活跃 session 可能在生成；内存态 folderId 为权威（移动会先同步内存态）。
     */
    fun hasGeneratingConversationInFolder(folderId: Uuid): Boolean {
        return sessionManager.snapshot().any { it.isGenerating && it.state.value.folderId == folderId }
    }

    /**
     * 删除文件夹（folder_id 归属会被清空，会话本身保留）。
     * 已加载的会话先改内存再删库，之后落库不会写回已删除的文件夹。
     */
    suspend fun deleteFolder(folderId: Uuid) {
        sessionManager.snapshot()
            .filter { it.isInitialized && it.state.value.folderId == folderId }
            .forEach { session ->
                // 这一列由下面删文件夹时统一清空，这里只改内存，不让会话因此变成有未落库的修改
                session.updateMetadata(
                    transform = { if (it.folderId == folderId) it.copy(folderId = null) else it },
                    persist = {},
                )
            }
        folderRepository.deleteFolder(folderId)
    }

    // ---- 删除会话 ----

    /**
     * 删除会话：停掉生成后在会话的写锁内删库，内存换成一个还没落过库的新会话。
     * 还停在这个会话上的页面看到的是空会话，之后再发消息会新建记录，不会写回已删除的那条。
     */
    suspend fun deleteConversation(conversationId: Uuid) {
        val session = sessionManager.getOrCreate(conversationId)
        // 正在生成的这一轮必须先停掉：否则它会把内容写回刚被清空的会话，甚至把会话重新落库
        val jobs = synchronized(session) {
            session.inbox.pause()
            session.cancelJobs()
        }
        jobs.forEach { job -> runCatching { job.join() } }
        discardQueuedMessages(session)
        // 有人在工具调用里等这个子代理的话，先告诉他会话没了，别干等到超时
        gateway.abortWaiter(
            conversationId,
            "Sub-agent conversation $conversationId was deleted before it produced a result. Do not wait for it again.",
        )
        session.replace(lifecycle.newConversation(conversationId), existsInDatabase = false) { previous ->
            conversationRepo.getConversationById(conversationId)?.let { conversationRepo.deleteConversation(it) }
            // 数据库那份不知道只在内存里出现过的附件
            previous?.let { filesManager.deleteChatFiles(it.files) }
        }
        // 会话没了草稿也没用了，否则同一个 id 再打开会恢复出已经删掉的内容
        draftStore.clear(conversationId)
        // 待投递的子代理结果没有接收方了
        gateway.clearPendingResults(conversationId)
        lifecycle.forgetDraftRestore(conversationId)
        sessionManager.removeIfIdle(session)
        // 后台命令随会话一起终止, 撤销删除也不会恢复; 终止要等宽限期, 不阻塞删除
        appScope.launch { backgroundShellManager.killConversation(conversationId) }
    }

    suspend fun deleteConversationsOfAssistant(assistantId: Uuid) {
        val persisted = conversationRepo.getConversationsOfAssistant(assistantId).first().map { it.id }
        val loaded = sessionManager.snapshot()
            .filter { it.isInitialized && it.state.value.assistantId == assistantId }
            .map { it.id }
        (persisted + loaded).distinct().forEach { deleteConversation(it) }
    }

    private fun discardQueuedMessages(session: ConversationSession) {
        session.inbox.pause()
        session.inbox.state.value.messages.forEach { queued ->
            (session.inbox.remove(queued.id) as? QueuedMessage)?.let(::cleanupQueuedAttachments)
        }
        session.inbox.takeNotices()
    }

    // ---- 翻译消息 ----

    fun translateMessage(
        conversationId: Uuid,
        message: UIMessage,
        targetLanguage: Locale
    ) {
        appScope.launch(Dispatchers.IO) {
            sessionManager.withSession<Unit>(conversationId) { session ->
                try {
                    ensureInitialized(session)
                    val settings = settingsStore.awaitLoaded()

                    val messageText = message.parts.filterIsInstance<UIMessagePart.Text>()
                        .joinToString("\n\n") { it.text }
                        .trim()

                    if (messageText.isBlank()) return@withSession

                    // Set loading state for translation
                    val loadingText = context.getString(R.string.translating)
                    session.update { it.withTranslation(message.id, loadingText) }

                    translationHandler.translateText(
                        settings = settings,
                        sourceText = messageText,
                        targetLanguage = targetLanguage
                    ) { translatedText ->
                        // Update translation field in real-time
                        session.update { it.withTranslation(message.id, translatedText) }
                    }.collect { /* Final translation already handled in onStreamUpdate */ }

                    session.flush()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Clear translation field on error
                    if (session.isInitialized) session.update { it.withTranslation(message.id, null) }
                    addError(e, conversationId, title = context.getString(R.string.error_title_translate_message))
                }
            }
        }
    }

    suspend fun clearTranslationField(conversationId: Uuid, messageId: Uuid) {
        lifecycle.mutate(conversationId) { it.withTranslation(messageId, null) }
    }

    // ---- 消息操作 ----

    suspend fun editMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>
    ) {
        if (parts.isEmptyInputMessage()) return
        val settings = settingsStore.awaitLoaded()

        lifecycle.mutate(conversationId) { conversation ->
            conversation.editMessage(messageId, parts.replaceUserRegexes(settings.getStoredAssistantOf(conversation)))
        }
    }

    suspend fun forkConversationAtMessage(
        conversationId: Uuid,
        messageId: Uuid
    ): Conversation {
        val source = withLoadedSession(conversationId) { it.state.value }
        val existingTitles = conversationRepo.getTitlesOfAssistant(source.assistantId)
        val fork = source.fork(messageId, existingTitles, copyFile = ::copyChatFile)
            ?: throw NotFoundException("Message not found")
        // 新会话直接落库，打开时再从数据库加载
        val forkConversation = conversationStore.prepareWrite()(fork)
        conversationRepo.insertConversation(forkConversation)
        memoryConsolidationScheduler.onConversationForked(forkConversation)
        return forkConversation
    }

    private fun copyChatFile(url: String): String =
        filesManager.createChatFilesByContents(listOf(url.toUri())).firstOrNull()?.toString() ?: url

    suspend fun selectMessageNode(
        conversationId: Uuid,
        nodeId: Uuid,
        selectIndex: Int
    ) {
        lifecycle.mutate(conversationId) { conversation ->
            val targetNode = conversation.messageNodes.firstOrNull { it.id == nodeId }
                ?: throw NotFoundException("Message node not found")
            if (selectIndex !in targetNode.messages.indices) {
                throw BadRequestException("Invalid selectIndex")
            }
            if (targetNode.selectIndex == selectIndex) return@mutate conversation
            conversation.selectBranch(nodeId, selectIndex)
        }
    }

    // 收藏记录在单独的表里，节点上的标记只用于显示，不需要落库
    suspend fun setNodeFavorite(conversationId: Uuid, nodeId: Uuid, favorite: Boolean) {
        val session = sessionManager.get(conversationId)?.takeIf { it.isInitialized } ?: return
        session.updateMetadata(transform = { it.withNodeFavorite(nodeId, favorite) }, persist = {})
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        messageId: Uuid,
        failIfMissing: Boolean = true,
    ) {
        var todosCleared = false
        lifecycle.mutate(conversationId) { conversation ->
            val nodeId = conversation.getMessageNodeByMessageId(messageId)?.id
            val updated = conversation.deleteMessage(messageId)
                ?: if (failIfMissing) throw NotFoundException("Message not found") else conversation
            // 清单是这条消息写下的，消息没了清单也就没有来源
            if (nodeId != null && nodeId == updated.todosOwnerNodeId) {
                todosCleared = true
                updated.copy(todos = emptyList(), todosOwnerNodeId = null)
            } else {
                updated
            }
        }
        // 整条保存会把库里还在的清单当成内存没同步捡回来，清空要单独落库
        if (todosCleared) conversationRepo.updateTodos(conversationId, emptyList())
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        message: UIMessage,
    ) {
        deleteMessage(conversationId, message.id, failIfMissing = false)
    }

    // 停止当前会话生成任务（不清理会话缓存）
    suspend fun stopGeneration(conversationId: Uuid) {
        val session = sessionManager.get(conversationId) ?: return
        val jobs = synchronized(session) {
            session.inbox.pause()
            session.cancelJobs()
        }
        if (jobs.isEmpty()) return
        jobs.forEach { it.join() }
        if (session.isInitialized) finishInterruptedPendingTools(session)
        draftStore.clear(conversationId)
    }
}
