package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult

data class InsightContext(
    val result: AdjustmentResult,
    val input: AdjustmentInput,
    val targetCalories: Int,
    val targetProteinG: Int,
)
