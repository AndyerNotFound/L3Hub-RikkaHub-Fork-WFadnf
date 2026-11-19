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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 数字输入框的草稿状态。
 *
 * 文本不能按 `remember(value) { mutableStateOf(value.toString()) }` 存：用户敲 "0" 或 "007" 时
 * 外部值立刻跟着变，键一变文本就被外部值重写，光标跳回行尾、前导零被吃掉。所以文本单独保存，
 * 另外记两件事：
 * - [external]：上一次见到的外部值，用来判断这次外部变化是不是真的；
 * - [pending] + [expected]：本地这次输入期望外部变成的值。
 *
 * 外部值变成 [expected] 只是自己刚写下去的回声，保留用户输入的原文；其余变化（切模型、点预设、
 * 拖滑杆）才算真的外部变化，覆盖草稿。
 */
internal data class NumberInputDraft(
    val text: String = "",
    val external: Int? = null,
    val pending: Boolean = false,
    val expected: Int? = null,
)

/** 按外部值造一份干净草稿：进入页面、失焦回填都用它 */
internal fun numberInputDraft(value: Int?): NumberInputDraft =
    NumberInputDraft(text = value?.toString() ?: "", external = value)

/** 外部值变化时对齐文本；本地刚提交的那个值不算外部变化 */
internal fun NumberInputDraft.withExternal(value: Int?): NumberInputDraft = when {
    value == external -> this
    pending && value == expected -> copy(external = value, pending = false, expected = null)
    else -> numberInputDraft(value)
}

/**
 * 键入：只留数字并限长，[input] 里其它字符直接丢掉。
 *
 * [accepts] 决定这次输入提不提交：滑杆的可选区间之外先记在框里、不往回弹；
 * [normalize] 决定提交成什么值：门限填 0 等于清空门限；
 * [commitEmpty] 决定清空输入框要不要提交 null（门限要，滑杆不要）。
 */
internal fun NumberInputDraft.withInput(
    input: String,
    maxDigits: Int,
    accepts: (Int) -> Boolean = { true },
    normalize: (Int) -> Int? = { it },
    commitEmpty: Boolean = false,
): NumberInputDraft {
    val digits = input.filter { it.isDigit() }.take(maxDigits)
    val parsed = digits.toIntOrNull()
    return when {
        parsed == null -> NumberInputDraft(
            text = digits,
            external = external,
            pending = commitEmpty,
            expected = null,
        )

        !accepts(parsed) -> NumberInputDraft(text = digits, external = external)
        else -> NumberInputDraft(
            text = digits,
            external = external,
            pending = true,
            expected = normalize(parsed),
        )
    }
}

/**
 * 草稿跟着外部值走：外部值真的变了才对齐文本。
 *
 * 对齐之后 [NumberInputDraft.external] 与 [value] 一致，这行不会再写，不会反复触发重组；
 * 所以别的页面写同一个字段、切模型、点预设都会刷新，而用户自己敲的 007 不会被回声冲掉。
 */
@Composable
internal fun rememberNumberDraft(value: Int?): MutableState<NumberInputDraft> {
    val draft = remember { mutableStateOf(numberInputDraft(value)) }
    if (draft.value.external != value) {
        draft.value = draft.value.withExternal(value)
    }
    return draft
}

/**
 * 数字输入框本体，草稿和提交规则都在这里，门限滑杆和 [NumberSlider] 共用。
 *
 * 只有宽度、后缀、placeholder 这些外观参数不同；[onCommit] 只在确实要提交时调用，
 * 提交 null 表示清空（门限用）。
 */
