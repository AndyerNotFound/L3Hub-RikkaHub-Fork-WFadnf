package me.rerere.rikkahub.ui.pages.fingerprint

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.Json
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.entity.ModelFingerprintEntity
import me.rerere.rikkahub.data.fingerprint.FingerprintAnalysis
import me.rerere.rikkahub.data.fingerprint.FingerprintDecision
import me.rerere.rikkahub.data.fingerprint.FingerprintProtocol
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.style.Button
import me.rerere.rikkahub.ui.style.Card
import me.rerere.rikkahub.ui.style.Switch
import me.rerere.rikkahub.ui.style.TextButton
import me.rerere.rikkahub.ui.style.TopAppBar
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.ui.toast.ToastType
import org.koin.androidx.compose.koinViewModel
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ModelFingerprintPage(vm: ModelFingerprintVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val imports by vm.importState.collectAsStateWithLifecycle()
    val run by vm.runState.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val selected = settings.findFingerprintModel()
    val canStart = !imports.isBusy && imports.database.isReady && !run.isRunning && selected != null
    var confirmDetection by rememberSaveable { mutableStateOf(false) }
    var confirmClearHistory by rememberSaveable { mutableStateOf(false) }

    val bankPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBank(uri)
    }
    val detectorPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importDetector(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.model_fingerprint_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton() },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.model_fingerprint_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                ImportSection(
                    state = imports,
                    enabled = !imports.isBusy && !run.isRunning,
                    onPickBank = { bankPicker.launch(arrayOf("*/*")) },
                    onPickDetector = { detectorPicker.launch(arrayOf("*/*")) },
                )
            }
            item {
                SectionCard(stringResource(R.string.model_fingerprint_detection_title)) {
                    if (run.isRunning) {
                        run.modelLabel?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                        RunProgress(run, onStop = vm::stop)
                    } else {
                        ModelSelector(
                            modelId = selected?.id,
                            providers = settings.providers,
                            type = ModelType.CHAT,
                            modifier = Modifier.fillMaxWidth(),
                            onSelect = vm::selectModel,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.model_fingerprint_strict_mode), style = MaterialTheme.typography.titleSmall)
                                SupportingText(stringResource(R.string.model_fingerprint_strict_mode_desc))
                            }
                            Switch(checked = settings.modelFingerprintStrictMode, onCheckedChange = vm::setStrictMode)
                        }
                        Button(enabled = canStart, onClick = { confirmDetection = true }) {
                            Text(stringResource(R.string.model_fingerprint_detect))
                        }
                        run.issue?.let { issue ->
                            SupportingText(stringResource(issue.labelRes), error = issue != FingerprintRunIssue.CANCELLED)
                        }
                        if (run.historySaveFailed) {
                            SupportingText(stringResource(R.string.model_fingerprint_history_save_failed), error = true)
                        }
                    }
                }
            }
            run.analysis?.let { analysis ->
                item { AnalysisSection(analysis, run.modelLabel) }
            }
            item {
                SectionCard(stringResource(R.string.model_fingerprint_history_title)) {
                    if (history.isEmpty()) {
                        SupportingText(stringResource(R.string.model_fingerprint_history_empty))
                    } else {
                        TextButton(enabled = !run.isRunning, onClick = { confirmClearHistory = true }) {
                            Text(stringResource(R.string.model_fingerprint_history_clear))
                        }
                    }
                }
            }
            items(history, key = { it.id }) { HistoryRow(it) }
        }
    }

    RikkaConfirmDialog(
        show = confirmDetection && canStart,
        title = stringResource(R.string.model_fingerprint_cost_title),
        confirmText = stringResource(R.string.model_fingerprint_detect),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            confirmDetection = false
            selected?.let { vm.detect(it.id) }
        },
        onDismiss = { confirmDetection = false },
    ) {
        Text(stringResource(R.string.model_fingerprint_cost_warning, FingerprintProtocol.ANSWER_COUNT))
    }
    RikkaConfirmDialog(
        show = confirmClearHistory,
        title = stringResource(R.string.model_fingerprint_history_clear),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            confirmClearHistory = false
            vm.clearHistory {
                toaster.show(context.getString(R.string.model_fingerprint_history_clear_failed), type = ToastType.Error)
            }
        },
        onDismiss = { confirmClearHistory = false },
    ) {
        Text(stringResource(R.string.model_fingerprint_history_clear_desc))
    }
}

