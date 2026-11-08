package me.rerere.rikkahub.ui.pages.setting

import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

// 未分组那一栏的 key 单独占一个名字，用户完全可以把分组叫 "ungrouped"，
// 命名分组统一带 "group:" 前缀，两边不会撞在一起
internal const val UNGROUPED_GROUP_KEY = "ungrouped"

/** 折叠状态在 rikkahub.preferences 里的 key。 */
internal const val COLLAPSED_GROUPS_KEY = "provider_collapsed_groups"

internal fun providerGroupKey(name: String?): String =
    name?.let { "group:$it" } ?: UNGROUPED_GROUP_KEY

/**
 * 页面上实际显示的分组顺序。
 *
 * 已保存的顺序优先；只出现在归属表里、还没写进 [Settings.providerGroups] 的分组名按字母补在后面，
 * 不然这些分组里的提供商会无处可去。
 */
internal fun Settings.orderedProviderGroups(): List<String> {
    val assigned = providers
        .mapNotNull { provider -> providerGroupAssignments[provider.id.toString()] }
        .distinct()
    return buildList {
        providerGroups.forEach { name -> if (name in assigned) add(name) }
        assigned.filterNot { it in providerGroups }.sorted().forEach { add(it) }
    }
}

/**
 * 把 [fromName] 这个分组挪到 [targetName] 的位置。
 *
 * 只重排看得见的名字，空分组留在原来的槽位。名字对不上（分组已被删、或只是归属表里的残留名）时原样返回。
 */
internal fun Settings.reorderProviderGroup(fromName: String, targetName: String): Settings {
    if (fromName == targetName) return this
    val groups = providerGroups.toMutableList()
    val fromIndex = groups.indexOf(fromName)
    val toIndex = groups.indexOf(targetName)
    if (fromIndex == -1 || toIndex == -1) return this
    groups.add(toIndex, groups.removeAt(fromIndex))
    return copy(providerGroups = groups)
}

/**
 * 把 [movedId] 挪到同组 [targetId] 的位置。
 *
 * 只在同一组里换位置：跨组会改动分组归属，不在这个手势里做，遇到就原样返回。
 * 组内新顺序写回全局列表里这一组占的槽位，别的提供商不动。
 */
internal fun Settings.reorderProviderWithinGroup(
    movedId: Uuid,
    targetId: Uuid,
    group: String?,
): Settings {
    fun inGroup(provider: ProviderSetting) =
        providerGroupAssignments[provider.id.toString()] == group

    val sameGroup = providers.filter { provider -> inGroup(provider) }
    val fromIndex = sameGroup.indexOfFirst { it.id == movedId }
    val toIndex = sameGroup.indexOfFirst { it.id == targetId }
    if (fromIndex == -1 || toIndex == -1 || fromIndex == toIndex) return this

    val reordered = sameGroup.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    val slots = providers.indices.filter { index -> inGroup(providers[index]) }
    if (slots.size != reordered.size) return this

    val list = providers.toMutableList()
    slots.forEachIndexed { index, slot -> list[slot] = reordered[index] }
    return copy(providers = list)
}

/**
 * 只留下还看得见的分组。
 *
 * key 用的是分组名，删掉或清空的分组如果不顺手清掉，之后同名分组重建时会直接继承旧的收起状态，
 * 用户把提供商放回去却看不见它。
 */
internal fun Settings.pruneCollapsedGroups(collapsed: Set<String>): Set<String> {
    val visible = buildSet {
        orderedProviderGroups().forEach { name -> add(providerGroupKey(name)) }
        add(UNGROUPED_GROUP_KEY)
    }
    return collapsed.intersect(visible)
}

/** 折叠状态存成一个 JSON 字符串数组：分组名里可能有逗号或引号，不能用分隔符拼。 */
internal fun encodeCollapsedGroups(groups: Set<String>): String =
    JsonInstant.encodeToString(groups.sorted())

/** 盘上的内容坏掉（手改、旧版本写了别的格式）时按全部展开处理，不要连累整页打不开。 */
internal fun decodeCollapsedGroups(encoded: String?): Set<String> =
    encoded
        ?.let { raw -> runCatching { JsonInstant.decodeFromString<List<String>>(raw) }.getOrNull() }
        .orEmpty()
        .toSet()
