package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一轮模型指纹检测的摘要，含模型、提供商的标签快照及排名信息。
 *
 * 记录里存的是检测当时看到的标签与结论，不引用 provider/model 的外键：模型和提供商随时可能被改名或
 * 删除，历史记录仍然要能独立看懂。
 *
 * 所有非空列都没有 SQL DEFAULT：建表语句与 Room 生成的 schema 逐字对应，运行时校验不会因为默认值
 * 不一致而报错。
 */
@Entity(
    tableName = "model_fingerprint_history",
    indices = [Index(value = ["created_at"])]
)
data class ModelFingerprintEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    // 检测发起时的时间戳（毫秒）
    @ColumnInfo("created_at")
    val createdAt: Long,
    // 被测模型的显示标签快照，比如 "gpt-4o"
    @ColumnInfo("model_label")
    val modelLabel: String,
    // 提供商的显示标签快照，比如 "OpenAI"
    @ColumnInfo("provider_label")
    val providerLabel: String,
    // 最终判定的模型标签；无法判定或检测失败时为 null
    @ColumnInfo("result_label")
    val resultLabel: String?,
    // 候选标签、家族、排名分数和可空的校准概率，以 JSON 数组存储
    @ColumnInfo("candidates_json")
    val candidatesJson: String,
    // 最佳候选的库内校准概率，0.0 表示无法给出概率
    @ColumnInfo("confidence")
    val confidence: Double,
    // 检测器版本号，用来区分不同判据下算出的结论
    @ColumnInfo("detector_version")
    val detectorVersion: String,
    // 评分结论为 RANKED、PARTIAL、UNSCORABLE、NOT_LANGUAGE_MODEL，无结论时为 FAILED 或 CANCELLED
    @ColumnInfo("status")
    val status: String,
    // 探测失败、评分失败或取消的状态码，可与部分结果同时出现
    @ColumnInfo("error")
    val error: String?,
)
