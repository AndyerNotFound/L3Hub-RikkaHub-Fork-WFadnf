package me.rerere.ai.ui

/** 各家表示「输出没写完」的 finishReason：OpenAI length、Claude max_tokens、Gemini MAX_TOKENS、Responses incomplete */
val TRUNCATED_FINISH_REASONS = setOf("length", "max_tokens", "max_output_tokens", "incomplete")

/** 该 finishReason 是否表示输出被截断 */
fun String?.isTruncatedOutput(): Boolean = this?.lowercase() in TRUNCATED_FINISH_REASONS
