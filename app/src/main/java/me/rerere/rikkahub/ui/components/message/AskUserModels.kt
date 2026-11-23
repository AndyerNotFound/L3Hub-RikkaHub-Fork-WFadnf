package me.rerere.rikkahub.ui.components.message

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UIMessagePart

/**
 * 一道 ask_user 问题。同时兼容两代 schema：
 * - 旧：`options` 为字符串数组，`selection_type`(text/single/multi)
 * - 新（对齐 DeepSeek Harness）：`options` 为 {label, description} 对象数组，
 *   `header` 短标题，`multi_select` 布尔
 */
internal data class AskUserQuestion(
    val id: String,
    val question: String,
    val header: String? = null,
    val options: List<AskUserOption> = emptyList(),
    val multiSelect: Boolean = false,
)

internal data class AskUserOption(
    val label: String,
    val description: String? = null,
)

/** 任务清单卡里「需回答的问题」提示行 */
data class AskUserPendingSummary(
    val toolCallId: String,
    val title: String,
)

/** 解析工具调用参数里的问题列表；解析失败返回空列表 */
internal fun UIMessagePart.Tool.parseAskUserQuestions(): List<AskUserQuestion> {
    val arguments = inputAsJson()
    return runCatching {
        arguments.jsonObject["questions"]?.jsonArray?.mapNotNull { q ->
            val obj = q.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val question = obj["question"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val options = obj["options"]?.jsonArray?.mapNotNull { item ->
                if (item is JsonPrimitive) {
                    val label = item.contentOrNull ?: return@mapNotNull null
                    AskUserOption(label = label)
                } else {
                    val o = item.jsonObject
                    val label = o["label"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    AskUserOption(
                        label = label,
                        description = o["description"]?.jsonPrimitive?.contentOrNull,
                    )
                }
            } ?: emptyList()
            val multiSelect = when {
                obj["multi_select"]?.jsonPrimitive?.booleanOrNull == true -> true
                obj["selection_type"]?.jsonPrimitive?.contentOrNull == "multi" -> true
                else -> false
            }
            AskUserQuestion(
                id = id,
                question = question,
                header = obj["header"]?.jsonPrimitive?.contentOrNull,
                options = options,
                multiSelect = multiSelect,
            )
        } ?: emptyList()
    }.getOrElse { emptyList() }
}

/**
 * 把已提交的答案 JSON 格式化成单行文本用于消息卡展示。
 * 兼容两种答案形状：
 * - 旧：`{"answers": {"<id>": "文本"}}`
 * - 新：`{"answers": [{"id": "<id>", "selected": ["label"], "custom": "文本"}]}`
 * 未作答（跳过）返回 null。
 */
internal fun formatAskUserAnswer(answerJson: String, questionId: String): String? {
    val answers = runCatching {
        me.rerere.rikkahub.utils.JsonInstant.parseToJsonElement(answerJson).jsonObject["answers"]
    }.getOrNull() ?: return null
    return when (answers) {
        is JsonObject -> answers[questionId]?.jsonPrimitive?.contentOrNull
        is JsonArray -> answers.firstOrNull { item ->
            item.jsonObject["id"]?.jsonPrimitive?.contentOrNull == questionId
        }?.jsonObject?.let { item ->
            val selected = item["selected"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
            val custom = item["custom"]?.jsonPrimitive?.contentOrNull
            when {
                selected.isEmpty() && custom.isNullOrBlank() -> null // 跳过
                selected.isEmpty() -> custom
                custom.isNullOrBlank() -> selected.joinToString(", ")
                else -> (selected + custom).joinToString(", ")
            }
        }
        else -> null
    }
}
