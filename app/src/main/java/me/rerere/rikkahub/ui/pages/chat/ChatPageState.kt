// Extracted from ChatPage.kt
package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.State
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeState
import kotlin.uuid.Uuid
import kotlinx.coroutines.Job
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.service.SessionActivity
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.ChatInputState

/**
 * 聊天页装配所需的只读上下文。
 *
 * 页面层负责算派生值，这里只做承载：加一个字段不用再改函数签名和两个布局分支的调用点。
 * 会话正文仍按 [conversationState] 传入，页面只读派生出来的字段，逐 token 的重组留给消息列表。
 */
internal data class ChatPageUiState(
    val inputState: ChatInputState,
    val loadingJob: Job?,
    val sessionActivity: SessionActivity?,
    val processingStatus: String?,
    val setting: Settings,
    val conversationState: State<Conversation>,
    val conversationId: Uuid,
    val folderId: Uuid?,
    val messageCount: Int,
    val folderWorkspaceName: String?,
    val navController: Navigator,
    val vm: ChatVM,
    val chatListState: LazyListState,
    val assistant: Assistant,
    val currentChatModel: Model?,
    val errors: List<ChatError>,
    // Null when no live effect will sample this source. Only chrome can sample the message list.
    val backgroundHazeState: HazeState?,
    val chromeHazeState: HazeState?,

    // 布局：两个布局分支的差异只落在这三个字段上
    val threePane: Boolean = false,
    val sidebarOpen: Boolean = false,
    val chatWidth: Dp? = null,
)

/** 聊天页向上的动作。两个布局分支只覆盖自己需要的槽位。 */
internal data class ChatPageActions(
    val onStartVoiceMode: () -> Unit,
    val onCreateNewChat: () -> Unit,
    val onDismissError: (Uuid) -> Unit,
    val onClearAllErrors: () -> Unit,
    val onOpenSidebar: () -> Unit = {},
    val onChatResize: (Dp) -> Unit = {},
)
