package me.rerere.rikkahub.nav

/**
 * 一次返回只走一步，顺序固定：右列的下钻页 → 中列的上层页 → 交回系统。
 *
 * 返回键、页面上的返回按钮、预测性返回手势的启用条件都读同一个结果，
 * 界面层不再各自判断"该弹哪一列"。
 */
sealed interface BackStep {
    /** 右列还有下钻页：先收右列 */
    data object PopDetail : BackStep

    /** 右列已空：弹中列的上层页 */
    data object PopRoot : BackStep

    /** 两层都到底：把返回交回系统（回到桌面或结束 Activity） */
    data object ExitApp : BackStep
}

/**
 * @param rootDepth 根栈深度，至少为 1
 * @param detailDepth 右列深度，空槽位 [DetailDestination.None] 时也是 1
 */
fun nextBackStep(rootDepth: Int, detailDepth: Int): BackStep = when {
    detailDepth > 1 -> BackStep.PopDetail
    rootDepth > 1 -> BackStep.PopRoot
    else -> BackStep.ExitApp
}
