package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.theme.CustomColors

private val CardGroupCorner = 20.dp
private val CardGroupInnerCorner = 4.dp

private data class CardGroupItem(
    val onClick: (() -> Unit)?,
    val onLongClick: (() -> Unit)? = null,
    val modifier: Modifier,
    val overlineContent: (@Composable () -> Unit)?,
    val headlineContent: (@Composable () -> Unit)?,
    val supportingContent: (@Composable () -> Unit)?,
    val leadingContent: (@Composable () -> Unit)?,
    val trailingContent: (@Composable () -> Unit)?,
    val colors: ListItemColors?,
    // 不为空时是表单项，标题行下方接这块内容
    val formContent: (@Composable ColumnScope.() -> Unit)? = null,
    // 从属项：随开关出现/消失，渲染时这一项自己做尺寸收放，不牵动组内其它行
    val animatedVisible: (() -> Boolean)? = null,
)

@DslMarker
private annotation class CardGroupDsl

@CardGroupDsl
interface CardGroupScope {
    fun item(
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null,
        modifier: Modifier = Modifier,
        overlineContent: (@Composable () -> Unit)? = null,
        supportingContent: (@Composable () -> Unit)? = null,
        leadingContent: (@Composable () -> Unit)? = null,
        trailingContent: (@Composable () -> Unit)? = null,
        colors: ListItemColors? = null,
        headlineContent: @Composable () -> Unit,
        animatedVisible: (() -> Boolean)? = null,
    )

    // 表单项：标题行下方带一块自定义内容（输入框、滑块等），不传标题时整项都是自定义内容
    fun formItem(
        modifier: Modifier = Modifier,
        headlineContent: (@Composable () -> Unit)? = null,
        supportingContent: (@Composable () -> Unit)? = null,
        leadingContent: (@Composable () -> Unit)? = null,
        trailingContent: (@Composable () -> Unit)? = null,
        animatedVisible: (() -> Boolean)? = null,
        content: @Composable ColumnScope.() -> Unit,
    )
}

private class CardGroupScopeImpl : CardGroupScope {
    val items = mutableListOf<CardGroupItem>()

    override fun item(
        onClick: (() -> Unit)?,
        onLongClick: (() -> Unit)?,
        modifier: Modifier,
        overlineContent: (@Composable () -> Unit)?,
        supportingContent: (@Composable () -> Unit)?,
        leadingContent: (@Composable () -> Unit)?,
        trailingContent: (@Composable () -> Unit)?,
        colors: ListItemColors?,
        headlineContent: @Composable () -> Unit,
        animatedVisible: (() -> Boolean)?,
    ) {
        items.add(
            CardGroupItem(
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier,
                overlineContent = overlineContent,
                headlineContent = headlineContent,
                supportingContent = supportingContent,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                colors = colors,
                animatedVisible = animatedVisible,
            )
        )
    }

    override fun formItem(
        modifier: Modifier,
        headlineContent: (@Composable () -> Unit)?,
        supportingContent: (@Composable () -> Unit)?,
        leadingContent: (@Composable () -> Unit)?,
        trailingContent: (@Composable () -> Unit)?,
        animatedVisible: (() -> Boolean)?,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        items.add(
            CardGroupItem(
                onClick = null,
                modifier = modifier,
                overlineContent = null,
                headlineContent = headlineContent,
                supportingContent = supportingContent,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                colors = null,
                formContent = content,
                animatedVisible = animatedVisible,
            )
        )
    }
}

// 带开关的项，点整行也能切换
fun CardGroupScope.switchItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    headlineContent: @Composable () -> Unit,
) = item(
    onClick = if (enabled) {
        { onCheckedChange(!checked) }
    } else null,
    modifier = modifier,
    supportingContent = supportingContent,
    leadingContent = leadingContent,
    trailingContent = {
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    },
    headlineContent = headlineContent,
)

// 随开关出现的从属行：只有这一项自己做尺寸收放
fun CardGroupScope.animatedItem(
    visible: () -> Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    colors: ListItemColors? = null,
    headlineContent: @Composable () -> Unit,
) = item(
    onClick = onClick,
    modifier = modifier,
    overlineContent = overlineContent,
    supportingContent = supportingContent,
    leadingContent = leadingContent,
    trailingContent = trailingContent,
    colors = colors,
    headlineContent = headlineContent,
    animatedVisible = visible,
)

// 随开关出现的从属开关项
fun CardGroupScope.animatedSwitchItem(
    visible: () -> Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    headlineContent: @Composable () -> Unit,
) = item(
    onClick = if (enabled) {
        { onCheckedChange(!checked) }
    } else null,
    modifier = modifier,
    supportingContent = supportingContent,
    leadingContent = leadingContent,
    trailingContent = {
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    },
    headlineContent = headlineContent,
    animatedVisible = visible,
)

