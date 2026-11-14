package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 价格输入框自己保存草稿：只按本地值格式化一次，避免逐字符解析导致小数点被吃掉；
 * 换模型或换规则时按 [modelKey] 重建，防止旧文本被写进新的价目。
 */
@Composable
internal fun PricingInput(
    value: Double,
    unit: String,
    modelKey: Any,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier.width(136.dp),
) {
    var text by remember(modelKey) { mutableStateOf(value.toPriceText()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val candidate = input.replace(',', '.')
            if (candidate.length <= 12 && PRICE_INPUT_REGEX.matches(candidate)) {
                text = candidate
                onValueChange(candidate.toDoubleOrNull() ?: 0.0)
            }
        },
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium,
        suffix = {
            Text(
                text = "/$unit",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        },
        placeholder = { Text("0") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
}

private val PRICE_INPUT_REGEX = Regex("""^\d{0,9}(\.\d{0,6})?$""")

private fun Double.toPriceText(): String =
    if (this <= 0) "" else java.math.BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()
