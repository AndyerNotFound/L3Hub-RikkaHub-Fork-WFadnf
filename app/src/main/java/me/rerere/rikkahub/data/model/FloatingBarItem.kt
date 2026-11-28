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

/**
 * 悬浮栏最多能放下的槽位数。
 *
 * 7 个是可用宽度的上限：100% 缩放下 7 × 44dp + 内边距 = 316dp，
 * 360dp 屏刚好放得下（再宽就要横向滚动了）。调大缩放时会自动滚动。
 */
const val MAX_FLOATING_BAR_ITEMS = 7

/**
 * 默认槽位：装满 7 个。
 *
 * 顺序按「从输入框往外」的常用度排：会话列表、历史、搜索、助手、收藏、
 * 翻译、设置。用户可以在设置页里增删和调序。
 */
val DEFAULT_FLOATING_BAR_ITEMS: List<FloatingBarItem> = listOf(
    FloatingBarItem.CHAT,
    FloatingBarItem.HISTORY,
    FloatingBarItem.SEARCH,
    FloatingBarItem.ASSISTANT,
    FloatingBarItem.FAVORITE,
    FloatingBarItem.TRANSLATOR,
    FloatingBarItem.SETTINGS,
)
