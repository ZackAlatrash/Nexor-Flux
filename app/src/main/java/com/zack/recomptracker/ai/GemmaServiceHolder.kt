// app/src/main/java/com/zack/recomptracker/ai/GemmaServiceHolder.kt
package com.zack.recomptracker.ai

import android.content.Context
import java.io.File

class GemmaServiceHolder(private val context: Context) {

    val modelFile: File
        get() = File(context.getExternalFilesDir(null), "ai/gemma-4-E2B-it.litertlm")

    private var _service: GemmaInsightService? = null

    fun getOrCreateService(): GemmaInsightService =
        _service ?: GemmaInsightService(
            modelPath = modelFile.absolutePath,
            cacheDir = context.cacheDir.absolutePath,
        ).also { _service = it }

    fun release() {
        _service?.release()
        _service = null
    }
}
