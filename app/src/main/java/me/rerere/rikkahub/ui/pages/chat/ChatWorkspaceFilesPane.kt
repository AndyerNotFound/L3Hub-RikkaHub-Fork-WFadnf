package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowTurnBackward
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceStorageArea
import org.koin.compose.koinInject
import java.util.concurrent.CancellationException

/**
 * 右侧栏的「工作区文件」看板：只列这个会话绑定工作区的 FILES 区。
 *
 * 点目录逐级进入，点文本文件在栏内直接预览（只读）；不跳页面，横屏时聊天区不会被顶掉。
 */
@Composable
internal fun WorkspaceFilesPane(
    workspaceIds: List<String>,
    workspaces: List<WorkspaceEntity>,
    modifier: Modifier = Modifier,
) {
    val repository: WorkspaceRepository = koinInject()
    val bound = remember(workspaceIds, workspaces) {
        workspaceIds.mapNotNull { id -> workspaces.firstOrNull { it.id == id } }
    }
    if (bound.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.chat_page_workspace_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    var selectedId by rememberSaveable { mutableStateOf(bound.first().id) }
    val workspace = bound.firstOrNull { it.id == selectedId } ?: bound.first()
    var path by rememberSaveable(workspace.id) { mutableStateOf("") }
    var previewPath by rememberSaveable(workspace.id) { mutableStateOf<String?>(null) }
    var entries by remember { mutableStateOf<List<WorkspaceFileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var previewText by remember { mutableStateOf("") }
    var previewLoading by remember { mutableStateOf(false) }
    var previewFailed by remember { mutableStateOf(false) }

    LaunchedEffect(workspace.id, path, reloadKey) {
        loading = true
        loadFailed = false
        entries = emptyList()
        try {
            entries = withContext(Dispatchers.IO) {
                repository.listFiles(workspace.id, WorkspaceStorageArea.FILES, path)
            }.sortedWith(
                compareByDescending<WorkspaceFileEntry> { it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            loadFailed = true
        }
        loading = false
    }

    LaunchedEffect(workspace.id, previewPath) {
        val target = previewPath ?: return@LaunchedEffect
        previewLoading = true
        previewFailed = false
        previewText = ""
        try {
            previewText = withContext(Dispatchers.IO) {
                repository.readTextForPreview(workspace.id, WorkspaceStorageArea.FILES, target)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            previewFailed = true
        }
        previewLoading = false
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 标题行：绑了多个工作区时可以切换，右边是刷新
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            WorkspaceName(
                bound = bound,
                current = workspace,
                onSelect = {
                    selectedId = it.id
                    previewPath = null
                },
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { reloadKey++ },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Refresh01,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // 路径行：跟文件管理器一样，点返回上一级
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                enabled = previewPath != null || path.isNotBlank(),
                onClick = {
                    if (previewPath != null) {
                        // 预览时先退回文件列表，再按一次才回上一级
                        previewPath = null
                    } else {
                        path = path.substringBeforeLast('/', missingDelimiterValue = "")
                    }
                },
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.ArrowTurnBackward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = previewPath ?: path.ifBlank { workspace.name },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        val target = previewPath
        if (target != null) {
            when {
                previewLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }

                previewFailed -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.workspace_file_editor_load_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                else -> SelectionContainer {
                    Text(
                        text = previewText,
                        fontFamily = JetbrainsMono,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        } else {
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else if (loadFailed) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.workspace_cwd_load_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.workspace_detail_empty_directory),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(entries, key = { it.path }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (entry.isDirectory) {
                                        path = entry.path
                                    } else {
                                        previewPath = entry.path
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = if (entry.isDirectory) HugeIcons.Folder01 else HugeIcons.File02,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = if (entry.isDirectory) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (!entry.isDirectory) {
                                Text(
                                    text = entry.sizeBytes.toReadableSize(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceName(
    bound: List<WorkspaceEntity>,
    current: WorkspaceEntity,
    onSelect: (WorkspaceEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = bound.size > 1) { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = current.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            bound.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.name) },
                    onClick = {
                        expanded = false
                        onSelect(item)
                    },
                )
            }
        }
    }
}

private fun Long.toReadableSize(): String = when {
    this >= 1024 * 1024 -> "%.1f MB".format(this / 1024.0 / 1024.0)
    this >= 1024 -> "%.0f KB".format(this / 1024.0)
    else -> "$this B"
}
