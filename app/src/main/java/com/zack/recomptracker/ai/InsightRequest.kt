package com.zack.recomptracker.ai

enum class InsightKind {
    PROGRESS_TREND,
    RECOVERY_READINESS,
    REST_OF_DAY,
    WEEKLY_PATTERN,
    TARGET_CHANGE,
    NOISE_DEFUSER,
    CROSS_METRIC,
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

    data class RestOfDay(val context: RestOfDayInsightContext) : InsightRequest {
        override val kind = InsightKind.REST_OF_DAY
        override val hasSufficientData get() = context.hasSufficientData
        override fun dedupKey() = context.key()
    }

    data class WeeklyPattern(val context: PatternInsightContext) : InsightRequest {
        override val kind = InsightKind.WEEKLY_PATTERN
        override val hasSufficientData get() = context.hasSufficientData
        override fun dedupKey() = context.key()
    }

    data class TargetChange(val context: TargetChangeContext) : InsightRequest {
        override val kind = InsightKind.TARGET_CHANGE
        override val hasSufficientData get() = context.hasSufficientData
        override fun dedupKey() = context.key()
    }

    data class NoiseDefuser(val context: NoiseDefuserContext) : InsightRequest {
        override val kind = InsightKind.NOISE_DEFUSER
        override val hasSufficientData get() = context.hasSufficientData
        override fun dedupKey() = context.key()
    }

    data class CrossMetric(val context: CrossMetricContext) : InsightRequest {
        override val kind = InsightKind.CROSS_METRIC
        override val hasSufficientData get() = context.hasSufficientData
        override fun dedupKey() = context.key()
    }
}
