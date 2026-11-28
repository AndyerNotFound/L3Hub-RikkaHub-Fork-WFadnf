package me.rerere.rikkahub.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.JsonInstant

/**
 * 旧的全局模糊特效开关搬进高级外观。
 *
 * 旧版把特效放在 `display_setting` 里：`enableBlurEffect` 配 `backgroundEffectType`（blur/glass/off），
 * 底色不透明度走 `blurOpacity` 或 `glassOpacity`；新版由 `advanced_appearance_setting` 的材质字段描述。
 * 旧字段原样留在 `display_setting` 里——备份、降级都还要读它们，迁移不负责清理。
 *
 * 一个旧开关管的是整屏，所以一次迁移落两份文档：
 * - `display_setting` 补输入框与顶栏的继承值（`inputSurfaceOpacity`/`inputBlurRadius`、
 *   `topBarSurfaceOpacity`/`topBarBlurRadius`），并写 `legacyEffectsMigrated`；
 * - `advanced_appearance_setting` 补页面 / 输入区 / 蒙层 / 导航 / 浮动 Dock / 消息气泡 / 富内容卡片
 *   的材质、透明度与半径。
 *
 * 不碰的东西：`chatMessageBackgroundStyle`、三个角色的 `*ChatBubbleStyleOverrides`、
 * `customizeThinkingToolCards`；各表面「要不要显示」的功能开关（`showTopBarBackground` 与
 * `showAssistantBubble`）也不在这里开。导航玻璃和 Dock 玻璃原本就在全局效果覆盖范围，需保持开启。
 * 气泡的透明度与半径走高级外观的新字段，带角色显式参数时角色那边优先，旧存的 SOLID 保持实色。
 *
 * 只改必须改的字段：
 * - 旧开关没开、类型是 off 或认不出来的值：不动；
 * - 高级外观已经开着：两份都整份保留，只在 display 里记「已经搬过」；
 * - 透明度不是 0..1 的合法数：用旧档自己的默认值（blur .45 / glass .35），别让材质没有底色。
 *
 * 「键不在」和「写着但读不出来」是两回事：
 * - 键不在（盘上根本没有这个 key）：当成没有这份设置，照常按旧开关写一份；
 * - 空串、`null`、数组、坏 JSON、或者 `enableAdvancedAppearance` 不是布尔：盘上确实写着这一份，
 *   只是读不出来 —— 整份保留，不猜、不重写，也不当成缺失去补一份新的。任何一份读不出来，两份都不动
 *   （只搬一半比不搬更难收拾）：display 读不出来时，高级外观的 backfill 也不跑。
 * 开关只认 JSON 布尔 `true` / `false`；字符串 `"true"`、`"false"`、`"yes"`、数字、`null` 与对象都
 * 不是开关值，不能据此迁移或写标记。`legacyEffectsMigrated` 也遵守同一条严格类型规则。
 *
 * 半径按旧档落：毛玻璃 12dp 写 `pageSurfaceBlurRadius` 这一族（不是 `globalBackgroundBlurRadius`，
 * 那个是壁纸图片自己的模糊），液态玻璃 16dp 写 `*LiquidGlassBlurRadius` 这一族。
 *
 * `legacyEffectsMigrated` 是「已搬过一次」的标记：V6 只按原始 JSON 里它是不是 true 判断。
 * 模型里这个字段默认 true，落盘又开着 `encodeDefaults`，所以它一旦被读进模型再写回去，文件里
 * 一定有一个真的布尔 `true`。有了它，用户之后自己关掉高级外观、再从那之后的备份恢复，
 * 旧开关不会把特效重新打开。
 *
 * 另有一条独立的、一次性的 backfill：既有高级外观里 `pageSurfaceBlurRadius` 还没有这个键、
 * 而 `globalBackgroundBlurRadius` 是合法非默认值时，把后者抄过去，让既有的磨砂页面与壁纸强度
 * 不变。只写这一个键，其他高级字段一个不动；写完键就存在，下次自然不再补。
 */
