package me.rerere.rikkahub.ui.pages.setting.components

import android.app.TimePickerDialog
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.MAX_PRICE_RULE_EXPRESSION_LENGTH
import me.rerere.ai.provider.PriceRule
import me.rerere.ai.provider.PriceRuleType
import me.rerere.ai.provider.PriceTable
import me.rerere.ai.provider.priceRuleExpressionErrorPosition
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.AppSwitch
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.glassFilterChipColors
import me.rerere.rikkahub.ui.components.ui.glassSegmentedButtonColors
import me.rerere.rikkahub.ui.pages.assistant.detail.SectionAddButton
import me.rerere.rikkahub.ui.pages.assistant.detail.SectionHeader
import me.rerere.rikkahub.ui.terminal.HorizontalDivider
import me.rerere.rikkahub.ui.terminal.IconButton
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import java.util.Locale

/** 模型计费里的变价规则列表：条件 + 命中后替换的完整价目表 */
@Composable
internal fun PriceRuleSettings(
    modelKey: Any,
    rules: List<PriceRule>,
    basePrice: PriceTable,
    unit: String,
    perCallUnit: String,
    onRulesChange: (List<PriceRule>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = stringResource(R.string.setting_provider_page_price_rules),
            description = stringResource(R.string.setting_provider_page_price_rules_desc),
            action = {
                SectionAddButton(
                    onClick = { onRulesChange(rules + newPriceRule(basePrice)) },
                    contentDescription = stringResource(R.string.setting_provider_page_price_rule_add),
                )
            },
        )
        if (rules.isEmpty()) {
            Text(
                text = stringResource(R.string.setting_provider_page_price_rule_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        } else {
            CardGroup {
                rules.forEachIndexed { index, rule ->
                    formItem(
                        headlineContent = {
                            Text(
                                stringResource(
                                    R.string.setting_provider_page_price_rule_title,
                                    index + 1,
                                )
                            )
                        },
                        supportingContent = {
                            Text(
                                text = priceRuleSummary(rule),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                AppSwitch(
                                    checked = rule.enabled,
                                    onCheckedChange = { enabled ->
                                        onRulesChange(rules.replacedAt(index, rule.copy(enabled = enabled)))
                                    },
                                )
                                IconButton(onClick = {
                                    onRulesChange(rules.removedAt(index))
                                }) {
                                    Icon(
                                        imageVector = HugeIcons.Delete01,
                                        contentDescription = stringResource(
                                            R.string.setting_provider_page_price_rule_delete
                                        ),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                    ) {
                        PriceRuleForm(
                            rule = rule,
                            modelKey = modelKey,
                            unit = unit,
                            perCallUnit = perCallUnit,
                            onUpdate = { updated ->
                                onRulesChange(rules.replacedAt(index, updated))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceRuleForm(
    rule: PriceRule,
    modelKey: Any,
    unit: String,
    perCallUnit: String,
    onUpdate: (PriceRule) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            PriceRuleType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = rule.type == type,
                    onClick = { onUpdate(rule.copy(type = type)) },
                    shape = SegmentedButtonDefaults.itemShape(index, PriceRuleType.entries.size),
                    colors = glassSegmentedButtonColors(),
                ) {
                    Text(
                        text = stringResource(type.labelRes()),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        when (rule.type) {
            PriceRuleType.TIME_RANGE -> PriceRuleTimeFields(rule, onUpdate)
            PriceRuleType.CONTEXT_LENGTH -> PriceRuleContextFields(rule, onUpdate)
            PriceRuleType.EXPRESSION -> PriceRuleExpressionField(rule, onUpdate)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.setting_provider_page_price_rule_price),
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.setting_provider_page_price_rule_price_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PriceRulePriceFields(
            rule = rule,
            modelKey = modelKey,
            unit = unit,
            perCallUnit = perCallUnit,
            onUpdate = onUpdate,
        )
    }
}

@Composable
private fun PriceRuleTimeFields(rule: PriceRule, onUpdate: (PriceRule) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimePickerField(
                label = stringResource(R.string.setting_provider_page_price_rule_start),
                minuteOfDay = rule.startMinute,
                modifier = Modifier.weight(1f),
                onPick = { onUpdate(rule.copy(startMinute = it)) },
            )
            TimePickerField(
                label = stringResource(R.string.setting_provider_page_price_rule_end),
                minuteOfDay = rule.endMinute,
                modifier = Modifier.weight(1f),
                onPick = { onUpdate(rule.copy(endMinute = it)) },
            )
        }
        Text(
            text = stringResource(R.string.setting_provider_page_price_rule_time_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimePickerField(
    label: String,
    minuteOfDay: Int,
    modifier: Modifier = Modifier,
    onPick: (Int) -> Unit,
) {
    val context = LocalContext.current
    val safe = wrapMinute(minuteOfDay)
    Surface(
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onPick(hour * 60 + minute) },
                safe / 60,
                safe % 60,
                true,
            ).show()
        },
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatMinute(safe),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun PriceRuleContextFields(rule: PriceRule, onUpdate: (PriceRule) -> Unit) {
    var text by remember(rule.id) {
        mutableStateOf(rule.contextTokens.takeIf { it > 0 }?.toString().orEmpty())
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = rule.contextAtLeast,
                onClick = { onUpdate(rule.copy(contextAtLeast = true)) },
                label = { Text(stringResource(R.string.setting_provider_page_price_rule_context_at_least)) },
                colors = glassFilterChipColors(),
            )
            FilterChip(
                selected = !rule.contextAtLeast,
                onClick = { onUpdate(rule.copy(contextAtLeast = false)) },
                label = { Text(stringResource(R.string.setting_provider_page_price_rule_context_less_than)) },
                colors = glassFilterChipColors(),
            )
        }
        val empty = rule.contextTokens <= 0
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                val filtered = input.filter { it.isDigit() }.take(9)
                text = filtered
                onUpdate(rule.copy(contextTokens = filtered.toIntOrNull() ?: 0))
            },
            label = { Text(stringResource(R.string.setting_provider_page_price_rule_context)) },
            suffix = { Text(stringResource(R.string.setting_provider_page_price_rule_context_tokens)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = empty,
            supportingText = {
                Text(
                    stringResource(
                        if (empty) {
                            R.string.setting_provider_page_price_rule_context_empty
                        } else {
                            R.string.setting_provider_page_price_rule_context_desc
                        }
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PriceRuleExpressionField(rule: PriceRule, onUpdate: (PriceRule) -> Unit) {
    var text by remember(rule.id) { mutableStateOf(rule.expression) }
    val trimmed = text.trim()
    val errorPosition = remember(trimmed) {
        if (trimmed.isEmpty()) null else priceRuleExpressionErrorPosition(trimmed)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                val next = input.take(MAX_PRICE_RULE_EXPRESSION_LENGTH)
                text = next
                onUpdate(rule.copy(expression = next))
            },
            label = { Text(stringResource(R.string.setting_provider_page_price_rule_expression)) },
            placeholder = { Text("hour >= 22 || hour < 8") },
            isError = errorPosition != null,
            supportingText = {
                when {
                    errorPosition != null -> Text(
                        stringResource(
                            R.string.setting_provider_page_price_rule_expression_invalid,
                            errorPosition + 1,
                        )
                    )
                    trimmed.isEmpty() -> Text(
                        stringResource(R.string.setting_provider_page_price_rule_expression_empty)
                    )
                }
            },
            textStyle = LocalTextStyle.current.copy(fontFamily = JetbrainsMono),
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.setting_provider_page_price_rule_expression_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PriceRulePriceFields(
    rule: PriceRule,
    modelKey: Any,
    unit: String,
    perCallUnit: String,
    onUpdate: (PriceRule) -> Unit,
) {
    val key = rule.id.toString()
    val updatePrice: (PriceTable) -> Unit = { price -> onUpdate(rule.copy(price = price)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RulePriceField(
                label = R.string.setting_provider_page_price_input,
                value = rule.price.input,
                unit = unit,
                modelKey = "$modelKey-$key-input",
                modifier = Modifier.weight(1f),
                onValueChange = { updatePrice(rule.price.copy(input = it)) },
            )
            RulePriceField(
                label = R.string.setting_provider_page_price_output,
                value = rule.price.output,
                unit = unit,
                modelKey = "$modelKey-$key-output",
                modifier = Modifier.weight(1f),
                onValueChange = { updatePrice(rule.price.copy(output = it)) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RulePriceField(
                label = R.string.setting_provider_page_price_cache_hit,
                value = rule.price.cacheHit,
                unit = unit,
                modelKey = "$modelKey-$key-cache-hit",
                modifier = Modifier.weight(1f),
                onValueChange = { updatePrice(rule.price.copy(cacheHit = it)) },
            )
            RulePriceField(
                label = R.string.setting_provider_page_price_cache_write,
                value = rule.price.cacheWrite,
                unit = unit,
                modelKey = "$modelKey-$key-cache-write",
                modifier = Modifier.weight(1f),
                onValueChange = { updatePrice(rule.price.copy(cacheWrite = it)) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RulePriceField(
                label = R.string.setting_provider_page_price_cache_read,
                value = rule.price.cacheRead,
                unit = unit,
                modelKey = "$modelKey-$key-cache-read",
                modifier = Modifier.weight(1f),
                onValueChange = { updatePrice(rule.price.copy(cacheRead = it)) },
            )
            RulePriceField(
                label = R.string.setting_provider_page_price_per_call,
                value = rule.price.perCall,
                unit = perCallUnit,
                modelKey = "$modelKey-$key-per-call",
                modifier = Modifier.weight(1f),
                onValueChange = { updatePrice(rule.price.copy(perCall = it)) },
            )
        }
    }
}

@Composable
private fun RulePriceField(
    @StringRes label: Int,
    value: Double,
    unit: String,
    modelKey: Any,
    modifier: Modifier = Modifier,
    onValueChange: (Double) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PricingInput(
            value = value,
            unit = unit,
            modelKey = modelKey,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun priceRuleSummary(rule: PriceRule): String = when (rule.type) {
    PriceRuleType.TIME_RANGE -> {
        val start = wrapMinute(rule.startMinute)
        val end = wrapMinute(rule.endMinute)
        if (start == end) {
            stringResource(R.string.setting_provider_page_price_rule_time_all_day)
        } else {
            "${formatMinute(start)} – ${formatMinute(end)}"
        }
    }
    PriceRuleType.CONTEXT_LENGTH -> if (rule.contextTokens <= 0) {
        stringResource(R.string.setting_provider_page_price_rule_context_empty)
    } else {
        stringResource(
            if (rule.contextAtLeast) {
                R.string.setting_provider_page_price_rule_context_summary_at_least
            } else {
                R.string.setting_provider_page_price_rule_context_summary_less_than
            },
            rule.contextTokens,
        )
    }
    PriceRuleType.EXPRESSION -> rule.expression.trim().ifBlank {
        stringResource(R.string.setting_provider_page_price_rule_expression)
    }
}

@StringRes
private fun PriceRuleType.labelRes(): Int = when (this) {
    PriceRuleType.TIME_RANGE -> R.string.setting_provider_page_price_rule_time
    PriceRuleType.CONTEXT_LENGTH -> R.string.setting_provider_page_price_rule_context
    PriceRuleType.EXPRESSION -> R.string.setting_provider_page_price_rule_expression
}

private fun newPriceRule(basePrice: PriceTable) = PriceRule(
    type = PriceRuleType.TIME_RANGE,
    startMinute = 0,
    endMinute = 8 * 60,
    contextTokens = 200_000,
    price = basePrice,
)

private fun List<PriceRule>.replacedAt(index: Int, rule: PriceRule): List<PriceRule> =
    toMutableList().also { it[index] = rule }

private fun List<PriceRule>.removedAt(index: Int): List<PriceRule> =
    toMutableList().also { it.removeAt(index) }

private fun wrapMinute(minuteOfDay: Int): Int {
    val rest = minuteOfDay % MINUTES_PER_DAY
    return if (rest < 0) rest + MINUTES_PER_DAY else rest
}

private fun formatMinute(minuteOfDay: Int): String {
    val safe = wrapMinute(minuteOfDay)
    return String.format(Locale.ROOT, "%02d:%02d", safe / 60, safe % 60)
}

private const val MINUTES_PER_DAY = 24 * 60
