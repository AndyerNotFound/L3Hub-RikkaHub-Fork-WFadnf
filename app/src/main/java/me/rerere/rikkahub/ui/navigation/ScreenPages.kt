package me.rerere.rikkahub.ui.navigation

import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import me.rerere.rikkahub.Screen
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
import me.rerere.rikkahub.ui.pages.quickstart.QuickStartPage
import me.rerere.rikkahub.ui.pages.setting.SettingFilesPage
import me.rerere.rikkahub.ui.pages.setting.SettingMcpDetailPage
import me.rerere.rikkahub.ui.pages.setting.SettingMcpPage
import me.rerere.rikkahub.ui.pages.setting.SettingMediaPage
import me.rerere.rikkahub.ui.pages.setting.SettingMediaProviderDetailPage
import me.rerere.rikkahub.ui.pages.setting.SettingModelPage
import me.rerere.rikkahub.ui.pages.setting.SettingPage
import me.rerere.rikkahub.ui.pages.setting.SettingPalettePage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesAdvancedAppearancePage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesGeneralPage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesMessageStylePage
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
 * 页面转场统一由 `ui/navigation/StackHost.kt` 的 fade + slide + scale 承担，
 * 这里不再有页面级容器变换包装。
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

        is Screen.ShareHandler -> {
            ShareHandlerPage(
                text = screen.text,
                image = screen.streamUri,
            )
        }

        Screen.History -> { HistoryPage() }
        Screen.Favorite -> { FavoritePage() }
        Screen.Assistant -> { AssistantPage() }

        is Screen.AssistantDetail -> { AssistantDetailPage(screen.id) }
        is Screen.AssistantBasic -> { AssistantBasicPage(screen.id) }
        is Screen.AssistantPrompt -> { AssistantPromptPage(screen.id) }
        is Screen.AssistantMemory -> { AssistantMemoryPage(screen.id) }

        is Screen.MemoryFile -> {
            MemoryFilePage(memoryId = screen.memoryId, path = screen.path)
        }

        is Screen.AssistantRequest -> { AssistantRequestPage(screen.id) }
        is Screen.AssistantHeartbeat -> { AssistantHeartbeatPage(screen.id) }
        is Screen.AssistantMcp -> { AssistantMcpPage(screen.id) }
        is Screen.AssistantLocalTool -> { AssistantLocalToolPage(screen.id) }
        is Screen.AssistantInjections -> { AssistantExtensionsPage(screen.id) }

        Screen.Translator -> { TranslatorPage() }
        Screen.Setting -> SettingPage()

        Screen.Backup -> { BackupPage() }
        Screen.ImageGen -> { ImageGenPage() }
        Screen.MediaCreationSessions -> { MediaCreationSessionsPage() }

        is Screen.MediaCreation -> MediaCreationPage(screen.id)

        is Screen.WebView -> { WebViewPage(screen.url, screen.contentId, screen.offlineMessagePreview) }

        Screen.SettingTheme -> { SettingThemePage() }
        Screen.SettingPalette -> { SettingPalettePage() }
        Screen.SettingPreferences -> { SettingPreferencesPage() }
        Screen.SettingPreferencesAdvancedAppearance -> { SettingPreferencesAdvancedAppearancePage() }
        Screen.SettingPreferencesTheme -> { SettingPreferencesThemePage() }
        Screen.SettingPreferencesNotification -> { SettingPreferencesNotificationPage() }
        Screen.SettingPreferencesGeneral -> { SettingPreferencesGeneralPage() }
        Screen.SettingPreferencesMessageStyle -> { SettingPreferencesMessageStylePage() }
        Screen.SettingPreferencesUI -> { SettingPreferencesUIPage() }
        Screen.SettingPreferencesNetwork -> { SettingPreferencesNetworkPage() }

        Screen.SettingProvider -> { SettingProviderPage() }
        is Screen.SettingProviderDetail -> {
            SettingProviderDetailPage(id = Uuid.parse(screen.providerId))
        }

        Screen.SettingModels -> { SettingModelPage() }
        is Screen.ModelEditor -> {
            ModelEditorPage(
                providerId = Uuid.parse(screen.providerId),
                modelId = screen.modelId,
                keyIndex = screen.keyIndex,
                accountId = screen.accountId,
            )
        }

        Screen.SettingAbout -> { SettingAboutPage() }
        Screen.SettingSearch -> { SettingSearchPage() }
        is Screen.SettingSearchDetail -> {
            SettingSearchDetailPage(Uuid.parse(screen.serviceId))
        }

        Screen.SettingSpeech -> { SettingSpeechPage() }
        Screen.SettingMedia -> { SettingMediaPage() }
        is Screen.SettingMediaProviderDetail -> {
            SettingMediaProviderDetailPage(id = Uuid.parse(screen.providerId))
        }

        Screen.SettingMcp -> { SettingMcpPage() }
        Screen.ToolOverrides -> { SettingToolOverridePage() }
        is Screen.SettingMcpDetail -> {
            SettingMcpDetailPage(id = Uuid.parse(screen.serverId))
        }

        Screen.SettingDonate -> { SettingDonatePage() }
        Screen.QuickStart -> { QuickStartPage() }
        Screen.SettingFiles -> { SettingFilesPage() }
        Screen.SettingWeb -> { SettingWebPage() }
        Screen.Debug -> { DebugPage() }
        Screen.Log -> { LogPage() }

        Screen.Extensions -> { ExtensionsPage() }
        Screen.QuickMessages -> { QuickMessagesPage() }
        Screen.Prompts -> { PromptPage() }
        Screen.Skills -> { SkillsPage() }

        Screen.Workspaces -> { WorkspacePage() }
        is Screen.WorkspaceDetail -> { WorkspaceDetailPage(screen.id) }
        is Screen.WorkspaceTerminal -> { WorkspaceTerminalPage(screen.id) }
        is Screen.WorkspaceSshEditor -> { WorkspaceSshEditorPage(screen.id) }
        is Screen.WorkspaceFileEditor -> {
            WorkspaceFileEditorPage(
                id = screen.id,
                area = WorkspaceStorageArea.valueOf(screen.area),
                path = screen.path,
            )
        }

        is Screen.SkillDetail -> { SkillDetailPage(skillName = screen.skillName) }

        Screen.MessageSearch -> { SearchPage() }
        Screen.Stats -> { StatsPage() }
    }.let { /* when 对 sealed interface 必须穷尽，漏了分支这里编译不过 */ }
}
