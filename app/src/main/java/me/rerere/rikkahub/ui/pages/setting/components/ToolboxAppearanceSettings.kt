package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.t8rin.dynamic.theme.ColorBlindType
import com.t8rin.dynamic.theme.PaletteStyle as ToolboxPaletteStyle
import com.t8rin.toolbox.ui.model.IconShape
import com.t8rin.toolbox.ui.widget.enhanced.EnhancedButtonGroup
import com.t8rin.toolbox.ui.widget.enhanced.EnhancedSliderItem
import com.t8rin.toolbox.ui.widget.icon_shape.IconShapeContainer
import kotlin.math.roundToInt
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.ThemeColorTuple
import me.rerere.rikkahub.data.datastore.ToolThemeSetting
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.ColorPickerRow
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.theme.ToolboxIconShapes

/**
 * 主题引擎设置（阶段 5）。挂在「主题设置」页里，全部写进 [ToolThemeSetting]。
 *
 * 控件一律用端口过来的 Enhanced 系列：这一页调的就是引擎自己的那几个参数，
 * 用引擎的控件看手感才一致（滑块、开关、按钮组的阴影/描边/形状都跟着设置走）。
 *
 * 大部分项在「动态取色」开着时不起作用——那一路的颜色由系统从壁纸推，引擎只是转发。
 * 页面上给了一条提示，别让人对着点了没反应的开关怀疑人生。
 */
