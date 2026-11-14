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
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.filterByKeywords
import me.rerere.ai.provider.supportsQuota
import me.rerere.ai.registry.ModelRegistry
import me.rerere.ai.ui.UIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Connect
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.FloppyDisk
import me.rerere.hugeicons.stroke.MoneyBag02
import me.rerere.hugeicons.stroke.Package01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.ScanText
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Share01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.account.preserveCredential
import me.rerere.rikkahub.data.datastore.removeProvider
import me.rerere.rikkahub.data.model.ContextThresholdPresets
import me.rerere.rikkahub.data.model.withResolvedContextLength
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.ai.ModelAbilityTag
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.ModelModalityTag
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.ai.ModelTypeTag
import me.rerere.rikkahub.ui.components.ai.ProviderQuotaText
import me.rerere.rikkahub.ui.components.ai.modelSettingItem
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.ConnectedMultiChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.components.ui.PagerTab
import me.rerere.rikkahub.ui.components.ui.PagerTabs
import me.rerere.rikkahub.ui.components.ui.ShareSheet
import me.rerere.rikkahub.ui.components.ui.SiliconFlowPowerByIcon
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.cardGroupItemShape
import me.rerere.rikkahub.ui.components.ui.localModelIconAsset
import me.rerere.rikkahub.ui.components.ui.longPressReorder
import me.rerere.rikkahub.ui.components.ui.rememberShareSheetState
import me.rerere.rikkahub.ui.components.ui.shadow.Button
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.assistant.detail.CustomBodies
import me.rerere.ui.toast.ToastType
import me.rerere.rikkahub.ui.pages.assistant.detail.CustomHeaders
import me.rerere.rikkahub.ui.pages.setting.components.PriceRuleSettings
import me.rerere.rikkahub.ui.pages.setting.components.PricingInput
import me.rerere.rikkahub.ui.pages.setting.components.ProviderConfigure
import me.rerere.rikkahub.ui.pages.setting.components.ProviderConnectionTester
import me.rerere.rikkahub.ui.pages.setting.components.ProviderGuardSettings
import me.rerere.rikkahub.ui.pages.setting.components.SettingProviderQuotaQuery
import me.rerere.rikkahub.ui.terminal.Card
import me.rerere.rikkahub.ui.terminal.IconButton
import me.rerere.rikkahub.ui.terminal.RadioButton
import me.rerere.rikkahub.ui.terminal.Switch
import me.rerere.rikkahub.ui.terminal.TextButton
import me.rerere.rikkahub.ui.terminal.TopAppBar
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.extendColors
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.rikkahub.utils.plus
import me.rerere.ui.components.AnimatedAlertDialog
import me.rerere.ui.components.RikkaConfirmDialog
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.hooks.heroSource

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

