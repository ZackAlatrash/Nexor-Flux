package com.zack.recomptracker.ai

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Application-scoped owner of the single [GemmaInsightService] / Engine instance.
 *
 * All methods use a coroutine [Mutex] (non-blocking, unlike @Synchronized) so callers
 * running on the IO dispatcher are never thread-blocked while waiting for the lock.
 */
class GemmaServiceHolder(context: Context) {

    // Always pin to applicationContext so an Activity context can never leak here.
    private val appContext: Context = context.applicationContext

    /**
     * Computed once; [File.getExternalFilesDir] is not free and the path never changes
     * at runtime.
     */
    val modelFile: File by lazy {
        File(appContext.getExternalFilesDir(null), "ai/gemma-4-E2B-it.litertlm")
    }

    private var _service: GemmaInsightService? = null
    private val lock = Mutex()

    /**
     * Returns the existing service or creates a fresh one. The service object is cheap
     * to create; the expensive work (loading the Engine) is deferred to
     * [GemmaInsightService.ensureInitialized].
     */
    suspend fun getOrCreateService(): GemmaInsightService = lock.withLock {
        _service ?: GemmaInsightService(
            modelPath = modelFile.absolutePath,
            cacheDir = appContext.cacheDir.absolutePath,
        ).also { _service = it }
    }

    /**
     * Closes the Engine and discards the service. Waits for any in-flight inference to
     * finish first by honouring [GemmaInsightService.inferenceLock] inside [release].
     */
    suspend fun release() = lock.withLock {
        _service?.release()
        _service = null
    }
}
