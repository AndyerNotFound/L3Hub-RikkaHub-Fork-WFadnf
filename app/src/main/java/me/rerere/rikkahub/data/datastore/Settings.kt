package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ProviderSetting
import me.rerere.asr.ASRProviderSetting
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_OCR_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_SUGGESTION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.DEFAULT_FLOATING_BAR_ITEMS
import me.rerere.rikkahub.data.model.FloatingBarItem
import me.rerere.rikkahub.data.model.FloatingBubbleIcon
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.model.ToolOverride
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.ui.theme.CustomTheme
import me.rerere.rikkahub.ui.theme.PaletteSetting
import me.rerere.rikkahub.ui.theme.PresetThemes
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchServiceOptions
import me.rerere.tts.provider.TTSProviderSetting
import kotlin.uuid.Uuid

@Serializable
data class Settings(
    @Transient
    val init: Boolean = false,
    val dynamicColor: Boolean = true,
    val themeId: String = PresetThemes[0].id,
    val customThemes: List<CustomTheme> = emptyList(),
    // 调色板：预设或用户逐个改过的深浅两套颜色，默认不启用（跟随 themeId / 动态取色）
    val palette: PaletteSetting = PaletteSetting(),
    // 主题引擎（阶段 5）：配色风格/对比度/色盲/反色/图标形状/描边与阴影，见 ToolThemeSetting
    val toolTheme: ToolThemeSetting = ToolThemeSetting(),
    val developerMode: Boolean = false,
    val displaySetting: DisplaySetting = DisplaySetting(),
    val networkSetting: NetworkSetting = NetworkSetting(),
    val favoriteModels: List<Uuid> = emptyList(),
    val chatModelId: Uuid = Uuid.random(),
    val fastModelId: Uuid = Uuid.random(),
    val fastModelReasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    // 记忆整理用的模型，没设置时用快速模型
    val memoryModelId: Uuid? = null,
    val memoryModelReasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    val imageGenerationModelId: Uuid = Uuid.random(),
    val enableAutoTitle: Boolean = true,
    // 生成过程中发送的消息是否进入队列排队; 关闭后直接打断当前回复立即发送
    val enableMessageQueue: Boolean = true,
    val titlePrompt: String = DEFAULT_TITLE_PROMPT,
    val translateModeId: Uuid = Uuid.random(),
    val translatePrompt: String = DEFAULT_TRANSLATION_PROMPT,
    val translateThinkingBudget: Int = 0,
    // 翻译页的目标语言，存 BCP 47 语言标签
    val translateTargetLanguage: String = "zh-CN",
    val enableSuggestion: Boolean = true,
    val suggestionPrompt: String = DEFAULT_SUGGESTION_PROMPT,
    val ocrModelId: Uuid = Uuid.random(),
    val ocrPrompt: String = DEFAULT_OCR_PROMPT,
    val assistantId: Uuid = DEFAULT_ASSISTANT_ID,
    val providers: List<ProviderSetting> = DEFAULT_PROVIDERS,
    // 用户删掉过的内置提供商 id：读盘时不再自动补回，可从「添加提供商」里恢复
    val deletedBuiltInProviderIds: Set<String> = emptySet(),
    // 手动维护的提供商分组（有序）与 提供商ID -> 分组名 的映射
    val providerGroups: List<String> = emptyList(),
    val providerGroupAssignments: Map<String, String> = emptyMap(),
    // 按接口域名单独给某个提供商设置代理: host -> proxyUrl
    val providerProxies: Map<String, String> = emptyMap(),
    val providerProxyUsernames: Map<String, String> = emptyMap(),
    val providerProxyPasswords: Map<String, String> = emptyMap(),
    // 应用锁：离开指定时间后要求验证锁屏密码
    val appLockEnabled: Boolean = false,
    val appLockTimeoutMinutes: Int = 5,
    // 额外信任的自签 CA（PEM 文本）
    val customCaCertificates: List<String> = emptyList(),
    // 同一时刻最多并行运行的子代理数（1..8，默认 3）；达到上限时派发会被拒绝
    val subAgentMaxParallel: Int = 3,
    val assistants: List<Assistant> = DEFAULT_ASSISTANTS,
    val assistantTags: List<Tag> = emptyList(),
    val searchServices: List<SearchServiceOptions> = listOf(SearchServiceOptions.DEFAULT),
    // 同时启用的搜索引擎 id；为空时只用 searchServiceSelected 指向的那个
    val enabledSearchServiceIds: Set<String> = emptySet(),
    val searchCommonOptions: SearchCommonOptions = SearchCommonOptions(),
    val searchServiceSelected: Int = 0,
    val mcpServers: List<McpServerConfig> = emptyList(),
    // 用户手写的工具覆写，按工具名全局生效；覆写后不再跟随应用与 MCP 的自动更新
    val toolOverrides: List<ToolOverride> = emptyList(),
    val webDavConfig: WebDavConfig = WebDavConfig(),
    val s3Config: S3Config = S3Config(),
    // 素材上传后端：S3 或 WebDAV
    val assetStorageType: AssetStorageType = AssetStorageType.S3,
    // RemoteFileStore 上传临时素材用的桶，和备份的 s3Config 互不影响；items 字段在这里不使用
    val uploadS3Config: S3Config = S3Config(),
    // 素材上传走 WebDAV 时的连接配置；path 是存放素材的目录
    val uploadWebDavConfig: WebDavConfig = WebDavConfig(path = "rikkahub_uploads"),
    val mediaGenerationProviders: List<MediaGenerationProviderSetting> = emptyList(),
    val ttsProviders: List<TTSProviderSetting> = DEFAULT_TTS_PROVIDERS,
    val selectedTTSProviderId: Uuid = DEFAULT_SYSTEM_TTS_ID,
    val defaultTTSPlaybackSpeed: Float = 1.0f,
    val asrProviders: List<ASRProviderSetting> = emptyList(),
    val selectedASRProviderId: Uuid? = null,
    val modeInjections: List<PromptInjection.ModeInjection> = DEFAULT_MODE_INJECTIONS,
    val lorebooks: List<Lorebook> = emptyList(),
    val quickMessages: List<QuickMessage> = emptyList(),
    // 引导提示词：生成过程中从快捷消息选单里加入的预设，和快捷消息分开存，
    // 因为前者插进正在跑的回合，后者是下一轮的用户消息
    val guideMessages: List<QuickMessage> = emptyList(),
    val webServerEnabled: Boolean = false,
    val webServerPort: Int = 8080,
    val webServerJwtEnabled: Boolean = false,
    val webServerAccessPassword: String = "",
    val webServerLocalhostOnly: Boolean = false,
    // 内置 SSH 服务端：密码沿用 Web 服务器的访问密码，不单独维护第二份令牌
    val sshServerEnabled: Boolean = false,
    val sshServerPort: Int = 2222,
    val sshServerAllowLan: Boolean = false,
    val backupReminderConfig: BackupReminderConfig = BackupReminderConfig(),
    // 备份格式：INCREMENTAL 为分层的 .l3b，FULL 为旧的整份 zip
    val backupFormat: String = "INCREMENTAL",
    val sponsorAlertDismissedAt: Int = 0,
) {
    companion object {
        // 构造一个用于初始化的settings, 但它不能用于保存，防止使用初始值存储
        fun dummy() = Settings(init = true)
    }
}

