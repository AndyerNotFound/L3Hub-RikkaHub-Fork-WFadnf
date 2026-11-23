package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.AppSettingsCard
import me.rerere.rikkahub.ui.components.ui.AppSettingsRow
import me.rerere.rikkahub.ui.components.ui.AppSwitchRow
import me.rerere.rikkahub.ui.style.IconButton
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.ui.components.OutlinedNumberInput
import me.rerere.ui.components.SelectTextField

@Composable
fun TTSProviderConfigure(
    setting: TTSProviderSetting,
    modifier: Modifier = Modifier,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        AppSettingsCard {
            AppSettingsRow {
                Column {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.setting_tts_page_provider_type))
                            Spacer(modifier = Modifier.height(2.dp))
                            CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                                ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                                    Text(stringResource(R.string.setting_tts_page_provider_type_description))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectTextField(
                        value = when (setting) {
                            is TTSProviderSetting.OpenAI -> "OpenAI"
                            is TTSProviderSetting.Gemini -> "Gemini"
                            is TTSProviderSetting.SystemTTS -> "System TTS"
                            is TTSProviderSetting.MiniMax -> "MiniMax"
                            is TTSProviderSetting.Qwen -> "Qwen"
                            is TTSProviderSetting.Groq -> "Groq"
                            is TTSProviderSetting.XAI -> "xAI"
                            is TTSProviderSetting.MiMo -> "MiMo"
                            is TTSProviderSetting.Step -> "Step"
                            is TTSProviderSetting.ElevenLabs -> "ElevenLabs"
                            is TTSProviderSetting.FishAudio -> "Fish Audio"
                            is TTSProviderSetting.Volcengine -> stringResource(R.string.setting_tts_page_provider_volcengine)
                            is TTSProviderSetting.DeepSeek -> "DeepSeek"
                        },
                        options = TTSProviderSetting.Types,
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        optionToString = { providerClass ->
                            when (providerClass) {
                                TTSProviderSetting.OpenAI::class -> "OpenAI"
                                TTSProviderSetting.Gemini::class -> "Gemini"
                                TTSProviderSetting.SystemTTS::class -> "System TTS"
                                TTSProviderSetting.MiniMax::class -> "MiniMax"
                                TTSProviderSetting.Qwen::class -> "Qwen"
                                TTSProviderSetting.Groq::class -> "Groq"
                                TTSProviderSetting.XAI::class -> "xAI"
                                TTSProviderSetting.MiMo::class -> "MiMo"
                                TTSProviderSetting.ElevenLabs::class -> "ElevenLabs"
                                TTSProviderSetting.FishAudio::class -> "Fish Audio"
                                TTSProviderSetting.Volcengine::class -> stringResource(R.string.setting_tts_page_provider_volcengine)
                                TTSProviderSetting.DeepSeek::class -> "DeepSeek"
                                TTSProviderSetting.Step::class -> "Step"
                                TTSProviderSetting.DeepSeek::class -> "DeepSeek"
                                else -> providerClass.simpleName ?: "Unknown"
                            }
                        },
                        onOptionSelected = { providerClass ->
                            val newSetting = when (providerClass) {
                                TTSProviderSetting.OpenAI::class -> TTSProviderSetting.OpenAI(
                                    id = setting.id,
                                    name = "OpenAI TTS"
                                )

                                TTSProviderSetting.Gemini::class -> TTSProviderSetting.Gemini(
                                    id = setting.id,
                                    name = "Gemini TTS"
                                )

                                TTSProviderSetting.SystemTTS::class -> TTSProviderSetting.SystemTTS(
                                    id = setting.id,
                                    name = "System TTS"
                                )

                                TTSProviderSetting.MiniMax::class -> TTSProviderSetting.MiniMax(
                                    id = setting.id,
                                    name = "MiniMax TTS"
                                )

                                TTSProviderSetting.Qwen::class -> TTSProviderSetting.Qwen(
                                    id = setting.id,
                                    name = "Qwen TTS"
                                )

                                TTSProviderSetting.Groq::class -> TTSProviderSetting.Groq(
                                    id = setting.id,
                                    name = "Groq TTS"
                                )

                                TTSProviderSetting.XAI::class -> TTSProviderSetting.XAI(
                                    id = setting.id,
                                    name = "xAI TTS"
                                )

                                TTSProviderSetting.MiMo::class -> TTSProviderSetting.MiMo(
                                    id = setting.id,
                                    name = "MiMo TTS"
                                )

                                TTSProviderSetting.ElevenLabs::class -> TTSProviderSetting.ElevenLabs(
                                    id = setting.id,
                                    name = "ElevenLabs TTS"
                                )

                                TTSProviderSetting.FishAudio::class -> TTSProviderSetting.FishAudio(
                                    id = setting.id,
                                    name = "Fish Audio TTS"
                                )

                                TTSProviderSetting.Step::class -> TTSProviderSetting.Step(
                                    id = setting.id,
                                    name = "Step TTS"
                                )

                                TTSProviderSetting.Volcengine::class -> TTSProviderSetting.Volcengine(id = setting.id)

                                TTSProviderSetting.DeepSeek::class -> TTSProviderSetting.DeepSeek(
                                    id = setting.id,
                                    name = "DeepSeek TTS"
                                )

                                else -> setting
                            }
                            onValueChange(newSetting)
                        }
                    )
                }
            }

            // Name
            AppSettingsRow {
                Column {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.setting_tts_page_name))
                            Spacer(modifier = Modifier.height(2.dp))
                            CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                                ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                                    Text(stringResource(R.string.setting_tts_page_name_description))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = setting.name,
                        onValueChange = { newName ->
                            onValueChange(setting.copyProvider(name = newName))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.setting_tts_page_name_placeholder)) }
                    )
                }
            }
        }

        AppSettingsCard {
            when (setting) {
                is TTSProviderSetting.OpenAI -> openAITTSItems(setting, onValueChange)
                is TTSProviderSetting.Gemini -> geminiTTSItems(setting, onValueChange)
                is TTSProviderSetting.MiniMax -> miniMaxTTSItems(setting, onValueChange)
                is TTSProviderSetting.SystemTTS -> systemTTSItems(setting, onValueChange)
                is TTSProviderSetting.Qwen -> qwenTTSItems(setting, onValueChange)
                is TTSProviderSetting.Groq -> groqTTSItems(setting, onValueChange)
                is TTSProviderSetting.XAI -> xAITTSItems(setting, onValueChange)
                is TTSProviderSetting.MiMo -> miMoTTSItems(setting, onValueChange)
                is TTSProviderSetting.ElevenLabs -> elevenLabsTTSItems(setting, onValueChange)
                is TTSProviderSetting.FishAudio -> fishAudioTTSItems(setting, onValueChange)
                is TTSProviderSetting.Step -> stepTTSItems(setting, onValueChange)
                is TTSProviderSetting.Volcengine -> volcengineTTSItems(setting, onValueChange)
                is TTSProviderSetting.DeepSeek -> deepSeekTTSItems(setting, onValueChange)
            }
        }
    }
}

