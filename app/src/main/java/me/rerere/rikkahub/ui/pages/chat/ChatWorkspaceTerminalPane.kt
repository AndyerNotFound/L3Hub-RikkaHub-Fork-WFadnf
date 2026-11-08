package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceTerminalReadiness
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceTerminalSessionManager
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceTerminalTabContent
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceTerminalTabsState
import me.rerere.rikkahub.ui.theme.ColorMode
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import org.koin.compose.koinInject

/**
 * 右侧栏的「工作区终端」看板。
 *
 * 和「文件」看板共用同一份工作区绑定；会话跑着的那个终端会话由
 * `WorkspaceTerminalSessionManager` 统一持有，这里只是换了个宿主，
 * 和整页的终端页共享同一批标签，切过去还能接上。
 */
@Composable
internal fun WorkspaceTerminalPane(
    workspaceIds: List<String>,
    workspaces: List<WorkspaceEntity>,
    modifier: Modifier = Modifier,
) {
    val manager: WorkspaceTerminalSessionManager = koinInject()
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
    val root = workspace.root
    val state by remember(root, manager) { manager.observeWorkspace(root) }
        .collectAsStateWithLifecycle(initialValue = WorkspaceTerminalTabsState())
    LaunchedEffect(workspace.id) {
        manager.ensureSession(workspace)
    }
    val selectedTab = state.tabs.firstOrNull { it.id == state.selectedTabId }
        ?: state.tabs.firstOrNull()
    var tabMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            // 键盘弹出时整块看板往上收，终端底部那一行不会被盖住
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            WorkspaceName(
                bound = bound,
                current = workspace,
                onSelect = { selectedId = it.id },
                modifier = Modifier.weight(1f),
            )
            Box {
                Text(
                    text = stringResource(
                        R.string.workspace_terminal_tab,
                        selectedTab?.number ?: 1,
                    ) + " ▾",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clickable(enabled = state.tabs.size > 1) { tabMenuExpanded = true }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                )
                DropdownMenu(
                    expanded = tabMenuExpanded,
                    onDismissRequest = { tabMenuExpanded = false },
                ) {
                    state.tabs.forEach { tab ->
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.workspace_terminal_tab, tab.number))
                            },
                            onClick = {
                                tabMenuExpanded = false
                                manager.selectTab(root, tab.id)
                            },
                        )
                    }
                }
            }
            IconButton(
                onClick = { manager.createTab(workspace) },
                enabled = !state.isCreating,
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Add01,
                    contentDescription = stringResource(R.string.workspace_terminal_new_tab),
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(
                onClick = { selectedTab?.let { manager.closeTab(root, it.id) } },
                enabled = selectedTab != null,
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Cancel01,
                    contentDescription = stringResource(
                        R.string.workspace_terminal_close_tab,
                        selectedTab?.number ?: 1,
                    ),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (selectedTab != null) {
                // 终端固定深色：外层主题可能是亮色，嵌套一层只换配色，不再叠一次界面缩放
                RikkahubTheme(colorMode = ColorMode.DARK, applyUiScale = false) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Black,
                    ) {
                        WorkspaceTerminalTabContent(
                            tab = selectedTab,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            state.isCreating ||
                                state.readiness == WorkspaceTerminalReadiness.Loading ->
                                stringResource(R.string.workspace_terminal_loading)

                            state.readiness == WorkspaceTerminalReadiness.NotInstalled ->
                                stringResource(R.string.workspace_terminal_not_installed)

                            else -> stringResource(R.string.workspace_terminal_no_tabs)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
