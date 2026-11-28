package me.rerere.rikkahub.data.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV6Migration
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V6：旧的全局特效开关搬进高级外观——历史行为，语义一个字没改。
 *
 * 高级外观那份模型已经整份下线，所以这里不再把 JSON 解回类里，一律按落盘 JSON 断言：
 * V6 写下去的就是这些键，键名对不对得上由 [me.rerere.rikkahub.data.datastore.migration.RetiredAppearanceCleanup]
 * 的黑名单和 V7 的清理测试保证。
 *
 * 备份那条路（`SettingsJsonMigrator`）不再跟着跑这套生成逻辑，它只做 V7 的清理：
 * 那边的判断由 `PreferenceStoreV7MigrationTest` 盯着。两条路的分工是刻意的——
 * 备份恢复要的是"别把退役字段带回来"，不是"重建一遍当年搬过去的外观"。
 */
class PreferenceStoreV6MigrationTest {
    private val migration = PreferenceStoreV6Migration()

    private val legacyBlur =
        """{"enableBlurEffect":true,"backgroundEffectType":"blur","blurOpacity":0.45,"glassOpacity":0.35}"""
    private val legacyGlass =
        """{"enableBlurEffect":true,"backgroundEffectType":"glass","blurOpacity":0.45,"glassOpacity":0.35}"""

    private suspend fun migrated(
        display: String? = null,
        advanced: String? = null,
        version: Int = 5,
    ): Preferences {
        val preferences = mutablePreferencesOf(SettingsStore.VERSION to version)
        if (display != null) preferences[SettingsStore.DISPLAY_SETTING] = display
        if (advanced != null) preferences[SettingsStore.ADVANCED_APPEARANCE_SETTING] = advanced
        return migration.migrate(preferences)
    }

    private fun Preferences.displayDoc(): JsonObject =
        JsonInstant.parseToJsonElement(this[SettingsStore.DISPLAY_SETTING]!!).jsonObject

    private fun Preferences.appearanceDoc(): JsonObject =
        JsonInstant.parseToJsonElement(this[SettingsStore.ADVANCED_APPEARANCE_SETTING]!!).jsonObject

