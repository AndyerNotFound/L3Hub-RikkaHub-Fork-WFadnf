package me.rerere.rikkahub.ui.theme

import androidx.compose.ui.unit.Dp
import com.t8rin.dynamic.theme.PaletteStyle
import com.t8rin.toolbox.ui.model.DialogPosition
import com.t8rin.toolbox.ui.model.FlingType
import com.t8rin.toolbox.ui.model.IconShape
import com.t8rin.toolbox.ui.model.ShapeType
import com.t8rin.toolbox.ui.model.SliderType as ToolboxSliderType
import com.t8rin.toolbox.ui.model.SwitchType as ToolboxSwitchType
import com.t8rin.toolbox.ui.settings.ToolboxSettings
import me.rerere.rikkahub.data.datastore.CornerStyle
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SliderType
import me.rerere.rikkahub.data.datastore.SwitchType

/**
 * 把 L3Hub 的设置接到 ImageToolbox 的 [ToolboxSettings] 上。
 *
 * 引擎（[com.t8rin.toolbox.ui.widget.enhanced.EnhancedAlertDialog]、
 * [com.t8rin.toolbox.ui.widget.enhanced.EnhancedModalBottomSheet] 等）读的是这一份只读视图，
 * 字段名与 ImageToolbox 的 `UiSettingsState` 一模一样，所以每个字段要么映射到本项目已有的设置，
 * 要么给那边的默认值。接设置只改这个文件，端口过来的组件一行不用动。
 *
 * 深浅色与 AMOLED 不在这里推：主题层已经算好 [darkTheme] / [amoledDark]，
 * 传进来即可，重复读 SharedPreferences 只会多一处会走岔的真相。
 */
