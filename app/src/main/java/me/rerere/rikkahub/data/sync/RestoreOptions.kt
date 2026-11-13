package me.rerere.rikkahub.data.sync

import kotlinx.serialization.Serializable

/** 恢复备份时可以选择的数据类别，对应备份包里的不同部分。 */
@Serializable
enum class RestoreItem {
    /** 设置：提供商、助手、提示词、界面偏好等，包含在备份的 settings.json 里。 */
    SETTINGS,

    /** 聊天记录：会话、消息、文件夹、收藏等，包含在备份的数据库里。 */
    CHAT_RECORDS,

    /** 资源文件：附件、图片、字体、技能与媒体创作产物。 */
    FILES,
}

/** 恢复方式，对所有选中的数据类别生效。 */
@Serializable
enum class RestoreMode {
    /** 保留当前数据，只补入备份里当前缺少的部分。 */
    MERGE,

    /** 用备份替换所选类别的当前数据。 */
    OVERWRITE,
}

/** 一次恢复所选择的数据类别与方式，会随待恢复目录一起保存到下次启动。 */
@Serializable
data class RestoreOptions(
    val items: Set<RestoreItem> = RestoreItem.entries.toSet(),
    val mode: RestoreMode = RestoreMode.OVERWRITE,
)

/** 实际写进待恢复目录的数据类别；[skipped] 是用户选了但备份里没有、会被跳过的部分。 */
data class RestoreStageResult(
    val staged: Set<RestoreItem>,
    val skipped: Set<RestoreItem>,
)
