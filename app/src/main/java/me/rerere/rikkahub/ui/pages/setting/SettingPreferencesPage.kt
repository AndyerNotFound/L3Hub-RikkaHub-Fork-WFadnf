package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.BubbleChat
import me.rerere.hugeicons.stroke.CloudServer
import me.rerere.hugeicons.stroke.Notification01
import me.rerere.hugeicons.stroke.PaintBoard
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sun01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AppIconRow
import me.rerere.rikkahub.ui.components.ui.AppSettingsCard
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.style.LargeFlexibleTopAppBar
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

/**
 * 偏好设置：分类入口页。每一类点击后进入对应子页（多级设置）。
 */
@Composable
fun SettingPreferencesPage(vm: SettingVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.setting_page_preferences))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 横向内距由 AppSettingsCard 自带（12dp）
            contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item("preferences") {
                val containerColor = MaterialTheme.colorScheme.primaryContainer
                val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                AppSettingsCard {
                    AppIconRow(
                        icon = HugeIcons.Settings03,
                        headline = stringResource(R.string.setting_page_preferences_general),
                        containerColor = containerColor,
                        contentColor = contentColor,
                        supporting = stringResource(R.string.setting_page_preferences_general_desc),
                        onClick = { navController.navigate(Screen.SettingPreferencesGeneral) },
                    )
                    AppIconRow(
                        icon = HugeIcons.Sun01,
                        headline = stringResource(R.string.setting_page_preferences_theme),
                        containerColor = containerColor,
                        contentColor = contentColor,
                        supporting = stringResource(R.string.setting_page_preferences_theme_desc),
                        onClick = { navController.navigate(Screen.SettingPreferencesTheme) },
                    )
                    AppIconRow(
                        icon = HugeIcons.PaintBoard,
                        headline = stringResource(R.string.setting_page_preferences_ui),
                        containerColor = containerColor,
                        contentColor = contentColor,
                        supporting = stringResource(R.string.setting_page_preferences_ui_desc),
                        onClick = { navController.navigate(Screen.SettingPreferencesUI) },
                    )
                    AppIconRow(
                        icon = HugeIcons.Notification01,
                        headline = stringResource(R.string.setting_page_preferences_notification),
                        containerColor = containerColor,
                        contentColor = contentColor,
                        supporting = stringResource(R.string.setting_page_preferences_notification_desc),
                        onClick = { navController.navigate(Screen.SettingPreferencesNotification) },
                    )
                    AppIconRow(
                        icon = HugeIcons.CloudServer,
                        headline = stringResource(R.string.setting_page_preferences_network),
                        containerColor = containerColor,
                        contentColor = contentColor,
                        supporting = stringResource(R.string.setting_page_preferences_network_desc),
                        onClick = { navController.navigate(Screen.SettingPreferencesNetwork) },
                    )
                    AppIconRow(
                        icon = HugeIcons.BubbleChat,
                        headline = stringResource(R.string.setting_page_preferences_message_style),
                        containerColor = containerColor,
                        contentColor = contentColor,
                        supporting = stringResource(R.string.setting_page_preferences_message_style_desc),
                        onClick = { navController.navigate(Screen.SettingPreferencesMessageStyle) },
                    )
                }
            }
        }
    }
}
