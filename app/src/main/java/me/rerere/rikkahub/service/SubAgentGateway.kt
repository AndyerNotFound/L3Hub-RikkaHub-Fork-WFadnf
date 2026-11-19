package me.rerere.rikkahub.service

import android.app.Application
import android.util.Log
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_NOTICE_FRAMING
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_STATE_COMPLETED
import me.rerere.rikkahub.data.ai.subagent.SUB_AGENT_STATE_FAILED
import me.rerere.rikkahub.data.ai.subagent.SubAgentResultStore
import me.rerere.rikkahub.data.ai.subagent.SubAgentWaits
import me.rerere.rikkahub.data.ai.subagent.formatSubAgentRoundResult
import me.rerere.rikkahub.data.ai.subagent.formatSubAgentResults
import me.rerere.rikkahub.data.ai.subagent.subAgentLabel
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getChatModelOf
import me.rerere.rikkahub.data.model.getStoredAssistantOf
import me.rerere.rikkahub.data.model.isAwaitingToolApproval
import me.rerere.rikkahub.data.model.isContextLimitReached
import me.rerere.rikkahub.data.model.lastContextTokens
import me.rerere.rikkahub.data.repository.ConversationRepository

private const val TAG = "SubAgentGateway"

/**
 * 子代理的运行时对接：任务树状态回报、await_sub_agent 记账与结果投递。
 * 从 ChatService 拆出；发送门禁经 [sendMessage] 回调接入，避免与队列互相依赖。
 */
