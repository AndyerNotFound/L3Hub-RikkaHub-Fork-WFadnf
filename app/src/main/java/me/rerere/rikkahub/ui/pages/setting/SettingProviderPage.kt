package me.rerere.rikkahub.ui.pages.setting

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Camera01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Tick02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.RECOMMENDED_PROVIDERS
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.components.ui.QRCodeScannerDialog
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.cardGroupItemShape
import me.rerere.rikkahub.ui.components.ui.decodeProviderSetting
import me.rerere.rikkahub.ui.components.ui.longPressReorder
import me.rerere.rikkahub.ui.components.ui.shadow.Button
import me.rerere.rikkahub.ui.components.ui.shadow.FilledTonalIconButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.heroSource
import me.rerere.rikkahub.ui.terminal.Card
import me.rerere.rikkahub.ui.terminal.IconButton
import me.rerere.rikkahub.ui.terminal.OutlinedButton
import me.rerere.rikkahub.ui.terminal.TextButton
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPadding
import me.rerere.rikkahub.utils.ImageUtils
import me.rerere.rikkahub.utils.plus
import me.rerere.ui.components.AnimatedAlertDialog
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.ui.toast.ToastType
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid

// 未分组那一栏的 key 和折叠状态都单独占一个名字，用户完全可以把分组叫 "ungrouped"，
// 命名分组统一带 "group:" 前缀，两边不会撞在一起
private const val UNGROUPED_KEY = "ungrouped"

private fun groupKey(name: String?): String = name?.let { "group:$it" } ?: UNGROUPED_KEY

// 提供商页的列表条目：分组标题和提供商各自是一个可拖拽条目。
// 拖标题就是排分组，拖提供商就是排组内顺序，两者共用同一份 LazyColumn 下标。
private sealed interface ProviderListEntry {
    val key: String

    data class GroupHeader(
        val name: String?,
        val count: Int,
    ) : ProviderListEntry {
        override val key: String get() = groupKey(name)
    }

    data class ProviderRow(
        val provider: ProviderSetting,
        val group: String?,
    ) : ProviderListEntry {
        override val key: String get() = "provider:${provider.id}"
    }
}

