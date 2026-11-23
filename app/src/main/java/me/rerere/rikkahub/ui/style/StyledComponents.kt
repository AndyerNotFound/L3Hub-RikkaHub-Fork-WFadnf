package me.rerere.rikkahub.ui.style

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.t8rin.toolbox.ui.settings.LocalToolboxSettings
import com.t8rin.toolbox.ui.theme.outlineVariant
import com.t8rin.toolbox.ui.widget.enhanced.EnhancedButton
import com.t8rin.toolbox.ui.widget.enhanced.EnhancedCheckbox
import com.t8rin.toolbox.ui.widget.enhanced.EnhancedFloatingActionButton
import com.t8rin.toolbox.ui.widget.enhanced.EnhancedIconButton
import com.t8rin.toolbox.ui.widget.enhanced.EnhancedRadioButton
import com.t8rin.toolbox.ui.widget.enhanced.EnhancedSwitch
import com.t8rin.toolbox.ui.widget.enhanced.EnhancedTopAppBar
import com.t8rin.toolbox.ui.widget.enhanced.EnhancedTopAppBarType
import com.t8rin.toolbox.ui.widget.modifier.drawHorizontalStroke
import com.t8rin.toolbox.ui.widget.modifier.container
import me.rerere.rikkahub.ui.components.ui.glassSwitchColors

/**
 * 组件的统一入口：按钮、卡片、顶栏都从这一层进来，调用点只认这里的签名，换实现只改这里。
 *
 * 内部实现：能无损映射的走 ImageToolbox 的 Enhanced 引擎（[EnhancedButton]、
 * [EnhancedIconButton]、[EnhancedTopAppBar]、[container]），映射不了或者会明显改变观感的
 * 保留 Material 转发，理由写在各函数上。公开参数一个都没改，调用方一行不用动。
 *
 * 形状（CornerStyle/cornerScale）已经在 [me.rerere.rikkahub.ui.theme.RikkahubTheme] 里算进
 * MaterialTheme.shapes，所以带 shape 参数的包装照传即可，别让引擎的 AutoCircleShape 顶掉；
 * 不带 shape 参数的（IconButton 一族）交给引擎从 LocalToolboxSettings.shapesType 读。
 */
/**
 * 按钮描边的颜色。
 *
 * 描边宽度不在调用点手里：引擎一律从 `ToolboxSettings.borderWidth` 读，所以这里只能决定颜色。
 * - 调用点给了 `border`：照它的颜色画，宽窄仍按设置走。
 * - 没给：宽度 0 时给 Transparent——引擎画的是 `OutlinedButton`，宽度 0 也可能留一圈 hairline，
 *   颜色不压透明就凭空多一条边；宽度 > 0 时用户要的就是那圈线，颜色取引擎的默认色
 *   （`outlineVariant` 盖在容器色上，和 EnhancedButton 自己的默认参数是同一个表达式），
 *   否则等于画了条看不见的边。
 */
@Composable
private fun buttonBorderColor(border: BorderStroke?, containerColor: Color): Color {
    (border?.brush as? SolidColor)?.value?.let { return it }
    return if (LocalToolboxSettings.current.borderWidth > 0.dp) {
        MaterialTheme.colorScheme.outlineVariant(onTopOf = containerColor)
    } else {
        Color.Transparent
    }
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    // elevation：M3 的 ButtonElevation 是随交互查表的函数，引擎没有对应入口
    // （阴影只认 ToolboxSettings.drawButtonShadows，现阶段 false），这里忽略；
    // 启用态的底色/文字色照传，禁用态由引擎自己算（onSurface 12%/38%），与 M3 填充按钮一致
    EnhancedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = colors.containerColor,
        contentColor = colors.contentColor,
        // 宽度由设置决定，颜色跟着同一个宽度走（见 buttonBorderColor）
        borderColor = buttonBorderColor(border = border, containerColor = colors.containerColor),
        shape = shape,
        contentPadding = contentPadding,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        content = content,
    )
}

