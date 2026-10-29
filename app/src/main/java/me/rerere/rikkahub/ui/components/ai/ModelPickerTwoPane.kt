package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.ui.icons.HeartIcon
import kotlin.uuid.Uuid

private const val UNGROUPED_KEY = "__ungrouped"

/**
 * 双列模型选择：左侧是分组和供应商 Logo，右侧是选中的供应商的模型。
 *
 * 分组名长按可以调整顺序，点 Logo 才切换右侧内容，避免一屏塞太多东西。
 */
@Composable
fun ModelPickerTwoPane(
    currentModel: Uuid?,
    providers: List<ProviderSetting>,
    modelType: ModelType,
    groups: List<String>,
    assignments: Map<String, String>,
    favoriteIds: Set<Uuid>,
    onSelect: (Model) -> Unit,
    onReorderGroups: (List<String>) -> Unit,
    onToggleFavorite: (Uuid) -> Unit,
) {
    val providersByGroup = remember(providers, assignments) {
        providers.groupBy { provider -> assignments[provider.id.toString()] }
    }
    val orderedGroups = remember(groups, providersByGroup) {
        buildList {
            groups.forEach { name -> if (providersByGroup[name] != null) add(name) }
            providersByGroup.keys
                .filterNotNull()
                .filterNot { it in groups }
                .sorted()
                .forEach { name -> if (name !in this) add(name) }
        }
    }
    val favoriteModels = remember(providers, favoriteIds, modelType) {
        providers.flatMap { provider -> provider.models.filter { it.type == modelType } }
            .filter { it.id in favoriteIds }
    }

    val initialProvider = remember(currentModel, providers) {
        providers.firstOrNull { provider -> provider.models.any { it.id == currentModel } }?.id
            ?: providers.firstOrNull()?.id
    }
    var selectedProviderId by remember(initialProvider) { mutableStateOf(initialProvider) }
    var showFavorites by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var reorderDialog by remember { mutableStateOf(false) }
    val collapsedGroups = remember { mutableStateMapOf<String, Boolean>() }

    val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }
    val models = remember(search, showFavorites, selectedProvider, providers, modelType, favoriteModels) {
        when {
            search.isNotBlank() -> providers.flatMap { provider ->
                provider.models
                    .filter { it.type == modelType && it.displayName.contains(search, true) }
                    .map { model -> model to provider.name }
            }

            showFavorites -> favoriteModels.map { model ->
                val providerName = providers.firstOrNull { provider -> provider.models.any { it.id == model.id } }?.name
                model to providerName.orEmpty()
            }

            selectedProvider != null -> selectedProvider.models
                .filter { it.type == modelType }
                .map { model -> model to selectedProvider.name }

            else -> emptyList()
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .width(88.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            if (favoriteModels.isNotEmpty()) {
                item(key = "favorites") {
                    GroupLabel(
                        text = stringResource(R.string.model_list_favorite),
                        selected = showFavorites,
                        onClick = {
                            showFavorites = true
                            search = ""
                        },
                        onLongClick = null,
                    )
                    ProviderLogoButton(
                        name = stringResource(R.string.model_list_favorite),
                        isFavoriteEntry = true,
                        selected = showFavorites,
                        onClick = {
                            showFavorites = true
                            search = ""
                        },
                    )
                }
            }

            orderedGroups.forEach { groupName ->
                item(key = "group:$groupName") {
                    val collapsed = collapsedGroups[groupName] == true
                    GroupLabel(
                        text = groupName,
                        selected = false,
                        collapsed = collapsed,
                        onClick = { collapsedGroups[groupName] = !collapsed },
                        onLongClick = if (orderedGroups.size > 1) {
                            { reorderDialog = true }
                        } else {
                            null
                        },
                    )
                }
                if (collapsedGroups[groupName] != true) {
                    val groupProviders = providersByGroup[groupName].orEmpty()
                    items(groupProviders, key = { it.id }) { provider ->
                        ProviderLogoButton(
                            name = provider.name,
                            isFavoriteEntry = false,
                            selected = !showFavorites && provider.id == selectedProviderId,
                            onClick = {
                                showFavorites = false
                                selectedProviderId = provider.id
                            },
                        )
                    }
                }
            }

            val ungrouped = providersByGroup[null].orEmpty()
            if (ungrouped.isNotEmpty()) {
                item(key = "group:$UNGROUPED_KEY") {
                    val collapsed = collapsedGroups[UNGROUPED_KEY] == true
                    GroupLabel(
                        text = stringResource(R.string.setting_provider_group_none),
                        selected = false,
                        collapsed = collapsed,
                        onClick = { collapsedGroups[UNGROUPED_KEY] = !collapsed },
                        onLongClick = null,
                    )
                }
                if (collapsedGroups[UNGROUPED_KEY] != true) {
                    items(ungrouped, key = { it.id }) { provider ->
                        ProviderLogoButton(
                            name = provider.name,
                            isFavoriteEntry = false,
                            selected = !showFavorites && provider.id == selectedProviderId,
                            onClick = {
                                showFavorites = false
                                selectedProviderId = provider.id
                            },
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        )

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Text(
                text = when {
                    search.isNotBlank() -> stringResource(R.string.model_list_search)
                    showFavorites -> stringResource(R.string.model_list_favorite)
                    else -> selectedProvider?.name.orEmpty()
                },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            )
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                leadingIcon = {
                    Icon(HugeIcons.Search01, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                placeholder = {
                    Text(stringResource(R.string.model_list_search), style = MaterialTheme.typography.bodySmall)
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(12.dp),
            ) {
                items(models, key = { it.first.id }) { (model, providerName) ->
                    ModelCardTwoPane(
                        model = model,
                        providerName = providerName,
                        selected = model.id == currentModel,
                        favorite = model.id in favoriteIds,
                        onSelect = { onSelect(model) },
                        onToggleFavorite = { onToggleFavorite(model.id) },
                    )
                }
            }
        }
    }

    if (reorderDialog) {
        GroupOrderDialog(
            groups = orderedGroups,
            onDismiss = { reorderDialog = false },
            onConfirm = { ordered ->
                onReorderGroups(ordered)
                reorderDialog = false
            },
        )
    }
}

@Composable
private fun GroupLabel(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    collapsed: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (collapsed) {
            Icon(
                imageVector = HugeIcons.ArrowDown01,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProviderLogoButton(
    name: String,
    isFavoriteEntry: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .size(44.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isFavoriteEntry) {
                Icon(
                    imageVector = HeartIcon,
                    contentDescription = name,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                AutoAIIcon(name = name, color = Color.Transparent)
            }
        }
    }
}

@Composable
private fun ModelCardTwoPane(
    model: Model,
    providerName: String,
    selected: Boolean,
    favorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.displayName.ifBlank { model.modelId },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = HeartIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (favorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Text(
                text = providerName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "WARN ${formatTokens(model.warnContextTokens)} · MAX ${formatTokens(model.maxContextTokens)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GroupOrderDialog(
    groups: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var order by remember(groups) { mutableStateOf(groups) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.model_list_reorder_groups)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                order.forEachIndexed { index, name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    order = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                                }
                            },
                            enabled = index > 0,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(HugeIcons.ArrowUp01, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                if (index < order.lastIndex) {
                                    order = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                                }
                            },
                            enabled = index < order.lastIndex,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(HugeIcons.ArrowDown01, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(order) }) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.chat_page_cancel))
            }
        },
    )
}

private fun formatTokens(value: Int?): String = when {
    value == null -> "-"
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000f)
    value >= 1_000 -> "%.1fK".format(value / 1_000f)
    else -> value.toString()
}
