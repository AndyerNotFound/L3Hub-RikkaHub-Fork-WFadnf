package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Conversation
import java.time.LocalDate
import kotlin.uuid.Uuid

/**
 * 竖向排序下的侧边栏列表：文件夹段 → 置顶 / 日期分组 → 会话。
 *
 * 纵向分段不参与分页，数据由 [ChatDrawerVM.verticalItems] 归好堆后整份传进来；
 * 条目渲染与扁平列表共用同一批组件，长按菜单、多选和折叠行为保持一致。
 * 文件夹段与周分组都能收起：收起状态由 [ChatDrawerVM] 持有，本组件只负责渲染与回调。
 */
@Composable
fun ColumnScope.ConversationSectionList(
    currentConversationId: Uuid,
    items: List<ConversationListItem>,
    conversationJobs: Collection<Uuid> = emptyList(),
    listState: LazyListState,
    collapsedDates: Set<LocalDate> = emptySet(),
    onToggleDate: (LocalDate) -> Unit = {},
    collapsedFolders: Set<String> = emptySet(),
    onToggleFolder: (Uuid?) -> Unit = {},
    scopeKey: Any? = null,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    onClick: (Conversation) -> Unit = {},
    onDelete: (Conversation) -> Unit = {},
    onRegenerateTitle: (Conversation) -> Unit = {},
    onPin: (Conversation) -> Unit = {},
    onMoveToAssistant: (Conversation) -> Unit = {},
    onMoveToFolder: (Conversation) -> Unit = {},
    onRename: (Conversation, String) -> Unit = { _, _ -> },
    onDeleteSelected: (List<Conversation>) -> Unit = {},
    onPinSelected: (List<Conversation>) -> Unit = {},
) {
    var hasScrolledToCurrent by remember(currentConversationId) { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Uuid>() }
    fun selectedConversations(): List<Conversation> = items
        .filterIsInstance<ConversationListItem.Item>()
        .map { it.conversation }
        .filter { it.id in selectedIds }
    val headerCount = if (header != null) 1 else 0

    var lastScopeKey by remember { mutableStateOf(scopeKey) }
    LaunchedEffect(scopeKey) {
        if (lastScopeKey == scopeKey) return@LaunchedEffect
        lastScopeKey = scopeKey
        selectionMode = false
        selectedIds.clear()
        hasScrolledToCurrent = true
    }

    // 每个下标属于哪个文件夹、哪一周：文件夹头重置分段，日期头开新段，会话沿用当前段
    val metas = remember(items) {
        var folderId: Uuid? = null
        var date: LocalDate? = null
        items.map { item ->
            when (item) {
                is ConversationListItem.FolderHeader -> {
                    folderId = item.folderId
                    date = null
                    SectionMeta(
                        key = "folder_${item.folderId ?: "unfiled"}",
                        date = null,
                        folderId = item.folderId,
                    )
                }

                is ConversationListItem.PinnedHeader -> {
                    date = null
                    SectionMeta(
                        key = "pinned_${folderId ?: "unfiled"}",
                        date = null,
                        folderId = folderId,
                    )
                }

                is ConversationListItem.DateHeader -> {
                    date = item.date
                    SectionMeta(
                        key = "date_${folderId ?: "unfiled"}_${item.date}",
                        date = item.date,
                        folderId = folderId,
                    )
                }

                is ConversationListItem.Item -> SectionMeta(
                    key = item.conversation.id.toString(),
                    date = date,
                    folderId = folderId,
                )
            }
        }
    }

    // 每个文件夹段里有几条会话，收起后标题上还看得见数量
    val folderCounts = remember(items) {
        val counts = mutableMapOf<Uuid?, Int>()
        var folderId: Uuid? = null
        items.forEach { item ->
            when (item) {
                is ConversationListItem.FolderHeader -> folderId = item.folderId
                is ConversationListItem.Item -> counts[folderId] = (counts[folderId] ?: 0) + 1
                else -> Unit
            }
        }
        counts
    }

    fun isFolderCollapsed(folderId: Uuid?): Boolean =
        folderCollapseKey(folderId) in collapsedFolders

    // 折叠后看不见的会话不应该还留在选中集合里
    LaunchedEffect(collapsedDates, collapsedFolders) {
        if (selectedIds.isEmpty()) return@LaunchedEffect
        val hiddenIds = items.mapIndexedNotNull { index, item ->
            val conversation = (item as? ConversationListItem.Item)?.conversation
                ?: return@mapIndexedNotNull null
            val meta = metas.getOrNull(index)
            val hidden = meta != null &&
                (isFolderCollapsed(meta.folderId) || meta.date?.let { it in collapsedDates } == true)
            if (hidden) conversation.id else null
        }
        if (hiddenIds.isNotEmpty()) {
            selectedIds.removeAll(hiddenIds.toSet())
        }
    }

    LaunchedEffect(
        currentConversationId,
        items,
        metas,
        collapsedDates,
        collapsedFolders,
        hasScrolledToCurrent,
    ) {
        if (hasScrolledToCurrent) return@LaunchedEffect
        val itemIndex = items.indexOfFirst {
            (it as? ConversationListItem.Item)?.conversation?.id == currentConversationId
        }
        if (itemIndex < 0) return@LaunchedEffect

        // 当前会话所在的文件夹段或周被收起时保持收起：那是用户自己收的，
        // 列表刷新、切换会话、重进应用都不替他拉开，也不往 0 高度的条目上滚
        val meta = metas.getOrNull(itemIndex)
        val hidden = meta != null &&
            (isFolderCollapsed(meta.folderId) || meta.date?.let { it in collapsedDates } == true)
        if (hidden) {
            hasScrolledToCurrent = true
            return@LaunchedEffect
        }

        val currentIndex = itemIndex + headerCount
        val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == currentIndex }
        if (!isVisible) {
            listState.scrollToItem(currentIndex)
        }
        hasScrolledToCurrent = true
    }

    Column(modifier = modifier) {
        AnimatedVisibility(visible = selectionMode) {
            SelectionBar(
                count = selectedIds.size,
                onPin = {
                    onPinSelected(selectedConversations())
                    selectedIds.clear()
                    selectionMode = false
                },
                onDelete = {
                    onDeleteSelected(selectedConversations())
                    selectedIds.clear()
                    selectionMode = false
                },
                onClear = {
                    selectedIds.clear()
                    selectionMode = false
                },
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.Top,
        ) {
            if (header != null) {
                item(key = "header", contentType = "header") {
                    Box(modifier = Modifier.padding(bottom = 8.dp)) {
                        header()
                    }
                }
            }

            if (items.isEmpty()) {
                item(key = "empty") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Text(
                            text = stringResource(id = R.string.chat_page_no_conversations),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            itemsIndexed(
                items = items,
                key = { index, _ -> metas[index].key },
            ) { index, item ->
                val meta = metas.getOrNull(index)
                // 文件夹收起时整段（周标题、置顶头、会话）一起收，只剩文件夹标题；
                // 周收起时只收会话。条目留在列表里交给高度动画收放，直接从数据里删掉就没有过渡了
                val visible = when {
                    meta == null -> true
                    isFolderCollapsed(meta.folderId) -> item is ConversationListItem.FolderHeader
                    item is ConversationListItem.Item ->
                        meta.date?.let { it in collapsedDates } != true
                    else -> true
                }

                CollapsibleSectionItem(visible = visible) {
                    Box(modifier = Modifier.padding(bottom = 8.dp)) {
                        when (item) {
                            is ConversationListItem.FolderHeader -> {
                                FolderSectionHeader(
                                    label = item.label,
                                    count = folderCounts[item.folderId] ?: 0,
                                    collapsed = isFolderCollapsed(item.folderId),
                                    onToggle = { onToggleFolder(item.folderId) },
                                    modifier = Modifier.animateItem(),
                                )
                            }

                            is ConversationListItem.DateHeader -> {
                                DateHeaderItem(
                                    label = item.label,
                                    collapsed = item.date in collapsedDates,
                                    onToggle = { onToggleDate(item.date) },
                                    modifier = Modifier.animateItem(),
                                )
                            }

                            is ConversationListItem.PinnedHeader -> {
                                PinnedHeader(modifier = Modifier.animateItem())
                            }

                            is ConversationListItem.Item -> {
                                ConversationItem(
                                    conversation = item.conversation,
                                    selected = item.conversation.id == currentConversationId,
                                    loading = item.conversation.id in conversationJobs,
                                    selectionMode = selectionMode,
                                    checked = item.conversation.id in selectedIds,
                                    onToggleSelection = {
                                        if (item.conversation.id in selectedIds) {
                                            selectedIds.remove(item.conversation.id)
                                        } else {
                                            selectedIds.add(item.conversation.id)
                                        }
                                    },
                                    onEnterSelection = {
                                        selectionMode = true
                                        if (item.conversation.id !in selectedIds) {
                                            selectedIds.add(item.conversation.id)
                                        }
                                    },
                                    onClick = if (selectionMode) {
                                        { conversation ->
                                            if (conversation.id in selectedIds) {
                                                selectedIds.remove(conversation.id)
                                            } else {
                                                selectedIds.add(conversation.id)
                                            }
                                            Unit
                                        }
                                    } else {
                                        onClick
                                    },
                                    onDelete = onDelete,
                                    onRegenerateTitle = onRegenerateTitle,
                                    onPin = onPin,
                                    onMoveToAssistant = onMoveToAssistant,
                                    onMoveToFolder = onMoveToFolder,
                                    onRename = onRename,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class SectionMeta(
    val key: String,
    val date: LocalDate?,
    // 所属文件夹段，null 表示「未归类」
    val folderId: Uuid?,
)

@Composable
private fun FolderSectionHeader(
    label: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = HugeIcons.Folder01,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = if (collapsed) HugeIcons.ArrowDown01 else HugeIcons.ArrowUp01,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