/**
 * 文本按钮的身份是透明底 + 主色字。引擎的禁用态会强制铺 onSurface 12% 的灰底，文本按钮
 * 禁用时应当全透明，所以禁用底色显式传 Transparent 压掉；描边宽度与颜色取调用点的
 * BorderStroke（不传即无描边、宽度锁 0，不吃全局描边设置）。elevation 无引擎入口，
 * 文本按钮本就无阴影，忽略。
 */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    EnhancedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = colors.containerColor,
        contentColor = colors.contentColor,
        borderColor = (border?.brush as? SolidColor)?.value ?: Color.Transparent,
        borderWidth = border?.width ?: 0.dp,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = colors.disabledContentColor,
        shape = shape,
        contentPadding = contentPadding,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        content = content,
    )
}

/**
 * 描边是描边按钮的全部身份：宽度与颜色都取调用点的 BorderStroke（不传走 M3 默认，
 * enabled/disabled 各有各的档），不再吃全局描边设置。禁用底色取 M3 defaults（透明）
 * 压掉引擎的灰底；contentColor 的禁用档由 M3 defaults 给。
 */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val resolvedBorder = border ?: ButtonDefaults.outlinedButtonBorder(enabled)
    EnhancedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = colors.containerColor,
        contentColor = colors.contentColor,
        borderColor = (resolvedBorder.brush as? SolidColor)?.value ?: Color.Transparent,
        borderWidth = resolvedBorder.width,
        disabledContainerColor = colors.disabledContainerColor,
        disabledContentColor = colors.disabledContentColor,
        shape = shape,
        contentPadding = contentPadding,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        content = content,
    )
}

@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    // 与 Button 同一套映射：elevation 仍由引擎的 drawButtonShadows 决定
    EnhancedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = colors.containerColor,
        contentColor = colors.contentColor,
        borderColor = buttonBorderColor(border = border, containerColor = colors.containerColor),
        shape = shape,
        contentPadding = contentPadding,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        content = content,
    )
}

@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    // M3 的 IconButton 是透明的：容器色原样传（默认 Transparent），禁用色引擎有专门入口，
    // 能无损映射；描边颜色和按钮同一套规则（宽度 0 时 Transparent 压掉 hairline）
    EnhancedIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = colors.containerColor,
        contentColor = colors.contentColor,
        disabledContainerColor = colors.disabledContainerColor,
        disabledContentColor = colors.disabledContentColor,
        borderColor = buttonBorderColor(border = null, containerColor = colors.containerColor),
        interactionSource = resolvedInteractionSource,
        content = content,
    )
}

@Composable
fun FilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.smallRoundShape,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    // 形状照传（M3 默认是圆），别让引擎的 AutoCircleShape 把 FilledIconButton 的圆改成跟随 CornerStyle
    EnhancedIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = colors.containerColor,
        contentColor = colors.contentColor,
        disabledContainerColor = colors.disabledContainerColor,
        disabledContentColor = colors.disabledContentColor,
        borderColor = buttonBorderColor(border = null, containerColor = colors.containerColor),
        shape = shape,
        interactionSource = resolvedInteractionSource,
        content = content,
    )
}

@Composable
fun FilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.smallRoundShape,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    EnhancedIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = colors.containerColor,
        contentColor = colors.contentColor,
        disabledContainerColor = colors.disabledContainerColor,
        disabledContentColor = colors.disabledContentColor,
        borderColor = buttonBorderColor(border = null, containerColor = colors.containerColor),
        shape = shape,
        interactionSource = resolvedInteractionSource,
        content = content,
    )
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    CardContainer(
        modifier = modifier,
        shape = shape,
        containerColor = colors.containerColor,
        contentColor = colors.contentColor,
        shadowElevation = 0.dp, // CardElevation 全 internal 无法取值；阴影统一走 drawContainerShadows（阶段 5 接设置）
        border = border,
        interactionSource = null,
        enabled = true,
        onClick = null,
        content = content,
    )
}

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    CardContainer(
        modifier = modifier,
        shape = shape,
        containerColor = if (enabled) colors.containerColor else colors.disabledContainerColor,
        contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
        shadowElevation = 0.dp, // CardElevation 全 internal 无法取值；阴影统一走 drawContainerShadows（阶段 5 接设置）
        border = border,
        interactionSource = interactionSource,
        enabled = enabled,
        onClick = onClick,
        content = content,
    )
}

