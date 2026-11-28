package me.rerere.rikkahub

import kotlinx.serialization.Serializable

/**
 * 全部导航目的地。
 *
 * 导航目的地清单。引擎换成 Decompose 后不再实现 NavKey，
 * `@Serializable` 的序列化名与 `Screen.serializer()` 的多态序列化照旧，
 * 各处 `import me.rerere.rikkahub.Screen` 不受影响。
 */
@Serializable
sealed interface Screen {
    @Serializable
    data class Chat(
        val id: String,
        val text: String? = null,
        val files: List<String> = emptyList(),
        val nodeId: String? = null,
        // 从侧边栏点进来时记下点击项在列表的上半还是下半，转场据此决定从上方还是下方落进来；
        // 其它入口（新会话、收藏、搜索）留 null，走默认转场
    ) : Screen

    @Serializable
    data class ShareHandler(val text: String, val streamUri: String? = null) : Screen

    /**
     * 侧边栏整页。
     *
     * 竖屏原来是 `ModalNavigationDrawer`，现在改成历史里的一页：从聊天页向左推开，
     * 返回键/侧滑回到聊天页。这样它和其余页面共用同一条栈、同一套转场，
     * 也不必再为一个覆盖层维护自己的返回键优先级。
     *
     * [fromChatId] 记录是从哪个会话打开来的，用于高亮和「删掉的是不是当前这条」的判断；
     * 从别处（如新会话页）打开时为 null。
     */
    @Serializable
    data class Sidebar(val fromChatId: String? = null) : Screen

    @Serializable
    data object History : Screen

    @Serializable
    data object Favorite : Screen

    @Serializable
    data object Assistant : Screen

    @Serializable
    data class AssistantDetail(val id: String) : Screen

    @Serializable
    data class AssistantBasic(val id: String) : Screen

    @Serializable
    data class AssistantPrompt(val id: String) : Screen

    @Serializable
    data class AssistantMemory(val id: String) : Screen

    /** 记忆库 [memoryId] 里的一个文件，[path] 为 null 时新建 */
    @Serializable
    data class MemoryFile(val memoryId: String, val path: String? = null) : Screen

    @Serializable
    data class AssistantRequest(val id: String) : Screen

    @Serializable
    data class AssistantHeartbeat(val id: String) : Screen

    @Serializable
    data class AssistantMcp(val id: String) : Screen

    @Serializable
    data class AssistantLocalTool(val id: String) : Screen

    @Serializable
    data class AssistantInjections(val id: String) : Screen

    @Serializable
    data object Translator : Screen

    @Serializable
    data object Setting : Screen

    @Serializable
    data object Backup : Screen

    @Serializable
    data object ImageGen : Screen

    @Serializable
    data object MediaCreationSessions : Screen

    @Serializable
    data class MediaCreation(val id: String) : Screen

    @Serializable
    data class WebView(
        val url: String = "",
        val contentId: String = "",
        val offlineMessagePreview: Boolean = false,
    ) : Screen

    @Serializable
    data object SettingTheme : Screen

    @Serializable
    data object SettingPalette : Screen

    @Serializable
    data object SettingPreferences : Screen

    @Serializable
    data object SettingPreferencesTheme : Screen

    @Serializable
    data object SettingPreferencesNotification : Screen

    @Serializable
    data object SettingPreferencesGeneral : Screen

    @Serializable
    data object SettingPreferencesMessageStyle : Screen

    @Serializable
    data object SettingPreferencesUI : Screen

    @Serializable
    data object SettingPreferencesNetwork : Screen

    @Serializable
    data object SettingProvider : Screen

    @Serializable
    data class SettingProviderDetail(val providerId: String) : Screen

    @Serializable
    data object SettingModels : Screen

    /** 模型编辑页：modelId 为 null 时新建；只存 Key 下标，导航状态不保存密钥文本。 */
    @Serializable
    data class ModelEditor(
        val providerId: String,
        val modelId: String? = null,
        val keyIndex: Int? = null,
        val accountId: String? = null,
    ) : Screen

    /** 模型指纹：拿导入的指纹集合比对提供商实际返回的模型，只做手动检测，没有网络更新。 */
    @Serializable
    data object ModelFingerprint : Screen

    @Serializable
    data object SettingAbout : Screen

    @Serializable
    data object SettingSearch : Screen

    @Serializable
    data class SettingSearchDetail(val serviceId: String) : Screen

    @Serializable
    data object SettingSpeech : Screen

    @Serializable
    data object SettingMedia : Screen

    @Serializable
    data class SettingMediaProviderDetail(val providerId: String) : Screen

    @Serializable
    data object SettingMcp : Screen

    @Serializable
    data object ToolOverrides : Screen

    @Serializable
    data class SettingMcpDetail(val serverId: String) : Screen

    @Serializable
    data object SettingDonate : Screen

    @Serializable
    data object QuickStart : Screen

    @Serializable
    data object SettingFiles : Screen

    @Serializable
    data object SettingWeb : Screen

    @Serializable
    data object Debug : Screen

    @Serializable
    data object Log : Screen

    @Serializable
    data object Extensions : Screen

    @Serializable
    data object QuickMessages : Screen

    @Serializable
    data object Prompts : Screen

    @Serializable
    data object Skills : Screen

    @Serializable
    data object Workspaces : Screen

    @Serializable
    data class WorkspaceDetail(val id: String) : Screen

    @Serializable
    data class WorkspaceTerminal(val id: String) : Screen

    /** [id] 为 null 时新建 */
    @Serializable
    data class WorkspaceSshEditor(val id: String? = null) : Screen

    @Serializable
    data class WorkspaceFileEditor(val id: String, val area: String, val path: String) : Screen

    @Serializable
    data class SkillDetail(val skillName: String) : Screen

    @Serializable
    data object MessageSearch : Screen

    @Serializable
    data object Stats : Screen
}
