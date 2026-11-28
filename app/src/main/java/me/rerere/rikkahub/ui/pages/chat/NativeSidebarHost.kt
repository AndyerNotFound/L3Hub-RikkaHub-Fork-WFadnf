package me.rerere.rikkahub.ui.pages.chat

import android.graphics.Rect
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Pin
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.TransactionHistory
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.components.ai.AssistantPicker
import me.rerere.rikkahub.ui.components.ui.BackupReminderCard
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.LocalAnimationsEnabled
import me.rerere.rikkahub.ui.theme.slideDurationMillis
import me.rerere.rikkahub.ui.views.chat.NativeSidebar
import me.rerere.rikkahub.ui.views.chat.SidebarRow
import me.rerere.rikkahub.ui.views.chat.rememberNativeChatContext
import me.rerere.rikkahub.ui.views.chat.nativeChatTheme
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import kotlin.uuid.Uuid

/**
 * 侧边栏的 Compose 入口。
 *
 * 布局本体是 Java（[NativeSidebar]）：外壳、快捷入口、文件夹条、底部按钮、会话列表
 * 全在 View 体系里。这个文件只做三件事：
 *
 * 1. **数据摊平** —— 把 VM 的 `ConversationListItem` 流翻成 [SidebarRow] 推给 Java；
 * 2. **图标** —— HugeIcons 的 pathData 是编译后的 `ImageVector.Builder` 字节码，
 *    Java 侧取不到，所以图标一律用 `ComposeView` 渲染；
 * 3. **整块 Compose 组件** —— 备份卡、头像、问候语、助手选择器要读 koin 与
 *    CompositionLocal，整块嵌进去比在 Java 里重建依赖链省事。
 */