@Composable
fun SettingProviderPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var searchQuery by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ProviderSetting?>(null) }
    val lazyListState = rememberLazyListState()

    val filteredProviders = remember(settings.providers, searchQuery) {
        if (searchQuery.isBlank()) {
            settings.providers
        } else {
            settings.providers.filter { provider ->
                provider.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val groupOf: (ProviderSetting) -> String? = { provider ->
        settings.providerGroupAssignments[provider.id.toString()]
    }
    // 一个分组都没有时保持原来的平铺列表，不给只用默认分组的用户硬塞一个「未分组」标题
    val grouped = settings.providerGroups.isNotEmpty()
    val orderedGroups = remember(settings.providerGroups, settings.providers, settings.providerGroupAssignments) {
        val assigned = settings.providers.mapNotNull { provider ->
            settings.providerGroupAssignments[provider.id.toString()]
        }.distinct()
        buildList {
            settings.providerGroups.forEach { name -> if (name in assigned) add(name) }
            assigned.filterNot { it in settings.providerGroups }.sorted().forEach { add(it) }
        }
    }
    // 折叠状态只活在这一页，和模型选择侧栏一样，离开再进来默认展开
    val collapsedGroups = remember { mutableStateMapOf<String, Boolean>() }

    // 有提供商的分组才占位置；一个命名分组都没有时退回平铺，不单独给「未分组」加标题
    val groupSections = orderedGroups.map { name ->
        name to filteredProviders.filter { groupOf(it) == name }
    }.filter { (_, providers) -> providers.isNotEmpty() }
    val ungroupedProviders = filteredProviders.filter { groupOf(it) == null }
    val showGroupHeaders = grouped && groupSections.isNotEmpty()

    val entries: List<ProviderListEntry> = if (searchQuery.isNotBlank() || !showGroupHeaders) {
        // 搜索结果拍平：过滤后的下标和真实设置里的顺序对不上，拖拽同时禁用
        filteredProviders.map { ProviderListEntry.ProviderRow(it, groupOf(it)) }
    } else {
        buildList {
            groupSections.forEach { (groupName, groupProviders) ->
                add(ProviderListEntry.GroupHeader(groupName, groupProviders.size))
                if (collapsedGroups[groupKey(groupName)] != true) {
                    groupProviders.forEach { add(ProviderListEntry.ProviderRow(it, groupName)) }
                }
            }
            if (ungroupedProviders.isNotEmpty()) {
                add(ProviderListEntry.GroupHeader(null, ungroupedProviders.size))
                if (collapsedGroups[UNGROUPED_KEY] != true) {
                    ungroupedProviders.forEach { add(ProviderListEntry.ProviderRow(it, null)) }
                }
            }
        }
    }
    // 拖拽回调是建表时记住的，用最新的条目表算目的地，别拿第一次组合的那份
    val currentEntries by rememberUpdatedState(entries)

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        if (searchQuery.isNotBlank()) return@rememberReorderableLazyListState
        val entriesNow = currentEntries
        val fromEntry = entriesNow.getOrNull(from.index) ?: return@rememberReorderableLazyListState
        val toEntry = entriesNow.getOrNull(to.index) ?: return@rememberReorderableLazyListState
        when {
            // 分组头：落在另一个分组头或它的提供商上都算换到这个分组的位置
            fromEntry is ProviderListEntry.GroupHeader -> {
                // 未分组那一栏固定在最末尾，不参与排序
                val fromName = fromEntry.name ?: return@rememberReorderableLazyListState
                val targetName = when (toEntry) {
                    is ProviderListEntry.GroupHeader -> toEntry.name
                    is ProviderListEntry.ProviderRow -> toEntry.group
                }
                if (targetName == null || targetName == fromName) {
                    return@rememberReorderableLazyListState
                }
                vm.updateSettings { latest ->
                    // 空分组不显示，但留在原来的槽位：只重排看得见的名字
                    val groups = latest.providerGroups.toMutableList()
                    val fromIndex = groups.indexOf(fromName)
                    val toIndex = groups.indexOf(targetName)
                    if (fromIndex == -1 || toIndex == -1) return@updateSettings latest
                    groups.add(toIndex, groups.removeAt(fromIndex))
                    latest.copy(providerGroups = groups)
                }
            }

            // 提供商：只在同一组里换位置。跨组会改动分组归属，不在这个手势里做
            fromEntry is ProviderListEntry.ProviderRow &&
                toEntry is ProviderListEntry.ProviderRow &&
                fromEntry.group == toEntry.group -> {
                val group = fromEntry.group
                val movedProvider = fromEntry.provider
                val targetProvider = toEntry.provider
                vm.updateSettings { latest ->
                    val sameGroup = latest.providers.filter { provider ->
                        latest.providerGroupAssignments[provider.id.toString()] == group
                    }
                    val fromIndex = sameGroup.indexOfFirst { it.id == movedProvider.id }
                    val toIndex = sameGroup.indexOfFirst { it.id == targetProvider.id }
                    if (fromIndex == -1 || toIndex == -1 || fromIndex == toIndex) {
                        return@updateSettings latest
                    }
                    val reordered = sameGroup.toMutableList().apply {
                        add(toIndex, removeAt(fromIndex))
                    }
                    // 把组内新顺序写回全局列表里这一组占的槽位，别的提供商不动
                    val slots = latest.providers.indices.filter { index ->
                        latest.providerGroupAssignments[latest.providers[index].id.toString()] == group
                    }
                    if (slots.size != reordered.size) return@updateSettings latest
                    val providers = latest.providers.toMutableList()
                    slots.forEachIndexed { index, slot -> providers[slot] = reordered[index] }
                    latest.copy(providers = providers)
                }
            }
        }
    }

    // 设置是异步写入的，等新提供商真正出现在列表里再滚过去；新建时会跳详情页，所以要能跨页面保存
    var pendingScrollId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingScrollId, entries) {
        val id = pendingScrollId ?: return@LaunchedEffect
        val index = entries.indexOfFirst { entry ->
            entry is ProviderListEntry.ProviderRow && entry.provider.id.toString() == id
        }
        if (index >= 0) {
            lazyListState.animateScrollToItem(index)
            pendingScrollId = null
        }
    }

    fun addProvider(provider: ProviderSetting) {
        // 搜索词可能把新提供商过滤掉
        searchQuery = ""
        // 新提供商没有分组，落在未分组里；它被折叠时滚不过去
        collapsedGroups[UNGROUPED_KEY] = false
        pendingScrollId = provider.id.toString()
        vm.updateSettings { latest ->
            // 连点时第二次写入已经能看到第一次的结果，避免出现重复 id
            if (latest.providers.any { it.id == provider.id }) return@updateSettings latest
            latest.copy(providers = listOf(provider) + latest.providers)
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(R.string.setting_provider_page_title))
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    // 推荐提供商保留固定 id，读盘后才能按 id 补回不落盘的描述
                    RecommendProviderButton(
                        addedIds = settings.providers.map { it.id }.toSet(),
                    ) { provider ->
                        addProvider(provider.copyProvider())
                    }
                    ImportProviderButton {
                        addProvider(it.copyProvider(Uuid.random()))
                    }
                    AddButton { provider ->
                        addProvider(provider)
                        navController.navigate(Screen.SettingProviderDetail(providerId = provider.id.toString()))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // Search bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .adaptiveHorizontalPadding(16.dp)
                    .padding(vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.setting_provider_page_search_providers)) },
                leadingIcon = {
                    Icon(HugeIcons.Search01, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(HugeIcons.Cancel01, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )


            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .imePadding(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp) +
                    PaddingValues(bottom = innerPadding.calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = lazyListState,
            ) {
                items(entries, key = { it.key }) { entry ->
                    when (entry) {
                        is ProviderListEntry.GroupHeader -> {
                            ReorderableItem(
                                state = reorderableState,
                                key = entry.key,
                            ) { isDragging ->
                                ProviderGroupHeader(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (showGroupHeaders && entry.name != null && groupSections.size > 1) {
                                                longPressReorder(isDragging)
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    name = entry.name,
                                    count = entry.count,
                                    collapsed = collapsedGroups[entry.key] == true,
                                    onClick = {
                                        collapsedGroups[entry.key] = collapsedGroups[entry.key] != true
                                    },
                                )
                            }
                        }

                        is ProviderListEntry.ProviderRow -> {
                            ReorderableItem(
                                state = reorderableState,
                                key = entry.key,
                            ) { isDragging ->
                                ProviderItem(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(longPressReorder(isDragging, enabled = searchQuery.isBlank()))
                                        .heroSource(
                                            Screen.SettingProviderDetail(
                                                providerId = entry.provider.id.toString()
                                            )
                                        ),
                                    provider = entry.provider,
                                    onClick = {
                                        navController.navigate(
                                            Screen.SettingProviderDetail(
                                                providerId = entry.provider.id.toString()
                                            )
                                        )
                                    },
                                    onDelete = {
                                        deleteTarget = entry.provider
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    RikkaConfirmDialog(
        show = deleteTarget != null,
        title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            deleteTarget?.let { target ->
                vm.updateSettings { latest -> latest.copy(providers = latest.providers.filter { it.id != target.id }) }
            }
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
    ) {
        Text(stringResource(R.string.setting_provider_page_delete_dialog_text))
    }
}

@Composable
private fun RecommendProviderButton(
    addedIds: Set<Uuid>,
    onAdd: (ProviderSetting) -> Unit
) {
    val toaster = LocalToaster.current
    var showSheet by remember { mutableStateOf(false) }
    val importSuccessMessage = stringResource(R.string.setting_provider_page_import_success)

    IconButton(
        onClick = { showSheet = true }
    ) {
        Icon(HugeIcons.Sparkles, contentDescription = stringResource(R.string.setting_provider_page_recommend))
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.setting_provider_page_recommend),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    RECOMMENDED_PROVIDERS.fastForEachIndexed { index, provider ->
                        RecommendProviderItem(
                            provider = provider,
                            added = provider.id in addedIds,
                            shape = cardGroupItemShape(index, RECOMMENDED_PROVIDERS.size),
                            onAdd = {
                                onAdd(provider)
                                toaster.show(
                                    importSuccessMessage,
                                    type = ToastType.Success
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendProviderItem(
    provider: ProviderSetting,
    added: Boolean,
    shape: Shape,
    onAdd: () -> Unit
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AutoAIIcon(
                        name = provider.name,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.7f)) {
                        provider.shortDescription()
                    }
                }
            }
            FilledTonalIconButton(onClick = onAdd, enabled = !added) {
                if (added) {
                    Icon(HugeIcons.Tick02, contentDescription = "已添加")
                } else {
                    Icon(HugeIcons.Add01, contentDescription = stringResource(R.string.setting_provider_page_add))
                }
            }
        }
    }
}

@Composable
private fun ImportProviderButton(
    onAdd: (ProviderSetting) -> Unit
) {
    val toaster = LocalToaster.current
    val context = LocalContext.current
    var showImportDialog by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }

    if (showScanner) {
        QRCodeScannerDialog(
            onResult = { content ->
                showScanner = false
                handleScannedQRCode(content, onAdd, toaster, context)
            },
            onPermissionDenied = {
                showScanner = false
                toaster.show(context.getString(R.string.setting_provider_page_no_permission), type = ToastType.Error)
            },
            onDismissRequest = { showScanner = false },
        )
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            handleImageQRCode(it, onAdd, toaster, context)
        }
    }

    IconButton(
        onClick = {
            showImportDialog = true
        }
    ) {
        Icon(HugeIcons.FileImport, null)
    }

    if (showImportDialog) {
        AnimatedAlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.setting_provider_page_import_dialog_title),
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.setting_provider_page_import_dialog_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 主要操作：扫描二维码
                        Button(
                            onClick = {
                                showImportDialog = false
                                showScanner = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Camera01,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.setting_provider_page_scan_qr_code),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        // 次要操作：从相册选择
                        OutlinedButton(
                            onClick = {
                                showImportDialog = false
                                pickImageLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Image02,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.setting_provider_page_select_from_gallery),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showImportDialog = false },
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }
}

private fun handleScannedQRCode(
    content: String,
    onAdd: (ProviderSetting) -> Unit,
    toaster: me.rerere.ui.toast.ToasterState,
    context: android.content.Context
) {
    runCatching {
        val setting = decodeProviderSetting(content)
        onAdd(setting)
        toaster.show(
            context.getString(R.string.setting_provider_page_import_success),
            type = ToastType.Success
        )
    }.onFailure { error ->
        toaster.show(
            context.getString(R.string.setting_provider_page_qr_decode_failed, error.message ?: ""),
            type = ToastType.Error
        )
    }
}

private fun handleImageQRCode(
    uri: Uri,
    onAdd: (ProviderSetting) -> Unit,
    toaster: me.rerere.ui.toast.ToasterState,
    context: android.content.Context
) {
    runCatching {
        // 使用ImageUtils解析二维码
        val qrContent = ImageUtils.decodeQRCodeFromUri(context, uri)

        if (qrContent.isNullOrEmpty()) {
            toaster.show(
                context.getString(R.string.setting_provider_page_no_qr_found),
                type = ToastType.Error
            )
            return
        }

        val setting = decodeProviderSetting(qrContent)
        onAdd(setting)
        toaster.show(
            context.getString(R.string.setting_provider_page_import_success),
            type = ToastType.Success
        )
    }.onFailure { error ->
        toaster.show(
            context.getString(R.string.setting_provider_page_image_qr_decode_failed, error.message ?: ""),
            type = ToastType.Error
        )
    }
}


// 新建时只选类型，其余配置到详情页里填
@Composable
private fun AddButton(onAdd: (ProviderSetting) -> Unit) {
    var showSheet by remember { mutableStateOf(false) }
    val templates = remember {
        listOf(
            ProviderSetting.OpenAI(),
            ProviderSetting.Google(),
            ProviderSetting.Claude(),
            ProviderSetting.ChatGPT(),
            ProviderSetting.Grok(),
        )
    }

    IconButton(
        onClick = { showSheet = true }
    ) {
        Icon(HugeIcons.Add01, "Add")
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.setting_provider_page_add_provider),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    templates.fastForEachIndexed { index, template ->
                        Surface(
                            onClick = {
                                showSheet = false
                                onAdd(template.copyProvider(Uuid.random()))
                            },
                            shape = cardGroupItemShape(index, templates.size),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val typeName = template.typeName()
                                AutoAIIcon(
                                    name = typeName,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = typeName,
                                    style = MaterialTheme.typography.titleMediumEmphasized,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(HugeIcons.ArrowRight01, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 类型的显示名，和用户可改的 ProviderSetting.name 分开
@Composable
private fun ProviderSetting.typeName(): String = when (this) {
    is ProviderSetting.OpenAI -> "OpenAI"
    is ProviderSetting.Google -> "Google"
    is ProviderSetting.Claude -> "Claude"
    is ProviderSetting.ChatGPT -> stringResource(R.string.setting_provider_page_type_subscription, "ChatGPT")
    is ProviderSetting.Grok -> stringResource(R.string.setting_provider_page_type_subscription, "Grok")
}

// 分组标题：整行可点折叠，长按整行拖拽排序；未分组那一栏不能拖，它在列表末尾是固定位置
@Composable
private fun ProviderGroupHeader(
    name: String?,
    count: Int,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = name ?: stringResource(R.string.setting_provider_group_none),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Icon(
                imageVector = if (collapsed) HugeIcons.ArrowRight01 else HugeIcons.ArrowDown01,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProviderItem(
    provider: ProviderSetting,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (provider.enabled) {
                CustomColors.listItemColors.containerColor
            } else MaterialTheme.colorScheme.errorContainer,
        ),
        onClick = {
            onClick()
        }
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AutoAIIcon(
                name = provider.name,
                modifier = Modifier.size(40.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.7f)) {
                        provider.shortDescription()
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Tag(type = if (provider.enabled) TagType.SUCCESS else TagType.WARNING) {
                        Text(stringResource(if (provider.enabled) R.string.setting_provider_page_enabled else R.string.setting_provider_page_disabled))
                    }
                    Tag(type = TagType.INFO) {
                        Text(
                            stringResource(
                                R.string.setting_provider_page_model_count,
                                provider.models.size
                            )
                        )
                    }
                    if (provider.name == "AiHubMix") {
                        Tag(type = TagType.INFO) {
                            Text(stringResource(R.string.setting_provider_page_aihubmix_discount))
                        }
                    }
                }
            }
            ItemActionMenu(
                actions = listOf(
                    ItemAction(
                        text = stringResource(R.string.delete),
                        icon = HugeIcons.Delete01,
                        destructive = true,
                        enabled = !provider.builtIn,
                        onClick = onDelete,
                    ),
                )
            )
        }
    }
}