@Serializable
data class NetworkSetting(
    val userAgent: String = "",
    val proxyUrl: String = "",
    val proxyUsername: String = "",
    val proxyPassword: String = "",
    // 自定义重试：开关沿用旧字段名，升级后原来的开关状态不会丢
    val enableAutoRetry: Boolean = true,
    val retryMaxCount: Int = 3,
    val retryIntervalSeconds: Int = 1,
    // 逗号分隔的 HTTP 错误码，空表示只重试网络错误
    val retryErrorCodes: String = "",
)

@Serializable
enum class ChatFontFamily {
    @SerialName("default")
    DEFAULT,

    @SerialName("serif")
    SERIF,

    @SerialName("monospace")
    MONOSPACE,

    @SerialName("custom")
    CUSTOM,
}

@Serializable
enum class BackgroundEffectType {
    @SerialName("off")
    OFF,

    @SerialName("blur")
    BLUR,

    @SerialName("glass")
    GLASS,
}

@Serializable
enum class InputLayoutStyle {
    @SerialName("single")
    SINGLE,

    @SerialName("double")
    DOUBLE,
}

@Serializable
enum class ConversationSortOrder {
    @SerialName("update_time")
    UPDATE_TIME,

    @SerialName("create_time")
    CREATE_TIME,
}

