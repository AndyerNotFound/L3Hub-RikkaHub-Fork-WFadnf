package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemContainer
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme

/**
 * 设置页用的 Salt 列表：标题在分组外，行在 [RoundedColumn] 里。
 *
 * Salt 的分组自带 16dp 的横向外距与内距，放在 `LazyColumn` 里时外层不要再补横向 padding，
 * 否则内外两圈会叠成 32dp。竖向间距仍由调用方（`verticalArrangement`）控制。
 */
@Composable
fun SaltGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            ItemOuterTitle(text = title)
        }
        RoundedColumn(content = content)
    }
}

/**
 * 一行纯跳转的设置项：图标 + 标题 + 说明，右侧箭头由 Salt 画。
 *
 * Salt 的 [Item] 只吃 String 与 Painter，不吃任意可组合内容，所以带自定义控件（开关、滑块、
 * 自定义右侧按钮）的行不走这里，交给 [ItemContainer] 或 [SaltIconRow] / [SaltSwitchRow]。
 */
@Composable
fun SaltNavItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
) {
    Item(
        onClick = onClick,
        text = text,
        modifier = modifier,
        iconPainter = rememberVectorPainter(icon),
        sub = sub,
    )
}

/**
 * 圆形底色的图标行头，替代 CardGroupIcon：纯色底、无颜色动画，行内视觉与旧版一致。
 * 默认取 M3 的 secondaryContainer 一档，调用方可传别的 container 色。
 */
@Composable
fun SaltIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .background(containerColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = contentColor,
        )
    }
}

/**
 * 带圆形底图标的一行（跳转 / 长按），替代 CardGroup 里的 `item + CardGroupIcon`。
 * 标题走 SaltTheme 下发的主文字样式，说明文字走 subText 档，与旧 CardGroup 视觉一致。
 */
@Composable
fun SaltIconRow(
    icon: ImageVector,
    headline: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ItemContainer(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SaltTheme.dimens.item)
                .then(
                    when {
                        onClick != null && onLongClick != null ->
                            Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                        onClick != null -> Modifier.clickable(onClick = onClick)
                        onLongClick != null -> Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick)
                        else -> Modifier
                    }
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SaltIconBadge(icon = icon, containerColor = containerColor, contentColor = contentColor)
            Spacer(modifier = Modifier.width(SaltTheme.dimens.subPadding))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = headline)
                supporting?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = it, color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                }
            }
            trailing?.invoke()
        }
    }
}

/**
 * 带开关的一行，替代 CardGroup 的 `switchItem`：开关仍走 AppSwitch，
 * 「开关类型」可配置功能原样保留；整行点击也切换开关（与旧版一致）。
 */
@Composable
fun SaltSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    headline: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supporting: String? = null,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    ItemContainer(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SaltTheme.dimens.item)
                .then(
                    if (enabled) {
                        Modifier.clickable { onCheckedChange(!checked) }
                    } else {
                        Modifier
                    }
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                SaltIconBadge(icon = icon, containerColor = containerColor, contentColor = contentColor)
                Spacer(modifier = Modifier.width(SaltTheme.dimens.subPadding))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = headline)
                supporting?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = it, color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                }
            }
            Spacer(modifier = Modifier.width(SaltTheme.dimens.subPadding))
            AppSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    }
}
