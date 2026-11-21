package me.rerere.rikkahub.ui.navigation

import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.hooks.HeroPage
import me.rerere.rikkahub.ui.hooks.heroKey
import me.rerere.rikkahub.ui.pages.assistant.AssistantPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantBasicPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantDetailPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantExtensionsPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantHeartbeatPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantLocalToolPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantMcpPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantMemoryPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantPromptPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantRequestPage
import me.rerere.rikkahub.ui.pages.backup.BackupPage
import me.rerere.rikkahub.ui.pages.chat.ChatPage
import me.rerere.rikkahub.ui.pages.debug.DebugPage
import me.rerere.rikkahub.ui.pages.extensions.ExtensionsPage
import me.rerere.rikkahub.ui.pages.extensions.PromptPage
import me.rerere.rikkahub.ui.pages.extensions.QuickMessagesPage
import me.rerere.rikkahub.ui.pages.extensions.skills.SkillDetailPage
import me.rerere.rikkahub.ui.pages.extensions.skills.SkillsPage
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceDetailPage
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceFileEditorPage
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspacePage
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceSshEditorPage
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceTerminalPage
import me.rerere.rikkahub.ui.pages.favorite.FavoritePage
import me.rerere.rikkahub.ui.pages.history.HistoryPage
import me.rerere.rikkahub.ui.pages.imggen.ImageGenPage
import me.rerere.rikkahub.ui.pages.log.LogPage
import me.rerere.rikkahub.ui.pages.mediacreation.MediaCreationPage
import me.rerere.rikkahub.ui.pages.mediacreation.MediaCreationSessionsPage
import me.rerere.rikkahub.ui.pages.memory.MemoryFilePage
import me.rerere.rikkahub.ui.pages.search.SearchPage
import me.rerere.rikkahub.ui.pages.setting.ModelEditorPage
import me.rerere.rikkahub.ui.pages.setting.SettingAboutPage
import me.rerere.rikkahub.ui.pages.setting.SettingDonatePage
import me.rerere.rikkahub.ui.pages.setting.SettingFilesPage
import me.rerere.rikkahub.ui.pages.setting.SettingMcpDetailPage
import me.rerere.rikkahub.ui.pages.setting.SettingMcpPage
import me.rerere.rikkahub.ui.pages.setting.SettingMediaPage
import me.rerere.rikkahub.ui.pages.setting.SettingMediaProviderDetailPage
import me.rerere.rikkahub.ui.pages.setting.SettingModelPage
import me.rerere.rikkahub.ui.pages.setting.SettingPage
import me.rerere.rikkahub.ui.pages.setting.SettingPalettePage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesGeneralPage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesNetworkPage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesNotificationPage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesPage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesThemePage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesUIPage
import me.rerere.rikkahub.ui.pages.setting.SettingProviderDetailPage
import me.rerere.rikkahub.ui.pages.setting.SettingProviderPage
import me.rerere.rikkahub.ui.pages.setting.SettingSearchDetailPage
import me.rerere.rikkahub.ui.pages.setting.SettingSearchPage
import me.rerere.rikkahub.ui.pages.setting.SettingSpeechPage
import me.rerere.rikkahub.ui.pages.setting.SettingThemePage
import me.rerere.rikkahub.ui.pages.setting.SettingToolOverridePage
import me.rerere.rikkahub.ui.pages.setting.SettingWebPage
import me.rerere.rikkahub.ui.pages.share.handler.ShareHandlerPage
import me.rerere.rikkahub.ui.pages.stats.StatsPage
import me.rerere.rikkahub.ui.pages.translator.TranslatorPage
import me.rerere.rikkahub.ui.pages.webview.WebViewPage
import me.rerere.workspace.WorkspaceStorageArea
import kotlin.uuid.Uuid

