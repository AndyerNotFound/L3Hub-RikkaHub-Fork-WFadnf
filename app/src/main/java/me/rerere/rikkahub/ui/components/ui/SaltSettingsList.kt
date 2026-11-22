package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.RoundedColumn

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
 * 自定义右侧按钮）的行不走这里，交给 [com.moriafly.salt.ui.ItemContainer] 或
 * [me.rerere.rikkahub.ui.components.ui.CardGroup]。
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
