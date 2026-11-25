package me.rerere.rikkahub.data.datastore.migration

import android.util.Log
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.utils.JsonInstant

private const val TAG = "SettingsJsonMigrator"

/**
 * 对备份文件中的 settings.json 应用与 DataStore migration 相同的迁移逻辑。
 *
 * DataStore migration 作用于分散的 key-value 存储，而备份文件中的 settings.json
 * 是整个 [me.rerere.rikkahub.data.datastore.Settings] 对象的序列化结果。
 * 此工具类负责在反序列化前对旧格式的 JSON 执行等价的迁移操作。
 */
object SettingsJsonMigrator {

    /**
     * 对 settings JSON 字符串依次应用所有版本的迁移。
     * 若发生异常则返回原始 JSON，不中断恢复流程。
     */
    fun migrate(settingsJson: String): String {
        return runCatching {
            val root = JsonInstant.parseToJsonElement(settingsJson).jsonObject.toMutableMap()

            // V1: 修复 mcpServers 中全限定类名的 type 字段
            root["mcpServers"]?.let { element ->
                val migrated = migrateMcpServersJson(JsonInstant.encodeToString(element))
                root["mcpServers"] = JsonInstant.parseToJsonElement(migrated)
            }

            // V2: 修复 assistants 中 UIMessagePart 的 type 字段
            root["assistants"]?.let { element ->
                val migrated = migrateAssistantsJson(JsonInstant.encodeToString(element))
                root["assistants"] = JsonInstant.parseToJsonElement(migrated)
            }

            // V3: 将 assistants 中内嵌的 quickMessages 提取为全局 quickMessages
            root["assistants"]?.let { element ->
                val (migratedAssistants, extractedQuickMessages) =
                    migrateAssistantsQuickMessages(JsonInstant.encodeToString(element))
                root["assistants"] = JsonInstant.parseToJsonElement(migratedAssistants)

                if (extractedQuickMessages.isNotEmpty()) {
                    val existing = root["quickMessages"]
                    val existingArray = existing?.let {
                        runCatching {
                            JsonInstant.parseToJsonElement(
                                JsonInstant.encodeToString(it)
                            ) as? JsonArray
                        }.getOrNull()
                    } ?: JsonArray(emptyList())
                    val existingIds = existingArray.mapNotNull {
                        (it as? JsonObject)?.get("id")?.toString()?.trim('"')
                    }.toSet()
                    val merged = JsonArray(
                        existingArray + extractedQuickMessages.filter { e ->
                            val id = (e as? JsonObject)?.get("id")?.toString()?.trim('"')
                            id != null && id !in existingIds
                        }
                    )
                    root["quickMessages"] = merged
                }
            }

            // V4: 移除 assistants 的 allowConversationPromptInjection 开关
            root["assistants"]?.let { element ->
                val migrated = migrateAssistantsConversationInjections(JsonInstant.encodeToString(element))
                root["assistants"] = JsonInstant.parseToJsonElement(migrated)
            }

            // V5: 提供商的余额查询 balanceOption 改成自定义额度查询 quotaQuery
            (root["providers"] as? JsonArray)?.let { providers ->
                root["providers"] = JsonArray(providers.map(::migrateProviderQuotaQuery))
            }

            // V6: displaySetting 的旧全局特效开关搬进 advancedAppearanceSetting
            // 旧键仍留在 displaySetting 里：JsonInstant 读的时候忽略多出来的键，降级也还要用
            // 输入区、顶栏、气泡、浮动 Dock 也吃这个旧开关，所以两份文档都要落
            migrateLegacyDisplayEffectsInBackup(root)

            JsonInstant.encodeToString(JsonObject(root))
        }.onFailure {
            Log.e(TAG, "migrate: Failed to migrate settings JSON, using original", it)
        }.getOrDefault(settingsJson)
    }
}

/**
 * V6：备份里的两份文档交给 [migrateLegacyDisplayEffects]，只写回真正变了的那一份。
 *
 * 这里要分清「这一份不在」和「这一份在、但读不出来」：键不在就传 null（当成没有这份设置）；
 * 键在就原样编码传进去，哪怕是 `null`、数组、空串——那些是写着但读不出来的坏文档，
 * 那边会整份保留，不会当成缺失去凭空补一份新的。DataStore 那条路拿到的是同一份文档的字符串，
 * 两条路因此对同一份数据做同一个判断。
 */
private fun migrateLegacyDisplayEffectsInBackup(root: MutableMap<String, JsonElement>) {
    val migratedEffects = migrateLegacyDisplayEffects(
        displayJson = root["displaySetting"]?.let { JsonInstant.encodeToString(it) },
        advancedJson = root["advancedAppearanceSetting"]?.let { JsonInstant.encodeToString(it) },
    )
    migratedEffects.displayJson?.let { migrated ->
        root["displaySetting"] = JsonInstant.parseToJsonElement(migrated)
    }
    migratedEffects.appearanceJson?.let { migrated ->
        root["advancedAppearanceSetting"] = JsonInstant.parseToJsonElement(migrated)
    }
}
