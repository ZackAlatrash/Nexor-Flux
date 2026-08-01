package com.zack.recomptracker.domain.insight

import com.zack.recomptracker.shared.time.fullNameEnglish
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.datetime.DayOfWeek

private const val WEEKDAY_WEEKEND_MIN_GAP = 200.0

internal fun detectWeekdayWeekend(days: List<DayNutrition>, targets: NutritionTargets): InsightFact? {
    val logged = days.filter { it.logged }
    val weekend = logged.filter { it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY }
    val weekday = logged.filter { it.date.dayOfWeek != DayOfWeek.SATURDAY && it.date.dayOfWeek != DayOfWeek.SUNDAY }
    if (weekday.size < 3 || weekend.size < 2) return null
    val weekendMean = weekend.map { it.calories }.average()
    val weekdayMean = weekday.map { it.calories }.average()
    val gap = weekendMean - weekdayMean
    if (abs(gap) < WEEKDAY_WEEKEND_MIN_GAP) return null
    val gapInt = gap.roundToInt()
    val signed = if (gapInt >= 0) "+$gapInt" else "$gapInt"
    val statement =
        "Your weekend days average ${weekendMean.roundToInt()} kcal vs ${weekdayMean.roundToInt()} on weekdays ($signed kcal)."
    val priority = 20 + ((abs(gap) - WEEKDAY_WEEKEND_MIN_GAP) / 50).toInt().coerceIn(0, 15)
    return InsightFact(InsightFactType.WEEKDAY_WEEKEND, priority, statement)
}

private const val DERAILMENT_MIN_WEEKLY_SURPLUS = 700
private const val DERAILMENT_MIN_SHARE_PCT = 60

/**
 * Public (unlike its three siblings) because `DerailmentDayDetector` in the app module's
 * proactive-coach spine reuses this exact fact. `internal` is module-scoped, and this file now
 * lives in `:shared`.
 */
fun detectDerailmentDay(days: List<DayNutrition>, targets: NutritionTargets): InsightFact? {
    val recent = days.filter { it.logged }.sortedBy { it.date }.takeLast(7)
    if (recent.size < 4) return null
    val surpluses = recent.map { it to (it.calories - targets.calories).coerceAtLeast(0) }
    val weeklySurplus = surpluses.sumOf { it.second }
    if (weeklySurplus < DERAILMENT_MIN_WEEKLY_SURPLUS) return null
    val sorted = surpluses.sortedByDescending { it.second }
    for (n in 1..2) {
        val top = sorted.take(n)
        if (top.any { it.second == 0 }) continue
        val sharePct = (top.sumOf { it.second }.toDouble() / weeklySurplus * 100).roundToInt()
        if (sharePct >= DERAILMENT_MIN_SHARE_PCT) {
            val label = top.joinToString(" and ") {
                it.first.date.dayOfWeek.fullNameEnglish()
            }
            val statement = "$label drove $sharePct% of this week's calorie surplus."
            val priority = 30 + ((sharePct - DERAILMENT_MIN_SHARE_PCT) / 5).coerceIn(0, 15)
            return InsightFact(InsightFactType.DERAILMENT_DAY, priority, statement)
        }
    }
    return null
}

private const val WEAKEST_MACRO_MAX_PCT = 85

internal fun detectWeakestMacro(days: List<DayNutrition>, targets: NutritionTargets): InsightFact? {
    val logged = days.filter { it.logged }
    if (logged.size < 4) return null

    // rank 0 = protein wins ties (most actionable lever).
    data class Macro(val name: String, val pct: Int, val rank: Int)
    val macros = listOf(
        Macro("protein", attainPct(logged.map { it.proteinG }, targets.proteinG), 0),
        Macro("carbs", attainPct(logged.map { it.carbsG }, targets.carbsG), 1),
        Macro("fat", attainPct(logged.map { it.fatG }, targets.fatG), 2),
    )
    val weakest = macros.minWithOrNull(compareBy({ it.pct }, { it.rank })) ?: return null
    if (weakest.pct >= WEAKEST_MACRO_MAX_PCT) return null

    // A lagging macro is worth surfacing regardless of calorie level. Only claim "calories are on
    // point" when the average genuinely sits in the zone; otherwise use neutral wording so we never
    // tell someone eating well under/over target that their calories are fine.
    val meanCalories = logged.map { it.calories }.average()
    val caloriesOnPoint = meanCalories >= targets.calorieZoneLower && meanCalories <= targets.calorieZoneUpper
    val statement = if (caloriesOnPoint) {
        "Calories are on point, but ${weakest.name} is averaging ${weakest.pct}% of target — your main gap."
    } else {
        "${weakest.name.replaceFirstChar { it.uppercase() }} is averaging ${weakest.pct}% of target — your weakest macro this stretch."
    }
    val priority = 30 + ((WEAKEST_MACRO_MAX_PCT - weakest.pct) / 5).coerceIn(0, 15)
    return InsightFact(InsightFactType.WEAKEST_MACRO, priority, statement)
}

private fun attainPct(values: List<Double>, target: Int): Int {
    if (target <= 0) return 100
    return (values.average() / target * 100).roundToInt()
}

private const val STREAK_MIN_DAYS = 3

internal fun detectStreak(days: List<DayNutrition>, targets: NutritionTargets): InsightFact? {
    if (targets.proteinG <= 0) return null
    val ordered = days.sortedByDescending { it.date } // most recent first
    // Allow a not-yet-logged most-recent day (e.g. today) to not break the streak.
    val startIndex = if (ordered.isNotEmpty() && !ordered[0].logged) 1 else 0
    var streak = 0
    for (i in startIndex until ordered.size) {
        val d = ordered[i]
        if (d.logged && d.proteinG >= targets.proteinG) streak++ else break
    }
    if (streak < STREAK_MIN_DAYS) return null
    val statement = "$streak days running at your protein target — keep it going."
    val priority = 10 + ((streak - STREAK_MIN_DAYS) * 4).coerceAtMost(25)
    return InsightFact(InsightFactType.STREAK, priority, statement)
}
