package me.rerere.rikkahub.data.sync

import android.content.Context
import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.database.sqlite.SQLiteDatabaseConfiguration
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.db.SQLiteConfiguration
import me.rerere.rikkahub.data.files.FileFolders
import java.io.File

/**
 * 把 L3Hub 的数据降级成原版 RikkaHub 能直接恢复的整份备份。
 *
 * 原版恢复时会用 Room 打开备份里的库：`user_version` 必须等于它编译进去的版本，`room_master_table`
 * 里的结构指纹也必须对得上，否则直接抛异常；库里的附件是绝对路径，指向本应用的私有目录。设置里还有
 * 原版认不出的取值（Fork 新增的枚举与多态类型），留着会让整份设置解码失败。
 *
 * 目标固定为原版 RikkaHub 3.3.0（数据库 34）。原版升级数据库后，[TARGET_DB_VERSION] 与
 * [TARGET_IDENTITY_HASH] 要跟着换一次。指纹取自原版 3.3.0 的 `AppDatabase_Impl`，与上游仓库的
 * `app/schemas/…/34.json` 一致；本仓库的同一个文件按 Fork 的实际结构改过，不能拿来当参照。
 */
internal object UpstreamCompat {
    const val TARGET_DB_VERSION = 34
    const val TARGET_IDENTITY_HASH = "4c94f9b43fb6614c97afcc5e53648650"

    /** 原版的 filesDir；不改写过去，原版按备份里的路径找不到任何附件 */
    const val TARGET_FILES_DIR = "/data/user/0/me.rerere.rikkahub/files"

    /** 原版打包与恢复都只认这几个目录；`images/` 不在其中，带过去也会被忽略 */
    val ATTACHMENT_FOLDERS = listOf(
        FileFolders.UPLOAD,
        FileFolders.SKILLS,
        FileFolders.FONTS,
        FileFolders.MEDIA_CREATION,
    )

    val NESTED_ATTACHMENT_FOLDERS = setOf(FileFolders.SKILLS, FileFolders.MEDIA_CREATION)

    /** 原版 `ProviderSetting` 认识的类型；Fork 的 litert / gguf / onnx 会让整份设置解码失败 */
    private val PROVIDER_TYPES = setOf("openai", "google", "claude", "chatgpt", "grok")

    /** 原版 `ModelType` 的取值；OCR 模型原版没有，留着同样会让整份设置解码失败 */
    private val MODEL_TYPES = setOf("CHAT", "IMAGE", "EMBEDDING")

    /** 原版缺失的枚举值按最近的档位降级，键是序列化后的字段名 */
    private val ENUM_VALUES = mapOf(
        "reasoningLevel" to mapOf("minimal" to "low", "ultra" to "max"),
        "backgroundEffectType" to mapOf("off" to "blur"),
    )

    /**
     * 把 [settingsJson] 降级成原版能解码的设置。
     *
     * 只处理会让原版**报错**的部分：多态类型与枚举取值；其余 Fork 独有的键原版会按
     * `ignoreUnknownKeys` 丢掉，不必逐个清理。附件路径同时改写到原版的 filesDir。
     */
    fun downgradeSettings(settingsJson: String): String {
        val fields = Json.parseToJsonElement(settingsJson).jsonObject.toMutableMap()
        val providers = fields["providers"] as? JsonArray
        if (providers != null) {
            fields["providers"] = JsonArray(providers.mapNotNull { downgradeProvider(it) })
        }
        val downgraded = JsonObject(fields).mapEnumValues(ENUM_VALUES)
        val text = Json.encodeToString(JsonElement.serializer(), downgraded)
        return BackupPaths.rewrite(text, File(TARGET_FILES_DIR))
    }

    /**
     * 把备份库改成原版认识的版本。
     *
     * 结构只增不减：Fork 多加的表与列原版不会读，列都带默认值，写入不受影响；真正会挡住原版的是
     * `user_version` 与结构指纹，这两项改掉它就能打开。
     */
    fun downgradeDatabase(context: Context, databaseFile: File) {
        val configuration = SQLiteConfiguration.configure(
            context,
            SQLiteDatabaseConfiguration(databaseFile.absolutePath, SQLiteDatabase.OPEN_READWRITE),
        )
        SQLiteDatabase.openDatabase(configuration, null) {
            error("Backup database is corrupt")
        }.use { database ->
            database.execSQL("PRAGMA user_version = $TARGET_DB_VERSION")
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"
            )
            database.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, ?)",
                arrayOf(TARGET_IDENTITY_HASH),
            )
            BackupPaths.rewriteDatabase(database, File(TARGET_FILES_DIR))
            DatabaseBackup.checkpoint(database)
        }
        DatabaseBackup.removeSidecars(databaseFile)
    }

    private fun downgradeProvider(provider: JsonElement): JsonElement? {
        val fields = provider.jsonObject
        if (fields["type"]?.jsonPrimitive?.contentOrNull !in PROVIDER_TYPES) return null
        val models = fields["models"] as? JsonArray ?: return provider
        val kept = models.filter { model ->
            model.jsonObject["type"]?.jsonPrimitive?.contentOrNull in MODEL_TYPES
        }
        return JsonObject(fields + ("models" to JsonArray(kept)))
    }

    /** 递归替换指定键上的取值；同名键只出现在这几类配置里，不必关心层级 */
    private fun JsonElement.mapEnumValues(mappings: Map<String, Map<String, String>>): JsonElement =
        when (this) {
            is JsonObject -> JsonObject(
                mapValues { (key, value) ->
                    val mapped = (value as? JsonPrimitive)
                        ?.takeIf { it.isString }
                        ?.contentOrNull
                        ?.let { mappings[key]?.get(it) }
                    if (mapped != null) JsonPrimitive(mapped) else value.mapEnumValues(mappings)
                }
            )

            is JsonArray -> JsonArray(map { it.mapEnumValues(mappings) })
            else -> this
        }
}
