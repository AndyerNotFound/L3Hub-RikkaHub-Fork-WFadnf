package me.rerere.rikkahub.ui.components.ui

import androidx.compose.material3.SliderColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.t8rin.toolbox.ui.widget.enhanced.EnhancedSlider

/**
 * 滑杆样式统一由 ImageToolbox 的 [EnhancedSlider] 按 sliderType 四档分发
 * （Bridge 与旧 LocalSliderType 读的是同一个设置来源），M3 接管与
 * slider/SliderByType 的双轨实现就此退场。
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
    EnhancedSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
    )
}
