package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.time.toJavaInstant
import kotlinx.coroutines.launch
import me.rerere.ai.provider.AccountBinding
import me.rerere.ai.provider.ProviderQuota
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.supportsQuota
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.DashboardSpeed02
import me.rerere.hugeicons.stroke.Login03
import me.rerere.hugeicons.stroke.Logout03
import me.rerere.hugeicons.stroke.MoneyBag02
import me.rerere.hugeicons.stroke.UserCircle
import me.rerere.oauth.CustomTabsOAuthAuthorizationLauncher
import me.rerere.ui.components.AnimatedAlertDialog
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.account.AccountLoginStatus
import me.rerere.rikkahub.data.ai.account.ProviderAccountManager
import me.rerere.rikkahub.ui.components.ai.longLabel
import me.rerere.rikkahub.ui.components.ai.rememberProviderQuota
import me.rerere.rikkahub.ui.components.ui.animatedItem
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.style.IconButton
import me.rerere.rikkahub.ui.style.TextButton
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.utils.toMessageTimeString
import org.koin.compose.koinInject

// 账号类提供商：登录状态、用量和说明
@Composable
internal fun ProviderConfigureAccount(
    provider: ProviderSetting.Account,
    onEdit: (provider: ProviderSetting) -> Unit,
    onCommit: (provider: ProviderSetting) -> Unit,
    serviceName: String,
    plan: String?,
    disclaimer: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountManager = koinInject<ProviderAccountManager>()
    val loginStatus by accountManager.loginStatus.collectAsStateWithLifecycle()
    val status = loginStatus[provider.id]
    val accounts = provider.accounts
    // CardGroup 的内容不是 @Composable，文案先在函数里取好
    val disabledLabel = stringResource(R.string.setting_provider_page_account_disabled)
    val signedInLabel = stringResource(R.string.setting_provider_page_signed_in)
    val signInLabel = stringResource(R.string.setting_provider_page_sign_in_with, serviceName)
    val addAccountLabel = stringResource(R.string.setting_provider_page_add_account)
    // 只有多账号的提供商才谈得上「添加账号」，单账号类型登录一次就是换人
    val multiAccount = provider is ProviderSetting.DeepSeekAccount
    var pendingLogout by remember { mutableStateOf<AccountBinding?>(null) }
    // 登录、退出、停用都直接写设置，写之前先把配置页的草稿落盘：
    // 否则随后的重建会把还没保存的名称和启用开关换成库里的旧值
    fun commitDraft() {
        onCommit(provider)
    }

    CardGroup {
        nameItem(provider, onEdit)
    }

    CardGroup(title = { Text(stringResource(R.string.setting_provider_page_account)) }) {
        // 先在收集条目时算好文案，组合调用都放进条目的内容槽。
        for (binding in accounts) {
            val credential = binding.credential
            // 账号给得出昵称时以昵称为主、联系方式为辅，否则退回联系方式
            val headline = credential.displayName ?: credential.email
            val contact = when {
                binding.disabled -> disabledLabel
                credential.displayName != null -> credential.email ?: plan
                else -> plan
            }
            item(
                // 多账号时点整行停用/启用，退出登录单独一个按钮
                onClick = if (multiAccount) {
                    {
                        commitDraft()
                        scope.launch {
                            accountManager.setAccountDisabled(provider.id, binding.id, !binding.disabled)
                        }
                    }
                } else {
                    null
                },
                leadingContent = { CardGroupIcon(HugeIcons.UserCircle) },
                supportingContent = contact?.let { text -> { Text(text) } },
                headlineContent = { Text(headline ?: signedInLabel) },
                trailingContent = {
                    IconButton(onClick = { pendingLogout = binding }) {
                        Icon(
                            imageVector = HugeIcons.Logout03,
                            contentDescription = stringResource(R.string.setting_provider_page_sign_out),
                        )
                    }
                },
            )
        }

        val deviceCode = status as? AccountLoginStatus.DeviceCode
        animatedItem(
            visible = { deviceCode != null },
            onClick = {
                deviceCode?.let { CustomTabsOAuthAuthorizationLauncher.launch(context, it.verificationUrl) }
            },
            leadingContent = { ContainedLoadingIndicator(modifier = Modifier.size(40.dp)) },
            overlineContent = { Text(stringResource(R.string.setting_provider_page_device_code_hint)) },
            supportingContent = { Text(stringResource(R.string.setting_provider_page_device_code_reopen)) },
            headlineContent = {
                Text(
                    text = deviceCode?.userCode.orEmpty(),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    fontFamily = JetbrainsMono,
                )
            },
        )
        animatedItem(
            visible = { deviceCode != null },
            onClick = { accountManager.cancelLogin(provider.id) },
            leadingContent = { CardGroupIcon(HugeIcons.Cancel01) },
            headlineContent = { Text(stringResource(R.string.cancel)) },
        )
        animatedItem(
            visible = { status == AccountLoginStatus.Authorizing },
            onClick = { accountManager.cancelLogin(provider.id) },
            leadingContent = { ContainedLoadingIndicator(modifier = Modifier.size(40.dp)) },
            supportingContent = { Text(stringResource(R.string.setting_provider_page_waiting_authorization_desc)) },
            headlineContent = { Text(stringResource(R.string.setting_provider_page_waiting_authorization)) },
        )
        animatedItem(
            visible = {
                deviceCode == null && status != AccountLoginStatus.Authorizing &&
                    (accounts.isEmpty() || multiAccount)
            },
            onClick = {
                commitDraft()
                accountManager.startLogin(provider.id, context)
            },
            leadingContent = { CardGroupIcon(HugeIcons.Login03) },
            supportingContent = (status as? AccountLoginStatus.Failed)?.let { failed ->
                { Text(failed.message, color = MaterialTheme.colorScheme.error) }
            },
            trailingContent = { Icon(HugeIcons.ArrowRight01, contentDescription = null) },
            headlineContent = { Text(if (accounts.isEmpty()) signInLabel else addAccountLabel) },
        )
    }

    // 退出一份登录会立刻改变路由，先确认一次
    pendingLogout?.let { binding ->
        AnimatedAlertDialog(
            onDismissRequest = { pendingLogout = null },
            title = { Text(stringResource(R.string.setting_provider_page_sign_out)) },
            text = { Text(stringResource(R.string.setting_provider_page_sign_out_confirm)) },
            dismissButton = {
                TextButton(onClick = { pendingLogout = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingLogout = null
                        commitDraft()
                        scope.launch { accountManager.logout(provider.id, binding.id) }
                    }
                ) {
                    Text(stringResource(R.string.setting_provider_page_sign_out))
                }
            },
        )
    }

    if (provider.supportsQuota) {
        AccountUsage(provider)
    }

    Text(
        text = disclaimer,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

// 订阅用量：每个窗口一条进度和重置时间
@Composable
private fun AccountUsage(provider: ProviderSetting.Account) {
    val result = rememberProviderQuota(provider)
    val quota = result?.getOrNull()
    // CardGroup 的内容不是 @Composable，文案先取好
    val bonusLabel = stringResource(R.string.setting_provider_page_bonus_balance)
    CardGroup(title = { Text(stringResource(R.string.setting_provider_page_usage)) }) {
        when {
            result == null -> item(
                leadingContent = { ContainedLoadingIndicator(modifier = Modifier.size(40.dp)) },
                headlineContent = { Text(stringResource(R.string.setting_provider_page_usage_loading)) },
            )

            quota == null -> item(
                leadingContent = { CardGroupIcon(HugeIcons.DashboardSpeed02) },
                supportingContent = {
                    Text(
                        text = result.exceptionOrNull()?.message.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                headlineContent = { Text(stringResource(R.string.setting_provider_page_usage_failed)) },
            )

            // 余额：先给合计，多账号时再按账号逐条列出
            quota is ProviderQuota.Balance -> {
                item(
                    leadingContent = { CardGroupIcon(HugeIcons.MoneyBag02) },
                    supportingContent = quota.secondary?.let { value -> { Text("$bonusLabel $value") } },
                    headlineContent = { Text(stringResource(R.string.chat_quota_bar_balance)) },
                    trailingContent = {
                        Text(quota.value, style = MaterialTheme.typography.titleMediumEmphasized)
                    },
                )
                for (account in quota.accounts) {
                    item(
                        leadingContent = { CardGroupIcon(HugeIcons.UserCircle) },
                        supportingContent = account.secondary?.let { value -> { Text("$bonusLabel $value") } },
                        headlineContent = { Text(account.label) },
                        trailingContent = {
                            Text(account.value, style = MaterialTheme.typography.titleMediumEmphasized)
                        },
                    )
                }
            }

            quota is ProviderQuota.UsageLimits && quota.windows.isEmpty() -> item(
                leadingContent = { CardGroupIcon(HugeIcons.DashboardSpeed02) },
                headlineContent = { Text(stringResource(R.string.setting_provider_page_usage_none)) },
            )

            quota is ProviderQuota.UsageLimits -> quota.windows.forEach { window ->
                val reached = quota.limitReached || window.usedPercent >= 100
                formItem(
                    headlineContent = { Text(window.longLabel()) },
                    supportingContent = window.resetsAt?.let { resetsAt ->
                        {
                            val time = LocalDateTime.ofInstant(resetsAt.toJavaInstant(), ZoneId.systemDefault())
                            Text(stringResource(R.string.setting_provider_page_usage_resets, time.toMessageTimeString()))
                        }
                    },
                    trailingContent = {
                        Text(
                            text = "${window.usedPercent.roundToInt()}%",
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = if (reached) MaterialTheme.colorScheme.error else Color.Unspecified,
                        )
                    },
                ) {
                    LinearWavyProgressIndicator(
                        progress = { (window.usedPercent / 100).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (reached) MaterialTheme.colorScheme.error else ProgressIndicatorDefaults.linearColor,
                    )
                }
            }
        }
    }
}
