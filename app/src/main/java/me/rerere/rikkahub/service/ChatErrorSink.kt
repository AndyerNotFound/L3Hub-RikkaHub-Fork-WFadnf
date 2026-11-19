package me.rerere.rikkahub.service

import android.app.Application
import androidx.annotation.StringRes
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.rerere.rikkahub.R

/**
 * 会话错误的唯一写入口。
 *
 * 取消不是错误：停止生成、切走页面都会抛 [CancellationException]，记进列表只会在界面上留下一条没有内容的
 * 「操作失败」。这一层统一挡掉，调用方不必各写一遍判断。
 *
 * 消费者是聊天页、悬浮窗与 Web 事件流，读的都是同一份 [state]。
 */
internal class ChatErrorSink(private val context: Application) {
    private val _errors = MutableStateFlow<List<ChatError>>(emptyList())
    val state: StateFlow<List<ChatError>> = _errors.asStateFlow()

    fun report(
        error: Throwable,
        conversationId: Uuid? = null,
        title: String? = null,
        solution: ChatErrorSolution? = null,
    ) {
        if (error is CancellationException) return
        _errors.update {
            it + ChatError(title = title, error = error, conversationId = conversationId, solution = solution)
        }
    }

    fun dismiss(id: Uuid) {
        _errors.update { list -> list.filter { it.id != id } }
    }

    fun clearAll() {
        _errors.value = emptyList()
    }

    /** 上下文到达 MAX 又没开自动压缩：这一条发不出去。 */
    fun reportContextLimit(conversationId: Uuid) {
        report(
            error = IllegalStateException(context.getString(R.string.chat_page_context_limit_reached)),
            conversationId = conversationId,
            title = context.getString(R.string.error_title_operation),
        )
    }

    /** 队列暂停又压着旧消息：说明原因，内容留给调用方。 */
    fun reportRejected(conversationId: Uuid, @StringRes message: Int) {
        report(
            error = IllegalStateException(context.getString(message)),
            conversationId = conversationId,
            title = context.getString(R.string.error_title_operation),
        )
    }
}