class PreferenceStoreV6Migration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[SettingsStore.VERSION]
        return version == null || version < 6
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()

        val migrated = runCatching {
            migrateLegacyDisplayEffects(
                displayJson = prefs[SettingsStore.DISPLAY_SETTING],
                advancedJson = prefs[SettingsStore.ADVANCED_APPEARANCE_SETTING],
            )
        }.getOrDefault(MigratedDisplayEffects.NoChange)

        // null 表示那一份不用改：盘上那份字符串（含读不出来的坏数据）原样留着
        migrated.displayJson?.let { prefs[SettingsStore.DISPLAY_SETTING] = it }
        migrated.appearanceJson?.let { prefs[SettingsStore.ADVANCED_APPEARANCE_SETTING] = it }

        prefs[SettingsStore.VERSION] = 6
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}
}

/**
 * 一次迁移要落下去的两份文档。
 *
 * 字段为 null 表示那一份不用改（没有旧开关、已经开过高级外观、类型是 off、JSON 读不出来）。
 * 两份都可能同时有值，也可能一份有值另一份没有——判断统一在 [migrateLegacyDisplayEffects] 里做，
 * 调用方只管把非 null 的那份写回去。
 */
internal data class MigratedDisplayEffects(
    val displayJson: String? = null,
    val appearanceJson: String? = null,
) {
    companion object {
        /** 两份都不改。 */
        val NoChange = MigratedDisplayEffects()
    }
}

// 旧 BackgroundEffectType 的落盘值。模型已经随高级外观下线，这些字符串只剩 V6 自己还要读。
private const val LEGACY_EFFECT_BLUR = "blur"
private const val LEGACY_EFFECT_GLASS = "glass"

/** 旧的事件类型缺键时模型默认 blur。 */
private const val LEGACY_EFFECT_DEFAULT = LEGACY_EFFECT_BLUR

// 旧 BackgroundSurfaceStyle 的落盘值：同样只剩 V6 往原始 JSON 里写。
private const val SURFACE_FROSTED = "frosted"
private const val SURFACE_LIQUID_GLASS = "liquid_glass"

// 旧 RichContentStyle 的落盘值；毛玻璃沿用既有的 translucent。
private const val RICH_CONTENT_TRANSLUCENT = "translucent"
private const val RICH_CONTENT_LIQUID_GLASS = "liquid_glass"

// 旧 blur 映射毛玻璃 12dp，旧 glass 映射液态玻璃 16dp
private const val FROSTED_BLUR_RADIUS = 12f
private const val LIQUID_GLASS_BLUR_RADIUS = 16f

// 旧档自己的默认透明度：blurOpacity / glassOpacity 在模型里的默认值
private const val LEGACY_BLUR_DEFAULT_OPACITY = .45f
private const val LEGACY_GLASS_DEFAULT_OPACITY = .35f

// 页面磨砂半径的合法区间与默认值（退役前的 AdvancedAppearanceSetting.pageSurfaceBlurRadius）
private val PAGE_BLUR_RADIUS_RANGE = 0f..40f
private const val DEFAULT_PAGE_BLUR_RADIUS = 20f

// display_setting 的键
private const val LEGACY_ENABLED_KEY = "enableBlurEffect"
private const val LEGACY_EFFECT_TYPE_KEY = "backgroundEffectType"
private const val LEGACY_BLUR_OPACITY_KEY = "blurOpacity"
private const val LEGACY_GLASS_OPACITY_KEY = "glassOpacity"
private const val LEGACY_MIGRATED_KEY = "legacyEffectsMigrated"
private const val DISPLAY_INPUT_OPACITY_KEY = "inputSurfaceOpacity"
private const val DISPLAY_INPUT_BLUR_RADIUS_KEY = "inputBlurRadius"
private const val DISPLAY_TOP_BAR_OPACITY_KEY = "topBarSurfaceOpacity"
private const val DISPLAY_TOP_BAR_BLUR_RADIUS_KEY = "topBarBlurRadius"

