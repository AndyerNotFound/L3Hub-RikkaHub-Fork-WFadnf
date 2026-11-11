package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.JsonElement
import me.rerere.rikkahub.data.model.InjectionPosition

/**
 * 会话详情里展示的「这一轮实际发给模型的请求前缀」快照。
 *
 * 只用于展示，不参与生成；工具和系统提示词按当前会话配置重新解析一次，
 * 和 [ChatRequestFactory.buildStep] 里的前缀保持一致。
 */
data class ChatRequestPreview(
    val systemPrompt: String,
    val tools: List<ChatRequestPreviewTool>,
    val injections: List<ChatRequestPreviewInjection>,
)

data class ChatRequestPreviewTool(
    val name: String,
    val description: String,
    /** 工具参数 schema，已经按 provider 的序列化方式转成 JSON，直接给 JsonTree 用 */
    val parameters: JsonElement?,
    /** 工具追加到系统提示词末尾的说明，可能为空 */
    val instructions: String,
)

data class ChatRequestPreviewInjection(
    val name: String,
    val isLorebook: Boolean,
    val position: InjectionPosition,
    val depth: Int,
    val content: String,
)
