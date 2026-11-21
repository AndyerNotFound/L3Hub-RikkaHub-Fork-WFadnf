package me.rerere.rikkahub.ui.components.ui

import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.rerere.rikkahub.data.datastore.SliderType
import me.rerere.rikkahub.ui.components.ui.slider.SliderByType
import me.rerere.rikkahub.ui.theme.LocalSliderType

/**
 * 按「滑块类型」分发的滑杆。
 *
 * [SliderType.MATERIAL_YOU]（默认）不接管，仍走 M3 的 `Slider`，与改动前完全一致；
 * 其余三档走 `ui/components/ui/slider` 里从 ImageToolbox 搬过来的实现。
 *
 * 只收单值滑杆的参数：本项目没有区间滑杆（源工程的 `*RangeSlider` 也没搬）。
 */
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors? = null,
) {
    val sliderType = LocalSliderType.current
    if (sliderType == SliderType.MATERIAL_YOU) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = colors ?: SliderDefaults.colors(),
        )
    } else {
        SliderByType(
            sliderType = sliderType,
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = modifier,
            enabled = enabled,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = colors,
        )
    }
}
