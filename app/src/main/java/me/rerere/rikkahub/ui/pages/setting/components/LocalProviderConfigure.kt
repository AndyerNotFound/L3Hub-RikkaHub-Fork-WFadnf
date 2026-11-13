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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
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
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.local.LiteRTModelStorage
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.terminal.IconButton
import me.rerere.rikkahub.ui.terminal.RadioButton
import me.rerere.rikkahub.ui.terminal.TextButton
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.ui.toast.ToastType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
internal fun ProviderConfigureLiteRT(
    provider: ProviderSetting.LiteRT,
    onEdit: (ProviderSetting) -> Unit,
    onCommit: (ProviderSetting) -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val storage = remember(context) { LiteRTModelStorage.of(context) }
    var models by remember { mutableStateOf(emptyList<LiteRTModelStorage.Model>()) }
    var importing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var pendingDelete by remember { mutableStateOf<LiteRTModelStorage.Model?>(null) }

    // 列表就是模型目录的内容，进出页面都重新读一次，别让已删掉的文件留在界面上
    LaunchedEffect(storage) {
        models = withContext(Dispatchers.IO) { storage.list() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || importing) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            progress = 0f
            runCatching {
                withContext(Dispatchers.IO) {
                    val meta = context.readModelFileMeta(uri)
                    context.contentResolver.openInputStream(uri)?.use { source ->
                        storage.importModel(
                            source = source,
                            displayName = meta.name,
                            contentSize = meta.size,
                            onProgress = { progress = it },
                        )
                    } ?: error(context.getString(R.string.setting_provider_litert_import_failed))
                }
            }.onSuccess { result ->
                val model = when (result) {
                    is LiteRTModelStorage.ImportResult.Imported -> result.model
                    is LiteRTModelStorage.ImportResult.Duplicate -> result.model
                }
                models = withContext(Dispatchers.IO) { storage.list() }
                onCommit(provider.withSelectedModel(model))
                toaster.show(
                    context.getString(
                        if (result is LiteRTModelStorage.ImportResult.Duplicate) {
                            R.string.setting_provider_litert_duplicated
                        } else {
                            R.string.setting_provider_litert_imported
                        },
                        model.displayName,
                    ),
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

    pendingDelete?.let { model ->
        RikkaConfirmDialog(
            show = true,
            title = stringResource(R.string.setting_provider_litert_delete_title),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                pendingDelete = null
                scope.launch {
                    val deleted = withContext(Dispatchers.IO) { storage.delete(model) }
                    if (deleted) {
                        models = withContext(Dispatchers.IO) { storage.list() }
                        if (provider.isSelected(model)) {
                            onCommit(provider.withoutSelectedModel())
                        }
                        toaster.show(
                            context.getString(
                                R.string.setting_provider_litert_deleted,
                                model.displayName,
                            ),
                            type = ToastType.Success,
                        )
                    } else {
                        toaster.show(
                            context.getString(R.string.setting_provider_litert_delete_failed),
                            type = ToastType.Error,
                        )
                    }
                }
            },
            onDismiss = { pendingDelete = null },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.setting_provider_litert_delete_desc, model.displayName))
                if (provider.isSelected(model)) {
                    Text(stringResource(R.string.setting_provider_litert_delete_selected_desc))
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CardGroup {
            nameItem(provider, onEdit)
        }

        CardGroup(
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.setting_provider_litert_models))
                    if (models.isNotEmpty()) {
                        Text(
                            stringResource(
                                R.string.setting_provider_litert_models_usage,
                                models.size,
                                models.sumOf { it.sizeBytes }.toReadableSize(),
                            )
                        )
                    }
                }
            },
        ) {
            item(
                onClick = if (importing) null else ({ picker.launch(arrayOf("*/*")) }),
                leadingContent = { CardGroupIcon(HugeIcons.FileImport) },
                headlineContent = { Text(stringResource(R.string.setting_provider_litert_import)) },
                supportingContent = { Text(stringResource(R.string.setting_provider_litert_import_desc)) },
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
            if (models.isEmpty() && !importing) {
                item(
                    headlineContent = { Text(stringResource(R.string.setting_provider_litert_empty)) },
                )
            }
            models.forEach { model ->
                val selected = provider.isSelected(model)
                item(
                    onClick = if (selected) null else ({ onCommit(provider.withSelectedModel(model)) }),
                    leadingContent = { RadioButton(selected = selected, onClick = null) },
                    headlineContent = {
                        Text(
                            text = model.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                model.sizeBytes.toReadableSize(),
                                stringResource(R.string.setting_provider_litert_model_in_use)
                                    .takeIf { selected },
                            ).joinToString(" · ")
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { pendingDelete = model }) {
                            Icon(
                                imageVector = HugeIcons.Delete01,
                                contentDescription = stringResource(R.string.delete),
                            )
                        }
                    },
                )
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

private fun ProviderSetting.LiteRT.isSelected(model: LiteRTModelStorage.Model): Boolean =
    modelPath.isNotBlank() && (modelPath == model.path || File(modelPath).name == model.file.name)

private fun ProviderSetting.LiteRT.withSelectedModel(
    model: LiteRTModelStorage.Model,
): ProviderSetting.LiteRT {
    val previous = models.firstOrNull()
    val entry = (previous ?: Model()).copy(
        modelId = model.file.name,
        displayName = model.displayName,
        contextLength = contextTokens,
        inputModalities = if (visionEnabled) {
            listOf(Modality.TEXT, Modality.IMAGE)
        } else {
            listOf(Modality.TEXT)
        },
    )
    return copy(
        // 名字还是默认值或上一个模型名时跟着换，用户自己改过就不动
        name = if (name == ProviderSetting.LiteRT().name || name == previous?.displayName) {
            model.displayName
        } else {
            name
        },
        modelPath = model.path,
        models = listOf(entry),
    )
}

private fun ProviderSetting.LiteRT.withoutSelectedModel(): ProviderSetting.LiteRT = copy(
    name = if (name == models.firstOrNull()?.displayName) ProviderSetting.LiteRT().name else name,
    modelPath = "",
    models = emptyList(),
)

private data class PickedModelFile(val name: String?, val size: Long?)

private fun Context.readModelFileMeta(uri: Uri): PickedModelFile {
    val resolver = contentResolver
    val name = resolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    val size = resolver
        .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null }
    return PickedModelFile(name, size)
}

private fun Long.toReadableSize(): String = when {
    this >= (1L shl 30) -> "%.1f GB".format(toDouble() / (1L shl 30))
    this >= (1L shl 20) -> "%.0f MB".format(toDouble() / (1L shl 20))
    this >= (1L shl 10) -> "%.0f KB".format(toDouble() / (1L shl 10))
    else -> "$this B"
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
