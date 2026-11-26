package me.rerere.rikkahub.ui.pages.setting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.availableKeys
import me.rerere.ai.provider.filterByKeywords
import me.rerere.ai.registry.ModelRegistry
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Key01
import me.rerere.hugeicons.stroke.Package01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.UserCircle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.ai.ModelAbilityTag
import me.rerere.rikkahub.ui.components.ai.ModelKeyGroup
import me.rerere.rikkahub.ui.components.ai.ModelKeyGroupStrip
import me.rerere.rikkahub.ui.components.ai.ModelModalityTag
import me.rerere.rikkahub.ui.components.ai.accountGroupLabel
import me.rerere.rikkahub.ui.components.ai.accountGroups
import me.rerere.rikkahub.ui.components.ai.availableAccountIds
import me.rerere.rikkahub.ui.components.ai.belongsToAccountGroup
import me.rerere.rikkahub.ui.components.ai.belongsToKeyGroup
import me.rerere.rikkahub.ui.components.ai.keyGroupLabel
import me.rerere.rikkahub.ui.components.ai.keyGroups
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.longPressReorder
import me.rerere.rikkahub.ui.components.ui.shadow.Button
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.modifier.MotionExpandCollapseHorizontal
import me.rerere.rikkahub.ui.style.IconButton
import me.rerere.rikkahub.ui.style.TextButton
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.adaptiveCornerRadius
import me.rerere.rikkahub.ui.theme.adaptiveStyledShape
import me.rerere.ui.components.AppModalSheet
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// 提供商模型列表：分组、远端模型拉取、模型选择抽屉

