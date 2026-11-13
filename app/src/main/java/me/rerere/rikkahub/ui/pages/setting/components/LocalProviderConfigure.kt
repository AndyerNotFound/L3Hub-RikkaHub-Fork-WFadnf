package me.rerere.rikkahub.ui.pages.setting.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.LiteRTBackend
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.terminal.TextButton
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.ui.toast.ToastType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
internal fun ProviderConfigureLiteRT(
    provider: ProviderSetting.LiteRT,
    onEdit: (ProviderSetting) -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            progress = 0f
            runCatching {
                withContext(Dispatchers.IO) {
                    importLiteRTModel(context, uri) { progress = it }
                }
            }.onSuccess { file ->
                onEdit(provider.withImportedModel(file))
                toaster.show(
                    context.getString(R.string.setting_provider_litert_imported, file.name),
                    type = ToastType.Success,
                )
            }.onFailure { error ->
                toaster.show(
                    error.message ?: context.getString(R.string.setting_provider_litert_import_failed),
                    type = ToastType.Error,
                )
            }
            importing = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CardGroup {
            nameItem(provider, onEdit)
            item(
                onClick = if (importing) null else ({ picker.launch(arrayOf("*/*")) }),
                headlineContent = { Text(stringResource(R.string.setting_provider_litert_model_file)) },
                supportingContent = {
                    Text(
                        provider.modelPath
                            .takeIf { it.isNotBlank() }
                            ?.let { File(it).name }
                            ?: stringResource(R.string.setting_provider_litert_model_file_hint)
                    )
                },
                trailingContent = {
                    Icon(HugeIcons.FileImport, contentDescription = null)
                },
            )
            if (importing) {
                formItem {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(
                            R.string.setting_provider_litert_importing,
                            (progress * 100).roundToInt(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        CardGroup(title = { Text(stringResource(R.string.setting_provider_litert_runtime)) }) {
            formItem(headlineContent = { Text(stringResource(R.string.setting_provider_litert_backend)) }) {
                ConnectedChoiceButtons(
                    options = LiteRTBackend.entries,
                    selected = provider.backend,
                    onSelect = { onEdit(provider.copy(backend = it)) },
                ) { option, selected ->
                    ConnectedChoiceLabel(
                        text = when (option) {
                            LiteRTBackend.CPU -> stringResource(R.string.setting_provider_litert_backend_cpu)
                            LiteRTBackend.GPU -> stringResource(R.string.setting_provider_litert_backend_gpu)
                        },
                        selected = selected,
                    )
                }
            }
            if (provider.backend == LiteRTBackend.CPU) {
                numberItem(
                    label = { Text(stringResource(R.string.setting_provider_litert_cpu_threads)) },
                    value = provider.cpuThreads,
                    range = 0..64,
                    supporting = { Text(stringResource(R.string.setting_provider_litert_cpu_threads_desc)) },
                ) { onEdit(provider.copy(cpuThreads = it)) }
            }
            numberItem(
                label = { Text(stringResource(R.string.setting_provider_litert_context)) },
                value = provider.contextTokens,
                range = 512..262144,
                supporting = { Text(stringResource(R.string.setting_provider_litert_context_desc)) },
            ) { tokens ->
                onEdit(
                    provider.copy(
                        contextTokens = tokens,
                        models = provider.models.map { it.copy(contextLength = tokens) },
                    )
                )
            }
            numberItem(
                label = { Text(stringResource(R.string.setting_provider_litert_max_output)) },
                value = provider.maxOutputTokens,
                range = 1..131072,
            ) { onEdit(provider.copy(maxOutputTokens = it)) }
            switchItem(
                checked = provider.visionEnabled,
                onCheckedChange = { enabled ->
                    onEdit(
                        provider.copy(
                            visionEnabled = enabled,
                            models = provider.models.map { model ->
                                model.copy(
                                    inputModalities = if (enabled) {
                                        listOf(Modality.TEXT, Modality.IMAGE)
                                    } else {
                                        listOf(Modality.TEXT)
                                    }
                                )
                            },
                        )
                    )
                },
                headlineContent = { Text(stringResource(R.string.setting_provider_litert_vision)) },
                supportingContent = { Text(stringResource(R.string.setting_provider_litert_vision_desc)) },
            )
            switchItem(
                checked = provider.enableThinking,
                onCheckedChange = { onEdit(provider.copy(enableThinking = it)) },
                headlineContent = { Text(stringResource(R.string.setting_provider_litert_thinking)) },
                supportingContent = { Text(stringResource(R.string.setting_provider_litert_thinking_desc)) },
            )
            if (provider.enableThinking) {
                numberItem(
                    label = { Text(stringResource(R.string.setting_provider_litert_thinking_budget)) },
                    value = provider.thinkingBudget,
                    range = 0..65536,
                ) { onEdit(provider.copy(thinkingBudget = it)) }
            }
        }

        CardGroup(title = { Text(stringResource(R.string.setting_provider_litert_sampling)) }) {
            numberItem(
                label = { Text(stringResource(R.string.setting_provider_litert_temperature)) },
                value = provider.temperature,
                range = 0f..2f,
            ) { onEdit(provider.copy(temperature = it)) }
            numberItem(
                label = { Text(stringResource(R.string.setting_provider_litert_top_k)) },
                value = provider.topK,
                range = 1..200,
            ) { onEdit(provider.copy(topK = it)) }
            numberItem(
                label = { Text(stringResource(R.string.setting_provider_litert_top_p)) },
                value = provider.topP,
                range = 0f..1f,
            ) { onEdit(provider.copy(topP = it)) }
        }
    }
}

@Composable
internal fun ProviderConfigureLocalServer(
    provider: ProviderSetting.LocalServer,
    onEdit: (ProviderSetting) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val command = provider.launchCommand.ifBlank { provider.commandExample() }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CardGroup {
            nameItem(provider, onEdit)
            textItem(
                label = { Text(stringResource(R.string.setting_provider_local_endpoint)) },
                value = provider.baseUrl,
                isError = provider.baseUrl.isNotBlank() && provider.baseUrl.toHttpUrlOrNull() == null,
                supporting = { Text(stringResource(R.string.setting_provider_local_endpoint_desc)) },
                onValueChange = { value ->
                    onEdit(provider.copyLocal(baseUrl = value.trim()))
                },
            )
            textItem(
                label = { Text(stringResource(R.string.setting_provider_local_runtime_path)) },
                value = provider.runtimePath,
                supporting = { Text(stringResource(R.string.setting_provider_local_runtime_path_desc)) },
                onValueChange = { value ->
                    onEdit(provider.copyLocal(runtimePath = value.trim()))
                },
            )
            textItem(
                label = { Text(stringResource(R.string.setting_provider_local_model_path)) },
                value = provider.modelPath,
                supporting = { Text(stringResource(R.string.setting_provider_local_model_path_desc)) },
                onValueChange = { value ->
                    onEdit(provider.copyLocal(modelPath = value.trim()))
                },
            )
            textItem(
                label = { Text(stringResource(R.string.setting_provider_local_api_key)) },
                value = provider.apiKey,
                supporting = { Text(stringResource(R.string.setting_provider_local_api_key_desc)) },
                onValueChange = { value ->
                    onEdit(provider.copyLocal(apiKey = value.trim()))
                },
            )
            numberItem(
                label = { Text(stringResource(R.string.setting_provider_local_context)) },
                value = provider.contextTokens,
                range = 512..262144,
                supporting = { Text(stringResource(R.string.setting_provider_local_context_desc)) },
            ) { tokens ->
                onEdit(
                    provider.copyLocal(
                        contextTokens = tokens,
                        models = provider.models.map { it.copy(contextLength = tokens) },
                    )
                )
            }
            when (provider) {
                is ProviderSetting.GGUF -> {
                    numberItem(
                        label = { Text(stringResource(R.string.setting_provider_local_gpu_layers)) },
                        value = provider.gpuLayers,
                        range = 0..1024,
                        supporting = { Text(stringResource(R.string.setting_provider_local_gpu_layers_desc)) },
                    ) { onEdit(provider.copy(gpuLayers = it)) }
                    numberItem(
                        label = { Text(stringResource(R.string.setting_provider_local_threads)) },
                        value = provider.threads,
                        range = 1..64,
                    ) { onEdit(provider.copy(threads = it)) }
                }

                is ProviderSetting.ONNX -> {
                    textItem(
                        label = { Text(stringResource(R.string.setting_provider_local_execution_provider)) },
                        value = provider.executionProvider,
                        supporting = { Text(stringResource(R.string.setting_provider_local_execution_provider_desc)) },
                        onValueChange = { onEdit(provider.copy(executionProvider = it.trim())) },
                    )
                    numberItem(
                        label = { Text(stringResource(R.string.setting_provider_local_threads)) },
                        value = provider.threads,
                        range = 1..64,
                    ) { onEdit(provider.copy(threads = it)) }
                }
            }
        }

        CardGroup(title = { Text(stringResource(R.string.setting_provider_local_runtime)) }) {
            item(
                headlineContent = { Text(provider.runtimeName()) },
                supportingContent = { Text(provider.runtimeDescription()) },
            )
            formItem(
                headlineContent = { Text(stringResource(R.string.setting_provider_local_command)) },
                supportingContent = { Text(stringResource(R.string.setting_provider_local_command_desc)) },
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = command,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetbrainsMono),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { clipboard.setText(AnnotatedString(command)) },
                    ) {
                        Text(stringResource(R.string.setting_provider_local_copy_command))
                    }
                    if (provider.launchCommand.isNotBlank()) {
                        TextButton(
                            onClick = { onEdit(provider.copyLocal(launchCommand = "")) },
                        ) {
                            Text(stringResource(R.string.setting_provider_local_reset_command))
                        }
                    }
                }
            }
            textItem(
                label = { Text(stringResource(R.string.setting_provider_local_command_memo)) },
                value = provider.launchCommand,
                supporting = { Text(stringResource(R.string.setting_provider_local_command_memo_desc)) },
                onValueChange = { onEdit(provider.copyLocal(launchCommand = it)) },
            )
        }
    }
}

