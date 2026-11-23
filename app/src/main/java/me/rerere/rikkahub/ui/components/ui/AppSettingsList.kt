package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.t8rin.toolbox.ui.utils.provider.SafeLocalContainerColor
import com.t8rin.toolbox.ui.widget.icon_shape.IconShapeContainer
import com.t8rin.toolbox.ui.widget.modifier.ShapeDefaults
import com.t8rin.toolbox.ui.widget.modifier.container
import com.t8rin.toolbox.ui.widget.preferences.PreferenceItem
import com.t8rin.toolbox.ui.widget.preferences.PreferenceItemDefaults
import com.t8rin.toolbox.ui.widget.preferences.PreferenceRow
import com.t8rin.toolbox.ui.widget.preferences.PreferenceRowSwitch

/**
 * ImageToolbox 观感的设置列表。结构对齐源工程：**每行一张独立卡**（行卡底色走引擎的
 * [SafeLocalContainerColor]，组内行间隔 4dp，卡外距 12dp），分组标题在卡外；
 * 行全部是引擎件（[PreferenceItem] / [PreferenceRow] / [PreferenceRowSwitch]）——
 * 按压形状动画、scrollAnchor、触感、图标形状容器（跟随设置里的图标形状）都在引擎内。
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
 * - `SaltTheme.dimens.item / itemIcon / subPadding` → 行内边距 16dp / 图标 24dp / 间距 16dp
 */
@Composable
fun AppSettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/**
 * 行卡：手拼行（滑杆、输入等复杂内容）与引擎行同底同形。横向 16dp、纵向 8dp 是
 * 卡内边距；卡外距 12dp 与引擎行的默认外距一致。
 */
@Composable
fun AppSettingsRow(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .container(
                shape = ShapeDefaults.default,
                color = SafeLocalContainerColor,
                resultPadding = 0.dp,
                autoShadowElevation = 1.dp,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
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

/** 标题在分组外、行在分组里的完整区块。 */
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
 * 一行纯跳转的设置项：图标形状容器（可空，无图标即源工程的「标题 + 说明」行）+ 标题 +
 * 说明，直接走 ImageToolbox 的 [PreferenceItem]；卡底、阴影、外距全走引擎默认。
 * 尾部默认不带图标（源工程分组导航行的样子），调用点可用 [endIcon] 挂自己的图标。
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .then(modifier),
    )
}

/**
 * 圆形/形状底色的图标行头：走引擎的 [IconShapeContainer]，容器形状跟随设置里的
 * 图标形状，尺寸与配色由引擎按主题算（淡 primaryContainer 系）。
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
                tint = contentColor.takeOrElse { LocalContentColor.current },
            )
        },
    )
}

/**
 * 带图标形状容器的一行（跳转 / 长按 / 尾部自定义控件）。
 * 无尾部控件时走 [PreferenceItem]（支持 onLongClick），有尾部控件走 [PreferenceRow]
 * （endContent 收任意 Composable）；两条分支都是引擎行：16sp Medium 标题、12sp 50% 说明、
 * 按压形状动画、scrollAnchor 与触感。applyHorizontalPadding 关掉，卡外距统一自己给
 * 12dp（与 [PreferenceItem] 的默认外距一致），卡内边距交给引擎的 resultModifier。
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .then(modifier),
        )
    } else {
        PreferenceRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .then(modifier),
            title = headline,
            subtitle = supporting,
            onClick = onClick,
            applyHorizontalPadding = false,
            // 对齐源工程 startIcon 重载的做法：图标在场也保持全向 16dp 卡内边距
            resultModifier = Modifier.padding(16.dp),
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .then(modifier),
        title = headline,
        subtitle = supporting,
        enabled = enabled,
        checked = checked,
        onClick = onCheckedChange,
        applyHorizontalPadding = false,
        // 对齐源工程 startIcon 重载的做法：图标在场也保持全向 16dp 卡内边距
        resultModifier = Modifier.padding(16.dp),
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
        drawStartIconContainer = false,
    )
}