// advanced_appearance_setting 的键
private const val ADVANCED_ENABLED_KEY = "enableAdvancedAppearance"
private const val PAGE_SURFACE_STYLE_KEY = "pageSurfaceStyle"
private const val PAGE_SURFACE_OPACITY_KEY = "pageSurfaceOpacity"
private const val PAGE_SURFACE_BLUR_RADIUS_KEY = "pageSurfaceBlurRadius"
private const val PAGE_LIQUID_GLASS_BLUR_RADIUS_KEY = "pageLiquidGlassBlurRadius"
private const val COMPOSER_SURFACE_STYLE_KEY = "composerSurfaceStyle"
private const val OVERLAY_SURFACE_STYLE_KEY = "overlaySurfaceStyle"
private const val OVERLAY_SURFACE_OPACITY_KEY = "overlaySurfaceOpacity"
private const val OVERLAY_SURFACE_BLUR_RADIUS_KEY = "overlaySurfaceBlurRadius"
private const val OVERLAY_LIQUID_GLASS_BLUR_RADIUS_KEY = "overlayLiquidGlassBlurRadius"
private const val NAVIGATION_SURFACE_STYLE_KEY = "navigationSurfaceStyle"
private const val ENABLE_NAVIGATION_GLASS_KEY = "enableNavigationGlass"
private const val NAVIGATION_GLASS_OPACITY_KEY = "navigationGlassOpacity"
private const val NAVIGATION_GLASS_BLUR_RADIUS_KEY = "navigationGlassBlurRadius"
private const val NAVIGATION_LIQUID_GLASS_BLUR_RADIUS_KEY = "navigationLiquidGlassBlurRadius"
private const val CHAT_DOCK_SURFACE_STYLE_KEY = "chatDockSurfaceStyle"
private const val CHAT_DOCK_GLASS_OPACITY_KEY = "chatDockGlassOpacity"
private const val CHAT_DOCK_GLASS_BLUR_RADIUS_KEY = "chatDockGlassBlurRadius"
private const val CHAT_BUBBLE_STYLE_KEY = "chatBubbleStyle"
private const val CHAT_BUBBLE_SURFACE_OPACITY_KEY = "chatBubbleSurfaceOpacity"
private const val CHAT_BUBBLE_BLUR_RADIUS_KEY = "chatBubbleBlurRadius"
private const val RICH_CONTENT_STYLE_KEY = "richContentStyle"
private const val RICH_CONTENT_SURFACE_OPACITY_KEY = "richContentSurfaceOpacity"
private const val RICH_CONTENT_SURFACE_BLUR_RADIUS_KEY = "richContentSurfaceBlurRadius"
private const val GLOBAL_BACKGROUND_BLUR_RADIUS_KEY = "globalBackgroundBlurRadius"

/** 旧开关翻出来的材质：落盘名、半径、旧档默认透明度，以及旧档读哪一项透明度。 */
private data class LegacyMaterial(
    val surfaceStyle: String,
    val richContentStyle: String,
    val blurRadius: Float,
    val defaultOpacity: Float,
    val opacityKey: String,
    val liquidGlass: Boolean,
)

private val LEGACY_BLUR_MATERIAL = LegacyMaterial(
    surfaceStyle = SURFACE_FROSTED,
    richContentStyle = RICH_CONTENT_TRANSLUCENT,
    blurRadius = FROSTED_BLUR_RADIUS,
    defaultOpacity = LEGACY_BLUR_DEFAULT_OPACITY,
    opacityKey = LEGACY_BLUR_OPACITY_KEY,
    liquidGlass = false,
)

private val LEGACY_GLASS_MATERIAL = LegacyMaterial(
    surfaceStyle = SURFACE_LIQUID_GLASS,
    richContentStyle = RICH_CONTENT_LIQUID_GLASS,
    blurRadius = LIQUID_GLASS_BLUR_RADIUS,
    defaultOpacity = LEGACY_GLASS_DEFAULT_OPACITY,
    opacityKey = LEGACY_GLASS_OPACITY_KEY,
    liquidGlass = true,
)

/** 一次旧开关迁移要写进两份文档的字段。 */
private data class LegacyPatches(
    val display: Map<String, JsonElement>,
    val appearance: Map<String, JsonElement>,
)

