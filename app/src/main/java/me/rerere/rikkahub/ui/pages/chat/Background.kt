package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.theme.LocalUiSlots

/**
 * 聊天页底色。
 *
 * 助手背景图与动态渐变已经停用：这里只铺一层页面底色。
 * [assistant]、[scrim]、[animateGradient] 仍留在签名里，供调用点逐步清理；
 * 它们不再影响绘制。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun AssistantBackground(
    setting: Settings,
    modifier: Modifier,
    scrim: Boolean = true,
    animateGradient: Boolean = true,
    assistant: Assistant = setting.getCurrentAssistant(),
) {
    Box(modifier.fillMaxSize().background(LocalUiSlots.current.pageBackground))
}
