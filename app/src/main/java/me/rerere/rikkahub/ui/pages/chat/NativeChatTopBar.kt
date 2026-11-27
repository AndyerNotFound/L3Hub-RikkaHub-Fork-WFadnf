/*
 * Thin Compose bridge for the ported native chat header (NativeChatHeader / l3_native_chat_header.xml).
 * The header itself is derived from the "Neko chat demo" sample (MIT, Copyright (c) 2026
 * 潇桦桉・洛｜WetherFlar); the full permission notice lives in the project's license appendix.
 */
package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.uuid.Uuid
import me.rerere.rikkahub.data.model.Folder
import me.rerere.rikkahub.ui.theme.LocalAnimationsEnabled
import me.rerere.rikkahub.ui.views.chat.NativeChatHeader
import me.rerere.rikkahub.ui.views.chat.nativeChatTheme
import me.rerere.rikkahub.ui.views.chat.rememberNativeChatContext

/**
 * The chat top bar: the native Material 3 Expressive toolbar (56dp row, 48dp fixed tabs) hosted
 * in a Compose slot.
 *
 * What this bridge deliberately does not do:
 * - it owns no page content and no pager. The message body stays in Compose on the main thread,
 *   so a tab tap only reports [onSelectTab]; nothing here can clear the composer draft;
 * - it consumes no insets. The caller applies `windowInsetsPadding`/`safeDrawing` around this
 *   composable, and the Java view installs no inset listener at all;
 * - it keeps no per-frame state of its own. Every frame pushes a fresh
 *   [NativeChatHeader.State] and the callbacks below, so the native view can never act on a
 *   value captured two recompositions ago.
 *
 * Tab indices match [NativeChatHeader.TAB_CHAT] = 0 chat, 1 map, 2 mission, 3 usage.
 *
 * @param folderId the conversation's folder, `null` when it is unassigned. It is only used to
 *   mark the selected entry; native popup callbacks carry a stable ID and are checked against
 *   the latest [folders], never resolved through a possibly reordered list index.
 * @param onToggleDetails opens the real conversation details. Both the session title button and
 *   the configuration button call it: that button is a configuration entry, not the demo's fake
 *   preset cycle, and it keeps no local selection.
 * @param onShowModelPicker the model entry stays in the overflow menu.
 */
@Composable
internal fun NativeChatTopBar(
    title: String,
    running: Boolean,
    folderId: Uuid?,
    folders: List<Folder>,
    hasMessages: Boolean,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onOpenSidebar: () -> Unit,
    onNewChat: () -> Unit,
    onShowModelPicker: () -> Unit,
    onSelectFolder: (Uuid?) -> Unit,
    onCreateFolder: () -> Unit,
    onToggleDetails: () -> Unit,
    onShowTaskTree: () -> Unit,
    onShowOverview: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // Activity-backed, already wrapped in Theme.L3Hub.NativeChat by the interop layer: the native
    // view needs a theme with the Material Components attributes for its styles and native menus.
    val context = rememberNativeChatContext()
    val theme = nativeChatTheme()
    val animationsEnabled = LocalAnimationsEnabled.current

    val folderNames = remember(folders) { folders.map { folder -> folder.name } }
    val folderIds = remember(folders) { folders.map { folder -> folder.id.toString() } }
    val selectedFolderIndex = remember(folders, folderId) {
        folders.indexOfFirst { folder -> folder.id == folderId }
    }

    // A fresh handler per frame, but the object installed on the view is created once and only
    // *reads* the latest one through rememberUpdatedState. That keeps the listeners from calling a
    // stale lambda without rebinding six click listeners on every recomposition.
    val latestHandler = rememberUpdatedState(
        NativeChatTopBarHandler(
            folders = folders,
            onSelectTab = onSelectTab,
            onOpenSidebar = onOpenSidebar,
            onNewChat = onNewChat,
            onShowModelPicker = onShowModelPicker,
            onSelectFolder = onSelectFolder,
            onCreateFolder = onCreateFolder,
            onToggleDetails = onToggleDetails,
            onShowTaskTree = onShowTaskTree,
            onShowOverview = onShowOverview,
            onExport = onExport,
            onDelete = onDelete,
            onOpenSettings = onOpenSettings,
        )
    )
    val callbacks = remember {
        object : NativeChatHeader.Callbacks {
            override fun onSelectTab(tab: Int) {
                latestHandler.value.onSelectTab(tab)
            }

            override fun onOpenSidebar() {
                latestHandler.value.onOpenSidebar()
            }

            override fun onNewChat() {
                latestHandler.value.onNewChat()
            }

            override fun onShowModelPicker() {
                latestHandler.value.onShowModelPicker()
            }

            override fun onSelectFolder(folderId: String?) {
                latestHandler.value.selectFolder(folderId)
            }

            override fun onCreateFolder() {
                latestHandler.value.onCreateFolder()
            }

            override fun onToggleDetails() {
                latestHandler.value.onToggleDetails()
            }

            override fun onShowTaskTree() {
                latestHandler.value.onShowTaskTree()
            }

            override fun onShowOverview() {
                latestHandler.value.onShowOverview()
            }

            override fun onExport() {
                latestHandler.value.onExport()
            }

            override fun onDelete() {
                latestHandler.value.onDelete()
            }

            override fun onOpenSettings() {
                latestHandler.value.onOpenSettings()
            }
        }
    }

    key(context) {
        AndroidView(
            factory = { NativeChatHeader(context) },
            modifier = Modifier.fillMaxWidth(),
            onRelease = { header -> header.release() },
            update = { header ->
                header.applyTheme(theme)
                header.setAnimationsEnabled(animationsEnabled)
                header.render(
                    NativeChatHeader.State()
                        .title(title)
                        .running(running)
                        .folderNames(folderNames)
                        .folderIds(folderIds)
                        .selectedFolderIndex(selectedFolderIndex)
                        .hasMessages(hasMessages)
                        .selectedTab(selectedTab),
                    callbacks,
                )
            },
        )
    }
}

/**
 * Current callbacks and stable folder identity validation. A removed folder is ignored rather
 * than accidentally resolving the same position to a different folder.
 */
private class NativeChatTopBarHandler(
    private val folders: List<Folder>,
    val onSelectTab: (Int) -> Unit,
    val onOpenSidebar: () -> Unit,
    val onNewChat: () -> Unit,
    val onShowModelPicker: () -> Unit,
    val onSelectFolder: (Uuid?) -> Unit,
    val onCreateFolder: () -> Unit,
    val onToggleDetails: () -> Unit,
    val onShowTaskTree: () -> Unit,
    val onShowOverview: () -> Unit,
    val onExport: () -> Unit,
    val onDelete: () -> Unit,
    val onOpenSettings: () -> Unit,
) {
    fun selectFolder(folderId: String?) {
        if (folderId == null) {
            onSelectFolder(null)
            return
        }
        folders.firstOrNull { it.id.toString() == folderId }?.let { folder ->
            onSelectFolder(folder.id)
        }
    }
}
