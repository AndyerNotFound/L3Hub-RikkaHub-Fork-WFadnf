package me.rerere.rikkahub.data.datastore

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ProviderSetting
import me.rerere.asr.ASRProviderSetting
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.ExecutionPermission
import me.rerere.rikkahub.data.model.InspirationAppearance
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.model.ToolOverride
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.ui.theme.CustomTheme
import me.rerere.rikkahub.ui.theme.PaletteColors
import me.rerere.rikkahub.ui.theme.PaletteSetting
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchServiceOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class SettingsFieldsTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `every settings property has exactly one field`() {
        val properties = Settings.serializer().descriptor.elementNames.sorted()

        assertEquals(properties, SETTINGS_FIELDS.map { it.property.name }.sorted())
    }

    @Test
    fun `settings survive a round trip through preferences`() {
        // 样本里和默认值相同的字段测不出读写是否对得上
        val defaults = emptyPreferences().toSettings()
        assertEquals(
            emptyList<String>(),
            SETTINGS_FIELDS.filterNot { it.changed(defaults, SAMPLE) }.map { it.property.name },
        )

        val preferences = mutablePreferencesOf().apply { putSettings(SAMPLE) }

        assertEquals(SAMPLE, preferences.toSettings())
    }

    // 这里的 key 名和取值就是用户盘上的格式，改动它们等于丢掉已有的设置
    @Test
    fun `stored format stays compatible with existing data`() {
        val stored = mutablePreferencesOf().apply { putSettings(SAMPLE) }.asMap().mapKeys { it.key.name }

        assertEquals(STORED_KEYS, stored.keys)
        assertEquals(false, stored["dynamic_color"])
        assertEquals(9090, stored["web_server_port"])
        assertEquals(1.5f, stored["default_tts_playback_speed"])
        assertEquals("title", stored["title_prompt"])
        assertEquals("00000000-0000-0000-0000-000000000001", stored["chat_model"])
        assertEquals("LOW", stored["fast_model_reasoning_level"])
        assertEquals("""[{"id":"00000000-0000-0000-0000-000000000007","name":"tag"}]""", stored["assistant_tags"])
        assertEquals("""{"resultSize":3}""", stored["search_common"])
        assertEquals(
            """[{"name":"clipboard_tool","description":"overridden description","instructions":"overridden instructions","parameters":"{\"type\":\"object\",\"properties\":{}}"}]""",
            stored["tool_overrides"],
        )
    }

    @Test
    fun `old assistants keep their data when workspace permissions are migrated`() {
        val assistantsJson = """
            [
                {"id":"00000000-0000-0000-0000-000000000001","name":"Legacy write","executionPermission":"READ_WRITE","planMode":false},
                {"id":"00000000-0000-0000-0000-000000000002","name":"Legacy plan","executionPermission":"FULL","planMode":true}
            ]
        """.trimIndent()
        val preferences = mutablePreferencesOf(SettingsStore.ASSISTANTS to assistantsJson)

        val settings = preferences.toSettings()
        assertEquals(listOf(id(1), id(2)), settings.assistants.map { it.id })
        assertEquals(listOf("Legacy write", "Legacy plan"), settings.assistants.map { it.name })
        assertEquals(
            listOf(ExecutionPermission.APPROVAL, ExecutionPermission.READ_ONLY),
            settings.assistants.map { it.executionPermission },
        )

        preferences.putSettings(settings)
        val stored = preferences[SettingsStore.ASSISTANTS].orEmpty()
        assertFalse(stored.contains("READ_WRITE"))
        assertFalse(stored.contains("planMode"))
        assertEquals(settings.assistants, preferences.toSettings().assistants)
    }

    @Test
    fun `missing keys read as first launch values`() {
        val settings = emptyPreferences().toSettings()

        assertEquals(DEFAULT_AUTO_MODEL_ID, settings.chatModelId)
        assertEquals(DEFAULT_AUTO_MODEL_ID, settings.fastModelId)
        assertEquals(DEFAULT_AUTO_MODEL_ID, settings.translateModeId)
        assertEquals(DEFAULT_MODE_INJECTIONS, settings.modeInjections)
        assertEquals(DEFAULT_ASSISTANT_ID, settings.assistantId)
        assertEquals(DEFAULT_SYSTEM_TTS_ID, settings.selectedTTSProviderId)
        assertEquals(null, settings.selectedASRProviderId)
        assertEquals(null, settings.memoryModelId)
        assertEquals(true, settings.dynamicColor)
        assertEquals(AdvancedAppearanceSetting(), settings.advancedAppearanceSetting)
        assertEquals(8080, settings.webServerPort)
        assertEquals(false, settings.sshServerEnabled)
        assertEquals(2222, settings.sshServerPort)
        assertEquals(false, settings.sshServerAllowLan)
    }

    @Test
    fun `unknown reasoning level reads as auto`() {
        val preferences = mutablePreferencesOf(SettingsStore.FAST_MODEL_REASONING_LEVEL to "REMOVED_LEVEL")

        assertEquals(ReasoningLevel.AUTO, preferences.toSettings().fastModelReasoningLevel)
    }

    @Test
    fun `clearing the asr provider removes its key`() {
        val preferences = mutablePreferencesOf().apply { putSettings(SAMPLE) }

        preferences.putSettings(SAMPLE.copy(selectedASRProviderId = null), persisted = SAMPLE)

        assertFalse(SettingsStore.SELECTED_ASR_PROVIDER in preferences)
        assertEquals(null, preferences.toSettings().selectedASRProviderId)
    }

    @Test
    fun `only changed fields are written`() {
        val preferences = mutablePreferencesOf().apply { putSettings(SAMPLE) }
        // 盘上的值故意和已落盘的那份不一致：没变的字段如果也被重写，它就会变回去
        preferences[SettingsStore.TITLE_PROMPT] = "on disk"

        preferences.putSettings(SAMPLE.copy(themeId = "changed"), persisted = SAMPLE)

        assertEquals("changed", preferences[SettingsStore.THEME_ID])
        assertEquals("on disk", preferences[SettingsStore.TITLE_PROMPT])
    }

    // 高级外观默认是关的：旧版本盘上没有这个 key 时读出来就是「不启用」，不会把新外观强加给老用户
    @Test
    fun `advanced appearance is off unless it was turned on`() {
        val fromEmpty = emptyPreferences().toSettings().advancedAppearanceSetting
        assertFalse(fromEmpty.enableAdvancedAppearance)
        assertEquals(AdvancedAppearanceSetting(), fromEmpty)

        // 只写了部分字段的旧 JSON：缺的字段回落到默认值，多出来的键被忽略
        val legacy = mutablePreferencesOf(
            SettingsStore.ADVANCED_APPEARANCE_SETTING to
                """{"enableAdvancedAppearance":true,"pageSurfaceStyle":"mica","removedOption":42}"""
        ).toSettings().advancedAppearanceSetting

        assertTrue(legacy.enableAdvancedAppearance)
        assertEquals(BackgroundSurfaceStyle.MICA, legacy.pageSurfaceStyle)
        assertEquals(AdvancedAppearanceSetting().overlaySurfaceStyle, legacy.overlaySurfaceStyle)
        assertEquals(AdvancedAppearanceSetting().chatTextLineHeightRatio, legacy.chatTextLineHeightRatio, 0f)
    }

    @Test
    fun `advanced appearance survives a preferences round trip`() {
        val appearance = AdvancedAppearanceSetting(
            enableAdvancedAppearance = true,
            enableGlobalBackground = true,
            globalBackground = "uploads/bg.webp",
            globalBackgroundOpacity = .35f,
            applyGlobalBackgroundToChat = true,
            applyGlobalBackgroundToFullscreenPreview = false,
            pageSurfaceStyle = BackgroundSurfaceStyle.FROSTED,
            overlaySurfaceStyle = BackgroundSurfaceStyle.LIQUID_GLASS,
            navigationSurfaceStyle = BackgroundSurfaceStyle.ACRYLIC,
            textColorMode = TextColorMode.SYSTEM_WALLPAPER,
            colorStyle = AppearanceColorStyle.EXPRESSIVE,
            gradientRendererMode = GradientRendererMode.AGSL,
            chatBubbleStyle = ChatBubbleStyle.LIQUID_GLASS,
            richContentStyle = RichContentStyle.OUTLINED,
            chatTextLineHeightRatio = 1.4f,
            chatParagraphSpacingRatio = .5f,
            wallpaperTextSeedArgb = 0xFF112233L,
            wallpaperTextSeedWallpaperId = 7,
            inspirationAppearance = InspirationAppearance(followRichContent = false, cornerRadius = 20f),
        )
        val settings = SAMPLE.copy(advancedAppearanceSetting = appearance)

        val stored = mutablePreferencesOf().apply { putSettings(settings) }

        assertEquals(appearance, stored.toSettings().advancedAppearanceSetting)
        // 枚举按 @SerialName 落盘，改名等于丢掉用户已经存下的选择
        val raw = stored[SettingsStore.ADVANCED_APPEARANCE_SETTING].orEmpty()
        assertTrue(raw.contains("\"frosted\""))
        assertTrue(raw.contains("\"liquid_glass\""))
        assertTrue(raw.contains("\"acrylic\""))
        assertTrue(raw.contains("\"system_wallpaper\""))
        assertTrue(raw.contains("\"expressive\""))
        assertTrue(raw.contains("\"agsl\""))
        assertTrue(raw.contains("\"outlined\""))
    }

    // 盘上越界或非有限的值在读盘规整时收回范围，界面拿到的永远是能直接画的值
    @Test
    fun `advanced appearance inputs are clamped on read`() {
        val stored = mutablePreferencesOf(
            SettingsStore.ADVANCED_APPEARANCE_SETTING to """
                {
                  "micaTintStrength": 5.0,
                  "globalBackground": "   ",
                  "globalBackgroundOpacity": -1.0,
                  "globalBackgroundBlurRadius": 999.0,
                  "pageSurfaceOpacity": 0.01,
                  "chatTextLineHeightRatio": 9.0,
                  "chatParagraphSpacingRatio": 0.0,
                  "chatSuggestionMaxHeight": 10.0,
                  "colorContrast": -5.0,
                  "inspirationAppearance": {"surfaceOpacity": 5.0, "cornerRadius": 99.0}
                }
            """.trimIndent()
        )

        val appearance = stored.toSettings().normalized().advancedAppearanceSetting

        assertEquals(1f, appearance.micaTintStrength, 0f)
        assertNull(appearance.globalBackground)
        assertEquals(0f, appearance.globalBackgroundOpacity, 0f)
        assertEquals(MAX_GLOBAL_BACKGROUND_BLUR_RADIUS, appearance.globalBackgroundBlurRadius, 0f)
        assertEquals(.35f, appearance.pageSurfaceOpacity, 0f)
        assertEquals(MAX_CHAT_TEXT_LINE_HEIGHT_RATIO, appearance.chatTextLineHeightRatio, 0f)
        assertEquals(MIN_CHAT_PARAGRAPH_SPACING_RATIO, appearance.chatParagraphSpacingRatio, 0f)
        assertEquals(MIN_CHAT_SUGGESTION_HEIGHT, appearance.chatSuggestionMaxHeight, 0f)
        assertEquals(-1f, appearance.colorContrast, 0f)
        assertEquals(1f, appearance.inspirationAppearance.surfaceOpacity, 0f)
        assertEquals(28f, appearance.inspirationAppearance.cornerRadius, 0f)
    }

    // 非有限值（NaN / Infinity）不夹取而是回落到设计值，否则渲染出来是空白
    @Test
    fun `non finite appearance values fall back to design values`() {
        val appearance = AdvancedAppearanceSetting(
            chatTextLineHeightRatio = Float.NaN,
            chatParagraphSpacingRatio = Float.NEGATIVE_INFINITY,
            pageSurfaceOpacity = Float.POSITIVE_INFINITY,
        ).normalized()

        assertEquals(1.5f, appearance.chatTextLineHeightRatio, 0f)
        assertEquals(.65f, appearance.chatParagraphSpacingRatio, 0f)
        assertEquals(.68f, appearance.pageSurfaceOpacity, 0f)
        assertEquals(1.5f, appearance.normalizedChatTextLineHeightRatio(), 0f)
        assertEquals(.65f, appearance.normalizedChatParagraphSpacingRatio(), 0f)
    }

    @Test
    fun `unknown appearance mode falls back without losing other options`() {
        val settings = mutablePreferencesOf(
            SettingsStore.ADVANCED_APPEARANCE_SETTING to
                """{"enableAdvancedAppearance":true,"textColorMode":"future_mode","pageSurfaceStyle":"mica"}""",
            SettingsStore.TITLE_PROMPT to "custom title prompt",
        ).toSettings()

        assertTrue(settings.advancedAppearanceSetting.enableAdvancedAppearance)
        assertEquals(TextColorMode.THEME, settings.advancedAppearanceSetting.textColorMode)
        assertEquals(BackgroundSurfaceStyle.MICA, settings.advancedAppearanceSetting.pageSurfaceStyle)
        assertEquals("custom title prompt", settings.titlePrompt)
    }

    @Test
    fun `malformed appearance json does not reset unrelated preferences`() {
        val settings = mutablePreferencesOf(
            SettingsStore.ADVANCED_APPEARANCE_SETTING to "{",
            SettingsStore.TITLE_PROMPT to "custom title prompt",
        ).toSettings()

        assertEquals(AdvancedAppearanceSetting(), settings.advancedAppearanceSetting)
        assertEquals("custom title prompt", settings.titlePrompt)
    }

    // 高级外观关掉时，即使背景图、不透明度、两个「应用到」开关都留着，也不该有任何一层背景生效
    @Test
    fun `disabling advanced appearance switches every background off`() {
        val on = Settings(
            advancedAppearanceSetting = AdvancedAppearanceSetting(
                enableAdvancedAppearance = true,
                enableGlobalBackground = true,
                globalBackground = "uploads/bg.webp",
                globalBackgroundOpacity = .8f,
                pageSurfaceStyle = BackgroundSurfaceStyle.TRANSLUCENT,
                applyGlobalBackgroundToChat = true,
                applyGlobalBackgroundToFullscreenPreview = true,
            )
        )
        assertTrue(on.isGlobalBackgroundActive())
        assertTrue(on.isGlobalBackgroundVisible())
        assertTrue(on.isPageBackgroundActive())
        assertTrue(on.isGlobalBackgroundAppliedToChat())
        assertTrue(on.isFullscreenPreviewBackgroundActive())

        val off = on.copy(
            advancedAppearanceSetting = on.advancedAppearanceSetting.copy(enableAdvancedAppearance = false)
        )
        assertFalse(off.isGlobalBackgroundActive())
        assertFalse(off.isGlobalBackgroundVisible())
        assertFalse(off.isPageBackgroundActive())
        assertFalse(off.isGlobalBackgroundAppliedToChat())
        assertFalse(off.isFullscreenPreviewBackgroundActive())
    }

    // 三个判据各自管一层：普通页 / 聊天页 / 全屏预览，不能一个开关同时决定三处
    @Test
    fun `ordinary page chat and fullscreen predicates stay separate`() {
        val base = AdvancedAppearanceSetting(
            enableAdvancedAppearance = true,
            enableGlobalBackground = true,
            globalBackground = "uploads/bg.webp",
            pageSurfaceStyle = BackgroundSurfaceStyle.FROSTED,
            applyGlobalBackgroundToChat = false,
            applyGlobalBackgroundToFullscreenPreview = false,
        )

        // 两处「应用到」都关：普通页照旧用背景，聊天页与全屏预览不跟
        val neither = Settings(advancedAppearanceSetting = base)
        assertTrue(neither.isPageBackgroundActive())
        assertFalse(neither.isGlobalBackgroundAppliedToChat())
        assertFalse(neither.isFullscreenPreviewBackgroundActive())

        val chatOnly = Settings(advancedAppearanceSetting = base.copy(applyGlobalBackgroundToChat = true))
        assertTrue(chatOnly.isGlobalBackgroundAppliedToChat())
        assertFalse(chatOnly.isFullscreenPreviewBackgroundActive())

        val fullscreenOnly =
            Settings(advancedAppearanceSetting = base.copy(applyGlobalBackgroundToFullscreenPreview = true))
        assertTrue(fullscreenOnly.isFullscreenPreviewBackgroundActive())
        assertFalse(fullscreenOnly.isGlobalBackgroundAppliedToChat())

        // 不透明度为 0：可见与两处应用都算关，但「有没有配背景」这个事实不变
        val transparent = Settings(
            advancedAppearanceSetting = base.copy(
                globalBackgroundOpacity = 0f,
                applyGlobalBackgroundToChat = true,
                applyGlobalBackgroundToFullscreenPreview = true,
            )
        )
        assertTrue(transparent.isGlobalBackgroundActive())
        assertFalse(transparent.isGlobalBackgroundVisible())
        assertFalse(transparent.isPageBackgroundActive())
        assertFalse(transparent.isGlobalBackgroundAppliedToChat())
        assertFalse(transparent.isFullscreenPreviewBackgroundActive())
    }

    // 不透明表面吃掉背景图：普通页与全屏预览都不画，聊天页那条不受表面样式影响
    @Test
    fun `an opaque page surface hides the image on pages and fullscreen`() {
        val settings = Settings(
            advancedAppearanceSetting = AdvancedAppearanceSetting(
                enableAdvancedAppearance = true,
                enableGlobalBackground = true,
                globalBackground = "uploads/bg.webp",
                pageSurfaceStyle = BackgroundSurfaceStyle.OPAQUE,
                applyGlobalBackgroundToChat = true,
            )
        )

        assertTrue(settings.isGlobalBackgroundVisible())
        assertFalse(settings.isPageBackgroundActive())
        assertFalse(settings.isFullscreenPreviewBackgroundActive())
        assertTrue(settings.isGlobalBackgroundAppliedToChat())
        assertTrue(BackgroundSurfaceStyle.OPAQUE.opaqueMaterial)
        assertFalse(BackgroundSurfaceStyle.OPAQUE.usesBlur)
        assertTrue(BackgroundSurfaceStyle.LIQUID_GLASS.usesBlur)
        assertTrue(BackgroundSurfaceStyle.ACRYLIC.usesBlur)
        assertTrue(BackgroundSurfaceStyle.FROSTED.usesBlur)
        assertFalse(BackgroundSurfaceStyle.TRANSLUCENT.usesBlur)
        assertFalse(BackgroundSurfaceStyle.MICA.opaqueMaterial)
    }

    @Test
    fun `blank background path counts as no background`() {
        val blank = Settings(
            advancedAppearanceSetting = AdvancedAppearanceSetting(
                enableAdvancedAppearance = true,
                enableGlobalBackground = true,
                globalBackground = "   ",
            )
        )

        assertFalse(blank.isGlobalBackgroundActive())
        assertFalse(blank.isGlobalBackgroundVisible())
    }

    @Test
    fun `assistant background count reads only per assistant backgrounds`() {
        val withImage = Assistant(id = Uuid.random(), name = "image", background = "uploads/a.webp")
        val withGradient = Assistant(id = Uuid.random(), name = "gradient", useGradientBackground = true)
        val plain = Assistant(id = Uuid.random(), name = "plain")
        val blankPath = Assistant(id = Uuid.random(), name = "blank", background = "  ")

        val settings = Settings(assistants = listOf(withImage, withGradient, plain, blankPath))

        assertEquals(2, settings.configuredAssistantBackgroundCount())
        assertEquals(0, Settings(assistants = emptyList()).configuredAssistantBackgroundCount())
    }

    @Test
    fun `appearance display inputs are clamped separately from advanced appearance`() {
        val display = DisplaySetting(
            topBarBlurRadius = -4f,
            topBarSurfaceOpacity = 5f,
            inputBlurRadius = Float.NaN,
            inputSurfaceOpacity = 0f,
        ).normalizedAppearanceInputs()

        assertEquals(0f, display.topBarBlurRadius, 0f)
        assertEquals(1f, display.topBarSurfaceOpacity, 0f)
        assertEquals(12f, display.inputBlurRadius, 0f)
        assertEquals(.2f, display.inputSurfaceOpacity, 0f)

        // 规整只动外观输入，别的显示项原样带过
        val untouched = DisplaySetting(userNickname = "me", displayMode = DisplayMode.LANDSCAPE)
        assertSame(untouched, untouched.normalizedAppearanceInputs())
    }
}

