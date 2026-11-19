package me.rerere.rikkahub.service

import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid
import me.rerere.rikkahub.data.chat.DraftRestore
import me.rerere.rikkahub.data.chat.DraftStore
import me.rerere.rikkahub.data.chat.applyDraftRestore
import me.rerere.rikkahub.data.chat.decideDraftRestore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.bindConfig
import me.rerere.rikkahub.data.model.closeInterruptedReasoning
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.web.NotFoundException

/**
 * 会话的加载与接手收尾：从库里读、缺库时按需新建、补上一轮被进程杀掉留下的草稿与思考段。
 *
 * 从 ChatService 拆出，它自己不认识发送、压缩、子代理这些上层逻辑；那边按需经 [withLoaded] 或
 * [ensureInitialized] 拿到已加载的会话，再在自己的写锁里做事。
 */
internal class ConversationLifecycle(
    private val sessionManager: ConversationSessionManager,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val draftStore: DraftStore,
    /** 落库后重新检查消息队列；删除消息、切分支都可能解除工具审批阻塞 */
    private val onFlushed: (Uuid) -> Unit = {},
) {
    // 每个会话在进程里只恢复一次草稿：之后进页面、重连都不再拿旧草稿盖库里的内容
    private val draftRestored = ConcurrentHashMap.newKeySet<Uuid>()

    /** 持有会话引用并确保已加载后执行 [block]；[createIfMissing] 为 false 时数据库里没有就抛 NotFoundException */
    suspend fun <T> withLoaded(
        conversationId: Uuid,
        createIfMissing: Boolean = true,
        block: suspend (ConversationSession) -> T,
    ): T = sessionManager.withSession(conversationId) { session ->
        session.initialize(
            load = { loadConversation(conversationId) },
            create = {
                if (!createIfMissing) throw NotFoundException("Conversation not found")
                newConversation(conversationId)
            },
        )
        block(session)
    }

    suspend fun ensureInitialized(session: ConversationSession) {
        session.initialize(
            load = { loadConversation(session.id) },
            create = { newConversation(session.id) },
        )
        recoverInterruptedTurn(session)
    }

    /**
     * 加载会话后在最新状态上修改并落库。
     * 删除消息或切换分支也可能解除工具审批阻塞，落库后重新检查队列。
     */
    suspend fun mutate(conversationId: Uuid, transform: (Conversation) -> Conversation): Conversation =
        withLoaded(conversationId) { session ->
            session.update(transform)
            session.flush().also { onFlushed(conversationId) }
        }

    /** 会话删掉后同一个 id 再打开不该把旧草稿恢复出来 */
    fun forgetDraftRestore(conversationId: Uuid) {
        draftRestored.remove(conversationId)
    }

    /**
     * 上一轮被进程杀掉留下的两样东西：写了一半的内容在草稿里，思考段的收尾时刻根本没人写。
     * 进页面、发消息等所有入口都在会话加载完后走这一遍，每个会话每个进程一次。
     */
    suspend fun recoverInterruptedTurn(session: ConversationSession) {
        restoreDraft(session)
        sealInterruptedReasoning(session)
    }

    /**
     * 上一轮没来得及收尾的思考段还挂着 `finishedAt = null`，界面会当成「此刻正在写」、
     * 耗时跟着现在的时间一直涨。草稿只能补内容、补不回收尾时刻，所以这里统一收一次尾再让它显示。
     */
    private suspend fun sealInterruptedReasoning(session: ConversationSession) {
        // 正在生成的那一轮自己会收尾，不能把此刻真正在写的那一段封掉
        if (session.isGenerating) return
        if (session.tryUpdate { it.closeInterruptedReasoning() } == null) return
        runCatching { session.flush() }
    }

    /**
     * 上一轮生成写到一半进程就被杀掉的话，那份内容从来没落过库，这里用草稿补回来：
     * 节点还在就补这一条消息的 parts，节点不在了就把它作为助手消息接在会话末尾。
     * 补完把草稿删掉，下次打开不再恢复一遍。这件事发生在整轮生成之外，和流式循环无关。
     */
    private suspend fun restoreDraft(session: ConversationSession) {
        if (!session.isInitialized) return
        // 正在生成的那一轮自己在写草稿，不能把它当成上次崩掉留下的内容恢复出来
        if (session.isGenerating) return
        if (!draftRestored.add(session.id)) return
        val draft = draftStore.load(session.id) ?: return
        val restore = decideDraftRestore(draft, session.state.value)
        if (restore !is DraftRestore.Discard) {
            session.update { it.applyDraftRestore(restore) }
            // 先把恢复出来的内容落库，再删草稿：这中间被杀也不会丢
            runCatching { session.flush() }
        }
        draftStore.clear(session.id)
    }

    // 新建对话, 并添加预设消息
    suspend fun newConversation(conversationId: Uuid): Conversation {
        val assistant = settingsStore.awaitLoaded().getCurrentAssistant()
        return Conversation.ofId(
            id = conversationId,
            assistantId = assistant.id,
            newConversation = true
        ).updateCurrentMessages(assistant.presetMessages)
    }

    // 引入会话配置之前创建的会话没有固定配置，加载时按助手当前的值补上，之后不再随助手变化。
    suspend fun loadConversation(conversationId: Uuid): Conversation? {
        val conversation = conversationRepo.getConversationById(conversationId) ?: return null
        // settingsFlow 在启动初期还是占位值，固定配置必须基于真实设置
        val bound = conversation.bindConfig(settingsStore.awaitLoaded())
        if (bound !== conversation) conversationRepo.updateConversationConfig(bound)
        return bound
    }
}