@Serializable
enum class ContextMeterStyle {
    OFF,
    BAR,
    RING,
}

/**
 * 屏幕方向。落到窗口上的映射在 RouteActivity 里（DisplayModeOrientation.kt）。
 *
 * 注意 Android 16 起，targetSdk 36+ 在最小宽度 ≥ 600dp 的屏幕上会忽略固定方向，
 * 竖屏/横屏只是手机上的行为；自动与跟随系统不受这条限制。
 */
@Serializable
enum class DisplayMode {
    PORTRAIT,
    LANDSCAPE,

    /** 按重力感应转，不管系统有没有开自动旋转 */
    AUTO,

    /** 不表态，系统自动旋转开关说了算 */
    SYSTEM,
}

/** 页面/列表过渡的速度手感，只在没关动效时生效。 */
@Serializable
enum class AnimationSpeed {
    /** 跟随 Material 的 expressive 曲线 */
    SYSTEM,

    /** 收紧：同样的位移用更短的时间走完 */
    FAST,

    /** 摊开：慢一点，尾段拖得长一点 */
    GENTLE,
}

/**
 * 开关外观，取自 ImageToolbox 的 `SwitchType`（T8RIN，Apache-2.0）。
 *
 * 顺序与源工程一致（源工程是八个 `data object` 的 sealed class，这里改成 enum，
 * 因为要直接序列化进 DataStore）。[MATERIAL_YOU] 是默认值，走的仍是本项目原来的
 * 开关实现（M3 的 `Switch`），其余七档一律用移植过来的自绘实现。
 */
enum class SwitchType {
    /** 跟随 Material 3 的开关 */
    MATERIAL_YOU,

    /** Compose 基础款：轨道与拇指都是 M3 baseline */
    COMPOSE,

    /** Pixel 风格：拇指带描边 */
    PIXEL,

    /** Fluent（Windows）风格：方形轨道，拇指按压缩放 */
    FLUENT,

    /** Cupertino（iOS）风格：胶囊轨道，拇指大 */
    CUPERTINO,

    /** LiquidGlass 风格：玻璃质感的轨道与高光 */
    LIQUID_GLASS,

    /** HyperOS（小米）风格 */
    HYPEROS,

    /** OneUI（三星）风格 */
    ONE_UI,
}

/**
 * 滑块外观，取自 ImageToolbox 的 `SliderType`（T8RIN，Apache-2.0）。
 *
 * 源工程是四个 `data object`，这里改成 enum 以便序列化。
 * [MATERIAL_YOU] 是默认值，走的仍是本项目原来的 M3 `Slider`。
 */
enum class SliderType {
    /** 跟随 Material 3 的滑块 */
    MATERIAL_YOU,

    /** 精致款：拇指带外圈与光晕，轨道有刻度（源工程 `FancySlider`） */
    FANCY,

    /** Material 2 款：拇指空心、轨道更细 */
    MATERIAL,

    /** HyperOS 款：轨道两端圆头更薄，滑块跟手更紧 */
    HYPEROS,
}

/**
 * 组件轮廓的形状类型，取自 ImageToolbox 的 ShapeType（T8RIN，Apache-2.0）。
 *
 * 与那个工程的差别：那边是带 `strength` 的 sealed class，每个实例自带强度；
 * 这里拆成 enum + 独立的 [DisplaySetting.cornerScale]，一是能直接序列化进设置，
 * 二是「形状」与「大小」本来就是两个设置项，合成一个对象反而不好存回 DataStore。
 *
 * [scaleRatio]：Scoop / Notch 在同样的半径下视觉上更夸张，源工程按 0.65 打薄，
 * 照搬这个比例，两种形状才不会比圆角大一圈。
 */
