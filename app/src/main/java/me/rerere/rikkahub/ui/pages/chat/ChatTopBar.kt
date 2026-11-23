package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import me.rerere.rikkahub.ui.style.DropdownMenu
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.rerere.ai.provider.Model
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Flowchart01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.FolderAdd
import me.rerere.hugeicons.stroke.Layers01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Share04
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.effectiveBackgroundEffect
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Folder
import me.rerere.rikkahub.ui.components.ai.ChatCapsule
import me.rerere.rikkahub.ui.components.ai.ChatCapsuleDivider
import me.rerere.rikkahub.ui.components.ai.chatInputHaze
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.style.IconButton
import me.rerere.rikkahub.ui.style.TopAppBar
import me.rerere.rikkahub.ui.theme.LocalFullWidthComponents
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPaddingValue
import me.rerere.rikkahub.ui.theme.appButtonShadow
import kotlin.uuid.Uuid

// 顶栏按钮统一尺寸：45dp 的按钮配 22.5dp 的图标（原来是 40dp/20dp，等比放大 12.5%），
// 按钮和图标保持 2:1，圆角、间距关系都不变。
private val TopBarButtonSize = 45.dp
private val TopBarButtonIconSize = 22.5.dp

@Composable
internal fun TopBar(
    hazeState: HazeState,
    settings: Settings,
    assistant: Assistant,
    chatModel: Model?,
    folderId: Uuid?,
    folders: List<Folder>,
    hasMessages: Boolean,
    threePane: Boolean,
    sidebarOpen: Boolean,
    onOpenSidebar: () -> Unit,
    showCapsuleBackground: Boolean,
    onNewChat: () -> Unit,
    onShowTaskTree: () -> Unit,
    onShowModelPicker: () -> Unit,
    onSelectFolder: (Uuid?) -> Unit,
    onCreateFolder: () -> Unit,
    onShowOverview: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    detailsExpanded: Boolean,
    onToggleDetails: () -> Unit,
) {
    val effect = settings.displaySetting.effectiveBackgroundEffect
    // 着色交给 chatInputHaze，按当前档位读滑杆；按钮自己只在关闭特效时铺实色
    fun effectColor(base: Color): Color =
        if (effect == BackgroundEffectType.OFF) base else Color.Transparent
    val effectBorder = if (effect == BackgroundEffectType.BLUR) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    } else {
        null
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        title = {
            // 横屏三栏的顶栏只留菜单、新建和会话菜单：模型、详情、任务树都在右栏的标签页里。
            // 胶囊放在标题位：这里只拿到菜单与右侧按钮之间的剩余宽度，文件夹名超长时按这份宽度截断，
            // 放在导航位则会顶到右边的按钮上
            if (!threePane && chatModel != null) {
                ChatModelFolderPill(
                    settings = settings,
                    model = chatModel,
                    folderId = folderId,
                    folders = folders,
                    onSelectFolder = onSelectFolder,
                    onCreateFolder = onCreateFolder,
                    onShowModelPicker = onShowModelPicker,
                    hazeState = hazeState,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        },
        navigationIcon = {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Row(
                    // 全宽模式下顶栏两侧不再留白，仍然保留状态栏与刘海的 inset
                    modifier = Modifier.padding(start = adaptiveHorizontalPaddingValue(12.dp)),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val navShape = when {
                        LocalFullWidthComponents.current -> RoundedCornerShape(0.dp)
                        else -> CircleShape
                    }
                    val navColor = MaterialTheme.colorScheme.surfaceContainerLow
                    // 横屏三栏里这是左栏的开合，竖屏还是把抽屉拉出来
                    Surface(
                        onClick = onOpenSidebar,
                        modifier = Modifier
                            .size(TopBarButtonSize)
                            .appButtonShadow(navShape, 2.dp)
                            .clip(navShape)
                            .background(effectColor(navColor))
                            .chatInputHaze(
                                hazeState = hazeState,
                                displaySetting = settings.displaySetting,
                                containerColor = navColor,
                                shape = navShape,
                            )
                            // 描边画在模糊层之后，否则会被糊掉
                            .then(
                                if (effectBorder != null) {
                                    Modifier.border(effectBorder, navShape)
                                } else {
                                    Modifier
                                }
                            ),
                        shape = navShape,
                        color = Color.Transparent,
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                HugeIcons.Menu03,
                                "Messages",
                                modifier = Modifier.size(TopBarButtonIconSize),
                                // 左栏展开时按钮提亮，和详情按钮同一套反馈
                                tint = if (threePane && sidebarOpen) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    LocalContentColor.current
                                },
                            )
                        }
                    }
                }
            }
        },
        actions = {
            Row(
                modifier = Modifier.padding(end = adaptiveHorizontalPaddingValue(8.dp)),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 和左侧的会话胶囊共用同一个容器，入口之间同样用一条细线隔开，
                // 顶栏两头的胶囊才是同一套东西
                ChatCapsule(
                    displaySetting = settings.displaySetting,
                    hazeState = hazeState,
                    modifier = Modifier.height(TopBarButtonSize),
                ) {
                    // 详情和任务树在横屏是右栏的标签页，顶栏不再重复两个入口
                    if (!threePane) {
                        IconButton(onClick = onToggleDetails, modifier = Modifier.size(TopBarButtonSize)) {
                            Icon(
                                HugeIcons.Layers01,
                                stringResource(R.string.chat_page_details_button),
                                modifier = Modifier.size(TopBarButtonIconSize),
                                // 展开时按钮跟着提亮，顶栏一眼能看出详情面板是开着的
                                tint = if (detailsExpanded) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    LocalContentColor.current
                                },
                            )
                        }

                        ChatCapsuleDivider()

                        IconButton(onClick = onShowTaskTree, modifier = Modifier.size(TopBarButtonSize)) {
                            Icon(
                                HugeIcons.Flowchart01,
                                stringResource(R.string.chat_task_tree_button),
                                modifier = Modifier.size(TopBarButtonIconSize),
                            )
                        }

                        ChatCapsuleDivider()
                    }

                    IconButton(onClick = { onNewChat() }, modifier = Modifier.size(TopBarButtonSize)) {
                        Icon(HugeIcons.PencilEdit01, "New Message", modifier = Modifier.size(TopBarButtonIconSize))
                    }

                    ChatCapsuleDivider()

                    ChatPageMenu(
                        enabled = hasMessages,
                        onShowOverview = onShowOverview,
                        onExport = onExport,
                        onDelete = onDelete,
                    )
                }
            }
        },
    )
}