private fun ProviderSetting.LocalServer.runtimeName(): String = when (this) {
    is ProviderSetting.GGUF -> "llama.cpp"
    is ProviderSetting.ONNX -> "ONNX Runtime"
}

@Composable
private fun ProviderSetting.LocalServer.runtimeDescription(): String = when (this) {
    is ProviderSetting.GGUF -> stringResource(R.string.setting_provider_local_gguf_runtime_desc)
    is ProviderSetting.ONNX -> stringResource(R.string.setting_provider_onnx_runtime_desc)
}

private fun ProviderSetting.LocalServer.commandExample(): String = when (this) {
    is ProviderSetting.GGUF -> buildString {
        append(runtimePath.ifBlank { "llama-server" })
        append(" -m ")
        append(modelPath.ifBlank { "/path/to/model.gguf" })
        append(" -c ")
        append(contextTokens)
        append(" --host 127.0.0.1 --port ")
        append(baseUrl.portOrDefault(8080))
        if (gpuLayers > 0) append(" -ngl ").append(gpuLayers)
        if (threads > 0) append(" -t ").append(threads)
    }

    is ProviderSetting.ONNX -> buildString {
        append(runtimePath.ifBlank { "<onnxruntime-genai 服务>" })
        append(" --model ")
        append(modelPath.ifBlank { "/path/to/model_dir" })
        append(" --provider ")
        append(executionProvider.ifBlank { "cpu" })
        append(" --host 127.0.0.1 --port ")
        append(baseUrl.portOrDefault(8001))
    }
}