@Composable
internal fun ModelList(
    providerSetting: ProviderSetting,
    onUpdateProvider: (ProviderSetting) -> Unit,
) {
    val providerManager = koinInject<ProviderManager>()
    // 增删、排序已添加的模型不影响远端列表，去掉 models 再作为 key，避免每次编辑都重新请求
    val listKey = remember(providerSetting) { providerSetting.copyProvider(models = emptyList()) }
    val apiKeySetting = listKey as? ProviderSetting.ApiKey
    val availableKeys = remember(listKey) { apiKeySetting?.availableKeys.orEmpty() }
    // 一份登录账号一页，与多 Key 同一套分组条；只有一份登录时不分组
    val accountSetting = listKey as? ProviderSetting.DeepSeekAccount
    val accountGroups = remember(listKey) { accountSetting?.accountGroups().orEmpty() }
    val availableAccountIds = remember(listKey) { accountSetting?.availableAccountIds().orEmpty() }
    val allAccountsName = stringResource(R.string.model_account_group_all)
    val missingAccountName = stringResource(R.string.model_account_group_missing)
    val accountNames = remember(accountGroups) {
        accountGroups
            .mapNotNull { group ->
                group.key?.let { key -> runCatching { Uuid.parse(key) }.getOrNull() }?.let { it to group.name }
            }
            .toMap()
    }
    // 一个启用的 Key 一页，末尾再加一页收没有绑定 Key 的模型；只有一把 Key 时不分组
    val allKeysName = stringResource(R.string.model_key_group_all)
    val missingName = stringResource(R.string.model_key_group_missing)
    val keyGroups = remember(listKey) { apiKeySetting?.keyGroups().orEmpty() }
    // 多 Key 与多账号不会同时成立（账号类提供商没有 Key），谁有分组就按谁分
    val groups = if (keyGroups.isNotEmpty()) keyGroups else accountGroups
    val allGroupName = if (keyGroups.isNotEmpty()) allKeysName else allAccountsName
    val tabs = remember(groups, allGroupName) {
        groups + ModelKeyGroup(key = null, name = allGroupName)
    }
    // 选中的分组页：换提供商时回到第一页
    var selectedTab by rememberSaveable(listKey.id) { mutableStateOf(0) }
    val pageKey = if (groups.isEmpty()) null else tabs.getOrNull(selectedTab)?.key
    // 从这一页新建的模型默认绑这一页的账号
    val pageAccountId = groups.firstOrNull { it.key == pageKey }?.key
        ?.let { key -> runCatching { Uuid.parse(key) }.getOrNull() }
    val pageModels = remember(
        providerSetting.models,
        groups,
        pageKey,
        availableKeys,
        availableAccountIds,
    ) {
        when {
            groups.isEmpty() -> providerSetting.models
            keyGroups.isNotEmpty() -> providerSetting.models.filter {
                it.belongsToKeyGroup(pageKey, availableKeys)
            }

            else -> providerSetting.models.filter {
                it.belongsToAccountGroup(pageKey, availableAccountIds)
            }
        }
    }

    var refreshCount by remember { mutableIntStateOf(0) }
    // 每把 Key、每份登录各拉一份：分页各看各的，某一页读不到不影响别的页
    val remoteModels by produceState(RemoteModelPages(), listKey, refreshCount) {
        value = RemoteModelPages(
            value.pages.mapValues { (_, page) -> page.copy(loading = true, error = null) }
        )
        value = RemoteModelPages(
            fetchRemoteModelPages(providerManager, listKey, availableKeys, availableAccountIds)
        )
    }
    val remotePage = remoteModels.of(pageKey)

    val navController = LocalNavController.current
    var expanded by rememberSaveable { mutableStateOf(true) }
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // 列表只显示一页模型，拖动的下标要换回整个模型列表里的位置
        val moved = pageModels.getOrNull(from.index)
        val target = pageModels.getOrNull(to.index)
        if (moved != null && target != null) {
            val all = providerSetting.models
            val fromIndex = all.indexOfFirst { it.id == moved.id }
            val toIndex = all.indexOfFirst { it.id == target.id }
            if (fromIndex >= 0 && toIndex >= 0) {
                onUpdateProvider(providerSetting.moveMove(fromIndex, toIndex))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (groups.isNotEmpty()) {
                ModelKeyGroupStrip(
                    groups = tabs,
                    selectedKey = pageKey,
                    onSelect = { key -> selectedTab = tabs.indexOfFirst { it.key == key }.coerceAtLeast(0) },
                    modifier = Modifier.padding(top = 4.dp),
                    groupIcon = if (keyGroups.isNotEmpty()) HugeIcons.Key01 else HugeIcons.UserCircle,
                )
            }

            when {
                providerSetting.models.isEmpty() -> ExtensionEmptyState(
                    message = stringResource(R.string.setting_provider_page_no_models),
                    description = stringResource(R.string.setting_provider_page_add_models_hint),
                    icon = HugeIcons.Package01,
                )

                // 整个提供商有模型、这一页没有：说清是分组空着，别让人以为模型被删了
                pageModels.isEmpty() -> ExtensionEmptyState(
                    message = stringResource(R.string.model_key_group_empty),
                    description = stringResource(R.string.model_key_group_empty_desc),
                    icon = HugeIcons.Package01,
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .floatingToolbarVerticalNestedScroll(
                            expanded = expanded,
                            onExpand = { expanded = true },
                            onCollapse = { expanded = false },
                        ),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 128.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    state = lazyListState
                ) {
                    items(pageModels, key = { it.id }) { item ->
                        ReorderableItem(
                            state = reorderableLazyListState,
                            key = item.id
                        ) { isDragging ->
                            val editorScreen = Screen.ModelEditor(
                                providerId = providerSetting.id.toString(),
                                modelId = item.id.toString(),
                            )
                            ModelCard(
                                model = item,
                                keyGroupLabel = item.keyGroupLabel(providerSetting, allKeysName, missingName)
                                    ?: item.accountGroupLabel(
                                        provider = providerSetting,
                                        availableAccountIds = availableAccountIds,
                                        groupNames = accountNames,
                                        allAccountsName = allAccountsName,
                                        missingName = missingAccountName,
                                    ),
                                onDelete = {
                                    onUpdateProvider(providerSetting.delModel(item))
                                },
                                onOpenEditor = { navController.navigate(editorScreen) },
                                modifier = longPressReorder(isDragging),
                            )
                        }
                    }
                }
            }
        }

        HorizontalFloatingToolbar(
            expanded = expanded,
            colors = CustomColors.floatingToolbarColors,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -ScreenOffset)
                .height(64.dp),
        ) {
            // M3 无 leadingContent 时 balanced padding 公式会把高度撑爆，钉死 64dp 并关掉内部补偿
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                AddModelButton(
                    remoteModels = remotePage,
                    groupKey = pageKey,
                    groupAccountId = pageAccountId,
                    groupName = tabs.firstOrNull { it.key == pageKey }?.name,
                    onRefresh = { refreshCount++ },
                    // 只看这一页的模型：同名模型在别的分组里另有一条，不能混着算
                    selectedModels = pageModels,
                    // 同一分组重复勾选不新增，别的分组已有同一个模型时沿用它的价目
                    onAddModel = {
                        onUpdateProvider(providerSetting.addCatalogModel(it))
                    },
                    onRemoveModel = {
                        onUpdateProvider(providerSetting.delModel(it))
                    },
                    expanded = expanded,
                    parentProvider = providerSetting,
                    onUpdateProvider = onUpdateProvider,
                    onOpenEditor = {
                        navController.navigate(
                            Screen.ModelEditor(
                                providerId = providerSetting.id.toString(),
                                // 新建的模型跟着当前分组，保存后还在这一页
                                keyIndex = pageKey?.let { key -> availableKeys.indexOf(key).takeIf { it >= 0 } },
                                accountId = pageAccountId?.toString(),
                            )
                        )
                    },
                )
            }
        }
    }
}