internal class SubAgentGateway(
    private val context: Application,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val sessionManager: ConversationSessionManager,
    private val subAgentResultStore: SubAgentResultStore,
    private val subAgentWaits: SubAgentWaits,
    private val sendMessage: (conversationId: Uuid, content: List<UIMessagePart>, notice: Boolean) -> Boolean,
) {
    // 待投递的结果落在 subAgentResultStore 里（父会话正忙、没打开、进程被杀都还在），
    // 这里只记「谁正在投递」：同一时刻只送一条，投递期间新到的结果留到下一轮再合并
    private val deliveringSubAgentResults = mutableSetOf<Uuid>()

    /**
     * 子代理会话每跑完一轮就把这一轮的结果投回父会话。
     *
     * 一轮就是一次生成任务；被子代理回传的结果触发的新一轮同样会走到这里，所以中间层不会只回传第一次。
     * 只在生成任务的完成回调里触发，一个任务只完成一次，因此天然不会重复投递。
     */
    internal suspend fun reportSubAgentRound(session: ConversationSession, cause: Throwable?) {
        val conversation = session.state.value
        val parentConversationId = conversation.parentConversationId ?: return
        // 停止和中途出错都只留下半截回复，不把半截内容当成「子代理完成」
        if (cause != null) {
            setSubAgentState(session, SUB_AGENT_STATE_FAILED)
            // 异常中止：没有结果可回传，记一笔欠账，抵掉下一次正常结尾的自动回传
            subAgentResultStore.recordAbort(parentConversationId, session.id)
            failSubAgentWaiter(
                session,
                if (cause is CancellationException) "it was stopped" else "generation failed",
            )
            return
        }
        val answer = conversation.currentMessages
            .lastOrNull { it.role == MessageRole.ASSISTANT }
            ?.toText()
            ?.trim()
            .orEmpty()
        if (conversation.isAwaitingToolApproval || answer.isBlank()) {
            setSubAgentState(session, SUB_AGENT_STATE_FAILED)
            // 卡在审批上没人会点、回复又是空的：这一轮同样没有结果可回传，按异常中止记账
            subAgentResultStore.recordAbort(parentConversationId, session.id)
            failSubAgentWaiter(
                session,
                if (conversation.isAwaitingToolApproval) {
                    "it is waiting for a tool approval nobody will answer"
                } else {
                    "it produced no text"
                },
            )
            return
        }
        setSubAgentState(session, SUB_AGENT_STATE_COMPLETED)
        // 每轮开头都会追加一条用户消息，用它数这是第几轮：父会话靠这个数区分同一个子代理的多条结果。
        // 压缩检查点存的是一段摘要，角色同样是 USER，但它不是谁说的话，不能算进轮次，
        // 否则子代理会话压过一次之后 round 会跳号
        val round = conversation.currentMessages.count { message ->
            message.role == MessageRole.USER && !message.isContextCheckpoint
        }
        // 每一轮正常结尾都要回传，用户手动拉起的那一轮也算：父会话那边看到的是同一条子代理任务
        val assistantName = subAgentLabel(settingsStore.awaitLoaded().getStoredAssistantOf(conversation))
        val result = formatSubAgentRoundResult(assistantName, session.id, round, answer)
        // 先叫醒等待者：有人接走就说明这一份已经通过 await_sub_agent 回传给父会话了。
        // 这一轮的账是「+1 之后当场 -1」，所以不进队列、流式结尾也不会再当通知送第二遍；
        // 父回合在这之后被打断（工具输出没落库）时，按这条记录把结果退回待回传
        val awaited = subAgentWaits.complete(session.id, result)
        val handedOver = awaited && rememberClaim(parentConversationId, session.id, result)
        if (!handedOver) {
            // 没人接走；或者父回合刚好收尾、这次等待其实没落到会话里——照常入队，流式结尾回传
            deliverSubAgentResult(
                parentConversationId = parentConversationId,
                childConversationId = session.id,
                result = result,
            )
        }
    }

    /** 子代理没跑出结果：叫醒工具调用里的等待者，让他别再干等 */
    private fun failSubAgentWaiter(session: ConversationSession, reason: String) {
        subAgentWaits.complete(
            session.id,
            "Sub-agent conversation ${session.id} ended without a result: $reason. Do not wait for it again.",
        )
    }

    /** 改任务树上的状态；会话被回收或还没加载时只记一条日志，不影响对话本身 */
    private suspend fun setSubAgentState(session: ConversationSession, state: String) {
        if (session.state.value.subAgentState == state) return
        try {
            session.update { it.copy(subAgentState = state) }
            session.flush()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "set sub agent state failed: ${session.id}", e)
        }
    }

    /**
     * background 子代理完成后调用：父会话空闲就立刻把结果当作一条用户消息投递；
     * 正在生成时由 [takeSubAgentNotices] 在下一个工具间隙插进当前这一轮；
     * 停在工具审批上或还没打开时先攒着，等这一轮结束或下次打开会话时合并投递。
     */
    internal suspend fun deliverSubAgentResult(
        parentConversationId: Uuid,
        childConversationId: Uuid,
        result: String,
    ) {
        if (!subAgentResultStore.append(parentConversationId, childConversationId, result)) {
            // 没落盘：这一条只在本进程里能投出去，进程被杀就没了（write 里已经记过 error）
            Log.e(TAG, "deliver sub agent result: not persisted for $parentConversationId")
        }
        flushSubAgentResults(parentConversationId)
    }

    /**
     * await_sub_agent 用：取走某条子会话最早一段还没回传的结果。
     *
     * 取走就算回传（这一段不再当通知投递，账同时 -1）。父回合在这之后被打断、工具输出没落库时，
     * 会按 [claimedInRound] 里这条记录把正文退回待回传，两头都不丢。
     */
    internal suspend fun takePendingSubAgentResult(
        parentConversationId: Uuid,
        childConversationId: Uuid,
    ): String? = withContext(NonCancellable) {
        // 取走和记账要么都做完、要么都不做：卡在中间的话这一段凭空消失，父会话两头都拿不到
        val taken = subAgentResultStore.take(parentConversationId, childConversationId)
            ?: return@withContext null
        if (!rememberClaim(parentConversationId, childConversationId, taken)) {
            // 父回合已经收尾（await 只在一轮生成里跑，正常撞不上）：这一份没人接，退回队列别丢
            subAgentResultStore.append(parentConversationId, childConversationId, taken)
        }
        taken
    }

    /** 一轮生成结束：取走这一轮的交接账；被打断时把正文退回待回传，让这一份照常补投 */
    internal suspend fun settleClaims(sessionId: Uuid, interrupted: Boolean) {
        val claims = takeClaims(sessionId)
        if (interrupted) {
            claims.forEach { claim ->
                subAgentResultStore.append(sessionId, claim.conversationId, claim.text)
            }
        }
    }

    /** 一次交接：哪条子会话的哪一份结果（父回合被打断时按它退回队列） */
    private data class SubAgentClaim(val conversationId: Uuid, val text: String)

    // 这一轮生成里交给 await_sub_agent 的那几份：正常结束就算送到，被打断就退回待回传
    private val claimedInRound = mutableMapOf<Uuid, MutableSet<SubAgentClaim>>()

    /**
     * 记一笔「这一份已经交给父会话」，返回能不能记。
     *
     * 「父回合还在跑」的判断和 [takeClaims] 在同一把锁里：父回合收尾时把整份账取走，
     * 这里要么在取走之前记上（那一轮被打断时会把它退回队列），要么看到回合已经结束、改成入队。
     * 不这样的话，一份结果可能落在结账之后，既没人接管、也不会补投。
     */
    private fun rememberClaim(parentConversationId: Uuid, childConversationId: Uuid, text: String): Boolean =
        synchronized(claimedInRound) {
            if (sessionManager.get(parentConversationId)?.isGenerating != true) {
                return@synchronized false
            }
            claimedInRound.getOrPut(parentConversationId) { mutableSetOf() }
                .add(SubAgentClaim(childConversationId, text))
            true
        }

    private fun takeClaims(parentConversationId: Uuid): Set<SubAgentClaim> =
        synchronized(claimedInRound) { claimedInRound.remove(parentConversationId) }.orEmpty()

    /**
     * 进程重启后的补投。
     *
     * 待投递的结果已经从内存挪到文件里，重启后不会自己消失；这里按会话把它们再送一次，
     * 否则中间层子代理收到孙代理结果也接不上下一轮，整条链停在那里。
     * 普通会话仍然遵守「用户下次打开再送」，只有子代理会话会在这里真的开始生成。
     */
    internal suspend fun resumePendingSubAgentResults() {
        subAgentResultStore.listAll().forEach { conversationId ->
            try {
                flushSubAgentResults(conversationId)
            } catch (e: CancellationException) {
                // AppScope 被取消时别再把剩下的会话跑一遍、每个记一条 warn
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "resume sub agent results failed: $conversationId", e)
            }
        }
    }

    /** 会话被删除：有人还在工具调用里等这个子代理的话，先叫醒他，别干等到超时 */
    internal fun abortWaiter(conversationId: Uuid, reason: String) {
        subAgentWaits.complete(conversationId, reason)
    }

    /** 待投递的子代理结果没有接收方了 */
    internal suspend fun clearPendingResults(conversationId: Uuid) {
        subAgentResultStore.clear(conversationId)
    }

    internal suspend fun flushSubAgentResults(conversationId: Uuid) {
        // 同一会话同一时刻只投递一批：投递期间新到的结果留到下一轮再合并
        synchronized(deliveringSubAgentResults) {
            if (!deliveringSubAgentResults.add(conversationId)) return
        }
        try {
            val pending = subAgentResultStore.peekDeliverable(conversationId)
            if (pending.isEmpty) return
            val session = sessionManager.get(conversationId)
            // 会话没打开时：子代理会话只能自己接着跑，普通会话等用户下次打开再送。
            // 数据库里也没有这个会话，说明父会话已经被删了，结果没有接收方，顺手清掉，
            // 否则这文件会一直躺在磁盘上，每次启动都白扫一遍
            val conversation = if (session == null) {
                conversationRepo.getConversationById(conversationId) ?: run {
                    subAgentResultStore.clear(conversationId)
                    return
                }
            } else {
                session.state.value
            }
            val canDeliver = if (session == null) {
                canDeliverToUnopened(conversation)
            } else {
                // 上下文已满又没开自动压缩时先留着，等用户压缩或删消息后再投递
                // 会话刚被删除时 existsInDatabase 已经翻成 false，这时投递会把删掉的会话又写回来
                session.existsInDatabase &&
                    !session.isGenerating &&
                    !session.state.value.isAwaitingToolApproval &&
                    // 队列里还压着用户消息时的暂停说明它们都不会执行，通知混进去只会一起搁着；
                    // 队列空时这个暂停只是「上一轮被停掉或失败」的残留，sendMessage 会自己恢复，
                    // 不放开的话子代理结果会一直躺在盘上，直到用户再发一条并跑完一整轮
                    (!session.inbox.state.value.paused ||
                        session.inbox.state.value.messages.isEmpty()) &&
                    !(session.maxContextReached(settingsStore) && !autoCompressEnabled(settingsStore, session.state.value))
            }
            if (!canDeliver) return
            // 系统通知：走 USER 角色进请求，界面和导出都按系统消息展示
            // 参数依次为：父会话 id、结果正文、notice（走 USER 角色进请求，界面按系统消息展示）
            val accepted = sendMessage(
                conversationId,
                listOf(UIMessagePart.Text(formatSubAgentResults(pending.texts))),
                true,
            )
            // 没被收下（判定和入队之间上下文刚好越过 MAX）：结果留在队首，等下次再送
            if (!accepted) return
            // 父会话收下了这一批：丢掉快照覆盖的队首（含已经交给父会话、被正文滤掉的那几份），
            // 投递期间新到的还在队尾。从受理到消息真正落库（生成任务里 session.update + flush）
            // 之间被杀，这一批仍然会丢；消息队列本身不持久化，这个窗口关不掉，
            // 这里只是把它缩到「已入队」为止
            subAgentResultStore.commit(conversationId, pending)
        } finally {
            synchronized(deliveringSubAgentResults) { deliveringSubAgentResults.remove(conversationId) }
        }
    }

    /**
     * 会话没打开时能不能直接投递。
     *
     * 普通会话等用户下次打开再送，免得后台突然开始生成；子代理会话没人会打开它，
     * 等下去整条子代理链就断在中间这一层，所以它自己接着跑。
     */
    private fun canDeliverToUnopened(conversation: Conversation): Boolean {
        if (conversation.parentConversationId == null) return false
        val settings = settingsStore.settingsFlow.value
        val model = settings.getChatModelOf(conversation)
        if (model?.enforceMaxContext == false) return true
        return !isContextLimitReached(conversation.lastContextTokens(), model?.maxContextTokens) ||
            settings.getAssistantOf(conversation).autoCompress
    }

    /**
     * 父会话正在这一轮里跑时，把子代理回传结果直接插进这一步。
     *
     * 走 Notice 而不是合成工具调用：它和后台命令结束是同一类运行时事件，展开后是一条
     * 合成 user 消息，位置就在工具结果之后。框架语要跟着正文一起发，模型才知道这不是用户说的。
     *
     * 和 [flushSubAgentResults] 共用 [deliveringSubAgentResults] 这把锁：父会话刚空闲的
     * 那一瞬间，两条路会同时看到同一批结果。谁先拿到锁谁投，另一条看到已经被 commit 的空队列。
     *
     * 代价是 commit 早于消息真正落库：这一步如果在中途失败，这一批就没了。窗口和
     * [flushSubAgentResults] 的「已入队」窗口一样大，是已知的取舍。
     */
    internal suspend fun takeSubAgentNotices(session: ConversationSession): List<UIMessagePart.Notice> {
        synchronized(deliveringSubAgentResults) {
            if (!deliveringSubAgentResults.add(session.id)) return emptyList()
        }
        try {
            val pending = subAgentResultStore.peekDeliverable(session.id)
            if (pending.isEmpty) return emptyList()
            val notice = UIMessagePart.Notice(
                title = context.getString(R.string.chat_message_sub_agent_notice_title),
                text = SUB_AGENT_NOTICE_FRAMING + "\n" + formatSubAgentResults(pending.texts),
            )
            subAgentResultStore.commit(session.id, pending)
            return listOf(notice)
        } finally {
            synchronized(deliveringSubAgentResults) { deliveringSubAgentResults.remove(session.id) }
        }
    }
}
