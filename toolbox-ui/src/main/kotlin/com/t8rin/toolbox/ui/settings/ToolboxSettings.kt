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
import com.t8rin.toolbox.ui.model.IconShape
import com.t8rin.toolbox.ui.model.ShapeType

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
}

val LocalToolboxSettings = staticCompositionLocalOf<ToolboxSettings> {
    error("ToolboxSettings not provided")
}
