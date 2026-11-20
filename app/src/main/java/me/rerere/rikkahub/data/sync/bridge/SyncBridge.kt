package me.rerere.rikkahub.data.sync.bridge

import android.net.Uri
import com.l3hub.syncproto.L3HubSyncProtocol
import me.rerere.rikkahub.data.sync.RestoreItem
import me.rerere.rikkahub.data.sync.RestoreMode

/**
 * 正式版（com.rikkahub.l3hub.wfadnf）、Canary（.canary）与 Nightly（.debug）之间的进程间协议。
 *
 * 这几个包名可以同时装在一台设备上；但它们的签名密钥不同（正式版与 Canary 用 Secrets 里的
 * l3hub-release.jks，Nightly 用仓库里的 l3hub-debug.jks），所以 signature 级权限恰好会把要同步的
 * 那一方挡在外面，sharedUserId 同样受签名约束。剩下的办法是按 UID 反查包名：包名全设备唯一，
 * 同 UID 的包还必须全部落在 [TRUSTED_CALLERS] 里，才允许调用。
 *
 * 协议 2 起支持增量：先换清单，再按会话的 update_at 水位与文件的 (路径, 大小) 差异取数据，
 * 只有对端仍是协议 1 的老包时才退回整份归档。两条路最终都落到同一套安装流程：归档走
 * [me.rerere.rikkahub.data.sync.BackupManager.stageRestore]，增量走
 * [me.rerere.rikkahub.data.sync.PendingDelta]，都在下次启动、Room 打开之前生效。
 *
 * 常量本身定义在 `:syncproto`，数据中心与本应用共用同一份，避免两边各写一套键名慢慢漂移。
 */
object SyncBridge {
    const val BASE_PACKAGE = L3HubSyncProtocol.BASE_PACKAGE

    /** 允许互相同步的包名；同步的前提是两版装在同一台设备上 */
    val PACKAGES = L3HubSyncProtocol.BUILD_PACKAGES

    /** 允许调用本 provider 的包名：各构建，加上只读快照与投递数据的数据中心 */
    val TRUSTED_CALLERS = L3HubSyncProtocol.BUILD_PACKAGES + L3HubSyncProtocol.DATA_CENTER_PACKAGE

    /** 增量协议版本。对端 ping 返回值低于它时退回整份归档 */
    const val PROTOCOL_VERSION = L3HubSyncProtocol.PROTOCOL_VERSION

    // 方法
    const val METHOD_PING = L3HubSyncProtocol.METHOD_PING
    const val METHOD_EXPORT = L3HubSyncProtocol.METHOD_EXPORT
    const val METHOD_JOB = L3HubSyncProtocol.METHOD_JOB
    const val METHOD_IMPORT = L3HubSyncProtocol.METHOD_IMPORT
    const val METHOD_CLEANUP = L3HubSyncProtocol.METHOD_CLEANUP
    const val METHOD_MANIFEST = L3HubSyncProtocol.METHOD_MANIFEST
    const val METHOD_EXPORT_DELTA = L3HubSyncProtocol.METHOD_EXPORT_DELTA
    const val METHOD_IMPORT_DELTA = L3HubSyncProtocol.METHOD_IMPORT_DELTA
    const val METHOD_SCHEMA = L3HubSyncProtocol.METHOD_SCHEMA
    const val METHOD_EXPORT_BUNDLE = L3HubSyncProtocol.METHOD_EXPORT_BUNDLE

    // 参数与返回值的键
    const val KEY_TOKEN = L3HubSyncProtocol.KEY_TOKEN
    const val KEY_STATE = L3HubSyncProtocol.KEY_STATE
    const val KEY_SIZE = L3HubSyncProtocol.KEY_SIZE
    const val KEY_ERROR = L3HubSyncProtocol.KEY_ERROR
    const val KEY_PACKAGE = L3HubSyncProtocol.KEY_PACKAGE
    const val KEY_VERSION_NAME = L3HubSyncProtocol.KEY_VERSION_NAME
    const val KEY_VERSION_CODE = L3HubSyncProtocol.KEY_VERSION_CODE
    const val KEY_SCHEMA_VERSION = L3HubSyncProtocol.KEY_SCHEMA_VERSION
    const val KEY_PENDING_RESTORE = L3HubSyncProtocol.KEY_PENDING_RESTORE
    const val KEY_PROTOCOL = L3HubSyncProtocol.KEY_PROTOCOL
    const val KEY_INCLUDE_DATABASE = L3HubSyncProtocol.KEY_INCLUDE_DATABASE
    const val KEY_INCLUDE_FILES = L3HubSyncProtocol.KEY_INCLUDE_FILES
    const val KEY_INCLUDE_SETTINGS = L3HubSyncProtocol.KEY_INCLUDE_SETTINGS
    const val KEY_SINCE = L3HubSyncProtocol.KEY_SINCE
    const val KEY_PATHS = L3HubSyncProtocol.KEY_PATHS
    const val KEY_ARCHIVE = L3HubSyncProtocol.KEY_ARCHIVE
    const val KEY_ITEMS = L3HubSyncProtocol.KEY_ITEMS
    const val KEY_MODE = L3HubSyncProtocol.KEY_MODE
    const val KEY_STAGED = L3HubSyncProtocol.KEY_STAGED
    const val KEY_SKIPPED = L3HubSyncProtocol.KEY_SKIPPED
    const val KEY_SCHEMA = L3HubSyncProtocol.KEY_SCHEMA
    const val KEY_BASE = L3HubSyncProtocol.KEY_BASE

    /** 分层备份包从这个协议版本开始支持 */
    const val BUNDLE_PROTOCOL = L3HubSyncProtocol.BUNDLE_PROTOCOL

    /** 对外自报的协议版本：分层包（3）是在增量（2）之上加的，报最高那个即可 */
    const val ADVERTISED_PROTOCOL = L3HubSyncProtocol.BUNDLE_PROTOCOL

    // 打包任务的状态
    const val STATE_RUNNING = L3HubSyncProtocol.STATE_RUNNING
    const val STATE_DONE = L3HubSyncProtocol.STATE_DONE
    const val STATE_FAILED = L3HubSyncProtocol.STATE_FAILED

    fun authority(packageName: String): String = L3HubSyncProtocol.authority(packageName)

    fun endpoint(packageName: String): Uri = Uri.parse(L3HubSyncProtocol.endpoint(packageName))

    /** 取走一份已生成的中转文件：整份归档、清单、增量包或按需文件包都用这条路径 */
    fun exportUri(packageName: String, token: String): Uri =
        Uri.parse(L3HubSyncProtocol.exportUri(packageName, token))

    fun encodeItems(items: Set<RestoreItem>): ArrayList<String> =
        ArrayList(items.map { it.name })

    fun decodeItems(names: Collection<String>?): Set<RestoreItem> =
        names.orEmpty()
            .mapNotNull { name -> RestoreItem.entries.firstOrNull { it.name == name } }
            .toSet()

    fun decodeMode(name: String?): RestoreMode =
        RestoreMode.entries.firstOrNull { it.name == name } ?: RestoreMode.MERGE
}