@Composable
fun ToolboxAppearanceSettings(
    toolTheme: ToolThemeSetting,
    dynamicColor: Boolean,
    onChange: (ToolThemeSetting) -> Unit,
) {
    val styles = ToolboxPaletteStyle.entries
    val colorBlindOptions = listOf<ColorBlindType?>(null) + ColorBlindType.entries
    val iconShapeKey = toolTheme.iconShapeName

    Column(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CardGroup(
            title = { Text(stringResource(R.string.setting_theme_page_engine_title)) },
        ) {
            if (dynamicColor) {
                item("engineDynamicHint") {
                    Text(
                        text = stringResource(R.string.setting_theme_page_engine_dynamic_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            formItem(
                headlineContent = { Text(stringResource(R.string.setting_theme_page_engine_style_title)) },
                supportingContent = { Text(stringResource(R.string.setting_theme_page_engine_style_desc)) },
            ) {
                EnhancedButtonGroup(
                    items = styles.map { stringResource(paletteStyleLabel(it)) },
                    selectedIndex = styles.indexOf(toolTheme.paletteStyle).coerceAtLeast(0),
                    onIndexChange = { index ->
                        styles.getOrNull(index)?.let { style ->
                            onChange(toolTheme.copy(paletteStyle = style))
                        }
                    },
                    isScrollable = true,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            formItem(
                supportingContent = { Text(stringResource(R.string.setting_theme_page_engine_contrast_desc)) },
            ) {
                EnhancedSliderItem(
                    value = toolTheme.themeContrastLevel,
                    title = stringResource(R.string.setting_theme_page_engine_contrast_title),
                    valueRange = 0f..1f,
                    onValueChange = { onChange(toolTheme.copy(themeContrastLevel = it.toDouble())) },
                    // 内部按 0–100 显示，存回去的仍是 0.0–1.0
                    internalStateTransformation = { (it * 100).roundToInt() },
                    valueSuffix = "%",
                    behaveAsContainer = false,
                    topContentPadding = 0.dp,
                )
            }
            formItem(
                headlineContent = { Text(stringResource(R.string.setting_theme_page_engine_color_blind_title)) },
                supportingContent = { Text(stringResource(R.string.setting_theme_page_engine_color_blind_desc)) },
            ) {
                EnhancedButtonGroup(
                    items = colorBlindOptions.map { stringResource(colorBlindLabel(it)) },
                    selectedIndex = colorBlindOptions.indexOf(toolTheme.colorBlindType).coerceAtLeast(0),
                    onIndexChange = { index ->
                        onChange(toolTheme.copy(colorBlindType = colorBlindOptions.getOrNull(index)))
                    },
                    isScrollable = true,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            switchItem(
                checked = toolTheme.isInvertThemeColors,
                onCheckedChange = { onChange(toolTheme.copy(isInvertThemeColors = it)) },
                supportingContent = { Text(stringResource(R.string.setting_theme_page_engine_invert_desc)) },
                headlineContent = { Text(stringResource(R.string.setting_theme_page_engine_invert_title)) },
            )
        }

        CardGroup(
            title = { Text(stringResource(R.string.setting_theme_page_engine_icon_shape_title)) },
        ) {
            formItem(
                supportingContent = { Text(stringResource(R.string.setting_theme_page_engine_icon_shape_desc)) },
            ) {
                IconShapePicker(
                    selectedKey = iconShapeKey,
                    onSelect = { key -> onChange(toolTheme.copy(iconShapeName = key)) },
                )
            }
        }

        CardGroup(
            title = { Text(stringResource(R.string.setting_theme_page_engine_border_width_title)) },
        ) {
            formItem(
                supportingContent = { Text(stringResource(R.string.setting_theme_page_engine_border_width_desc)) },
            ) {
                EnhancedSliderItem(
                    value = toolTheme.uiBorderWidth,
                    title = stringResource(R.string.setting_theme_page_engine_border_width_title),
                    valueRange = 0f..8f,
                    onValueChange = { onChange(toolTheme.copy(uiBorderWidth = it)) },
                    internalStateTransformation = { it.roundToInt() },
                    valueSuffix = " dp",
                    behaveAsContainer = false,
                    topContentPadding = 0.dp,
                )
            }
        }

        CardGroup(
            title = { Text(stringResource(R.string.setting_theme_page_engine_shadows_title)) },
        ) {
            switchItem(
                checked = toolTheme.drawContainerShadows,
                onCheckedChange = { onChange(toolTheme.copy(drawContainerShadows = it)) },
                headlineContent = { Text(stringResource(R.string.setting_theme_shadow_container)) },
            )
            switchItem(
                checked = toolTheme.drawAppBarShadows,
                onCheckedChange = { onChange(toolTheme.copy(drawAppBarShadows = it)) },
                headlineContent = { Text(stringResource(R.string.setting_theme_shadow_app_bar)) },
            )
            switchItem(
                checked = toolTheme.drawButtonShadows,
                onCheckedChange = { onChange(toolTheme.copy(drawButtonShadows = it)) },
                headlineContent = { Text(stringResource(R.string.setting_theme_shadow_button)) },
            )
            switchItem(
                checked = toolTheme.drawFabShadows,
                onCheckedChange = { onChange(toolTheme.copy(drawFabShadows = it)) },
                headlineContent = { Text(stringResource(R.string.setting_theme_shadow_fab)) },
            )
            switchItem(
                checked = toolTheme.drawSwitchShadows,
                onCheckedChange = { onChange(toolTheme.copy(drawSwitchShadows = it)) },
                headlineContent = { Text(stringResource(R.string.setting_theme_shadow_switch)) },
            )
            switchItem(
                checked = toolTheme.drawSliderShadows,
                onCheckedChange = { onChange(toolTheme.copy(drawSliderShadows = it)) },
                headlineContent = { Text(stringResource(R.string.setting_theme_shadow_slider)) },
            )
        }

        CardGroup(
            title = { Text(stringResource(R.string.setting_theme_page_engine_primary_title)) },
        ) {
            formItem(
                supportingContent = { Text(stringResource(R.string.setting_theme_page_engine_primary_desc)) },
            ) {
                ColorPickerRow(
                    // 没设过就显示当前主题的主色，别凭空给一个和界面对不上的颜色
                    color = toolTheme.appColorTuple?.let { Color(it.primary.toInt()) }
                        ?: MaterialTheme.colorScheme.primary,
                    onColorChange = { color ->
                        onChange(
                            toolTheme.copy(
                                appColorTuple = ThemeColorTuple(
                                    primary = color.toArgb().toLong() and 0xFFFFFFFFL,
                                )
                            )
                        )
                    },
                )
            }
            // 有主色时这一行才可点：点一下清掉，颜色回到当前主题的配色
            item(
                onClick = if (toolTheme.appColorTuple != null) {
                    { onChange(toolTheme.copy(appColorTuple = null)) }
                } else null,
                headlineContent = { Text(stringResource(R.string.setting_theme_page_engine_primary_reset)) },
            )
        }
    }
}

/** 51 个形状 + 「无」+「随机」摆一行横向滚动：每格都是引擎自己画的容器，选哪个看到的就是哪个。 */
@Composable
private fun IconShapePicker(
    selectedKey: String?,
    onSelect: (String?) -> Unit,
) {
    val shapes = ToolboxIconShapes.entries
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        item("none") {
            IconShapeOption(
                shape = null,
                label = stringResource(R.string.setting_theme_icon_shape_none),
                selected = selectedKey == null,
                onClick = { onSelect(null) },
            )
        }
        items(shapes.size) { index ->
            val shape = shapes[index]
            val key = ToolboxIconShapes.keyOf(shape)
            IconShapeOption(
                shape = shape,
                label = null,
                selected = key != null && key == selectedKey,
                onClick = { onSelect(key) },
            )
        }
        item("random") {
            IconShapeOption(
                shape = ToolboxIconShapes.random,
                label = stringResource(R.string.setting_theme_icon_shape_random),
                selected = selectedKey == ToolboxIconShapes.keyOf(ToolboxIconShapes.random),
                onClick = { onSelect(ToolboxIconShapes.keyOf(ToolboxIconShapes.random)) },
            )
        }
    }
}

@Composable
private fun IconShapeOption(
    shape: IconShape?,
    label: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val ringColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .border(2.dp, ringColor, RoundedCornerShape(16.dp))
                .padding(6.dp),
        ) {
            IconShapeContainer(
                iconShape = shape,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                // 形状里放一个小圆点：不依赖任何图标资源，形状本身才是要看的东西
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer),
                )
            }
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun paletteStyleLabel(style: ToolboxPaletteStyle): Int = when (style) {
    ToolboxPaletteStyle.TonalSpot -> R.string.setting_theme_style_tonal_spot
    ToolboxPaletteStyle.Neutral -> R.string.setting_theme_style_neutral
    ToolboxPaletteStyle.Vibrant -> R.string.setting_theme_style_vibrant
    ToolboxPaletteStyle.Expressive -> R.string.setting_theme_style_expressive
    ToolboxPaletteStyle.Rainbow -> R.string.setting_theme_style_rainbow
    ToolboxPaletteStyle.FruitSalad -> R.string.setting_theme_style_fruit_salad
    ToolboxPaletteStyle.Monochrome -> R.string.setting_theme_style_monochrome
    ToolboxPaletteStyle.Fidelity -> R.string.setting_theme_style_fidelity
    ToolboxPaletteStyle.Content -> R.string.setting_theme_style_content
}

private fun colorBlindLabel(type: ColorBlindType?): Int = when (type) {
    null -> R.string.setting_theme_color_blind_none
    ColorBlindType.Protanomaly -> R.string.setting_theme_color_blind_protanomaly
    ColorBlindType.Deuteranomaly -> R.string.setting_theme_color_blind_deuteranomaly
    ColorBlindType.Tritanomaly -> R.string.setting_theme_color_blind_tritanomaly
    ColorBlindType.Protanopia -> R.string.setting_theme_color_blind_protanopia
    ColorBlindType.Deuteranopia -> R.string.setting_theme_color_blind_deuteranopia
    ColorBlindType.Tritanopia -> R.string.setting_theme_color_blind_tritanopia
    ColorBlindType.Achromatomaly -> R.string.setting_theme_color_blind_achromatomaly
    ColorBlindType.Achromatopsia -> R.string.setting_theme_color_blind_achromatopsia
}
