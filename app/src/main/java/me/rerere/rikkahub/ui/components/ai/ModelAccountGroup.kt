package me.rerere.rikkahub.ui.components.ai

import kotlin.uuid.Uuid
import me.rerere.ai.provider.AccountBinding
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting

/** 账号在分组条和标签上的名字：昵称优先，其次联系方式，都没有时用 id 前八位 */
fun AccountBinding.groupName(fallback: String): String =
    credential.displayName ?: credential.email ?: fallback

/**
 * 提供商按登录账号分出来的分组。
 *
 * 只有一份登录时返回空列表，与单 Key 的提供商一致：分组条上只有一段，多一层切换没有意义。
 */
fun ProviderSetting.DeepSeekAccount.accountGroups(): List<ModelKeyGroup> {
    val enabled = accounts.filter { !it.disabled }
    if (enabled.size <= 1) return emptyList()
    return enabled.map { binding ->
        ModelKeyGroup(key = binding.id.toString(), name = binding.groupName(name))
    }
}

/** 还可以路由的账号；绑定的账号被删或停用后，模型回到「全部账号」页，不至于在界面上消失 */
fun ProviderSetting.DeepSeekAccount.availableAccountIds(): List<Uuid> =
    accounts.filter { !it.disabled }.map { it.id }

/** 模型是否属于这一页 */
fun Model.belongsToAccountGroup(groupKey: String?, availableAccountIds: List<Uuid>): Boolean =
    if (groupKey == null) {
        accountIds.none { it in availableAccountIds }
    } else {
        accountIds.any { it.toString() == groupKey }
    }

/**
 * 卡片上要显示的账号标签；单账号的提供商不显示（没有第二组可比）。
 *
 * [groupNames] 是分组条上的名字，按账号 id 索引。
 */
fun Model.accountGroupLabel(
    provider: ProviderSetting,
    availableAccountIds: List<Uuid>,
    groupNames: Map<Uuid, String>,
    allAccountsName: String,
    missingName: String,
): String? {
    val accountProvider = provider as? ProviderSetting.DeepSeekAccount ?: return null
    if (accountProvider.accounts.count { !it.disabled } <= 1) return null
    val bound = accountIds.filter { it in availableAccountIds }
    return when {
        bound.isNotEmpty() -> bound.joinToString(" + ") { id -> groupNames[id] ?: allAccountsName }
        accountIds.isEmpty() -> allAccountsName
        // 绑定的账号都被删掉或停用了：照样请求（退回顺序路由），但要让用户看出绑定已经失效
        else -> missingName
    }
}
