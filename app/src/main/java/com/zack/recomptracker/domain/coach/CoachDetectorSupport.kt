package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.domain.trend.MeasurementPoint
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.datetime.toKotlinLocalDate

/**
 * Shared, deterministic helpers for the detector catalog: number formatting for [SignalFacts] and
 * fallback strings, ISO-week stamping and value bucketing for stable [CoachSignal.dedupKey]s, and a
 * bridge from [CoachContext]'s [MetricPoint] series into the existing calculators' [MeasurementPoint]
 * type. No detector re-implements trend/adherence/streak math — it maps here and calls the real one.
 */
internal object CoachDetectorSupport {

    /** ISO-8601 week stamp, e.g. "2026-W27". Used in dedupKeys for weekly signals. */
    fun isoWeek(date: LocalDate): String {
        val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val year = date.get(IsoFields.WEEK_BASED_YEAR)
        return String.format(Locale.US, "%04d-W%02d", year, week)
    }

    /** Format a double to [decimals] places, US locale (stable for facts + dedup buckets). */
    fun fmt(value: Double, decimals: Int = 2): String =
        String.format(Locale.US, "%.${decimals}f", value)

    /** Signed one-decimal string, e.g. "+0.4" / "-0.2" — for human-facing fallback prose. */
    fun signed1(value: Double): String {
        val s = String.format(Locale.US, "%+.1f", value)
        return if (s == "+0.0" || s == "-0.0") "0.0" else s
    }

    fun pct(value: Double): String = "${value.roundToInt()}%"

    /**
     * Bucket a continuous value into a coarse band for a dedupKey so tiny wiggles don't churn the
     * signal identity. Buckets to the nearest [step] and formats with [decimals].
     */
    fun bucket(value: Double, step: Double, decimals: Int = 2): String {
        val bucketed = (value / step).roundToInt() * step
        // Normalise -0.0 → 0.0 so the key is stable across signs of zero.
        val safe = if (bucketed == 0.0) 0.0 else bucketed
        return fmt(safe, decimals)
    }

    fun bucketInt(value: Int, step: Int): String {
        val safeStep = if (step <= 0) 1 else step
        return ((value.toDouble() / safeStep).roundToInt() * safeStep).toString()
    }

    /** Map a [MetricPoint] series (coach context) into the calculators' [MeasurementPoint] type. */
    fun toMeasurementPoints(series: List<MetricPoint>): List<MeasurementPoint> =
        series.map { MeasurementPoint(date = it.date.toKotlinLocalDate(), value = it.value) }

    /**
     * Severity 0..100 from how far a value sits past a threshold. [distance] is the magnitude past
     * the threshold; [span] is the distance that should read as "fully severe" (100). Clamped.
     */
    fun severityFromDistance(distance: Double, span: Double): Int {
        if (span <= 0.0) return 0
        return ((abs(distance) / span) * 100.0).roundToInt().coerceIn(0, 100)
    }
}
