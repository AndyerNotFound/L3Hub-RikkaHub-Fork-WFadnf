package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiBrain01
import me.rerere.hugeicons.stroke.BubbleChat
import me.rerere.hugeicons.stroke.CheckList
import me.rerere.hugeicons.stroke.Edit03
import me.rerere.hugeicons.stroke.FolderCode
import me.rerere.hugeicons.stroke.GlobalSearch
import me.rerere.hugeicons.stroke.McpServer
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.ChatToolFactory
import me.rerere.rikkahub.data.ai.tools.ToolCatalogEntry
import me.rerere.rikkahub.data.ai.tools.ToolCatalogGroup
import me.rerere.rikkahub.data.ai.tools.decodeToolOverrideParameters
import me.rerere.rikkahub.data.ai.tools.encodeToolOverrideParameters
import me.rerere.rikkahub.data.model.ToolOverride
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.CardGroupValue
import me.rerere.rikkahub.ui.components.ui.JsonTree
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.terminal.Button
import me.rerere.rikkahub.ui.terminal.Switch
import me.rerere.rikkahub.ui.terminal.TextButton
import me.rerere.rikkahub.ui.terminal.TopAppBar
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.ui.components.AnimatedAlertDialog
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * 工具覆写：列出应用当前能生成的全部工具定义，按工具名保存覆写。
 *
 * 覆写在每轮组装请求时盖到真实工具上，所以同一份覆写对本地工具、MCP 工具和动态生成的说明同样生效。
 */
