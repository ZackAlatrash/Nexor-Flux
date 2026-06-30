package com.zack.recomptracker.domain.plan

import java.time.LocalDate

/**
 * Pure resolver for "what plan was in effect on date X". The single source of truth for
 * historical target judgments — every consumer routes day/range judgments through here so a
 * plan change only affects today forward and never re-judges already-logged days.
 */
object PlanHistory {

    /** Sentinel effective date for the seeded baseline version (covers all pre-existing days). */
    val BASELINE_DATE: LocalDate = LocalDate.parse("1970-01-01")

    /**
     * The targets in effect on [date]: the version with the greatest effectiveFrom <= date.
     * If [date] precedes the earliest version, clamps to the earliest version. Throws if
     * [versions] is empty — callers must guarantee a baseline exists (see PlanHistoryInitializer).
     */
    fun planOn(versions: List<PlanVersion>, date: LocalDate): PlanTargets {
        require(versions.isNotEmpty()) { "planOn requires at least one version" }
        val sorted = versions.sortedBy { it.effectiveFrom }
        val match = sorted.lastOrNull { !it.effectiveFrom.isAfter(date) } ?: sorted.first()
        return match.targets
    }

    /** Resolve many dates at once (sorts versions once). */
    fun resolve(versions: List<PlanVersion>, dates: List<LocalDate>): Map<LocalDate, PlanTargets> {
        if (versions.isEmpty()) return emptyMap()
        val sorted = versions.sortedBy { it.effectiveFrom }
        return dates.associateWith { date ->
            (sorted.lastOrNull { !it.effectiveFrom.isAfter(date) } ?: sorted.first()).targets
        }
    }

    /** True when any day-judging field differs (calories, macros, or zone bounds). */
    fun targetsChanged(old: PlanTargets, new: PlanTargets): Boolean = old != new
}