enum class CornerStyle(val scaleRatio: Float) {
    /** 普通圆角（Compose `RoundedCornerShape`） */
    ROUNDED(1f),

    /** 切角（Compose `CutCornerShape`） */
    CUT(1f),

    /** 超椭圆，四角像 iOS 图标那样更"方"一点 */
    SQUIRCLE(1f),

    /** 连续圆角：圆角与直边的过渡带一段曲率过渡（`kyant0:capsule`） */
    SMOOTH(1f),

    /** 波浪边：直边按正弦起伏 */
    WAVY(1f),

    /** 凹角：圆角向内挖 */
    SCOOP(0.65f),

    /** 缺口角：圆角处切成直角缺口 */
    NOTCH(0.65f),
}

/**
 * 触感强度。
 *
 * SYSTEM 是默认值：不接管 `LocalHapticFeedback`，行为与没有这个设置时完全一致。
 * 其余两档用 `FLAG_IGNORE_GLOBAL_SETTING` 发震动，绕过系统里"触摸时震动"的开关。
 */
enum class HapticsStrength {
    /** 沿用系统默认实现 */
    SYSTEM,

    /** 短促一下（`CONTEXT_CLICK`） */
    LIGHT,

    /** 重一些（`LONG_PRESS`） */
    STRONG,
}

/**
 * 动态模糊的统一参数。
 *
 * 列表滚动、卡片收放、消息入场这些过渡共用同一套数：半径按各处的相对强度缩放，
 * 时长决定模糊收放与入场位移走多久，行程决定滚动过渡的位移长度。
 * 任何一项调整都对整棵界面里的模糊同时生效，不必一个效果一个效果地改。
 */
@Serializable
data class MotionBlurSetting(
    val enabled: Boolean = true,
    // 基准半径(dp)：各处按自己的相对强度（6dp 记作 1.0）缩放，调到 0 就是完全不糊
    val radius: Int = 6,
    // 模糊收放与入场位移的时长(ms)；越小越利落，越大尾段拖得越长
    val durationMillis: Int = 280,
    // 滚动过渡的位移行程(dp)
    val travel: Int = 32,
    // 列表滚动时按速度叠的那层模糊，单独给个开关：有人只想要文本的飘入，不想要整屏糊
    val scrollBlur: Boolean = true,
)