class ToolboxSettingsBridge(
    private val settings: Settings,
    private val darkTheme: Boolean,
    private val amoledDark: Boolean,
) : ToolboxSettings {

    /** 主题引擎那一份设置（配色风格/对比度/反色/图标形状/描边/阴影/拖拽条）：下面这些字段都从这里读。 */
    private val toolTheme = settings.toolTheme

    override val isNightMode: Boolean get() = darkTheme

    override val isAmoledMode: Boolean get() = amoledDark

    /** L3Hub 的 AMOLED 是单个开关（[amoledDark]），没有 ImageToolbox 那层「扩展」。 */
    override val isAmoledModeExtended: Boolean get() = false

    /** 配色风格：引擎用它把种子色推开成整套 48 个角色。 */
    override val themeStyle: PaletteStyle get() = toolTheme.paletteStyle

    /** 对比度 0.0–1.0，0 是引擎默认档 */
    override val themeContrastLevel: Double get() = toolTheme.themeContrastLevel

    /** 反色：和 RikkahubTheme 传给引擎的是同一个字段，两处必须是同一个值 */
    override val isInvertThemeColors: Boolean get() = toolTheme.isInvertThemeColors

    /** 形状：CornerStyle 的七种与 ShapeType 的七个子类一一对上，强度用 cornerScale。 */
    override val shapesType: ShapeType
        get() = settings.displaySetting.let { shapeTypeOf(it.cornerStyle, it.cornerScale) }

    /**
     * 图标形状：设置里存的是位置键（见 [ToolboxIconShapes]），这里换成引擎的实例。
     * 选择器还没接到设置页，未设置（null）时给引擎的出厂默认（列表第 0 项 = Squircle，
     * 对应 ImageToolbox 的 iconShape = 0）——不然图标容器整个不画，设置行少了那块
     * 招牌底。接出选择器之后要区分「显式无」与「未设置」，再加一个键。
     */
    override val iconShape: IconShape? get() = ToolboxIconShapes.resolve(toolTheme.iconShapeName)
        ?: IconShape.entriesNoRandom.first()

    /** 组件描边宽度：0 表示不描边；引擎据此决定按钮/卡片要不要画那圈线。 */
    override val borderWidth: Dp get() = toolTheme.uiBorderWidthDp

    /** 引擎的六项阴影开关：默认全关（和接设置之前一致），开哪一项就在设置页里勾哪一项。 */
    override val drawContainerShadows: Boolean get() = toolTheme.drawContainerShadows

    override val drawAppBarShadows: Boolean get() = toolTheme.drawAppBarShadows

    override val drawButtonShadows: Boolean get() = toolTheme.drawButtonShadows

    override val drawFabShadows: Boolean get() = toolTheme.drawFabShadows

    override val drawSwitchShadows: Boolean get() = toolTheme.drawSwitchShadows

    override val drawSliderShadows: Boolean get() = toolTheme.drawSliderShadows

    /** 形状动画的节流：本项目的 cornerAnimThrottle 是同一个意思，直接映射。 */
    override val shapeByInteractionThrottle: Long
        get() = settings.displaySetting.cornerAnimThrottle.coerceIn(0, 2000).toLong()

    /**
     * 抽屉的嵌套滚动：跟 M3 `ModalBottomSheet` 的现状一致（内容滚到顶继续下拉能把抽屉带走）。
     *
     * ImageToolbox 的默认值是 false（只有拖拽条能拖），照抄会让列表型抽屉失去下拉关闭——
     * 本项目 29 个调用点里绝大多数内容是列表，这个默认值不能跟着换。
     */
    override val sheetNestedScrollEnabled: Boolean get() = true

    /** 固定值：本项目的滑块/选择器一直用宽松布局，没有紧凑档 */
    override val isCompactSelectorsLayout: Boolean get() = false

    /** 固定值：靠右——引擎的默认，也是本项目一直以来的样子 */
    override val isCenterAlignDialogButtons: Boolean get() = false

    /** 拖拽条宽度：引擎只在用 EnhancedModalSheetDragHandle 时才读它。 */
    override val dragHandleWidth: Dp get() = toolTheme.dragHandleWidthDp

    override val switchType: ToolboxSwitchType
        get() = when (settings.displaySetting.switchType) {
            SwitchType.MATERIAL_YOU -> ToolboxSwitchType.MaterialYou
            SwitchType.COMPOSE -> ToolboxSwitchType.Compose
            SwitchType.PIXEL -> ToolboxSwitchType.Pixel
            SwitchType.FLUENT -> ToolboxSwitchType.Fluent
            SwitchType.CUPERTINO -> ToolboxSwitchType.Cupertino
            SwitchType.LIQUID_GLASS -> ToolboxSwitchType.LiquidGlass
            SwitchType.HYPEROS -> ToolboxSwitchType.HyperOS
            SwitchType.ONE_UI -> ToolboxSwitchType.OneUI
        }

    override val sliderType: ToolboxSliderType
        get() = when (settings.displaySetting.sliderType) {
            SliderType.MATERIAL_YOU -> ToolboxSliderType.MaterialYou
            SliderType.FANCY -> ToolboxSliderType.Fancy
            SliderType.MATERIAL -> ToolboxSliderType.Material
            SliderType.HYPEROS -> ToolboxSliderType.HyperOS
        }

    /** 固定值：引擎默认的惯性曲线，本项目没有惯性档设置 */
    override val flingType: FlingType get() = FlingType.DEFAULT

    /** 固定值：弹窗居中，和接引擎之前的位置一致 */
    override val dialogPosition: DialogPosition get() = DialogPosition.Center
}

/**
 * CornerStyle → ShapeType。
 *
 * 强度只传 cornerScale：Scoop / Notch 自己那 0.65 的收窄在两边都有
 * （本项目是 [CornerStyle.scaleRatio]，引擎是 `ShapeType.scaleRatio`），
 * 在这里再乘一次就变成 0.42 了。
 */
private fun shapeTypeOf(style: CornerStyle, strength: Float): ShapeType = when (style) {
    CornerStyle.ROUNDED -> ShapeType.Rounded(strength)
    CornerStyle.CUT -> ShapeType.Cut(strength)
    CornerStyle.SQUIRCLE -> ShapeType.Squircle(strength)
    CornerStyle.SMOOTH -> ShapeType.Smooth(strength)
    CornerStyle.WAVY -> ShapeType.Wavy(strength)
    CornerStyle.SCOOP -> ShapeType.Scoop(strength)
    CornerStyle.NOTCH -> ShapeType.Notch(strength)
}