/**
 * 把 `display_setting` 里的旧特效开关翻成新版两份设置。
 *
 * 返回两份新 JSON；某一份为 null 表示那一份不需要改。旧字段一律不删。
 *
 * 「这一份不在」：参数为 null。除此之外的字符串都算盘上写着这一份，读不出来就整份保留。
 */
internal fun migrateLegacyDisplayEffects(
    displayJson: String?,
    advancedJson: String?,
): MigratedDisplayEffects {
    return try {
        // display 读不出来（坏字符串、空串、JsonNull、数组）：整份保留，两份都不碰
        val display = displayJson.parseObjectOrNull()
        if (displayJson != null && display == null) return MigratedDisplayEffects.NoChange
        // 高级外观同样要分清「键不在」和「写着但读不出来」；读不出来时整份保留，两份都不碰
        val appearanceState = declaredAppearance(advancedJson)
            ?: return MigratedDisplayEffects.NoChange
        val appearance = appearanceState.document

        val legacy = display?.let { legacyPatches(it, appearanceState.enabled) }
        val appearancePatch: Map<String, JsonElement> = buildMap<String, JsonElement> {
            legacy?.let { putAll(it.appearance) }
            // 既有高级外观的页面磨砂强度：与旧开关无关，缺才补，补完就不再动
            appearance?.let { putAll(backfillPageSurfaceBlurRadius(it, appearanceState.enabled)) }
        }
        val displayPatch = legacy?.display.orEmpty()

        MigratedDisplayEffects(
            displayJson = display
                ?.takeIf { displayPatch.isNotEmpty() }
                ?.let { JsonInstant.encodeToString(JsonObject(it + displayPatch)) },
            appearanceJson = appearancePatch
                .takeIf { it.isNotEmpty() }
                ?.let { patch ->
                    JsonInstant.encodeToString(
                        JsonObject((appearance ?: JsonObject(emptyMap())) + patch)
                    )
                },
        )
    } catch (e: Exception) {
        // 读不出来就当没这回事，盘上的字符串原样留着
        MigratedDisplayEffects.NoChange
    }
}

/**
 * 高级外观那一份的读盘结果。
 *
 * [document] 为 null 表示键根本不在盘上；[enabled] 是盘上写清楚的开关值，缺键按模型默认的「关」，
 * 键在却不是布尔时整个结果为 null —— 那是坏档，调用方整份保留，不猜它是开还是关。
 */
private data class DeclaredAppearance(
    val document: JsonObject?,
    val enabled: Boolean,
)

/**
 * 读 `advanced_appearance_setting`：键不在返回「没有这份设置」；写着却读不出对象、
 * 或者 `enableAdvancedAppearance` 不是布尔，返回 null。
 */
private fun declaredAppearance(advancedJson: String?): DeclaredAppearance? {
    if (advancedJson == null) return DeclaredAppearance(document = null, enabled = false)
    val document = advancedJson.parseObjectOrNull() ?: return null
    val declared = document[ADVANCED_ENABLED_KEY]
        ?: return DeclaredAppearance(document = document, enabled = false)
    val enabled = declared.jsonBooleanOrNull() ?: return null
    return DeclaredAppearance(document = document, enabled = enabled)
}

/**
 * 旧开关 → 两份补丁。
 *
 * 返回 null 表示这次没什么可搬的：搬过了、开关没开、类型是 off、认不出来的值。
 */
