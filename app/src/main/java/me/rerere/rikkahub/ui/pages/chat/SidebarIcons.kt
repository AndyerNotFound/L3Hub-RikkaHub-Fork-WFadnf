package me.rerere.rikkahub.ui.pages.chat

import android.graphics.Rect
import android.view.View
import androidx.compose.ui.graphics.vector.ImageVector
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.Favorite
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.ImageToVideo
import me.rerere.hugeicons.stroke.LanguageCircle
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Pin
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sorting01
import me.rerere.hugeicons.stroke.TransactionHistory

/**
 * 侧栏图标 key 到 HugeIcons 的映射。
 *
 * key 由 Java 侧的 `NativeSidebar` / `NativeSidebarAdapter` 决定 —— 那边只传字符串，
 * 因为 HugeIcons 的 pathData 是编译后的 `ImageVector.Builder` 字节码调用序列，
 * Java 侧取不到，只能把画图标这件事交回 Compose。
 *
 * 单独一个文件是因为这份表会随侧栏入口增减而变，与数据摊平、插槽装配不是一类事。
 */
internal fun sidebarIcon(key: String): ImageVector = when (key) {
    // 顶栏三条快捷入口
    "history" -> HugeIcons.TransactionHistory
    "search" -> HugeIcons.Search01
    "media" -> HugeIcons.ImageToVideo
    "translate" -> HugeIcons.LanguageCircle

    // 文件夹条与排序
    "sort" -> HugeIcons.Sorting01
    "folder" -> HugeIcons.Folder01

    // 用户区
    "pencil" -> HugeIcons.PencilEdit01

    // 底部那排圆按钮
    "assistant" -> HugeIcons.LookTop
    "menu" -> HugeIcons.Menu03
    "favorite" -> HugeIcons.Favorite
    "stats" -> HugeIcons.ChartColumn
    "settings" -> HugeIcons.Settings03

    // 会话行与分区标题
    "pin" -> HugeIcons.Pin
    "chevron_down" -> HugeIcons.ArrowDown01
    "chevron_up" -> HugeIcons.ArrowUp01
    "check" -> HugeIcons.CheckmarkCircle02

    // 认不出来的 key 给一个中性图标，不画空白
    else -> HugeIcons.Menu03
}

/** 取一个 View 在窗口里的矩形，给容器变换当起点。 */
internal fun View.windowRect(): Rect {
    val location = IntArray(2)
    getLocationInWindow(location)
    return Rect(location[0], location[1], location[0] + width, location[1] + height)
}
