package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable

/**
 * 输入框下方悬浮栏的一个图标槽位。
 *
 * 槽位只表示「去哪里」，具体图标与文案由界面层映射；设置里按列表顺序排列，
 * 顺序就是悬浮栏上的显示顺序。
 */
@Serializable
enum class FloatingBarItem {
    /** 打开会话列表 */
    CHAT,

    /** 媒体创作 */
    MEDIA_CREATION,

    /** 翻译 */
    TRANSLATOR,

    /** 助手 */
    ASSISTANT,

    /** 收藏 */
    FAVORITE,

    /** 历史记录 */
    HISTORY,

    /** 用量统计 */
    STATS,

    /** 图像生成 */
    IMAGE_GEN,

    /** 工作区设置 */
    WORKSPACES,

    /** 工作区终端 */
    TERMINAL,

    /** 搜索会话 */
    SEARCH,

    /** 设置 */
    SETTINGS,
}

/** 悬浮栏最多能放下的槽位数。 */
const val MAX_FLOATING_BAR_ITEMS = 5

/** 默认槽位：聊天与媒体创作。 */
val DEFAULT_FLOATING_BAR_ITEMS: List<FloatingBarItem> = listOf(
    FloatingBarItem.CHAT,
    FloatingBarItem.MEDIA_CREATION,
)
