package me.rerere.rikkahub.ui.pages.assistant.detail

import android.text.format.DateUtils
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import me.rerere.rikkahub.ui.components.ui.AppSettingsCard
import me.rerere.rikkahub.ui.components.ui.AppSettingsGroup
import me.rerere.rikkahub.ui.components.ui.AppSettingsRow
import me.rerere.rikkahub.ui.components.ui.AppSwitchRow
import me.rerere.rikkahub.ui.style.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.ui.components.ui.AppSlider
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.MemoryConsolidationStatus
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AUTO_COMPRESS_RATIO_RANGE
import me.rerere.rikkahub.data.model.AutoCompressThreshold
import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.rikkahub.data.model.groupByDirectory
import me.rerere.rikkahub.data.model.memoryTitleOf
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.modelSettingItem
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.components.ui.PagerTab
import me.rerere.rikkahub.ui.components.ui.PagerTabs
import me.rerere.rikkahub.ui.components.ui.shadow.FilledTonalButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.pages.memory.resultText
import me.rerere.rikkahub.ui.style.IconButton
import me.rerere.rikkahub.ui.style.RadioButton
import me.rerere.rikkahub.ui.style.TextButton
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.ui.components.AnimatedAlertDialog
import me.rerere.ui.components.RikkaConfirmDialog
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// 记忆文件放第一页：从侧边栏的整理入口进来时能直接看到整理状态
private enum class MemoryTab(
    override val icon: ImageVector,
    @param:StringRes private val labelRes: Int,
) : PagerTab {
    FILES(HugeIcons.Brain02, R.string.assistant_page_manage_memory_title),
    SETTINGS(HugeIcons.Settings03, R.string.settings);

    override val label: String
        @Composable get() = stringResource(labelRes)
}

