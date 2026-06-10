package com.zack.recomptracker.ui.today

import com.zack.recomptracker.ai.RecoveryInsightContext
import com.zack.recomptracker.data.local.entity.DailyLogEntity

/**
 * Pure mapper: builds a [RecoveryInsightContext] from the PERSISTED daily log (not the editable
 * form fields, which default scores to 5). Returns null when there is no log for the day. When a
 * log exists but holds no recovery signals, the resulting context's `hasSufficientData` is false
 * so the card stays hidden.
 */
fun buildRecoveryInsightContext(log: DailyLogEntity?): RecoveryInsightContext? {
    if (log == null) return null
    return RecoveryInsightContext(
        sleepHours = log.sleepHours,
        energyScore = log.energyScore,
        hungerScore = log.hungerScore,
        sorenessScore = log.sorenessScore,
        trained = log.trained,
    )
}