@Composable
fun SettingToolOverridePage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val toolFactory: ChatToolFactory = koinInject()
    val toaster = LocalToaster.current
    val savedToast = stringResource(R.string.tool_override_saved)
    val resetToast = stringResource(R.string.tool_override_reset_done)

    var catalog by remember { mutableStateOf<List<ToolCatalogEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(settings.init) {
        if (settings.init) return@LaunchedEffect
        loading = true
        loadError = null
        runCatching { withContext(Dispatchers.IO) { toolFactory.createToolCatalog(settings) } }
            .onSuccess { catalog = it }
            .onFailure { loadError = it.message ?: it::class.simpleName.orEmpty() }
        loading = false
    }

    val overrides = remember(settings.toolOverrides) {
        settings.toolOverrides.associateBy { it.name }
    }
    val selected = catalog.firstOrNull { it.name == selectedName }

    fun saveOverride(name: String, newOverride: ToolOverride?) {
        vm.updateSettings { latest ->
            val rest = latest.toolOverrides.filterNot { it.name == name }
            latest.copy(
                toolOverrides = if (newOverride == null || newOverride.isEmpty) rest else rest + newOverride
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setting_page_tool_override)) },
                navigationIcon = { BackButton() },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("intro") {
                CardGroup {
                    formItem {
                        Text(
                            text = stringResource(R.string.tool_override_page_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (loading) {
                item("loading") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.tool_override_page_loading))
                    }
                }
            }

            loadError?.let { message ->
                item("error") {
                    Text(
                        text = stringResource(R.string.tool_override_page_load_failed, message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (!loading && loadError == null) {
                ToolCatalogGroup.entries.forEach { group ->
                    val entries = catalog.filter { it.group == group }
                    if (entries.isNotEmpty()) {
                        item(group.name) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                ToolGroupHeader(group)
                                CardGroup {
                                    entries.forEach { entry ->
                                        val toolOverride = overrides[entry.name]
                                        val description = toolOverride?.description ?: entry.description
                                        val overlineText = entry.groupDetail
                                        item(
                                            onClick = { selectedName = entry.name },
                                            overlineContent = if (overlineText != null) {
                                                { Text(text = overlineText) }
                                            } else {
                                                null
                                            },
                                            supportingContent = {
                                                val noDescription = stringResource(R.string.tool_override_detail_no_description)
                                                Text(
                                                    text = description.ifBlank { noDescription },
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                            leadingContent = {
                                                CardGroupIcon(
                                                    icon = group.icon(),
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                )
                                            },
                                            trailingContent = if (toolOverride == null) null else {
                                                { CardGroupValue(text = stringResource(R.string.tool_override_overridden)) }
                                            },
                                            headlineContent = {
                                                Text(
                                                    text = entry.name,
                                                    fontFamily = JetbrainsMono,
                                                    fontWeight = FontWeight.Medium,
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (catalog.isEmpty()) {
                    item("empty") {
                        Text(
                            text = stringResource(R.string.tool_override_page_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (selected != null) {
        ToolOverrideSheet(
            entry = selected,
            toolOverride = overrides[selected.name],
            onSave = { saved ->
                saveOverride(selected.name, saved)
                toaster.show(savedToast)
            },
            onReset = {
                saveOverride(selected.name, null)
                toaster.show(resetToast)
            },
            onDismiss = { selectedName = null },
        )
    }
}

@Composable
private fun ToolGroupHeader(group: ToolCatalogGroup) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = group.icon(),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = group.label(),
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ToolCatalogGroup.label(): String = stringResource(
    when (this) {
        ToolCatalogGroup.LOCAL -> R.string.tool_override_group_local
        ToolCatalogGroup.MEMORY -> R.string.tool_override_group_memory
        ToolCatalogGroup.SEARCH -> R.string.tool_override_group_search
        ToolCatalogGroup.CONVERSATION -> R.string.tool_override_group_conversation
        ToolCatalogGroup.WORKSPACE -> R.string.tool_override_group_workspace
        ToolCatalogGroup.TODO -> R.string.tool_override_group_todo
        ToolCatalogGroup.SKILLS -> R.string.tool_override_group_skills
        ToolCatalogGroup.MCP -> R.string.tool_override_group_mcp
    }
)

private fun ToolCatalogGroup.icon(): ImageVector = when (this) {
    ToolCatalogGroup.LOCAL -> HugeIcons.Tools
    ToolCatalogGroup.MEMORY -> HugeIcons.AiBrain01
    ToolCatalogGroup.SEARCH -> HugeIcons.GlobalSearch
    ToolCatalogGroup.CONVERSATION -> HugeIcons.BubbleChat
    ToolCatalogGroup.WORKSPACE -> HugeIcons.FolderCode
    ToolCatalogGroup.TODO -> HugeIcons.CheckList
    ToolCatalogGroup.SKILLS -> HugeIcons.Puzzle
    ToolCatalogGroup.MCP -> HugeIcons.McpServer
}

@Composable
private fun ToolOverrideSheet(
    entry: ToolCatalogEntry,
    toolOverride: ToolOverride?,
    onSave: (ToolOverride) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember(entry.name) { mutableStateOf(false) }
    var confirmReset by remember(entry.name) { mutableStateOf(false) }

    val overriddenNote = stringResource(R.string.tool_override_detail_override_note)
    val noDescription = stringResource(R.string.tool_override_detail_no_description)
    val noParameters = stringResource(R.string.tool_override_detail_no_parameters)

    val effectiveDescription = toolOverride?.description ?: entry.description
    val effectiveInstructions = toolOverride?.instructions ?: entry.instructions
    val effectiveParameters: JsonElement? = remember(toolOverride?.parameters, entry.parameters) {
        toolOverride?.parameters
            ?.let { raw -> runCatching { JsonInstant.parseToJsonElement(raw) }.getOrNull() }
            ?: entry.parameters
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = JetbrainsMono,
                        modifier = Modifier.weight(1f),
                    )
                    if (toolOverride != null) {
                        CardGroupValue(text = stringResource(R.string.tool_override_overridden))
                    }
                }
                val groupLabel = entry.group.label()
                Text(
                    text = buildString {
                        append(groupLabel)
                        entry.groupDetail?.let { append(" · ").append(it) }
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (toolOverride != null) {
                    Text(
                        text = overriddenNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (!editing) {
                ToolSheetSection(title = stringResource(R.string.tool_override_detail_description)) {
                    Text(
                        text = effectiveDescription.ifBlank { noDescription },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (effectiveInstructions.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.tool_override_detail_instructions),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SelectionContainer {
                            Text(
                                text = effectiveInstructions,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = JetbrainsMono,
                            )
                        }
                    }
                }

                ToolSheetSection(title = stringResource(R.string.tool_override_detail_definition)) {
                    if (effectiveParameters == null) {
                        Text(
                            text = noParameters,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        JsonTree(
                            json = effectiveParameters,
                            initialExpandLevel = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (toolOverride != null) {
                        TextButton(onClick = { confirmReset = true }) {
                            Text(stringResource(R.string.tool_override_action_reset))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.tool_override_action_close))
                    }
                    Button(
                        onClick = { editing = true },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Edit03,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.tool_override_action_edit))
                    }
                }
            } else {
                ToolOverrideEditor(
                    entry = entry,
                    toolOverride = toolOverride,
                    onCancel = { editing = false },
                    onSave = {
                        onSave(it)
                        editing = false
                    },
                )
            }
        }
    }

    if (confirmReset) {
        AnimatedAlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.tool_override_action_reset)) },
            text = { Text(stringResource(R.string.tool_override_reset_confirm_text, entry.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onReset()
                    }
                ) {
                    Text(stringResource(R.string.tool_override_action_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.tool_override_action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ToolOverrideEditor(
    entry: ToolCatalogEntry,
    toolOverride: ToolOverride?,
    onCancel: () -> Unit,
    onSave: (ToolOverride) -> Unit,
) {
    val invalidJson = stringResource(R.string.tool_override_edit_invalid_json)
    var descriptionOn by remember(entry.name) {
        mutableStateOf(toolOverride?.description != null || toolOverride?.instructions != null)
    }
    var descriptionDraft by remember(entry.name) {
        mutableStateOf(toolOverride?.description ?: entry.description)
    }
    var instructionsDraft by remember(entry.name) {
        mutableStateOf(toolOverride?.instructions ?: entry.instructions)
    }
    var definitionOn by remember(entry.name) { mutableStateOf(toolOverride?.parameters != null) }
    var definitionDraft by remember(entry.name) {
        mutableStateOf(
            toolOverride?.parameters
                ?: entry.parameters?.let { JsonInstantPretty.encodeToString(JsonElement.serializer(), it) }
                ?: DEFAULT_TOOL_SCHEMA
        )
    }
    var definitionError by remember(entry.name) { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.tool_override_edit_description_switch),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.tool_override_edit_description_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = descriptionOn, onCheckedChange = { descriptionOn = it })
        }

        if (descriptionOn) {
            OutlinedTextField(
                value = descriptionDraft,
                onValueChange = { descriptionDraft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tool_override_detail_description)) },
                minLines = 4,
                maxLines = 10,
            )
            OutlinedTextField(
                value = instructionsDraft,
                onValueChange = { instructionsDraft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tool_override_edit_instructions_label)) },
                supportingText = { Text(stringResource(R.string.tool_override_edit_instructions_hint)) },
                minLines = 3,
                maxLines = 10,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetbrainsMono),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.tool_override_edit_definition_switch),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.tool_override_edit_definition_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = definitionOn, onCheckedChange = { definitionOn = it })
        }

        if (definitionOn) {
            OutlinedTextField(
                value = definitionDraft,
                onValueChange = {
                    definitionDraft = it
                    definitionError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tool_override_edit_definition_label)) },
                isError = definitionError != null,
                supportingText = {
                    Text(
                        text = definitionError
                            ?: stringResource(R.string.tool_override_edit_definition_supporting)
                    )
                },
                minLines = 8,
                maxLines = 16,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetbrainsMono),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = onCancel) {
                Text(stringResource(R.string.tool_override_action_cancel))
            }
            Button(
                onClick = {
                    val schemaText = if (definitionOn) definitionDraft.trim() else null
                    val schema = schemaText?.let(::decodeToolOverrideParameters)
                    if (schemaText != null && schema == null) {
                        definitionError = invalidJson
                        return@Button
                    }
                    onSave(
                        ToolOverride(
                            name = entry.name,
                            description = if (descriptionOn) descriptionDraft else null,
                            instructions = if (descriptionOn) instructionsDraft else null,
                            parameters = schema?.let(::encodeToolOverrideParameters),
                        )
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.tool_override_action_save))
            }
        }
    }
}

@Composable
private fun ToolSheetSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

private const val DEFAULT_TOOL_SCHEMA = """{"type": "object", "properties": {}}"""
