package com.zack.recomptracker.domain.insight

import java.time.DayOfWeek
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