// 随开关出现的从属表单项
fun CardGroupScope.animatedFormItem(
    visible: () -> Boolean,
    modifier: Modifier = Modifier,
    headlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) = formItem(
    modifier = modifier,
    headlineContent = headlineContent,
    supportingContent = supportingContent,
    leadingContent = leadingContent,
    trailingContent = trailingContent,
    animatedVisible = visible,
    content = content,
)

// 分组里第 index 项的形状，给不方便走 CardGroup 的列表（比如可拖拽排序的）用
fun cardGroupItemShape(index: Int, count: Int): Shape {
    val top = if (index == 0) CardGroupCorner else CardGroupInnerCorner
    val bottom = if (index == count - 1) CardGroupCorner else CardGroupInnerCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

// 列表项开头带圆形底色的图标
@Composable
fun CardGroupIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val animatedContainerColor by animateColorAsState(
        targetValue = containerColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    val animatedContentColor by animateColorAsState(
        targetValue = contentColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    Box(
        modifier = modifier
            .size(40.dp)
            .background(animatedContainerColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = animatedContentColor,
        )
    }
}

// 标题行右侧显示当前取值的小标签，比如滑块的数值
@Composable
fun CardGroupValue(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            // 等宽数字，拖动滑块时标签宽度不跳
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
    }
}

@Composable
private fun CardGroupFormItem(
    item: CardGroupItem,
    formContent: @Composable ColumnScope.() -> Unit,
    count: Int,
    index: Int,
) {
    Surface(
        modifier = item.modifier.fillMaxWidth(),
        shape = cardGroupItemShape(index, count),
        color = CustomColors.listItemColors.containerColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (item.headlineContent != null || item.leadingContent != null || item.trailingContent != null) {
                Row(
                    // 和 ListItem 一致：带说明文字时，两侧内容靠上对齐
                    verticalAlignment = if (item.supportingContent != null) Alignment.Top else Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item.leadingContent?.invoke()
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        item.headlineContent?.let { headline ->
                            ProvideTextStyle(MaterialTheme.typography.bodyLarge, headline)
                        }
                        item.supportingContent?.let { supporting ->
                            CompositionLocalProvider(
                                LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium, supporting)
                            }
                        }
                    }
                    item.trailingContent?.invoke()
                }
            }
            formContent()
        }
    }
}

@Composable
private fun CardGroupListItem(
    item: CardGroupItem,
    count: Int,
    index: Int,
) {
    // 首尾圆角交给 M3 官方的分段形状：它按 index/count 解析，条目增减时自然跟着变
    val baseShapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    val interactionSource = remember { MutableInteractionSource() }

    // 按压形变自己驱动：M3 内部那套依赖形状全为 CornerBasedShape 且缓存键稳定，
    // 我们的圆角风格（平滑/超椭圆/波浪）不满足时整段跳过，表现为硬切。
    // 这里按同一个按压态与 FastSpatial 曲线插值圆角，六种状态形状统一成同一个值，
    // 按下与松开都连续过渡，不依赖 M3 的分支判定。
    val pressed by interactionSource.collectIsPressedAsState()
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spec,
        label = "cardGroupPressShape",
    )
    val shapes = baseShapes.morphedBy(pressProgress)

    SegmentedListItem(
        shapes = shapes,
        modifier = item.modifier.fillMaxWidth(),
        overlineContent = item.overlineContent,
        supportingContent = item.supportingContent,
        leadingContent = item.leadingContent,
        trailingContent = item.trailingContent,
        colors = item.colors ?: CustomColors.listItemColors,
        onClick = item.onClick ?: {},
        onLongClick = item.onLongClick,
        interactionSource = interactionSource,
        content = item.headlineContent ?: {},
    )
}

// M3 只在六个形状全为 CornerBasedShape 时才做形变动画
// 非标准形状（平滑/超椭圆/波浪）的按压过渡。
// 关键：按下时 M3 取的是 pressedShape、常态取 shape，两者若不同就会瞬间跳过去（硬切）。
// 所以这里把六个状态形状统一成同一个插值结果，由 progress 自己从常态过渡到按压态。
@Composable
private fun ListItemShapes.morphedBy(progress: Float): ListItemShapes {
    val morphed = if (progress <= 0f) shape else lerpShape(shape, pressedShape, progress)
    return copy(
        shape = morphed,
        selectedShape = morphed,
        pressedShape = morphed,
        focusedShape = morphed,
        hoveredShape = morphed,
        draggedShape = morphed,
    )
}