@Composable
internal fun NativeSidebarHost(
    listVm: ConversationListVM,
    currentConversationId: Uuid?,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val appContext = LocalContext.current
    val themedContext = rememberNativeChatContext()
    // 运行期调色板：侧边栏是 Java View，颜色不走 Compose，得显式喂进去
    val theme = nativeChatTheme()
    // ChatDrawerVM 要 SavedStateHandle（记滚动位置与折叠状态），
    // 而 SavedStateHandle 只有能提供 SavedStateRegistry 的 owner 才给得出来。
    // 用默认 owner 会抛 InstanceCreationException: Could not create instance for ChatDrawerVM。
    // 这里钉到 Activity：它一定是 ComponentActivity，注册表齐全。
    val activity = appContext as ComponentActivity
    val drawerVm: ChatDrawerVM = koinViewModel(viewModelStoreOwner = activity)
    val scope = rememberCoroutineScope()
    val hostView = LocalView.current

    val verticalItems by drawerVm.verticalItems.collectAsStateWithLifecycle()
    val folders by drawerVm.folders.collectAsStateWithLifecycle()
    val collapsedDates by drawerVm.collapsedDates.collectAsStateWithLifecycle()
    val collapsedFolders by drawerVm.collapsedFolders.collectAsStateWithLifecycle()
    val conversationJobs by listVm.conversationJobs.collectAsStateWithLifecycle(
        initialValue = emptyMap(),
    )

    val animationsEnabled = LocalAnimationsEnabled.current
    val duration = slideDurationMillis()

    // 长按某一行时弹出的操作菜单。状态放在这一层：菜单不在 RecyclerView 里，
    // 跟着行一起回收会丢
    // 当前选中的文件夹 Tab。null 表示「默认」—— 全部文件夹按段列出
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    // 上一次已经推给列表的文件夹。用来判断这一次是「换文件夹」还是「同文件夹内刷新」：
    // 前者要 fade through，后者只做 DiffUtil 增量
    var lastSubmittedFolder by remember { mutableStateOf<String?>(null) }

    // 多选模式：进入后每行变成 Outline Toggle 按钮
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var menuRow by remember { mutableStateOf<SidebarRow?>(null) }
    var menuAnchor by remember { mutableStateOf<Rect?>(null) }
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }

    val rows = remember(
        verticalItems,
        collapsedDates,
        collapsedFolders,
        conversationJobs,
        currentConversationId,
        selectedIds,
        selectedFolderId,
    ) {
        buildSidebarRows(
            items = verticalItems,
            collapsedDates = collapsedDates,
            collapsedFolders = collapsedFolders,
            runningIds = conversationJobs.keys.map { it.toString() }.toSet(),
            checkedIds = selectedIds,
            defaultLabel = appContext.getString(R.string.chat_page_folder_default),
            pinnedLabel = appContext.getString(R.string.pinned_chats),
            filterFolderId = selectedFolderId,
        )
    }

    // XML 依赖 Material/AppCompat 主题；深色模式或缩放变化时同步重建 View。
    key(themedContext) {
        AndroidView(
            modifier = modifier,
            factory = {
                NativeSidebar(themedContext).apply {
                    // 强调色跟界面其余部分走同一份运行期调色板
                    setTheme(theme)

                    setIconBinder { container, iconKey, tint ->
                        container.addView(
                            ComposeView(container.context).apply {
                                setContent {
                                    Icon(
                                        imageVector = sidebarIcon(iconKey),
                                        contentDescription = null,
                                        tint = Color(tint),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(1.dp),
                                    )
                                }
                            }
                        )
                    }

                    setSlotBinder { container, slot ->
                        container.addView(
                            ComposeView(container.context).apply {
                                // 整块组件要读 koin 与 CompositionLocal，把宿主那套搬进来
                                hostView.findViewTreeLifecycleOwner()?.let(::setViewTreeLifecycleOwner)
                                hostView.findViewTreeSavedStateRegistryOwner()
                                    ?.let(::setViewTreeSavedStateRegistryOwner)
                                hostView.findViewTreeViewModelStoreOwner()
                                    ?.let(::setViewTreeViewModelStoreOwner)
                                setContent {
                                    SidebarSlot(
                                        slot = slot,
                                        listVm = listVm,
                                        onNavigateSettings = onOpenSettings,
                                    )
                                }
                            }
                        )
                    }

                    setConversationListListener(object : NativeSidebar.ConversationListListener {
                        override fun onConversationClick(conversationId: String) {
                            val id = NativeSidebar.toUuid(conversationId) ?: return
                            navigateToChatPage(navController, Uuid.parse(id.toString()))
                        }

                        override fun onConversationLongClick(rowView: View, row: SidebarRow) {
                            menuRow = row
                            menuAnchor = rowView.windowRect()
                        }

                        override fun onConversationToggle(
                            conversationId: String,
                            checked: Boolean,
                        ) {
                            selectedIds = if (checked) selectedIds + conversationId
                            else selectedIds - conversationId
                        }

                        override fun onHeaderClick(row: SidebarRow) {
                            when (row.kind) {
                                SidebarRow.Kind.FOLDER_HEADER ->
                                    drawerVm.toggleFolderCollapsed(
                                        row.folderUuid()?.let { Uuid.parse(it.toString()) }
                                    )

                                SidebarRow.Kind.DATE_HEADER ->
                                    row.date?.let { iso ->
                                        runCatching { LocalDate.parse(iso) }.getOrNull()
                                            ?.let { drawerVm.toggleDateCollapsed(it) }
                                    }

                                else -> Unit
                            }
                        }

                        override fun onFolderHeaderLongClick(rowView: View, row: SidebarRow) {
                            // 文件夹的编辑菜单暂时不开：它要改名 / 删除 / 绑工作区三件事，
                            // 与已完成的 NativeSelectMenu 结构一致，但回调还没接过来
                        }
                    })

                    setListener(object : NativeSidebar.Listener {
                        override fun onNewChat() = onNewChat()
                        override fun onOpenSettings() = onOpenSettings()
                        override fun onOpenAssistant() = navController.navigate(Screen.Assistant)
                        override fun onOpenImageGen() = navController.navigate(Screen.ImageGen)
                        override fun onOpenFavorite() = navController.navigate(Screen.Favorite)
                        override fun onOpenStats() = navController.navigate(Screen.Stats)
                        override fun onOpenHistory() = navController.navigate(Screen.History)
                        override fun onOpenMessageSearch() =
                            navController.navigate(Screen.MessageSearch)

                        override fun onOpenMediaCreation() =
                            navController.navigate(Screen.MediaCreationSessions)

                        override fun onOpenTranslator() = navController.navigate(Screen.Translator)
                        override fun onEditNickname() = Unit
                        override fun onSelectFolder(folderId: String?) {
                            if (selectedFolderId == folderId) return
                            selectedFolderId = folderId
                        }
                        override fun onCreateFolder() = Unit
                        override fun onRenameFolder(folderId: String) = Unit
                        override fun onDeleteFolder(folderId: String) = Unit
                        override fun onSortClick(anchorView: View) = Unit
                        override fun onAssistantPickerClick(anchorView: View) = Unit

                        override fun onExitSelection() {
                            selectionMode = false
                            selectedIds = emptySet()
                        }

                        override fun onDeleteSelected(conversationIds: List<String>) {
                            if (conversationIds.isEmpty()) return
                            scope.launch {
                                conversationIds.forEach { id ->
                                    val conversation = verticalItems
                                        .filterIsInstance<ConversationListItem.Item>()
                                        .firstOrNull { it.conversation.id.toString() == id }
                                        ?.conversation
                                    if (conversation != null) {
                                        listVm.deleteConversation(conversation).join()
                                    }
                                }
                                selectionMode = false
                                selectedIds = emptySet()
                                if (conversationIds.any { it == currentConversationId?.toString() }) {
                                    navigateToChatPage(navController)
                                }
                            }
                        }

                        override fun onAssistantSettingClick() {
                            navController.navigate(
                                Screen.AssistantDetail(id = settings.assistantId.toString())
                            )
                        }
                    })
                }
            },
            update = { sidebar ->
                // 换配色时 factory 不会重跑，这里每次都推一遍
                sidebar.setTheme(theme)
                sidebar.setAnimationsEnabled(animationsEnabled)
                sidebar.setTransformDuration(duration)
                sidebar.setSelectionMode(selectionMode)
                sidebar.updateSelectionCount(selectedIds.size)
                // 换文件夹时整批换内容，走 fade through；
                // 其余情况（流式输出刷新标题、勾选变化）走 DiffUtil 增量，不播转场
                if (lastSubmittedFolder != selectedFolderId) {
                    lastSubmittedFolder = selectedFolderId
                    sidebar.submitRowsWithFadeThrough(rows)
                } else {
                    sidebar.submitRows(rows)
                }
                sidebar.setCurrentConversationId(currentConversationId?.toString())
                sidebar.setUser(
                    settings.displaySetting.userNickname
                        .ifBlank { appContext.getString(R.string.user_default_name) },
                    "",
                )
                sidebar.setFolders(
                    folders.map { it.id.toString() },
                    folders.map { it.name },
                    null,
                )
            },
        )
    }

    // 会话操作菜单
    val target = menuRow
    if (target != null) {
        val conversation = verticalItems
            .filterIsInstance<ConversationListItem.Item>()
            .firstOrNull { it.conversation.id.toString() == target.conversationId }
            ?.conversation

        if (conversation == null) {
            menuRow = null
        } else {
            val labels = SidebarMenuLabels(
                selectMultiple = appContext.getString(R.string.chat_page_select_multiple),
                pin = appContext.getString(
                    if (conversation.isPinned) R.string.unpin_chat else R.string.pin_chat
                ),
                rename = appContext.getString(R.string.chat_page_rename),
                regenerate = appContext.getString(R.string.chat_page_regenerate_title),
                delete = appContext.getString(R.string.chat_page_delete),
            )
            rememberNativeSelectMenu(
                expanded = true,
                onDismiss = { menuRow = null },
                anchorBounds = menuAnchor,
                icons = listOf(
                    HugeIcons.CheckmarkCircle02,
                    HugeIcons.Pin,
                    HugeIcons.PencilEdit01,
                    HugeIcons.Refresh01,
                    HugeIcons.TransactionHistory,
                ),
            ) {
                // 进多选：把这一条勾上，整张列表换成 toggle 行
                addItem(labels.selectMultiple, null, false) {
                    selectionMode = true
                    selectedIds = setOf(target.conversationId)
                }
                addItem(labels.pin, null, false) { listVm.updatePinnedStatus(conversation) }
                addItem(labels.rename, null, false) { renameTarget = conversation }
                addItem(labels.regenerate, null, false) { listVm.generateTitle(conversation, true) }
                addDivider()
                addItem(labels.delete, null, false, true, true) {
                    scope.launch {
                        listVm.deleteConversation(conversation).join()
                        if (conversation.id == currentConversationId) {
                            navigateToChatPage(navController)
                        }
                    }
                }
            }
        }
    }

    // 重命名对话框：沿用 Compose 那份，不必在 View 里重写一遍
    renameTarget?.let { conversation ->
        RenameConversationDialog(
            initialTitle = conversation.title,
            onDismissRequest = { renameTarget = null },
            onConfirm = { title ->
                listVm.updateTitle(conversation.id, title)
                renameTarget = null
            },
        )
    }
}

