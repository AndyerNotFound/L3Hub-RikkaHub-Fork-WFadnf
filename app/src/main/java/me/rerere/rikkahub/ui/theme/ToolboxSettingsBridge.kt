package me.rerere.rikkahub.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
 * 要么给那边的默认值。阶段 5 接设置时只改这个文件，端口过来的组件一行不用动。
 *
 * 深浅色与 AMOLED 不在这里推：主题层已经算好 [darkTheme] / [amoledDark]，
 * 传进来即可，重复读 SharedPreferences 只会多一处会走岔的真相。
 */
class ToolboxSettingsBridge(
    private val settings: Settings,
    private val darkTheme: Boolean,
    private val amoledDark: Boolean,
) : ToolboxSettings {

    override val isNightMode: Boolean get() = darkTheme

    override val isAmoledMode: Boolean get() = amoledDark

    /** L3Hub 的 AMOLED 是单个开关（[amoledDark]），没有 ImageToolbox 那层「扩展」。 */
    override val isAmoledModeExtended: Boolean get() = false

    /** 阶段 5 接设置：本项目自己生成调色板，不走这边的主题风格。 */
    override val themeStyle: PaletteStyle get() = PaletteStyle.TonalSpot

    /** 阶段 5 接设置 */
    override val themeContrastLevel: Double get() = 0.0

    /** 阶段 5 接设置 */
    override val isInvertThemeColors: Boolean get() = false

    /** 形状：CornerStyle 的七种与 ShapeType 的七个子类一一对上，强度用 cornerScale。 */
    override val shapesType: ShapeType
        get() = settings.displaySetting.let { shapeTypeOf(it.cornerStyle, it.cornerScale) }

    /** 阶段 5 接设置：本项目的图标不带形状容器，用引擎的「无」。 */
    override val iconShape: IconShape? get() = null

    /** 阶段 5 接设置：引擎的描边宽度（边框贴边时用来补一行 offset），本项目没有描边体系。 */
    override val borderWidth: Dp get() = 0.dp

    /** 阶段 5 接设置：这些都是引擎的阴影/描边开关，本项目还没有对应设置，先按「不画」给。 */
    override val drawContainerShadows: Boolean get() = false

    override val drawAppBarShadows: Boolean get() = false

    override val drawButtonShadows: Boolean get() = false

    override val drawFabShadows: Boolean get() = false

    override val drawSwitchShadows: Boolean get() = false

    override val drawSliderShadows: Boolean get() = false

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

    /** 阶段 5 接设置：紧凑选择器布局 */
    override val isCompactSelectorsLayout: Boolean get() = false

    /** 阶段 5 接设置：对话框按钮居中（引擎默认靠右，与本项目现状一致） */
    override val isCenterAlignDialogButtons: Boolean get() = false

    /** 阶段 5 接设置：拖拽条宽度。引擎只在用 EnhancedModalSheetDragHandle 时才读它。 */
    override val dragHandleWidth: Dp get() = 12.dp

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

    /** 阶段 5 接设置：惯性滚动预设 */
    override val flingType: FlingType get() = FlingType.DEFAULT

    /** 阶段 5 接设置：弹窗贴顶/居中/贴底 */
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