/** 提供商远端的可用模型列表，[error] 不为空时 [models] 是上次成功拿到的结果 */
private data class RemoteModels(
    val models: List<Model> = emptyList(),
    val loading: Boolean = false,
    val error: Throwable? = null,
)

/**
 * 按 Key / 账号分开的远端模型，键为 null 的那份是全部合并结果。
 *
 * 每个 Key、每份登录开通的模型可能不同，合并之后就分不清某个模型能不能用某一份凭据请求，
 * 所以拉取和展示都按分组分开。
 */
private data class RemoteModelPages(
    val pages: Map<String?, RemoteModels> = emptyMap(),
) {
    fun of(key: String?): RemoteModels = pages[key] ?: RemoteModels(loading = pages.isEmpty())
}

/**
 * 每把 Key、每一份登录各拉一份远端模型。
 *
 * 只有一份凭据时只需要一份；多份时同时拉，全部页用手上这几份去重合并，
 * 只有全都失败才算读不到，否则错误会被空列表吃掉。
 *
 * 账号型提供商按账号拉：平台给不同账号开的模型可能不一样，合并成一份会让某一页出现
 * 这个账号根本用不了的模型。
 */
private suspend fun fetchRemoteModelPages(
    providerManager: ProviderManager,
    setting: ProviderSetting,
    keys: List<String>,
    accountIds: List<Uuid>,
): Map<String?, RemoteModels> = coroutineScope {
    suspend fun fetch(key: String? = null, accountId: Uuid? = null): RemoteModels {
        val keySetting = if (key != null && setting is ProviderSetting.ApiKey) {
            setting.withApiKey(key)
        } else {
            setting
        }
        return try {
            val provider = providerManager.getProviderByType(keySetting)
            val models = if (accountId != null) {
                provider.listModelsForAccount(keySetting, accountId)
            } else {
                provider.listModels(keySetting)
            }
            RemoteModels(models = models.sortedBy { it.modelId })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            RemoteModels(error = e)
        }
    }

    when {
        accountIds.size > 1 -> {
            val perAccount = accountIds
                .map { id -> async { id.toString() to fetch(accountId = id) } }
                .awaitAll()
                .toMap()
            val merged = perAccount.values.flatMap { it.models }.distinctBy { it.modelId }.sortedBy { it.modelId }
            val errors = perAccount.values.mapNotNull { it.error }
            mapOf(
                null to RemoteModels(
                    models = merged,
                    error = if (merged.isEmpty()) errors.firstOrNull() else null,
                )
            ) + perAccount
        }

        keys.size <= 1 -> mapOf(null to fetch(null))

        else -> {
            val perKey = keys.map { key -> async { key to fetch(key) } }.awaitAll().toMap()
            val merged = perKey.values.flatMap { it.models }.distinctBy { it.modelId }.sortedBy { it.modelId }
            val errors = perKey.values.mapNotNull { it.error }
            mapOf(
                null to RemoteModels(
                    models = merged,
                    error = if (merged.isEmpty()) errors.firstOrNull() else null,
                )
            ) + perKey
        }
    }
}

