package me.rerere.rikkahub.data.sync

import android.content.Context
import android.util.Log
import com.l3hub.syncproto.BundleFormat
import me.rerere.rikkahub.data.db.SQLiteConfiguration
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * 把 `.l3b` 的一条链物化到待安装目录里。
 *
 * 结构直接落成 `payload/database/rikka_hub.db`、`payload/files/…` 与 `settings.json`，与整份 zip
 * 恢复解包出来的布局一模一样，收尾工作（完整性校验、Room 迁移、路径改写、SSH 凭据清理）因此可以
 * 与旧格式共用一份实现，也不必为恢复再多写一遍整库的 zip。
 *
 * 顺序固定为链的顺序：base 先整份落盘，后面的 delta 逐层打进这份副本。层的 `user_version` 由写入方
 * 保证与链首一致（结构版本变了就重开 base），对不上时 [SyncDelta] 直接失败，不会留下半份数据。
 */
internal object BundleRestore {
    private const val TAG = "BundleRestore"

    fun materialize(
        context: Context,
        bundle: File,
        chain: List<BundleFormat.Ref>,
        staging: File,
        items: Set<RestoreItem>,
    ): Set<RestoreItem> {
        val payload = File(staging, "payload")
        val databaseFile = File(payload, "database/${SQLiteConfiguration.DATABASE_NAME}")
        val filesRoot = File(payload, "files")
        val settingsFile = File(staging, "settings.json")
        val staged = mutableSetOf<RestoreItem>()
        chain.forEachIndexed { position, ref ->
            val layer = File(staging, "layer-$position.zip")
            try {
                BundleFormat.extractPayload(bundle, ref, layer)
                ZipFile(layer).use { zip ->
                    if (RestoreItem.CHAT_RECORDS in items) {
                        zip.getEntry(BundleFormat.ENTRY_DATABASE)?.let { entry ->
                            require(!databaseFile.exists()) { "Backup bundle carries more than one base layer" }
                            check(databaseFile.parentFile!!.isDirectory || databaseFile.parentFile!!.mkdirs())
                            zip.getInputStream(entry).use { input ->
                                FileOutputStream(databaseFile).use { output ->
                                    input.copyTo(output)
                                    output.fd.sync()
                                }
                            }
                            staged += RestoreItem.CHAT_RECORDS
                        }
                        zip.getEntry(BundleFormat.ENTRY_DELTA)?.let { entry ->
                            require(databaseFile.isFile) { "A delta layer has no base layer to apply to" }
                            val delta = File(staging, "delta-$position.db")
                            try {
                                zip.getInputStream(entry).use { input ->
                                    FileOutputStream(delta).use { output ->
                                        input.copyTo(output)
                                        output.fd.sync()
                                    }
                                }
                                SyncDelta.apply(context, databaseFile, delta)
                                staged += RestoreItem.CHAT_RECORDS
                            } finally {
                                delta.delete()
                            }
                        }
                    }
                    // 设置后面层覆盖前面层，与整份备份里的单份 settings.json 等价
                    if (RestoreItem.SETTINGS in items) {
                        zip.getEntry(BundleFormat.ENTRY_SETTINGS)?.let { entry ->
                            zip.getInputStream(entry).use { input ->
                                FileOutputStream(settingsFile).use { output ->
                                    input.copyTo(output)
                                    output.fd.sync()
                                }
                            }
                            staged += RestoreItem.SETTINGS
                        }
                    }
                    if (RestoreItem.FILES in items) {
                        for (entry in zip.entries()) {
                            if (entry.isDirectory) continue
                            if (!BackupManager.isAttachmentName(entry.name)) continue
                            val target = PendingRestore.resolveInside(filesRoot, entry.name)
                            check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) {
                                "Cannot create the backup staging directory"
                            }
                            zip.getInputStream(entry).use { input ->
                                FileOutputStream(target).use { output ->
                                    input.copyTo(output)
                                    output.fd.sync()
                                }
                            }
                            staged += RestoreItem.FILES
                        }
                    }
                }
            } finally {
                layer.delete()
            }
        }
        val deleted = BundleFormat.chainDeletions(chain)
        if (deleted.isNotEmpty()) {
            // 覆盖模式下 PendingRestore 会清掉备份里没有的附件；合并模式保留本地的，只记一条日志
            Log.i(TAG, "bundle declares ${deleted.size} deleted attachments")
        }
        return staged
    }
}