private enum class ModelFormTab(
    override val icon: ImageVector,
    @param:StringRes private val labelRes: Int,
) : PagerTab {
    BASIC(HugeIcons.Package01, R.string.setting_provider_page_basic_settings),
    ADVANCED(HugeIcons.Settings03, R.string.setting_provider_page_advanced_settings),
    BUILT_IN_TOOLS(HugeIcons.Tools, R.string.setting_page_built_in_tools),
    OTHER(HugeIcons.MoneyBag02, R.string.setting_provider_page_other_settings);

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
                            iconAsset = provider.localModelIconAsset(),
                        )
                        Text(text = provider.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    // 账号型提供商离开账号就没法用；LiteRT 的模型路径只在本机有效
                    if (provider !is ProviderSetting.Account && provider !is ProviderSetting.LiteRT) {
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
                // 底部留出悬浮工具栏的位置
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProviderConfigure(
                provider = internalProvider,
                allowTypeSwitch = true,
                onEdit = {
                    internalProvider = it
                },
                // 模型的导入、选择、删除会直接动文件，设置不能只停在草稿里
                onCommit = {
                    internalProvider = it
                    onEdit(it)
                },
            )

            CardGroup {
                item(
                    onClick = { showGroupDialog = true },
                    headlineContent = { Text(stringResource(R.string.setting_provider_group_title)) },
                    supportingContent = {
                        Text(group ?: stringResource(R.string.setting_provider_group_none))
                    },
                )
                item(
                    onClick = {
                        proxyInput = proxyUrl.orEmpty()
                        proxyUsername = proxyHost?.let { settings.providerProxyUsernames[it] }.orEmpty()
                        proxyPassword = proxyHost?.let { settings.providerProxyPasswords[it] }.orEmpty()
                        showProxyDialog = true
                    },
                    headlineContent = { Text(stringResource(R.string.setting_provider_proxy_title)) },
                    supportingContent = {
                        Text(proxyUrl ?: stringResource(R.string.setting_provider_proxy_desc_short))
                    },
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
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
        ) {
            HorizontalFloatingToolbar(
                expanded = expanded,
                colors = CustomColors.floatingToolbarColors,
                leadingContent = {
                    ProviderConnectionTester(
                        internalProvider = internalProvider,
                    )

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
                        AnimatedVisibility(expanded) {
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
                            // 点开时这张卡片展开成整页
                            modifier = longPressReorder(isDragging).heroSource(editorScreen),
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
                .offset(y = -ScreenOffset),
        ) {
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

@Composable
private fun ModelSettingsForm(
    model: Model,
    onModelChange: (Model) -> Unit,
    isEdit: Boolean,
    parentProvider: ProviderSetting? = null,
    modifier: Modifier = Modifier,
) {
    // 横屏宽屏：标签 / 基础字段 / 其余选项分三列，一屏看全，不用来回翻页
    val windowDpSize = currentWindowDpSize()
    val wide = windowDpSize.width > windowDpSize.height && windowDpSize.width >= 700.dp
    if (wide) {
        ModelSettingsWideForm(model, onModelChange, isEdit, parentProvider, modifier)
    } else {
        ModelSettingsPagerForm(model, onModelChange, isEdit, parentProvider, modifier)
    }
}

/** 窄屏：原来的标签 + 翻页 */
@Composable
private fun ModelSettingsPagerForm(
    model: Model,
    onModelChange: (Model) -> Unit,
    isEdit: Boolean,
    parentProvider: ProviderSetting?,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState { ModelFormTab.entries.size }

    Column(modifier = modifier) {
        PagerTabs(
            pagerState = pagerState,
            tabs = ModelFormTab.entries,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            when (ModelFormTab.entries[page]) {
                ModelFormTab.BASIC -> {
                    // 基本设置页面
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        ModelIdentityFields(
                            model = model,
                            onModelChange = onModelChange,
                            isEdit = isEdit,
                            allowOcr = parentProvider.supportsOcrModels(),
                        )
                        ModelBasicOptions(model, onModelChange)
                    }
                }

                ModelFormTab.ADVANCED -> {
                    // 高级设置页面
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ModelAdvancedOptions(model, onModelChange, parentProvider)
                    }
                }

                ModelFormTab.OTHER -> {
                    // 其它设置页面
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ModelOtherOptions(model, onModelChange)
                    }
                }

                ModelFormTab.BUILT_IN_TOOLS -> {
                    // 内置工具页面
                    BuiltInToolsSettings(
                        tools = model.tools,
                        onUpdateTools = { tools ->
                            onModelChange(model.copy(tools = tools))
                        }
                    )
                }
            }
        }
    }
}

/**
 * 宽屏：最左是标签，中列是模型 ID / 名称 / 类型，右列是当前标签的其余选项。
 *
 * 中列常驻：切标签时只有右列换内容，改完 ID 再去看高级设置不用滚回去找字段。
 */
@Composable
private fun ModelSettingsWideForm(
    model: Model,
    onModelChange: (Model) -> Unit,
    isEdit: Boolean,
    parentProvider: ProviderSetting?,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(ModelFormTab.BASIC) }

    Row(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(176.dp)
                .fillMaxHeight()
                .padding(top = 12.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ModelFormTab.entries.forEach { tab ->
                ModelFormTabItem(
                    tab = tab,
                    selected = tab == selectedTab,
                    onClick = { selectedTab = tab },
                )
            }
        }

        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ModelIdentityFields(
                model = model,
                onModelChange = onModelChange,
                isEdit = isEdit,
                allowOcr = parentProvider.supportsOcrModels(),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            when (selectedTab) {
                ModelFormTab.BASIC -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ModelBasicOptions(model, onModelChange)
                }

                ModelFormTab.ADVANCED -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ModelAdvancedOptions(model, onModelChange, parentProvider)
                }

                ModelFormTab.OTHER -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ModelOtherOptions(model, onModelChange)
                }

                ModelFormTab.BUILT_IN_TOOLS -> BuiltInToolsSettings(
                    tools = model.tools,
                    onUpdateTools = { tools ->
                        onModelChange(model.copy(tools = tools))
                    }
                )
            }
        }
    }
}

