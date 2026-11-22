package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.ItemContainer
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import me.rerere.asr.ASRProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.SaltSwitchRow
import me.rerere.ui.components.OutlinedNumberInput
import me.rerere.ui.components.SelectTextField

val ASRProviderSetting.typeName: String
    get() = when (this) {
        is ASRProviderSetting.OpenAIRealtime -> "OpenAI Realtime"
        is ASRProviderSetting.DashScope -> "DashScope"
        is ASRProviderSetting.Volcengine -> "Volcengine"
        is ASRProviderSetting.MiMo -> "MiMo"
        is ASRProviderSetting.Step -> "Step"
        is ASRProviderSetting.DeepSeek -> "DeepSeek"
    }

@Composable
fun ASRProviderConfigure(
    setting: ASRProviderSetting,
    modifier: Modifier = Modifier,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        RoundedColumn {
            // 类型在新建时选定，之后不能改
            ItemContainer {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = SaltTheme.dimens.item),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.setting_asr_configure_provider_type))
                        Spacer(modifier = Modifier.height(2.dp))
                        CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                            ProvideTextStyle(SaltTheme.textStyles.sub) {
                                Text(stringResource(R.string.setting_asr_configure_provider_type_desc))
                            }
                        }
                    }
                    Text(text = setting.typeName, color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
                }
            }

            ItemContainer {
                Column {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.setting_asr_configure_name))
                            Spacer(modifier = Modifier.height(2.dp))
                            CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                                ProvideTextStyle(SaltTheme.textStyles.sub) {
                                    Text(stringResource(R.string.setting_asr_configure_name_desc))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = setting.name,
                        onValueChange = { onValueChange(setting.copyProvider(name = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("OpenAI Realtime") }
                    )
                }
            }
        }

        RoundedColumn {
            when (setting) {
                is ASRProviderSetting.OpenAIRealtime -> openAIRealtimeASRItems(setting, onValueChange)
                is ASRProviderSetting.DashScope -> dashScopeASRItems(setting, onValueChange)
                is ASRProviderSetting.Volcengine -> volcengineASRItems(setting, onValueChange)
                is ASRProviderSetting.MiMo -> miMoASRItems(setting, onValueChange)
                is ASRProviderSetting.Step -> stepASRItems(setting, onValueChange)
                is ASRProviderSetting.DeepSeek -> deepSeekASRItems(setting, onValueChange)
            }
        }
    }
}

@Composable
private fun openAIRealtimeASRItems(
    setting: ASRProviderSetting.OpenAIRealtime,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_api_key))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_openai_api_key_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.apiKey,
                onValueChange = { onValueChange(setting.copy(apiKey = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-...") }
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_websocket_url))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_openai_websocket_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.websocketUrl,
                onValueChange = { onValueChange(setting.copy(websocketUrl = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("wss://api.openai.com/v1/realtime?intent=transcription") }
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_model))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_model_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.model,
                onValueChange = { onValueChange(setting.copy(model = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("gpt-4o-transcribe") }
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_language))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_language_iso_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.language,
                onValueChange = { onValueChange(setting.copy(language = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("auto") }
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_prompt))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_prompt_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.prompt,
                onValueChange = { onValueChange(setting.copy(prompt = it)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                placeholder = { Text("Optional") }
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_vad_threshold))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_vad_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedNumberInput(
                value = setting.vadThreshold,
                onValueChange = { value ->
                    if (value in 0.0f..1.0f) {
                        onValueChange(setting.copy(vadThreshold = value))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = "VAD Threshold"
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_prefix_padding))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_prefix_padding_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedNumberInput(
                value = setting.prefixPaddingMs,
                onValueChange = { value ->
                    if (value in 0..2000) {
                        onValueChange(setting.copy(prefixPaddingMs = value))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = "Prefix Padding"
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_silence_duration))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_silence_duration_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedNumberInput(
                value = setting.silenceDurationMs,
                onValueChange = { value ->
                    if (value in 100..5000) {
                        onValueChange(setting.copy(silenceDurationMs = value))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = "Silence Duration"
            )
        }
    }
}

@Composable
private fun dashScopeASRItems(
    setting: ASRProviderSetting.DashScope,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_api_key))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_dashscope_api_key_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.apiKey,
                onValueChange = { onValueChange(setting.copy(apiKey = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-...") }
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_websocket_url))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_dashscope_websocket_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.websocketUrl,
                onValueChange = { onValueChange(setting.copy(websocketUrl = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("wss://dashscope.aliyuncs.com/api-ws/v1/realtime") }
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_model))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_model_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.model,
                onValueChange = { onValueChange(setting.copy(model = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("qwen3-asr-flash-realtime-2026-02-10") }
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_language))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_language_iso_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.language,
                onValueChange = { onValueChange(setting.copy(language = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("zh") }
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_vad_threshold))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_dashscope_vad_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedNumberInput(
                value = setting.vadThreshold,
                onValueChange = { value ->
                    if (value in -1.0f..1.0f) {
                        onValueChange(setting.copy(vadThreshold = value))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = "VAD Threshold"
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_silence_duration))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_silence_duration_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedNumberInput(
                value = setting.silenceDurationMs,
                onValueChange = { value ->
                    if (value in 200..6000) {
                        onValueChange(setting.copy(silenceDurationMs = value))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = "Silence Duration"
            )
        }
    }
}

@Composable
private fun volcengineASRItems(
    setting: ASRProviderSetting.Volcengine,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_api_key))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_volcengine_api_key_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.apiKey,
                onValueChange = { onValueChange(setting.copy(apiKey = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("your-api-key") }
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_websocket_url))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_volcengine_websocket_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.websocketUrl,
                onValueChange = { onValueChange(setting.copy(websocketUrl = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async") }
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_resource_id))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_resource_id_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.resourceId,
                onValueChange = { onValueChange(setting.copy(resourceId = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("volc.seedasr.sauc.duration") }
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_language))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_language_code_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.language,
                onValueChange = { onValueChange(setting.copy(language = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("auto") }
            )
        }
    }
    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_page_asr_silence_duration))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_page_asr_silence_duration_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedNumberInput(
                value = setting.silenceDurationMs,
                onValueChange = { value ->
                    if (value in 300..5000) onValueChange(setting.copy(silenceDurationMs = value))
                },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.setting_page_asr_milliseconds),
            )
        }
    }

}

