package com.l3hub.syncproto

/**
 * L3Hub 各构建之间、以及数据中心与 L3Hub 之间的进程间协议。
 *
 * 协议走 ContentProvider：每个构建暴露 `<包名>.sync`，方法与参数键名都定义在这里。
 * 正式版、Canary、Nightly 的签名密钥不同，signature 级权限会把要同步的一方挡在外面，
 * 因此接收端按 UID 反查包名做校验；允许的调用方是各构建加数据中心（[DATA_CENTER_PACKAGE]）。
 *
 * 协议 2 起有增量（清单 / 增量包）与整份归档两条路；协议只增不改，新增的方法对旧构建
 * 表现为「调用失败」，调用方据此降级。
 */
object L3HubSyncProtocol {
    /** 正式版包名；Canary 与 Nightly 在它后面加后缀 */
    const val BASE_PACKAGE = "com.rikkahub.l3hub.wfadnf"

    /** 参与互相同步的构建；顺序即界面上的展示顺序 */
    val BUILD_PACKAGES = listOf(
        BASE_PACKAGE,
        "$BASE_PACKAGE.canary",
        "$BASE_PACKAGE.debug",
    )

    /** 数据中心包名；它不属于 L3Hub 构建，只允许读快照与投递数据 */
    const val DATA_CENTER_PACKAGE = "com.l3hub.database.wfadnf"

    /** 增量协议版本。对端 ping 返回值低于它时退回整份归档 */
    const val PROTOCOL_VERSION = 2

    /** 分层备份包协议版本：`exportBundle` 与接受 `.l3b` 的 `importDelta` 从这个版本开始 */
    const val BUNDLE_PROTOCOL = 3

    // 方法
    const val METHOD_PING = "ping"
    const val METHOD_EXPORT = "export"
    const val METHOD_JOB = "job"
    const val METHOD_IMPORT = "import"
    const val METHOD_CLEANUP = "cleanup"
    const val METHOD_MANIFEST = "manifest"
    const val METHOD_EXPORT_DELTA = "exportDelta"
    const val METHOD_IMPORT_DELTA = "importDelta"

    /** 协议 2 之后新增：导出当前库的结构与统计，供数据中心做跨版本换算 */
    const val METHOD_SCHEMA = "schema"

    /** 协议 3：导出一层 `.l3b`（base 或 delta）；接收侧仍走 `importDelta`，按文件头识别格式 */
    const val METHOD_EXPORT_BUNDLE = "exportBundle"

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
    const val KEY_BASE = "base"
    const val KEY_MODE = "mode"
    const val KEY_STAGED = "staged"
    const val KEY_SKIPPED = "skipped"

    /** [METHOD_SCHEMA] 的返回值：一段 [PeerSchema] JSON */
    const val KEY_SCHEMA = "schema"

    // 打包任务的状态
    const val STATE_RUNNING = "running"
    const val STATE_DONE = "done"
    const val STATE_FAILED = "failed"

    // 数据类别与恢复方式在线上用枚举名传输，与 L3Hub 的 RestoreItem / RestoreMode 一一对应
    const val ITEM_SETTINGS = "SETTINGS"
    const val ITEM_CHAT_RECORDS = "CHAT_RECORDS"
    const val ITEM_FILES = "FILES"
    const val MODE_MERGE = "MERGE"
    const val MODE_OVERWRITE = "OVERWRITE"

    /** 同步 provider 的 authority */
    fun authority(packageName: String): String = "$packageName.sync"

    fun endpoint(packageName: String): String = "content://${authority(packageName)}"

    /** 取走一份已生成的中转文件：整份归档、清单、增量包或按需文件包都用这条路径 */
    fun exportUri(packageName: String, token: String): String =
        "content://${authority(packageName)}/export/$token"
}
