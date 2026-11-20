package me.rerere.rikkahub.data.sync.bridge

import android.net.Uri
import me.rerere.rikkahub.data.sync.RestoreItem
import me.rerere.rikkahub.data.sync.RestoreMode

/**
 * 正式版（com.rikkahub.l3hub.wfadnf）与预览版（com.rikkahub.l3hub.wfadnf.debug）之间的进程间协议。
 *
 * 两版包名相同是做不到同装的，能装在一起的就是这两个包名；但它们的签名密钥不同（正式版用 Secrets
 * 里的 l3hub-release.jks，预览版用仓库里的 l3hub-debug.jks），所以 signature 级权限恰好会把要同步的
 * 那一方挡在外面，sharedUserId 同样受签名约束。剩下的办法是按 UID 反查包名：包名全设备唯一，
 * 同 UID 的包还必须全部落在 [PACKAGES] 里，才允许调用。
 *
 * 协议 2 起支持增量：先换清单，再按会话的 update_at 水位与文件的 (路径, 大小) 差异取数据，
 * 只有对端仍是协议 1 的老包时才退回整份归档。两条路最终都落到同一套安装流程：
 * 归档走 [me.rerere.rikkahub.data.sync.BackupManager.stageRestore]，
 * 增量走 [me.rerere.rikkahub.data.sync.PendingDelta]，都在下次启动、Room 打开之前生效。
 */
object SyncBridge {
    const val BASE_PACKAGE = "com.rikkahub.l3hub.wfadnf"

    /** 允许互相同步的包名；同步的前提是两版装在同一台设备上 */
    val PACKAGES = listOf(BASE_PACKAGE, "$BASE_PACKAGE.debug")

    /** 增量协议版本。对端 ping 返回值低于它时退回整份归档 */
    const val PROTOCOL_VERSION = 2

    // 方法
    const val METHOD_PING = "ping"
    const val METHOD_EXPORT = "export"
    const val METHOD_JOB = "job"
    const val METHOD_IMPORT = "import"
    const val METHOD_CLEANUP = "cleanup"
    const val METHOD_MANIFEST = "manifest"
    const val METHOD_EXPORT_DELTA = "exportDelta"
    const val METHOD_IMPORT_DELTA = "importDelta"

    // 参数与返回值的键
    const val KEY_TOKEN = "token"
    const val KEY_STATE = "state"
    const val KEY_SIZE = "size"
    const val KEY_ERROR = "error"
    const val KEY_PACKAGE = "package"
    const val KEY_VERSION_NAME = "versionName"
    const val KEY_VERSION_CODE = "versionCode"
    const val KEY_SCHEMA_VERSION = "schemaVersion"
    const val KEY_PENDING_RESTORE = "pendingRestore"
    const val KEY_PROTOCOL = "protocol"
    const val KEY_INCLUDE_DATABASE = "includeDatabase"
    const val KEY_INCLUDE_FILES = "includeFiles"
    const val KEY_INCLUDE_SETTINGS = "includeSettings"
    const val KEY_SINCE = "since"
    const val KEY_PATHS = "paths"
    const val KEY_ARCHIVE = "archive"
    const val KEY_ITEMS = "items"
    const val KEY_MODE = "mode"
    const val KEY_STAGED = "staged"
    const val KEY_SKIPPED = "skipped"

    // 打包任务的状态
    const val STATE_RUNNING = "running"
    const val STATE_DONE = "done"
    const val STATE_FAILED = "failed"

    fun authority(packageName: String): String = "$packageName.sync"

    fun endpoint(packageName: String): Uri = Uri.parse("content://${authority(packageName)}")

    /** 取走一份已生成的中转文件：整份归档、清单、增量包或按需文件包都用这条路径 */
    fun exportUri(packageName: String, token: String): Uri =
        Uri.parse("content://${authority(packageName)}/export/$token")

    fun encodeItems(items: Set<RestoreItem>): ArrayList<String> =
        ArrayList(items.map { it.name })

    fun decodeItems(names: Collection<String>?): Set<RestoreItem> =
        names.orEmpty()
            .mapNotNull { name -> RestoreItem.entries.firstOrNull { it.name == name } }
            .toSet()

    fun decodeMode(name: String?): RestoreMode =
        RestoreMode.entries.firstOrNull { it.name == name } ?: RestoreMode.MERGE
}