@Composable
private fun AddModelButton(
    remoteModels: RemoteModels,
    groupKey: String?,
    groupAccountId: Uuid?,
    groupName: String?,
    onRefresh: () -> Unit,
    selectedModels: List<Model>,
    expanded: Boolean,
    onAddModel: (Model) -> Unit,
    onRemoveModel: (Model) -> Unit,
    parentProvider: ProviderSetting,
    onUpdateProvider: (ProviderSetting) -> Unit,
    onOpenEditor: () -> Unit,
) {
    // 从这一页添加的模型只认这一页的 Key / 账号；全部页不加绑定，沿用提供商的顺序路由
    val modelKeys = listOfNotNull(groupKey)
    val modelAccountIds = listOfNotNull(groupAccountId)

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModelPicker(
            remoteModels = remoteModels,
            groupName = groupName,
            onRefresh = onRefresh,
            selectedModels = selectedModels,
            onModelSelected = { model ->
                val inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(model.modelId)
                val outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(model.modelId)
                val abilities = ModelRegistry.MODEL_ABILITIES.getData(model.modelId)
                onAddModel(
                    model.copy(
                        apiKeys = modelKeys,
                        accountIds = modelAccountIds,
                        inputModalities = inputModalities,
                        outputModalities = outputModalities,
                        abilities = abilities
                    )
                )
            },
            onModelDeselected = { model ->
                onRemoveModel(model)
            },
            onAllModelSelected = {
                // 逐个走 addCatalogModel：页内已有的跳过，别的分组已有的沿用它的价目
                onUpdateProvider(
                    it.fold(parentProvider) { provider, model ->
                        provider.addCatalogModel(
                            model.copy(
                                apiKeys = modelKeys,
                                accountIds = modelAccountIds,
                                inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(model.modelId),
                                outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(model.modelId),
                                abilities = ModelRegistry.MODEL_ABILITIES.getData(model.modelId),
                            )
                        )
                    }
                )
            },
            onAllModelDeselected = { filteredModels ->
                // 按 id 删：这一页的模型和别的分组里的同名模型不是同一条
                val removed = selectedModels.filter { model ->
                    filteredModels.any { it.modelId == model.modelId }
                }
                onUpdateProvider(
                    parentProvider.copyProvider(
                        models = parentProvider.models.filterNot { model ->
                            removed.any { it.id == model.id }
                        }
                    )
                )
            }
        )

        Button(
            onClick = onOpenEditor,
        ) {
            Row(
                modifier = Modifier,
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    HugeIcons.Add01,
                    contentDescription = stringResource(R.string.setting_provider_page_add_model)
                )
                MotionExpandCollapseHorizontal(visible = expanded) {
                    Row {
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.setting_provider_page_add_new_model))
                    }
                }
            }
        }
    }

}

