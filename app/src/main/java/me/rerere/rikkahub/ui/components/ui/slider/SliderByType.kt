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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import me.rerere.rikkahub.data.datastore.SliderType
import me.rerere.rikkahub.ui.theme.rememberFixedSpec

/**
 * 按 [SliderType] 分发单值滑杆，取自 ImageToolbox 的 `EnhancedSlider`。
 *
 * 与源工程的差别只有几处管道替换：滑块类型从全局设置改成参数传入，
 * 每一步的触感回调整段删掉（本仓库有自己的触感层），
 * 100ms 的数值过渡改用本仓库的 `rememberFixedSpec`。
 */
@Composable
fun SliderByType(
    sliderType: SliderType,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors? = null,
) {
    // 源工程从全局设置读滑块类型，这里用 sliderType 参数。
    val realColors = colors ?: when (sliderType) {
        SliderType.FANCY -> {
            SliderDefaults.colors(
                activeTickColor = MaterialTheme.colorScheme.inverseSurface,
                inactiveTickColor = MaterialTheme.colorScheme.surface,
                activeTrackColor = MaterialTheme.colorScheme.primaryContainer,
                inactiveTrackColor = SwitchDefaults.colors().disabledCheckedTrackColor,
                disabledThumbColor = SliderDefaults.colors().disabledThumbColor,
                disabledActiveTrackColor = SliderDefaults.colors().disabledActiveTrackColor,
                thumbColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        SliderType.MATERIAL_YOU -> SliderDefaults.colors()
        SliderType.MATERIAL -> SliderDefaults.colors()
        SliderType.HYPEROS -> SliderDefaults.colors()
    }

    val interactionSource = remember { MutableInteractionSource() }

    // 源工程在 steps != 0 时按每条刻度震一下（haptics.press()），本仓库有自己的触感层，
    // 这段 LaunchedEffect 连同它的 compositions 计数一起删掉。
    val value = animateFloatAsState(
        targetValue = value,
        animationSpec = rememberFixedSpec<Float>(100)
    ).value

    when (sliderType) {
        SliderType.FANCY -> {
            FancySlider(
                value = value,
                enabled = enabled,
                colors = realColors,
                interactionSource = interactionSource,
                thumbShape = fancyThumbShape(),
                modifier = modifier,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps
            )
        }

        SliderType.MATERIAL_YOU -> {
            M3Slider(
                value = value,
                enabled = enabled,
                colors = realColors,
                interactionSource = interactionSource,
                modifier = modifier,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps
            )
        }

        SliderType.MATERIAL -> {
            M2Slider(
                value = value,
                enabled = enabled,
                colors = realColors,
                interactionSource = interactionSource,
                modifier = modifier,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps
            )
        }

        SliderType.HYPEROS -> {
            HyperOSSlider(
                value = value,
                enabled = enabled,
                colors = realColors,
                interactionSource = interactionSource,
                modifier = modifier,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps
            )
        }
    }
}