@Composable
private fun miMoASRItems(
    setting: ASRProviderSetting.MiMo,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_api_key))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_mimo_api_key_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.apiKey,
                onValueChange = { onValueChange(setting.copy(apiKey = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-... or tp-...") }
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_base_url))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_mimo_base_url_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.baseUrl,
                onValueChange = { onValueChange(setting.copy(baseUrl = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://api.xiaomimimo.com/v1") }
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_model))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_mimo_model_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.model,
                onValueChange = { onValueChange(setting.copy(model = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("mimo-v2.5-asr") }
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_language))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_mimo_language_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.language,
                onValueChange = { onValueChange(setting.copy(language = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("auto") }
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_sample_rate))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_mimo_sample_rate_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedNumberInput(
                value = setting.sampleRate,
                onValueChange = { value ->
                    if (value in 8000..48000) {
                        onValueChange(setting.copy(sampleRate = value))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = "Sample Rate"
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_segment_duration))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_mimo_segment_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedNumberInput(
                value = setting.segmentDurationSec,
                onValueChange = { value ->
                    if (value in 0..300) {
                        onValueChange(setting.copy(segmentDurationSec = value))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = "Segment Duration (s)"
            )
        }
    }
}

@Composable
private fun deepSeekASRItems(
    setting: ASRProviderSetting.DeepSeek,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_api_key))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_deepseek_api_key_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.apiKey,
                onValueChange = { onValueChange(setting.copy(apiKey = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-...") }
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_base_url))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_deepseek_base_url_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.baseUrl,
                onValueChange = { onValueChange(setting.copy(baseUrl = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://api.deepseek.com/v1") }
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_sample_rate))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_deepseek_sample_rate_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SelectTextField(
                value = setting.sampleRate.toString(),
                options = listOf(8000, 16000, 22050, 24000, 32000, 44100, 48000),
                onOptionSelected = { value -> onValueChange(setting.copy(sampleRate = value)) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                label = { Text("Sample Rate") },
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_segment_duration))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_deepseek_segment_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedNumberInput(
                value = setting.segmentDurationSec,
                onValueChange = { value ->
                    if (value in 1..120) {
                        onValueChange(setting.copy(segmentDurationSec = value))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = "Segment Duration (s)"
            )
        }
    }
}

@Composable
private fun stepASRItems(
    setting: ASRProviderSetting.Step,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_api_key))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_step_api_key_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.apiKey,
                onValueChange = { onValueChange(setting.copy(apiKey = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("your-stepfun-api-key") }
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_base_url))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_step_base_url_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.baseUrl,
                onValueChange = { onValueChange(setting.copy(baseUrl = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://api.stepfun.com") }
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_model))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_step_model_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.model,
                onValueChange = { onValueChange(setting.copy(model = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("stepaudio-2.5-asr") }
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_language))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_step_language_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.language,
                onValueChange = { onValueChange(setting.copy(language = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("auto") }
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_sample_rate))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_step_sample_rate_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedNumberInput(
                value = setting.sampleRate,
                onValueChange = { value ->
                    if (value in 8000..48000) {
                        onValueChange(setting.copy(sampleRate = value))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = "Sample Rate"
            )
        }
    }

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_segment_duration))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_step_segment_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedNumberInput(
                value = setting.segmentDurationSec,
                onValueChange = { value ->
                    if (value in 0..300) {
                        onValueChange(setting.copy(segmentDurationSec = value))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = "Segment Duration (s)"
            )
        }
    }

    SaltSwitchRow(
        checked = setting.enableItn,
        onCheckedChange = { onValueChange(setting.copy(enableItn = it)) },
        headline = stringResource(R.string.setting_asr_configure_step_itn),
        supporting = stringResource(R.string.setting_asr_configure_step_itn_desc),
    )

    SaltSwitchRow(
        checked = setting.enableTimestamp,
        onCheckedChange = { onValueChange(setting.copy(enableTimestamp = it)) },
        headline = stringResource(R.string.setting_asr_configure_step_timestamp),
        supporting = stringResource(R.string.setting_asr_configure_step_timestamp_desc),
    )

    ItemContainer {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_step_hotwords))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides SaltTheme.colors.subText) {
                        ProvideTextStyle(SaltTheme.textStyles.sub) {
                            Text(stringResource(R.string.setting_asr_configure_step_hotwords_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                // 用逗号分隔展示, 输入时按逗号 split 回 List
                value = setting.hotwords.joinToString(","),
                onValueChange = { text ->
                    val list = text.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    onValueChange(setting.copy(hotwords = list))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.setting_asr_configure_step_hotwords_placeholder)) }
            )
        }
    }
}