@Composable
fun OutlinedCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.outlinedCardColors(),
    elevation: CardElevation = CardDefaults.outlinedCardElevation(),
    border: BorderStroke = CardDefaults.outlinedCardBorder(),
    content: @Composable ColumnScope.() -> Unit,
) {
    CardContainer(
        modifier = modifier,
        shape = shape,
        containerColor = colors.containerColor,
        contentColor = colors.contentColor,
        shadowElevation = 0.dp, // CardElevation 全 internal 无法取值；阴影统一走 drawContainerShadows（阶段 5 接设置）
        border = border,
        interactionSource = null,
        enabled = true,
        onClick = null,
        content = content,
    )
}

@Composable
fun OutlinedCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.outlinedCardColors(),
    elevation: CardElevation = CardDefaults.outlinedCardElevation(),
    border: BorderStroke = CardDefaults.outlinedCardBorder(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    CardContainer(
        modifier = modifier,
        shape = shape,
        containerColor = if (enabled) colors.containerColor else colors.disabledContainerColor,
        contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
        shadowElevation = 0.dp, // CardElevation 全 internal 无法取值；阴影统一走 drawContainerShadows（阶段 5 接设置）
        border = border,
        interactionSource = interactionSource,
        enabled = enabled,
        onClick = onClick,
        content = content,
    )
}

@Composable
fun ElevatedCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.elevatedShape,
    colors: CardColors = CardDefaults.elevatedCardColors(),
    elevation: CardElevation = CardDefaults.elevatedCardElevation(),
    content: @Composable ColumnScope.() -> Unit,
) {
    CardContainer(
        modifier = modifier,
        shape = shape,
        containerColor = colors.containerColor,
        contentColor = colors.contentColor,
        shadowElevation = 0.dp, // CardElevation 全 internal 无法取值；阴影统一走 drawContainerShadows（阶段 5 接设置）
        border = null,
        interactionSource = null,
        enabled = true,
        onClick = null,
        content = content,
    )
}

/**
 * 三个 Card 的共同实现：底色/形状/描边/阴影走引擎的 [container]，内容用 Column 排。
 *
 * M3 的 Card 是 Surface + Column，这里对齐它的几个可见行为：内容色经 LocalContentColor 下发；
 * 不可点的卡片用空 pointerInput 吞掉点击（M3 Surface 的做法，卡片下面的可点击区域不该被穿透）。
 * 描边宽度能完整保住——[container] 要的是 borderWidth/borderColor，不像按钮那样只收颜色。
 */
@Composable
private fun CardContainer(
    modifier: Modifier,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    shadowElevation: Dp,
    border: BorderStroke?,
    interactionSource: MutableInteractionSource?,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val indication = LocalIndication.current
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Column(
            modifier = modifier
                .container(
                    shape = shape,
                    color = containerColor,
                    // M3 的 Card 没有内边距；引擎默认那 4.dp 是给阴影留的余量，卡片不能要
                    resultPadding = 0.dp,
                    borderWidth = border?.width ?: 0.dp,
                    borderColor = (border?.brush as? SolidColor)?.value ?: Color.Transparent, // BorderStroke 无 .color 属性，从 brush 取
                    // 阴影是否真的画由 ToolboxSettings.drawContainerShadows 定（现阶段 false）；
                    // 照样把 M3 的 elevation 传下去，阶段 5 打开开关就直接生效
                    autoShadowElevation = shadowElevation,
                    // 玻璃卡/半透明底色原样保留，别在引擎里合成到底色上
                    composeColorOnTopOfBackground = false,
                )
                .then(
                    if (onClick != null) {
                        // 没有额外触感反馈：M3 的 Surface(onClick) 本来也不发，行为与改动前一致
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = indication,
                            enabled = enabled,
                            onClick = onClick,
                        )
                    } else {
                        Modifier.pointerInput(Unit) {}
                    }
                ),
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    expandedHeight: Dp = 64.dp,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    // expandedHeight：引擎的 EnhancedTopAppBar 只有 Normal/Center/Medium/Large 四档，
    // 接不住逐 dp 的高度。M3 自己的默认值就是 64.dp，21 个调用点全用默认，忽略。
    // 底部分隔线/顶栏阴影由引擎按 ToolboxSettings.borderWidth / drawAppBarShadows 决定画不画
    // （现阶段 0.dp / false），观感与原来的 M3 顶栏一致，所以那两个参数不必显式关。
    EnhancedTopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = windowInsets,
        colors = colors,
        scrollBehavior = scrollBehavior,
    )
}

