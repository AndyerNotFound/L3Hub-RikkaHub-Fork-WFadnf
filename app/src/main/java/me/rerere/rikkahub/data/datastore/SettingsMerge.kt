package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.ProviderSetting

/**
 * 恢复备份时的设置合并：保留当前设置，只补入备份里当前缺少的条目。
 *
 * 只合并用户维护的集合（提供商、助手、提示词、主题、服务商配置等），同 id 的条目以当前为准；
 * 主题、模型选择、界面与网络偏好这类单值设置不被备份改动——要整份还原请用覆盖模式。
 */
internal fun Settings.mergedWith(backup: Settings): Settings = copy(
    customThemes = customThemes.unionBy(backup.customThemes) { it.id },
    favoriteModels = (favoriteModels + backup.favoriteModels).distinct(),
    providers = mergeProviders(providers, backup.providers),
    providerGroups = (providerGroups + backup.providerGroups).distinct(),
    providerGroupAssignments = backup.providerGroupAssignments + providerGroupAssignments,
    providerProxies = backup.providerProxies + providerProxies,
    providerProxyUsernames = backup.providerProxyUsernames + providerProxyUsernames,
    providerProxyPasswords = backup.providerProxyPasswords + providerProxyPasswords,
    customCaCertificates = (customCaCertificates + backup.customCaCertificates).distinct(),
    assistants = assistants.unionBy(backup.assistants) { it.id },
    assistantTags = assistantTags.unionBy(backup.assistantTags) { it.id },
    searchServices = searchServices.unionBy(backup.searchServices) { it.id },
    enabledSearchServiceIds = enabledSearchServiceIds + backup.enabledSearchServiceIds,
    mcpServers = mcpServers.unionBy(backup.mcpServers) { it.id },
    toolOverrides = toolOverrides.unionBy(backup.toolOverrides) { it.name },
    mediaGenerationProviders = mediaGenerationProviders.unionBy(backup.mediaGenerationProviders) { it.id },
    ttsProviders = ttsProviders.unionBy(backup.ttsProviders) { it.id },
    asrProviders = asrProviders.unionBy(backup.asrProviders) { it.id },
    modeInjections = modeInjections.unionBy(backup.modeInjections) { it.id },
    lorebooks = lorebooks.unionBy(backup.lorebooks) { it.id },
    quickMessages = quickMessages.unionBy(backup.quickMessages) { it.id },
    guideMessages = guideMessages.unionBy(backup.guideMessages) { it.id },
)

// 同一个提供商里缺的模型也补进来，其余以当前为准
private fun mergeProviders(
    current: List<ProviderSetting>,
    backup: List<ProviderSetting>,
): List<ProviderSetting> {
    val backupById = backup.associateBy { it.id }
    val currentIds = current.mapTo(HashSet()) { it.id }
    return current.map { provider ->
        val incoming = backupById[provider.id]
        if (incoming == null) {
            provider
        } else {
            provider.copyProvider(models = provider.models.unionBy(incoming.models) { it.id })
        }
    } + backup.filter { it.id !in currentIds }
}

// 按 key 取并集，同 key 保留当前列表里的那一条
private fun <T, K> List<T>.unionBy(other: List<T>, key: (T) -> K): List<T> {
    val existing = mapTo(HashSet()) { key(it) }
    return this + other.filter { key(it) !in existing }
}
