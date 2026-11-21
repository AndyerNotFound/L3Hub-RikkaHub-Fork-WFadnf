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
import androidx.compose.material3.SliderColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun M2Slider(
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
    CustomSlider(
        interactionSource = interactionSource,
        enabled = enabled,
        // 源工程这里用一层圆形容器修饰符包住了底、描边与阴影（阴影开关来自全局设置
        // drawSliderShadows）；本仓库删掉那层包装，只留下它内侧的横向 padding。
        modifier = modifier
            .then(
                if (drawContainer) {
                    Modifier.padding(horizontal = 12.dp)
                } else Modifier
            ),
        value = value,
        colors = colors.toCustom(),
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = steps,
        track = {
            CustomSliderDefaults.Track(
                sliderState = it,
                colors = colors.toCustom(),
                trackHeight = 4.dp,
                enabled = enabled
            )
        }
    )
}
