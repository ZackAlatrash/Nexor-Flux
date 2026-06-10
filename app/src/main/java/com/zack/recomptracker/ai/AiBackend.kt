package com.zack.recomptracker.ai

/** Which engine powers the AI features. */
enum class AiBackend {
    LOCAL,
    CLOUD;

    companion object {
        /** Parse a stored preference name, defaulting to [LOCAL] for null/unknown values. */
        fun fromStored(name: String?): AiBackend =
            entries.firstOrNull { it.name == name } ?: LOCAL
    }
}

/**
 * What a backend is allowed to do. Coordinators and UI branch on these flags, never on
 * `backend == CLOUD` directly — so Tier-2 features (e.g. [proactiveReview]) can be switched
 * on later without re-architecting.
 */
data class AiCapabilities(
    val richInsights: Boolean,
    val longContext: Boolean,
    val unboundedToolLoop: Boolean,
    val proactiveReview: Boolean,
) {
    companion object {
        fun of(backend: AiBackend): AiCapabilities = when (backend) {
            AiBackend.LOCAL -> AiCapabilities(
                richInsights = false,
                longContext = false,
                unboundedToolLoop = false,
                proactiveReview = false,
            )
            AiBackend.CLOUD -> AiCapabilities(
                richInsights = true,
                longContext = true,
                unboundedToolLoop = true,
                proactiveReview = false, // Tier 2 — deferred to a follow-up spec.
            )
        }
    }
}
