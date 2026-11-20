package com.l3hub.syncproto

import kotlinx.serialization.Serializable

/**
 * 增量同步的第一包：对端有什么，不搬内容。
 *
 * 与 L3Hub 侧 `SyncManifest` 的 JSON 字段一一对应（类名不影响线格式）。数据中心用它比对附件，
 * 只请求自己镜像里缺的那几个文件。
 */
@Serializable
data class PeerManifest(
    val protocol: Int = 0,
    val schemaVersion: Int = 0,
    val capturedAt: Long = 0,
    val conversations: List<ConversationEntry> = emptyList(),
    val tombstones: List<TombstoneEntry> = emptyList(),
    val files: List<FileEntry> = emptyList(),
) {
    @Serializable
    data class ConversationEntry(val id: String, val updatedAt: Long)

    @Serializable
    data class TombstoneEntry(val kind: String, val targetId: String, val deletedAt: Long)

    @Serializable
    data class FileEntry(val path: String, val size: Long)
}
