package me.rerere.rikkahub.ui.style

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.t8rin.toolbox.ui.widget.enhanced.EnhancedButton
import com.t8rin.toolbox.ui.widget.enhanced.EnhancedIconButton
import com.t8rin.toolbox.ui.widget.enhanced.EnhancedTopAppBar
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
        // 引擎的描边宽度只从 ToolboxSettings.borderWidth 读（现阶段 0.dp），这里只能保住颜色；
        // 没描边的调用点给 Transparent，别让 0.dp 那圈 hairline 冒出来
        borderColor = (border?.brush as? SolidColor)?.value ?: Color.Transparent,
        shape = shape,
        contentPadding = contentPadding,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        content = content,
    )
}

/**
 * 保留 Material 转发。
 *
 * [EnhancedButton] 没有 disabledContainerColor 这类入口：禁用时它自己铺一层 onSurface 12%
 * 的底色。填充按钮本来就是这个禁用色，文本按钮不是——文本按钮禁用时应当全透明。换过去会让
 * 每个加载中的「取消」「确定」多出一块灰底。需要底色的文本按钮另有专门实现
 * （ui/components/ui/shadow/ShadowButtons.kt），不必在这里兼着。
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
    androidx.compose.material3.TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * 保留 Material 转发。
 *
 * 描边是描边按钮的全部身份，而 [EnhancedButton] 的描边宽度写死从 ToolboxSettings.borderWidth
 * 读（现阶段 0.dp），只开放颜色。换过去等于把描边按钮变成文字按钮，属明显观感退化。
 * 等阶段 5 把描边体系接进设置，再回来换成 EnhancedButton（那时 border 用 defaults 里的颜色）。
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
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
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
    // 与 Button 同一套映射：elevation 引擎接不住（现阶段阴影全关），描边只能保颜色
    EnhancedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = colors.containerColor,
        contentColor = colors.contentColor,
        borderColor = (border?.brush as? SolidColor)?.value ?: Color.Transparent,
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
    // 能无损映射（引擎默认会画的描边现在没有宽度，颜色给 Transparent 压掉）
    EnhancedIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = colors.containerColor,
        contentColor = colors.contentColor,
        disabledContainerColor = colors.disabledContainerColor,
        disabledContentColor = colors.disabledContentColor,
        borderColor = Color.Transparent,
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
        borderColor = Color.Transparent,
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
        borderColor = Color.Transparent,
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
    // 未选中的轨道走玻璃底；关闭模糊特效时 glassSwitchColors 交回 M3 默认配色
    colors: SwitchColors = glassSwitchColors(),
    interactionSource: MutableInteractionSource? = null,
) {
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        thumbContent = thumbContent,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    )
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
    androidx.compose.material3.Checkbox(
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
    androidx.compose.material3.RadioButton(
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
