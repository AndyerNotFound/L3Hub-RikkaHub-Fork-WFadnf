/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/LICENSE-2.0>.
 */

package com.t8rin.toolbox.ui.settings

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import com.t8rin.dynamic.theme.PaletteStyle
import com.t8rin.toolbox.ui.model.DialogPosition
import com.t8rin.toolbox.ui.model.FlingType
import com.t8rin.toolbox.ui.model.IconShape
import com.t8rin.toolbox.ui.model.ShapeType
import com.t8rin.toolbox.ui.model.SliderType
import com.t8rin.toolbox.ui.model.SwitchType

/**
 * Read-only view of the host app settings consumed by toolbox-ui.
 *
 * Field names mirror ImageToolbox's `UiSettingsState` so that a host
 * implementation can delegate to its own settings model one-to-one.
 */
interface ToolboxSettings {

    val isNightMode: Boolean

    val isAmoledMode: Boolean

    val isAmoledModeExtended: Boolean

    val themeStyle: PaletteStyle

    val themeContrastLevel: Double

    val isInvertThemeColors: Boolean

    val shapesType: ShapeType

    val iconShape: IconShape?

    val borderWidth: Dp

    val drawContainerShadows: Boolean

    val drawAppBarShadows: Boolean

    val shapeByInteractionThrottle: Long

    // 阶段 3（Enhanced 组件系列）新增：字段名与 ImageToolbox UiSettingsState 一致
    val drawButtonShadows: Boolean

    val drawFabShadows: Boolean

    val drawSwitchShadows: Boolean

    val drawSliderShadows: Boolean

    val sheetNestedScrollEnabled: Boolean

    val isCompactSelectorsLayout: Boolean

    val isCenterAlignDialogButtons: Boolean

    val dragHandleWidth: Dp

    val switchType: SwitchType

    val sliderType: SliderType

    val flingType: FlingType

    val dialogPosition: DialogPosition
}

val LocalToolboxSettings = staticCompositionLocalOf<ToolboxSettings> {
    error("ToolboxSettings not provided")
}

/**
 * Adapter for the `copy(...)` call sites in the ported widgets.
 *
 * ImageToolbox's `UiSettingsState` is a data class, so `copy` is free there;
 * [ToolboxSettings] is a read-only view owned by the host, so a full-field
 * defaulted extension mirrors the data-class `copy` semantics. Every ported
 * call site uses named arguments, so new interface fields only need to be
 * appended here (and to [ToolboxSettingsOverride]) without touching call sites.
 */
fun ToolboxSettings.copy(
    isNightMode: Boolean = this.isNightMode,
    isAmoledMode: Boolean = this.isAmoledMode,
    isAmoledModeExtended: Boolean = this.isAmoledModeExtended,
    themeStyle: PaletteStyle = this.themeStyle,
    themeContrastLevel: Double = this.themeContrastLevel,
    isInvertThemeColors: Boolean = this.isInvertThemeColors,
    shapesType: ShapeType = this.shapesType,
    iconShape: IconShape? = this.iconShape,
    borderWidth: Dp = this.borderWidth,
    drawContainerShadows: Boolean = this.drawContainerShadows,
    drawAppBarShadows: Boolean = this.drawAppBarShadows,
    shapeByInteractionThrottle: Long = this.shapeByInteractionThrottle,
    drawButtonShadows: Boolean = this.drawButtonShadows,
    drawFabShadows: Boolean = this.drawFabShadows,
    drawSwitchShadows: Boolean = this.drawSwitchShadows,
    drawSliderShadows: Boolean = this.drawSliderShadows,
    sheetNestedScrollEnabled: Boolean = this.sheetNestedScrollEnabled,
    isCompactSelectorsLayout: Boolean = this.isCompactSelectorsLayout,
    isCenterAlignDialogButtons: Boolean = this.isCenterAlignDialogButtons,
    dragHandleWidth: Dp = this.dragHandleWidth,
    switchType: SwitchType = this.switchType,
    sliderType: SliderType = this.sliderType,
    flingType: FlingType = this.flingType,
    dialogPosition: DialogPosition = this.dialogPosition
): ToolboxSettings = ToolboxSettingsOverride(
    delegate = this,
    isNightMode = isNightMode,
    isAmoledMode = isAmoledMode,
    isAmoledModeExtended = isAmoledModeExtended,
    themeStyle = themeStyle,
    themeContrastLevel = themeContrastLevel,
    isInvertThemeColors = isInvertThemeColors,
    shapesType = shapesType,
    iconShape = iconShape,
    borderWidth = borderWidth,
    drawContainerShadows = drawContainerShadows,
    drawAppBarShadows = drawAppBarShadows,
    shapeByInteractionThrottle = shapeByInteractionThrottle,
    drawButtonShadows = drawButtonShadows,
    drawFabShadows = drawFabShadows,
    drawSwitchShadows = drawSwitchShadows,
    drawSliderShadows = drawSliderShadows,
    sheetNestedScrollEnabled = sheetNestedScrollEnabled,
    isCompactSelectorsLayout = isCompactSelectorsLayout,
    isCenterAlignDialogButtons = isCenterAlignDialogButtons,
    dragHandleWidth = dragHandleWidth,
    switchType = switchType,
    sliderType = sliderType,
    flingType = flingType,
    dialogPosition = dialogPosition
)

private class ToolboxSettingsOverride(
    private val delegate: ToolboxSettings,
    override val isNightMode: Boolean,
    override val isAmoledMode: Boolean,
    override val isAmoledModeExtended: Boolean,
    override val themeStyle: PaletteStyle,
    override val themeContrastLevel: Double,
    override val isInvertThemeColors: Boolean,
    override val shapesType: ShapeType,
    override val iconShape: IconShape?,
    override val borderWidth: Dp,
    override val drawContainerShadows: Boolean,
    override val drawAppBarShadows: Boolean,
    override val shapeByInteractionThrottle: Long,
    override val drawButtonShadows: Boolean,
    override val drawFabShadows: Boolean,
    override val drawSwitchShadows: Boolean,
    override val drawSliderShadows: Boolean,
    override val sheetNestedScrollEnabled: Boolean,
    override val isCompactSelectorsLayout: Boolean,
    override val isCenterAlignDialogButtons: Boolean,
    override val dragHandleWidth: Dp,
    override val switchType: SwitchType,
    override val sliderType: SliderType,
    override val flingType: FlingType,
    override val dialogPosition: DialogPosition
) : ToolboxSettings
