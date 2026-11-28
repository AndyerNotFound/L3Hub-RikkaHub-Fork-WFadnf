package me.rerere.rikkahub.ui.pages.chat

import me.rerere.rikkahub.ui.views.chat.SidebarRow
import java.time.LocalDate

/**
 * 把 VM 的分区项摊平成 Java 侧的行模型。
 *
 * Java 的 `NativeSidebarAdapter` 只认 [SidebarRow]：字段全是 String / int / boolean，
 * 不持有 `Conversation`、`LocalDate`、`Uuid` 这些 Kotlin 类型。转换点集中在这里，
 * 别处不再各转一遍。
 *
 * ## 缩进层级
 *
 * 层级靠 `depth` 表达，Adapter 按它加左缩进 —— 不是只靠折叠箭头。
 * 收起时那一层仍然在，看缩进就知道归属：
 *
 * ```
 * 文件夹「未归类」   depth 0
 *   ▾ 置顶           depth 1
 *       会话A        depth 2
 *   ▾ 本周           depth 1
 *       会话B        depth 2
 * 文件夹「工作」     depth 0
 *       会话C        depth 1   ← 没有日期分组时只深一级
 * ```
 *
 * @param checkedIds 多选态下已勾选的会话 id
 */
internal fun buildSidebarRows(
    items: List<ConversationListItem>,
    collapsedDates: Set<LocalDate>,
    collapsedFolders: Set<String>,
    runningIds: Set<String>,
    checkedIds: Set<String>,
    defaultLabel: String,
    pinnedLabel: String,
): List<SidebarRow> {
    val rows = ArrayList<SidebarRow>(items.size)
    // 当前是否处在收起的段里。收起时只跳过它下面的会话，标题本身留着 ——
    // 否则收起来就找不到展开它的入口了
    var skipSection = false
    // 当前所在段的层级：文件夹标题 0，它下面的会话 1；有日期段时日期 1、会话 2
    var sectionDepth = 0

    items.forEach { item ->
        when (item) {
            is ConversationListItem.FolderHeader -> {
                val key = item.folderId?.toString()
                val collapsed = key != null && key in collapsedFolders
                skipSection = collapsed
                sectionDepth = 0
                rows += SidebarRow.folderHeader(
                    "f:" + (key ?: "unfiled"),
                    item.label.ifBlank { defaultLabel },
                    countConversationsInSection(items, item),
                    collapsed,
                    key,
                )
            }

            is ConversationListItem.DateHeader -> {
                val collapsed = item.date in collapsedDates
                skipSection = collapsed
                // 日期段嵌在文件夹段里，所以比文件夹标题深一级
                sectionDepth = 1
                rows += SidebarRow.dateHeader(
                    "d:" + item.date.toString(),
                    item.label,
                    collapsed,
                    item.date.toString(),
                )
            }

            ConversationListItem.PinnedHeader -> {
                skipSection = false
                // 置顶段与日期段同级：都在文件夹段里面
                sectionDepth = 1
                rows += SidebarRow.pinnedHeader(pinnedLabel)
            }

            is ConversationListItem.Item -> {
                if (skipSection) return@forEach
                val conversation = item.conversation
                val id = conversation.id.toString()
                rows += SidebarRow.conversation(
                    id,
                    conversation.title.ifBlank { defaultLabel },
                    conversation.isPinned,
                    id in runningIds,
                    id in checkedIds,
                    // 会话比它所属的标题再深一级
                    sectionDepth + 1,
                )
            }
        }
    }
    return rows
}

/** 数一个文件夹段里有多少条会话：从它后面数起，遇到下一个文件夹标题就停。 */
private fun countConversationsInSection(
    items: List<ConversationListItem>,
    header: ConversationListItem.FolderHeader,
): Int {
    val start = items.indexOf(header)
    if (start < 0) return 0
    var count = 0
    for (i in start + 1 until items.size) {
        when (items[i]) {
            is ConversationListItem.FolderHeader -> return count
            is ConversationListItem.Item -> count++
            else -> Unit
        }
    }
    return count
}