/**
 * expressive 弹性大标题走引擎的 LargeFlexible 档：折叠语义与 Large 一致，展开高度由 M3 定，
 * 描边与阴影按设置画，配色原样透传。40 个页面调用点只换 import，参数一字不动。
 */
@Composable
fun LargeFlexibleTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    EnhancedTopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = windowInsets,
        colors = colors,
        scrollBehavior = scrollBehavior,
        type = EnhancedTopAppBarType.LargeFlexible,
    )
}

/**
 * 带副标题的弹性大标题（M3 expressive 的 subtitle 重载）。引擎没有 subtitle 档，
 * 直接用 M3 的 subtitle 版本铺布局，观感补齐引擎的两件事：描边按设置画
 * （[drawHorizontalStroke]），形状随全局轮廓设置走。
 */
@Composable
fun LargeFlexibleTopAppBar(
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    androidx.compose.material3.LargeFlexibleTopAppBar(
        title = title,
        subtitle = subtitle,
        modifier = modifier.drawHorizontalStroke(enabled = true),
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = windowInsets,
        colors = colors,
        scrollBehavior = scrollBehavior,
    )
}

/**
 * 菜单走引擎的 [EnhancedDropdownMenu]：单一实现在 ui 模块的 `me.rerere.ui.style.DropdownMenu`
 * （ui 模块不能反向依赖 app），这里只做转发。DropdownMenuItem 等 item 依旧从 material3
 * 直接取，菜单容器只在 ui 模块那份里换内核。
 */
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    me.rerere.ui.style.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        content = content,
    )
}

/**
 * FAB 走引擎的 [EnhancedFloatingActionButton]：形状由引擎按 FAB 类型与轮廓设置画，
 * M3 的 shape 参数不再透传（当前调用点无人传）；elevation 由引擎的 drawFabShadows 决定。
 * 阴影开关默认关闭时观感与 M3 一致。
 */
@Composable
fun FloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    EnhancedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        content = { content() },
    )
}

@Composable
fun ExtendedFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    EnhancedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        content = content,
    )
}

/**
 * 表达式版弹性 FAB（M3 expressive 的 text/icon/expanded 重载）。图标常驻，
 * 文字随 [expanded] 横向收放，观感走引擎的 [EnhancedFloatingActionButton]。
 */
@Composable
fun ExtendedFloatingActionButton(
    text: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    interactionSource: MutableInteractionSource? = null,
) {
    EnhancedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        content = {
            icon()
            AnimatedVisibility(
                visible = expanded,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut(),
            ) {
                Row {
                    Spacer(modifier = Modifier.width(8.dp))
                    text()
                }
            }
        },
    )
}

@Composable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
    color: Color = DividerDefaults.color,
) {
    androidx.compose.material3.HorizontalDivider(modifier, thickness, color)
}

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    // 未选中的轨道走玻璃底；关闭模糊特效时 glassSwitchColors 交回 M3 默认配色。
    // 引擎按 switchType 八档自绘：MaterialYou/Compose/Pixel/Fluent/HyperOS/OneUI 都吃
    // 这份 SwitchColors，Cupertino/LiquidGlass 用自己的配色档
    colors: SwitchColors = glassSwitchColors(),
    interactionSource: MutableInteractionSource? = null,
) {
    // thumbContent 是「选中态在拇指上画图标」，引擎只收 ImageVector 收不回 Composable；
    // 当前没有调用点用到，仅留兜底转发
    if (thumbContent != null) {
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            thumbContent = thumbContent,
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
        )
    } else {
        EnhancedSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
        )
    }
}

@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    EnhancedCheckbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    )
}

@Composable
fun RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: RadioButtonColors = RadioButtonDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    EnhancedRadioButton(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Button(
    onClick: () -> Unit,
    shapes: ButtonShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        shape = shapes.shape,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TextButton(
    onClick: () -> Unit,
    shapes: ButtonShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        shape = shapes.shape,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    shapes: ButtonShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = shapes.shape,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    shapes: ButtonShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        shape = shapes.shape,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IconButton(
    onClick: () -> Unit,
    shapes: IconButtonShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilledIconButton(
    onClick: () -> Unit,
    shapes: IconButtonShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = IconButtonDefaults.filledShape,
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilledTonalIconButton(
    onClick: () -> Unit,
    shapes: IconButtonShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = IconButtonDefaults.filledShape,
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}
