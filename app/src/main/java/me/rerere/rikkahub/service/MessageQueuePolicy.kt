package me.rerere.rikkahub.service

/**
 * 消息队列关闭时，一次发送该怎么处理。
 *
 * 关掉开关以后不允许有任何用户消息在收件箱里等待：能立刻开一轮就 [Dispatch]，
 * 正在生成或等工具审批就 [Interrupt]（审批中的工具会被标成取消），
 * 只有压缩上下文这种没法安全打断的状态才拒绝。
 */
internal enum class UnqueuedSend {
    /** 现在就能开一轮，立刻派发 */
    Dispatch,

    /** 正在生成或等工具审批：打断这一轮，这一轮收尾后立刻发 */
    Interrupt,

    /** 系统回投（子代理结果）：不是用户输入，忙时留在调用方，不打断当前回复 */
    DeferNotice,

    /** 正在压缩上下文：开不了新一轮，拒绝并说明原因 */
    RefuseCompressing,
}

/**
 * 判定顺序就是优先级：
 *
 * - 系统回投永远不打断正在进行的任何工作，只在完全空闲时投递
 * - 正在生成：直接打断（生成中的自动压缩属于这一轮，一并按打断处理）
 * - 没有生成但正在压缩上下文：这时打断不安全，拒绝
 * - 等工具审批同样算「占着这一轮」：用户这时发消息就是不要这半截了，
 *   排进收件箱并跳过审批（审批中的工具由新一轮开头标成取消）
 * - 剩下就是立刻派发
 */
internal fun decideUnqueuedSend(
    notice: Boolean,
    hasJob: Boolean,
    awaitingApproval: Boolean,
    compressing: Boolean,
): UnqueuedSend = when {
    notice && (hasJob || awaitingApproval || compressing) -> UnqueuedSend.DeferNotice
    notice -> UnqueuedSend.Dispatch
    hasJob -> UnqueuedSend.Interrupt
    compressing -> UnqueuedSend.RefuseCompressing
    awaitingApproval -> UnqueuedSend.Interrupt
    else -> UnqueuedSend.Dispatch
}

/**
 * 收件箱现在能不能派发下一条。
 *
 * 队列打开时，待在等审批的工具仍然算当前这一轮的一部分，先压着。
 * 队列关闭时不一样：收件箱里有用户消息就说明用户已经接着发了，等于放弃那半截，
 * 放行后新一轮开头会把待审批的工具标成取消；只有通知在等时仍然让路。
 */
internal fun canDispatchInbox(
    hasJob: Boolean,
    awaitingApproval: Boolean,
    compressing: Boolean,
    queueEnabled: Boolean,
    hasUserMessage: Boolean,
): Boolean = when {
    hasJob -> false
    compressing -> false
    awaitingApproval && (queueEnabled || !hasUserMessage) -> false
    else -> true
}
