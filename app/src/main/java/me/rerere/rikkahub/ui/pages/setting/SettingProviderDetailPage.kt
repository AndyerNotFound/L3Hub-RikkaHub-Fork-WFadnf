package me.rerere.rikkahub.ui.pages.setting
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.supportsQuota
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Connect
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.FloppyDisk
import me.rerere.hugeicons.stroke.Package01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Share01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.account.preserveCredential
import me.rerere.rikkahub.data.datastore.removeProvider
import me.rerere.rikkahub.ui.components.ai.ProviderQuotaText
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.PagerTab
import me.rerere.rikkahub.ui.components.ui.PagerTabs
import me.rerere.rikkahub.ui.components.ui.ShareSheet
import me.rerere.rikkahub.ui.components.ui.SiliconFlowPowerByIcon
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
import me.rerere.rikkahub.ui.theme.rememberEffectsSpec
import me.rerere.ui.components.AnimatedAlertDialog
import me.rerere.ui.toast.ToastType
import org.koin.androidx.compose.koinViewModel

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
                // 底部留出悬浮工具栏的位置；横向内距 16dp
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
                onCommit = { committed ->
                    internalProvider = committed
                    onEdit(committed.copyProvider(name = committed.name.trim()))
                },
            )

            CardGroup {
                item(
                    onClick = { showGroupDialog = true },
                    headlineContent = { Text(stringResource(R.string.setting_provider_group_title)) },
                    supportingContent = { Text(group ?: stringResource(R.string.setting_provider_group_none)) },
                )
                item(
                    onClick = {
                        proxyInput = proxyUrl.orEmpty()
                        proxyUsername = proxyHost?.let { settings.providerProxyUsernames[it] }.orEmpty()
                        proxyPassword = proxyHost?.let { settings.providerProxyPasswords[it] }.orEmpty()
                        showProxyDialog = true
                    },
                    headlineContent = { Text(stringResource(R.string.setting_provider_proxy_title)) },
                    supportingContent = { Text(proxyUrl ?: stringResource(R.string.setting_provider_proxy_desc_short)) },
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

private fun ProviderSetting.proxyHost(): String? = when (this) {
    is ProviderSetting.OpenAI -> baseUrl.hostOf()
    is ProviderSetting.Google -> baseUrl.hostOf()
    is ProviderSetting.Claude -> baseUrl.hostOf()
    is ProviderSetting.ChatGPT -> "chatgpt.com"
    is ProviderSetting.Grok -> "api.x.ai"
    is ProviderSetting.DeepSeekAccount -> "api.deepseek.com"
    else -> null
}

private fun String.hostOf(): String? = runCatching { java.net.URI(this).host }
    .getOrNull()
    ?.takeIf { it.isNotBlank() }
