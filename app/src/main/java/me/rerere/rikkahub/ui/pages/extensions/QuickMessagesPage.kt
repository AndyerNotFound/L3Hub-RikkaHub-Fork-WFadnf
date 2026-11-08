package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.terminal.TextButton
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.ui.components.AnimatedAlertDialog
import me.rerere.ui.components.RikkaConfirmDialog
import org.koin.androidx.compose.koinViewModel

/** 分页：0 是快捷消息，1 是引导提示词 */
private const val TAB_MESSAGES = 0
private const val TAB_GUIDES = 1

@Composable
fun QuickMessagesPage(vm: QuickMessagesVM = koinViewModel()) {
    val settings = vm.settings.collectAsStateWithLifecycle().value
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var selectedTab by rememberSaveable { mutableStateOf(TAB_MESSAGES) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<QuickMessage?>(null) }
    var deleteTarget by remember { mutableStateOf<QuickMessage?>(null) }

    val isGuideTab = selectedTab == TAB_GUIDES
    val messages = if (isGuideTab) settings.guideMessages else settings.quickMessages

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.assistant_page_quick_messages)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(HugeIcons.Add01, contentDescription = null)
            }
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = !isGuideTab,
                    onClick = { selectedTab = TAB_MESSAGES },
                    text = { Text(stringResource(R.string.quick_messages_tab_messages)) },
                )
                Tab(
                    selected = isGuideTab,
                    onClick = { selectedTab = TAB_GUIDES },
                    text = { Text(stringResource(R.string.quick_messages_tab_guides)) },
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (messages.isEmpty()) {
                    item {
                        ExtensionEmptyState(
                            message = stringResource(
                                if (isGuideTab) R.string.guide_messages_page_empty_title
                                else R.string.quick_messages_page_empty_title
                            ),
                            description = stringResource(
                                if (isGuideTab) R.string.guide_messages_page_empty_hint
                                else R.string.quick_messages_page_empty_hint
                            ),
                            icon = HugeIcons.Zap,
                        )
                    }
                }

                items(messages, key = { it.id }) { quickMessage ->
                    QuickMessageCard(
                        quickMessage = quickMessage,
                        onEdit = { editTarget = quickMessage },
                        onDelete = { deleteTarget = quickMessage },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        EditQuickMessageDialog(
            title = stringResource(
                if (isGuideTab) R.string.guide_messages_page_add_title
                else R.string.quick_messages_page_add_title
            ),
            initialQuickMessage = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, content ->
                if (isGuideTab) vm.addGuideMessage(title, content) else vm.addQuickMessage(title, content)
                showAddDialog = false
            },
        )
    }

    editTarget?.let { quickMessage ->
        EditQuickMessageDialog(
            title = stringResource(
                if (isGuideTab) R.string.guide_messages_page_edit_title
                else R.string.quick_messages_page_edit_title
            ),
            initialQuickMessage = quickMessage,
            onDismiss = { editTarget = null },
            onConfirm = { title, content ->
                val updated = quickMessage.copy(title = title, content = content)
                if (isGuideTab) vm.updateGuideMessage(updated) else vm.updateQuickMessage(updated)
                editTarget = null
            },
        )
    }

    RikkaConfirmDialog(
        show = deleteTarget != null,
        title = stringResource(R.string.quick_messages_page_delete_title),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            deleteTarget?.let { message ->
                if (isGuideTab) vm.deleteGuideMessage(message.id) else vm.deleteQuickMessage(message.id)
            }
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
    ) {
        Text(stringResource(R.string.quick_messages_page_delete_message, deleteTarget?.title ?: ""))
    }
}

@Composable
private fun QuickMessageCard(
    quickMessage: QuickMessage,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CustomColors.listItemColors.containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardGroupIcon(HugeIcons.Zap)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = quickMessage.title.ifBlank { stringResource(R.string.quick_messages_page_untitled) },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = quickMessage.content.ifBlank { stringResource(R.string.quick_messages_page_empty_content) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ItemActionMenu(
                actions = listOf(
                    ItemAction(
                        text = stringResource(R.string.delete),
                        icon = HugeIcons.Delete01,
                        destructive = true,
                        onClick = onDelete,
                    ),
                )
            )
        }
    }
}

@Composable
private fun EditQuickMessageDialog(
    title: String,
    initialQuickMessage: QuickMessage?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, content: String) -> Unit,
) {
    var quickMessageTitle by rememberSaveable(initialQuickMessage?.id) {
        mutableStateOf(initialQuickMessage?.title ?: "")
    }
    var quickMessageContent by rememberSaveable(initialQuickMessage?.id) {
        mutableStateOf(initialQuickMessage?.content ?: "")
    }

    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = quickMessageTitle,
                    onValueChange = { quickMessageTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.assistant_page_quick_message_title)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = quickMessageContent,
                    onValueChange = { quickMessageContent = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.assistant_page_quick_message_content)) },
                    minLines = 4,
                    maxLines = 8,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(quickMessageTitle.trim(), quickMessageContent.trim()) },
                enabled = quickMessageTitle.isNotBlank() && quickMessageContent.isNotBlank(),
            ) {
                Text(stringResource(R.string.assistant_page_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