@Composable
private fun ImportSection(
    state: FingerprintImportState,
    enabled: Boolean,
    onPickBank: () -> Unit,
    onPickDetector: () -> Unit,
) {
    val database = state.database
    SectionCard(stringResource(R.string.model_fingerprint_import)) {
        if (database.isReady) {
            Text(stringResource(R.string.model_fingerprint_dataset_size, database.modelCount))
            database.builtAt?.let { SupportingText(stringResource(R.string.model_fingerprint_dataset_built, it)) }
            database.detectorVersion?.let { SupportingText(stringResource(R.string.model_fingerprint_detector_version, it.take(12))) }
        } else if (!state.isBusy) {
            SupportingText(stringResource(R.string.model_fingerprint_dataset_empty))
        }
        SupportingText(stringResource(R.string.model_fingerprint_import_desc))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = enabled, onClick = onPickBank) {
                Text(stringResource(R.string.model_fingerprint_import_bank))
            }
            Button(enabled = enabled, onClick = onPickDetector) {
                Text(stringResource(R.string.model_fingerprint_import_detector))
            }
        }
        when {
            state.isBusy -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                SupportingText(stringResource(R.string.model_fingerprint_import_loading))
            }
            state.issue != null -> SupportingText(stringResource(state.issue.labelRes), error = true)
            state.activated -> SupportingText(stringResource(R.string.model_fingerprint_import_success))
            database.hasBankImport && database.hasDetectorImport -> {
                SupportingText(stringResource(R.string.model_fingerprint_import_pending_pair))
            }
            database.hasBankImport -> SupportingText(stringResource(R.string.model_fingerprint_import_wait_detector))
            database.hasDetectorImport -> SupportingText(stringResource(R.string.model_fingerprint_import_wait_bank))
        }
    }
}

