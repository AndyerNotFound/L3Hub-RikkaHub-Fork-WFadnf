package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors

/**
 * 偏好设置：所有选项都在这一页，按类别分组，不再收进二级菜单。
 */
@Composable
fun SettingPreferencesPage() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item { PreferencesSectionTitle(stringResource(R.string.setting_page_preferences_theme)) }
            item { SettingPreferencesThemePage(embedded = true) }

            item { PreferencesSectionTitle(stringResource(R.string.setting_page_preferences_ui)) }
            item { SettingPreferencesUIPage(embedded = true) }

            item { PreferencesSectionTitle(stringResource(R.string.setting_page_preferences_general)) }
            item { SettingPreferencesGeneralPage(embedded = true) }

            item { PreferencesSectionTitle(stringResource(R.string.setting_page_preferences_notification)) }
            item { SettingPreferencesNotificationPage(embedded = true) }

            item { PreferencesSectionTitle(stringResource(R.string.setting_page_preferences_network)) }
            item { SettingPreferencesNetworkPage(embedded = true) }
        }
    }
}

@Composable
private fun PreferencesSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
    )
}
