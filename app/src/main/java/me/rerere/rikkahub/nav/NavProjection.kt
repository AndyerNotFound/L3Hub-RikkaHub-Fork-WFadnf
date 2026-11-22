package me.rerere.rikkahub.nav

import me.rerere.rikkahub.Screen

/**
 * 一条历史，两种铺开。
 *
 * [logical] 是唯一的历史：前进与后退都只改这一条。屏幕宽窄只决定这条历史尾巴上有几页同时
 * 可见，不决定某一页归哪一列。
 *
 * 现在不是这样：根栈与右列栈各持一条过去，哪一页进哪条栈由界面层判断，于是「按几次返回
 * 能退到某一页」取决于这一页当时落在哪条栈里，横屏与竖屏因此对不上。两列各自的 Children
 * 照旧渲染（转场与预测性返回不动），但栈内容从此由 [logical] 派生，不再独立演进。
 *
 * [root] 与 [detail] 都是 [logical] 的切片，不各自持有历史。
 */
data class NavProjection(
    /** 主列承担的那段，栈底在 index 0，至少一页 */
    val root: List<Screen>,
    /** 右列承担的那段，空表示右列不出现；超过一页时右列只显示栈顶，其余留在栈里等返回 */
    val detail: List<Screen>,
) {
    /** 主列最靠后的一页；宽屏铺开时它就是分区首屏，单列时它就是栈顶 */
    val middle: Screen get() = root.last()

    /** 右列这一页；空槽位时与 [middle] 相同，不另造一个 null 路由 */
    val top: Screen get() = detail.lastOrNull() ?: middle

    companion object {

        /**
         * 单列：整条历史归主列，右列不出现。竖屏与窄窗口走这一条，
         * 与 [masterDetail] 用同一份 [logical]，所以转屏不换历史。
         */
        fun single(logical: List<Screen>): NavProjection =
            NavProjection(root = logical, detail = emptyList())

        /**
         * 主从：历史从栈底切到最靠后的分区首屏归主列，其后的页归右列。
         *
         * [isColumnHost] 是「这一页能不能当中列的一级页」，判据留在界面层（各页自己知道
         * 属于哪个分区），这里只按它切一刀。栈顶本身就是分区首屏时右列为空——分区首屏
         * 没有下钻页，右列的槽位就不该占着宽度。
         */
        fun masterDetail(logical: List<Screen>, isColumnHost: (Screen) -> Boolean): NavProjection {
            val host = logical.indexOfLast(isColumnHost)
            if (host < 0) return single(logical)
            return NavProjection(
                root = logical.take(host + 1),
                detail = logical.drop(host + 1),
            )
        }
    }
}
