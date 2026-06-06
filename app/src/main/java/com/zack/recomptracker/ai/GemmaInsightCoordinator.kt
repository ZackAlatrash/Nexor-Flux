package com.zack.recomptracker.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GemmaInsightCoordinator(
    private val context: Context,
    private val aiEnabledFlow: Flow<Boolean>,
    private val scope: CoroutineScope,
    private val serviceHolder: GemmaServiceHolder,
) : AiInsightCoordinator {

    private val _state = MutableStateFlow<AiInsightState>(AiInsightState.Disabled)
    override val state: StateFlow<AiInsightState> = _state.asStateFlow()

    private var lastGeneratedKey: String? = null
    private var downloadId: Long = -1L

    private val modelUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    private val requiredFreeBytes = 3L * 1024 * 1024 * 1024

    init {
        scope.launch {
            aiEnabledFlow.collect { enabled ->
                if (!enabled) {
                    _state.value = AiInsightState.Disabled
                } else if (_state.value == AiInsightState.Disabled) {
                    _state.value = if (serviceHolder.modelFile.exists()) AiInsightState.ModelReady
                    else AiInsightState.ModelMissing
                }
            }
        }
    }

    override fun requestDownload() {
        if (_state.value != AiInsightState.ModelMissing) return
        if (!hasSufficientStorage()) { _state.value = AiInsightState.DownloadFailed; return }
        serviceHolder.modelFile.parentFile?.mkdirs()
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(modelUrl))
            .setTitle("Gemma 4 E2B — AI Explanation Model")
            .setDescription("Downloading for on-device AI features (~2.6 GB)")
            .setDestinationInExternalFilesDir(context, null, "ai/gemma-4-E2B-it.litertlm")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverRoaming(false)
        downloadId = dm.enqueue(request)
        _state.value = AiInsightState.Downloading(null)
        scope.launch { pollDownloadProgress(dm) }
    }

    private suspend fun pollDownloadProgress(dm: DownloadManager) {
        while (true) {
            delay(1000L)
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
                DownloadManager.STATUS_SUCCESSFUL -> { _state.value = AiInsightState.ModelReady; break }
                DownloadManager.STATUS_FAILED -> { _state.value = AiInsightState.DownloadFailed; break }
            }
        }
    }

    override fun cancelDownload() {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.remove(downloadId); downloadId = -1L
        _state.value = AiInsightState.ModelMissing
    }

    override fun deleteModel() {
        lastGeneratedKey = null
        serviceHolder.modelFile.delete()
        serviceHolder.release()
        _state.value = AiInsightState.ModelMissing
    }

    override fun onAiCardVisible(context: InsightContext) {
        if (context.result.verdict == AdjustmentVerdict.WAIT_FOR_DATA) return
        if (_state.value != AiInsightState.ModelReady) return
        val key = context.result.key()
        if (key == lastGeneratedKey) return
        lastGeneratedKey = key
        scope.launch { generate(context) }
    }

    override fun retryGeneration(context: InsightContext) {
        if (_state.value !is AiInsightState.Ready && _state.value != AiInsightState.ModelReady) return
        lastGeneratedKey = null
        _state.value = AiInsightState.ModelReady
        onAiCardVisible(context)
    }

    private val promptBuilder = InsightPromptBuilder()

    private suspend fun generate(context: InsightContext) {
        _state.value = AiInsightState.LoadingModel
        try {
            val service = serviceHolder.getOrCreateService()
            withContext(Dispatchers.IO) { service.initialize() }
            _state.value = AiInsightState.Generating("")
            val prompt = promptBuilder.buildWeeklySummaryPrompt(context)
            val text = withContext(Dispatchers.IO) { service.generateExplanation(prompt) }
            val words = text.trim().split(" ")
            val sb = StringBuilder()
            for (word in words) {
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append(word)
                _state.value = AiInsightState.Generating(sb.toString())
            }
            _state.value = AiInsightState.Ready(sb.toString())
        } catch (e: Exception) {
            _state.value = AiInsightState.Error("Something went wrong — try again.")
        }
    }

    private fun hasSufficientStorage(): Boolean {
        val stat = StatFs(Environment.getDataDirectory().path)
        return stat.availableBytes >= requiredFreeBytes
    }
}
