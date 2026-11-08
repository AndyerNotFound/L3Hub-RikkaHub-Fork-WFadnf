package me.rerere.rikkahub.ui.pages.setting

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.hugeicons.stroke.Bookshelf01
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Clapping01
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.GlobalSearch
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.ImageUpload
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.McpServer
import me.rerere.hugeicons.stroke.Megaphone01
import me.rerere.hugeicons.stroke.Package
import me.rerere.hugeicons.stroke.ServerStack01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen

/**
 * 横屏两栏时设置页左列的一级选项。
 *
 * 每一项对应设置首页上的一张卡片，顺序也跟首页一致；助手 / 扩展功能 / 数据备份 / 请求日志
 * 这些独立管理页同样收进来，横屏时不用再退回首页点一层。
 */
internal enum class SettingSection(
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val screen: Screen,
) {
    Preferences(R.string.setting_page_preferences, HugeIcons.Settings03, Screen.SettingPreferences),
    Assistant(R.string.setting_page_assistant, HugeIcons.LookTop, Screen.Assistant),
    Extensions(R.string.setting_page_extensions, HugeIcons.Package, Screen.Extensions),
    Models(R.string.setting_page_default_model, HugeIcons.AiMagic, Screen.SettingModels),
    Providers(R.string.setting_page_providers, HugeIcons.Brain02, Screen.SettingProvider),
    Search(R.string.setting_page_search_service, HugeIcons.GlobalSearch, Screen.SettingSearch),
    Speech(R.string.setting_page_tts_service, HugeIcons.Megaphone01, Screen.SettingSpeech),
    Media(R.string.setting_page_media, HugeIcons.Image02, Screen.SettingMedia),
    Mcp(R.string.setting_page_mcp, HugeIcons.McpServer, Screen.SettingMcp),
    Web(R.string.setting_page_web_server, HugeIcons.ServerStack01, Screen.SettingWeb),
    Backup(R.string.setting_page_data_backup, HugeIcons.Database02, Screen.Backup),
    Files(R.string.setting_page_chat_storage, HugeIcons.ImageUpload, Screen.SettingFiles),
    About(R.string.setting_page_about, HugeIcons.Clapping01, Screen.SettingAbout),
    Log(R.string.setting_page_request_logs, HugeIcons.Bookshelf01, Screen.Log),
}

private val SettingRailGroups: List<Pair<Int, List<SettingSection>>> = listOf(
    R.string.setting_page_general_settings to listOf(
        SettingSection.Preferences,
        SettingSection.Assistant,
        SettingSection.Extensions,
    ),
    R.string.setting_page_model_and_services to listOf(
        SettingSection.Models,
        SettingSection.Providers,
        SettingSection.Search,
        SettingSection.Speech,
        SettingSection.Media,
        SettingSection.Mcp,
        SettingSection.Web,
    ),
    R.string.setting_page_data_settings to listOf(
        SettingSection.Backup,
        SettingSection.Files,
    ),
    R.string.setting_page_more to listOf(
        SettingSection.About,
        SettingSection.Log,
    ),
)

/** 当前路由落在哪一节；详情页跟着它的一级页走，首页（完全没有选中）返回 null */
internal fun Screen.settingSection(): SettingSection? = when (this) {
    is Screen.SettingPreferences,
    is Screen.SettingPreferencesTheme,
    is Screen.SettingPreferencesNotification,
    is Screen.SettingPreferencesGeneral,
    is Screen.SettingPreferencesUI,
    is Screen.SettingPreferencesNetwork,
    is Screen.SettingTheme,
    is Screen.SettingPalette -> SettingSection.Preferences

    is Screen.Assistant,
    is Screen.AssistantDetail,
    is Screen.AssistantBasic,
    is Screen.AssistantPrompt,
    is Screen.AssistantMemory,
    is Screen.MemoryFile,
    is Screen.AssistantRequest,
    is Screen.AssistantHeartbeat,
    is Screen.AssistantMcp,
    is Screen.AssistantLocalTool,
    is Screen.AssistantInjections -> SettingSection.Assistant

    is Screen.Extensions -> SettingSection.Extensions

    is Screen.SettingModels -> SettingSection.Models
    is Screen.SettingProvider, is Screen.SettingProviderDetail, is Screen.ModelEditor -> SettingSection.Providers
    is Screen.SettingSearch, is Screen.SettingSearchDetail -> SettingSection.Search
    is Screen.SettingSpeech -> SettingSection.Speech
    is Screen.SettingMedia, is Screen.SettingMediaProviderDetail -> SettingSection.Media
    is Screen.SettingMcp, is Screen.SettingMcpDetail -> SettingSection.Mcp
    is Screen.SettingWeb -> SettingSection.Web
    is Screen.Backup -> SettingSection.Backup
    is Screen.SettingFiles -> SettingSection.Files
    is Screen.SettingAbout -> SettingSection.About
    is Screen.Log -> SettingSection.Log
    else -> null
}

/** 左栏自己的一级页：这些页面落在中列，它们的下钻页落在右列 */
internal fun Screen.isSettingSectionPage(): Boolean =
    this is Screen.Setting || SettingSection.entries.any { it.screen == this }

/** 设置相关的页面：这些路由上才显示左列 */
internal fun Screen.isSettingBoard(): Boolean {
    // 模型编辑是聚焦编辑页，它自己就要三栏的宽度，不再挂设置栏
    if (this is Screen.ModelEditor) return false
    return this is Screen.Setting || settingSection() != null
}

/**
 * 设置页的一级选项栏。横屏时贴在左边，点一项把右边换成对应的设置页。
 *
 * 不用抽屉：它和聊天页左栏一样是常驻列，进出子页时一直留着。
 */
@Composable
internal fun SettingRail(
    current: Screen?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = current?.settingSection()

    Surface(
        modifier = modifier
            .width(224.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SettingRailGroups.forEach { (titleRes, sections) ->
                    item(key = "group-$titleRes") {
                        Text(
                            text = stringResource(titleRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 2.dp),
                        )
                    }
                    items(sections, key = { it.name }) { section ->
                        SettingRailItem(
                            section = section,
                            selected = section == selected,
                            onClick = { onNavigate(section.screen) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRailItem(
    section: SettingSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = stringResource(section.titleRes),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
