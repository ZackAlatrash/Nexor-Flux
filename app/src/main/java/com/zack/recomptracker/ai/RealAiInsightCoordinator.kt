package com.zack.recomptracker.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
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
import java.io.File

class RealAiInsightCoordinator(
    private val context: Context,
    private val aiEnabledFlow: Flow<Boolean>,
    private val scope: CoroutineScope,
) : AiInsightCoordinator {

    private val _state = MutableStateFlow<AiInsightState>(AiInsightState.Disabled)
    override val state: StateFlow<AiInsightState> = _state.asStateFlow()

    private var lastGeneratedKey: String? = null
    private var downloadId: Long = -1L
    private val modelFile: File
        get() = File(context.filesDir, "ai/gemma-4-E2B-it.litertlm")

    private val modelUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    private val requiredFreeBytes = 3L * 1024 * 1024 * 1024

    init {
        scope.launch {
            aiEnabledFlow.collect { enabled ->
                if (!enabled) {
                    _state.value = AiInsightState.Disabled
                } else if (_state.value == AiInsightState.Disabled) {
                    _state.value = if (modelFile.exists()) AiInsightState.ModelReady
                    else AiInsightState.ModelMissing
                }
            }
        }
    }

    override fun requestDownload() {
        if (_state.value != AiInsightState.ModelMissing) return
        if (!hasSufficientStorage()) {
            _state.value = AiInsightState.DownloadFailed
            return
        }
        modelFile.parentFile?.mkdirs()
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(modelUrl))
            .setTitle("Gemma 4 E2B — AI Explanation Model")
            .setDescription("Downloading for on-device AI features (~2.6 GB)")
            .setDestinationUri(Uri.fromFile(modelFile))
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
                DownloadManager.STATUS_SUCCESSFUL -> {
                    _state.value = AiInsightState.ModelReady
                    break
                }
                DownloadManager.STATUS_FAILED -> {
                    _state.value = AiInsightState.DownloadFailed
                    break
                }
            }
        }
    }

    override fun cancelDownload() {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.remove(downloadId)
        downloadId = -1L
        _state.value = AiInsightState.ModelMissing
    }

    override fun deleteModel() {
        lastGeneratedKey = null
        modelFile.delete()
        _state.value = AiInsightState.ModelMissing
    }

    override fun onAiCardVisible(result: AdjustmentResult) {
        if (result.verdict == AdjustmentVerdict.WAIT_FOR_DATA) return
        if (_state.value != AiInsightState.ModelReady) return
        val key = result.key()
        if (key == lastGeneratedKey) return
        lastGeneratedKey = key
        scope.launch { generate(result) }
    }

    override fun retryGeneration(result: AdjustmentResult) {
        if (_state.value !is AiInsightState.Ready && _state.value != AiInsightState.ModelReady) return
        lastGeneratedKey = null
        _state.value = AiInsightState.ModelReady
        onAiCardVisible(result)
    }

    // Inference implemented in Task 12 — will be replaced with GemmaInsightService call.
    private suspend fun generate(result: AdjustmentResult) {
        _state.value = AiInsightState.Error("Inference not yet implemented — wired in Task 12.")
    }

    private fun hasSufficientStorage(): Boolean {
        val stat = StatFs(Environment.getDataDirectory().path)
        return stat.availableBytes >= requiredFreeBytes
    }
}
