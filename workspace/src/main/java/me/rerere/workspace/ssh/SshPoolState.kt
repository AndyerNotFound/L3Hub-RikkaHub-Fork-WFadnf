package me.rerere.workspace.ssh

/**
 * SSH 连接池里一个工作区的连接状态，供界面显示：
 * 建连/重试时转圈，连上了绿灯，断开或还没连过是红灯。
 */
enum class SshPoolState {
    CONNECTING,
    RETRYING,
    CONNECTED,
    DISCONNECTED,
}