@Composable
private fun openAITTSItems(
    setting: TTSProviderSetting.OpenAI,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_api_key))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_api_key_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.apiKey,
                onValueChange = { newApiKey ->
                    onValueChange(setting.copy(apiKey = newApiKey))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.setting_tts_page_api_key_placeholder_openai)) },
            )
        }
    }

    // Base URL
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_base_url))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_base_url_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.baseUrl,
                onValueChange = { newBaseUrl ->
                    onValueChange(setting.copy(baseUrl = newBaseUrl))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) }
            )
        }
    }

    // Model
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_model))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_model_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.model,
                onValueChange = { newModel ->
                    onValueChange(setting.copy(model = newModel))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.setting_tts_page_model_placeholder_openai)) }
            )
        }
    }

    // Voice
    val voices = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer")

    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_voice))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_voice_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SelectTextField(
                value = setting.voice,
                options = voices,
                onValueChange = { newVoice ->
                    onValueChange(setting.copy(voice = newVoice))
                },
                onOptionSelected = { voice ->
                    onValueChange(setting.copy(voice = voice))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun deepSeekTTSItems(
    setting: TTSProviderSetting.DeepSeek,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_api_key))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_api_key_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.apiKey,
                onValueChange = { onValueChange(setting.copy(apiKey = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-...") },
                singleLine = true,
            )
        }
    }

    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_base_url))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_base_url_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.baseUrl,
                onValueChange = { onValueChange(setting.copy(baseUrl = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://api.deepseek.com/v1") },
                singleLine = true,
            )
        }
    }

    val voices = listOf("stella", "tide")
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_voice))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_voice_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SelectTextField(
                value = setting.voiceId,
                options = voices,
                onValueChange = { onValueChange(setting.copy(voiceId = it)) },
                onOptionSelected = { onValueChange(setting.copy(voiceId = it)) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
            )
        }
    }

    val formats = listOf("wav", "pcm")
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_format))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_format_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SelectTextField(
                value = setting.format,
                options = formats,
                onValueChange = { onValueChange(setting.copy(format = it)) },
                onOptionSelected = { onValueChange(setting.copy(format = it)) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
            )
        }
    }

    AppSwitchRow(
        checked = setting.filterMarkdown,
        onCheckedChange = { onValueChange(setting.copy(filterMarkdown = it)) },
        headline = stringResource(R.string.setting_tts_page_filter_markdown),
        supporting = stringResource(R.string.setting_tts_page_filter_markdown_desc),
    )
}

