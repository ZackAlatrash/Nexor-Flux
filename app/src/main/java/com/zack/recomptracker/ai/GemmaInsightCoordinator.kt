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

    @Volatile private var lastGeneratedKey: String? = null
    @Volatile private var downloadId: Long = -1L

    private val modelUrl =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    private val requiredFreeBytes = 3L * 1024 * 1024 * 1024

    init {
        // Observe AI-enabled toggle
        scope.launch {
            aiEnabledFlow.collect { enabled ->
                if (!enabled) {
                    _state.value = AiInsightState.Disabled
                } else if (_state.value == AiInsightState.Disabled) {
                    if (serviceHolder.modelFile.exists()) {
                        _state.value = AiInsightState.ModelVerifying
                        scope.launch { runIntegrityCheck(fullCheck = false) }
                    } else {
                        // Attempt to resume a download that survived a previous process death.
                        val savedId = uiPreferences.pendingDownloadId.first()
                        if (savedId != -1L) {
                            downloadId = savedId
                            val dm =
                                context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                            _state.value = AiInsightState.Downloading(null)
                            scope.launch { pollDownloadProgress(dm) }
                        } else {
                            _state.value = AiInsightState.ModelMissing
                        }
                    }
                }
            }
        }
    }

    override fun requestDownload() {
        if (_state.value != AiInsightState.ModelMissing && _state.value != AiInsightState.DownloadFailed) return
        if (!hasSufficientStorage()) {
            _state.value = AiInsightState.DownloadFailed
            return
        }
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
        // Persist the ID so process-death recovery works.
        scope.launch {
            uiPreferences.setPendingDownloadId(downloadId)
            pollDownloadProgress(dm)
        }
    }

    private suspend fun pollDownloadProgress(dm: DownloadManager) {
        while (true) {
            delay(1000L)
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = withContext(Dispatchers.IO) { dm.query(query) }
            if (!cursor.moveToFirst()) {
                cursor.close()
                break
            }
            val status =
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val bytesDownloaded =
                cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val bytesTotal =
                cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            cursor.close()

            when (status) {
                DownloadManager.STATUS_RUNNING -> {
                    val progress =
                        if (bytesTotal > 0) bytesDownloaded.toFloat() / bytesTotal.toFloat()
                        else null
                    _state.value = AiInsightState.Downloading(progress)
                }

                DownloadManager.STATUS_SUCCESSFUL -> {
                    clearPendingDownloadId()
                    _state.value = AiInsightState.ModelVerifying
                    scope.launch { runIntegrityCheck() }
                    break
                }

                DownloadManager.STATUS_FAILED -> {
                    clearPendingDownloadId()
                    _state.value = AiInsightState.DownloadFailed
                    break
                }
            }
        }
    }

    /**
     * [fullCheck] = true (post-download): verifies size AND SHA-256.
     * [fullCheck] = false (launch): verifies size only — avoids a 10–30 s hash on every cold start.
     */
    private suspend fun runIntegrityCheck(fullCheck: Boolean = true) {
        val passed = withContext(Dispatchers.IO) { verifyModel(fullCheck) }
        if (_state.value != AiInsightState.ModelVerifying) return
        if (passed) {
            _state.value = AiInsightState.ModelReady
        } else {
            // Delete the corrupt/partial file and let the user retry.
            serviceHolder.modelFile.delete()
            _state.value = AiInsightState.DownloadFailed
        }
    }

    /**
     * Returns true only if the model file exists, has the expected byte count, and its
     * SHA-256 matches the digest pinned from the litert-community HF repo (verified
     * 2026-06-06 against revision main, filename gemma-4-E2B-it.litertlm).
     *
     * Refresh these constants whenever the model file is updated upstream.
     */
    private fun verifyModel(fullCheck: Boolean = true): Boolean {
        val file = serviceHolder.modelFile
        if (!file.exists() || file.length() != MODEL_EXPECTED_BYTES) return false
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
            hex == MODEL_SHA256
        } catch (_: Exception) {
            false
        }
    }

    override fun cancelDownload() {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.remove(downloadId)
        downloadId = -1L
        clearPendingDownloadId()
        _state.value = AiInsightState.ModelMissing
    }

    override fun deleteModel() {
        lastGeneratedKey = null
        serviceHolder.modelFile.delete()
        // release() acquires inferenceLock, so it waits for any in-flight generation.
        scope.launch { serviceHolder.release() }
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
        if (context.result.verdict == AdjustmentVerdict.WAIT_FOR_DATA) return
        if (_state.value !is AiInsightState.Ready && _state.value != AiInsightState.ModelReady && _state.value !is AiInsightState.Error) return
        lastGeneratedKey = null
        _state.value = AiInsightState.ModelReady
        onAiCardVisible(context)
    }

    private val promptBuilder = InsightPromptBuilder()

    private suspend fun generate(context: InsightContext) {
        _state.value = AiInsightState.LoadingModel
        try {
            val service = serviceHolder.getOrCreateService()
            service.ensureInitialized()

            if (_state.value !is AiInsightState.LoadingModel) return
            _state.value = AiInsightState.Generating("")
            val prompt = promptBuilder.buildWeeklySummaryPrompt(context)

            val sb = StringBuilder()
            withTimeout(GENERATION_TIMEOUT_MS) {
                // collect is itself a suspension point and participates in cancellation;
                // no ensureActive() needed here.
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
                _state.value = AiInsightState.Ready(finalText)
            }
        } catch (e: TimeoutCancellationException) {
            if (_state.value is AiInsightState.LoadingModel || _state.value is AiInsightState.Generating) {
                _state.value = AiInsightState.Error("Took too long — try again.")
            }
        } catch (e: CancellationException) {
            // Genuine scope cancellation — do not swallow; let it propagate.
            throw e
        } catch (e: Exception) {
            if (_state.value is AiInsightState.LoadingModel || _state.value is AiInsightState.Generating) {
                _state.value = AiInsightState.Error("Something went wrong — try again.")
            }
        }
    }

    private fun hasSufficientStorage(): Boolean {
        val path = serviceHolder.modelFile.parentFile
            ?.takeIf { it.exists() }?.absolutePath
            ?: context.getExternalFilesDir(null)?.absolutePath
            ?: Environment.getExternalStorageDirectory().path
        return StatFs(path).availableBytes >= requiredFreeBytes
    }

    private fun clearPendingDownloadId() {
        downloadId = -1L
        scope.launch { uiPreferences.setPendingDownloadId(-1L) }
    }

    private companion object {
        private const val GENERATION_TIMEOUT_MS = 45_000L

        /** Pinned from litert-community/gemma-4-E2B-it-litert-lm, verified 2026-06-06. */
        private const val MODEL_EXPECTED_BYTES = 2_588_147_712L
        private const val MODEL_SHA256 =
            "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
    }
}
