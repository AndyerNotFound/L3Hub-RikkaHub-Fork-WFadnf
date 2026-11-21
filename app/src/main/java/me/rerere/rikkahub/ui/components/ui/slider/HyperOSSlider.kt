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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SliderColors as M3SliderColors
import androidx.compose.material3.SliderDefaults as M3Defaults

@Composable
fun HyperOSSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    colors: M3SliderColors = M3Defaults.colors(),
    steps: Int = 0,
    drawContainer: Boolean = true
) {
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val sliderColors = colors.toCustom()

    CustomSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        colors = sliderColors,
        interactionSource = interactionSource,
        steps = steps,
        thumb = {},
        track = { sliderState ->
            CustomSliderDefaults.Track(
                colors = sliderColors,
                enabled = enabled,
                sliderState = sliderState,
                trackHeight = 30.dp,
                // 源工程这里用一层圆形（AutoCircleShape）容器修饰符给轨道套了底、描边与阴影
                // （阴影开关来自全局设置 drawSliderShadows）；
                // 删掉那层之后链上没有剩下的 padding，drawContainer 只为与另外三档保持同一签名。
                modifier = Modifier,
                strokeCap = StrokeCap.Butt
            )
        },
        valueRange = valueRange
    )
}