@Composable
private fun miMoTTSItems(
    setting: TTSProviderSetting.MiMo,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // MiMo 配置均为自由输入 默认值只是占位
    // API Key
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_api_key))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_api_key_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.apiKey,
                onValueChange = { newApiKey ->
                    onValueChange(setting.copy(apiKey = newApiKey))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("mimo-xxx") },
            )
        }
    }

    // Base URL
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_base_url))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_base_url_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.baseUrl,
                onValueChange = { newBaseUrl ->
                    onValueChange(setting.copy(baseUrl = newBaseUrl))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://api.xiaomimimo.com/v1") }
            )
        }
    }

    // Model
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_model))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_model_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.model,
                onValueChange = { newModel ->
                    onValueChange(setting.copy(model = newModel))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("mimo-v2-tts") }
            )
        }
    }

    // Voice
    val voices = listOf(
        "mimo_default",
        "冰糖",
        "茉莉",
        "苏打",
        "白桦",
        "Mia",
        "Chloe",
        "Milo",
        "Dean"
    )

    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_voice))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_voice_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SelectTextField(
                value = setting.voice,
                options = voices,
                onValueChange = { newVoice ->
                    onValueChange(setting.copy(voice = newVoice))
                },
                onOptionSelected = { voice ->
                    onValueChange(setting.copy(voice = voice))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("mimo_default") }
            )
        }
    }
}

@Composable
private fun miniMaxTTSItems(
    setting: TTSProviderSetting.MiniMax,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_api_key))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_api_key_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.apiKey,
                onValueChange = { newApiKey ->
                    onValueChange(setting.copy(apiKey = newApiKey))
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // Base URL
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_base_url))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_base_url_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.baseUrl,
                onValueChange = { newBaseUrl ->
                    onValueChange(setting.copy(baseUrl = newBaseUrl))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) }
            )
        }
    }

    // Model
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_model))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_model_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.model,
                onValueChange = { newModel ->
                    onValueChange(setting.copy(model = newModel))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("speech-2.8-hd") }
            )
        }
    }

    // Voice ID
    val voiceIds = listOf(
        "male-qn-qingse",
        "male-qn-jingying",
        "male-qn-badao",
        "male-qn-daxuesheng",
        "female-shaonv",
        "female-yujie",
        "female-chengshu",
        "female-tianmei",
        "audiobook_male_1",
        "audiobook_female_1",
        "cartoon_pig"
    )

    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_voice_id))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_voice_id_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SelectTextField(
                value = setting.voiceId,
                options = voiceIds,
                onValueChange = { newVoiceId ->
                    onValueChange(setting.copy(voiceId = newVoiceId))
                },
                onOptionSelected = { voiceId ->
                    onValueChange(setting.copy(voiceId = voiceId))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Speed
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_speed))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_speed_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedNumberInput(
                value = setting.speed,
                onValueChange = { newSpeed ->
                    if (newSpeed in 0.25f..4.0f) {
                        onValueChange(setting.copy(speed = newSpeed))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.setting_tts_page_speed)
            )
        }
    }
}

