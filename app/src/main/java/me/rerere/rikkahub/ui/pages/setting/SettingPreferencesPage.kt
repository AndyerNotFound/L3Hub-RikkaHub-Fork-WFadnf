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
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.navigation.appearancePageContainerColor
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
        containerColor = appearancePageContainerColor(CustomColors.topBarColors.containerColor)
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 横向 16dp：CardGroup 自身不带外距
            contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item("preferences") {
                CardGroup {
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesGeneral) },
                        leadingContent = {
                            CardGroupIcon(
                                HugeIcons.Settings03,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_general)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_general_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesTheme) },
                        leadingContent = {
                            CardGroupIcon(
                                HugeIcons.Sun01,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_theme)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_theme_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesUI) },
                        leadingContent = {
                            CardGroupIcon(
                                HugeIcons.PaintBoard,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_ui)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_ui_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesAdvancedAppearance) },
                        leadingContent = {
                            CardGroupIcon(
                                HugeIcons.PaintBoard,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.aa_title)) },
                        supportingContent = { Text(stringResource(R.string.aa_description)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesNotification) },
                        leadingContent = {
                            CardGroupIcon(
                                HugeIcons.Notification01,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_notification)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_notification_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesNetwork) },
                        leadingContent = {
                            CardGroupIcon(
                                HugeIcons.CloudServer,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_network)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_network_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesMessageStyle) },
                        leadingContent = {
                            CardGroupIcon(
                                HugeIcons.BubbleChat,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_message_style)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_message_style_desc)) },
                    )
                }
            }
        }
    }
}