@Serializable
data class DisplaySetting(
    val userAvatar: Avatar = Avatar.Dummy,
    val userNickname: String = "",
    val showUserAvatar: Boolean = true,
    val showAssistantBubble: Boolean = false,
    val splitAssistantBubbleByParagraph: Boolean = false,
    val bubbleOpacity: Float = 1.0f,
    val showModelIcon: Boolean = true,
    val showModelName: Boolean = true,
    val showDateTimeInMessage: Boolean = false,
    val showTokenUsage: Boolean = true,
    // 顶栏下方的限额折叠条：收起时一行摘要，展开后看当前渠道的限流与限额
    val showQuotaBar: Boolean = false,
    // 以二进制量级(Ki/Mi/Gi)显示数量，关闭时按十进制 K/M/B；模型计价的单位也随之切换
    val binaryUnits: Boolean = false,
    // 输入框上方的上下文预警样式
    val contextMeterStyle: ContextMeterStyle = ContextMeterStyle.BAR,
    // 使用 L3Hub 的思考强度图标和圆形滑块
    val useL3HubReasoningStyle: Boolean = false,
    // 为按钮与开关显示阴影
    val buttonShadow: Boolean = false,
    // 任务清单全部完成后从输入框上方收起；回看走输入框左边的「+」菜单
    val hideCompletedTodoCard: Boolean = true,
    // Usage 看板的上下文占用用饼图显示
    val usagePieChart: Boolean = false,
    // 关闭所有动效：Material 的 motionScheme 换成 snap，列表/卡片的过渡直接跳变
    val disableAnimations: Boolean = false,
    // 页面转场和列表展开的速度手感；disableAnimations 打开时忽略这一项
    val animationSpeed: AnimationSpeed = AnimationSpeed.SYSTEM,
    // 动态模糊：滚动、收放、入场共用的一整套参数
    val motionBlur: MotionBlurSetting = MotionBlurSetting(),
    // 上下文预警条用绝对值(数字/数字)显示, 关闭时显示百分比
    val contextMeterAbsolute: Boolean = false,
    val showThinkingContent: Boolean = true,
    val autoCloseThinking: Boolean = true,
    // 折叠的思考卡标题取思考内容的第一句，关掉则只显示思考时长
    val showThinkingSummary: Boolean = true,
    val updateCheckDisabledUntilEpochMillis: Long = 0L,
    val showMessageJumper: Boolean = true,
    val messageJumperOnLeft: Boolean = false,
    val fontSizeRatio: Float = 1.0f,
    // 整个界面的缩放（0.5–2.0）：改的是应用自己的密度，dp 与 sp 一起缩放
    val uiScale: Float = 1.0f,
    val enableMessageGenerationHapticEffect: Boolean = false,
    // 全局触感强度：只影响走 LocalHapticFeedback 的震动，不影响消息生成那条独立的开关
    val hapticsStrength: HapticsStrength = HapticsStrength.SYSTEM,
    // 组件轮廓：形状类型、边角大小倍率（0f–2f，1f 为设计值）、改变按压轮廓时的节流（毫秒）
    val cornerStyle: CornerStyle = CornerStyle.ROUNDED,
    val cornerScale: Float = 1f,
    val cornerAnimThrottle: Int = 128,
    // 开关外观：默认跟随皮肤，其余七档是自绘实现
    val switchType: SwitchType = SwitchType.MATERIAL_YOU,
    // 滑块外观：默认跟随 M3，其余三档是自绘实现
    val sliderType: SliderType = SliderType.MATERIAL_YOU,
    val skipCropImage: Boolean = true,
    val enableNotificationOnMessageGeneration: Boolean = false,
    val enableLiveUpdateNotification: Boolean = false,
    val enableFloatingChatWindow: Boolean = false,
    // 输入框下方的悬浮栏：开启后可快速切换聊天、媒体创作与自定义槽位
    val enableFloatingBar: Boolean = false,
    // 悬浮栏静默五秒后自动收起；关闭后常驻，只在输入框获得焦点时让位
    val floatingBarAutoHide: Boolean = true,
    // 悬浮栏上的图标槽位，按列表顺序显示，最多 MAX_FLOATING_BAR_ITEMS 个
    val floatingBarItems: List<FloatingBarItem> = DEFAULT_FLOATING_BAR_ITEMS,
    // 悬浮栏的高度与图标缩放，1.0 为默认大小
    val floatingBarScale: Float = 1.0f,
    // 面板宽度占屏幕宽度的比例，设置里的滑块和面板右下角的拖动改的是同一个值
    val floatingPanelWidthRatio: Float = 1.0f,
    // 面板最高占可用高度的比例；内容少时照常收缩，这个只是上限
    val floatingPanelHeightRatio: Float = 1.0f,
    // 面板里预览区的最大高度(dp)，超出后在预览区内部滚动
    val floatingPreviewMaxHeight: Int = 200,
    val floatingBubbleIcon: FloatingBubbleIcon = FloatingBubbleIcon.AppLogo,
    val codeBlockAutoWrap: Boolean = false,
    val codeBlockAutoCollapse: Boolean = false,
    val showLineNumbers: Boolean = false,
    val ttsOnlyReadQuoted: Boolean = false,
    val ttsOnlyReadOutsideBrackets: Boolean = false,
    val autoPlayTTSAfterGeneration: Boolean = false,
    val voiceModeListenWhileReplying: Boolean = true,
    val pasteLongTextAsFile: Boolean = false,
    val pasteLongTextThreshold: Int = 1000,
    val sendOnEnter: Boolean = false,
    val enableAutoScroll: Boolean = true,
    val enableLatexRendering: Boolean = true,
    val enableQuoteHighlight: Boolean = false,
    val enableBlurEffect: Boolean = false,
    val backgroundEffectType: BackgroundEffectType = BackgroundEffectType.BLUR,
    val inputLayoutStyle: InputLayoutStyle = InputLayoutStyle.DOUBLE,
    val showToolCallOutput: Boolean = true,
    val uiCornerRadius: Int = 24,
    // 输入框卡片的内边距，和圆角一起决定内部元素是否和外框同心
    val inputPadding: Int = 8,
    // 全宽组件：气泡/卡片撑满父宽、圆角收成直角、贴边组件去掉左右内边距
    val fullWidthComponents: Boolean = false,
    // 模糊特效下气泡/输入框/顶栏的底色不透明度
    val blurOpacity: Float = 0.45f,
    // 玻璃底（设置项/选项卡/按钮/顶栏图标）的透明度，0 = 全透明，1 = 原实色
    val glassOpacity: Float = 0.35f,
    // 消息样式：气泡背景三档（默认 / 毛玻璃 / 实色），参数按明暗与角色分别覆盖
    val chatMessageBackgroundStyle: ChatMessageBackgroundStyle = ChatMessageBackgroundStyle.DEFAULT,
    val userChatBubbleStyleOverrides: ChatBubbleStyleOverrides = ChatBubbleStyleOverrides(),
    val assistantChatBubbleStyleOverrides: ChatBubbleStyleOverrides = ChatBubbleStyleOverrides(),
    val thinkingToolCardStyleOverrides: ChatBubbleStyleOverrides = ChatBubbleStyleOverrides(),
    // 思考链卡片是否单独套一套消息样式（默认跟助手气泡走同一套）
    val customizeThinkingToolCards: Boolean = false,
    // 新对话界面中央的自定义标语
    val emptyChatSlogan: String = "",
    val chatFontFamily: ChatFontFamily = ChatFontFamily.DEFAULT,
    val chatCustomFontPath: String = "",
    val chatCustomFontName: String = "",
    // 多字体回退链（有序）：同一字形靠前的字体优先，缺失的字形自动顺延到下一款（如英文款→中文款→俄文款）
    val chatCustomFontPaths: List<String> = emptyList(),
    val chatCustomFontNames: List<String> = emptyList(),
    // 聊天字体是否应用到整个应用（不限于聊天界面）
    val fontApplyGlobal: Boolean = false,
    val enableVolumeKeyScroll: Boolean = false,
    val volumeKeyScrollRatio: Float = 1.0f,
    val conversationSortOrder: ConversationSortOrder = ConversationSortOrder.UPDATE_TIME,
    // 屏幕方向：竖屏 / 横屏 / 自动 / 跟随系统
    val displayMode: DisplayMode = DisplayMode.SYSTEM,
)