@Composable
private fun geminiTTSItems(
    setting: TTSProviderSetting.Gemini,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_api_key))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_api_key_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.apiKey,
                onValueChange = { newApiKey ->
                    onValueChange(setting.copy(apiKey = newApiKey))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.setting_tts_page_api_key_placeholder_gemini)) },
            )
        }
    }

    // Base URL
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_base_url))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_base_url_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.baseUrl,
                onValueChange = { newBaseUrl ->
                    onValueChange(setting.copy(baseUrl = newBaseUrl))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) }
            )
        }
    }

    // Model
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_model))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_model_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.model,
                onValueChange = { newModel ->
                    onValueChange(setting.copy(model = newModel))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.setting_tts_page_model_placeholder_gemini)) }
            )
        }
    }

    // Voice Name
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_voice_name))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_voice_name_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.voiceName,
                onValueChange = { newVoiceName ->
                    onValueChange(setting.copy(voiceName = newVoiceName))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.setting_tts_page_voice_name_placeholder)) }
            )
        }
    }
}

@Composable
private fun systemTTSItems(
    setting: TTSProviderSetting.SystemTTS,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // Speech Rate
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_speech_rate))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_speech_rate_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedNumberInput(
                value = setting.speechRate,
                onValueChange = { newRate ->
                    if (newRate in 0.1f..3.0f) {
                        onValueChange(setting.copy(speechRate = newRate))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.setting_tts_page_speech_rate)
            )
        }
    }

    // Pitch
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_pitch))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_pitch_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedNumberInput(
                value = setting.pitch,
                onValueChange = { newPitch ->
                    if (newPitch in 0.1f..2.0f) {
                        onValueChange(setting.copy(pitch = newPitch))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.setting_tts_page_pitch)
            )
        }
    }
}