private fun ProviderSetting.LocalServer.copyLocal(
    baseUrl: String = this.baseUrl,
    runtimePath: String = this.runtimePath,
    modelPath: String = this.modelPath,
    apiKey: String = this.apiKey,
    contextTokens: Int = this.contextTokens,
    models: List<Model> = this.models,
    launchCommand: String = this.launchCommand,
): ProviderSetting = when (this) {
    is ProviderSetting.GGUF -> copy(
        baseUrl = baseUrl,
        runtimePath = runtimePath,
        modelPath = modelPath,
        apiKey = apiKey,
        contextTokens = contextTokens,
        models = models,
        launchCommand = launchCommand,
    )

    is ProviderSetting.ONNX -> copy(
        baseUrl = baseUrl,
        runtimePath = runtimePath,
        modelPath = modelPath,
        apiKey = apiKey,
        contextTokens = contextTokens,
        models = models,
        launchCommand = launchCommand,
    )
}

private fun String.portOrDefault(default: Int): Int =
    runCatching { java.net.URI(this).port }.getOrNull()?.takeIf { it > 0 } ?: default

private fun ProviderSetting.LiteRT.withImportedModel(file: File): ProviderSetting.LiteRT {
    val existing = models.firstOrNull()
    val model = (existing ?: Model()).copy(
        modelId = file.name,
        displayName = file.nameWithoutExtension,
        contextLength = contextTokens,
        inputModalities = if (visionEnabled) {
            listOf(Modality.TEXT, Modality.IMAGE)
        } else {
            listOf(Modality.TEXT)
        },
    )
    return copy(
        name = if (name == "LiteRT") file.nameWithoutExtension else name,
        modelPath = file.absolutePath,
        models = listOf(model),
    )
}