private fun id(n: Int) = Uuid.parse("00000000-0000-0000-0000-%012d".format(n))

// 每个字段都取了和默认值不同的值
private val SAMPLE = Settings(
    dynamicColor = false,
    themeId = "custom",
    customThemes = listOf(CustomTheme(id = "theme", name = "Theme")),
    palette = PaletteSetting(
        presetId = "ocean",
        light = PaletteColors(primary = 0xFF112233, onPrimary = 0xFF445566),
        dark = PaletteColors(primary = 0xFF778899),
    ),
    developerMode = true,
    appLockEnabled = true,
    appLockTimeoutMinutes = 15,
    displaySetting = DisplaySetting(userNickname = "me", displayMode = DisplayMode.LANDSCAPE),
    advancedAppearanceSetting = AdvancedAppearanceSetting(
        enableAdvancedAppearance = true,
        pageSurfaceStyle = BackgroundSurfaceStyle.MICA,
        overlaySurfaceStyle = BackgroundSurfaceStyle.ACRYLIC,
        chatParagraphSpacingRatio = .8f,
    ),
    networkSetting = NetworkSetting(
        userAgent = "agent",
        retryMaxCount = 5,
        retryIntervalSeconds = 3,
        retryErrorCodes = "429,500",
    ),
    favoriteModels = listOf(id(30)),
    chatModelId = id(1),
    fastModelId = id(2),
    fastModelReasoningLevel = ReasoningLevel.LOW,
    memoryModelId = id(6),
    memoryModelReasoningLevel = ReasoningLevel.HIGH,
    imageGenerationModelId = id(3),
    enableAutoTitle = false,
    enableMessageQueue = false,
    titlePrompt = "title",
    translateModeId = id(4),
    translatePrompt = "translate",
    translateThinkingBudget = 1024,
    translateTargetLanguage = "ja",
    enableSuggestion = false,
    suggestionPrompt = "suggestion",
    ocrModelId = id(5),
    ocrPrompt = "ocr",
    assistantId = id(20),
    providers = listOf(ProviderSetting.OpenAI(id = id(31), name = "Provider")),
    deletedBuiltInProviderIds = setOf(id(32).toString()),
    providerGroups = listOf("work"),
    providerGroupAssignments = mapOf(id(31).toString() to "work"),
    providerProxies = mapOf("api.example.com" to "socks5://127.0.0.1:1080"),
    providerProxyUsernames = mapOf("api.example.com" to "user"),
    providerProxyPasswords = mapOf("api.example.com" to "pass"),
    customCaCertificates = listOf("-----BEGIN CERTIFICATE-----"),
    assistants = listOf(Assistant(id = id(20), name = "Assistant")),
    assistantTags = listOf(Tag(id = id(7), name = "tag")),
    searchServices = listOf(
        SearchServiceOptions.BingLocalOptions(id = id(21)),
        SearchServiceOptions.BingLocalOptions(id = id(22)),
    ),
    searchCommonOptions = SearchCommonOptions(resultSize = 3),
    searchServiceSelected = 1,
    enabledSearchServiceIds = setOf(id(21).toString()),
    mcpServers = listOf(McpServerConfig.SseTransportServer(id = id(8), url = "https://example.com/sse")),
    toolOverrides = listOf(
        ToolOverride(
            name = "clipboard_tool",
            description = "overridden description",
            instructions = "overridden instructions",
            parameters = """{"type":"object","properties":{}}""",
        )
    ),
    webDavConfig = WebDavConfig(url = "https://dav.example.com"),
    s3Config = S3Config(bucket = "backup"),
    uploadS3Config = S3Config(bucket = "upload"),
    assetStorageType = AssetStorageType.WEBDAV,
    uploadWebDavConfig = WebDavConfig(
        url = "https://dav.example.com",
        username = "user",
        password = "pass",
        path = "media",
    ),
    mediaGenerationProviders = listOf(MediaGenerationProviderSetting.OpenAI(id = id(9))),
    ttsProviders = DEFAULT_TTS_PROVIDERS.take(1),
    selectedTTSProviderId = id(10),
    defaultTTSPlaybackSpeed = 1.5f,
    asrProviders = listOf(ASRProviderSetting.OpenAIRealtime(id = id(11))),
    selectedASRProviderId = id(11),
    modeInjections = listOf(PromptInjection.ModeInjection(id = id(14), name = "mode")),
    lorebooks = listOf(Lorebook(id = id(12), name = "lorebook")),
    quickMessages = listOf(QuickMessage(id = id(13), title = "hi")),
    guideMessages = listOf(QuickMessage(id = id(41), title = "guide", content = "keep it")),
    webServerEnabled = true,
    webServerPort = 9090,
    webServerJwtEnabled = true,
    webServerAccessPassword = "secret",
    webServerLocalhostOnly = true,
    sshServerEnabled = true,
    sshServerPort = 2223,
    sshServerAllowLan = true,
    subAgentMaxParallel = 5,
    backupReminderConfig = BackupReminderConfig(enabled = true),
    backupFormat = "FULL",
    sponsorAlertDismissedAt = 7,
)