private fun legacyPatches(display: JsonObject, appearanceEnabled: Boolean): LegacyPatches? {
    // 搬过一次就不再搬：用户之后自己关掉高级外观，旧开关也不该把它重新打开
    if (display[LEGACY_MIGRATED_KEY]?.jsonBooleanOrNull() == true) return null
    if (display[LEGACY_ENABLED_KEY]?.jsonBooleanOrNull() != true) return null

    val material = when (display.legacyEffectType()) {
        LEGACY_EFFECT_BLUR -> LEGACY_BLUR_MATERIAL
        LEGACY_EFFECT_GLASS -> LEGACY_GLASS_MATERIAL
        // off 或者认不出来的类型：旧版这时候本来就没有特效，别凭空开一套
        else -> return null
    }
    // 合法 0..1 沿用，坏值/缺值用旧档真实默认，避免迁移后没有底色
    val alpha = display.legalUnitValue(material.opacityKey) ?: material.defaultOpacity
    val migratedMarker = mapOf(LEGACY_MIGRATED_KEY to JsonPrimitive(true))

    // 高级外观本来就开着：那份文档整份保留，旧开关不覆盖；只在 display 里记一笔「搬过了」
    if (appearanceEnabled) return LegacyPatches(display = migratedMarker, appearance = emptyMap())

    return LegacyPatches(
        display = migratedMarker + inheritedDisplaySurface(material, alpha),
        appearance = appearanceSurface(material, alpha),
    )
}

/**
 * `display_setting` 只补输入框与顶栏的继承值，其余原字段一律留着。
 *
 * `showTopBarBackground` 与助手气泡的显示开关不在这里打开——旧开关管的是「有没有特效」，
 * 不是「这个部件显不显示」。导航玻璃和 Dock 玻璃在旧版全局效果管辖范围内，迁移时要启用这两项材质。
 */
private fun inheritedDisplaySurface(
    material: LegacyMaterial,
    alpha: Float,
): Map<String, JsonElement> = mapOf(
    DISPLAY_INPUT_BLUR_RADIUS_KEY to JsonPrimitive(material.blurRadius),
    DISPLAY_INPUT_OPACITY_KEY to JsonPrimitive(alpha),
    DISPLAY_TOP_BAR_BLUR_RADIUS_KEY to JsonPrimitive(material.blurRadius),
    DISPLAY_TOP_BAR_OPACITY_KEY to JsonPrimitive(alpha),
)

/**
 * 高级外观：整屏所有吃到旧开关的表面。
 *
 * 材质与半径成对写：毛玻璃那一档写 `*SurfaceBlurRadius` / `navigationGlassBlurRadius`，
 * 液态玻璃那一档写 `*LiquidGlassBlurRadius`，避免给另一种材质留一个用不上的半径。
 * 液态玻璃不指定预设，交给默认那套，不强制选回「原版」。
 */
private fun appearanceSurface(
    material: LegacyMaterial,
    alpha: Float,
): Map<String, JsonElement> = buildMap<String, JsonElement> {
    put(ADVANCED_ENABLED_KEY, JsonPrimitive(true))
    put(PAGE_SURFACE_STYLE_KEY, JsonPrimitive(material.surfaceStyle))
    put(PAGE_SURFACE_OPACITY_KEY, JsonPrimitive(alpha))
    put(COMPOSER_SURFACE_STYLE_KEY, JsonPrimitive(material.surfaceStyle))
    put(OVERLAY_SURFACE_STYLE_KEY, JsonPrimitive(material.surfaceStyle))
    put(OVERLAY_SURFACE_OPACITY_KEY, JsonPrimitive(alpha))
    put(NAVIGATION_SURFACE_STYLE_KEY, JsonPrimitive(material.surfaceStyle))
    put(ENABLE_NAVIGATION_GLASS_KEY, JsonPrimitive(true))
    put(NAVIGATION_GLASS_OPACITY_KEY, JsonPrimitive(alpha))
    if (material.liquidGlass) {
        put(PAGE_LIQUID_GLASS_BLUR_RADIUS_KEY, JsonPrimitive(material.blurRadius))
        put(OVERLAY_LIQUID_GLASS_BLUR_RADIUS_KEY, JsonPrimitive(material.blurRadius))
        put(NAVIGATION_LIQUID_GLASS_BLUR_RADIUS_KEY, JsonPrimitive(material.blurRadius))
    } else {
        // 页面走毛玻璃时页面读的是 pageSurfaceBlurRadius，不是壁纸自己的 globalBackgroundBlurRadius
        put(PAGE_SURFACE_BLUR_RADIUS_KEY, JsonPrimitive(material.blurRadius))
        put(OVERLAY_SURFACE_BLUR_RADIUS_KEY, JsonPrimitive(material.blurRadius))
        put(NAVIGATION_GLASS_BLUR_RADIUS_KEY, JsonPrimitive(material.blurRadius))
    }
    // 聊天浮动 Dock / 气泡 / 富内容卡片（思考卡跟 richContentMaterial 走）
    put(CHAT_DOCK_SURFACE_STYLE_KEY, JsonPrimitive(material.surfaceStyle))
    put("enableChatDockGlass", JsonPrimitive(true))
    put(CHAT_DOCK_GLASS_OPACITY_KEY, JsonPrimitive(alpha))
    put(CHAT_DOCK_GLASS_BLUR_RADIUS_KEY, JsonPrimitive(material.blurRadius))
    // 气泡材质沿用既有 ChatBubbleStyle 字段。
    put(CHAT_BUBBLE_STYLE_KEY, JsonPrimitive(material.surfaceStyle))
    put(CHAT_BUBBLE_SURFACE_OPACITY_KEY, JsonPrimitive(alpha))
    put(CHAT_BUBBLE_BLUR_RADIUS_KEY, JsonPrimitive(material.blurRadius))
    put(RICH_CONTENT_STYLE_KEY, JsonPrimitive(material.richContentStyle))
    put(RICH_CONTENT_SURFACE_OPACITY_KEY, JsonPrimitive(alpha))
    put(RICH_CONTENT_SURFACE_BLUR_RADIUS_KEY, JsonPrimitive(material.blurRadius))
}