/**
 * 路由 → 页面的分发清单（原 `RouteActivity.appNavEntries()` 的 entry DSL 摊平成 when）。
 *
 * 容器变换页（对应旧 `heroEntry`）包一层 [HeroPage]：页面从触发它的条目展开，
 * 返回时收回条目里，两边靠 `heroKey()` 对上。两个例外保持原样：
 * - `Screen.Setting`：切换配色会 popUpTo 原地重进，同一个 heroKey 新旧两页同时在组合里，不套容器变换；
 * - `Screen.MediaCreation`：从会话列表展开的位移由页面内部的 sharedBounds 演，外层不套也不叠转场。
 */
@Composable
fun ScreenContent(screen: Screen) {
    when (screen) {
        is Screen.Chat -> ChatPage(
            id = Uuid.parse(screen.id),
            text = screen.text,
            files = screen.files.map { it.toUri() },
            nodeId = screen.nodeId?.let { Uuid.parse(it) },
        )

        is Screen.ShareHandler -> HeroPage(screen.heroKey()) {
            ShareHandlerPage(
                text = screen.text,
                image = screen.streamUri,
            )
        }

        Screen.History -> HeroPage(screen.heroKey()) { HistoryPage() }
        Screen.Favorite -> HeroPage(screen.heroKey()) { FavoritePage() }
        Screen.Assistant -> HeroPage(screen.heroKey()) { AssistantPage() }

        is Screen.AssistantDetail -> HeroPage(screen.heroKey()) { AssistantDetailPage(screen.id) }
        is Screen.AssistantBasic -> HeroPage(screen.heroKey()) { AssistantBasicPage(screen.id) }
        is Screen.AssistantPrompt -> HeroPage(screen.heroKey()) { AssistantPromptPage(screen.id) }
        is Screen.AssistantMemory -> HeroPage(screen.heroKey()) { AssistantMemoryPage(screen.id) }

        is Screen.MemoryFile -> HeroPage(screen.heroKey()) {
            MemoryFilePage(memoryId = screen.memoryId, path = screen.path)
        }

        is Screen.AssistantRequest -> HeroPage(screen.heroKey()) { AssistantRequestPage(screen.id) }
        is Screen.AssistantHeartbeat -> HeroPage(screen.heroKey()) { AssistantHeartbeatPage(screen.id) }
        is Screen.AssistantMcp -> HeroPage(screen.heroKey()) { AssistantMcpPage(screen.id) }
        is Screen.AssistantLocalTool -> HeroPage(screen.heroKey()) { AssistantLocalToolPage(screen.id) }
        is Screen.AssistantInjections -> HeroPage(screen.heroKey()) { AssistantExtensionsPage(screen.id) }

        Screen.Translator -> HeroPage(screen.heroKey()) { TranslatorPage() }
        Screen.Setting -> SettingPage()

        Screen.Backup -> HeroPage(screen.heroKey()) { BackupPage() }
        Screen.ImageGen -> HeroPage(screen.heroKey()) { ImageGenPage() }
        Screen.MediaCreationSessions -> HeroPage(screen.heroKey()) { MediaCreationSessionsPage() }

        is Screen.MediaCreation -> MediaCreationPage(screen.id)

        is Screen.WebView -> HeroPage(screen.heroKey()) { WebViewPage(screen.url, screen.contentId) }

        Screen.SettingTheme -> HeroPage(screen.heroKey()) { SettingThemePage() }
        Screen.SettingPalette -> HeroPage(screen.heroKey()) { SettingPalettePage() }
        Screen.SettingPreferences -> HeroPage(screen.heroKey()) { SettingPreferencesPage() }
        Screen.SettingPreferencesTheme -> HeroPage(screen.heroKey()) { SettingPreferencesThemePage() }
        Screen.SettingPreferencesNotification -> HeroPage(screen.heroKey()) { SettingPreferencesNotificationPage() }
        Screen.SettingPreferencesGeneral -> HeroPage(screen.heroKey()) { SettingPreferencesGeneralPage() }
        Screen.SettingPreferencesUI -> HeroPage(screen.heroKey()) { SettingPreferencesUIPage() }
        Screen.SettingPreferencesNetwork -> HeroPage(screen.heroKey()) { SettingPreferencesNetworkPage() }

        Screen.SettingProvider -> HeroPage(screen.heroKey()) { SettingProviderPage() }
        is Screen.SettingProviderDetail -> HeroPage(screen.heroKey()) {
            SettingProviderDetailPage(id = Uuid.parse(screen.providerId))
        }

        Screen.SettingModels -> HeroPage(screen.heroKey()) { SettingModelPage() }
        is Screen.ModelEditor -> HeroPage(screen.heroKey()) {
            ModelEditorPage(
                providerId = Uuid.parse(screen.providerId),
                modelId = screen.modelId,
            )
        }

        Screen.SettingAbout -> HeroPage(screen.heroKey()) { SettingAboutPage() }
        Screen.SettingSearch -> HeroPage(screen.heroKey()) { SettingSearchPage() }
        is Screen.SettingSearchDetail -> HeroPage(screen.heroKey()) {
            SettingSearchDetailPage(Uuid.parse(screen.serviceId))
        }

        Screen.SettingSpeech -> HeroPage(screen.heroKey()) { SettingSpeechPage() }
        Screen.SettingMedia -> HeroPage(screen.heroKey()) { SettingMediaPage() }
        is Screen.SettingMediaProviderDetail -> HeroPage(screen.heroKey()) {
            SettingMediaProviderDetailPage(id = Uuid.parse(screen.providerId))
        }

        Screen.SettingMcp -> HeroPage(screen.heroKey()) { SettingMcpPage() }
        Screen.ToolOverrides -> HeroPage(screen.heroKey()) { SettingToolOverridePage() }
        is Screen.SettingMcpDetail -> HeroPage(screen.heroKey()) {
            SettingMcpDetailPage(id = Uuid.parse(screen.serverId))
        }

        Screen.SettingDonate -> HeroPage(screen.heroKey()) { SettingDonatePage() }
        Screen.SettingFiles -> HeroPage(screen.heroKey()) { SettingFilesPage() }
        Screen.SettingWeb -> HeroPage(screen.heroKey()) { SettingWebPage() }
        Screen.Debug -> HeroPage(screen.heroKey()) { DebugPage() }
        Screen.Log -> HeroPage(screen.heroKey()) { LogPage() }

        Screen.Extensions -> HeroPage(screen.heroKey()) { ExtensionsPage() }
        Screen.QuickMessages -> HeroPage(screen.heroKey()) { QuickMessagesPage() }
        Screen.Prompts -> HeroPage(screen.heroKey()) { PromptPage() }
        Screen.Skills -> HeroPage(screen.heroKey()) { SkillsPage() }

        Screen.Workspaces -> HeroPage(screen.heroKey()) { WorkspacePage() }
        is Screen.WorkspaceDetail -> HeroPage(screen.heroKey()) { WorkspaceDetailPage(screen.id) }
        is Screen.WorkspaceTerminal -> HeroPage(screen.heroKey()) { WorkspaceTerminalPage(screen.id) }
        is Screen.WorkspaceSshEditor -> HeroPage(screen.heroKey()) { WorkspaceSshEditorPage(screen.id) }
        is Screen.WorkspaceFileEditor -> HeroPage(screen.heroKey()) {
            WorkspaceFileEditorPage(
                id = screen.id,
                area = WorkspaceStorageArea.valueOf(screen.area),
                path = screen.path,
            )
        }

        is Screen.SkillDetail -> HeroPage(screen.heroKey()) { SkillDetailPage(skillName = screen.skillName) }

        Screen.MessageSearch -> HeroPage(screen.heroKey()) { SearchPage() }
        Screen.Stats -> HeroPage(screen.heroKey()) { StatsPage() }
    }.let { /* when 对 sealed interface 必须穷尽，漏了分支这里编译不过 */ }
}
