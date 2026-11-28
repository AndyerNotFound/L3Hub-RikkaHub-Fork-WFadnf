package me.rerere.rikkahub.data.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV6Migration
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV7Migration
import me.rerere.rikkahub.data.datastore.migration.RetiredAppearanceCleanup
import me.rerere.rikkahub.data.datastore.migration.SettingsJsonMigrator
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V7：高级外观整份下线，盘上留下的那一份删掉。
 *
 * 覆盖三件事：
 * - 升级路径：缺版本号（首次启动）、≤5、6 都要迁到 7，重复跑不出变化；
 * - 只删该删的：退役键之外的东西（providers / assistants / 主题 / 普通消息样式 /
 *   `showTopBarBackground` / `motionBlur`）一个都不许少，坏文档不许被覆盖；
 * - 版本号已经 ≥ 7 时只清残留、不把版本降下来（降级装过旧包又升回来会遇到）。
 *
 * 断言全部落在盘上的 JSON 上：模型已经没有了，能当证据的只剩字节。
 */
class PreferenceStoreV7MigrationTest {
    private val v6 = PreferenceStoreV6Migration()
    private val v7 = PreferenceStoreV7Migration()

    // 黑名单只有一份（Java 那边），这里不抄第二份；具体是哪几个键由下面那条测试钉死
    private val retiredDisplayKeys = RetiredAppearanceCleanup.retiredDisplayKeys().toList()

    private fun stored(
        display: String? = null,
        advanced: String? = null,
        version: Int? = null,
    ): Preferences {
        val preferences = mutablePreferencesOf()
        if (version != null) preferences[SettingsStore.VERSION] = version
        if (display != null) preferences[SettingsStore.DISPLAY_SETTING] = display
        if (advanced != null) preferences[SettingsStore.ADVANCED_APPEARANCE_SETTING] = advanced
        return preferences
    }

    /** 真实升级顺序：先 V6 再 V7。 */
    private suspend fun upgraded(
        display: String? = null,
        advanced: String? = null,
        version: Int? = 5,
    ): Preferences {
        val afterV6 = v6.migrate(stored(display = display, advanced = advanced, version = version))
        return v7.migrate(afterV6.toMutablePreferences())
    }

    private fun Preferences.displayDoc(): JsonObject? =
        this[SettingsStore.DISPLAY_SETTING]
            ?.let { JsonInstant.parseToJsonElement(it) as? JsonObject }

