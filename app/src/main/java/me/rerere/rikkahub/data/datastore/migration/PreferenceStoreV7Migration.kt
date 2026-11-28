package me.rerere.rikkahub.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.JsonInstant

/**
 * V7：高级外观整份下线，把盘上留下的那一份删掉。
 *
 * 规则本身在 [RetiredAppearanceCleanup]（Java，纯规则、不碰存储）。这里只负责三件事：
 * 读盘上的版本号、按规则摘 key、把版本号写成「不低于 7」。
 *
 * 删的是什么：
 * - `advanced_appearance_setting` 这个 key 整体删掉——模型、字段、界面都没了，
 *   留着只是让下次降级装回的旧包又读出一套用户已经关掉的外观；
 * - `display_setting` 里只属于高级外观与旧全局特效的十个键（见 [RetiredAppearanceCleanup]）。
 *   普通主题、调色板、`showTopBarBackground`、消息样式与角色气泡参数、`motionBlur` 都不动。
 *
 * 两条边界照旧：
 * - `display_setting` 不是 JSON 对象（坏字符串、空串、`null`、数组）时原样留着。那是写着但读不出来
 *   的数据，改写它等于把用户的原始字节换成我们猜的东西；等换成读得懂的包，它还能回来。
 * - 版本号已经 ≥ 7 时不降版本。降级装过旧包又升回来，盘上会重新出现这些键：那时只按
 *   [RetiredAppearanceCleanup.shouldCleanResidue] 清残留，版本保持原值；没有残留就不跑迁移。
 *
 * V6 的语义一个字没改：它照旧把旧特效开关翻成高级外观那份 JSON 字符串写下去，
 * 紧接着 V7 就把那份删了。同一个升级过程里两份代码各做各的事，互不依赖。
 */
class PreferenceStoreV7Migration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[SettingsStore.VERSION]
        if (RetiredAppearanceCleanup.shouldMigrate(version)) return true
        // 版本已经到 7 了：只有盘上真的还有能清的东西才再跑一次，否则每次启动都白迁一遍
        return RetiredAppearanceCleanup.shouldCleanResidue(
            version,
            hasRetiredResidue(currentData),
        )
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        // 版本号先读出来：下面写入的是「不低于 7」，不能把它当成清理结果再读一遍
        val version = prefs[SettingsStore.VERSION]

        // 高级外观那份文档整个 key 退役：内容是 object、坏字符串还是空串都一样删
        prefs.remove(SettingsStore.ADVANCED_APPEARANCE_SETTING)
        // display_setting 只摘退役键；没得摘时一个字都不重写
        prefs[SettingsStore.DISPLAY_SETTING]?.let { raw ->
            cleanedDisplayJson(raw)?.let { prefs[SettingsStore.DISPLAY_SETTING] = it }
        }

        prefs[SettingsStore.VERSION] = RetiredAppearanceCleanup.nextDataVersion(version)
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}
}

/**
 * 盘上是不是还有可清的东西：高级外观那个 key 在，或者 `display_setting` 里还留着退役键。
 *
 * `display_setting` 读不出来（不是对象）时返回 false——那份文档我们本来就不动，
 * 为此每次都重跑迁移没有意义。
 */
private fun hasRetiredResidue(preferences: Preferences): Boolean {
    if (preferences[SettingsStore.ADVANCED_APPEARANCE_SETTING] != null) return true
    val display = preferences[SettingsStore.DISPLAY_SETTING] ?: return false
    val document = display.parseObjectOrNull() ?: return false
    return RetiredAppearanceCleanup.hasRetiredDisplayKeys(document.keys)
}

/**
 * 摘掉 `display_setting` 里的退役键。
 *
 * 返回 null 表示这份文档不用改（退役键一个都没有，或者是读不出来的坏文档）——调用方必须
 * 原样留着盘上那串字符串，连重新编码都不要，无关字段的字节才不会被我们改写一遍。
 * 真要改的时候只减键，其余键值原样带着走。
 */
internal fun cleanedDisplayJson(raw: String): String? {
    val document = raw.parseObjectOrNull() ?: return null
    val retired = RetiredAppearanceCleanup.retiredDisplayKeysPresent(document.keys)
    if (retired.isEmpty()) return null
    return JsonInstant.encodeToString(JsonObject(document - retired.toSet()))
}

/**
 * 备份 `settings.json` 那份根对象上的同一套清理：去掉整个 `advancedAppearanceSetting`，
 * 再摘掉 `displaySetting` 里的退役键。
 *
 * 与 DataStore 那条路共用 [RetiredAppearanceCleanup] 的规则与 [cleanedDisplayJson]，
 * 两条路对同一份数据给出同一个判断。`displaySetting` 不是对象时原样留着，不凭空补一份。
 */
internal fun pruneRetiredAppearance(root: MutableMap<String, JsonElement>) {
    root.remove(RetiredAppearanceCleanup.ROOT_ADVANCED_APPEARANCE)
    val display = root[RetiredAppearanceCleanup.ROOT_DISPLAY_SETTING]
    val document = (display as? JsonObject) ?: return
    val retired = RetiredAppearanceCleanup.retiredDisplayKeysPresent(document.keys)
    if (retired.isEmpty()) return
    root[RetiredAppearanceCleanup.ROOT_DISPLAY_SETTING] =
        JsonObject(document - retired.toSet())
}