@Serializable
enum class AssetStorageType {
    S3,
    WEBDAV,
}

@Serializable
data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val path: String = "rikkahub_backups",
    val items: List<BackupItem> = listOf(
        BackupItem.DATABASE,
        BackupItem.FILES
    ),
) {
    @Serializable
    enum class BackupItem {
        DATABASE,
        FILES,
    }
}

@Serializable
data class BackupReminderConfig(
    val enabled: Boolean = false,
    val intervalDays: Int = 7,
    val lastBackupTime: Long = 0L,
)

val DisplaySetting.effectiveBackgroundEffect: BackgroundEffectType
    get() = if (enableBlurEffect) backgroundEffectType else BackgroundEffectType.OFF

/** 删除提供商。内置提供商删过后记住 id，读盘时不再自动补回 */
fun Settings.removeProvider(id: Uuid): Settings = copy(
    providers = providers.filter { it.id != id },
    deletedBuiltInProviderIds = if (DEFAULT_PROVIDERS.any { it.id == id }) {
        deletedBuiltInProviderIds + id.toString()
    } else {
        deletedBuiltInProviderIds
    },
)

/** 恢复一个被删掉的内置提供商；用默认配置重建，不保留删除前的 key 和模型 */
fun Settings.restoreProvider(provider: ProviderSetting): Settings = copy(
    providers = providers.filterNot { it.id == provider.id } + provider,
    deletedBuiltInProviderIds = deletedBuiltInProviderIds - provider.id.toString(),
)
