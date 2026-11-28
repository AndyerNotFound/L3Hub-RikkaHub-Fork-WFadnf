package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.clickable
import android.graphics.Rect
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.rerere.ai.provider.Model
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.FolderAdd
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Flowchart01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Layers01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Share04
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Folder
import me.rerere.rikkahub.ui.components.ai.ChatCapsule
import me.rerere.rikkahub.ui.components.ai.ChatCapsuleDivider
import me.rerere.rikkahub.ui.components.ai.chatCapsuleSurface
import me.rerere.rikkahub.ui.components.ui.AppearanceMaterial
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.advancedMaterial
import me.rerere.rikkahub.ui.components.ui.topBarMaterial
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
    hazeState: HazeState?,
    settings: Settings,
    assistant: Assistant,
    chatModel: Model?,
    folderId: Uuid?,
    folders: List<Folder>,
    hasMessages: Boolean,
    threePane: Boolean,
    sidebarOpen: Boolean,
    onOpenSidebar: () -> Unit,
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
    val material = settings.advancedAppearanceSetting.topBarMaterial(settings.displaySetting)
    val backdropMaterial = material?.takeIf { settings.displaySetting.showTopBarBackground }
    // 非实色顶栏已绘制整层背景时，组件只绘制轮廓，避免重复取样和叠色。
    val shareBackdrop = backdropMaterial?.style?.opaqueMaterial == false

    TopAppBar(
        // 底色交给材质层，容器本身保持透明，否则会和底叠成两倍浓度
        modifier = if (backdropMaterial != null) {
            Modifier.advancedMaterial(
                shape = RectangleShape,
                // 整条顶栏和里面的胶囊用同一个主题槽位（surfaceContainerLow），
                // 之前容器用 surfaceContainer，胶囊比底更浅，看起来像两套东西
                base = MaterialTheme.colorScheme.surfaceContainerLow,
                material = backdropMaterial,
                hazeState = hazeState,
            )
        } else {
            Modifier
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        title = {
            // 横屏三栏的顶栏只留菜单、新建和会话菜单：模型、详情、任务树都在右栏的标签页里。
            // 胶囊放在标题位：这里只拿到菜单与右侧按钮之间的剩余宽度，文件夹名超长时按这份宽度截断，
            // 放在导航位则会顶到右边的按钮上
            if (!threePane && chatModel != null) {
                ChatModelFolderPill(
                    model = chatModel,
                    folderId = folderId,
                    folders = folders,
                    onSelectFolder = onSelectFolder,
                    onCreateFolder = onCreateFolder,
                    onShowModelPicker = onShowModelPicker,
                    hazeState = hazeState,
                    material = material,
                    outlineOnly = shareBackdrop,
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
                            .chatCapsuleSurface(
                                shape = navShape,
                                base = navColor,
                                material = material,
                                hazeState = hazeState,
                                outlineOnly = shareBackdrop,
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
                    hazeState = hazeState,
                    material = material,
                    readableFloor = 0f,
                    outlineOnly = shareBackdrop,
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
    model: Model,
    folderId: Uuid?,
    folders: List<Folder>,
    onSelectFolder: (Uuid?) -> Unit,
    onCreateFolder: () -> Unit,
    onShowModelPicker: () -> Unit,
    hazeState: HazeState?,
    material: AppearanceMaterial?,
    outlineOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val folder = folders.firstOrNull { it.id == folderId }
    var menuExpanded by remember { mutableStateOf(false) }
    // 容器变换的起点：胶囊左半边那块可点区域。菜单从它的窗口矩形长出来，
    // 所以视觉上是「这块胶囊变成了菜单」，而不是旁边冒出一张卡。
    // 用 rememberSelectMenuAnchor 而不是 LocalView.current —— 后者是整个 Compose 宿主
    val anchor = rememberSelectMenuAnchor()

    ChatCapsule(
        hazeState = hazeState,
        material = material,
        readableFloor = 0f,
        outlineOnly = outlineOnly,
        modifier = modifier.height(TopBarButtonSize),
    ) {
        Box {
            Row(
                modifier = Modifier
                    .height(TopBarButtonSize)
                    .then(anchor.modifier)
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
                    // demo 的 .model-pill .fp：12px、次要色。文件夹在这颗胶囊里是坐标，
                    // 模型才是主体，两者同一档字号会把层级压平
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                anchorBounds = anchor.value.value,
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

// 胶囊里的文件夹选单：未归类排在最上面，当前归属打勾。
//
// 展开 / 收起走容器变换（NativeSelectMenu）：从胶囊那一小块原地长成菜单卡，
// 不是淡入淡出。起点是胶囊左半边那块可点区域的窗口矩形。
@Composable
private fun ChatFolderMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    folders: List<Folder>,
    selectedFolderId: Uuid?,
    onSelect: (Uuid?) -> Unit,
    onCreateFolder: () -> Unit,
    anchorBounds: Rect?,
) {
    // 文案必须在组合里读好：content lambda 在 LaunchedEffect 里执行，
    // 那里没有 Composition，直接调 stringResource 会崩
    val headerLabel = stringResource(R.string.chat_page_folder_pill)
    val unfiledLabel = stringResource(R.string.chat_page_folder_unfiled)
    val emptyLabel = stringResource(R.string.chat_page_folder_empty)
    val createLabel = stringResource(R.string.chat_page_create_folder)

    rememberNativeSelectMenu(
        expanded = expanded,
        onDismiss = onDismissRequest,
        anchorBounds = anchorBounds,
        // 顺序与下面的 addItem 一一对应：未归类 → 各文件夹 →（空时占位）→ 新建
        icons = buildList {
            add(HugeIcons.Folder01)
            folders.forEach { add(HugeIcons.Folder01) }
            if (folders.isEmpty()) add(null)
            add(HugeIcons.FolderAdd)
        },
    ) {
        addHeader(headerLabel)
        addItem(unfiledLabel, null, selectedFolderId == null) {
            onSelect(null)
        }
        folders.forEach { folder ->
            addItem(folder.name, null, folder.id == selectedFolderId) {
                onSelect(folder.id)
            }
        }
        if (folders.isEmpty()) {
            addItem(emptyLabel, null, false) { }
        }
        addDivider()
        addItem(createLabel, null, false) { onCreateFolder() }
    }
}

// 顶栏右侧的会话菜单；空对话没有内容可概览、导出或删除，菜单项全部置灰。
//
// 分成两组：概览与导出是一组，删除单独一组 —— 破坏性操作隔开，避免误触。
// 每组是独立的一张卡，组间留 2dp，圆角按 M3 的分组规则算。
@Composable
private fun ChatPageMenu(
    enabled: Boolean,
    onShowOverview: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val anchor = rememberSelectMenuAnchor()

    // 文案在组合里读好：content lambda 在 LaunchedEffect 里跑，没有 Composition
    val overviewLabel = stringResource(R.string.chat_page_conversation_map)
    val exportLabel = stringResource(R.string.export_title)
    val deleteLabel = stringResource(R.string.delete)

    rememberNativeSelectMenu(
        expanded = expanded,
        onDismiss = { expanded = false },
        anchorBounds = anchor.value.value,
        icons = listOf(
            HugeIcons.LeftToRightListBullet,
            HugeIcons.Share04,
            HugeIcons.Delete01,
        ),
    ) {
        addItem(overviewLabel, null, false, enabled, false) { onShowOverview() }
        addItem(exportLabel, null, false, enabled, false) { onExport() }
        startGroup()
        addItem(deleteLabel, null, false, enabled, true) { onDelete() }
    }

    IconButton(
        onClick = { expanded = true },
        modifier = Modifier
            .size(TopBarButtonSize)
            .then(anchor.modifier),
    ) {
        Icon(
            HugeIcons.MoreVertical,
            stringResource(R.string.more_options),
            modifier = Modifier.size(TopBarButtonIconSize),
        )
    }
}

