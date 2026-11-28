package me.rerere.rikkahub.ui.pages.chat

import me.rerere.rikkahub.data.model.Conversation
import java.time.LocalDate
import kotlin.uuid.Uuid

/**
 * 侧栏会话列表的一项。
 *
 * 只是一个数据模型：[ChatDrawerVM] 用它把会话按「文件夹 → 置顶 / 周 → 会话」
 * 归好堆，桥接层再把它摊平成 Java 侧的 `SidebarRow`。
 *
 * 渲染在 `NativeSidebarAdapter` 里 —— 原来这里还有一整套 Compose 渲染
 * （`ConversationItem` / `DateHeaderItem` / `CollapsibleSectionItem` 等），
 * 侧栏改成 Java View 之后那些都成了孤儿代码，已删。
 */
sealed class ConversationListItem {

    /** 日期段标题。收起状态由 `ChatDrawerVM.collapsedDates` 持有。 */
    data class DateHeader(
        val date: LocalDate,
        val label: String,
        val sortOrder: ConversationSortOrder = ConversationSortOrder.UPDATE_TIME,
    ) : ConversationListItem()

    /** 「已置顶」段标题。不可折叠。 */
    data object PinnedHeader : ConversationListItem()

    /**
     * 文件夹段标题。
     *
     * @param folderId null 表示「未归类」那一段 —— 它不是真实文件夹，长按不该弹编辑菜单
     */
    data class FolderHeader(
        val folderId: Uuid?,
        val label: String,
    ) : ConversationListItem()

    /** 一条会话。 */
    data class Item(
        val conversation: Conversation,
    ) : ConversationListItem()
}