/**
 * 顶栏的会话胶囊：左半边是会话文件夹，右边是当前模型。
 *
 * 两部分各点各的：文件夹打开选单，模型打开模型选择面板。整块放在 AppBar 的标题位，
 * 标题位只拿到菜单按钮与右侧按钮之间的剩余宽度，文件夹名过长时按这份宽度截断。
 * 供应商图标让位给文件名：标题位在手机上一共一百多 dp，两个图标加名字放不下，
 * 供应商在模型选择面板里仍然看得到。
 */
@Composable
private fun ChatModelFolderPill(
    settings: Settings,
    model: Model,
    folderId: Uuid?,
    folders: List<Folder>,
    onSelectFolder: (Uuid?) -> Unit,
    onCreateFolder: () -> Unit,
    onShowModelPicker: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val folder = folders.firstOrNull { it.id == folderId }
    var menuExpanded by remember { mutableStateOf(false) }

    ChatCapsule(
        displaySetting = settings.displaySetting,
        hazeState = hazeState,
        modifier = modifier.height(TopBarButtonSize),
    ) {
        Box {
            Row(
                modifier = Modifier
                    .height(TopBarButtonSize)
                    .clickable { menuExpanded = true }
                    .padding(start = 8.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    HugeIcons.Folder01,
                    stringResource(R.string.chat_page_folder_pill),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = folder?.name ?: stringResource(R.string.chat_page_folder_unfiled),
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ChatFolderMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                folders = folders,
                selectedFolderId = folderId,
                onSelect = { selected ->
                    menuExpanded = false
                    onSelectFolder(selected)
                },
                onCreateFolder = {
                    menuExpanded = false
                    onCreateFolder()
                },
            )
        }

        ChatCapsuleDivider()

        Row(
            modifier = Modifier
                .height(TopBarButtonSize)
                .clickable(onClick = onShowModelPicker)
                .padding(start = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AutoAIIcon(name = model.modelId, color = Color.Transparent)
        }
    }
}

// 胶囊里的文件夹选单：未归类排在最上面，当前归属打勾
@Composable
private fun ChatFolderMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    folders: List<Folder>,
    selectedFolderId: Uuid?,
    onSelect: (Uuid?) -> Unit,
    onCreateFolder: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        Text(
            text = stringResource(R.string.chat_page_folder_pill),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        FolderMenuItem(
            label = stringResource(R.string.chat_page_folder_unfiled),
            selected = selectedFolderId == null,
            onClick = { onSelect(null) },
        )
        folders.forEach { folder ->
            FolderMenuItem(
                label = folder.name,
                selected = folder.id == selectedFolderId,
                onClick = { onSelect(folder.id) },
            )
        }
        if (folders.isEmpty()) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_page_folder_empty)) },
                onClick = {},
                enabled = false,
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.chat_page_create_folder)) },
            leadingIcon = { Icon(HugeIcons.FolderAdd, contentDescription = null) },
            onClick = onCreateFolder,
        )
    }
}

