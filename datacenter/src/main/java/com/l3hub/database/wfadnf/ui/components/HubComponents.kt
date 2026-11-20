package com.l3hub.database.wfadnf.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// 与 L3Hub 的 CardGroup 保持同一组尺寸：组内条目 2dp 缝、首尾 20dp 圆角、中间 4dp
private val GroupCorner = 20.dp
private val GroupInnerCorner = 4.dp
private val GroupItemSpacing = 2.dp

/**
 * 数据备份页那种「一张卡就是一组设置」的分组列表。
 *
 * L3Hub 的 `CardGroup` 挂在 app 模块里，依赖设置存储、Miuix 分支与毛玻璃那一整串东西，
 * 数据中心是独立应用、链接不到它；这里按同一套排版与尺寸重写一份，条目结构、圆角变化与
 * 图标位置都和备份页一致。
 */
@Composable
fun HubCardGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    // 不能标 @Composable：那会变成独立的重组作用域，外层状态变了只重跑收集、下面读条目的 Column 不重组
    content: HubCardGroupScope.() -> Unit,
) {
    val scope = HubCardGroupScopeImpl()
    scope.content()
    val items = scope.items.filter { it.visible }
    Column(modifier = modifier) {
        if (title != null && items.isNotEmpty()) {
            ProvideTextStyle(MaterialTheme.typography.titleSmall) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                    Box(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)) {
                        Text(title)
                    }
                }
            }
        }
        items.forEachIndexed { index, item ->
            HubCardGroupItem(item = item, count = items.size, index = index)
            if (index != items.lastIndex) Spacer(Modifier.height(GroupItemSpacing))
        }
    }
}

@DslMarker
private annotation class HubCardGroupDsl

@HubCardGroupDsl
interface HubCardGroupScope {
    fun item(
        onClick: (() -> Unit)? = null,
        visible: Boolean = true,
        headlineContent: @Composable () -> Unit,
        supportingContent: (@Composable () -> Unit)? = null,
        leadingContent: (@Composable () -> Unit)? = null,
        trailingContent: (@Composable () -> Unit)? = null,
    )

    fun switchItem(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        enabled: Boolean = true,
        visible: Boolean = true,
        headlineContent: @Composable () -> Unit,
        supportingContent: (@Composable () -> Unit)? = null,
        leadingContent: (@Composable () -> Unit)? = null,
    )

    // 标题行下方接一块自定义内容：进度、日志、统计、选项组都用它
    fun formItem(
        visible: Boolean = true,
        headlineContent: (@Composable () -> Unit)? = null,
        content: @Composable ColumnScope.() -> Unit,
    )
}

private data class HubCardItem(
    val onClick: (() -> Unit)?,
    val visible: Boolean,
    val headlineContent: (@Composable () -> Unit)?,
    val supportingContent: (@Composable () -> Unit)?,
    val leadingContent: (@Composable () -> Unit)?,
    val trailingContent: (@Composable () -> Unit)?,
    val formContent: (@Composable ColumnScope.() -> Unit)? = null,
)

private class HubCardGroupScopeImpl : HubCardGroupScope {
    val items = mutableListOf<HubCardItem>()

    override fun item(
        onClick: (() -> Unit)?,
        visible: Boolean,
        headlineContent: @Composable () -> Unit,
        supportingContent: (@Composable () -> Unit)?,
        leadingContent: (@Composable () -> Unit)?,
        trailingContent: (@Composable () -> Unit)?,
    ) {
        items += HubCardItem(
            onClick = onClick,
            visible = visible,
            headlineContent = headlineContent,
            supportingContent = supportingContent,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
        )
    }

    override fun switchItem(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        enabled: Boolean,
        visible: Boolean,
        headlineContent: @Composable () -> Unit,
        supportingContent: (@Composable () -> Unit)?,
        leadingContent: (@Composable () -> Unit)?,
    ) {
        item(
            onClick = if (enabled) {
                { onCheckedChange(!checked) }
            } else {
                null
            },
            visible = visible,
            headlineContent = headlineContent,
            supportingContent = supportingContent,
            leadingContent = leadingContent,
            trailingContent = {
                androidx.compose.material3.Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                )
            },
        )
    }

    override fun formItem(
        visible: Boolean,
        headlineContent: (@Composable () -> Unit)?,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        items += HubCardItem(
            onClick = null,
            visible = visible,
            headlineContent = headlineContent,
            supportingContent = null,
            leadingContent = null,
            trailingContent = null,
            formContent = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HubCardGroupItem(item: HubCardItem, count: Int, index: Int) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val corner = GroupCorner
    val inner = GroupInnerCorner
    val topCorner by animateDpAsState(
        targetValue = if (pressed || count == 1 || index == 0) corner else inner,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "hubItemTopCorner",
    )
    val bottomCorner by animateDpAsState(
        targetValue = if (pressed || count == 1 || index == count - 1) corner else inner,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "hubItemBottomCorner",
    )
    // 弹簧会过冲，负半径会让 RoundedCornerShape 抛异常
    val shape = RoundedCornerShape(
        topStart = topCorner.coerceAtLeast(0.dp),
        topEnd = topCorner.coerceAtLeast(0.dp),
        bottomStart = bottomCorner.coerceAtLeast(0.dp),
        bottomEnd = bottomCorner.coerceAtLeast(0.dp),
    )
    val container = MaterialTheme.colorScheme.surfaceContainerLow
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (item.onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = item.onClick,
                    )
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = container,
    ) {
        val formContent = item.formContent
        if (formContent != null) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (item.headlineContent != null) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item.leadingContent?.invoke()
                        ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                            Box(Modifier.weight(1f)) { item.headlineContent() }
                        }
                        item.trailingContent?.invoke()
                    }
                }
                formContent()
            }
        } else {
            ListItem(
                headlineContent = item.headlineContent ?: {},
                supportingContent = item.supportingContent,
                leadingContent = item.leadingContent,
                trailingContent = item.trailingContent,
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

/** 列表项开头带圆形底色的图标，与备份页每一个操作行的图标一致 */
@Composable
fun HubCardIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** 正在跑的操作用波浪进度环顶掉图标，和备份页的导出/导入行一致 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HubActionIcon(icon: ImageVector, loading: Boolean = false) {
    if (loading) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
        }
    } else {
        HubCardIcon(icon)
    }
}

/** 取值小标签：状态、库版本、数量 */
@Composable
fun HubStatusPill(
    text: String,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

data class HubTabSpec(val label: String, val icon: ImageVector)

/** 备份页顶部那排胶囊选项卡：选中项展开图标，未选中只有文字 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HubPillTabs(
    tabs: List<HubTabSpec>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selected
            ToggleButton(
                checked = isSelected,
                onCheckedChange = { if (!isSelected) onSelect(index) },
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                AnimatedVisibility(
                    visible = isSelected,
                    enter = expandHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) +
                        fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                    exit = shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) +
                        fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    Row {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                    }
                }
                Text(text = tab.label, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** 一段说明文字：组与组之间的解释、空态提示 */
@Composable
fun HubNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
