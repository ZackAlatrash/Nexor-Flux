package com.zack.recomptracker.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import com.zack.recomptracker.data.preferences.UiPreferences
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.security.MessageDigest

class GemmaInsightCoordinator(
    private val context: Context,
    private val aiEnabledFlow: Flow<Boolean>,
    private val scope: CoroutineScope,
    private val serviceHolder: GemmaServiceHolder,
    private val uiPreferences: UiPreferences,
) : AiInsightCoordinator {

    private val _state = MutableStateFlow<AiInsightState>(AiInsightState.Disabled)
    override val state: StateFlow<AiInsightState> = _state.asStateFlow()

    private val _selectedModel = MutableStateFlow(ModelVariant.GEMMA_2B)
    override val selectedModel: StateFlow<ModelVariant> = _selectedModel.asStateFlow()

    @Volatile private var lastGeneratedKey: String? = null
    @Volatile private var downloadId: Long = -1L

    private val insightStates: Map<InsightKind, MutableStateFlow<AiInsightState>> =
        InsightKind.entries.associateWith { MutableStateFlow<AiInsightState>(AiInsightState.ModelReady) }

    private val lastInsightKeys = java.util.concurrent.ConcurrentHashMap<InsightKind, String>()

    private val promptBuilder = InsightPromptBuilder()

    init {
        // Load saved model selection (fast DataStore read; default GEMMA_2B until loaded).
        scope.launch {
            _selectedModel.value = uiPreferences.selectedModelVariant.first()
        }

        // React to AI-enabled toggle.
        scope.launch {
            aiEnabledFlow.collect { enabled ->
                if (!enabled) {
                    cancelActiveDownloadSilently()
                    lastInsightKeys.clear()
                    _state.value = AiInsightState.Disabled
                } else if (_state.value == AiInsightState.Disabled) {
                    initializeForCurrentVariant()
                }
            }
        }
    }

    override fun setSelectedModel(variant: ModelVariant) {
        if (_selectedModel.value == variant) return
        scope.launch {
            uiPreferences.setSelectedModel(variant)
            _selectedModel.value = variant
            if (_state.value != AiInsightState.Disabled) {
                cancelActiveDownloadSilently()
                lastGeneratedKey = null
                lastInsightKeys.clear()
                serviceHolder.release()
                initializeForCurrentVariant()
            }
        }
    }

    // ── Download ───────────────────────────────────────────────────────────────

    override fun requestDownload() {
        if (_state.value != AiInsightState.ModelMissing && _state.value != AiInsightState.DownloadFailed) return
        val variant = _selectedModel.value
        if (!hasSufficientStorage(variant)) {
            _state.value = AiInsightState.DownloadFailed
            return
        }
        val modelFile = serviceHolder.modelFileFor(variant)
        modelFile.parentFile?.mkdirs()
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(variant.downloadUrl))
            .setTitle("${variant.displayName} — AI Model")
            .setDescription("Downloading for on-device AI features (~${variant.displaySizeGb})")
            .setDestinationInExternalFilesDir(context, null, "ai/${variant.fileName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverRoaming(false)
        downloadId = dm.enqueue(request)
        _state.value = AiInsightState.Downloading(null)
        scope.launch {
            uiPreferences.setPendingDownloadId(downloadId)
            pollDownloadProgress(dm, variant)
        }
    }

    private suspend fun pollDownloadProgress(dm: DownloadManager, variant: ModelVariant) {
        while (true) {
            delay(1000L)
            if (_selectedModel.value != variant) break
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = withContext(Dispatchers.IO) { dm.query(query) }
            if (!cursor.moveToFirst()) { cursor.close(); break }
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            cursor.close()

            when (status) {
                DownloadManager.STATUS_RUNNING -> {
                    val progress = if (bytesTotal > 0) bytesDownloaded.toFloat() / bytesTotal.toFloat() else null
                    _state.value = AiInsightState.Downloading(progress)
                }
                DownloadManager.STATUS_SUCCESSFUL -> {
                    clearPendingDownloadId()
                    if (_selectedModel.value == variant) {
                        _state.value = AiInsightState.ModelVerifying
                        scope.launch { runIntegrityCheck(variant) }
                    }
                    break
                }
                DownloadManager.STATUS_FAILED -> {
                    clearPendingDownloadId()
                    if (_selectedModel.value == variant) {
                        _state.value = AiInsightState.DownloadFailed
                    }
                    break
                }
            }
        }
    }

    // ── Integrity ──────────────────────────────────────────────────────────────

    /**
     * [fullCheck] = true (post-download): verifies size AND SHA-256.
     * [fullCheck] = false (launch): verifies size only — avoids a 10–30 s hash on every cold start.
     */
    private suspend fun runIntegrityCheck(variant: ModelVariant, fullCheck: Boolean = true) {
        val passed = withContext(Dispatchers.IO) { verifyModel(variant, fullCheck) }
        if (_state.value != AiInsightState.ModelVerifying) return
        if (_selectedModel.value != variant) return
        if (passed) {
            _state.value = AiInsightState.ModelReady
        } else {
            serviceHolder.modelFileFor(variant).delete()
            _state.value = AiInsightState.DownloadFailed
        }
    }

    private fun verifyModel(variant: ModelVariant, fullCheck: Boolean = true): Boolean {
        val file = serviceHolder.modelFileFor(variant)
        if (!file.exists() || file.length() != variant.expectedBytes) return false
        if (!fullCheck) return true
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered(8 * 1024).use { stream ->
                val buf = ByteArray(8 * 1024)
                var n: Int
                while (stream.read(buf).also { n = it } != -1) {
                    digest.update(buf, 0, n)
                }
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            hex == variant.sha256
        } catch (_: Exception) {
            false
        }
    }

    // ── Model management ───────────────────────────────────────────────────────

    override fun cancelDownload() {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.remove(downloadId)
        clearPendingDownloadId()
        _state.value = AiInsightState.ModelMissing
    }

    override fun deleteModel() {
        val variant = _selectedModel.value
        lastGeneratedKey = null
        lastInsightKeys.clear()
        serviceHolder.modelFileFor(variant).delete()
        scope.launch { serviceHolder.release() }
        _state.value = AiInsightState.ModelMissing
    }

    // ── Generation ─────────────────────────────────────────────────────────────

    override fun onAiCardVisible(context: InsightContext) {
        if (context.result.verdict == AdjustmentVerdict.WAIT_FOR_DATA) return
        if (_state.value != AiInsightState.ModelReady) return
        val key = context.result.key()
        if (key == lastGeneratedKey) return
        lastGeneratedKey = key
        scope.launch { generate(context) }
    }

    override fun retryGeneration(context: InsightContext) {
        if (context.result.verdict == AdjustmentVerdict.WAIT_FOR_DATA) return
        if (_state.value !is AiInsightState.Ready && _state.value != AiInsightState.ModelReady && _state.value !is AiInsightState.Error) return
        lastGeneratedKey = null
        _state.value = AiInsightState.ModelReady
        onAiCardVisible(context)
    }

    override fun generationState(kind: InsightKind): StateFlow<AiInsightState> =
        insightStates.getValue(kind).asStateFlow()

    override fun onInsightVisible(request: InsightRequest) {
        if (request.kind in CLOUD_ONLY_KINDS) {
            // These richer cards are cloud-only; keep them hidden on the local backend.
            insightStates.getValue(request.kind).value = AiInsightState.Disabled
            return
        }
        if (!request.hasSufficientData) return
        val flow = insightStates.getValue(request.kind)
        if (!isModelUsable()) {
            flow.value = _state.value
            return
        }
        val key = request.dedupKey()
        if (lastInsightKeys[request.kind] == key) return
        lastInsightKeys[request.kind] = key
        scope.launch { generateInsight(request, flow) }
    }

    override fun retryInsight(request: InsightRequest) {
        lastInsightKeys.remove(request.kind)
        insightStates.getValue(request.kind).value = AiInsightState.ModelReady
        onInsightVisible(request)
    }

    private fun isModelUsable(): Boolean = when (_state.value) {
        AiInsightState.Disabled,
        AiInsightState.ModelMissing,
        is AiInsightState.Downloading,
        AiInsightState.DownloadFailed,
        AiInsightState.ModelVerifying -> false
        else -> true
    }

    private suspend fun generateInsight(
        request: InsightRequest,
        flow: MutableStateFlow<AiInsightState>,
    ) {
        flow.value = AiInsightState.LoadingModel
        try {
            val variant = _selectedModel.value
            val modelPath = serviceHolder.modelFileFor(variant).absolutePath
            val service = serviceHolder.getOrCreateService(modelPath)
            service.ensureInitialized()

            if (flow.value !is AiInsightState.LoadingModel) return
            flow.value = AiInsightState.Generating("")
            val prompt = when (request) {
                is InsightRequest.ProgressTrend -> promptBuilder.buildProgressTrendPrompt(request.context)
                is InsightRequest.RecoveryReadiness -> promptBuilder.buildRecoveryReadinessPrompt(request.context)
                is InsightRequest.RestOfDay -> promptBuilder.buildRestOfDayPrompt(request.context)
                is InsightRequest.WeeklyPattern,
                is InsightRequest.TargetChange,
                is InsightRequest.NoiseDefuser,
                is InsightRequest.CrossMetric -> error("${request.kind} is cloud-only; handled in onInsightVisible")
            }

            val sb = StringBuilder()
            withTimeout(GENERATION_TIMEOUT_MS) {
                service.generateExplanation(prompt).collect { chunk ->
                    sb.append(chunk)
                    flow.value = AiInsightState.Generating(sb.toString())
                }
            }
            if (flow.value is AiInsightState.Generating) {
                val finalText = sb.toString()
                    .trim()
                    .replace(Regex("""[*_`#>]"""), "")
                    .replace(Regex("""\n{2,}"""), " ")
                    .let { InsightPromptBuilder.limitToSentences(it, 2) }
                flow.value = AiInsightState.Ready(finalText)
            }
        } catch (e: TimeoutCancellationException) {
            if (flow.value is AiInsightState.LoadingModel || flow.value is AiInsightState.Generating) {
                flow.value = AiInsightState.Error("Took too long — try again.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (flow.value is AiInsightState.LoadingModel || flow.value is AiInsightState.Generating) {
                flow.value = AiInsightState.Error("Something went wrong — try again.")
            }
        }
    }

    private suspend fun generate(context: InsightContext) {
        _state.value = AiInsightState.LoadingModel
        try {
            val variant = _selectedModel.value
            val modelPath = serviceHolder.modelFileFor(variant).absolutePath
            val service = serviceHolder.getOrCreateService(modelPath)
            service.ensureInitialized()

            if (_state.value !is AiInsightState.LoadingModel) return
            _state.value = AiInsightState.Generating("")
            val prompt = promptBuilder.buildWeeklySummaryPrompt(context)

            val sb = StringBuilder()
            withTimeout(GENERATION_TIMEOUT_MS) {
                service.generateExplanation(prompt).collect { chunk ->
                    sb.append(chunk)
                    _state.value = AiInsightState.Generating(sb.toString())
                }
            }
            if (_state.value is AiInsightState.Generating) {
                val finalText = sb.toString()
                    .trim()
                    .replace(Regex("""[*_`#>]"""), "")
                    .replace(Regex("""\n{2,}"""), " ")
                    .let { InsightPromptBuilder.limitToSentences(it, 2) }
                _state.value = AiInsightState.Ready(finalText)
            }
        } catch (e: TimeoutCancellationException) {
            if (_state.value is AiInsightState.LoadingModel || _state.value is AiInsightState.Generating) {
                _state.value = AiInsightState.Error("Took too long — try again.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (_state.value is AiInsightState.LoadingModel || _state.value is AiInsightState.Generating) {
                _state.value = AiInsightState.Error("Something went wrong — try again.")
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private suspend fun initializeForCurrentVariant() {
        val variant = _selectedModel.value
        val modelFile = serviceHolder.modelFileFor(variant)
        if (modelFile.exists()) {
            _state.value = AiInsightState.ModelVerifying
            scope.launch { runIntegrityCheck(variant, fullCheck = false) }
        } else {
            val savedId = uiPreferences.pendingDownloadId.first()
            if (savedId != -1L) {
                downloadId = savedId
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                _state.value = AiInsightState.Downloading(null)
                scope.launch { pollDownloadProgress(dm, variant) }
            } else {
                _state.value = AiInsightState.ModelMissing
            }
        }
    }

    private fun hasSufficientStorage(variant: ModelVariant): Boolean {
        val modelFile = serviceHolder.modelFileFor(variant)
        val path = modelFile.parentFile
            ?.takeIf { it.exists() }?.absolutePath
            ?: context.getExternalFilesDir(null)?.absolutePath
            ?: Environment.getExternalStorageDirectory().path
        // Require model size + 1 GiB headroom for the temp download file.
        val required = variant.expectedBytes + 1_073_741_824L
        return StatFs(path).availableBytes >= required
    }

    private fun cancelActiveDownloadSilently() {
        if (downloadId == -1L) return
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.remove(downloadId)
        clearPendingDownloadId()
    }

    private fun clearPendingDownloadId() {
        downloadId = -1L
        scope.launch { uiPreferences.setPendingDownloadId(-1L) }
    }

    private companion object {
        private const val GENERATION_TIMEOUT_MS = 45_000L

        /** Richer cards that only run on the cloud backend; the local Gemma model hides them. */
        private val CLOUD_ONLY_KINDS = setOf(
            InsightKind.WEEKLY_PATTERN,
            InsightKind.TARGET_CHANGE,
            InsightKind.NOISE_DEFUSER,
            InsightKind.CROSS_METRIC,
        )
    }
}