@Composable
private fun qwenTTSItems(
    setting: TTSProviderSetting.Qwen,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_api_key))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_api_key_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.apiKey,
                onValueChange = { newApiKey ->
                    onValueChange(setting.copy(apiKey = newApiKey))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-xxx") },
            )
        }
    }

    // Base URL
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_base_url))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_base_url_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.baseUrl,
                onValueChange = { newBaseUrl ->
                    onValueChange(setting.copy(baseUrl = newBaseUrl))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) }
            )
        }
    }

    // Model
    val models = listOf(
        "qwen-audio-3.0-tts-flash",
        "qwen-audio-3.0-tts-plus",
    )

    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_model))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_model_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SelectTextField(
                value = setting.model,
                options = models,
                onValueChange = { newModel ->
                    onValueChange(setting.copy(model = newModel))
                },
                onOptionSelected = { model ->
                    val defaultVoice = when (model) {
                        "qwen-audio-3.0-tts-plus" -> "longanlingxin"
                        "qwen-audio-3.0-tts-flash" -> "longanhuan_v3.6"
                        else -> setting.voice
                    }
                    onValueChange(setting.copy(model = model, voice = defaultVoice))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("qwen-audio-3.0-tts-flash") }
            )
        }
    }

    // Voice
    val voices = when (setting.model) {
        "qwen-audio-3.0-tts-plus" -> listOf("longanlingxin", "longanlufeng")
        "qwen-audio-3.0-tts-flash" -> listOf(
            "longanfengyue", "longanyuanfei", "longanlingxi", "longanxiaoxin",
            "longanhuan_v3.6", "longjielidou_v3.6", "longpaopao_v3.6",
            "longhuohuo_v3.6", "longchuanshu_v3.6", "loongmary",
            "loongeva_v3.6", "loongjohn",
        )

        else -> emptyList()
    }

    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_voice))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_voice_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SelectTextField(
                value = setting.voice,
                options = voices,
                onValueChange = { newVoice ->
                    onValueChange(setting.copy(voice = newVoice))
                },
                onOptionSelected = { voice ->
                    onValueChange(setting.copy(voice = voice))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Audio Format
    val formats = listOf("wav", "mp3", "pcm", "opus")

    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_audio_format))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_audio_format_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SelectTextField(
                value = setting.format,
                options = formats,
                readOnly = true,
                onOptionSelected = { format ->
                    onValueChange(setting.copy(format = format))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Sample Rate
    val sampleRates = listOf(8000, 16000, 22050, 24000, 44100, 48000)

    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_sample_rate))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_sample_rate_desc))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SelectTextField(
                value = setting.sampleRate.toString(),
                options = sampleRates,
                readOnly = true,
                onOptionSelected = { sampleRate ->
                    onValueChange(setting.copy(sampleRate = sampleRate))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun groqTTSItems(
    setting: TTSProviderSetting.Groq,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_api_key))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_api_key_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.apiKey,
                onValueChange = { newApiKey ->
                    onValueChange(setting.copy(apiKey = newApiKey))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("gsk_xxx") },
            )
        }
    }

    // Base URL
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_base_url))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_base_url_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.baseUrl,
                onValueChange = { newBaseUrl ->
                    onValueChange(setting.copy(baseUrl = newBaseUrl))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) }
            )
        }
    }

    // Model
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_model))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_model_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.model,
                onValueChange = { newModel ->
                    onValueChange(setting.copy(model = newModel))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("canopylabs/orpheus-v1-english") }
            )
        }
    }

    // Voice
    val voices = listOf("austin", "natalie", "kailin")

    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_voice))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_voice_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SelectTextField(
                value = setting.voice,
                options = voices,
                onValueChange = { newVoice ->
                    onValueChange(setting.copy(voice = newVoice))
                },
                onOptionSelected = { voice ->
                    onValueChange(setting.copy(voice = voice))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun xAITTSItems(
    setting: TTSProviderSetting.XAI,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_api_key))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_api_key_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.apiKey,
                onValueChange = { newApiKey ->
                    onValueChange(setting.copy(apiKey = newApiKey))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("xai-xxx") },
            )
        }
    }

    // Base URL
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_base_url))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_base_url_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.baseUrl,
                onValueChange = { newBaseUrl ->
                    onValueChange(setting.copy(baseUrl = newBaseUrl))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://api.x.ai/v1") }
            )
        }
    }

    // Voice ID
    val voices = listOf(
        "eve" to "Eve",
        "ara" to "Ara",
        "rex" to "Rex",
        "sal" to "Sal",
        "leo" to "Leo"
    )

    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_voice))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_voice_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SelectTextField(
                value = setting.voiceId,
                options = voices,
                onValueChange = { newVoiceId ->
                    onValueChange(setting.copy(voiceId = newVoiceId))
                },
                onOptionSelected = { (voiceId, _) ->
                    onValueChange(setting.copy(voiceId = voiceId))
                },
                optionToString = { (_, description) -> description },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Language
    val languages = listOf(
        "auto" to "Auto-detect",
        "en" to "English",
        "zh" to "Chinese (Simplified)",
        "ja" to "Japanese",
        "ko" to "Korean",
        "fr" to "French",
        "de" to "German",
        "es-ES" to "Spanish (Spain)",
        "es-MX" to "Spanish (Mexico)",
        "pt-BR" to "Portuguese (Brazil)",
        "pt-PT" to "Portuguese (Portugal)",
        "it" to "Italian",
        "ru" to "Russian",
        "ar-EG" to "Arabic (Egypt)",
        "hi" to "Hindi",
        "tr" to "Turkish",
        "vi" to "Vietnamese",
        "id" to "Indonesian",
        "bn" to "Bengali"
    )

    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_language))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SelectTextField(
                value = setting.language,
                options = languages,
                onValueChange = { newLanguage ->
                    onValueChange(setting.copy(language = newLanguage))
                },
                onOptionSelected = { (code, _) ->
                    onValueChange(setting.copy(language = code))
                },
                optionToString = { (code, displayName) -> "$displayName ($code)" },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun elevenLabsTTSItems(
    setting: TTSProviderSetting.ElevenLabs,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_api_key))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_api_key_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.apiKey,
                onValueChange = { newApiKey ->
                    onValueChange(setting.copy(apiKey = newApiKey))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk_...") },
            )
        }
    }

    // Base URL
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_base_url))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_base_url_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.baseUrl,
                onValueChange = { newBaseUrl ->
                    onValueChange(setting.copy(baseUrl = newBaseUrl))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://api.elevenlabs.io") }
            )
        }
    }

    // Model
    val models = listOf(
        "eleven_multilingual_v2" to "Eleven Multilingual v2",
        "eleven_v3" to "Eleven v3",
        "eleven_flash_v2_5" to "Eleven Flash v2.5"
    )

    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_model))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_model_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SelectTextField(
                value = setting.model,
                options = models,
                onValueChange = { newModel ->
                    onValueChange(setting.copy(model = newModel))
                },
                onOptionSelected = { (modelId, _) ->
                    onValueChange(setting.copy(model = modelId))
                },
                optionToString = { (modelId, displayName) -> "$displayName ($modelId)" },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Voice ID
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_voice))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_voice_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.voiceId,
                onValueChange = { newVoiceId ->
                    onValueChange(setting.copy(voiceId = newVoiceId))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("JBFqnCBsd6RMkjVDRZzb") }
            )
        }
    }

    // Stability
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_stability))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_stability_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedNumberInput(
                value = setting.stability,
                onValueChange = { newStability ->
                    onValueChange(setting.copy(stability = newStability.coerceIn(0f, 1f)))
                },
                modifier = Modifier.fillMaxWidth(),
                label = "0.5",
            )
        }
    }

    // Similarity Boost
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_similarity_boost))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_similarity_boost_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedNumberInput(
                value = setting.similarityBoost,
                onValueChange = { newSimilarityBoost ->
                    onValueChange(setting.copy(similarityBoost = newSimilarityBoost.coerceIn(0f, 1f)))
                },
                modifier = Modifier.fillMaxWidth(),
                label = "0.75",
            )
        }
    }
}

