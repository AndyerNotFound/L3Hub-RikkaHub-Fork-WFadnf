package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moriafly.salt.ui.ItemContainer
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.rikkahub.ui.components.ui.AppSlider
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.HeartbeatSetting
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.modelSettingItem
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.NumberSlider
import me.rerere.rikkahub.ui.components.ui.SaltSwitchRow
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

@Composable
fun AssistantHeartbeatPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val heartbeat = assistant.heartbeat
    val modelState = rememberModelListState(
        modelId = heartbeat.modelId,
        providers = settings.providers,
        type = ModelType.CHAT,
    )

    fun update(block: (HeartbeatSetting) -> HeartbeatSetting) {
        vm.update(assistant.copy(heartbeat = block(heartbeat)))
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_heartbeat))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoundedColumn {
                SaltSwitchRow(
                    checked = heartbeat.enabled,
                    onCheckedChange = { enabled -> update { it.copy(enabled = enabled) } },
                    headline = stringResource(R.string.heartbeat_page_enable_title),
                    supporting = stringResource(R.string.heartbeat_page_enable_desc),
                )
            }

            RoundedColumn {
                ItemContainer {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.heartbeat_page_interval_title))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.heartbeat_page_interval_desc),
                                    color = SaltTheme.colors.subText,
                                    style = SaltTheme.textStyles.sub,
                                )
                            }
                            ValuePill(text = "${heartbeat.intervalMinutes} min")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        NumberSlider(
                            value = heartbeat.intervalMinutes,
                            onValueChange = { value ->
                                update { it.copy(intervalMinutes = value) }
                            },
                            valueRange = 5..720,
                            suffix = "min",
                        )
                    }
                }
                ItemContainer {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.heartbeat_page_hours_title))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.heartbeat_page_hours_desc),
                                    color = SaltTheme.colors.subText,
                                    style = SaltTheme.textStyles.sub,
                                )
                            }
                            ValuePill(text = "%02d:00 - %02d:00".format(heartbeat.startHour, heartbeat.endHour))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = stringResource(R.string.heartbeat_page_hours_start),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            AppSlider(
                                value = heartbeat.startHour.toFloat(),
                                onValueChange = { value ->
                                    update { it.copy(startHour = value.roundToInt().coerceIn(0, 23)) }
                                },
                                valueRange = 0f..23f,
                                steps = 22,
                            )
                            Text(
                                text = stringResource(R.string.heartbeat_page_hours_end),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            AppSlider(
                                value = heartbeat.endHour.toFloat(),
                                onValueChange = { value ->
                                    update { it.copy(endHour = value.roundToInt().coerceIn(0, 23)) }
                                },
                                valueRange = 0f..23f,
                                steps = 22,
                            )
                        }
                    }
                }
            }

            RoundedColumn {
                ItemContainer {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.heartbeat_page_prompt_title))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.heartbeat_page_prompt_desc),
                                    color = SaltTheme.colors.subText,
                                    style = SaltTheme.textStyles.sub,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = heartbeat.prompt,
                            onValueChange = { text -> update { it.copy(prompt = text) } },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.heartbeat_page_prompt_placeholder)) },
                            minLines = 2,
                            maxLines = 6,
                        )
                    }
                }
            }

            val modelIconContainer = MaterialTheme.colorScheme.tertiaryContainer
            val modelIconContent = MaterialTheme.colorScheme.onTertiaryContainer
            RoundedColumn {
                modelSettingItem(
                    icon = HugeIcons.Clock01,
                    title = R.string.heartbeat_page_model_title,
                    state = modelState,
                    iconContainerColor = modelIconContainer,
                    iconContentColor = modelIconContent,
                    placeholder = R.string.heartbeat_page_model_desc,
                    onClear = { update { it.copy(modelId = null) } },
                    clearContentDescription = R.string.heartbeat_page_model_clear,
                )
            }
        }
    }

    ModelListSheet(
        state = modelState,
        onSelect = { model ->
            update { it.copy(modelId = model.id) }
        },
    )
}

// 标题行右侧的取值小标签，保持旧版胶囊观感；等宽数字，拖动滑块时宽度不跳
@Composable
private fun ValuePill(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
    }
}
