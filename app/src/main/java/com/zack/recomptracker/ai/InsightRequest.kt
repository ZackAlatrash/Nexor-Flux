package com.zack.recomptracker.ai

enum class InsightKind {
    PROGRESS_TREND,
    RECOVERY_READINESS,
}

sealed interface InsightRequest {
    val kind: InsightKind
    val hasSufficientData: Boolean
    fun dedupKey(): String

    data class ProgressTrend(val context: ProgressInsightContext) : InsightRequest {
        override val kind = InsightKind.PROGRESS_TREND
        override val hasSufficientData get() = context.hasSufficientData
        override fun dedupKey() = context.key()
    }

    data class RecoveryReadiness(val context: RecoveryInsightContext) : InsightRequest {
        override val kind = InsightKind.RECOVERY_READINESS
        override val hasSufficientData get() = context.hasSufficientData
        override fun dedupKey() = context.key()
    }
}
