package me.rerere.rikkahub.ui.pages.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

/**
 * 侧边栏整页。
 *
 * 竖屏原来是 ModalNavigationDrawer：一个压在聊天页上的浮层，要自己处理边缘手势、
 * 返回键优先级和「动画途中再点一次」的收敛。改成历史里的一页之后，这些全部回到导航栈
 * 这一层 —— 它和别的页面一样有返回、有转场、有生命周期。
 *
 * 内容由 [NativeSidebarHost] 提供：布局本体是 Java View（外壳、快捷入口、文件夹条、
 * 底部按钮、会话列表），图标与几块需要 koin 的组件用 ComposeView 嵌进去。
 *
 * @param fromChatId 从哪个会话打开来的，只用于高亮。没有会话在下面时为 null。
 */
@Composable
fun SidebarPage(fromChatId: Uuid?) {
    val navController = LocalNavController.current
    val listVm: ConversationListVM = koinViewModel()

    // 返回键：整页侧边栏自己吃掉，弹回聊天页
    BackHandler(enabled = true) {
        navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton() },
                title = { Text(stringResource(R.string.chat_page_conversation_history)) },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        NativeSidebarHost(
            listVm = listVm,
            currentConversationId = fromChatId,
            // 新会话：换掉栈底那一页，侧边栏跟着退场
            onNewChat = { navigateToChatPage(navController) },
            onOpenSettings = { navController.navigate(Screen.Setting) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}