@Composable
fun AssistantMemoryPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val consolidation by vm.memoryConsolidation.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val pagerState = rememberPagerState { MemoryTab.entries.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val memoryRepository: MemoryRepository = koinInject()

    // 导出整个记忆库为 JSON；导入时按 path 覆盖同名文件、其余新增
    val exportMemory = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val files = memoryRepository.getFiles(vm.memoryId)
                val json = buildJsonObject {
                    put("version", 1)
                    put("memoryId", vm.memoryId)
                    putJsonArray("files") {
                        files.forEach { file ->
                            addJsonObject {
                                put("path", file.path)
                                put("content", file.content)
                            }
                        }
                    }
                }
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(json.toString().toByteArray())
                } ?: error("cannot open output stream")
            }.onSuccess {
                Toast.makeText(
                    context,
                    context.getString(R.string.memory_export_done),
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure {
                Toast.makeText(
                    context,
                    context.getString(R.string.memory_export_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val importMemory = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("cannot open input stream")
                val files = Json.parseToJsonElement(text).jsonObject["files"]?.jsonArray
                    ?: error("missing files array")
                files.forEach { element ->
                    val obj = element.jsonObject
                    val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    memoryRepository.writeFile(vm.memoryId, path, content)
                }
            }.onSuccess {
                Toast.makeText(
                    context,
                    context.getString(R.string.memory_import_done),
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure {
                Toast.makeText(
                    context,
                    context.getString(R.string.memory_import_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_memory))
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    if (MemoryTab.entries[pagerState.currentPage] == MemoryTab.FILES) {
                        IconButton(onClick = {
                            exportMemory.launch(
                                "memory-${assistant.name.ifBlank { "assistant" }}-${System.currentTimeMillis()}.json"
                            )
                        }) {
                            Icon(HugeIcons.ArrowDown01, contentDescription = stringResource(R.string.memory_export))
                        }
                        IconButton(onClick = { importMemory.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                            Icon(HugeIcons.ArrowUp01, contentDescription = stringResource(R.string.memory_import))
                        }
                        IconButton(onClick = { navController.navigate(Screen.MemoryFile(vm.memoryId, null)) }) {
                            Icon(HugeIcons.Add01, contentDescription = stringResource(R.string.add))
                        }
                    }
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
        ) {
            PagerTabs(
                pagerState = pagerState,
                tabs = MemoryTab.entries,
                modifier = Modifier.padding(bottom = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (MemoryTab.entries[page]) {
                    MemoryTab.FILES -> MemoryFilesTab(
                        memoryId = vm.memoryId,
                        memories = memories,
                        consolidation = consolidation.takeIf { assistant.enableMemory },
                        onOrganizeMemory = { vm.organizeMemory() },
                        onOpenMemory = { path -> navController.navigate(Screen.MemoryFile(vm.memoryId, path)) },
                        onDeleteMemory = { vm.deleteMemory(it) },
                        onOpenSettings = {
                            scope.launch { pagerState.animateScrollToPage(MemoryTab.SETTINGS.ordinal) }
                        },
                    )

                    MemoryTab.SETTINGS -> MemorySettingsTab(
                        assistant = assistant,
                        onUpdateAssistant = { vm.update(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MemorySettingsTab(
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
) {
    var showTimeReminderIntervalDialog by remember(assistant.id) { mutableStateOf(false) }
    var showAutoCompressThresholdDialog by remember(assistant.id) { mutableStateOf(false) }
    var timeReminderIntervalInput by remember(assistant.id) { mutableStateOf("") }
    val settings = LocalSettings.current
    val dreamingModelState = rememberModelListState(
        modelId = assistant.dreamingModelId,
        providers = settings.providers,
        type = ModelType.CHAT,
    )
    ModelListSheet(
        state = dreamingModelState,
        onSelect = { onUpdateAssistant(assistant.copy(dreamingModelId = it.id)) },
    )

    if (showTimeReminderIntervalDialog) {
        val interval = timeReminderIntervalInput.toIntOrNull()?.takeIf { it > 0 }
        AnimatedAlertDialog(
            onDismissRequest = { showTimeReminderIntervalDialog = false },
            title = { Text(stringResource(R.string.assistant_page_time_reminder_interval)) },
            text = {
                TextField(
                    value = timeReminderIntervalInput,
                    onValueChange = { timeReminderIntervalInput = it },
                    label = { Text(stringResource(R.string.assistant_page_time_reminder_interval_label)) },
                    supportingText = { Text(stringResource(R.string.assistant_page_time_reminder_interval_hint)) },
                    isError = interval == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = interval != null,
                    onClick = {
                        interval?.let {
                            onUpdateAssistant(assistant.copy(timeReminderIntervalMinutes = it))
                        }
                        showTimeReminderIntervalDialog = false
                    },
                ) {
                    Text(stringResource(R.string.assistant_page_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimeReminderIntervalDialog = false }) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            },
        )
    }

    if (showAutoCompressThresholdDialog) {
        AnimatedAlertDialog(
            onDismissRequest = { showAutoCompressThresholdDialog = false },
            title = { Text(stringResource(R.string.assistant_page_memory_auto_compress_threshold)) },
            text = {
                Column {
                    AutoCompressThreshold.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateAssistant(assistant.copy(autoCompressThreshold = option))
                                    showAutoCompressThresholdDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = assistant.autoCompressThreshold == option,
                                onClick = null,
                            )
                            Text(
                                text = option.name,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAutoCompressThresholdDialog = false }) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val modelIconContainer = MaterialTheme.colorScheme.tertiaryContainer
        val modelIconContent = MaterialTheme.colorScheme.onTertiaryContainer
        AppSettingsCard {
            AppSwitchRow(
                checked = assistant.enableMemory,
                onCheckedChange = {
                    onUpdateAssistant(
                        assistant.copy(
                            enableMemory = it
                        )
                    )
                },
                headline = stringResource(R.string.assistant_page_memory),
                supporting = stringResource(R.string.assistant_page_memory_desc),
            )
            AppSwitchRow(
                checked = assistant.enableMemoryWrite,
                onCheckedChange = {
                    onUpdateAssistant(
                        assistant.copy(
                            enableMemoryWrite = it
                        )
                    )
                },
                enabled = assistant.enableMemory,
                headline = stringResource(R.string.assistant_page_memory_write),
                supporting = stringResource(R.string.assistant_page_memory_write_desc),
            )
            AppSwitchRow(
                checked = assistant.useGlobalMemory,
                onCheckedChange = {
                    onUpdateAssistant(
                        assistant.copy(
                            useGlobalMemory = it
                        )
                    )
                },
                enabled = assistant.enableMemory,
                headline = stringResource(R.string.assistant_page_global_memory),
                supporting = stringResource(R.string.assistant_page_global_memory_desc),
            )
            AppSwitchRow(
                checked = assistant.autoConsolidateMemory,
                onCheckedChange = {
                    onUpdateAssistant(
                        assistant.copy(
                            autoConsolidateMemory = it
                        )
                    )
                },
                enabled = assistant.enableMemory,
                headline = stringResource(R.string.assistant_page_memory_auto_dream),
                supporting = stringResource(R.string.assistant_page_memory_auto_dream_desc),
            )
            modelSettingItem(
                icon = HugeIcons.Brain02,
                title = R.string.assistant_page_dreaming_model,
                description = R.string.assistant_page_dreaming_model_desc,
                state = dreamingModelState,
                iconContainerColor = modelIconContainer,
                iconContentColor = modelIconContent,
                placeholder = R.string.assistant_page_dreaming_model_follow_global,
                onClear = { onUpdateAssistant(assistant.copy(dreamingModelId = null)) },
                clearContentDescription = R.string.assistant_page_dreaming_model_follow_global,
            )
            AppSwitchRow(
                checked = assistant.enableRecentChatsReference,
                onCheckedChange = {
                    onUpdateAssistant(
                        assistant.copy(
                            enableRecentChatsReference = it
                        )
                    )
                },
                headline = stringResource(R.string.assistant_page_recent_chats),
                supporting = stringResource(R.string.assistant_page_recent_chats_desc),
            )
        }

        AppSettingsCard {
            AppSwitchRow(
                checked = assistant.autoCompress,
                onCheckedChange = {
                    onUpdateAssistant(
                        assistant.copy(
                            autoCompress = it
                        )
                    )
                },
                headline = stringResource(R.string.assistant_page_memory_auto_compress),
                supporting = stringResource(R.string.assistant_page_memory_auto_compress_desc),
            )
            if (assistant.autoCompress) {
                AppSettingsRow {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.assistant_page_memory_auto_compress_ratio))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.assistant_page_memory_auto_compress_ratio_desc),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            ValuePill(text = "${(assistant.autoCompressRatio * 100).roundToInt()}%")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        AppSlider(
                            value = assistant.autoCompressRatio,
                            onValueChange = { value ->
                                // 按 5% 取整，避免浮点误差存成 0.79999995 之类的值
                                onUpdateAssistant(
                                    assistant.copy(autoCompressRatio = (value * 20).roundToInt() / 20f)
                                )
                            },
                            valueRange = AUTO_COMPRESS_RATIO_RANGE,
                            steps = 8,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (assistant.autoCompress) {
                AppSettingsRow {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { showAutoCompressThresholdDialog = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.assistant_page_memory_auto_compress_threshold))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.assistant_page_memory_auto_compress_threshold_desc),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            text = assistant.autoCompressThreshold.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        AppSettingsCard {
            AppSwitchRow(
                checked = assistant.enableTimeReminder,
                onCheckedChange = {
                    onUpdateAssistant(
                        assistant.copy(
                            enableTimeReminder = it
                        )
                    )
                },
                headline = stringResource(R.string.assistant_page_time_reminder),
                supporting = stringResource(R.string.assistant_page_time_reminder_desc),
            )
            if (assistant.enableTimeReminder) {
                AppSettingsRow {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable {
                                timeReminderIntervalInput = assistant.timeReminderIntervalMinutes.toString()
                                showTimeReminderIntervalDialog = true
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.assistant_page_time_reminder_interval))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.assistant_page_time_reminder_interval_desc),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.assistant_page_time_reminder_interval_value,
                                assistant.timeReminderIntervalMinutes
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryFilesTab(
    memoryId: String,
    memories: List<MemoryFile>,
    // 为 null 时不显示整理入口
    consolidation: MemoryConsolidationStatus?,
    onOrganizeMemory: () -> Unit,
    onOpenMemory: (path: String) -> Unit,
    onDeleteMemory: (MemoryFile) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var pendingDeleteMemory by remember { mutableStateOf<MemoryFile?>(null) }
    val memoryGroups = remember(memories) { memories.groupByDirectory() }

    // 空列表不滚动，占位才能在剩余空间里居中
    if (memoryGroups.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (consolidation != null) {
                MemoryConsolidationSection(
                    status = consolidation,
                    onOrganize = onOrganizeMemory,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                ExtensionEmptyState(
                    message = stringResource(R.string.assistant_page_memory_empty),
                    description = stringResource(
                        if (consolidation != null) {
                            R.string.assistant_page_memory_empty_hint
                        } else {
                            R.string.assistant_page_memory_empty_disabled_hint
                        }
                    ),
                    buttonText = stringResource(R.string.settings).takeIf { consolidation == null },
                    onAction = onOpenSettings,
                    icon = HugeIcons.Brain02,
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (consolidation != null) {
            MemoryConsolidationSection(
                status = consolidation,
                onOrganize = onOrganizeMemory,
            )
        }

        memoryGroups.fastForEach { (directory, files) ->
            AppSettingsGroup(
                title = memorySectionTitle(directory),
            ) {
                files.fastForEach { memory ->
                    AppSettingsRow {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable { onOpenMemory(memory.path) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = memory.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    if (memory.description.isNotEmpty()) {
                                        Text(
                                            text = memory.description,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    Text(
                                        text = stringResource(
                                            R.string.assistant_page_memory_updated_at,
                                            relativeTimeOf(memory.updatedAt),
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                            ItemActionMenu(
                                actions = listOf(
                                    ItemAction(
                                        text = stringResource(R.string.delete),
                                        icon = HugeIcons.Delete01,
                                        destructive = true,
                                        onClick = { pendingDeleteMemory = memory },
                                    ),
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    RikkaConfirmDialog(
        show = pendingDeleteMemory != null,
        title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            pendingDeleteMemory?.let(onDeleteMemory)
            pendingDeleteMemory = null
        },
        onDismiss = { pendingDeleteMemory = null },
        text = {
            Text(
                text = pendingDeleteMemory?.path.orEmpty(),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

/** 后台整理的状态和手动入口 */
@Composable
private fun MemoryConsolidationSection(
    status: MemoryConsolidationStatus,
    onOrganize: () -> Unit,
) {
    val lastRun = status.lastRun
    AppSettingsCard {
        AppSettingsRow {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.memory_dream_title))
                    Spacer(modifier = Modifier.height(2.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = memoryConsolidationSummary(status),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        // 整理中画一条进度条，整理完的对话数写在上面那行
                        status.roundProgress?.let { fraction ->
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                if (status.running) {
                    ContainedLoadingIndicator(modifier = Modifier.size(32.dp))
                } else {
                    FilledTonalButton(onClick = onOrganize) {
                        Text(
                            stringResource(
                                if (lastRun?.error != null) {
                                    R.string.assistant_page_memory_dream_retry
                                } else {
                                    R.string.assistant_page_memory_dream_now
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun memoryConsolidationSummary(status: MemoryConsolidationStatus): String {
    val lastRun = status.lastRun
    return when {
        status.running -> status.roundProgress
            ?.let { stringResource(R.string.memory_dream_progress, status.roundDone, status.roundTotal) }
            ?: stringResource(R.string.memory_dreaming)
        lastRun?.error != null -> lastRun.resultText()
        else -> listOfNotNull(
            status.pendingTurns.takeIf { it > 0 }?.let {
                stringResource(R.string.assistant_page_memory_dream_pending, it)
            },
            lastRun?.let { "${relativeTimeOf(it.finishedAt)}: ${it.resultText()} (${durationTextOf(it.duration)})" },
        ).joinToString("\n").ifEmpty { stringResource(R.string.assistant_page_memory_dream_hint) }
    }
}

// 按整秒显示成 "42s"、"1m 5s"
private fun durationTextOf(duration: Duration): String = duration.inWholeSeconds.coerceAtLeast(1).seconds.toString()

// 根目录下是 profile 和 preferences，都是关于用户本人的；记忆格式约定的几个目录有译名，其余的从目录名推
@Composable
private fun memorySectionTitle(directory: String): String = when (directory) {
    "/" -> stringResource(R.string.assistant_page_memory_section_you)
    "/topics" -> stringResource(R.string.assistant_page_memory_section_topics)
    "/areas" -> stringResource(R.string.assistant_page_memory_section_areas)
    "/people" -> stringResource(R.string.assistant_page_memory_section_people)
    else -> memoryTitleOf(directory.removePrefix("/"))
}

// 一周内显示"41 分钟前"这样的相对时间，更早的显示日期
private fun relativeTimeOf(time: Long): String = DateUtils.getRelativeTimeSpanString(
    time,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
    DateUtils.FORMAT_ABBREV_MONTH,
).toString()

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