@Composable
private fun fishAudioTTSItems(
    setting: TTSProviderSetting.FishAudio,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_api_key))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_api_key_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.apiKey,
                onValueChange = { newApiKey ->
                    onValueChange(setting.copy(apiKey = newApiKey))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://fish.audio/app/api-keys") },
            )
        }
    }

    // Base URL
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_base_url))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_base_url_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.baseUrl,
                onValueChange = { newBaseUrl ->
                    onValueChange(setting.copy(baseUrl = newBaseUrl))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://api.fish.audio") }
            )
        }
    }

    // Model (下拉选择框 + 文本输入框，完全同 ElevenLabs 格式)
    val models = listOf(
        "s2.1-pro" to R.string.setting_tts_page_fish_model_s2_1_pro,
        "s2.1-pro-free" to R.string.setting_tts_page_fish_model_s2_1_pro_free,
        "s2-pro" to R.string.setting_tts_page_fish_model_s2_pro,
        "s1" to R.string.setting_tts_page_fish_model_s1
    )

    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_model))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_model_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SelectTextField(
                value = setting.model,
                options = models,
                onValueChange = { newModel ->
                    onValueChange(setting.copy(model = newModel))
                },
                onOptionSelected = { (modelId, _) ->
                    onValueChange(setting.copy(model = modelId))
                },
                optionToString = { (modelId, nameRes) -> stringResource(nameRes) + " ($modelId)" },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Voice ID (reference_id)
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_voice_id))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_voice_id_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.referenceId,
                onValueChange = { newReferenceId ->
                    onValueChange(setting.copy(referenceId = newReferenceId))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("802e3bc2b27e49c2995d23ef70e6ac89") }
            )
        }
    }

    // Temperature
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_temperature))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_temperature_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedNumberInput(
                value = setting.temperature,
                onValueChange = { newTemperature ->
                    onValueChange(setting.copy(temperature = newTemperature.coerceIn(0f, 1f)))
                },
                modifier = Modifier.fillMaxWidth(),
                label = "0.7",
            )
        }
    }

    // Speed
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_speed))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_fish_audio_speed_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedNumberInput(
                value = setting.speed,
                onValueChange = { newSpeed ->
                    onValueChange(setting.copy(speed = newSpeed.coerceIn(0.5f, 2f)))
                },
                modifier = Modifier.fillMaxWidth(),
                label = "1.0",
            )
        }
    }
}

