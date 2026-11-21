// Ported from ImageToolbox (https://github.com/T8RIN/ImageToolbox), Apache-2.0, Copyright (c) T8RIN (Malik Mukhametzyanov).
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

package me.rerere.rikkahub.ui.components.ui.slider

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun M3Slider(
    value: Float,
    enabled: Boolean,
    colors: SliderColors,
    interactionSource: MutableInteractionSource,
    modifier: Modifier,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)?,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    drawContainer: Boolean = true
) {
    // 源工程用 material3 的 rememberSliderState；本仓库没有调用点，无法确认它在 1.5.0-rc01
    // 里的可见性，按 ReasoningPicker.kt 的既有写法直接建 SliderState，外部值用 SideEffect 同步。
    val state = remember { SliderState(value = value, steps = steps, trackRange = valueRange) }
    SideEffect { state.value = value }

    Slider(
        state = state,
        interactionSource = interactionSource,
        enabled = enabled,
        // 源工程这里用一层容器修饰符画了描边、圆角与阴影（形状取小圆角），
        // 阴影开关来自全局设置 drawSliderShadows；本仓库没有那层包装，
        // 只留下它内外两侧的 padding，滑杆不贴边。
        modifier = modifier
            .then(
                if (drawContainer) {
                    Modifier
                        .padding(vertical = 2.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                } else Modifier
            ),
        colors = colors,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
    )
}
