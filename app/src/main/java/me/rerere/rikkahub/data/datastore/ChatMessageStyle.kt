package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 消息样式：气泡背景的三档选择。
 *
 * 与全局「背景特效」（BLUR / GLASS / OFF）正交：样式决定气泡的
 * 底色 / 边框 / 文字 / 圆角等视觉参数，特效决定是否在上面叠加模糊与玻璃。
 */
@Serializable
enum class ChatMessageBackgroundStyle {
    @SerialName("default")
    DEFAULT,

    @SerialName("frosted")
    FROSTED,

    @SerialName("solid")
    SOLID,
}

/** 消息样式可定制对象：用户气泡 / 助手气泡 / 思考链卡片。 */
@Serializable
enum class ChatBubbleStyleTarget {
    @SerialName("user")
    USER,

    @SerialName("assistant")
    ASSISTANT,

    @SerialName("thinking_tool_card")
    THINKING_TOOL_CARD,
}

/**
 * 气泡样式覆盖项，移植自 Kelivo 的 `ChatBubbleStyleOverrides`。
 *
 * 每个字段可空：`null` 表示不覆盖，沿用 L3Hub 现有的默认值
 * （主题跟随色、现有圆角与特效半径等）。颜色存 ARGB 整数，
 * 明暗两套分别记忆；透明度与几何参数两档样式共用。
 */
@Serializable
data class ChatBubbleStyleOverrides(
    val backgroundArgbLight: Int? = null,
    val backgroundArgbDark: Int? = null,
    val borderArgbLight: Int? = null,
    val borderArgbDark: Int? = null,
    val textArgbLight: Int? = null,
    val textArgbDark: Int? = null,
    val borderWidth: Float? = null,
    val borderOpacity: Float? = null,
    val cornerRadius: Float? = null,
    val blurSigma: Float? = null,
    val frostedOpacity: Float? = null,
    val solidOpacity: Float? = null,
) {
    /** 全部字段为空：整份覆盖项未设置过，气泡完全走现状。 */
    val isDefault: Boolean
        get() = this == ChatBubbleStyleOverrides()
}

/** 按角色取当前生效的覆盖项。 */
fun DisplaySetting.overridesFor(target: ChatBubbleStyleTarget): ChatBubbleStyleOverrides =
    when (target) {
        ChatBubbleStyleTarget.USER -> userChatBubbleStyleOverrides
        ChatBubbleStyleTarget.ASSISTANT -> assistantChatBubbleStyleOverrides
        ChatBubbleStyleTarget.THINKING_TOOL_CARD -> thinkingToolCardStyleOverrides
    }
