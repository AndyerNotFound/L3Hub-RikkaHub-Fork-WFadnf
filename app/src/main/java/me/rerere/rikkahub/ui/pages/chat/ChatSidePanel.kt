package me.rerere.rikkahub.ui.pages.chat

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.canGlassPane
import me.rerere.rikkahub.ui.components.ui.glassSurface
import me.rerere.rikkahub.ui.theme.appButtonShadow

/**
 * 横屏三栏时右侧栏里的看板，一个看板就是一个标签页。
 *
 * 这些内容原来都是盖在屏幕下半截的 [androidx.compose.material3.ModalBottomSheet]，
 * 或者顶栏底下拉开的详情页。横屏时屏幕只有 400dp 出头的高度，弹层一开聊天区就没了，
 * 所以改成固定的一列：标签条切换看板，聊天区始终留在中间。
 */
internal enum class ChatSidePane(
    // 标签页上的名字。看板自己不再画标题行，标题归标签条
    @StringRes val titleRes: Int,
) {
    Details(R.string.chat_page_details_button),
    TaskTree(R.string.task_tree_title),
    Changes(R.string.turn_diff_this_turn),
    Status(R.string.chat_page_side_panel_status),
    Usage(R.string.chat_message_usage_title),
    Files(R.string.chat_page_side_panel_files),
    Workspace(R.string.chat_page_side_panel_workspace),
    Terminal(R.string.chat_page_side_panel_terminal),
    ModelPicker(R.string.chat_page_side_panel_model),
    BackgroundTasks(R.string.background_tasks_title),
}

/**
 * 右侧栏的宿主：一块玻璃底板，上面一条浏览器风格的标签条，下面放当前看板。
 *
 * 和上一版的浮窗不同，它不是模态的——不会盖住聊天区、点聊天区也不会收起来，
 * 收法只有两个：标签上的 ×，或者把最后一个标签关掉（退化成「新标签页」）。
 */
@Composable
internal fun ChatSidePanelColumn(
    panes: List<ChatSidePane>,
    selected: ChatSidePane?,
    onSelect: (ChatSidePane) -> Unit,
    onOpen: (ChatSidePane) -> Unit,
    onClose: (ChatSidePane) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    content: @Composable (ChatSidePane) -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    // 选中的标签被关掉、或恢复出来的选中项已经不在标签里时，退到第一个，别让内容区空着
    val active = selected?.takeIf { it in panes } ?: panes.firstOrNull()

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .appButtonShadow(shape, 6.dp)
            .glassSurface(
                shape = shape,
                baseColor = containerColor,
                hazeState = hazeState,
                radius = 16.dp,
                // 大块浮层：只糊注册过的源，正文不在源里，透明度留低了文字会透上来
                minAlpha = 0.95f,
            ),
        shape = shape,
        color = if (canGlassPane(hazeState)) Color.Transparent else containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            if (panes.isEmpty()) {
                EmptyTabPage(
                    onOpen = onOpen,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ChatSidePanelTabStrip(
                    panes = panes,
                    selected = active,
                    onSelect = onSelect,
                    onOpen = onOpen,
                    onClose = onClose,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    active?.let { pane -> content(pane) }
                }
            }
        }
    }
}

/** 标签条：横向滚动的标签 + 末尾的「新建标签页」。样式和终端页的分页条一致 */
@Composable
private fun ChatSidePanelTabStrip(
    panes: List<ChatSidePane>,
    selected: ChatSidePane?,
    onSelect: (ChatSidePane) -> Unit,
    onOpen: (ChatSidePane) -> Unit,
    onClose: (ChatSidePane) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val selectedIndex = panes.indexOf(selected).coerceAtLeast(0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 4.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            modifier = Modifier.weight(1f),
            edgePadding = 0.dp,
            minTabWidth = 104.dp,
            containerColor = Color.Transparent,
        ) {
            panes.forEach { pane ->
                Tab(
                    selected = pane == selected,
                    onClick = { onSelect(pane) },
                    modifier = Modifier
                        .height(38.dp)
                        .widthIn(min = 104.dp, max = 200.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(pane.titleRes),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        IconButton(
                            onClick = { onClose(pane) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = HugeIcons.Cancel01,
                                contentDescription = stringResource(R.string.chat_page_side_panel_close_tab),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Add01,
                    contentDescription = stringResource(R.string.chat_page_side_panel_new_tab),
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                ChatSidePane.entries.forEach { pane ->
                    DropdownMenuItem(
                        text = { Text(stringResource(pane.titleRes)) },
                        onClick = {
                            menuExpanded = false
                            onOpen(pane)
                        },
                        // 已经开着的看板打勾，点了等于切过去
                        trailingIcon = if (pane in panes) {
                            {
                                Icon(
                                    imageVector = HugeIcons.Tick01,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

/** 一个标签都不剩时的「新标签页」：把能开的看板直接列出来，点一下就开 */
@Composable
private fun EmptyTabPage(
    onOpen: (ChatSidePane) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.chat_page_side_panel_new_tab),
            style = MaterialTheme.typography.titleSmallEmphasized,
        )
        Text(
            text = stringResource(R.string.chat_page_side_panel_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChatSidePane.entries.forEach { pane ->
            Surface(
                onClick = { onOpen(pane) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(pane.titleRes),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}
