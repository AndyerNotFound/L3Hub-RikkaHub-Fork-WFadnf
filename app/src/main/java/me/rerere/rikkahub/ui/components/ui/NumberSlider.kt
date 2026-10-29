package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 分节很多的滑块旁边加一个数字输入框。
 *
 * 取值范围超过 48 个整数时，拖动很难精确落到想要的值，直接输入更快。
 */
@Composable
fun NumberSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    modifier: Modifier = Modifier,
    suffix: String = "",
    enabled: Boolean = true,
) {
    val steps = (valueRange.last - valueRange.first - 1).coerceAtLeast(0)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Slider(
            value = value.coerceIn(valueRange.first, valueRange.last).toFloat(),
            onValueChange = { raw ->
                onValueChange(raw.roundToInt().coerceIn(valueRange.first, valueRange.last))
            },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = steps,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { input ->
                val digits = input.filter { it.isDigit() }.take(7)
                digits.toIntOrNull()?.let { parsed ->
                    if (parsed in valueRange) onValueChange(parsed)
                }
            },
            modifier = Modifier.width(96.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            enabled = enabled,
            suffix = if (suffix.isNotBlank()) {
                { Text(suffix, style = MaterialTheme.typography.labelSmall) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}
