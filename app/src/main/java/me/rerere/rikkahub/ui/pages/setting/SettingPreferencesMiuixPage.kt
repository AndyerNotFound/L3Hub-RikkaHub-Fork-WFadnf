package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.MiuixColorSource
import me.rerere.rikkahub.data.datastore.MiuixPalette
import me.rerere.rikkahub.data.datastore.MiuixPressFeedback
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.NumberSlider
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPadding
import org.koin.androidx.compose.koinViewModel

/**
 * Miuix 专用的外观选项。只在「界面风格」选了 Miuix 时出现，挂在偏好设置里。
 */
@Composable
fun SettingPreferencesMiuixPage(embedded: Boolean = false, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun updateDisplay(transform: (DisplaySetting) -> DisplaySetting) {
        vm.updateSettings { latest -> latest.copy(displaySetting = transform(latest.displaySetting)) }
    }

    val colorSource = settings.displaySetting.miuixColorSource
    val paletteEnabled = colorSource == MiuixColorSource.GENERATED
    var paletteExpanded by remember { mutableStateOf(false) }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .adaptiveHorizontalPadding(16.dp)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CardGroup {
                formItem {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(stringResource(R.string.setting_page_miuix_color_source))
                    }
                    ConnectedChoiceButtons(
                        options = MiuixColorSource.entries,
                        selected = colorSource,
                        onSelect = { source ->
                            updateDisplay { it.copy(miuixColorSource = source) }
                        },
                    ) { source, selected ->
                        ConnectedChoiceLabel(
                            text = when (source) {
                                MiuixColorSource.THEME -> stringResource(R.string.setting_page_miuix_color_source_theme)
                                MiuixColorSource.GENERATED -> stringResource(R.string.setting_page_miuix_color_source_generated)
                                MiuixColorSource.DEFAULT -> stringResource(R.string.setting_page_miuix_color_source_default)
                            },
                            selected = selected,
                        )
                    }
                }
                Box {
                    item(
                        onClick = if (paletteEnabled) {
                            { paletteExpanded = true }
                        } else null,
                        headlineContent = { Text(stringResource(R.string.setting_page_miuix_palette)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_miuix_palette_desc)) },
                        trailingContent = { Text(settings.displaySetting.miuixPalette.label()) },
                    )
                    DropdownMenu(
                        expanded = paletteExpanded,
                        onDismissRequest = { paletteExpanded = false },
                    ) {
                        MiuixPalette.entries.forEach { palette ->
                            DropdownMenuItem(
                                text = { Text(palette.label()) },
                                onClick = {
                                    updateDisplay { it.copy(miuixPalette = palette) }
                                    paletteExpanded = false
                                },
                            )
                        }
                    }
                }
                formItem {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(stringResource(R.string.setting_page_miuix_corner_radius))
                    }
                    NumberSlider(
                        value = settings.displaySetting.miuixCornerRadius,
                        onValueChange = { radius ->
                            updateDisplay { it.copy(miuixCornerRadius = radius) }
                        },
                        valueRange = 0..32,
                        suffix = "dp",
                    )
                }
                formItem {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(stringResource(R.string.setting_page_miuix_press_feedback))
                    }
                    ConnectedChoiceButtons(
                        options = MiuixPressFeedback.entries,
                        selected = settings.displaySetting.miuixPressFeedback,
                        onSelect = { feedback ->
                            updateDisplay { it.copy(miuixPressFeedback = feedback) }
                        },
                    ) { feedback, selected ->
                        ConnectedChoiceLabel(
                            text = when (feedback) {
                                MiuixPressFeedback.NONE -> stringResource(R.string.setting_page_miuix_press_feedback_none)
                                MiuixPressFeedback.SINK -> stringResource(R.string.setting_page_miuix_press_feedback_sink)
                                MiuixPressFeedback.TILT -> stringResource(R.string.setting_page_miuix_press_feedback_tilt)
                            },
                            selected = selected,
                        )
                    }
                }
                switchItem(
                    checked = settings.displaySetting.miuixOverscroll,
                    onCheckedChange = { enabled ->
                        updateDisplay { it.copy(miuixOverscroll = enabled) }
                    },
                    supportingContent = { Text(stringResource(R.string.setting_page_miuix_overscroll_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_page_miuix_overscroll)) },
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
                        Text(stringResource(R.string.setting_page_miuix_group))
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

@Composable
private fun MiuixPalette.label(): String = stringResource(
    when (this) {
        MiuixPalette.TONAL_SPOT -> R.string.setting_page_miuix_palette_tonal_spot
        MiuixPalette.NEUTRAL -> R.string.setting_page_miuix_palette_neutral
        MiuixPalette.VIBRANT -> R.string.setting_page_miuix_palette_vibrant
        MiuixPalette.EXPRESSIVE -> R.string.setting_page_miuix_palette_expressive
        MiuixPalette.RAINBOW -> R.string.setting_page_miuix_palette_rainbow
        MiuixPalette.FRUIT_SALAD -> R.string.setting_page_miuix_palette_fruit_salad
        MiuixPalette.MONOCHROME -> R.string.setting_page_miuix_palette_monochrome
        MiuixPalette.FIDELITY -> R.string.setting_page_miuix_palette_fidelity
        MiuixPalette.CONTENT -> R.string.setting_page_miuix_palette_content
        MiuixPalette.CMF -> R.string.setting_page_miuix_palette_cmf
    }
)