/** 菜单文案一次读齐：content lambda 在 LaunchedEffect 里跑，那里没有 Composition。 */
private data class SidebarMenuLabels(
    val selectMultiple: String,
    val pin: String,
    val rename: String,
    val regenerate: String,
    val delete: String,
)

/** 侧栏里那几块「整块交给 Compose」的组件。 */
@Composable
private fun SidebarSlot(
    slot: String,
    listVm: ConversationListVM,
    onNavigateSettings: () -> Unit,
) {
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current

    when (slot) {
        NativeSidebar.SLOT_BACKUP_CARD -> BackupReminderCard(
            settings = settings,
            onClick = onNavigateSettings,
        )

        NativeSidebar.SLOT_USER_AVATAR -> UIAvatar(
            name = settings.displaySetting.userNickname
                .ifBlank { appContext.getString(R.string.user_default_name) },
            value = settings.displaySetting.userAvatar,
            avatarSize = 50.dp,
            videoPlayback = true,
            onUpdate = { avatar ->
                scope.launch {
                    listVm.updateSettings {
                        it.copy(
                            displaySetting = it.displaySetting.copy(userAvatar = avatar)
                        )
                    }
                }
            },
        )

        NativeSidebar.SLOT_ASSISTANT_PICKER -> AssistantPicker(
            settings = settings,
            onSelectAssistant = { assistant ->
                scope.launch {
                    listVm.updateSettings { it.copy(assistantId = assistant.id) }.join()
                }
            },
            modifier = Modifier,
            onClickSetting = onNavigateSettings,
        )

        else -> Unit
    }
}
