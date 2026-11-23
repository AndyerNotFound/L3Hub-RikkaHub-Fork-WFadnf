package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import me.rerere.rikkahub.ui.style.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.AppNavItem
import me.rerere.rikkahub.ui.components.ui.AppSwitchRow

import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPadding
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingPreferencesThemePage(embedded: Boolean = false, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var amoledDarkMode by rememberAmoledDarkMode()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current

    

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 横向内距由 AppSettingsCard 自带（12dp 横向外距），这里只留全宽模式的刘海安全区
                .adaptiveHorizontalPadding(0.dp)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppSettingsCard {
                AppSwitchRow(
                    checked = settings.dynamicColor,
                    onCheckedChange = { vm.updateSettings { latest -> latest.copy(dynamicColor = it) } },
                    headline = stringResource(R.string.setting_page_dynamic_color),
                    supporting = stringResource(R.string.setting_page_dynamic_color_desc),
                )
                AppNavItem(
                    onClick = { navController.navigate(Screen.SettingTheme) },
                    text = stringResource(R.string.setting_page_theme_setting),
                    sub = stringResource(R.string.setting_page_theme_setting_desc),
                )
                AppNavItem(
                    onClick = { navController.navigate(Screen.SettingPalette) },
                    text = stringResource(R.string.setting_page_palette),
                    sub = stringResource(R.string.setting_page_palette_desc),
                )
                AppSwitchRow(
                    checked = amoledDarkMode,
                    onCheckedChange = { amoledDarkMode = it },
                    headline = stringResource(R.string.setting_display_page_amoled_dark_mode_title),
                    supporting = stringResource(R.string.setting_display_page_amoled_dark_mode_desc),
                )
            }

        }
    }
    if (embedded) {
        content()
    } else {
        Scaffold(
            topBar = {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(stringResource(R.string.setting_page_preferences_theme))
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding),
            ) {
                content()
            }
        }
    }
}
