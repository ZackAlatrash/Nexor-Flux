package com.zack.recomptracker.domain.plan

/** The plan fields used to JUDGE a single day (calories, macros, calorie zone). Pure. */
data class PlanTargets(
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val zoneLowerBound: Int,
    val zoneUpperBound: Int,
)
