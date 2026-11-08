package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** 引导的生效方式 */
@Serializable
enum class GuidanceMode {
    /** 单轮：插进工具间隙一次，随即从收件箱删掉 */
    @SerialName("single")
    SINGLE,

    /** 多轮：每一次工具批次之后都再插一次，直到用户手动取消 */
    @SerialName("repeat")
    REPEAT,
}

/**
 * 一条引导：生成过程中用户追加给模型的指令。
 *
 * 待发出的引导由收件箱持有；插入之后它就作为一条合成的工具调用留在这一轮的助手消息里，
 * 和真实工具结果一样进请求、进落库、进导出。多轮模式会在每一批工具之后各插一条，
 * 所以一条长回合里会反复出现同一段话——这是「持续生效」的代价，取消之后才停。
 *
 * 放在收件箱而不是会话上：待发出的部分只是运行时的控制项，和消息队列一样活在这一轮会话里。
 */
data class Guidance(
    val id: Uuid = Uuid.random(),
    /** 来自预设时的标题，手输的为空 */
    val title: String = "",
    /** 发给模型的原文 */
    val content: String = "",
    val mode: GuidanceMode = GuidanceMode.SINGLE,
) {
    /** 界面上显示的一行字：有标题用标题，没有就用正文 */
    val displayTitle: String get() = title.ifBlank { content.lineSequence().firstOrNull().orEmpty() }
}