private fun importLiteRTModel(
    context: Context,
    uri: Uri,
    onProgress: (Float) -> Unit,
): File {
    val resolver = context.contentResolver
    val displayName = resolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: "model.litertlm"
    val size = resolver
        .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }

    val targetDir = File(context.filesDir, "litert-models").apply { mkdirs() }
    val safeName = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "model.litertlm" }
    var target = File(targetDir, safeName)
    if (target.exists()) {
        target = File(
            targetDir,
            "${target.nameWithoutExtension}-${System.currentTimeMillis()}.${target.extension}",
        )
    }

    try {
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(1 shl 20)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    if (size != null && size > 0) {
                        onProgress((copied.toFloat() / size).coerceIn(0f, 1f))
                    }
                }
            }
        } ?: error("无法读取所选文件")
        if (target.length() == 0L) error("所选文件为空")
    } catch (error: Throwable) {
        target.delete()
        throw error
    }
    return target
}

private fun CardGroupScope.textItem(
    label: @Composable () -> Unit,
    value: String,
    supporting: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    onValueChange: (String) -> Unit,
) = formItem(
    headlineContent = label,
    supportingContent = supporting,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        isError = isError,
        minLines = 1,
        maxLines = 3,
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

private fun CardGroupScope.numberItem(
    label: @Composable () -> Unit,
    value: Int,
    range: IntRange,
    supporting: (@Composable () -> Unit)? = null,
    onValueChange: (Int) -> Unit,
) = formItem(
    headlineContent = label,
    supportingContent = supporting,
) {
    NumberField(value = value, range = range, onValueChange = onValueChange)
}

private fun CardGroupScope.numberItem(
    label: @Composable () -> Unit,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    supporting: (@Composable () -> Unit)? = null,
    onValueChange: (Float) -> Unit,
) = formItem(
    headlineContent = label,
    supportingContent = supporting,
) {
    NumberField(value = value, range = range, onValueChange = onValueChange)
}

@Composable
private fun NumberField(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter { it.isDigit() }.take(9)
            text = digits
            digits.toIntOrNull()?.takeIf { it in range }?.let(onValueChange)
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun NumberField(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    var text by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val filtered = input.filter { it.isDigit() || it == '.' }.take(8)
            text = filtered
            filtered.toFloatOrNull()?.takeIf { it in range }?.let(onValueChange)
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}