@Composable
private fun FolderMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val trailingIcon: (@Composable () -> Unit)? = if (selected) {
        {
            Icon(
                HugeIcons.Tick01,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    } else {
        null
    }
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onClick = onClick,
        leadingIcon = {
            Icon(
                HugeIcons.Folder01,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        trailingIcon = trailingIcon,
    )
}

// 顶栏右侧的会话菜单；空对话没有内容可概览、导出或删除，菜单项全部置灰
@Composable
private fun ChatPageMenu(
    enabled: Boolean,
    onShowOverview: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    @Composable
    fun MenuItem(
        text: String,
        icon: ImageVector,
        index: Int,
        count: Int,
        destructive: Boolean = false,
        onClick: () -> Unit,
    ) {
        DropdownMenuItem(
            text = { Text(text) },
            onClick = {
                expanded = false
                onClick()
            },
            shape = MenuDefaults.itemShape(index = index, count = count).shape,
            leadingIcon = { Icon(icon, contentDescription = null) },
            enabled = enabled,
            colors = if (destructive) {
                val errorColor = MaterialTheme.colorScheme.error
                MenuDefaults.itemColors(textColor = errorColor, leadingIconColor = errorColor)
            } else {
                MenuDefaults.itemColors()
            },
        )
    }

    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(TopBarButtonSize)) {
            Icon(
                HugeIcons.MoreVertical,
                stringResource(R.string.more_options),
                modifier = Modifier.size(TopBarButtonIconSize),
            )
        }
        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 2)) {
                MenuItem(
                    text = stringResource(R.string.chat_page_conversation_map),
                    icon = HugeIcons.LeftToRightListBullet,
                    index = 0,
                    count = 2,
                    onClick = onShowOverview,
                )
                MenuItem(
                    text = stringResource(R.string.export_title),
                    icon = HugeIcons.Share04,
                    index = 1,
                    count = 2,
                    onClick = onExport,
                )
            }
            Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = 1)) {
                MenuItem(
                    text = stringResource(R.string.delete),
                    icon = HugeIcons.Delete01,
                    index = 0,
                    count = 1,
                    destructive = true,
                    onClick = onDelete,
                )
            }
        }
    }
}