@Composable
private fun stepTTSItems(
    setting: TTSProviderSetting.Step,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_api_key))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_step_api_key_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.apiKey,
                onValueChange = { newApiKey ->
                    onValueChange(setting.copy(apiKey = newApiKey))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.setting_tts_page_step_api_key_placeholder)) },
            )
        }
    }

    // Base URL
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_base_url))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_base_url_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.baseUrl,
                onValueChange = { newBaseUrl ->
                    onValueChange(setting.copy(baseUrl = newBaseUrl))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://api.stepfun.com") }
            )
        }
    }

    // Model
    val models = listOf(
        "step-tts-mini" to R.string.setting_tts_page_step_model_step_tts_mini,
        "step-tts-vivid" to R.string.setting_tts_page_step_model_step_tts_vivid,
        "stepaudio-2.5-tts" to R.string.setting_tts_page_step_model_stepaudio_2_5_tts,
        "step-tts-2" to R.string.setting_tts_page_step_model_step_tts_2,
    )

    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_model))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_model_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SelectTextField(
                value = setting.model,
                options = models,
                onValueChange = { newModel ->
                    onValueChange(setting.copy(model = newModel))
                },
                onOptionSelected = { (modelId, _) ->
                    onValueChange(setting.copy(model = modelId))
                },
                optionToString = { (_, nameRes) -> stringResource(nameRes) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Voice
    // 部分常用 voice-id, 完整列表见官方开发指南
    // https://platform.stepfun.com/docs/zh/guides/developer/tts
    val voices = listOf(
        "elegantgentle-female" to R.string.setting_tts_page_step_voice_elegantgentle_female,
        "livelybreezy-female" to R.string.setting_tts_page_step_voice_livelybreezy_female,
        "energeticconfident-female" to R.string.setting_tts_page_step_voice_energeticconfident_female,
        "jingdiannvsheng" to R.string.setting_tts_page_step_voice_jingdiannvsheng,
        "wenroushunv" to R.string.setting_tts_page_step_voice_wenroushunv,
        "tianmeinvsheng" to R.string.setting_tts_page_step_voice_tianmeinvsheng,
        "qingchunshaonv" to R.string.setting_tts_page_step_voice_qingchunshaonv,
        "wenrounvsheng" to R.string.setting_tts_page_step_voice_wenrounvsheng,
        "ruanmengnvsheng" to R.string.setting_tts_page_step_voice_ruanmengnvsheng,
        "youyanvsheng" to R.string.setting_tts_page_step_voice_youyanvsheng,
        "lengyanyujie" to R.string.setting_tts_page_step_voice_lengyanyujie,
        "shuangkuaijiejie" to R.string.setting_tts_page_step_voice_shuangkuaijiejie,
        "wenjingxuejie" to R.string.setting_tts_page_step_voice_wenjingxuejie,
        "linjiajiejie" to R.string.setting_tts_page_step_voice_linjiajiejie,
        "linjiameimei" to R.string.setting_tts_page_step_voice_linjiameimei,
        "zhixingjiejie" to R.string.setting_tts_page_step_voice_zhixingjiejie,
        "cixingnansheng" to R.string.setting_tts_page_step_voice_cixingnansheng,
        "wenrounansheng" to R.string.setting_tts_page_step_voice_wenrounansheng,
        "yuanqinansheng" to R.string.setting_tts_page_step_voice_yuanqinansheng,
        "zhengpaiqingnian" to R.string.setting_tts_page_step_voice_zhengpaiqingnian,
        "ruyananshi" to R.string.setting_tts_page_step_voice_ruyananshi,
        "boyinnansheng" to R.string.setting_tts_page_step_voice_boyinnansheng,
        "shenchennanyin" to R.string.setting_tts_page_step_voice_shenchennanyin,
        "shuangkuainansheng" to R.string.setting_tts_page_step_voice_shuangkuainansheng,
        "ganliannvsheng" to R.string.setting_tts_page_step_voice_ganliannvsheng,
        "qinhenvsheng" to R.string.setting_tts_page_step_voice_qinhenvsheng,
        "huolinvsheng" to R.string.setting_tts_page_step_voice_huolinvsheng,
        "jilingshaonv" to R.string.setting_tts_page_step_voice_jilingshaonv,
        "yuanqishaonv" to R.string.setting_tts_page_step_voice_yuanqishaonv,
        "wenrougongzi" to R.string.setting_tts_page_step_voice_wenrougongzi,
        "qingniandaxuesheng" to R.string.setting_tts_page_step_voice_qingniandaxuesheng,
    )

    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_voice))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_voice_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SelectTextField(
                value = setting.voice,
                options = voices,
                onValueChange = { newVoice ->
                    onValueChange(setting.copy(voice = newVoice))
                },
                onOptionSelected = { (voiceId, _) ->
                    onValueChange(setting.copy(voice = voiceId))
                },
                optionToString = { (_, nameRes) -> stringResource(nameRes) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Response Format
    val formats = listOf("mp3", "wav", "pcm", "opus", "flac")

    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_response_format))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_step_response_format_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SelectTextField(
                value = setting.responseFormat,
                options = formats,
                onValueChange = { newFormat ->
                    onValueChange(setting.copy(responseFormat = newFormat))
                },
                onOptionSelected = { format ->
                    onValueChange(setting.copy(responseFormat = format))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Speed
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_speed))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_fish_audio_speed_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedNumberInput(
                value = setting.speed,
                onValueChange = { newSpeed ->
                    if (newSpeed in 0.5f..2.0f) {
                        onValueChange(setting.copy(speed = newSpeed))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.setting_tts_page_speed)
            )
        }
    }

    // Volume
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_volume))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_step_volume_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedNumberInput(
                value = setting.volume,
                onValueChange = { newVolume ->
                    if (newVolume in 0.1f..2.0f) {
                        onValueChange(setting.copy(volume = newVolume))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = "Volume"
            )
        }
    }

    // Sample Rate
    val sampleRates = listOf(8000, 16000, 22050, 24000)

    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_sample_rate))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_step_sample_rate_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SelectTextField(
                value = setting.sampleRate.toString(),
                options = sampleRates,
                readOnly = true,
                onOptionSelected = { rate ->
                    onValueChange(setting.copy(sampleRate = rate))
                },
                optionToString = { rate -> "$rate Hz" },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Instruction (仅 stepaudio-2.5-tts 生效)
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_instruction))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_step_instruction_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.instruction,
                onValueChange = { newInstruction ->
                    // 服务端上限 200 字符, 客户端做一层保护
                    if (newInstruction.length <= 200) {
                        onValueChange(setting.copy(instruction = newInstruction))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.setting_tts_page_step_instruction_placeholder)) },
                minLines = 2,
                maxLines = 4,
            )
        }
    }
}