@Composable
private fun ModelFormTabItem(
    tab: ModelFormTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(text = tab.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** 模型身份：ID、名称、类型。窄屏在基础页顶部，宽屏固定在中列 */
@Composable
private fun ModelIdentityFields(
    model: Model,
    onModelChange: (Model) -> Unit,
    isEdit: Boolean,
    allowOcr: Boolean = true,
) {
    fun setModelId(id: String) {
        val inputModality = ModelRegistry.MODEL_INPUT_MODALITIES.getData(id)
        val outputModality = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(id)
        val abilities = ModelRegistry.MODEL_ABILITIES.getData(id)
        onModelChange(
            model.copy(
                modelId = id,
                displayName = id,
                inputModalities = inputModality,
                outputModalities = outputModality,
                abilities = abilities
            )
        )
    }

    OutlinedTextField(
        value = model.modelId,
        onValueChange = {
            if (isEdit) {
                onModelChange(model.copy(modelId = it.trim()))
            } else {
                setModelId(it.trim())
            }
        },
        label = { Text(stringResource(R.string.setting_provider_page_model_id)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            if (!isEdit) {
                Text(stringResource(R.string.setting_provider_page_model_id_placeholder))
            }
        },
    )

    OutlinedTextField(
        value = model.displayName,
        onValueChange = {
            onModelChange(model.copy(displayName = it))
        },
        label = { Text(stringResource(if (isEdit) R.string.setting_provider_page_model_name else R.string.setting_provider_page_model_display_name)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            if (!isEdit) {
                Text(stringResource(R.string.setting_provider_page_model_display_name_placeholder))
            }
        }
    )

    ModelTypeSelector(
        selectedType = model.type,
        allowOcr = allowOcr,
        onTypeSelected = {
            onModelChange(model.copy(type = it))
        }
    )
}

/** 基础设置里除身份以外的部分：模态、能力、OCR 辅助模型、上下文门限 */
@Composable
private fun ModelBasicOptions(
    model: Model,
    onModelChange: (Model) -> Unit,
) {
    ModelModalitySelector(
        model = model,
        inputModalities = model.inputModalities,
        onUpdateInputModalities = {
            onModelChange(model.copy(inputModalities = it))
        },
        outputModalities = model.outputModalities,
        onUpdateOutputModalities = {
            onModelChange(model.copy(outputModalities = it))
        }
    )

    if (model.type == ModelType.CHAT) {
        ModalAbilitySelector(
            abilities = model.abilities,
            onUpdateAbilities = {
                onModelChange(model.copy(abilities = it))
            }
        )

        ModelOcrHelperSetting(
            model = model,
            onModelChange = onModelChange,
        )

        ModelContextThresholdFields(
            model = model,
            onModelChange = onModelChange,
        )
    }
}

/**
 * 这个模型单独的 OCR 辅助模型：模型自己看不了图时，把图片交给它识别。
 * 不设置时跟着「默认模型」页的全局 OCR 模型走，所以这一项要能清掉。
 */
@Composable
private fun ModelOcrHelperSetting(
    model: Model,
    onModelChange: (Model) -> Unit,
) {
    val state = rememberModelListState(
        modelId = model.ocrModelId,
        providers = LocalSettings.current.providers,
        type = ModelType.CHAT,
        types = setOf(ModelType.CHAT, ModelType.OCR),
    )
    val containerColor = MaterialTheme.colorScheme.secondaryContainer
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    // 只有选了看不懂图的模型才需要提醒，没配过是正常情况
    val warning = state.currentModel
        ?.takeIf { it.type == ModelType.CHAT }
        ?.takeUnless { it.inputModalities.contains(Modality.IMAGE) }
        ?.let { R.string.setting_model_page_ocr_model_missing }

    CardGroup {
        modelSettingItem(
            icon = HugeIcons.ScanText,
            title = R.string.setting_provider_page_ocr_helper_model,
            description = R.string.setting_provider_page_ocr_helper_model_desc,
            state = state,
            iconContainerColor = containerColor,
            iconContentColor = contentColor,
            placeholder = R.string.setting_provider_page_ocr_helper_model_follow_global,
            onClear = { onModelChange(model.copy(ocrModelId = null)) },
            clearContentDescription = R.string.setting_provider_page_ocr_helper_model_follow_global,
            warning = warning,
        )
    }
    ModelListSheet(state = state) {
        onModelChange(model.copy(ocrModelId = it.id))
    }
}

/** 高级设置：覆盖配置、自定义请求头和请求体 */
@Composable
private fun ModelAdvancedOptions(
    model: Model,
    onModelChange: (Model) -> Unit,
    parentProvider: ProviderSetting?,
) {
    // 覆盖配置是提供商的独立副本，账号凭据不能复制出去，账号型提供商不支持覆盖
    if (parentProvider !is ProviderSetting.Account) {
        ProviderOverrideSettings(
            providerOverride = model.providerOverwrite,
            onUpdateProviderOverride = { providerOverride ->
                onModelChange(model.copy(providerOverwrite = providerOverride))
            },
            parentProvider = parentProvider
        )
    }

    CustomHeaders(
        headers = model.customHeaders,
        onUpdate = { headers ->
            onModelChange(model.copy(customHeaders = headers))
        }
    )

    CustomBodies(
        customBodies = model.customBodies,
        onUpdate = { bodies ->
            onModelChange(model.copy(customBodies = bodies))
        }
    )
}

/** 其它设置：模型计费。价格单位跟随「以二进制量计数」，十进制按 1M、二进制按 Mi */
@Composable
private fun ModelOtherOptions(
    model: Model,
    onModelChange: (Model) -> Unit,
) {
    val binaryUnits = LocalSettings.current.displaySetting.binaryUnits
    val unit = if (binaryUnits) "Mi" else "1M"
    val perCallUnit = stringResource(R.string.setting_provider_page_price_per_call_unit)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.setting_provider_page_pricing),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.setting_provider_page_pricing_desc, unit),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CardGroup {
            PricingItem(
                label = R.string.setting_provider_page_price_input,
                value = model.pricing.input,
                modelKey = model.id,
                unit = unit,
                onValueChange = { onModelChange(model.copy(pricing = model.pricing.copy(input = it))) },
            )
            PricingItem(
                label = R.string.setting_provider_page_price_output,
                value = model.pricing.output,
                modelKey = model.id,
                unit = unit,
                onValueChange = { onModelChange(model.copy(pricing = model.pricing.copy(output = it))) },
            )
            PricingItem(
                label = R.string.setting_provider_page_price_cache_hit,
                value = model.pricing.cacheHit,
                modelKey = model.id,
                unit = unit,
                onValueChange = { onModelChange(model.copy(pricing = model.pricing.copy(cacheHit = it))) },
            )
            PricingItem(
                label = R.string.setting_provider_page_price_cache_write,
                value = model.pricing.cacheWrite,
                modelKey = model.id,
                unit = unit,
                onValueChange = { onModelChange(model.copy(pricing = model.pricing.copy(cacheWrite = it))) },
            )
            PricingItem(
                label = R.string.setting_provider_page_price_cache_read,
                value = model.pricing.cacheRead,
                modelKey = model.id,
                unit = unit,
                onValueChange = { onModelChange(model.copy(pricing = model.pricing.copy(cacheRead = it))) },
            )
            PricingItem(
                label = R.string.setting_provider_page_price_per_call,
                value = model.pricing.perCall,
                modelKey = model.id,
                unit = perCallUnit,
                onValueChange = { onModelChange(model.copy(pricing = model.pricing.copy(perCall = it))) },
            )
        }
        Text(
            text = stringResource(R.string.setting_provider_page_price_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PriceRuleSettings(
            modelKey = model.id,
            rules = model.pricing.rules,
            basePrice = model.pricing.priceTable(),
            unit = unit,
            perCallUnit = perCallUnit,
            onRulesChange = { rules ->
                onModelChange(model.copy(pricing = model.pricing.copy(rules = rules)))
            },
        )
    }
}

private fun CardGroupScope.PricingItem(
    @StringRes label: Int,
    value: Double,
    unit: String,
    modelKey: Any,
    onValueChange: (Double) -> Unit,
) {
    item(
        headlineContent = { Text(stringResource(label)) },
        trailingContent = {
            PricingInput(value = value, unit = unit, modelKey = modelKey, onValueChange = onValueChange)
        },
    )
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
                AnimatedVisibility(expanded) {
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
    if (showModal) {
        ModalBottomSheet(
            onDismissRequest = { showModal = false },
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
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
                                            shape = cardGroupItemShape(index, filteredModels.size),
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

@Composable
private fun ModelTypeSelector(
    selectedType: ModelType,
    onTypeSelected: (ModelType) -> Unit,
    allowOcr: Boolean = true,
) {
    Text(
        stringResource(R.string.setting_provider_page_model_type),
        style = MaterialTheme.typography.titleSmall
    )
    ConnectedChoiceButtons(
        options = ModelType.entries.filter { allowOcr || it != ModelType.OCR },
        selected = selectedType,
        onSelect = onTypeSelected,
    ) { type, selected ->
        ConnectedChoiceLabel(
            text = stringResource(
                when (type) {
                    ModelType.CHAT -> R.string.setting_provider_page_chat_model
                    ModelType.EMBEDDING -> R.string.setting_provider_page_embedding_model
                    ModelType.IMAGE -> R.string.setting_provider_page_image_model
                    ModelType.OCR -> R.string.setting_provider_page_ocr_model
                }
            ),
            selected = selected,
        )
    }
}

@Composable
private fun ModelModalitySelector(
    model: Model,
    inputModalities: List<Modality>,
    onUpdateInputModalities: (List<Modality>) -> Unit,
    outputModalities: List<Modality>,
    onUpdateOutputModalities: (List<Modality>) -> Unit
) {
    if (model.type == ModelType.CHAT) {
        Text(
            stringResource(R.string.setting_provider_page_input_modality),
            style = MaterialTheme.typography.titleSmall
        )
        ConnectedMultiChoiceButtons(
            options = Modality.entries,
            selected = inputModalities,
            onToggle = { modality, checked ->
                onUpdateInputModalities(if (checked) inputModalities + modality else inputModalities - modality)
            },
        ) { modality, selected ->
            ConnectedChoiceLabel(
                text = stringResource(
                    when (modality) {
                        Modality.TEXT -> R.string.setting_provider_page_text
                        Modality.IMAGE -> R.string.setting_provider_page_image
                    }
                ),
                selected = selected,
                icon = HugeIcons.Tick01,
            )
        }

        Text(
            stringResource(R.string.setting_provider_page_output_modality),
            style = MaterialTheme.typography.titleSmall
        )
        ConnectedMultiChoiceButtons(
            options = Modality.entries,
            selected = outputModalities,
            onToggle = { modality, checked ->
                onUpdateOutputModalities(if (checked) outputModalities + modality else outputModalities - modality)
            },
        ) { modality, selected ->
            ConnectedChoiceLabel(
                text = stringResource(
                    when (modality) {
                        Modality.TEXT -> R.string.setting_provider_page_text
                        Modality.IMAGE -> R.string.setting_provider_page_image
                    }
                ),
                selected = selected,
                icon = HugeIcons.Tick01,
            )
        }
    }
}

@Composable
fun ModalAbilitySelector(
    abilities: List<ModelAbility>,
    onUpdateAbilities: (List<ModelAbility>) -> Unit
) {
    Text(
        stringResource(R.string.setting_provider_page_abilities),
        style = MaterialTheme.typography.titleSmall
    )
    ConnectedMultiChoiceButtons(
        options = ModelAbility.entries,
        selected = abilities,
        onToggle = { ability, checked ->
            onUpdateAbilities(if (checked) abilities + ability else abilities - ability)
        },
    ) { ability, selected ->
        ConnectedChoiceLabel(
            text = stringResource(
                when (ability) {
                    ModelAbility.TOOL -> R.string.setting_provider_page_tool
                    ModelAbility.REASONING -> R.string.setting_provider_page_reasoning
                }
            ),
            selected = selected,
            icon = HugeIcons.Tick01,
        )
    }
}

@Composable
private fun ModelContextThresholdFields(
    model: Model,
    onModelChange: (Model) -> Unit,
) {
    val warn = model.warnContextTokens
    val max = model.maxContextTokens
    val invalid = warn != null && max != null && warn >= max

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.setting_provider_page_context_thresholds),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (!model.contextThresholdsAuto) {
                TextButton(
                    onClick = {
                        val resolved = model.withResolvedContextLength()
                        val preset = ContextThresholdPresets.forModel(resolved)
                        onModelChange(
                            model.copy(
                                contextLength = resolved.contextLength,
                                warnContextTokens = preset.warn,
                                maxContextTokens = preset.max,
                                contextThresholdsAuto = true,
                            )
                        )
                    },
                ) {
                    Text(stringResource(R.string.setting_provider_page_context_threshold_use_preset))
                }
            }
        }
        ContextThresholdSlider(
            label = stringResource(R.string.setting_provider_page_context_warn),
            value = warn,
            isError = invalid,
            onValueChange = { value ->
                onModelChange(model.copy(warnContextTokens = value, contextThresholdsAuto = false))
            },
        )
        ContextThresholdSlider(
            label = stringResource(R.string.setting_provider_page_context_max),
            value = max,
            isError = invalid,
            onValueChange = { value ->
                onModelChange(model.copy(maxContextTokens = value, contextThresholdsAuto = false))
            },
        )
        Text(
            text = if (invalid) {
                stringResource(R.string.setting_provider_page_context_threshold_invalid)
            } else {
                stringResource(R.string.setting_provider_page_context_threshold_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (invalid) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.setting_provider_page_context_block),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.setting_provider_page_context_block_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = model.enforceMaxContext,
                onCheckedChange = { onModelChange(model.copy(enforceMaxContext = it)) },
            )
        }
    }
}

/**
 * 单个门限的调整：滑杆按 [ContextThresholdPresets.stops] 的档位粗调，
 * 右边的输入框填任意精确值，两边改的是同一个字段。
 *
 * 输入框自己保存草稿：清空表示不设门限，非数字直接忽略；滑杆拖动时草稿跟着跳到对应档位。
 */
@Composable
private fun ContextThresholdSlider(
    label: String,
    value: Int?,
    isError: Boolean,
    onValueChange: (Int?) -> Unit,
) {
    val stops = ContextThresholdPresets.stops
    val binaryUnits = LocalSettings.current.displaySetting.binaryUnits
    var text by remember(value) { mutableStateOf(value?.toString() ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(8)
                    text = digits
                    onValueChange(digits.toIntOrNull()?.takeIf { it > 0 })
                },
                modifier = Modifier.width(136.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                suffix = { Text("tokens", style = MaterialTheme.typography.labelSmall) },
                placeholder = { Text("-") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = isError,
            )
        }
        Slider(
            value = contextStopPosition(stops, value),
            onValueChange = { position ->
                onValueChange(stops[position.roundToInt().coerceIn(stops.indices)])
            },
            valueRange = 0f..stops.lastIndex.toFloat(),
            steps = stops.size - 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = contextThresholdSummary(stops.first(), binaryUnits),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = contextThresholdSummary(value, binaryUnits),
                style = MaterialTheme.typography.labelSmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Text(
                text = contextThresholdSummary(stops.last(), binaryUnits),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 滑杆位置：值落在两档之间就停在两者中间，超出范围贴住两端。
 * 输入框填的精确值不在档位上时只是近似位置，再拖一下才会落到档位。
 */
private fun contextStopPosition(stops: List<Int>, value: Int?): Float {
    if (value == null || value <= stops.first()) return 0f
    if (value >= stops.last()) return stops.lastIndex.toFloat()
    val upper = stops.indexOfFirst { it >= value }
    if (stops[upper] == value) return upper.toFloat()
    val lower = stops[upper - 1]
    return upper - 1 + (value - lower).toFloat() / (stops[upper] - lower)
}

/** 档位和当前值的显示：二进制口径整 KiB 写成 384Ki，其余按全局单位显示 */
private fun contextThresholdSummary(value: Int?, binary: Boolean): String = when {
    value == null || value <= 0 -> "-"
    binary && value % KIB == 0 -> "${value / KIB}Ki"
    else -> value.formatNumber(binary)
}

private const val KIB = 1024

@Composable
private fun ModelCard(
    model: Model,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onOpenEditor: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        onClick = onOpenEditor,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = CustomColors.listItemColors.containerColor
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                AutoAIIcon(
                    name = model.modelId,
                    modifier = Modifier.size(36.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (model.providerOverwrite != null) {
                        Tag(type = TagType.INFO) {
                            Text(
                                model.providerOverwrite?.javaClass?.simpleName ?: model.providerOverwrite?.name
                                ?: "ProviderOverwrite"
                            )
                        }
                    }
                    ModelTypeTag(model = model)
                    ModelModalityTag(model = model)
                    ModelAbilityTag(model = model)
                }
            }

            ItemActionMenu(
                actions = listOf(
                    ItemAction(
                        text = stringResource(R.string.delete),
                        icon = HugeIcons.Delete01,
                        destructive = true,
                        onClick = { showDeleteDialog = true },
                    ),
                )
            )
        }
    }

    RikkaConfirmDialog(
        show = showDeleteDialog,
        title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showDeleteDialog = false
            onDelete()
        },
        onDismiss = { showDeleteDialog = false },
    ) {
        Text(stringResource(R.string.common_delete_confirm_message, model.displayName))
    }
}

@Composable
private fun BuiltInToolsSettings(
    tools: Set<BuiltInTools>,
    onUpdateTools: (Set<BuiltInTools>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.setting_page_built_in_tools),
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = stringResource(R.string.setting_page_built_in_tools_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val availableTools = listOf(
            BuiltInTools.Search to Pair(
                stringResource(R.string.setting_page_built_in_tools_search),
                stringResource(R.string.setting_page_built_in_tools_search_desc)
            ),
            BuiltInTools.UrlContext to Pair(
                stringResource(R.string.setting_page_built_in_tools_url_context),
                stringResource(R.string.setting_page_built_in_tools_url_context_desc)
            ),
            BuiltInTools.ImageGeneration to Pair(
                stringResource(R.string.setting_page_built_in_tools_image_generation),
                stringResource(R.string.setting_page_built_in_tools_image_generation_desc)
            )
        )

        // 弹窗底色和 CardGroup 默认的底色太接近，换一个深一档的
        val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        CardGroup {
            availableTools.fastForEach { (tool, info) ->
                val (title, description) = info
                val checked = tool in tools
                val toggle = { enabled: Boolean ->
                    onUpdateTools(if (enabled) tools + tool else tools - tool)
                }
                item(
                    onClick = { toggle(!checked) },
                    colors = itemColors,
                    supportingContent = { Text(description) },
                    trailingContent = { Switch(checked = checked, onCheckedChange = toggle) },
                    headlineContent = { Text(title) },
                )
            }
        }
    }
}

@Composable
private fun ProviderOverrideSettings(
    providerOverride: ProviderSetting?,
    onUpdateProviderOverride: (ProviderSetting?) -> Unit,
    parentProvider: ProviderSetting?
) {
    var showProviderConfig by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<ProviderSetting?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.setting_provider_page_provider_override),
            style = MaterialTheme.typography.titleSmall
        )

        Text(
            text = stringResource(R.string.setting_provider_page_provider_override_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (providerOverride != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AutoAIIcon(
                            providerOverride.name,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "${providerOverride.name} (Override)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                editingProvider = providerOverride
                                showProviderConfig = true
                            }
                        ) {
                            Icon(HugeIcons.Tools, contentDescription = "Edit override")
                        }
                        IconButton(
                            onClick = {
                                onUpdateProviderOverride(null)
                            }
                        ) {
                            Icon(HugeIcons.Cancel01, contentDescription = "Remove override")
                        }
                    }
                }
            }
        } else {
            Button(
                onClick = {
                    editingProvider = parentProvider?.copyProvider(
                        id = Uuid.random(),
                        builtIn = false,
                        models = emptyList(), // 这里必须设置为空，不然会导致循环依赖JSON
                        description = {},
                    )
                    showProviderConfig = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(HugeIcons.Add01, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.setting_provider_page_add_provider_override))
            }
        }

        // Provider configuration modal
        if (showProviderConfig && editingProvider != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    showProviderConfig = false
                    editingProvider = null
                },
                sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
                // 和服务商详情页同一个底色，里面的分段表单才分得开
                containerColor = CustomColors.topBarColors.containerColor,
            ) {
                var internalProvider by remember(editingProvider) { mutableStateOf(editingProvider!!) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.9f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.setting_provider_page_configure_provider_override),
                        style = MaterialTheme.typography.titleLarge,
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProviderConfigure(
                            provider = internalProvider,
                            allowTypeSwitch = true,
                            onEdit = { internalProvider = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        TextButton(
                            onClick = {
                                showProviderConfig = false
                                editingProvider = null
                            },
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(
                            onClick = {
                                onUpdateProviderOverride(internalProvider.copyProvider(name = internalProvider.name.trim()))
                                showProviderConfig = false
                                editingProvider = null
                            },
                        ) {
                            Text(stringResource(R.string.setting_provider_page_save))
                        }
                    }
                }
            }
        }
    }
}

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