    private fun JsonObject.float(key: String): Float? = (this[key] as? JsonPrimitive)?.floatOrNull

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.flag(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    // 黑名单就是这十个键：多一个会误删用户设置，少一个会把退役字段留在盘上
    @Test
    fun `the retired key list is exactly the advanced appearance fields`() {
        assertEquals(
            listOf(
                "backgroundEffectType",
                "blurOpacity",
                "enableBlurEffect",
                "enableTopBarBlur",
                "glassOpacity",
                "inputBlurRadius",
                "inputSurfaceOpacity",
                "legacyEffectsMigrated",
                "topBarBlurRadius",
                "topBarSurfaceOpacity",
            ),
            RetiredAppearanceCleanup.retiredDisplayKeys().sorted(),
        )
    }

    // 首次启动：盘上什么都没写，迁移只是把版本号立起来，不动任何数据
    @Test
    fun `fresh install only moves the version to seven`() = runTest {
        assertTrue(v7.shouldMigrate(stored()))

        val result = v7.migrate(stored())

        assertEquals(RetiredAppearanceCleanup.MIGRATION_VERSION, result[SettingsStore.VERSION])
        assertEquals(setOf(SettingsStore.VERSION), result.asMap().keys)
    }

    // 6 → 7：高级外观那份整份消失，display 里只掉退役键
    @Test
    fun `version six upgrade drops the appearance document and the retired keys`() = runTest {
        val display = """
            {
                "showTopBarBackground": true,
                "enableTopBarBlur": true,
                "topBarBlurRadius": 20.0,
                "topBarSurfaceOpacity": 0.65,
                "inputBlurRadius": 12.0,
                "inputSurfaceOpacity": 0.55,
                "legacyEffectsMigrated": true,
                "enableBlurEffect": true,
                "backgroundEffectType": "blur",
                "blurOpacity": 0.45,
                "glassOpacity": 0.35,
                "motionBlur": {"enabled": true, "radius": 9},
                "chatMessageBackgroundStyle": "frosted"
            }
        """.trimIndent()
        val preferences = stored(
            display = display,
            advanced = """{"enableAdvancedAppearance":true,"pageSurfaceStyle":"mica"}""",
            version = 6,
        )
        assertTrue(v7.shouldMigrate(preferences))

        val result = v7.migrate(preferences)

        assertNull(result[SettingsStore.ADVANCED_APPEARANCE_SETTING])
        assertEquals(7, result[SettingsStore.VERSION])

        val doc = result.displayDoc()!!
        retiredDisplayKeys.forEach { key -> assertFalse("退役键没清掉：$key", key in doc) }
        // 留下来的是还在用的显示项
        assertEquals(true, doc.flag("showTopBarBackground"))
        assertEquals("frosted", doc.text("chatMessageBackgroundStyle"))
        assertEquals("{\"enabled\":true,\"radius\":9}", doc["motionBlur"].toString())
    }

    // 缺版本号与 ≤5 的老盘同样升到 7（V6 先跑一遍，紧接着 V7 清掉它写的东西）
    @Test
    fun `missing and older versions all upgrade to seven`() = runTest {
        listOf<Int?>(null, 0, 1, 4, 5).forEach { version ->
            val preferences = stored(
                display = """{"enableBlurEffect":true,"backgroundEffectType":"blur","blurOpacity":0.45}""",
                version = version,
            )
            assertTrue("版本 $version 应该迁移", v7.shouldMigrate(preferences))

            val result = upgraded(
                display = preferences[SettingsStore.DISPLAY_SETTING],
                version = version,
            )

            assertEquals("版本 $version 没升到 7", 7, result[SettingsStore.VERSION])
            assertNull(result[SettingsStore.ADVANCED_APPEARANCE_SETTING])
            // V6 写进去的继承值与标记被 V7 一并摘掉，只剩一个空对象
            assertEquals(JsonObject(emptyMap()), result.displayDoc())
        }
    }

    // 6 → 7 直接升级：V6 刚写下去的那份 JSON 立刻被删掉，旧特效不会以任何形式留下
    @Test
    fun `version six to seven leaves nothing of the legacy effects behind`() = runTest {
        val legacy = """{"enableBlurEffect":true,"backgroundEffectType":"glass","glassOpacity":0.35}"""

        val afterV6 = v6.migrate(stored(display = legacy, version = 5))
        // V6 照旧生产了那份文档（历史行为没改）
        assertTrue(afterV6[SettingsStore.ADVANCED_APPEARANCE_SETTING] != null)

        val afterV7 = v7.migrate(afterV6.toMutablePreferences())

        assertNull(afterV7[SettingsStore.ADVANCED_APPEARANCE_SETTING])
        val doc = afterV7.displayDoc()!!
        retiredDisplayKeys.forEach { key -> assertFalse("退役键没清掉：$key", key in doc) }
        assertEquals(7, afterV7[SettingsStore.VERSION])
    }

    // 连跑两遍：第一遍之后盘上已经没有可清的东西，第二遍不该改动任何字节
    @Test
    fun `migration is idempotent`() = runTest {
        val preferences = stored(
            display = """{"enableBlurEffect":true,"showTopBarBackground":true,"inputBlurRadius":9.0}""",
            advanced = """{"enableAdvancedAppearance":true}""",
            version = 6,
        )

        val once = v7.migrate(preferences)
        assertFalse(v7.shouldMigrate(once))

        val twice = v7.migrate(once.toMutablePreferences())
        assertEquals(once.asMap(), twice.asMap())
    }

    // 盘上本来就没有这两份数据：不凭空补一个空对象出来
    @Test
    fun `absent documents are not created`() = runTest {
        val result = v7.migrate(stored(version = 6))

        assertNull(result[SettingsStore.DISPLAY_SETTING])
        assertNull(result[SettingsStore.ADVANCED_APPEARANCE_SETTING])
        assertEquals(7, result[SettingsStore.VERSION])
    }

    // display 是空对象或只剩退役键：退役键走掉之后留下一个空对象，这是诚实的结果
    @Test
    fun `a document with only retired keys becomes empty`() = runTest {
        listOf(
            """{"enableBlurEffect":true,"blurOpacity":0.5}""",
            """{"legacyEffectsMigrated":true}""",
        ).forEach { display ->
            val result = v7.migrate(stored(display = display, version = 6))
            assertEquals("清完应该只剩空对象：$display", JsonObject(emptyMap()), result.displayDoc())
        }
    }

    // 高级外观那个 key 本身退役了：内容是坏字符串、空串、数组、null 都照样删；
    // display 读不出来时反过来一个字节都不许动
    @Test
    fun `broken appearance document is removed while a broken display document is kept`() = runTest {
        listOf("not json", "", "  ", "[]", "null", "42").forEach { broken ->
            val result = v7.migrate(stored(advanced = broken, version = 6))
            assertNull("坏文档也该删掉这个 key：$broken", result[SettingsStore.ADVANCED_APPEARANCE_SETTING])
            assertEquals(7, result[SettingsStore.VERSION])
        }

        listOf("not json", "", "  ", "[]", "null").forEach { broken ->
            val result = v7.migrate(stored(display = broken, advanced = """{"a":1}""", version = 6))
            assertEquals("坏文档不该被覆盖：$broken", broken, result[SettingsStore.DISPLAY_SETTING])
        }
    }

    // 只清退役键：别的设置一个都不许丢
    @Test
    fun `unrelated settings are untouched`() = runTest {
        val display = """
            {
                "showTopBarBackground": true,
                "chatMessageBackgroundStyle": "solid",
                "customizeThinkingToolCards": true,
                "userChatBubbleStyleOverrides": {"cornerRadius": 18.0},
                "motionBlur": {"enabled": true},
                "userNickname": "me",
                "enableBlurEffect": true
            }
        """.trimIndent()
        val preferences = stored(display = display, advanced = """{"x":1}""", version = 6)
            .toMutablePreferences()
        preferences[SettingsStore.PROVIDERS] = """[{"id":"p1","type":"openai"}]"""
        preferences[SettingsStore.ASSISTANTS] = """[{"id":"a1","name":"assistant"}]"""
        preferences[SettingsStore.THEME_ID] = "minimal"
        preferences[SettingsStore.SEARCH_SELECTED] = 3

        val result = v7.migrate(preferences)

        assertEquals("""[{"id":"p1","type":"openai"}]""", result[SettingsStore.PROVIDERS])
        assertEquals("""[{"id":"a1","name":"assistant"}]""", result[SettingsStore.ASSISTANTS])
        assertEquals("minimal", result[SettingsStore.THEME_ID])
        assertEquals(3, result[SettingsStore.SEARCH_SELECTED])

        val doc = result.displayDoc()!!
        assertFalse("enableBlurEffect" in doc)
        assertEquals(true, doc.flag("showTopBarBackground"))
        assertEquals("solid", doc.text("chatMessageBackgroundStyle"))
        assertEquals(true, doc.flag("customizeThinkingToolCards"))
        assertEquals(18f, (doc["userChatBubbleStyleOverrides"] as JsonObject).float("cornerRadius"))
        assertEquals("me", doc.text("userNickname"))
        assertTrue("motionBlur" in doc)
    }

    // 版本已经 7：没有残留就不跑；有残留（降级旧包留下的同名 key）就清掉，但版本保持 7
    @Test
    fun `version seven only cleans residue`() = runTest {
        val clean = stored(display = """{"showTopBarBackground":true}""", version = 7)
        assertFalse(v7.shouldMigrate(clean))

        val residue = stored(
            display = """{"enableBlurEffect":true,"showTopBarBackground":true}""",
            advanced = """{"enableAdvancedAppearance":true}""",
            version = 7,
        )
        assertTrue(v7.shouldMigrate(residue))

        val result = v7.migrate(residue)
        assertNull(result[SettingsStore.ADVANCED_APPEARANCE_SETTING])
        assertEquals(JsonObject(mapOf("showTopBarBackground" to JsonPrimitive(true))), result.displayDoc())
        assertEquals(7, result[SettingsStore.VERSION])
    }

    // 版本比 7 还高：只清残留，绝不把版本号降成 7
    @Test
    fun `a newer data version is never lowered`() = runTest {
        val preferences = stored(
            display = """{"enableBlurEffect":true}""",
            advanced = """{"a":1}""",
            version = 9,
        )
        assertTrue(v7.shouldMigrate(preferences))

        val result = v7.migrate(preferences)

        assertEquals(9, result[SettingsStore.VERSION])
        assertNull(result[SettingsStore.ADVANCED_APPEARANCE_SETTING])
        assertEquals(JsonObject(emptyMap()), result.displayDoc())

        assertFalse(v7.shouldMigrate(stored(display = """{"showTopBarBackground":true}""", version = 9)))
    }

    // 备份 settings.json：根上的高级外观整份摘掉，displaySetting 里只摘退役键
    @Test
    fun `backup json prunes the retired appearance fields`() {
        val input = """
            {
                "displaySetting": {
                    "showTopBarBackground": true,
                    "topBarBlurRadius": 20.0,
                    "enableBlurEffect": true,
                    "blurOpacity": 0.45,
                    "chatMessageBackgroundStyle": "frosted",
                    "motionBlur": {"enabled": true}
                },
                "advancedAppearanceSetting": {"enableAdvancedAppearance": true},
                "providers": [{"id": "p1", "type": "openai"}],
                "assistants": [{"id": "a1", "name": "assistant"}],
                "themeId": "minimal"
            }
        """.trimIndent()

        val root = JsonInstant.parseToJsonElement(SettingsJsonMigrator.migrate(input)).jsonObject

        assertFalse("advancedAppearanceSetting" in root)
        assertEquals("minimal", root.text("themeId"))
        assertTrue(root.getValue("providers").toString().contains("\"p1\""))
        assertTrue(root.getValue("assistants").toString().contains("\"a1\""))

        val display = root.getValue("displaySetting").jsonObject
        retiredDisplayKeys.forEach { key -> assertFalse("退役键没清掉：$key", key in display) }
        assertEquals(true, display.flag("showTopBarBackground"))
        assertEquals("frosted", display.text("chatMessageBackgroundStyle"))
        assertTrue("motionBlur" in display)
    }

    // 备份里 displaySetting 不是对象：原样留着（那是写着但读不出来的数据），根上的退役字段照删
    @Test
    fun `backup json keeps a broken display document`() {
        listOf("[]", "null", "\"not json\"", "\"\"", "42").forEach { broken ->
            val input = """{"displaySetting":$broken,"advancedAppearanceSetting":{"enableAdvancedAppearance":true}}"""
            val root = JsonInstant.parseToJsonElement(SettingsJsonMigrator.migrate(input)).jsonObject

            assertEquals("坏文档不该被改写：$broken", broken, root.getValue("displaySetting").toString())
            assertFalse("advancedAppearanceSetting" in root)
        }
    }

    // 备份那条路照旧跑 V1..V5：高级外观下线不该把别的迁移一起关掉
    @Test
    fun `backup json still runs the earlier migrations`() {
        val input = """{"providers":[{"type":"openai","balanceOption":{"apiPath":"/credits"}}]}"""

        val root = JsonInstant.parseToJsonElement(SettingsJsonMigrator.migrate(input)).jsonObject
        val provider = (root.getValue("providers") as JsonArray)[0].jsonObject

        assertFalse("balanceOption" in provider)
        assertEquals("{{baseUrl}}/credits", provider.getValue("quotaQuery").jsonObject.text("url"))
    }

    // 两条路对同一份有效数据给出同一个判断：DataStore（V6 后 V7）与备份（SettingsJsonMigrator）。
    // 只拿「有这份文档」和「没有这份文档」两种情形比：坏文档在两边分别是原始字符串与 JSON 字符串，
    // 落盘形态本来就不同，那两种情形由上面两个用例分别钉住。
    @Test
    fun `backup and datastore paths agree on the cleaned display document`() = runTest {
        val documents = listOf<String?>(
            null,
            """{"enableBlurEffect":true,"backgroundEffectType":"blur","blurOpacity":0.45}""",
            """{"enableBlurEffect":true,"showTopBarBackground":true,"inputSurfaceOpacity":0.4}""",
            """{"showTopBarBackground":true,"motionBlur":{"enabled":true}}""",
        )

        documents.forEach { document ->
            val fromStore = upgraded(display = document, advanced = """{"a":1}""", version = 6)
            val root = buildMap<String, JsonElement> {
                document?.let { put("displaySetting", JsonInstant.parseToJsonElement(it)) }
                put("advancedAppearanceSetting", JsonInstant.parseToJsonElement("""{"a":1}"""))
            }
            val fromBackup = JsonInstant.parseToJsonElement(
                SettingsJsonMigrator.migrate(JsonInstant.encodeToString(JsonObject(root)))
            ).jsonObject

            // 两边都是紧凑 JSON：对象按内容比，缺这份文档时两边都是 null
            assertEquals(
                "display 那份对不上：$document",
                fromStore[SettingsStore.DISPLAY_SETTING]?.let { JsonInstant.parseToJsonElement(it) },
                fromBackup["displaySetting"],
            )
            // 两条路都不会把高级外观带回来
            assertNull(fromStore[SettingsStore.ADVANCED_APPEARANCE_SETTING])
            assertFalse("advancedAppearanceSetting" in fromBackup)
        }
    }
}
