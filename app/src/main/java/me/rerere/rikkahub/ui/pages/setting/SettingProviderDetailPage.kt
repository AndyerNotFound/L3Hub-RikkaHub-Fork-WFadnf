package me.rerere.rikkahub.ui.pages.setting
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.RoundedColumn
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.filterByKeywords
import me.rerere.ai.provider.supportsQuota
import me.rerere.ai.registry.ModelRegistry
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Connect
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.FloppyDisk
import me.rerere.hugeicons.stroke.Package01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Share01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.account.preserveCredential
import me.rerere.rikkahub.data.datastore.removeProvider
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.ai.ModelAbilityTag
import me.rerere.rikkahub.ui.components.ai.ModelModalityTag
import me.rerere.rikkahub.ui.components.ai.ProviderQuotaText
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.PagerTab
import me.rerere.rikkahub.ui.components.ui.PagerTabs
import me.rerere.rikkahub.ui.components.ui.ShareSheet
import me.rerere.rikkahub.ui.components.ui.SiliconFlowPowerByIcon
import me.rerere.rikkahub.ui.components.ui.longPressReorder
import me.rerere.rikkahub.ui.components.ui.rememberShareSheetState
import me.rerere.rikkahub.ui.components.ui.shadow.Button
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.modifier.MotionExpandCollapseHorizontal
import me.rerere.rikkahub.ui.pages.assistant.detail.CustomHeaders
import me.rerere.rikkahub.ui.pages.setting.components.ProviderConfigure
import me.rerere.rikkahub.ui.pages.setting.components.ProviderConnectionTestDialog
import me.rerere.rikkahub.ui.pages.setting.components.ProviderGuardSettings
import me.rerere.rikkahub.ui.pages.setting.components.SettingProviderQuotaQuery
import me.rerere.rikkahub.ui.style.IconButton
import me.rerere.rikkahub.ui.style.RadioButton
import me.rerere.rikkahub.ui.style.TextButton
import me.rerere.rikkahub.ui.style.TopAppBar
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.adaptiveCornerRadius
import me.rerere.rikkahub.ui.theme.adaptiveStyledShape
import me.rerere.rikkahub.ui.theme.rememberEffectsSpec
import me.rerere.ui.components.AnimatedAlertDialog
import me.rerere.ui.components.SaltModalSheet
import me.rerere.ui.toast.ToastType
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private enum class ProviderDetailTab(
    override val icon: ImageVector,
    @param:StringRes private val labelRes: Int,
) : PagerTab {
    CONFIG(HugeIcons.Tools, R.string.setting_provider_page_configuration),
    MODELS(HugeIcons.Package01, R.string.setting_provider_page_models),
    ADVANCED(HugeIcons.Settings03, R.string.setting_provider_page_advanced_settings);

    override val label: String
        @Composable get() = stringResource(labelRes)
}