@Composable
private fun lerpShape(from: Shape, to: Shape, fraction: Float): Shape {
    val shape: Shape = remember(from, to, fraction) {
        object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density,
            ): Outline {
                val fromOutline = from.createOutline(size, layoutDirection, density)
                val toOutline = to.createOutline(size, layoutDirection, density)
                if (fromOutline is Outline.Rounded && toOutline is Outline.Rounded) {
                    val a = fromOutline.roundRect
                    val b = toOutline.roundRect
                    return Outline.Rounded(
                        RoundRect(
                            left = a.left + (b.left - a.left) * fraction,
                            top = a.top + (b.top - a.top) * fraction,
                            right = a.right + (b.right - a.right) * fraction,
                            bottom = a.bottom + (b.bottom - a.bottom) * fraction,
                            topLeftCornerRadius = lerpCorner(a.topLeftCornerRadius, b.topLeftCornerRadius, fraction),
                            topRightCornerRadius = lerpCorner(a.topRightCornerRadius, b.topRightCornerRadius, fraction),
                            bottomRightCornerRadius = lerpCorner(a.bottomRightCornerRadius, b.bottomRightCornerRadius, fraction),
                            bottomLeftCornerRadius = lerpCorner(a.bottomLeftCornerRadius, b.bottomLeftCornerRadius, fraction),
                        )
                    )
                }
                return fromOutline
            }
        }
    }
    return shape
}

private fun lerpCorner(a: CornerRadius, b: CornerRadius, fraction: Float): CornerRadius =
    CornerRadius(
        x = a.x + (b.x - a.x) * fraction,
        y = a.y + (b.y - a.y) * fraction,
    )

@Composable
fun CardGroup(
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    content: CardGroupScope.() -> Unit,
) {
    val scope = CardGroupScopeImpl()
    scope.content()

    // 卡片高度不在这里做动画：整卡 animateContentSize 会把不相关的行一起拉伸。
    // 需要收放的从属项由 animatedItem / animatedFormItem / animatedSwitchItem 自己处理。
    Column(modifier = modifier) {
        if (title != null) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                ProvideTextStyle(MaterialTheme.typography.titleSmallEmphasized) {
                    Box(modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)) {
                        title()
                    }
                }
            }
        }
        // 与 M3 官方 SegmentedListItemWithExpansionSample 一致：
        // 可见条目数与索引按「展开后的实际数量」算，从属项整块走 expandVertically/shrinkVertically，
        // 圆角由 segmentedShapes(index, count) 自己解析，间距用 SegmentedGap。
        val visibleCount = scope.items.count { it.animatedVisible?.invoke() != false }
        var shapeIndex = 0
        var i = 0
        while (i < scope.items.size) {
            val item = scope.items[i]
            val visibleCheck = item.animatedVisible
            if (visibleCheck == null) {
                val slot = shapeIndex++
                if (item.formContent != null) {
                    CardGroupFormItem(item = item, formContent = item.formContent, count = visibleCount, index = slot)
                } else {
                    CardGroupListItem(item = item, count = visibleCount, index = slot)
                }
                if (shapeIndex < visibleCount) {
                    Spacer(modifier = Modifier.height(ListItemDefaults.SegmentedGap))
                }
                i++
            } else {
                // 收集连续的从属项，作为一整块收放：整块高度动画，条目自身不缩放，
                // 展开后各条目的圆角与间距都与普通条目一致
                val group = mutableListOf<CardGroupItem>()
                while (i < scope.items.size && scope.items[i].animatedVisible != null) {
                    group += scope.items[i]
                    i++
                }
                val visible = group.first().animatedVisible?.invoke() == true
                // 先占位，展开后组内每个条目在可见序列里的位置要连续
                val slots = group.map { if (visible) shapeIndex++ else shapeIndex }
                AnimatedVisibility(
                    visible = visible,
                    enter = expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
                ) {
                    Column {
                        group.forEachIndexed { gi, gItem ->
                            if (gItem.formContent != null) {
                                CardGroupFormItem(item = gItem, formContent = gItem.formContent, count = visibleCount, index = slots[gi])
                            } else {
                                CardGroupListItem(item = gItem, count = visibleCount, index = slots[gi])
                            }
                            if (slots[gi] < visibleCount - 1) {
                                Spacer(modifier = Modifier.height(ListItemDefaults.SegmentedGap))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CardGroupPreview() {
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text("Card Group")
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            CardGroup(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = { Text("About") },
            ) {
                item(
                    headlineContent = { Text("第一项") },
                )
                item(
                    headlineContent = { Text("第二项") },
                    supportingContent = { Text("支持文本") },
                )
                item(
                    onClick = {},
                    headlineContent = { Text("第三项") },
                    trailingContent = { Text("→") },
                )
            }
        }
    }
}