@Composable
private fun volcengineTTSItems(
    setting: TTSProviderSetting.Volcengine,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_api_key))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_volcengine_api_key_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            var keyVisible by remember(setting.id) { mutableStateOf(false) }
            OutlinedTextField(
                value = setting.apiKey,
                onValueChange = { onValueChange(setting.copy(apiKey = it)) },
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            imageVector = if (keyVisible) HugeIcons.ViewOff else HugeIcons.View,
                            contentDescription = if (keyVisible) stringResource(R.string.setting_tts_page_hide_api_key) else stringResource(R.string.setting_tts_page_show_api_key),
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_base_url))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.baseUrl,
                onValueChange = { onValueChange(setting.copy(baseUrl = it)) },
                placeholder = { Text("https://openspeech.bytedance.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_asr_configure_resource_id))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_volcengine_resource_id_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.resourceId,
                onValueChange = { onValueChange(setting.copy(resourceId = it)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_voice_id))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_volcengine_voice_id_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = setting.speaker,
                onValueChange = { onValueChange(setting.copy(speaker = it)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setting_tts_page_speed))
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = 0.5f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            Text(stringResource(R.string.setting_tts_page_volcengine_speed_description))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedNumberInput(
                value = setting.speechRate,
                onValueChange = { onValueChange(setting.copy(speechRate = it.coerceIn(-50, 100))) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
