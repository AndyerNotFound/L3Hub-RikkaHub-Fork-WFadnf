package me.rerere.rikkahub.nav

/**
 * 一次返回只走一步：历史还有上一层就弹一格，到底了交回系统。
 *
 * 历史只有一条（[RootComponent.logicalStack]），所以这里只剩一个判据。原来的
 * 右列 / 中列两档随双栈一起消失——屏幕宽窄决定同时看见几页，不决定「这一下该弹哪一列」。
 *
 * 返回键、页面上的返回按钮、预测性返回手势的启用条件都读同一个结果。
 */
sealed interface BackStep {
    /** 历史里还有上一层：弹一格 */
    data object Pop : BackStep

    /** 栈底了：把返回交回系统（回到桌面或结束 Activity） */
    data object ExitApp : BackStep
}

/** @param depth 历史深度，至少为 1 */
fun nextBackStep(depth: Int): BackStep = if (depth > 1) BackStep.Pop else BackStep.ExitApp
