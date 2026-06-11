package com.zack.recomptracker.domain.review

import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import kotlin.math.abs
import kotlin.math.roundToInt

class WeeklyReviewComputer {

    fun build(
        weekStart: String,
        input: AdjustmentInput,
        result: AdjustmentResult,
        currentTargetCalories: Int,
    ): WeeklyReviewData {
        val actionable = result.verdict == AdjustmentVerdict.INCREASE_CALORIES ||
            result.verdict == AdjustmentVerdict.REDUCE_CALORIES
        val phase = if (input.daysLogged >= 14 && result.verdict != AdjustmentVerdict.WAIT_FOR_DATA) {
            BriefingPhase.FULL
        } else {
            BriefingPhase.EARLY
        }
        val applyTarget = if (phase == BriefingPhase.FULL && actionable) {
            currentTargetCalories + result.recommendedCalorieChange
        } else {
            null
        }
        return WeeklyReviewData(
            weekStart = weekStart,
            phase = phase,
            daysLogged = input.daysLogged,
            input = input,
            result = result,
            verdictLabel = verdictLabel(result.verdict),
            signals = signals(input),
            currentTargetCalories = currentTargetCalories,
            applyTargetCalories = applyTarget,
        )
    }

    /** Bucketed hash of the inputs that should trigger a regeneration when they shift. */
    fun signature(data: WeeklyReviewData): String {
        val i = data.input
        return listOf(
            data.weekStart,
            data.currentTargetCalories,
            bucket(i.weightTrendKgPerWeek, 0.05),
            bucket(i.waistTrendCmPerWeek, 0.1),
            (i.adherencePercent / 5.0).roundToInt(),
            i.performanceTrend.name,
            i.recoveryTrend.name,
            data.result.verdict.name,
            data.result.recommendedCalorieChange,
            data.phase.name,
        ).joinToString("|")
    }

    private fun bucket(value: Double, width: Double): Int = (value / width).roundToInt()

    private fun verdictLabel(v: AdjustmentVerdict): String = when (v) {
        AdjustmentVerdict.WAIT_FOR_DATA -> "Gathering data"
        AdjustmentVerdict.HOLD -> "Hold calories"
        AdjustmentVerdict.INCREASE_CALORIES -> "Increase calories"
        AdjustmentVerdict.REDUCE_CALORIES -> "Reduce calories"
    }

    private fun signals(i: AdjustmentInput): List<SignalSkeleton> = listOf(
        SignalSkeleton(
            id = "weight", label = "Weight",
            value = signed(i.weightTrendKgPerWeek, "kg/wk"),
            direction = dir(i.weightTrendKgPerWeek, 0.1),
        ),
        SignalSkeleton(
            id = "waist", label = "Waist",
            value = signed(i.waistTrendCmPerWeek, "cm/wk"),
            direction = dir(i.waistTrendCmPerWeek, 0.1),
        ),
        SignalSkeleton(
            id = "adherence", label = "Adherence",
            value = "${i.adherencePercent.roundToInt()}%",
            direction = SignalDirection.FLAT,
        ),
        SignalSkeleton(
            id = "strength", label = "Strength",
            value = i.performanceTrend.name.lowercase().replaceFirstChar { it.uppercase() },
            direction = when (i.performanceTrend) {
                PerformanceTrend.UP -> SignalDirection.UP
                PerformanceTrend.DOWN -> SignalDirection.DOWN
                else -> SignalDirection.FLAT
            },
        ),
        SignalSkeleton(
            id = "recovery", label = "Recovery",
            value = i.recoveryTrend.name.lowercase().replaceFirstChar { it.uppercase() },
            direction = when (i.recoveryTrend) {
                RecoveryTrend.GOOD -> SignalDirection.UP
                RecoveryTrend.POOR -> SignalDirection.DOWN
                else -> SignalDirection.FLAT
            },
        ),
    )

    private fun dir(value: Double, deadband: Double): SignalDirection = when {
        value > deadband -> SignalDirection.UP
        value < -deadband -> SignalDirection.DOWN
        else -> SignalDirection.FLAT
    }

    private fun signed(value: Double, unit: String): String {
        val rounded = (value * 10).roundToInt() / 10.0
        val sign = if (rounded > 0) "+" else if (rounded < 0) "−" else ""
        return "$sign${abs(rounded)} $unit"
    }
}