@Composable
private fun RunProgress(run: FingerprintRunState, onStop: () -> Unit) {
    val total = FingerprintProtocol.ANSWER_COUNT
    if (run.phase == FingerprintRunPhase.COLLECTING) {
        LinearProgressIndicator(
            progress = { run.completedRequests.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.model_fingerprint_detecting_progress, (run.completedRequests + 1).coerceAtMost(total), total))
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            stringResource(
                if (run.phase == FingerprintRunPhase.FINALIZING) R.string.model_fingerprint_finalizing
                else R.string.model_fingerprint_detecting,
            ),
        )
    }
    if (run.phase != FingerprintRunPhase.FINALIZING) {
        TextButton(onClick = onStop) { Text(stringResource(R.string.model_fingerprint_stop)) }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun AnalysisSection(analysis: FingerprintAnalysis, modelLabel: String?) {
    SectionCard(stringResource(R.string.model_fingerprint_result_title)) {
        modelLabel?.let { SupportingText(stringResource(R.string.model_fingerprint_tested_model, it)) }
        Text(stringResource(analysis.decision.labelRes), style = MaterialTheme.typography.titleSmall)
        analysis.prediction?.let { top ->
            Text(top.displayName, style = MaterialTheme.typography.headlineSmall)
            if (top.familyName.isNotBlank()) SupportingText(top.familyName)
            val probability = analysis.probability
            if (probability != null) {
                Text(stringResource(R.string.model_fingerprint_result_probability, formatProbability(probability)))
            } else {
                SupportingText(stringResource(R.string.model_fingerprint_result_no_probability))
            }
            SupportingText(stringResource(R.string.model_fingerprint_result_closed_set))
        }
        analysis.diagnostics.forEach { diagnostic ->
            SupportingText(
                stringResource(
                    R.string.model_fingerprint_result_parsed,
                    diagnostic.index + 1,
                    diagnostic.parsedNumbers,
                    diagnostic.minimumNumbers,
                ),
            )
        }
        if (analysis.results.isNotEmpty()) {
            Text(stringResource(R.string.model_fingerprint_candidates), style = MaterialTheme.typography.titleSmall)
            analysis.results.take(10).forEachIndexed { index, candidate ->
                CandidateRow(index + 1, candidate.displayName, candidate.probability)
            }
        }
    }
}

@Composable
private fun CandidateRow(rank: Int, label: String, probability: Double?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.model_fingerprint_candidate_rank, rank), style = MaterialTheme.typography.labelMedium)
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        probability?.takeIf { it.isFinite() }?.let {
            Text(formatProbability(it), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun HistoryRow(entry: ModelFingerprintEntity) {
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    val decision = FingerprintDecision.entries.firstOrNull { it.name == entry.status }
    val issue = FingerprintRunIssue.entries.firstOrNull { it.name == entry.error }
    val date = remember(entry.createdAt) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(entry.createdAt))
    }
    SectionCard(entry.resultLabel ?: entry.modelLabel) {
        SupportingText("${entry.providerLabel} · ${entry.modelLabel}")
        SupportingText(date)
        val status = decision?.labelRes ?: when (entry.status) {
            "CANCELLED" -> R.string.model_fingerprint_cancelled
            else -> R.string.model_fingerprint_status_failed
        }
        Text(stringResource(status), style = MaterialTheme.typography.titleSmall)
        if (entry.confidence.isFinite() && entry.confidence > 0.0) {
            SupportingText(stringResource(R.string.model_fingerprint_result_probability, formatProbability(entry.confidence)))
        } else if (entry.resultLabel != null) {
            SupportingText(stringResource(R.string.model_fingerprint_result_no_probability))
        }
        if (issue != null && entry.status != "CANCELLED") {
            SupportingText(stringResource(issue.labelRes), error = issue != FingerprintRunIssue.CANCELLED)
        }
        if (entry.resultLabel != null) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.model_fingerprint_hide_candidates else R.string.model_fingerprint_candidates))
            }
            if (expanded) {
                val candidates = remember(entry.candidatesJson) {
                    runCatching { Json.decodeFromString<List<FingerprintHistoryCandidate>>(entry.candidatesJson) }
                        .getOrDefault(emptyList())
                }
                candidates.forEachIndexed { index, candidate ->
                    CandidateRow(index + 1, candidate.label, candidate.probability)
                }
                SupportingText(stringResource(R.string.model_fingerprint_result_closed_set))
            }
        }
    }
}

@Composable
private fun SupportingText(text: String, error: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatProbability(value: Double): String = NumberFormat.getPercentInstance().apply {
    maximumFractionDigits = 1
}.format(value)

@get:StringRes
private val FingerprintDecision.labelRes: Int get() = when (this) {
    FingerprintDecision.RANKED -> R.string.model_fingerprint_decision_ranked
    FingerprintDecision.PARTIAL -> R.string.model_fingerprint_decision_partial
    FingerprintDecision.UNSCORABLE -> R.string.model_fingerprint_decision_unscorable
    FingerprintDecision.NOT_LANGUAGE_MODEL -> R.string.model_fingerprint_decision_not_model
}

@get:StringRes
private val FingerprintImportIssue.labelRes: Int get() = when (this) {
    FingerprintImportIssue.MISMATCH -> R.string.model_fingerprint_import_mismatch
    FingerprintImportIssue.INVALID_FILE -> R.string.model_fingerprint_import_invalid
    FingerprintImportIssue.TOO_LARGE -> R.string.model_fingerprint_import_too_large
    FingerprintImportIssue.READ_WRITE_FAILED -> R.string.model_fingerprint_import_failed
}

@get:StringRes
private val FingerprintRunIssue.labelRes: Int get() = when (this) {
    FingerprintRunIssue.NO_MODEL -> R.string.model_fingerprint_error_no_model
    FingerprintRunIssue.NO_DATASET -> R.string.model_fingerprint_error_no_dataset
    FingerprintRunIssue.REQUEST_OVERRIDE -> R.string.model_fingerprint_error_request_override
    FingerprintRunIssue.REQUEST_FAILED -> R.string.model_fingerprint_error_request
    FingerprintRunIssue.SCORING_FAILED -> R.string.model_fingerprint_error_scoring
    FingerprintRunIssue.CANCELLED -> R.string.model_fingerprint_cancelled
}