@Composable
fun SettingProviderDetailPage(id: Uuid, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val provider = settings.providers.find { it.id == id }
    if (provider == null) {
        // 提供商在别处被删掉了：留一个能按返回的壳，别把用户丢在一张空白页上
        LaunchedEffect(Unit) {
            delay(400)
            navController.popBackStack()
        }
        Box(modifier = Modifier.fillMaxSize()) {
            BackButton(modifier = Modifier.padding(16.dp))
        }
        return
    }
    val pager = rememberPagerState { ProviderDetailTab.entries.size }
    val toaster = LocalToaster.current
    val context = LocalContext.current

    val onEdit = { newProvider: ProviderSetting ->
        vm.updateSettings { latest ->
            latest.copy(
                providers = latest.providers.map {
                    if (newProvider.id == it.id) {
                        newProvider.preserveCredential(it)
                    } else {
                        it
                    }
                }
            )
        }
    }
    val onDelete = {
        vm.updateSettings { latest -> latest.removeProvider(provider.id) }
        navController.popBackStack()
    }

    Scaffold(
        containerColor = CustomColors.topBarColors.containerColor,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    BackButton()
                },
                colors = CustomColors.topBarColors,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AutoAIIcon(
                            provider.name,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(text = provider.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    // 账号型提供商离开账号就没法用
                    if (provider !is ProviderSetting.Account) {
                        val shareSheetState = rememberShareSheetState()
                        ShareSheet(shareSheetState)
                        IconButton(
                            onClick = {
                                shareSheetState.show(provider)
                            }
                        ) {
                            Icon(HugeIcons.Share01, null)
                        }
                    }
                }
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .consumeWindowInsets(it)
        ) {
            PagerTabs(
                pagerState = pager,
                tabs = ProviderDetailTab.entries,
                modifier = Modifier.padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            )

            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (ProviderDetailTab.entries[page]) {
                    ProviderDetailTab.CONFIG -> {
                        SettingProviderConfigPage(
                            provider = provider,
                            vm = vm,
                            onEdit = {
                                onEdit(it)
                                toaster.show(
                                    context.getString(R.string.setting_provider_page_save_success),
                                    type = ToastType.Success
                                )
                            },
                            onDelete = {
                                onDelete()
                            }
                        )
                    }

                    ProviderDetailTab.MODELS -> {
                        SettingProviderModelPage(
                            provider = provider,
                            onEdit = onEdit,
                        )
                    }

                    ProviderDetailTab.ADVANCED -> {
                        SettingProviderAdvancedPage(
                            provider = provider,
                            onEdit = onEdit
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingProviderConfigPage(
    provider: ProviderSetting,
    vm: SettingVM,
    onEdit: (ProviderSetting) -> Unit,
    onDelete: () -> Unit
) {
    var internalProvider by remember(provider) { mutableStateOf(provider) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(true) }

    val settings by vm.settings.collectAsStateWithLifecycle()
    val providerId = provider.id.toString()
    val group = settings.providerGroupAssignments[providerId]
    val proxyHost = provider.proxyHost()
    val proxyUrl = proxyHost?.let { settings.providerProxies[it] }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showProxyDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var proxyInput by remember { mutableStateOf("") }
    var proxyUsername by remember { mutableStateOf("") }
    var proxyPassword by remember { mutableStateOf("") }

    fun deleteGroup(name: String) {
        vm.updateSettings { latest ->
            latest.copy(
                providerGroups = latest.providerGroups - name,
                providerGroupAssignments = latest.providerGroupAssignments.filterValues { it != name },
            )
        }
    }

    fun assignGroup(name: String?) {
        vm.updateSettings { latest ->
            latest.copy(
                providerGroups = if (name != null && name !in latest.providerGroups) {
                    latest.providerGroups + name
                } else {
                    latest.providerGroups
                },
                providerGroupAssignments = if (name == null) {
                    latest.providerGroupAssignments - providerId
                } else {
                    latest.providerGroupAssignments + (providerId to name)
                },
            )
        }
    }

    if (showGroupDialog) {
        AnimatedAlertDialog(
            onDismissRequest = { showGroupDialog = false },
            title = { Text(stringResource(R.string.setting_provider_group_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                assignGroup(null)
                                showGroupDialog = false
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = group == null, onClick = null)
                        Text(stringResource(R.string.setting_provider_group_none))
                    }
                    settings.providerGroups.forEach { name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    assignGroup(name)
                                    showGroupDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = group == name, onClick = null)
                            Text(
                                text = name,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { deleteGroup(name) }) {
                                Icon(
                                    imageVector = HugeIcons.Delete01,
                                    contentDescription = stringResource(R.string.setting_provider_group_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text(stringResource(R.string.setting_provider_group_new)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newGroupName.isNotBlank(),
                    onClick = {
                        assignGroup(newGroupName.trim())
                        newGroupName = ""
                        showGroupDialog = false
                    },
                ) {
                    Text(stringResource(R.string.setting_provider_group_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showGroupDialog = false }) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            },
        )
    }

    if (showProxyDialog) {
        AnimatedAlertDialog(
            onDismissRequest = { showProxyDialog = false },
            title = { Text(stringResource(R.string.setting_provider_proxy_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.setting_provider_proxy_desc))
                    OutlinedTextField(
                        value = proxyInput,
                        onValueChange = { proxyInput = it },
                        placeholder = { Text("http://127.0.0.1:7890 / socks5://127.0.0.1:1080") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = proxyUsername,
                        onValueChange = { proxyUsername = it },
                        label = { Text(stringResource(R.string.setting_provider_proxy_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = proxyPassword,
                        onValueChange = { proxyPassword = it },
                        label = { Text(stringResource(R.string.setting_provider_proxy_password)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (proxyUrl != null) {
                        TextButton(
                            onClick = {
                                vm.updateSettings { latest ->
                                    if (proxyHost == null) {
                                        latest
                                    } else {
                                        latest.copy(
                                            providerProxies = latest.providerProxies - proxyHost,
                                            providerProxyUsernames = latest.providerProxyUsernames - proxyHost,
                                            providerProxyPasswords = latest.providerProxyPasswords - proxyHost,
                                        )
                                    }
                                }
                                showProxyDialog = false
                            },
                        ) {
                            Text(stringResource(R.string.setting_provider_proxy_clear))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val host = proxyHost
                        if (host != null) {
                            vm.updateSettings { latest ->
                                latest.copy(
                                    providerProxies = if (proxyInput.isBlank()) {
                                        latest.providerProxies - host
                                    } else {
                                        latest.providerProxies + (host to proxyInput.trim())
                                    },
                                    providerProxyUsernames = if (proxyInput.isBlank() || proxyUsername.isBlank()) {
                                        latest.providerProxyUsernames - host
                                    } else {
                                        latest.providerProxyUsernames + (host to proxyUsername.trim())
                                    },
                                    providerProxyPasswords = if (proxyInput.isBlank() || proxyPassword.isBlank()) {
                                        latest.providerProxyPasswords - host
                                    } else {
                                        latest.providerProxyPasswords + (host to proxyPassword)
                                    },
                                )
                            }
                        }
                        showProxyDialog = false
                    },
                ) {
                    Text(stringResource(R.string.assistant_page_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showProxyDialog = false }) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .floatingToolbarVerticalNestedScroll(
                    expanded = expanded,
                    onExpand = { expanded = true },
                    onCollapse = { expanded = false },
                )
                .verticalScroll(rememberScrollState())
                // 底部留出悬浮工具栏的位置；横向内距交给 Salt 的 RoundedColumn（自带 16dp）
                .padding(top = 16.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProviderConfigure(
                provider = internalProvider,
                allowTypeSwitch = true,
                onEdit = {
                    internalProvider = it
                },
            )

            RoundedColumn {
                Item(
                    onClick = { showGroupDialog = true },
                    text = stringResource(R.string.setting_provider_group_title),
                    sub = group ?: stringResource(R.string.setting_provider_group_none),
                )
                Item(
                    onClick = {
                        proxyInput = proxyUrl.orEmpty()
                        proxyUsername = proxyHost?.let { settings.providerProxyUsernames[it] }.orEmpty()
                        proxyPassword = proxyHost?.let { settings.providerProxyPasswords[it] }.orEmpty()
                        showProxyDialog = true
                    },
                    text = stringResource(R.string.setting_provider_proxy_title),
                    sub = proxyUrl ?: stringResource(R.string.setting_provider_proxy_desc_short),
                )
            }

            // 账号类的额度查询写死在 Provider 里，没有这一项
            (internalProvider as? ProviderSetting.ApiKey)?.let { apiKeyProvider ->
                SettingProviderQuotaQuery(
                    provider = apiKeyProvider,
                    // 额度按已保存的配置查，没开就不留这一行
                    supportingContent = if (provider.supportsQuota) {
                        {
                            ProviderQuotaText(
                                providerSetting = provider,
                                maxLines = 2,
                                refreshIntervalMillis = provider.guard.quotaRefreshSeconds * 1_000L,
                            )
                        }
                    } else null,
                    onEdit = { internalProvider = apiKeyProvider.withQuotaQuery(it) }
                )
            }

            // 硅基流动图标
            if (provider is ProviderSetting.OpenAI && provider.baseUrl.contains("siliconflow.cn")) {
                SiliconFlowPowerByIcon(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 16.dp)
                )
            }
        }

        // 键盘弹出时收起，不然会盖住正在输入的那一行
        AnimatedVisibility(
            visible = !WindowInsets.isImeVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -ScreenOffset),
            enter = fadeIn(rememberEffectsSpec<Float>()) + scaleIn(initialScale = 0.8f),
            exit = fadeOut(rememberEffectsSpec<Float>()) + scaleOut(targetScale = 0.8f),
        ) {
            HorizontalFloatingToolbar(
                expanded = expanded,
                colors = CustomColors.floatingToolbarColors,
                leadingContent = {
                    IconButton(onClick = { showTestDialog = true }) {
                        Icon(HugeIcons.Connect, null)
                    }

                    IconButton(
                        onClick = {
                            showDeleteDialog = true
                        },
                    ) {
                        Icon(HugeIcons.Delete01, stringResource(R.string.delete))
                    }
                },
            ) {
                Button(
                    onClick = {
                        val providerToSave: ProviderSetting = internalProvider
                        onEdit(providerToSave.copyProvider(name = providerToSave.name.trim()))
                    }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            HugeIcons.FloppyDisk,
                            contentDescription = stringResource(R.string.setting_provider_page_save)
                        )
                        MotionExpandCollapseHorizontal(visible = expanded) {
                            Row {
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(stringResource(R.string.setting_provider_page_save))
                            }
                        }
                    }
                }
            }
        }
    }

    // 工具栏在键盘弹出时会被移出 composition，dialog 不能挂在它下面
    if (showTestDialog) {
        ProviderConnectionTestDialog(
            internalProvider = internalProvider,
            onDismissRequest = { showTestDialog = false },
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AnimatedAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(stringResource(R.string.confirm_delete))
            },
            text = {
                Text(stringResource(R.string.setting_provider_page_delete_dialog_text))
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            }
        )
    }
}

@Composable
private fun SettingProviderModelPage(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit,
) {
    ModelList(
        providerSetting = provider,
        onUpdateProvider = onEdit,
    )
}

@Composable
private fun SettingProviderAdvancedPage(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CustomHeaders(
            headers = provider.customHeaders,
            onUpdate = { headers ->
                onEdit(provider.copyProvider(customHeaders = headers))
            }
        )

        ProviderGuardSettings(
            provider = provider,
            onEdit = onEdit,
        )
    }
}

@Composable
private fun ModelList(
    providerSetting: ProviderSetting,
    onUpdateProvider: (ProviderSetting) -> Unit,
) {
    val providerManager = koinInject<ProviderManager>()
    // 增删、排序已添加的模型不影响远端列表，去掉 models 再作为 key，避免每次编辑都重新请求
    val listKey = remember(providerSetting) { providerSetting.copyProvider(models = emptyList()) }
    var refreshCount by remember { mutableIntStateOf(0) }
    val remoteModels by produceState(RemoteModels(loading = true), listKey, refreshCount) {
        value = value.copy(loading = true, error = null)
        value = try {
            val provider = providerManager.getProviderByType(listKey)
            val apiKeySetting = listKey as? ProviderSetting.ApiKey
            val enabledKeys = apiKeySetting?.let { setting ->
                setting.apiKey.split(Regex("[\\n,]"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it !in setting.disabledApiKeys }
            }.orEmpty()
            val models = if (apiKeySetting != null && enabledKeys.size > 1) {
                // 每个 Key 可能开通了不同的模型，合并去重后再展示；
                // 只有全都失败才算读不到，否则错误会被空列表吃掉
                val errors = mutableListOf<Exception>()
                val merged = enabledKeys.flatMap { key ->
                    try {
                        provider.listModels(apiKeySetting.withApiKey(key))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errors += e
                        emptyList()
                    }
                }.distinctBy { it.modelId }
                if (merged.isEmpty() && errors.isNotEmpty()) throw errors.first()
                merged
            } else {
                provider.listModels(listKey)
            }
            RemoteModels(models = models.sortedBy { it.modelId })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            // 失败时保留上次拿到的列表
            value.copy(loading = false, error = e)
        }
    }
    val navController = LocalNavController.current
    var expanded by rememberSaveable { mutableStateOf(true) }
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onUpdateProvider(providerSetting.moveMove(from.index, to.index))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (providerSetting.models.isEmpty()) {
            ExtensionEmptyState(
                message = stringResource(R.string.setting_provider_page_no_models),
                description = stringResource(R.string.setting_provider_page_add_models_hint),
                icon = HugeIcons.Package01,
            )
        } else {
            LazyColumn(
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
                items(providerSetting.models, key = { it.id }) { item ->
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
                    remoteModels = remoteModels,
                    onRefresh = { refreshCount++ },
                    selectedModels = providerSetting.models,
                    onAddModel = {
                        onUpdateProvider(providerSetting.addModel(it))
                    },
                    onRemoveModel = {
                        onUpdateProvider(providerSetting.delModel(it))
                    },
                    expanded = expanded,
                    parentProvider = providerSetting,
                    onUpdateProvider = onUpdateProvider,
                    onOpenEditor = {
                        navController.navigate(
                            Screen.ModelEditor(providerId = providerSetting.id.toString())
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

@Composable
private fun AddModelButton(
    remoteModels: RemoteModels,
    onRefresh: () -> Unit,
    selectedModels: List<Model>,
    expanded: Boolean,
    onAddModel: (Model) -> Unit,
    onRemoveModel: (Model) -> Unit,
    parentProvider: ProviderSetting,
    onUpdateProvider: (ProviderSetting) -> Unit,
    onOpenEditor: () -> Unit,
) {

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModelPicker(
            remoteModels = remoteModels,
            onRefresh = onRefresh,
            selectedModels = selectedModels,
            onModelSelected = { model ->
                val inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(model.modelId)
                val outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(model.modelId)
                val abilities = ModelRegistry.MODEL_ABILITIES.getData(model.modelId)
                onAddModel(
                    model.copy(
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
                onUpdateProvider(
                    parentProvider.copyProvider(
                        models = parentProvider.models + it.filter { model ->
                            parentProvider.models.none { existing -> existing.modelId == model.modelId }
                        }.map { model ->
                            model.copy(
                                inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(model.modelId),
                                outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(model.modelId),
                                abilities = ModelRegistry.MODEL_ABILITIES.getData(model.modelId)
                            )
                        }
                    )
                )
            },
            onAllModelDeselected = { filteredModels ->
                onUpdateProvider(
                    parentProvider.copyProvider(
                        models = parentProvider.models.filter { model ->
                            filteredModels.none { filtered -> filtered.modelId == model.modelId }
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
    onRefresh: () -> Unit,
    selectedModels: List<Model>,
    onModelSelected: (Model) -> Unit,
    onModelDeselected: (Model) -> Unit,
    onAllModelSelected: (List<Model>) -> Unit,
    onAllModelDeselected: (List<Model>) -> Unit
) {
    var showModal by remember { mutableStateOf(false) }
    SaltModalSheet(
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

private fun ProviderSetting.proxyHost(): String? = when (this) {
    is ProviderSetting.OpenAI -> baseUrl.hostOf()
    is ProviderSetting.Google -> baseUrl.hostOf()
    is ProviderSetting.Claude -> baseUrl.hostOf()
    is ProviderSetting.ChatGPT -> "chatgpt.com"
    is ProviderSetting.Grok -> "api.x.ai"
    else -> null
}

private fun String.hostOf(): String? = runCatching { java.net.URI(this).host }
    .getOrNull()
    ?.takeIf { it.isNotBlank() }
