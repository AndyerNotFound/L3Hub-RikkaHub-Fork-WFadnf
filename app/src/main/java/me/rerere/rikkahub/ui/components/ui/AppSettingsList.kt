package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.t8rin.toolbox.ui.widget.icon_shape.IconShapeContainer
import com.t8rin.toolbox.ui.widget.modifier.ShapeDefaults
import com.t8rin.toolbox.ui.widget.modifier.container
import com.t8rin.toolbox.ui.widget.preferences.PreferenceItem
import com.t8rin.toolbox.ui.widget.preferences.PreferenceItemDefaults
import com.t8rin.toolbox.ui.widget.preferences.PreferenceRow
import com.t8rin.toolbox.ui.widget.preferences.PreferenceRowSwitch

/**
 * ImageToolbox 观感的设置列表。行全部走引擎的 [PreferenceRow] / [PreferenceRowSwitch] /
 * [PreferenceItem]（按压形状动画、scrollAnchor、触感、图标形状联动都在引擎内），
 * 分组卡用 [container]（底色 surfaceContainerLow、形状随轮廓设置）。
 * 观感基准是源工程 `core/ui/widget/preferences` 与 `feature/settings` 的用法：
 * 行卡底色 surfaceContainerLow、组圆角 16dp、跳转行不带尾部箭头、禁用整行 alpha 0.5。
 *
 * 与旧 Salt 列表的对应关系（调用点迁移表）：
 * - `RoundedColumn { … }` → [AppSettingsCard]
 * - `ItemContainer { … }` → [AppSettingsRow]
 * - `ItemOuterTitle(text)` → [AppSettingsGroupTitle]
 * - `ItemOuterTip(text)` → [AppSettingsGroupTip]
 * - `SaltGroup(title) { … }` → [AppSettingsGroup]
 * - `SaltNavItem(...)` → [AppNavItem]
 * - `SaltIconRow(...)` → [AppIconRow]
 * - `SaltSwitchRow(...)` → [AppSwitchRow]
 * - `SaltIconBadge(...)` → [AppIconBadge]
 * - `SaltTheme.colors.text/subText` → `colorScheme.onSurface / onSurface@50%`
 * - `SaltTheme.textStyles.sub` → [PreferenceItemDefaults.SubtitleFontStyle]
 * - `SaltTheme.dimens.item / itemIcon / subPadding` → 行高 56dp / 图标 20dp / 间距 16dp
 *
 * 分组卡自带 12dp 的横向外距，放在 `LazyColumn` 里时外层不要再补横向 padding，
 * 否则内外两圈会叠成 24dp。竖向间距仍由调用方（`verticalArrangement`）控制。
 */
@Composable
fun AppSettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .container(
                shape = ShapeDefaults.default,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                resultPadding = 0.dp,
                autoShadowElevation = 1.dp,
            ),
        content = content,
    )
}

/** 分组卡内的无底行容器：只负责横向内距，行高与点击由调用方（Row）自己决定。 */
@Composable
fun AppSettingsRow(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        content = content,
    )
}

/** 分组外的区块标题。 */
@Composable
fun AppSettingsGroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

/** 分组外的辅助说明小字。 */
@Composable
fun AppSettingsGroupTip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** 标题在分组外、行在分组卡里的完整区块。 */
@Composable
fun AppSettingsGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        title?.let { AppSettingsGroupTitle(it) }
        AppSettingsCard(content = content)
    }
}

/**
 * 一行纯跳转的设置项：图标容器（可空，无图标即源工程的「标题 + 说明」行）+ 标题 + 说明，
 * 直接走 ImageToolbox 的 [PreferenceItem]。
 * 底色透明、阴影交给分组卡；尾部默认不带图标（源工程分组导航行的样子），调用点可用
 * [endIcon] 挂自己的图标。
 */
@Composable
fun AppNavItem(
    icon: ImageVector? = null,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    endIcon: ImageVector? = null,
) {
    PreferenceItem(
        onClick = onClick,
        title = text,
        subtitle = sub,
        startIcon = icon,
        endIcon = endIcon,
        modifier = modifier,
        containerColor = Color.Transparent,
        autoShadowElevation = 0.dp,
    )
}

/**
 * 圆形底色的图标行头：走引擎的 [IconShapeContainer]，容器形状跟随设置里的图标形状，
 * 底色/内容色缺省由引擎按主题与容器亮度算（淡 primaryContainer 系）。
 */
@Composable
fun AppIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
) {
    IconShapeContainer(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        content = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor.takeOrElse { LocalContentColor.current },
            )
        },
    )
}

/**
 * 带圆形底图标的一行（跳转 / 长按 / 尾部自定义控件）。
 * 无尾部控件时走 [PreferenceItem]（支持 onLongClick），有尾部控件走 [PreferenceRow]
 * （endContent 收任意 Composable）；两条分支都是引擎行：16sp Medium 标题、12sp 50% 说明、
 * 按压形状动画、scrollAnchor 与触感。图标容器后 16dp 间距与源工程一致。
 */
@Composable
fun AppIconRow(
    icon: ImageVector,
    headline: String,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    if (trailing == null) {
        PreferenceItem(
            onClick = onClick,
            onLongClick = onLongClick,
            title = headline,
            subtitle = supporting,
            startIcon = icon,
            modifier = modifier,
            containerColor = Color.Transparent,
            autoShadowElevation = 0.dp,
        )
    } else {
        PreferenceRow(
            modifier = modifier,
            title = headline,
            subtitle = supporting,
            containerColor = Color.Unspecified,
            drawContainer = false,
            autoShadowElevation = 0.dp,
            onClick = onClick,
            startContent = {
                AppIconBadge(
                    icon = icon,
                    modifier = Modifier.padding(end = 16.dp),
                    containerColor = containerColor,
                    contentColor = contentColor,
                )
            },
            drawStartIconContainer = false,
            endContent = trailing,
        )
    }
}
/**
 * 带开关的一行：走引擎的 [PreferenceRowSwitch]（EnhancedSwitch 随 switchType 八档，
 * 选中态拇指画勾，液态玻璃档的底色取容器色），整行点击也切换开关；禁用整行 alpha 0.5
 * 由引擎的 changeAlphaWhenDisabled 处理。
 */
@Composable
fun AppSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    headline: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supporting: String? = null,
    icon: ImageVector? = null,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
) {
    PreferenceRowSwitch(
        modifier = modifier,
        title = headline,
        subtitle = supporting,
        enabled = enabled,
        checked = checked,
        onClick = onCheckedChange,
        containerColor = Color.Unspecified,
        drawContainer = false,
        autoShadowElevation = 0.dp,
        drawStartIconContainer = false,
        startContent = icon?.let { ic ->
            {
                AppIconBadge(
                    icon = ic,
                    modifier = Modifier.padding(end = 16.dp),
                    containerColor = containerColor,
                    contentColor = contentColor,
                )
            }
        },
    )
}