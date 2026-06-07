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

    private var _service: GemmaInsightService? = null
    private val lock = Mutex()

    /** Returns the file path for [variant]'s model on external storage. */
    fun modelFileFor(variant: ModelVariant): File =
        File(appContext.getExternalFilesDir(null), "ai/${variant.fileName}")

    /**
     * Returns the existing service or creates a fresh one with [modelPath].
     * The service object is cheap to create; the expensive work (loading the Engine)
     * is deferred to [GemmaInsightService.ensureInitialized].
     */
    suspend fun getOrCreateService(modelPath: String): GemmaInsightService = lock.withLock {
        _service ?: GemmaInsightService(
            modelPath = modelPath,
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
