package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.ui.components.AppModalSheet

/**
 * ask_user 问答抽屉：逐题作答。
 *
 * - 问题与选项在滚动区里，自由输入框固定在底部、不随表单滚动；
 * - 选项纵向排列：单选 RadioButton / 多选 Checkbox；
 * - 每题可跳过（跳过 = 清空该题答案，提交后模型收到空 selected、无 custom，与 DeepSeek
 *   Harness 的 skip 语义一致）；全部跳过也允许提交，生成继续；
 * - 上一题 / 下一题逐题导航，底部栏的「提交」常驻可用；
 * - 关闭抽屉不提交：调用方显示「需回答的问题」提示，点击重新打开。
 */
@Composable
fun AskUserSheet(
    tool: UIMessagePart.Tool?,
    onDismissRequest: () -> Unit,
    onSubmit: (toolCallId: String, answerJson: String) -> Unit,
) {
    val questions = remember(tool) { tool?.parseAskUserQuestions() ?: emptyList() }

    // 作答状态按调用 id 记忆：换一个调用就重置，同一个调用重开抽屉草稿还在
    var currentIndex by remember(tool?.toolCallId) { mutableIntStateOf(0) }
    val selected = remember(tool?.toolCallId) { mutableStateMapOf<String, Set<String>>() }
    val custom = remember(tool?.toolCallId) { mutableStateMapOf<String, String>() }
    val skipped = remember(tool?.toolCallId) { mutableStateMapOf<String, Boolean>() }

    fun submit() {
        val t = tool ?: return
        val answers = questions.map { q ->
            buildJsonObject {
                put("id", q.id)
                put(
                    "selected",
                    buildJsonArray {
                        selected[q.id].orEmpty().forEach { add(JsonPrimitive(it)) }
                    }
                )
                custom[q.id]?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let { put("custom", JsonPrimitive(it)) }
            }
        }
        onSubmit(
            t.toolCallId,
            buildJsonObject {
                put(
                    "answers",
                    buildJsonArray { answers.forEach { add(it) } }
                )
            }.toString(),
        )
    }

    fun skipCurrent() {
        val q = questions.getOrNull(currentIndex) ?: return
        if (skipped[q.id] == true) {
            // 取消跳过：恢复作答
            skipped.remove(q.id)
            return
        }
        selected.remove(q.id)
        custom.remove(q.id)
        skipped[q.id] = true
        if (currentIndex < questions.lastIndex) currentIndex++
    }

    AppModalSheet(
        visible = tool != null,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.ask_user_question_title),
        actions = {
            TextButton(onClick = ::submit, enabled = tool != null && questions.isNotEmpty()) {
                Text(stringResource(R.string.ask_user_question_submit))
            }
        },
    ) {
        val current = questions.getOrNull(currentIndex) ?: return@AppModalSheet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 题号 + 分组标题（固定）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.ask_user_question_count, currentIndex + 1, questions.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                current.header?.let { header ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 滚动区：问题文本 + 纵向选项
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = current.question,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (skipped[current.id] == true) {
                    Text(
                        text = stringResource(R.string.ask_user_question_skipped),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                current.options.forEach { option ->
                    val optionSelected = if (current.multiSelect) {
                        option.label in (selected[current.id].orEmpty())
                    } else {
                        selected[current.id]?.contains(option.label) == true
                    }
                    AskUserOptionRow(
                        option = option,
                        selected = optionSelected,
                        multiSelect = current.multiSelect,
                        onClick = {
                            if (current.multiSelect) {
                                val currentSelected = selected[current.id].orEmpty()
                                selected[current.id] = if (option.label in currentSelected) {
                                    currentSelected - option.label
                                } else {
                                    currentSelected + option.label
                                }
                            } else {
                                selected[current.id] = setOf(option.label)
                            }
                            skipped.remove(current.id)
                        },
                    )
                }
            }

            // 底部固定：自由输入，不随表单滚动
            OutlinedTextField(
                value = custom[current.id] ?: "",
                onValueChange = { text ->
                    custom[current.id] = text
                    skipped.remove(current.id)
                },
                placeholder = { Text(stringResource(R.string.ask_user_question_custom_hint)) },
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 4,
            )

            // 底部固定：跳过 / 上一题 / 下一题
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = ::skipCurrent) {
                    Text(
                        stringResource(
                            if (skipped[current.id] == true) {
                                R.string.ask_user_question_unskip
                            } else {
                                R.string.ask_user_question_skip
                            }
                        )
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { currentIndex-- },
                    enabled = currentIndex > 0,
                ) {
                    Text(stringResource(R.string.ask_user_question_previous))
                }
                TextButton(
                    onClick = { currentIndex++ },
                    enabled = currentIndex < questions.lastIndex,
                ) {
                    Text(stringResource(R.string.ask_user_question_next))
                }
            }
        }
    }
}

@Composable
private fun AskUserOptionRow(
    option: AskUserOption,
    selected: Boolean,
    multiSelect: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (multiSelect) {
                // 点击由外层可点击 Surface 统一处理，避免 Checkbox 冒泡触发两次
                Checkbox(checked = selected, onCheckedChange = null)
            } else {
                RadioButton(selected = selected, onClick = null)
            }
            Column(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                option.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
