package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * 助手心跳：每隔一段时间让助手主动说一句。
 *
 * 没选模型时只当闹钟用——到点发一条系统通知，不调用模型；
 * 选了模型就在一个专属会话里把 [prompt] 发给它，回复留在历史里。
 */
@Serializable
data class HeartbeatSetting(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 60,
    val prompt: String = "",
    // 活跃时间段，按小时算，左闭右开；start > end 表示跨夜
    val startHour: Int = 8,
    val endHour: Int = 23,
    // 为空表示不调用模型，只当闹钟
    val modelId: Uuid? = null,
) {
    fun isActiveAt(hour: Int): Boolean = when {
        startHour == endHour -> true
        startHour < endHour -> hour in startHour until endHour
        else -> hour >= startHour || hour < endHour
    }

    fun normalizedIntervalMinutes(): Int = intervalMinutes.coerceIn(1, 24 * 60)
}
