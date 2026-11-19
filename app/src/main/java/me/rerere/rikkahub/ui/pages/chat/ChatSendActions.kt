package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.rikkahub.ui.hooks.ChatInputState
import kotlin.time.Duration.Companion.milliseconds

/**
 * 点发送时的分支结果，见 [chatSendGate]。
 *
 * 四条发送路径里只有普通发送要看「选中模型」和上下文门限：插话、长按「不回答」
 * 只过上下文这一道（[contextAllowsSend]），所以判断和收尾动作分开写。
 */
internal enum class ChatSendGate {
    /** 还没选模型：提示先选一个，不发 */
    SelectModel,

    /** 上下文到顶但开着自动压缩：先弹确认，确认后由 ChatService 压缩再发 */
    ConfirmCompress,

    /** 上下文到顶、模型开了强制拦截：提示一次，不发 */
    BlockedByContextLimit,

    /**
     * 放行。
     *
     * 到顶但模型关了强制拦截时也落到这里：调用点按 contextLimitReached 再提示一次
     * （改前就是这个顺序），然后照常发送。
     */
    Allow,
}

/**
 * 普通发送前的判断。
 *
 * 分支顺序与改前的 if-else 链一致：没模型优先，其次是「到顶 + 开了自动压缩」的确认，
 * 再次是到顶拦截，最后才是放行。
 */
internal fun chatSendGate(
    hasModel: Boolean,
    contextLimitReached: Boolean,
    contextLimitBlocksSend: Boolean,
    autoCompress: Boolean,
): ChatSendGate = when {
    !hasModel -> ChatSendGate.SelectModel
    !contextLimitReached -> ChatSendGate.Allow
    contextLimitBlocksSend && autoCompress -> ChatSendGate.ConfirmCompress
    contextLimitBlocksSend -> ChatSendGate.BlockedByContextLimit
    else -> ChatSendGate.Allow
}

/**
 * 插话与长按「不回答」只过上下文这一道门：到顶就提示一次（提示由调用点做），
 * 模型开了强制拦截就不发。
 */
internal fun contextAllowsSend(
    contextLimitReached: Boolean,
    contextLimitBlocksSend: Boolean,
): Boolean = !contextLimitReached || !contextLimitBlocksSend

/**
 * 发送侧四个入口（发送、插话、长按不回答、编辑保存）共用的收尾动作。
 *
 * 门限判断在外面（[chatSendGate] / [contextAllowsSend]），这里只管交出去以后的事：
 * 受理了才清空输入框，然后等新消息进布局再滚到最新一条。没被受理
 * （关闭队列后正在压缩上下文、队列暂停还压着旧消息）时输入框里的内容留着，这是原来的语义。
 *
 * 不做 remember：和改前的 lambda 一样每次重组重建，闭包读到的永远是当前状态。
 */
internal class ChatSendActions(
    private val inputState: ChatInputState,
    private val vm: ChatVM,
    private val scope: CoroutineScope,
    private val chatListState: LazyListState,
) {
    /** 编辑中的消息写回那条消息；不在编辑态时返回 false，交给 [send] */
    fun submitEdit(): Boolean {
        val messageId = inputState.editingMessage ?: return false
        vm.handleMessageEdit(parts = inputState.getContents(), messageId = messageId)
        inputState.clearInput()
        return true
    }

    /**
     * 发一条新消息。[interject] 打断当前回复，[answer] 为 false 时不触发回复。
     * 返回 ChatService 是否受理。
     */
    fun send(answer: Boolean = true, interject: Boolean = false): Boolean {
        val accepted = vm.handleMessageSend(
            content = inputState.getContents(),
            answer = answer,
            interject = interject,
        )
        if (accepted) afterAccepted()
        return accepted
    }

    /** 普通发送：编辑态写回消息，否则发一条新消息并等回复 */
    fun submit() {
        if (!submitEdit()) send()
    }

    // 清空之后等 100ms 再滚：新消息要先过一遍布局，否则算出来的最后一个下标还是旧的
    private fun afterAccepted() {
        inputState.clearInput()
        scope.launch {
            delay(100.milliseconds)
            chatListState.animateScrollToItem(
                index = (chatListState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0),
            )
        }
    }
}