    private fun JsonObject.float(key: String): Float? = (this[key] as? JsonPrimitive)?.floatOrNull

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.flag(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    // 旧 blur 那一档：毛玻璃 12dp，透明度用旧 blurOpacity
    @Test
    fun `legacy blur effect becomes frosted 12dp on every surface`() = runTest {
        val result = migrated(display = legacyBlur)
        val doc = result.appearanceDoc()

        assertEquals("frosted", doc.text("pageSurfaceStyle"))
        assertEquals("frosted", doc.text("composerSurfaceStyle"))
        assertEquals("frosted", doc.text("overlaySurfaceStyle"))
        assertEquals("frosted", doc.text("navigationSurfaceStyle"))
        assertEquals("frosted", doc.text("chatDockSurfaceStyle"))
        assertEquals("frosted", doc.text("chatBubbleStyle"))
        // 思考卡跟 richContentMaterial 走；旧毛玻璃沿用既有的 translucent
        assertEquals("translucent", doc.text("richContentStyle"))

        assertEquals(12f, doc.float("pageSurfaceBlurRadius"))
        assertEquals(12f, doc.float("overlaySurfaceBlurRadius"))
        assertEquals(12f, doc.float("navigationGlassBlurRadius"))
        assertEquals(12f, doc.float("chatDockGlassBlurRadius"))
        assertEquals(12f, doc.float("chatBubbleBlurRadius"))
        assertEquals(12f, doc.float("richContentSurfaceBlurRadius"))

        assertEquals(.45f, doc.float("pageSurfaceOpacity"))
        assertEquals(.45f, doc.float("overlaySurfaceOpacity"))
        assertEquals(.45f, doc.float("navigationGlassOpacity"))
        assertEquals(.45f, doc.float("chatDockGlassOpacity"))
        assertEquals(.45f, doc.float("chatBubbleSurfaceOpacity"))
        assertEquals(.45f, doc.float("richContentSurfaceOpacity"))

        // 页面半径不该写进壁纸自己的模糊上，另一档的半径也不该留下
        assertFalse("globalBackgroundBlurRadius" in doc)
        assertFalse("pageLiquidGlassBlurRadius" in doc)
        assertFalse("navigationLiquidGlassBlurRadius" in doc)
        // 旧全局特效直接覆盖了浮动 Dock，迁移后由高级 Dock 材质继续承接
        assertTrue(doc.flag("enableChatDockGlass") == true)
        assertTrue(doc.flag("enableNavigationGlass") == true)
    }

    // 旧 glass 那一档：液态玻璃 16dp，透明度用旧 glassOpacity；不强制选液态玻璃预设
    @Test
    fun `legacy glass effect becomes liquid glass 16dp on every surface`() = runTest {
        val doc = migrated(display = legacyGlass).appearanceDoc()

        assertEquals("liquid_glass", doc.text("pageSurfaceStyle"))
        assertEquals("liquid_glass", doc.text("composerSurfaceStyle"))
        assertEquals("liquid_glass", doc.text("overlaySurfaceStyle"))
        assertEquals("liquid_glass", doc.text("navigationSurfaceStyle"))
        assertEquals("liquid_glass", doc.text("chatDockSurfaceStyle"))
        assertEquals("liquid_glass", doc.text("chatBubbleStyle"))
        assertEquals("liquid_glass", doc.text("richContentStyle"))

        assertEquals(16f, doc.float("pageLiquidGlassBlurRadius"))
        assertEquals(16f, doc.float("overlayLiquidGlassBlurRadius"))
        assertEquals(16f, doc.float("navigationLiquidGlassBlurRadius"))
        assertEquals(16f, doc.float("chatDockGlassBlurRadius"))
        assertEquals(16f, doc.float("chatBubbleBlurRadius"))
        assertEquals(16f, doc.float("richContentSurfaceBlurRadius"))

        assertEquals(.35f, doc.float("pageSurfaceOpacity"))
        assertEquals(.35f, doc.float("overlaySurfaceOpacity"))
        assertEquals(.35f, doc.float("navigationGlassOpacity"))
        assertEquals(.35f, doc.float("chatDockGlassOpacity"))
        assertEquals(.35f, doc.float("chatBubbleSurfaceOpacity"))
        assertEquals(.35f, doc.float("richContentSurfaceOpacity"))

        assertFalse("pageSurfaceBlurRadius" in doc)
        assertFalse("navigationGlassBlurRadius" in doc)
        // 液态玻璃交给默认那套预设，不写 liquidGlass
        assertFalse("liquidGlass" in doc)
        assertTrue(doc.flag("enableChatDockGlass") == true)
        assertTrue(doc.flag("enableNavigationGlass") == true)
    }

    // 输入区与顶栏继承同一档；但「要不要显示背景」这类功能开关不在这里开
    @Test
    fun `composer and top bar inherit the tier while feature switches stay off`() = runTest {
        val blur = migrated(display = legacyBlur).displayDoc()

        assertEquals(12f, blur.float("inputBlurRadius"))
        assertEquals(.45f, blur.float("inputSurfaceOpacity"))
        assertEquals(12f, blur.float("topBarBlurRadius"))
        assertEquals(.45f, blur.float("topBarSurfaceOpacity"))
        assertTrue(blur.flag("legacyEffectsMigrated") == true)

        assertFalse("showTopBarBackground" in blur)
        assertFalse("enableTopBarBlur" in blur)
        assertFalse("showAssistantBubble" in blur)
        // 气泡走高级外观的新字段，display 里这几项一个字都不动
        assertFalse("chatMessageBackgroundStyle" in blur)
        assertFalse("customizeThinkingToolCards" in blur)
        assertFalse("userChatBubbleStyleOverrides" in blur)
        assertFalse("assistantChatBubbleStyleOverrides" in blur)
        assertFalse("thinkingToolCardStyleOverrides" in blur)

        // 旧字段留着：V6 自己不删，紧接着由 V7 清掉（见 PreferenceStoreV7MigrationTest）
        assertEquals(JsonPrimitive(true), blur["enableBlurEffect"])
        assertEquals("blur", blur.text("backgroundEffectType"))
        assertEquals(.45f, blur.float("blurOpacity"))
        assertEquals(.35f, blur.float("glassOpacity"))

        val glass = migrated(display = legacyGlass).displayDoc()
        assertEquals(16f, glass.float("inputBlurRadius"))
        assertEquals(.35f, glass.float("inputSurfaceOpacity"))
        assertEquals(16f, glass.float("topBarBlurRadius"))
        assertEquals(.35f, glass.float("topBarSurfaceOpacity"))
    }

    // 用户自己存的 SOLID 和调过的角色参数（alpha、sigma、颜色、圆角）都要原样留住
    @Test
    fun `explicit solid bubble background and role overrides are kept`() = runTest {
        val display = """
            {
                "enableBlurEffect": true,
                "backgroundEffectType": "blur",
                "blurOpacity": 0.45,
                "chatMessageBackgroundStyle": "solid",
                "customizeThinkingToolCards": true,
                "userChatBubbleStyleOverrides": {
                    "backgroundArgbLight": -1,
                    "cornerRadius": 18.0,
                    "blurSigma": 9.5,
                    "frostedOpacity": 0.8
                },
                "assistantChatBubbleStyleOverrides": {"cornerRadius": 12.0, "blurSigma": 4.0},
                "thinkingToolCardStyleOverrides": {"frostedOpacity": 0.7}
            }
        """.trimIndent()

        val stored = migrated(display = display).displayDoc()

        assertEquals("solid", stored.text("chatMessageBackgroundStyle"))
        assertEquals(JsonPrimitive(true), stored["customizeThinkingToolCards"])

        val user = stored.obj("userChatBubbleStyleOverrides")!!
        assertEquals(setOf("backgroundArgbLight", "cornerRadius", "blurSigma", "frostedOpacity"), user.keys)
        assertEquals(JsonPrimitive(-1), user["backgroundArgbLight"])
        assertEquals(18f, user.float("cornerRadius"))
        assertEquals(9.5f, user.float("blurSigma"))
        assertEquals(.8f, user.float("frostedOpacity"))

        val assistant = stored.obj("assistantChatBubbleStyleOverrides")!!
        assertEquals(setOf("cornerRadius", "blurSigma"), assistant.keys)
        assertEquals(12f, assistant.float("cornerRadius"))
        assertEquals(4f, assistant.float("blurSigma"))

        val thinking = stored.obj("thinkingToolCardStyleOverrides")!!
        assertEquals(setOf("frostedOpacity"), thinking.keys)
        assertEquals(.7f, thinking.float("frostedOpacity"))
    }

    // 透明度不是 0..1 的合法数：用旧档自己的默认值，别让迁移后的表面没有底色
    @Test
    fun `invalid opacity falls back to the legacy tier default`() = runTest {
        val outOfRange = migrated(
            display = """{"enableBlurEffect":true,"backgroundEffectType":"blur","blurOpacity":2.5}"""
        )
        assertEquals(.45f, outOfRange.appearanceDoc().float("pageSurfaceOpacity"))
        assertEquals(.45f, outOfRange.displayDoc().float("inputSurfaceOpacity"))

        val negative = migrated(
            display = """{"enableBlurEffect":true,"backgroundEffectType":"glass","glassOpacity":-0.1}"""
        )
        assertEquals(.35f, negative.appearanceDoc().float("pageSurfaceOpacity"))

        // 字符串化的数字、JsonNull 都不算合法
        val stringy = migrated(
            display = """{"enableBlurEffect":true,"backgroundEffectType":"glass","glassOpacity":"0.35"}"""
        )
        assertEquals(.35f, stringy.appearanceDoc().float("pageSurfaceOpacity"))

        val nullValue = migrated(
            display = """{"enableBlurEffect":true,"backgroundEffectType":"blur","blurOpacity":null}"""
        )
        assertEquals(.45f, nullValue.appearanceDoc().float("pageSurfaceOpacity"))
    }

    // 合法的小透明度原样落地：0..1 里的值不该被抬到旧的下限去
    @Test
    fun `legal small alpha is written as is`() = runTest {
        val result = migrated(
            display = """{"enableBlurEffect":true,"backgroundEffectType":"blur","blurOpacity":0.3}"""
        )
        val appearance = result.appearanceDoc()

        assertEquals(.3f, appearance.float("pageSurfaceOpacity"))
        assertEquals(.3f, appearance.float("overlaySurfaceOpacity"))
        assertEquals(.3f, appearance.float("navigationGlassOpacity"))
        assertEquals(.3f, appearance.float("chatDockGlassOpacity"))
        assertEquals(.3f, appearance.float("chatBubbleSurfaceOpacity"))
        assertEquals(.3f, appearance.float("richContentSurfaceOpacity"))
        assertEquals(12f, appearance.float("pageSurfaceBlurRadius"))
    }

    // 旧版把这些类型当成「没有特效」，迁移后也不该凭空开出一套材质
    @Test
    fun `off missing switch and unknown type migrate nothing`() = runTest {
        val off = """{"enableBlurEffect":true,"backgroundEffectType":"off"}"""
        val offResult = migrated(display = off)
        assertNull(offResult[SettingsStore.ADVANCED_APPEARANCE_SETTING])
        assertEquals(off, offResult[SettingsStore.DISPLAY_SETTING])

        // 开关关着：类型写什么都一样
        val disabled = """{"enableBlurEffect":false,"backgroundEffectType":"blur"}"""
        val disabledResult = migrated(display = disabled)
        assertNull(disabledResult[SettingsStore.ADVANCED_APPEARANCE_SETTING])
        assertEquals(disabled, disabledResult[SettingsStore.DISPLAY_SETTING])

        listOf(
            // 认不出来的类型：旧版这时候本来就没有特效
            """{"enableBlurEffect":true,"backgroundEffectType":"hologram"}""",
            """{"enableBlurEffect":true,"backgroundEffectType":""}""",
            """{"enableBlurEffect":true,"backgroundEffectType":null}""",
            """{"enableBlurEffect":true,"backgroundEffectType":{"mode":"blur"}}""",
            // 开关是坏类型（数字、数组、null、认不出的字符串）：不猜成 true
            """{"enableBlurEffect":1,"backgroundEffectType":"blur"}""",
            """{"enableBlurEffect":["true"],"backgroundEffectType":"blur"}""",
            """{"enableBlurEffect":null,"backgroundEffectType":"blur"}""",
            """{"enableBlurEffect":"yes","backgroundEffectType":"blur"}""",
            // 类型是坏类型：数字、数组、布尔都不算写清楚的类型
            """{"enableBlurEffect":true,"backgroundEffectType":1}""",
            """{"enableBlurEffect":true,"backgroundEffectType":["blur"]}""",
            """{"enableBlurEffect":true,"backgroundEffectType":true}""",
        ).forEach { document ->
            assertNull(
                "不该被迁移：$document",
                migrated(display = document)[SettingsStore.ADVANCED_APPEARANCE_SETTING],
            )
        }

        // 没有 display 这份文档：两份都不动
        val advancedOff = """{"enableAdvancedAppearance":false}"""
        val missing = migrated(advanced = advancedOff)
        assertNull(missing[SettingsStore.DISPLAY_SETTING])
        assertEquals(advancedOff, missing[SettingsStore.ADVANCED_APPEARANCE_SETTING])

        // 旧版没写过类型键时模型默认 blur
        val defaulted = migrated(display = """{"enableBlurEffect":true}""")
        assertEquals("frosted", defaulted.appearanceDoc().text("pageSurfaceStyle"))
        assertEquals(12f, defaulted.appearanceDoc().float("pageSurfaceBlurRadius"))
    }

    // 用户自己配过高级外观：旧开关不能把那一套盖掉，只在 display 里记一笔「搬过了」
    @Test
    fun `advanced appearance that is already on is kept as it is`() = runTest {
        val advanced = """{"enableAdvancedAppearance":true,"pageSurfaceStyle":"mica","micaTintStrength":0.9}"""

        val result = migrated(display = legacyBlur, advanced = advanced)

        assertEquals(advanced, result[SettingsStore.ADVANCED_APPEARANCE_SETTING])
        val display = result.displayDoc()
        assertTrue(display.flag("legacyEffectsMigrated") == true)
        assertEquals(JsonPrimitive(true), display["enableBlurEffect"])
        assertFalse("inputSurfaceOpacity" in display)
        assertFalse("topBarBlurRadius" in display)
    }

    // 高级外观那份文档在、是合法对象，只是没写开关：当成模型默认的「关」，旧开关照常搬，
    // 文档里原有的其他字段一个不动。（开关写了但不是布尔是另一回事，那份整份保留、不迁移。）
    @Test
    fun `advanced appearance without the enable key migrates and keeps its own fields`() = runTest {
        val advanced = """{"micaTintStrength":0.9,"enableChatTextReadability":false}"""
        assertFalse(
            "enableAdvancedAppearance" in JsonInstant.parseToJsonElement(advanced).jsonObject
        )

        val result = migrated(display = legacyBlur, advanced = advanced)
        val doc = result.appearanceDoc()

        // 缺键按「关」处理：旧 blur 照常翻成毛玻璃 12dp
        assertEquals(JsonPrimitive(true), doc["enableAdvancedAppearance"])
        assertEquals("frosted", doc.text("pageSurfaceStyle"))
        assertEquals(12f, doc.float("pageSurfaceBlurRadius"))
        assertEquals(.45f, doc.float("pageSurfaceOpacity"))
        assertEquals("frosted", doc.text("chatDockSurfaceStyle"))
        assertEquals("translucent", doc.text("richContentStyle"))
        assertTrue(doc.flag("enableNavigationGlass") == true)

        // 原有的、跟这次迁移无关的字段原样留着
        assertEquals(.9f, doc.float("micaTintStrength"))
        assertEquals(JsonPrimitive(false), doc["enableChatTextReadability"])

        // display 那一份同样补继承值并记标记
        val display = result.displayDoc()
        assertTrue(display.flag("legacyEffectsMigrated") == true)
        assertEquals(JsonPrimitive(true), display["enableBlurEffect"])
        assertEquals(12f, display.float("inputBlurRadius"))
        assertEquals(.45f, display.float("inputSurfaceOpacity"))
        assertEquals(12f, display.float("topBarBlurRadius"))
        assertEquals(.45f, display.float("topBarSurfaceOpacity"))
    }

    // 页面磨砂半径的独立 backfill：留住既有的磨砂页面强度，其他高级字段一个不动
    @Test
    fun `page blur radius is backfilled from the global background radius once`() = runTest {
        val advanced = """{"enableAdvancedAppearance":true,"pageSurfaceStyle":"frosted","globalBackgroundBlurRadius":30.0}"""

        val result = migrated(advanced = advanced)
        val doc = result.appearanceDoc()

        assertEquals(30f, doc.float("pageSurfaceBlurRadius"))
        assertEquals(30f, doc.float("globalBackgroundBlurRadius"))
        assertEquals(
            setOf("enableAdvancedAppearance", "pageSurfaceStyle", "globalBackgroundBlurRadius", "pageSurfaceBlurRadius"),
            doc.keys,
        )
        // 没有旧开关要搬：display 那份不写
        assertNull(result[SettingsStore.DISPLAY_SETTING])

        // 第二次跑就已经有这个键了：不再补
        val twice = migrated(advanced = result[SettingsStore.ADVANCED_APPEARANCE_SETTING])
        assertEquals(
            result[SettingsStore.ADVANCED_APPEARANCE_SETTING],
            twice[SettingsStore.ADVANCED_APPEARANCE_SETTING],
        )
    }

    @Test
    fun `backfill stays out of the way for default broken or disabled values`() = runTest {
        listOf(
            // 等于新字段的默认值：不用补
            """{"enableAdvancedAppearance":true,"globalBackgroundBlurRadius":20}""",
            // 已经写过新字段
            """{"enableAdvancedAppearance":true,"globalBackgroundBlurRadius":30,"pageSurfaceBlurRadius":14}""",
            // 越界、字符串化、缺值
            """{"enableAdvancedAppearance":true,"globalBackgroundBlurRadius":90}""",
            """{"enableAdvancedAppearance":true,"globalBackgroundBlurRadius":"30"}""",
            """{"enableAdvancedAppearance":true}""",
            // 高级外观本来就关着
            """{"enableAdvancedAppearance":false,"globalBackgroundBlurRadius":30}""",
        ).forEach { advanced ->
            val result = migrated(advanced = advanced)
            assertEquals(
                "不该 backfill：$advanced",
                advanced,
                result[SettingsStore.ADVANCED_APPEARANCE_SETTING],
            )
        }
    }

    // 坏文档：不猜、不重写、也不当成缺失去补一份新的
    @Test
    fun `broken or non object documents are left alone`() = runTest {
        val advancedOff = """{"enableAdvancedAppearance":false}"""

        val badDisplay = migrated(display = "not json", advanced = advancedOff)
        assertEquals("not json", badDisplay[SettingsStore.DISPLAY_SETTING])
        assertEquals(advancedOff, badDisplay[SettingsStore.ADVANCED_APPEARANCE_SETTING])

        listOf("not json", "null", "[]").forEach { brokenAdvanced ->
            val result = migrated(display = legacyBlur, advanced = brokenAdvanced)
            assertEquals(legacyBlur, result[SettingsStore.DISPLAY_SETTING])
            assertEquals(brokenAdvanced, result[SettingsStore.ADVANCED_APPEARANCE_SETTING])
        }

        // JsonNull 的 display：同样不动
        val nullDisplay = migrated(display = "null", advanced = advancedOff)
        assertEquals("null", nullDisplay[SettingsStore.DISPLAY_SETTING])
        assertEquals(advancedOff, nullDisplay[SettingsStore.ADVANCED_APPEARANCE_SETTING])
        assertEquals(6, nullDisplay[SettingsStore.VERSION])

        // display 读不出来时，高级外观的 backfill 也不跑：只搬一半比不搬更难收拾
        val backfillCandidate = """{"enableAdvancedAppearance":true,"globalBackgroundBlurRadius":30}"""
        val brokenDisplay = migrated(display = "not json", advanced = backfillCandidate)
        assertEquals("not json", brokenDisplay[SettingsStore.DISPLAY_SETTING])
        assertEquals(backfillCandidate, brokenDisplay[SettingsStore.ADVANCED_APPEARANCE_SETTING])

        // 空串与只有空白：盘上确实写着这一份，只是读不出来 —— 保留，别当成缺失去补一份新的
        listOf("", "  ").forEach { blankAdvanced ->
            val result = migrated(display = legacyBlur, advanced = blankAdvanced)
            assertEquals(blankAdvanced, result[SettingsStore.ADVANCED_APPEARANCE_SETTING])
            assertEquals(legacyBlur, result[SettingsStore.DISPLAY_SETTING])
        }
    }

    // V6 跑两遍：结果不该变（两份文档都已经写过标记与继承值）
    @Test
    fun `upgrade is idempotent`() = runTest {
        val once = migrated(display = legacyBlur)

        val twice = migrated(
            display = once[SettingsStore.DISPLAY_SETTING],
            advanced = once[SettingsStore.ADVANCED_APPEARANCE_SETTING],
        )
        assertEquals(once[SettingsStore.DISPLAY_SETTING], twice[SettingsStore.DISPLAY_SETTING])
        assertEquals(
            once[SettingsStore.ADVANCED_APPEARANCE_SETTING],
            twice[SettingsStore.ADVANCED_APPEARANCE_SETTING],
        )
    }

    // V6 仍然按原样往 display 的原始 JSON 里写「已经搬过」的标记；模型里已经没有这个字段了，
    // 所以设置只要过一遍模型它就消失。V7 会直接把这个键删掉（见 PreferenceStoreV7MigrationTest）。
    @Test
    fun `the migrated marker is written by V6 but no longer survives the model`() = runTest {
        val once = migrated(display = legacyBlur)
        val displayJson = once[SettingsStore.DISPLAY_SETTING]!!
        assertTrue(JsonInstant.parseToJsonElement(displayJson).jsonObject.flag("legacyEffectsMigrated") == true)

        val roundTripped = JsonInstant.encodeToString(
            DisplaySetting.serializer(),
            JsonInstant.decodeFromString(DisplaySetting.serializer(), displayJson),
        )
        val document = JsonInstant.parseToJsonElement(roundTripped).jsonObject
        // 退役字段一个都留不下来：显示设置里不再有它们的 @SerialName
        listOf(
            "legacyEffectsMigrated",
            "enableBlurEffect",
            "backgroundEffectType",
            "blurOpacity",
            "glassOpacity",
            "enableTopBarBlur",
            "topBarBlurRadius",
            "topBarSurfaceOpacity",
            "inputBlurRadius",
            "inputSurfaceOpacity",
        ).forEach { key -> assertFalse("模型里不该再有 $key", key in document) }
    }

    @Test
    fun `other preferences are left untouched and the version moves to six`() = runTest {
        val preferences = mutablePreferencesOf(
            SettingsStore.VERSION to 5,
            SettingsStore.THEME_ID to "dark",
            SettingsStore.DISPLAY_SETTING to legacyBlur,
        )

        val result = migration.migrate(preferences)

        assertEquals("dark", result[SettingsStore.THEME_ID])
        assertEquals(6, result[SettingsStore.VERSION])
    }

    @Test
    fun `already migrated preferences are not migrated again`() = runTest {
        assertTrue(migration.shouldMigrate(mutablePreferencesOf()))
        assertTrue(migration.shouldMigrate(mutablePreferencesOf(SettingsStore.VERSION to 5)))
        assertFalse(migration.shouldMigrate(mutablePreferencesOf(SettingsStore.VERSION to 6)))
    }

    // 开关与 marker 只接受真实 JSON 布尔，不把字符串、数字等误认成 true。
    @Test
    fun `migration switches require actual JSON booleans`() = runTest {
        listOf("\"true\"", "\"false\"", "\"true-ish\"", "1", "null", "{}", "[]").forEach { bad ->
            val display = """{"enableBlurEffect":$bad,"backgroundEffectType":"blur"}"""
            val result = migrated(display = display)
            assertEquals(display, result[SettingsStore.DISPLAY_SETTING])
            assertNull(result[SettingsStore.ADVANCED_APPEARANCE_SETTING])
        }

        val quotedMarker = """{"enableBlurEffect":true,"backgroundEffectType":"blur","legacyEffectsMigrated":"true"}"""
        val result = migrated(display = quotedMarker)
        assertEquals(JsonPrimitive(true), result.displayDoc()["legacyEffectsMigrated"])
        assertEquals("frosted", result.appearanceDoc().text("pageSurfaceStyle"))

        listOf("\"true\"", "1", "null", "{}", "[]").forEach { bad ->
            val advanced = """{"enableAdvancedAppearance":$bad,"pageSurfaceStyle":"mica"}"""
            val broken = migrated(display = legacyBlur, advanced = advanced)
            assertEquals(advanced, broken[SettingsStore.ADVANCED_APPEARANCE_SETTING])
            assertEquals(legacyBlur, broken[SettingsStore.DISPLAY_SETTING])
        }
    }
}
