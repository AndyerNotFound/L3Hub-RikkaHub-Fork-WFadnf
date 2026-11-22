package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import me.rerere.hugeicons.HugeIcons
import me.rerere.rikkahub.R
import me.rerere.hugeicons.stroke.Book03
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.SaltGroup
import me.rerere.rikkahub.ui.components.ui.SaltIconRow
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus

@Composable
fun ExtensionsPage() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.extensions_page_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 横向内距交给 Salt 的 RoundedColumn（自带 16dp），这里不再补
            contentPadding = innerPadding + PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                val containerColor = MaterialTheme.colorScheme.primaryContainer
                val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                SaltGroup(title = stringResource(R.string.extensions_page_section_extensions)) {
                    SaltIconRow(
                        icon = HugeIcons.Zap,
                        containerColor = containerColor,
                        contentColor = contentColor,
                        headline = stringResource(R.string.assistant_page_quick_messages),
                        supporting = stringResource(R.string.extensions_page_quick_messages_desc),
                        onClick = { navController.navigate(Screen.QuickMessages) },
                    )
                    SaltIconRow(
                        icon = HugeIcons.Book03,
                        containerColor = containerColor,
                        contentColor = contentColor,
                        headline = stringResource(R.string.extensions_page_prompts),
                        supporting = stringResource(R.string.extensions_page_prompts_desc),
                        onClick = { navController.navigate(Screen.Prompts) },
                    )
                    SaltIconRow(
                        icon = HugeIcons.Puzzle,
                        containerColor = containerColor,
                        contentColor = contentColor,
                        headline = stringResource(R.string.extensions_page_agent_skills),
                        supporting = stringResource(R.string.extensions_page_agent_skills_desc),
                        onClick = { navController.navigate(Screen.Skills) },
                    )
                    SaltIconRow(
                        icon = HugeIcons.Folder01,
                        containerColor = containerColor,
                        contentColor = contentColor,
                        headline = stringResource(R.string.extensions_page_workspace),
                        supporting = stringResource(R.string.extensions_page_workspace_desc),
                        onClick = { navController.navigate(Screen.Workspaces) },
                    )
                }
            }
        }
    }
}
