package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult

data class InsightContext(
    val result: AdjustmentResult,
    val input: AdjustmentInput,
    val targetCalories: Int,
    val targetProteinG: Int,
    /** Goal pace, signed kg/week (negative = loss). For the target-change rationale. */
    val desiredWeeklyRateKg: Double? = null,
    /** The calorie target before this week's verdict, if it changed. */
    val priorTargetCalories: Int? = null,
)