@Composable
internal fun NumberDraftField(
    draft: MutableState<NumberInputDraft>,
    externalValue: Int?,
    onCommit: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    maxDigits: Int = 7,
    suffix: String = "",
    placeholder: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    accepts: (Int) -> Boolean = { true },
    normalize: (Int) -> Int? = { it },
    commitEmpty: Boolean = false,
    resetOnFocusLost: Boolean = false,
) {
    val current = draft.value
    OutlinedTextField(
        value = current.text,
        onValueChange = { input ->
            val next = draft.value.withInput(
                input = input,
                maxDigits = maxDigits,
                accepts = accepts,
                normalize = normalize,
                commitEmpty = commitEmpty,
            )
            draft.value = next
            if (next.pending) onCommit(next.expected)
        },
        textStyle = MaterialTheme.typography.bodyMedium,
        singleLine = true,
        enabled = enabled,
        isError = isError,
        placeholder = if (placeholder != null) {
            { Text(placeholder) }
        } else {
            null
        },
        modifier = modifier.onFocusChanged { state ->
            // 失焦回到真实值，避免留下一个不合法的草稿
            if (resetOnFocusLost && !state.isFocused) {
                draft.value = numberInputDraft(externalValue)
            }
        },
        suffix = if (suffix.isNotBlank()) {
            { Text(suffix, style = MaterialTheme.typography.labelSmall) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

/**
 * 刻度很多的滑块旁边加一个数字输入框。
 *
 * 可选值超过 32 个时，拖动很难精确落到想要的值，直接输入更快。
 * 输入框自己保存草稿：[valueRange] 之外的数字先记在框里、不往回弹，失焦时再回到真实值。
 * 敲出一个落在区间里的数就算一次提交（会调 [onValueChangeFinished]，不必等松手），
 * 这样打完字直接退出页面也不会丢。
 */
@Composable
fun NumberSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    modifier: Modifier = Modifier,
    suffix: String = "",
    enabled: Boolean = true,
    // 默认每个整数一档；取值范围太大时传 0，免得轨道上画出一片密密麻麻的刻度
    steps: Int = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
    onValueChangeFinished: () -> Unit = {},
) {
    val draft = rememberNumberDraft(value)
    SliderWithNumberField(
        modifier = modifier,
        enabled = enabled,
        suffix = suffix,
        draft = draft,
        externalValue = value,
        accepts = { it in valueRange },
        onCommit = { committed ->
            if (committed != null) {
                onValueChange(committed)
                onValueChangeFinished()
            }
        },
    ) { sliderModifier ->
        Slider(
            value = value.coerceIn(valueRange.first, valueRange.last).toFloat(),
            onValueChange = { raw ->
                onValueChange(raw.roundToInt().coerceIn(valueRange.first, valueRange.last))
            },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = steps,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
            modifier = sliderModifier,
        )
    }
}

/**
 * 比例值的版本：滑杆和输入框都按百分数走（0.45f 显示成 45）。
 *
 * 1% 算一格：40%..100% 就是 61 格，够多到需要输入框；滑杆本身仍是连续拖动的、不画刻度，手感不变。
 */
@Composable
fun PercentNumberSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: () -> Unit = {},
) {
    val percent = (value * 100).roundToInt()
    val minPercent = (valueRange.start * 100).roundToInt()
    val maxPercent = (valueRange.endInclusive * 100).roundToInt()
    val draft = rememberNumberDraft(percent)
    SliderWithNumberField(
        modifier = modifier,
        enabled = enabled,
        suffix = "%",
        draft = draft,
        externalValue = percent,
        maxDigits = 3,
        accepts = { it in minPercent..maxPercent },
        onCommit = { committed ->
            if (committed != null) {
                onValueChange(committed / 100f)
                onValueChangeFinished()
            }
        },
    ) { sliderModifier ->
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
            modifier = sliderModifier,
        )
    }
}

/** 滑杆占满剩下的宽度，输入框固定在最右边，两边都别抢对方的位置。 */
@Composable
private fun SliderWithNumberField(
    modifier: Modifier,
    enabled: Boolean,
    suffix: String,
    draft: MutableState<NumberInputDraft>,
    externalValue: Int?,
    accepts: (Int) -> Boolean,
    onCommit: (Int?) -> Unit,
    maxDigits: Int = 7,
    slider: @Composable (Modifier) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        slider(Modifier.weight(1f))
        NumberDraftField(
            draft = draft,
            externalValue = externalValue,
            onCommit = onCommit,
            modifier = Modifier.width(96.dp),
            maxDigits = maxDigits,
            suffix = suffix,
            enabled = enabled,
            accepts = accepts,
            resetOnFocusLost = true,
        )
    }
}
