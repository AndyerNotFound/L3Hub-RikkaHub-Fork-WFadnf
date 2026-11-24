package me.rerere.rikkahub.ui.pages.setting
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.availableKeys
import me.rerere.ai.provider.supportsBuiltInTool
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ai.groupName
import me.rerere.rikkahub.ui.components.ai.keyGroupName
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.style.RadioButton
import me.rerere.rikkahub.ui.style.TextButton
import me.rerere.ui.components.AnimatedAlertDialog

// 模型与提供商绑定的设置块：Key 分组、账号分组、内置工具

/**
 * Key 分组：这条模型只用哪一把 Key 发请求。
 *
 * 只在提供商有多把 Key 时出现。换分组等于换一条模型：同名模型在别的分组里是另一条，
 * 互不影响。想让一条模型在多把 Key 之间轮询，用下面的覆盖配置。
 */
@Composable
internal fun ModelKeyGroupSetting(
    model: Model,
    onModelChange: (Model) -> Unit,
    parentProvider: ProviderSetting?,
) {
    val apiKeyProvider = parentProvider as? ProviderSetting.ApiKey ?: return
    val keys = remember(apiKeyProvider) { apiKeyProvider.availableKeys }
    if (keys.size <= 1) return
    var showDialog by remember { mutableStateOf(false) }
    val allKeysName = stringResource(R.string.model_key_group_all)
    val currentName = remember(model.apiKeys, keys, allKeysName) {
        model.apiKeys
            .filter { it in keys }
            .joinToString(" + ") { key -> apiKeyProvider.keyGroupName(key) }
            .ifEmpty { allKeysName }
    }

    CardGroup {
        item(
            onClick = { showDialog = true },
            headlineContent = { Text(stringResource(R.string.setting_provider_page_model_key_group)) },
            supportingContent = { Text(currentName) },
        )
    }

    if (showDialog) {
        AnimatedAlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.setting_provider_page_model_key_group)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onModelChange(model.copy(apiKeys = emptyList()))
                                showDialog = false
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = model.apiKeys.isEmpty(), onClick = null)
                        Text(text = allKeysName)
                    }
                    keys.forEach { key ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onModelChange(model.copy(apiKeys = listOf(key)))
                                    showDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = model.apiKeys == listOf(key), onClick = null)
                            Text(
                                text = apiKeyProvider.keyGroupName(key),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}

/**
 * 账号分组：这条模型只用哪个登录账号发请求。
 *
 * 只在提供商有多份登录时出现。绑定后那个账号被平台拒绝就冷却报错，不会偷偷换成别的账号，
 * 免得同一条模型在几份登录之间来回计费、用户看不出实际用的是谁。
 */
@Composable
internal fun ModelAccountGroupSetting(
    model: Model,
    onModelChange: (Model) -> Unit,
    parentProvider: ProviderSetting?,
) {
    val accountProvider = parentProvider as? ProviderSetting.DeepSeekAccount ?: return
    val accounts = remember(accountProvider) { accountProvider.accounts.filter { !it.disabled } }
    if (accounts.size <= 1) return
    var showDialog by remember { mutableStateOf(false) }
    val allAccountsName = stringResource(R.string.model_account_group_all)
    val currentName = remember(model.accountIds, accounts, allAccountsName) {
        accounts
            .filter { it.id in model.accountIds }
            .joinToString(" + ") { binding -> binding.groupName(accountProvider.name) }
            .ifEmpty { allAccountsName }
    }

    CardGroup {
        item(
            onClick = { showDialog = true },
            headlineContent = { Text(stringResource(R.string.setting_provider_page_model_account_group)) },
            supportingContent = { Text(currentName) },
        )
    }

    if (showDialog) {
        AnimatedAlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.setting_provider_page_model_account_group)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onModelChange(model.copy(accountIds = emptyList()))
                                showDialog = false
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = model.accountIds.isEmpty(), onClick = null)
                        Text(text = allAccountsName)
                    }
                    accounts.forEach { binding ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onModelChange(model.copy(accountIds = listOf(binding.id)))
                                    showDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = model.accountIds == listOf(binding.id), onClick = null)
                            Text(
                                text = binding.groupName(accountProvider.name),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}

@Composable
internal fun BuiltInToolsSettings(
    tools: Set<BuiltInTools>,
    onUpdateTools: (Set<BuiltInTools>) -> Unit,
    parentProvider: ProviderSetting?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
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

        val allTools = listOf(
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

        // 提供商发不出去的工具不列出来：打开之后请求里没有它，还会挡住客户端搜索。
        // 已经打开过的模型仍然列出来，否则用户找不到地方关掉它
        val availableTools = allTools.filter { (tool, _) ->
            parentProvider == null || parentProvider.supportsBuiltInTool(tool) || tool in tools
        }

        CardGroup {
            availableTools.fastForEach { (tool, info) ->
                val (title, description) = info
                val checked = tool in tools
                val supported = parentProvider == null || parentProvider.supportsBuiltInTool(tool)
                val toggle = { enabled: Boolean ->
                    onUpdateTools(if (enabled) tools + tool else tools - tool)
                }
                switchItem(
                    checked = checked,
                    onCheckedChange = toggle,
                    supportingContent = {
                        Text(
                            if (supported) {
                                description
                            } else {
                                stringResource(R.string.setting_provider_page_built_in_tool_unsupported)
                            }
                        )
                    },
                    headlineContent = { Text(text = title) },
                )
            }
        }
    }
}
