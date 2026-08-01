package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.domain.trend.MeasurementPoint
import com.zack.recomptracker.shared.format.bucket as sharedBucket
import com.zack.recomptracker.shared.format.bucketInt as sharedBucketInt
import com.zack.recomptracker.shared.format.formatFixed
import com.zack.recomptracker.shared.format.pct as sharedPct
import com.zack.recomptracker.shared.format.signed1 as sharedSigned1
import com.zack.recomptracker.shared.time.isoWeek as sharedIsoWeek
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate

/**
 * Shared, deterministic helpers for the detector catalog: number formatting for [SignalFacts] and
 * fallback strings, ISO-week stamping and value bucketing for stable [CoachSignal.dedupKey]s, and a
 * bridge from [CoachContext]'s [MetricPoint] series into the existing calculators' [MeasurementPoint]
 * type. No detector re-implements trend/adherence/streak math — it maps here and calls the real one.
 *
 * The formatting and ISO-week primitives delegate to `shared.format` / `shared.time`, which
 * reproduce the former JVM `String.format(Locale.US, …)` and `IsoFields` behaviour bit-for-bit
 * (pinned by `GoldenFormatTest`). The public surface is unchanged, so no detector needed editing.
 */
internal object CoachDetectorSupport {

    /** ISO-8601 week stamp, e.g. "2026-W27". Used in dedupKeys for weekly signals. */
    fun isoWeek(date: LocalDate): String = sharedIsoWeek(date)

    /** Format a double to [decimals] places, US locale (stable for facts + dedup buckets). */
    fun fmt(value: Double, decimals: Int = 2): String = formatFixed(value, decimals)

    /** Signed one-decimal string, e.g. "+0.4" / "-0.2" — for human-facing fallback prose. */
    fun signed1(value: Double): String = sharedSigned1(value)

    fun pct(value: Double): String = sharedPct(value)

    /**
     * Bucket a continuous value into a coarse band for a dedupKey so tiny wiggles don't churn the
     * signal identity. Buckets to the nearest [step] and formats with [decimals].
     */
    fun bucket(value: Double, step: Double, decimals: Int = 2): String =
        sharedBucket(value, step, decimals)

    fun bucketInt(value: Int, step: Int): String = sharedBucketInt(value, step)

    /** Map a [MetricPoint] series (coach context) into the calculators' [MeasurementPoint] type. */
    fun toMeasurementPoints(series: List<MetricPoint>): List<MeasurementPoint> =
        series.map { MeasurementPoint(date = it.date, value = it.value) }

    /**
     * Severity 0..100 from how far a value sits past a threshold. [distance] is the magnitude past
     * the threshold; [span] is the distance that should read as "fully severe" (100). Clamped.
     */
    fun severityFromDistance(distance: Double, span: Double): Int {
        if (span <= 0.0) return 0
        return ((abs(distance) / span) * 100.0).roundToInt().coerceIn(0, 100)
    }
}