/**
 * 既有高级外观的页面磨砂半径 backfill。
 *
 * 拆字段之前，页面的模糊和壁纸图片的模糊共用 `globalBackgroundBlurRadius`；拆开之后页面读
 * `pageSurfaceBlurRadius`。老文档里后者是缺的（模型默认 20dp），前者却被调过——这时把前者的值
 * 抄过去，既有的磨砂页面和壁纸强度才不会变。只写这一个键，其他高级字段一个不动。
 */
private fun backfillPageSurfaceBlurRadius(
    appearance: JsonObject,
    appearanceEnabled: Boolean,
): Map<String, JsonElement> {
    if (!appearanceEnabled) return emptyMap()
    // 新字段已经写过：一次性，不再推算
    if (appearance.containsKey(PAGE_SURFACE_BLUR_RADIUS_KEY)) return emptyMap()
    val legacyRadius = (appearance[GLOBAL_BACKGROUND_BLUR_RADIUS_KEY] as? JsonPrimitive)
        ?.takeUnless { it.isString }
        ?.floatOrNull
        ?.takeIf { it.isFinite() && it in PAGE_BLUR_RADIUS_RANGE && it != DEFAULT_PAGE_BLUR_RADIUS }
        ?: return emptyMap()
    return mapOf(PAGE_SURFACE_BLUR_RADIUS_KEY to JsonPrimitive(legacyRadius))
}

/** 只认 JSON 的 true / false 原始布尔字面量；字符串数字化布尔不算开关。 */
private fun JsonElement?.jsonBooleanOrNull(): Boolean? =
    (this as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull

/** 旧类型字段：缺键是旧模型默认 blur；空串、JsonNull、非字符串一律当成认不出来。 */
private fun JsonObject.legacyEffectType(): String? {
    val raw = this[LEGACY_EFFECT_TYPE_KEY] ?: return LEGACY_EFFECT_DEFAULT
    return (raw as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()
}

/** 只认存在且合法的透明度：有限值并且在 0..1 里。缺字段、字符串化的、坏值都不认。 */
private fun JsonObject.legalUnitValue(key: String): Float? =
    (this[key] as? JsonPrimitive)
        ?.takeUnless { it.isString }
        ?.floatOrNull
        ?.takeIf { it.isFinite() && it in 0f..1f }

/** 解析成对象；缺失、坏字符串、`null`、数组都返回 null。V7 那条清理也用它，两条路判断一致。 */
internal fun String?.parseObjectOrNull(): JsonObject? =
    this?.takeIf { it.isNotBlank() }?.let {
        runCatching { JsonInstant.parseToJsonElement(it) as? JsonObject }.getOrNull()
    }
