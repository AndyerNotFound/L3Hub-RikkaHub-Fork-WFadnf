package me.rerere.rikkahub.data.sync

import android.content.Context
import com.l3hub.syncproto.BundleFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * 远端备份集的链状态。
 *
 * WebDAV / S3 不能原地追加，一个备份集是「base 对象 + 若干层对象」。要接着往上叠，得知道链首属于哪台
 * 设备、结构版本是多少、上次的水位与已经上传过哪些附件；这些只在本地记一份。状态丢了（换机、清数据、
 * 换备份目录）就重开一个备份集，旧集仍然可以完整恢复，只是从那一份之后重新开始叠。
 */
object BundleChainStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Serializable
    data class ChainState(
        val baseName: String,
        val deviceId: String,
        val schemaVersion: Int,
        val hasDatabase: Boolean,
        val layerCount: Int,
        val lastCreatedAt: Long,
        val settingsHash: String? = null,
        val files: Map<String, Long> = emptyMap(),
    ) {
        fun summary(): BundleFormat.ChainSummary = BundleFormat.ChainSummary(
            deviceId = deviceId,
            schemaVersion = schemaVersion,
            hasDatabase = hasDatabase,
            layerCount = layerCount,
            lastCreatedAt = lastCreatedAt,
            files = files,
            settingsHash = settingsHash,
        )
    }

    /** 本机的备份身份；换设备后旧链只读，不往里追加 */
    fun deviceId(context: Context): String {
        val preferences = context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        preferences.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val id = UUID.randomUUID().toString()
        preferences.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    fun load(context: Context, target: String): ChainState? = runCatching {
        val file = stateFile(context, target)
        if (!file.isFile) null else json.decodeFromString<ChainState>(file.readText())
    }.getOrNull()

    fun clear(context: Context, target: String) {
        runCatching { stateFile(context, target).delete() }
    }

    /** 一层上传成功之后推进状态；[previous] 为 null 表示这一层就是新的 base */
    fun record(
        context: Context,
        target: String,
        baseName: String,
        layer: BundleFormat.Layer,
        previous: ChainState?,
    ) {
        val files = LinkedHashMap(previous?.files.orEmpty())
        layer.deletedFiles.forEach { files.remove(it) }
        layer.files.forEach { files[it.path] = it.size }
        val state = ChainState(
            baseName = baseName,
            deviceId = layer.deviceId,
            schemaVersion = layer.schemaVersion,
            hasDatabase = if (layer.isBase) layer.hasDatabase else previous?.hasDatabase ?: layer.hasDatabase,
            layerCount = if (layer.isBase) 1 else (previous?.layerCount ?: 0) + 1,
            lastCreatedAt = layer.createdAt,
            settingsHash = layer.settingsHash ?: previous?.settingsHash,
            files = files,
        )
        val file = stateFile(context, target)
        runCatching {
            val temporary = File(file.parentFile, file.name + ".tmp")
            temporary.writeText(json.encodeToString(state))
            check(temporary.renameTo(file) || (file.delete() && temporary.renameTo(file))) {
                "Cannot write the backup chain state"
            }
        }
    }

    private fun stateFile(context: Context, target: String): File {
        val name = MessageDigest.getInstance("SHA-256")
            .digest(target.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)
        val directory = File(context.applicationContext.filesDir, DIRECTORY)
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create the backup chain directory" }
        return File(directory, "$name.json")
    }

    private const val PREFERENCES = "backup-bundle"
    private const val KEY_DEVICE_ID = "deviceId"
    private const val DIRECTORY = "backup-chains"
}
