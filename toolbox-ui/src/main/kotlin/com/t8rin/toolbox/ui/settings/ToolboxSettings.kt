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
 * Adapter for the single `copy(...)` call site in the ported widgets
 * (EnhancedButtonGroup temporarily overrides `shapesType` for its edge buttons).
 *
 * ImageToolbox's `UiSettingsState` is a data class, so `copy` is free there;
 * [ToolboxSettings] is a read-only view owned by the host, so only the override
 * that the widgets actually use is expressed here.
 */
fun ToolboxSettings.copy(shapesType: ShapeType): ToolboxSettings =
    ToolboxSettingsWithShapesType(this, shapesType)

private class ToolboxSettingsWithShapesType(
    private val delegate: ToolboxSettings,
    override val shapesType: ShapeType
) : ToolboxSettings by delegate