private val STORED_KEYS = setOf(
    "dynamic_color",
    "theme_id",
    "custom_themes",
    "palette",
    "display_setting",
    "advanced_appearance_setting",
    "network_setting",
    "developer_mode",
    "app_lock_enabled",
    "app_lock_timeout_minutes",
    "favorite_models",
    "chat_model",
    "fast_model",
    "fast_model_reasoning_level",
    "memory_model",
    "memory_model_reasoning_level",
    "translate_model",
    "enable_suggestion",
    "enable_auto_title",
    "enable_message_queue",
    "image_generation_model",
    "title_prompt",
    "translation_prompt",
    "translate_thinking_budget",
    "translate_target_language",
    "suggestion_prompt",
    "ocr_model",
    "ocr_prompt",
    "providers",
    "deleted_builtin_providers",
    "provider_groups",
    "provider_group_assignments",
    "provider_proxies",
    "provider_proxy_usernames",
    "provider_proxy_passwords",
    "custom_ca_certificates",
    "select_assistant",
    "sub_agent_max_parallel",
    "assistants",
    "assistant_tags",
    "search_services",
    "search_common",
    "search_selected",
    "enabled_search_service_ids",
    "mcp_servers",
    "tool_overrides",
    "webdav_config",
    "s3_config",
    "upload_s3_config",
    "upload_webdav_config",
    "asset_storage_type",
    "media_generation_providers",
    "tts_providers",
    "selected_tts_provider",
    "default_tts_playback_speed",
    "asr_providers",
    "selected_asr_provider",
    "web_server_enabled",
    "web_server_port",
    "web_server_jwt_enabled",
    "web_server_access_password",
    "web_server_localhost_only",
    "ssh_server_enabled",
    "ssh_server_port",
    "ssh_server_allow_lan",
    "mode_injections",
    "lorebooks",
    "quick_messages",
    "guide_messages",
    "backup_reminder_config",
    "backup_format",
    "sponsor_alert_dismissed_at",
)