@Composable
private fun ModelPicker(
    remoteModels: RemoteModels,
    groupName: String?,
    onRefresh: () -> Unit,
    selectedModels: List<Model>,
    onModelSelected: (Model) -> Unit,
    onModelDeselected: (Model) -> Unit,
    onAllModelSelected: (List<Model>) -> Unit,
    onAllModelDeselected: (List<Model>) -> Unit
) {
    var showModal by remember { mutableStateOf(false) }
    AppModalSheet(
        visible = showModal,
        onDismissRequest = { showModal = false },
        title = stringResource(R.string.model_list_select_model),
    ) {
        var filterText by remember { mutableStateOf("") }
        val filteredModels = remoteModels.models.filterByKeywords(filterText)
        val loading = remoteModels.loading
        val refreshError = remoteModels.error
        val hasModels = remoteModels.models.isNotEmpty()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(8.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 标题栏和添加所有按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.setting_provider_page_avaliable_models),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )

                // 拉取期间按钮置灰，进度由标题下的那条进度条给出
                IconButton(
                    onClick = onRefresh,
                    enabled = !loading,
                ) {
                    Icon(
                        imageVector = HugeIcons.Refresh01,
                        contentDescription = stringResource(R.string.setting_provider_page_models_refresh),
                    )
                }

                // 一个模型都没拿到时，全选没有意义
                if (hasModels) {
                    val unselectedCount = filteredModels.count { model ->
                        selectedModels.none { it.modelId == model.modelId }
                    }

                    TextButton(
                        onClick = {
                            if (unselectedCount > 0) {
                                onAllModelSelected(filteredModels)
                            } else {
                                onAllModelDeselected(filteredModels)
                            }
                        },
                    ) {
                        Text(
                            if (unselectedCount > 0) stringResource(
                                R.string.setting_provider_page_select_all,
                                unselectedCount
                            ) else stringResource(R.string.setting_provider_page_deselect_models)
                        )
                    }
                }
            }

            // 从哪个分组打开的：这一份远端列表只对这一页的 Key / 账号有效
            if (groupName != null) {
                Text(
                    text = groupName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // 拉取期间标题下面一直有一条进度条，首次加载时下面还有整块加载态
            if (loading) {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    // 还没有任何结果：给一块明确的加载态，别让抽屉看起来是空的
                    loading && !hasModels -> RemoteModelsLoading()

                    // 一个模型都读不到：说清原因，留一个重试入口
                    refreshError != null && !hasModels -> RemoteModelsError(
                        error = refreshError,
                        onRetry = onRefresh,
                    )

                    else -> Column(modifier = Modifier.fillMaxSize()) {
                        // 刷新失败但手上还有上次的结果：列表照旧显示，错误收在上方
                        if (refreshError != null) {
                            RemoteModelsErrorBanner(error = refreshError)
                        }

                        if (filteredModels.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(
                                        if (filterText.isBlank()) {
                                            R.string.setting_provider_page_models_empty
                                        } else {
                                            R.string.model_list_no_match
                                        }
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                                contentPadding = PaddingValues(8.dp),
                            ) {
                                itemsIndexed(filteredModels) { index, it ->
                                    Surface(
                                        // 分组圆角：首尾项的外角大、中间项的内角小，跟随全局圆角缩放
                                        shape = adaptiveStyledShape(
                                            topRadius = if (index == 0) {
                                                adaptiveCornerRadius(20.dp)
                                            } else {
                                                adaptiveCornerRadius(4.dp)
                                            },
                                            bottomRadius = if (index == filteredModels.lastIndex) {
                                                adaptiveCornerRadius(20.dp)
                                            } else {
                                                adaptiveCornerRadius(4.dp)
                                            },
                                        ),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(
                                                12.dp
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                                        ) {
                                            AutoAIIcon(
                                                it.modelId,
                                                Modifier.size(32.dp)
                                            )
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(
                                                    4.dp
                                                ),
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Text(
                                                    text = it.modelId,
                                                    style = MaterialTheme.typography.titleSmall,
                                                )

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    val modelMeta = remember(it) {
                                                        it.copy(
                                                            inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(it.modelId),
                                                            outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(it.modelId),
                                                            abilities = ModelRegistry.MODEL_ABILITIES.getData(it.modelId),
                                                        )
                                                    }
                                                    ModelModalityTag(
                                                        model = modelMeta,
                                                    )
                                                    ModelAbilityTag(
                                                        model = modelMeta,
                                                    )
                                                }
                                            }
                                            val added = selectedModels.any { model -> model.modelId == it.modelId }
                                            FilledTonalIconToggleButton(
                                                checked = added,
                                                onCheckedChange = { checked ->
                                                    if (checked) {
                                                        onModelSelected(it)
                                                    } else {
                                                        // 从selectedModels中计算出要删除的model，因为删除需要id匹配，而不是ModelId
                                                        onModelDeselected(selectedModels.firstOrNull { model -> model.modelId == it.modelId }
                                                            ?: it)
                                                    }
                                                }
                                            ) {
                                                Icon(if (added) HugeIcons.Tick01 else HugeIcons.Add01, null)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 没有模型可筛的时候不摆筛选框
            if (hasModels) {
                OutlinedTextField(
                    value = filterText,
                    onValueChange = {
                        filterText = it
                    },
                    label = { Text(stringResource(R.string.setting_provider_page_filter_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(stringResource(R.string.setting_provider_page_filter_example))
                    },
                )
            }
        }
    }
    BadgedBox(
        badge = {
            if (remoteModels.models.isNotEmpty()) {
                Badge {
                    Text(remoteModels.models.size.toString())
                }
            }
        }
    ) {
        IconButton(
            onClick = {
                showModal = true
            }
        ) {
            Icon(HugeIcons.Package01, null)
        }
    }
}

// 首次打开抽屉、还没有任何结果时的整块加载态
@Composable
private fun RemoteModelsLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        ContainedLoadingIndicator(modifier = Modifier.size(48.dp))
        Text(
            text = stringResource(R.string.setting_provider_page_models_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// 一个模型都读不到：原因和重试都摆出来，别留一个空白抽屉
@Composable
private fun RemoteModelsError(
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = HugeIcons.Alert01,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            text = stringResource(R.string.setting_provider_page_models_failed),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = error.displayMessage(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        FilledTonalButton(onClick = onRetry) {
            Icon(
                imageVector = HugeIcons.Refresh01,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.setting_provider_page_models_retry))
        }
    }
}

// 刷新失败、手上还有上次的结果：只在列表上方提示，不挡住选择
@Composable
private fun RemoteModelsErrorBanner(
    error: Throwable,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.setting_provider_page_models_failed),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = error.displayMessage(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// 网络异常的 message 常常是空的，退回到异常类名，界面上至少有一行字
private fun Throwable.displayMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
