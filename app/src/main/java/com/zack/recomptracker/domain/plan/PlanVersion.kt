package com.zack.recomptracker.domain.plan

import java.time.LocalDate

/** A plan version that takes effect on [effectiveFrom] (inclusive) for all later days. */
data class PlanVersion(
    val effectiveFrom: LocalDate,
    val targets: PlanTargets,
)
