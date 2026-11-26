package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.availableKeys
import me.rerere.ai.provider.keyLabel
import me.rerere.ai.provider.maskedKey
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Key01
import me.rerere.hugeicons.stroke.Layers01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType

/**
 * 模型列表里的一页：一把启用的 Key，或收「没有绑定 Key」的模型的那一页。
 *
 * [key] 为 null 表示全部 Key 页：分组功能上线前添加的模型、绑定的 Key 已被删掉的模型都落在这里。
 */
data class ModelKeyGroup(
    val key: String?,
    val name: String,
)

/** 分组名：Key 的备注优先，没写备注用掩码后的 Key */
fun ProviderSetting.ApiKey.keyGroupName(key: String): String = keyLabel(key) ?: maskedKey(key)

/**
 * 提供商按 Key 分出来的分组。
 *
 * 只有一把 Key 时返回空列表：分组条上只会有一段，多一层切换没有意义，界面退回不分组的老样子。
 */
fun ProviderSetting.ApiKey.keyGroups(): List<ModelKeyGroup> {
    val keys = availableKeys
    if (keys.size <= 1) return emptyList()
    return keys.map { key -> ModelKeyGroup(key = key, name = keyGroupName(key)) }
}

/**
 * 模型是否属于这一页。
 *
 * [availableKeys] 传提供商的可用 Key，用来判断绑定是否还有效：绑定的 Key 全被删掉或停用之后，
 * 模型会回到全部 Key 页，不至于在界面上消失。
 */
fun Model.belongsToKeyGroup(groupKey: String?, availableKeys: List<String>): Boolean =
    if (groupKey == null) {
        apiKeys.none { it in availableKeys }
    } else {
        groupKey in apiKeys
    }

/**
 * 卡片上要显示的 Key 分组名；不需要显示时返回 null。
 *
 * 只有一个 Key 的提供商不显示（没有第二组可比），覆写了提供商的模型也不显示：
 * 覆写自带 Key，实际生效的是覆写那一份。
 */
fun Model.keyGroupLabel(
    provider: ProviderSetting,
    allKeysName: String,
    missingName: String,
): String? {
    if (providerOverwrite != null) return null
    val apiKeyProvider = provider as? ProviderSetting.ApiKey ?: return null
    val available = apiKeyProvider.availableKeys
    if (available.size <= 1) return null
    val bound = apiKeys.filter { it in available }
    return when {
        bound.isNotEmpty() -> bound.joinToString(" + ") { apiKeyProvider.keyGroupName(it) }
        apiKeys.isEmpty() -> allKeysName
        // 绑定的 Key 都被删掉或停用了：照样请求（退回全部 Key），但要让用户看出分组已经失效
        else -> missingName
    }
}

/** 模型卡片上的 Key 分组标签，同名模型靠它区分 */
@Composable
fun ModelKeyGroupTag(model: Model, providerSetting: ProviderSetting) {
    val label = model.keyGroupLabel(
        provider = providerSetting,
        allKeysName = stringResource(R.string.model_key_group_all),
        missingName = stringResource(R.string.model_key_group_missing),
    ) ?: return
    Tag(type = TagType.DEFAULT) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Key/账号模型分组标签，空间不足时可横向滚动。
 *
 * [compact] 给较窄的模型选择窗口右栏使用。
 */
@Composable
fun ModelKeyGroupStrip(
    groups: List<ModelKeyGroup>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    groupIcon: ImageVector = HugeIcons.Key01,
) {
    if (groups.isEmpty()) return
    val selectedIndex = groups.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        edgePadding = if (compact) 8.dp else 16.dp,
        minTabWidth = if (compact) 72.dp else 88.dp,
    ) {
        groups.forEachIndexed { index, group ->
            key(group.key) {
                Tab(
                    selected = index == selectedIndex,
                    onClick = { onSelect(group.key) },
                    text = {
                        Text(
                            text = group.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = if (group.key == null) HugeIcons.Layers01 else groupIcon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
            }
        }
    }
}
