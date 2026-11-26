package me.rerere.rikkahub.ui.pages.fingerprint

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.priceUnitTokens
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.UsageRecorder
import me.rerere.rikkahub.data.ai.UsageSource
import me.rerere.rikkahub.data.ai.UsageTag
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.dao.ModelFingerprintDAO
import me.rerere.rikkahub.data.db.entity.ModelFingerprintEntity
import me.rerere.rikkahub.data.fingerprint.FingerprintAnalysis
import me.rerere.rikkahub.data.fingerprint.FingerprintBankMismatchException
import me.rerere.rikkahub.data.fingerprint.FingerprintChallengeProtocol
import me.rerere.rikkahub.data.fingerprint.FingerprintDatabaseStatus
import me.rerere.rikkahub.data.fingerprint.FingerprintDatabaseStore
import me.rerere.rikkahub.data.fingerprint.FingerprintFormatException
import me.rerere.rikkahub.data.fingerprint.FingerprintImportResult
import me.rerere.rikkahub.data.fingerprint.FingerprintOutput
import me.rerere.rikkahub.data.fingerprint.collectFingerprintReply
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlin.uuid.Uuid

internal data class FingerprintImportState(
    val database: FingerprintDatabaseStatus = FingerprintDatabaseStatus(),
    val isBusy: Boolean = true,
    val activated: Boolean = false,
    val issue: FingerprintImportIssue? = null,
)

internal enum class FingerprintImportIssue { MISMATCH, INVALID_FILE, TOO_LARGE, READ_WRITE_FAILED }

internal enum class FingerprintRunPhase { IDLE, PREPARING, COLLECTING, FINALIZING }

internal enum class FingerprintRunIssue { NO_MODEL, NO_DATASET, REQUEST_OVERRIDE, REQUEST_FAILED, SCORING_FAILED, CANCELLED }

internal data class FingerprintRunState(
    val phase: FingerprintRunPhase = FingerprintRunPhase.IDLE,
    val completedRequests: Int = 0,
    val modelLabel: String? = null,
    val analysis: FingerprintAnalysis? = null,
    val issue: FingerprintRunIssue? = null,
    val historySaveFailed: Boolean = false,
) {
    val isRunning: Boolean get() = phase != FingerprintRunPhase.IDLE
}

@Serializable
internal data class FingerprintHistoryCandidate(
    val label: String,
    val family: String,
    val score: Double,
    val probability: Double?,
)

internal fun Settings.findFingerprintModel(id: Uuid? = modelFingerprintModelId): Model? =
    id?.let {
        providers.asSequence()
            .filter { provider -> provider.enabled }
            .flatMap { provider -> provider.models.asSequence() }
            .firstOrNull { model -> model.id == id && model.type == ModelType.CHAT }
    }