private fun ProviderSetting?.supportsOcrModels(): Boolean =
    this == null || this is ProviderSetting.OpenAI

/**
 * 模型编辑页：原来是 [ModelCard] 里的一个 ModalBottomSheet，改成独立页面后可以从卡片直接展开。
 * [modelId] 为 null 时是新建。
 */
@Composable
internal fun ModelEditorPage(
    providerId: Uuid,
    modelId: String? = null,
    vm: SettingVM = koinViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    // 不在这里 early return：提供商被删掉时也得留一个能按返回的壳，
    // 否则用户会卡在一张空白页上
    val provider = settings.providers.find { it.id == providerId }

    val existing = remember(provider, modelId) {
        modelId?.let { id -> provider?.models?.find { it.id.toString() == id } }
    }
    var draft by remember(existing) { mutableStateOf(existing?.copy() ?: Model()) }
    val isEdit = existing != null

    // 新建这条不幂等：addModel 是 models + model，不去重。转场那 300ms 里按钮还能点，
    // 连点两下会写进两条同 id 的模型，列表的 LazyColumn 拿重复 key 会直接崩
    var saving by remember { mutableStateOf(false) }
    fun save() {
        if (saving) return
        val target = provider ?: return
        saving = true
        val edited = draft.copy(displayName = draft.displayName.trim())
        vm.updateSettings { latest ->
            latest.copy(
                providers = latest.providers.map { item ->
                    when {
                        item.id != target.id -> item
                        isEdit -> item.editModel(edited)
                        else -> item.addModel(edited)
                    }
                }
            )
        }
        navController.popBackStack()
    }

    Scaffold(
        containerColor = CustomColors.topBarColors.containerColor,
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton() },
                colors = CustomColors.topBarColors,
                title = {
                    Text(
                        text = stringResource(
                            if (isEdit) {
                                R.string.setting_provider_page_edit_model
                            } else {
                                R.string.setting_provider_page_add_model
                            }
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    TextButton(
                        // 新建时 modelId 也必须有，否则会写进一条点不动的空条目
                        enabled = provider != null && draft.displayName.isNotBlank() &&
                            (isEdit || draft.modelId.isNotBlank()),
                        onClick = { save() },
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                },
            )
        },
    ) { innerPadding ->
        ModelSettingsForm(
            model = draft,
            onModelChange = { draft = it },
            isEdit = isEdit,
            parentProvider = provider,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(horizontal = 16.dp)
                .imePadding(),
        )
    }
}
