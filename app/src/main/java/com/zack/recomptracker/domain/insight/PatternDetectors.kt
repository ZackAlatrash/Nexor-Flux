package com.zack.recomptracker.domain.insight

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val WEEKDAY_WEEKEND_MIN_GAP = 250.0

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

internal fun detectDerailmentDay(days: List<DayNutrition>, targets: NutritionTargets): InsightFact? {
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
                it.first.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
            }
            val statement = "$label drove $sharePct% of this week's calorie surplus."
            val priority = 30 + ((sharePct - DERAILMENT_MIN_SHARE_PCT) / 5).coerceIn(0, 15)
            return InsightFact(InsightFactType.DERAILMENT_DAY, priority, statement)
        }
    }
    return null
}