class ModelFingerprintVM(
    context: Context,
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val usageRecorder: UsageRecorder,
    private val databaseStore: FingerprintDatabaseStore,
    private val dao: ModelFingerprintDAO,
) : ViewModel() {
    private val contentResolver = context.contentResolver
    val settings = settingsStore.settingsFlow
    val history = dao.recentFlow(HISTORY_LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importState = MutableStateFlow(FingerprintImportState())
    internal val importState = _importState.asStateFlow()
    private val _runState = MutableStateFlow(FingerprintRunState())
    internal val runState = _runState.asStateFlow()
    private var runJob: Job? = null

    init {
        viewModelScope.launch {
            _importState.value = FingerprintImportState(database = databaseStore.status(), isBusy = false)
        }
    }

    fun selectModel(model: Model) {
        viewModelScope.launch {
            settingsStore.update { it.copy(modelFingerprintModelId = model.id) }
        }
    }

    fun setStrictMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it.copy(modelFingerprintStrictMode = enabled) }
        }
    }

    fun importBank(uri: Uri) = importFile(uri, isBank = true)

    fun importDetector(uri: Uri) = importFile(uri, isBank = false)

    private fun importFile(uri: Uri, isBank: Boolean) {
        if (_importState.value.isBusy || _runState.value.isRunning) return
        _importState.value = _importState.value.copy(isBusy = true, activated = false, issue = null)
        viewModelScope.launch {
            var activated = false
            val issue = try {
                val maxSize = if (isBank) {
                    FingerprintDatabaseStore.MAX_BANK_BYTES
                } else {
                    FingerprintDatabaseStore.MAX_DETECTOR_BYTES
                }
                val bytes = readDocument(uri, maxSize)
                val result = if (isBank) databaseStore.importBank(bytes) else databaseStore.importDetector(bytes)
                activated = result == FingerprintImportResult.Activated
                null
            } catch (error: CancellationException) {
                throw error
            } catch (_: FingerprintBankMismatchException) {
                FingerprintImportIssue.MISMATCH
            } catch (_: FingerprintFormatException) {
                FingerprintImportIssue.INVALID_FILE
            } catch (_: ImportTooLargeException) {
                FingerprintImportIssue.TOO_LARGE
            } catch (_: Exception) {
                FingerprintImportIssue.READ_WRITE_FAILED
            }
            _importState.value = FingerprintImportState(databaseStore.status(), false, activated, issue)
        }
    }

    private suspend fun readDocument(uri: Uri, maxSize: Int): ByteArray = withContext(Dispatchers.IO) {
        val stream = contentResolver.openInputStream(uri) ?: throw IOException("Cannot open selected document")
        stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            while (true) {
                ensureActive()
                val count = input.read(buffer)
                if (count == -1) break
                if (count > maxSize - output.size()) throw ImportTooLargeException()
                output.write(buffer, 0, count)
            }
            if (output.size() == 0) throw FingerprintFormatException("Empty document")
            output.toByteArray()
        }
    }

    fun detect(modelId: Uuid) {
        if (_importState.value.isBusy || _runState.value.isRunning || runJob?.isCompleted == false) return
        _runState.value = FingerprintRunState(phase = FingerprintRunPhase.PREPARING)
        runJob = viewModelScope.launch {
            try {
                runDetection(modelId)
            } catch (error: CancellationException) {
                if (_runState.value.isRunning) {
                    _runState.value = FingerprintRunState(issue = FingerprintRunIssue.CANCELLED)
                }
                throw error
            } catch (_: Exception) {
                _runState.value = FingerprintRunState(issue = FingerprintRunIssue.REQUEST_FAILED)
            }
        }
    }

    private suspend fun runDetection(modelId: Uuid) {
        val currentSettings = settingsStore.awaitLoaded()
        val selected = currentSettings.findFingerprintModel(modelId)
        val providerSetting = selected?.findProvider(currentSettings.providers)
        if (selected == null || providerSetting == null) {
            _runState.value = FingerprintRunState(issue = FingerprintRunIssue.NO_MODEL)
            return
        }
        if (providerSetting.guard.requestOverride.isActive) {
            _runState.value = FingerprintRunState(issue = FingerprintRunIssue.REQUEST_OVERRIDE)
            return
        }
        val detector = databaseStore.loadDetector()
        if (detector == null) {
            _runState.value = FingerprintRunState(issue = FingerprintRunIssue.NO_DATASET)
            return
        }

        val modelLabel = selected.displayName.ifBlank { selected.modelId }
        val startedAt = System.currentTimeMillis()
        val challenges = FingerprintChallengeProtocol.generate()
        val outputs = challenges.map { FingerprintOutput("", it.expectedCount) }.toMutableList()
        val allowPartial = !currentSettings.modelFingerprintStrictMode
        var analysis: FingerprintAnalysis? = null
        var issue: FingerprintRunIssue? = null

        _runState.value = FingerprintRunState(phase = FingerprintRunPhase.COLLECTING, modelLabel = modelLabel)
        try {
            val provider = usageRecorder.wrap(
                providerManager.getProviderByType(providerSetting),
                UsageTag(UsageSource.MODEL_FINGERPRINT),
            )
            val params = TextGenerationParams(
                // Keep the model's routing, without its chat-only tools and body overrides.
                model = selected.copy(tools = emptySet(), customBodies = emptyList()),
                maxTokens = if (providerSetting is ProviderSetting.Claude) CLAUDE_OUTPUT_TOKENS else null,
                customHeaders = selected.customHeaders,
                priceUnitTokens = priceUnitTokens(currentSettings.displaySetting.binaryUnits),
                sessionId = null,
            )
            challenges.forEachIndexed { index, challenge ->
                // A capped sample can arrive on IO even when cancellation prevents the return to Main.
                val cappedReply = AtomicReference<String?>(null)
                try {
                    val reply = withContext(Dispatchers.IO) {
                        collectFingerprintReply(
                            expectedCount = challenge.expectedCount,
                            allowPartial = allowPartial,
                            onCappedReply = cappedReply::set,
                        ) {
                            provider.streamText(providerSetting, listOf(UIMessage.user(challenge.prompt)), params)
                        }
                    }
                    outputs[index] = FingerprintOutput(reply, challenge.expectedCount)
                } catch (error: CancellationException) {
                    cappedReply.get()?.let { outputs[index] = FingerprintOutput(it, challenge.expectedCount) }
                    throw error
                } catch (_: Exception) {
                    issue = FingerprintRunIssue.REQUEST_FAILED
                }
                _runState.value = _runState.value.copy(completedRequests = index + 1)
            }
            _runState.value = _runState.value.copy(phase = FingerprintRunPhase.FINALIZING)
            if (outputs.any { it.text.isNotBlank() }) {
                analysis = withContext(Dispatchers.Default) { detector.analyze(outputs, allowPartial) }
            }
        } catch (error: CancellationException) {
            issue = FingerprintRunIssue.CANCELLED
            throw error
        } catch (_: Exception) {
            issue = if (_runState.value.phase == FingerprintRunPhase.FINALIZING) {
                FingerprintRunIssue.SCORING_FAILED
            } else {
                FingerprintRunIssue.REQUEST_FAILED
            }
        } finally {
            withContext(NonCancellable) {
                _runState.value = _runState.value.copy(phase = FingerprintRunPhase.FINALIZING)
                if (issue == FingerprintRunIssue.CANCELLED && outputs.any { it.text.isNotBlank() }) {
                    analysis = withContext(Dispatchers.Default) {
                        runCatching { detector.analyze(outputs, allowPartial) }.getOrNull()
                    }
                }
                val saved = try {
                    saveHistory(startedAt, modelLabel, providerSetting.name, detector.version, analysis, issue)
                    true
                } catch (_: Exception) {
                    false
                }
                _runState.value = _runState.value.copy(
                    phase = FingerprintRunPhase.IDLE,
                    analysis = analysis,
                    issue = issue,
                    historySaveFailed = !saved,
                )
            }
        }
    }

    private suspend fun saveHistory(
        startedAt: Long,
        modelLabel: String,
        providerLabel: String,
        detectorVersion: String,
        analysis: FingerprintAnalysis?,
        issue: FingerprintRunIssue?,
    ) {
        val candidates = analysis?.results.orEmpty().take(MAX_SAVED_CANDIDATES).map {
            FingerprintHistoryCandidate(it.displayName, it.familyName, it.score, it.probability)
        }
        dao.insertRecent(
            entity = ModelFingerprintEntity(
                createdAt = startedAt,
                modelLabel = modelLabel,
                providerLabel = providerLabel,
                resultLabel = analysis?.prediction?.displayName,
                candidatesJson = Json.encodeToString(candidates),
                confidence = analysis?.probability ?: 0.0,
                detectorVersion = detectorVersion,
                status = analysis?.decision?.name ?: if (issue == FingerprintRunIssue.CANCELLED) "CANCELLED" else "FAILED",
                error = issue?.name,
            ),
            limit = HISTORY_LIMIT,
        )
    }

    fun clearHistory(onFailure: () -> Unit) {
        if (_runState.value.isRunning) return
        viewModelScope.launch {
            try {
                dao.deleteAll()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                onFailure()
            }
        }
    }

    fun stop() {
        runJob?.cancel()
    }

    private class ImportTooLargeException : IOException()

    companion object {
        private const val HISTORY_LIMIT = 50
        private const val CLAUDE_OUTPUT_TOKENS = 8192
        private const val MAX_SAVED_CANDIDATES = 10
    }
}
