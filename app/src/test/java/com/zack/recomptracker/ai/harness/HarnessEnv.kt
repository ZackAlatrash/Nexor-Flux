package com.zack.recomptracker.ai.harness

import com.zack.recomptracker.data.remote.CloudConfig
import java.io.File

/**
 * Local-only cloud creds for the insight harness. Loaded from `.env.test` (git-ignored)
 * or process env vars. Pure parsing lives in [parse] so it is unit-testable offline.
 */
data class HarnessEnv(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val judgeModel: String,
) {
    fun generationConfig() = CloudConfig(baseUrl = baseUrl, apiKey = apiKey, model = model)
    fun judgeConfig() = CloudConfig(baseUrl = baseUrl, apiKey = apiKey, model = judgeModel)

    companion object {
        /** Parses dotenv-style text. Returns null if any required key is missing/blank. */
        fun parse(text: String): HarnessEnv? {
            val map = HashMap<String, String>()
            text.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                val idx = line.indexOf('=')
                if (idx <= 0) return@forEach
                val key = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim().trim('"', '\'')
                if (value.isNotEmpty()) map[key] = value
            }
            val baseUrl = map["INSIGHT_BASE_URL"] ?: return null
            val apiKey = map["INSIGHT_API_KEY"] ?: return null
            val model = map["INSIGHT_MODEL"] ?: return null
            val judge = map["INSIGHT_JUDGE_MODEL"] ?: model
            return HarnessEnv(baseUrl, apiKey, model, judge)
        }

        /**
         * Resolves creds for a live run: process env vars first, else the nearest `.env.test`
         * found by walking up from the working directory (Gradle runs tests with cwd = module dir,
         * so the repo-root file is one or two levels up). Returns null when nothing is configured.
         */
        fun load(): HarnessEnv? {
            System.getenv("INSIGHT_API_KEY")?.let { key ->
                val base = System.getenv("INSIGHT_BASE_URL")
                val model = System.getenv("INSIGHT_MODEL")
                if (base != null && model != null) {
                    return HarnessEnv(base, key, model, System.getenv("INSIGHT_JUDGE_MODEL") ?: model)
                }
            }
            var dir: File? = File(System.getProperty("user.dir"))
            repeat(4) {
                val candidate = dir?.resolve(".env.test")
                if (candidate != null && candidate.isFile) return parse(candidate.readText())
                dir = dir?.parentFile
            }
            return null
        }
    }
}
