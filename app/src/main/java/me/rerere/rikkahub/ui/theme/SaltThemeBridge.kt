package me.rerere.rikkahub.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.moriafly.salt.ui.SaltColors
import com.moriafly.salt.ui.SaltConfigs
import com.moriafly.salt.ui.SaltDimens
import com.moriafly.salt.ui.SaltDynamicColors
import com.moriafly.salt.ui.SaltShapes
import com.moriafly.salt.ui.SaltTextStyles
import com.moriafly.salt.ui.SaltTheme

/**
 * SaltUI 的主题入口：把外层 MaterialTheme 已经算好的配色 / 字体 / 形状翻成 Salt 的四份配置，
 * 再嵌一层 [SaltTheme]。全站的 Salt 组件（Sheet、Item、RoundedColumn、NestedHeaderLayout…）
 * 都从这里取色，不再每个页面各带一套。
 *
 * 必须放在 MaterialExpressiveTheme 的 content 里面：这里读的是 `MaterialTheme.colorScheme`，
 * 也就是调色板 / 动态色 / AMOLED 压黑之后的那一份；在外层读只会拿到默认值。
 *
 * 为什么 light / dark 两个槽填同一份颜色：[SaltTheme] 按 `configs.isDarkTheme` 二选一，
 * 而深浅在 [RikkahubTheme] 外就已定完（调色板与色彩引擎都只算当前模式的 scheme）。
 * 两份都放当前模式的映射，选哪份都等于当前配色；真做两套并存要多跑一次色彩引擎，没有收益。
 */
@Composable
fun SaltThemeHost(
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes

    // SaltUI 3.0.0 默认用黑色 AlphaIndication，会覆盖外层 M3 的涟漪，深色下几乎不可见。
    // 沿用 MaterialTheme 提供的 indication，让 Salt 组件和自定义 clickable 都保持 M3 点按反馈。
    val indication = LocalIndication.current
    val configs = remember(isDark, indication) {
        SaltConfigs.default(isDarkTheme = isDark, indication = indication)
    }
    val saltColors = remember(colorScheme, isDark) { colorScheme.toSaltColors(isDark) }
    val dynamicColors = remember(saltColors) {
        SaltDynamicColors(light = saltColors, dark = saltColors)
    }
    val textStyles = remember(typography) {
        SaltTextStyles(
            // main 与 M3 的 bodyLarge 取同一档：SaltTheme 会把正文样式下发成 LocalTextStyle，
            // 换成别的档位等于顺手改掉整棵子树里所有没写 style 的 M3 Text
            main = typography.bodyLarge,
            sub = typography.bodyMedium,
            paragraph = typography.bodyLarge,
            largeTitle = typography.headlineSmall,
        )
    }
    val dimens = remember { SaltDimens.default() }
    val saltShapes = remember(shapes) {
        // 形状直接接本项目的轮廓设置：角型、圆角大小、全宽直角都已经体现在 shapes 里
        SaltShapes(small = shapes.small, medium = shapes.medium, large = shapes.large)
    }

    SaltTheme(
        configs = configs,
        dynamicColors = dynamicColors,
        textStyles = textStyles,
        dimens = dimens,
        shapes = saltShapes,
        content = content,
    )
}

/**
 * 从 M3 角色表里挑出 Salt 需要的 11 个颜色。
 *
 * Salt 是扁平配色（11 格），M3 是角色化配色（几十格、带 Container 阶梯），
 * 只能取最接近的一档：卡片与列表底用 `surfaceContainer`，菜单与弹窗用 `surfaceContainerHigh`。
 * success / warning 在 M3 里没有对应角色，沿用 Salt 自带的两格（按深浅取不同基准）。
 */
private fun ColorScheme.toSaltColors(isDark: Boolean): SaltColors {
    val base = if (isDark) SaltColors.defaultDark() else SaltColors.defaultLight()
    return base.copy(
        highlight = primary,
        text = onBackground,
        subText = onSurfaceVariant,
        background = background,
        subBackground = surfaceContainer,
        popup = surfaceContainerHigh,
        stroke = outlineVariant,
        onHighlight = onPrimary,
        error = error,
    )
}
