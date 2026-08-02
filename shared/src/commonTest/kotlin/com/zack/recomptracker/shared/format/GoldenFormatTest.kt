package com.zack.recomptracker.shared.format

import com.zack.recomptracker.domain.coach.CoachDetectorSupport
import com.zack.recomptracker.shared.time.isoWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every row of `docs/ios-port/phases/phase-0-golden-corpus.txt`, which is the captured stdout of
 * the JVM implementation in `domain/coach/CoachDetectorSupport.kt`. The corpus is the contract:
 * if an assertion here fails, the common-Kotlin reimplementation is wrong, not the corpus.
 *
 * This test runs on BOTH the JVM (`:shared:testDebugUnitTest`) and Kotlin/Native
 * (`:shared:iosSimulatorArm64Test`).
 */
class GoldenFormatTest {

    /** Corpus section `=== SIGNED1 ===` (35 rows). */
    @Test
    fun signed1MatchesJvmGolden() {
        assertEquals("0.0", signed1(0.0))
        assertEquals("0.0", signed1(-0.0))
        assertEquals("+1.0", signed1(1.0))
        assertEquals("-1.0", signed1(-1.0))
        assertEquals("+0.5", signed1(0.5))
        assertEquals("-0.5", signed1(-0.5))
        assertEquals("+0.1", signed1(0.05))
        assertEquals("-0.1", signed1(-0.05))
        assertEquals("+0.2", signed1(0.15))
        assertEquals("-0.2", signed1(-0.15))
        assertEquals("+0.3", signed1(0.25))
        assertEquals("-0.3", signed1(-0.25))
        assertEquals("+0.1", signed1(0.145))
        assertEquals("-0.1", signed1(-0.145))
        assertEquals("+1.0", signed1(1.005))
        assertEquals("-1.0", signed1(-1.005))
        assertEquals("+2.7", signed1(2.675))
        assertEquals("-2.7", signed1(-2.675))
        assertEquals("+1.0", signed1(0.999))
        assertEquals("-1.0", signed1(-0.999))
        assertEquals("+12.3", signed1(12.34))
        assertEquals("-12.3", signed1(-12.34))
        assertEquals("+100.0", signed1(99.995))
        assertEquals("-100.0", signed1(-99.995))
        assertEquals("+1234.6", signed1(1234.5678))
        assertEquals("-1234.6", signed1(-1234.5678))
        assertEquals("0.0", signed1(0.049))
        assertEquals("0.0", signed1(-0.049))
        assertEquals("+0.1", signed1(0.051))
        assertEquals("-0.1", signed1(-0.051))
        assertEquals("+7.0", signed1(7.0))
        assertEquals("-7.0", signed1(-7.0))
        assertEquals("+100.0", signed1(100.0))
        assertEquals("0.0", signed1(0.001))
        assertEquals("0.0", signed1(-0.001))
    }

    /** Corpus sections `=== FMT0 ===`, `=== FMT1 ===`, `=== FMT2 ===` (105 rows). */
    @Test
    fun fixedDecimalMatchesJvmGolden() {
        // FMT0
        assertEquals("0", formatFixed(0.0, 0))
        assertEquals("-0", formatFixed(-0.0, 0))
        assertEquals("1", formatFixed(1.0, 0))
        assertEquals("-1", formatFixed(-1.0, 0))
        assertEquals("1", formatFixed(0.5, 0))
        assertEquals("-1", formatFixed(-0.5, 0))
        assertEquals("0", formatFixed(0.05, 0))
        assertEquals("-0", formatFixed(-0.05, 0))
        assertEquals("0", formatFixed(0.15, 0))
        assertEquals("-0", formatFixed(-0.15, 0))
        assertEquals("0", formatFixed(0.25, 0))
        assertEquals("-0", formatFixed(-0.25, 0))
        assertEquals("0", formatFixed(0.145, 0))
        assertEquals("-0", formatFixed(-0.145, 0))
        assertEquals("1", formatFixed(1.005, 0))
        assertEquals("-1", formatFixed(-1.005, 0))
        assertEquals("3", formatFixed(2.675, 0))
        assertEquals("-3", formatFixed(-2.675, 0))
        assertEquals("1", formatFixed(0.999, 0))
        assertEquals("-1", formatFixed(-0.999, 0))
        assertEquals("12", formatFixed(12.34, 0))
        assertEquals("-12", formatFixed(-12.34, 0))
        assertEquals("100", formatFixed(99.995, 0))
        assertEquals("-100", formatFixed(-99.995, 0))
        assertEquals("1235", formatFixed(1234.5678, 0))
        assertEquals("-1235", formatFixed(-1234.5678, 0))
        assertEquals("0", formatFixed(0.049, 0))
        assertEquals("-0", formatFixed(-0.049, 0))
        assertEquals("0", formatFixed(0.051, 0))
        assertEquals("-0", formatFixed(-0.051, 0))
        assertEquals("7", formatFixed(7.0, 0))
        assertEquals("-7", formatFixed(-7.0, 0))
        assertEquals("100", formatFixed(100.0, 0))
        assertEquals("0", formatFixed(0.001, 0))
        assertEquals("-0", formatFixed(-0.001, 0))
        // FMT1
        assertEquals("0.0", formatFixed(0.0, 1))
        assertEquals("-0.0", formatFixed(-0.0, 1))
        assertEquals("1.0", formatFixed(1.0, 1))
        assertEquals("-1.0", formatFixed(-1.0, 1))
        assertEquals("0.5", formatFixed(0.5, 1))
        assertEquals("-0.5", formatFixed(-0.5, 1))
        assertEquals("0.1", formatFixed(0.05, 1))
        assertEquals("-0.1", formatFixed(-0.05, 1))
        assertEquals("0.2", formatFixed(0.15, 1))
        assertEquals("-0.2", formatFixed(-0.15, 1))
        assertEquals("0.3", formatFixed(0.25, 1))
        assertEquals("-0.3", formatFixed(-0.25, 1))
        assertEquals("0.1", formatFixed(0.145, 1))
        assertEquals("-0.1", formatFixed(-0.145, 1))
        assertEquals("1.0", formatFixed(1.005, 1))
        assertEquals("-1.0", formatFixed(-1.005, 1))
        assertEquals("2.7", formatFixed(2.675, 1))
        assertEquals("-2.7", formatFixed(-2.675, 1))
        assertEquals("1.0", formatFixed(0.999, 1))
        assertEquals("-1.0", formatFixed(-0.999, 1))
        assertEquals("12.3", formatFixed(12.34, 1))
        assertEquals("-12.3", formatFixed(-12.34, 1))
        assertEquals("100.0", formatFixed(99.995, 1))
        assertEquals("-100.0", formatFixed(-99.995, 1))
        assertEquals("1234.6", formatFixed(1234.5678, 1))
        assertEquals("-1234.6", formatFixed(-1234.5678, 1))
        assertEquals("0.0", formatFixed(0.049, 1))
        assertEquals("-0.0", formatFixed(-0.049, 1))
        assertEquals("0.1", formatFixed(0.051, 1))
        assertEquals("-0.1", formatFixed(-0.051, 1))
        assertEquals("7.0", formatFixed(7.0, 1))
        assertEquals("-7.0", formatFixed(-7.0, 1))
        assertEquals("100.0", formatFixed(100.0, 1))
        assertEquals("0.0", formatFixed(0.001, 1))
        assertEquals("-0.0", formatFixed(-0.001, 1))
        // FMT2
        assertEquals("0.00", formatFixed(0.0, 2))
        assertEquals("-0.00", formatFixed(-0.0, 2))
        assertEquals("1.00", formatFixed(1.0, 2))
        assertEquals("-1.00", formatFixed(-1.0, 2))
        assertEquals("0.50", formatFixed(0.5, 2))
        assertEquals("-0.50", formatFixed(-0.5, 2))
        assertEquals("0.05", formatFixed(0.05, 2))
        assertEquals("-0.05", formatFixed(-0.05, 2))
        assertEquals("0.15", formatFixed(0.15, 2))
        assertEquals("-0.15", formatFixed(-0.15, 2))
        assertEquals("0.25", formatFixed(0.25, 2))
        assertEquals("-0.25", formatFixed(-0.25, 2))
        assertEquals("0.15", formatFixed(0.145, 2))
        assertEquals("-0.15", formatFixed(-0.145, 2))
        assertEquals("1.01", formatFixed(1.005, 2))
        assertEquals("-1.01", formatFixed(-1.005, 2))
        assertEquals("2.68", formatFixed(2.675, 2))
        assertEquals("-2.68", formatFixed(-2.675, 2))
        assertEquals("1.00", formatFixed(0.999, 2))
        assertEquals("-1.00", formatFixed(-0.999, 2))
        assertEquals("12.34", formatFixed(12.34, 2))
        assertEquals("-12.34", formatFixed(-12.34, 2))
        assertEquals("100.00", formatFixed(99.995, 2))
        assertEquals("-100.00", formatFixed(-99.995, 2))
        assertEquals("1234.57", formatFixed(1234.5678, 2))
        assertEquals("-1234.57", formatFixed(-1234.5678, 2))
        assertEquals("0.05", formatFixed(0.049, 2))
        assertEquals("-0.05", formatFixed(-0.049, 2))
        assertEquals("0.05", formatFixed(0.051, 2))
        assertEquals("-0.05", formatFixed(-0.051, 2))
        assertEquals("7.00", formatFixed(7.0, 2))
        assertEquals("-7.00", formatFixed(-7.0, 2))
        assertEquals("100.00", formatFixed(100.0, 2))
        assertEquals("0.00", formatFixed(0.001, 2))
        assertEquals("-0.00", formatFixed(-0.001, 2))
    }

    /**
     * Corpus ADDENDUM, sections `=== FMT0 ===` / `=== FMT1 ===` / `=== FMT2 ===` (51 rows).
     *
     * Every input here has a `Double.toString()` in scientific notation (except `0.001`, the
     * boundary control). The first `:shared` port threw on those instead of formatting them, which
     * silently killed a whole coach digest whenever an OLS trend slope came back as a
     * floating-point residue like `1.66E-15`.
     */
    @Test
    fun scientificNotationMatchesJvmGolden() {
        // FMT0
        assertEquals("0", formatFixed(1.66E-15, 0))
        assertEquals("-0", formatFixed(-1.66E-15, 0))
        assertEquals("0", formatFixed(3.55E-15, 0))
        assertEquals("0", formatFixed(4.0E-4, 0))
        assertEquals("-0", formatFixed(-4.0E-4, 0))
        assertEquals("0", formatFixed(9.9E-4, 0))
        assertEquals("-0", formatFixed(-9.9E-4, 0))
        assertEquals("0", formatFixed(1.0E-3, 0))
        assertEquals("0", formatFixed(5.0E-4, 0))
        assertEquals("-0", formatFixed(-5.0E-4, 0))
        assertEquals("0", formatFixed(1.0E-7, 0))
        assertEquals("-0", formatFixed(-1.0E-7, 0))
        assertEquals("0", formatFixed(Double.MIN_VALUE, 0))
        assertEquals("10000000", formatFixed(1.0E7, 0))
        assertEquals("-10000000", formatFixed(-1.0E7, 0))
        assertEquals("123000000", formatFixed(1.23E8, 0))
        assertEquals("100000000000000000000", formatFixed(1.0E20, 0))
        // FMT1
        assertEquals("0.0", formatFixed(1.66E-15, 1))
        assertEquals("-0.0", formatFixed(-1.66E-15, 1))
        assertEquals("0.0", formatFixed(3.55E-15, 1))
        assertEquals("0.0", formatFixed(4.0E-4, 1))
        assertEquals("-0.0", formatFixed(-4.0E-4, 1))
        assertEquals("0.0", formatFixed(9.9E-4, 1))
        assertEquals("-0.0", formatFixed(-9.9E-4, 1))
        assertEquals("0.0", formatFixed(1.0E-3, 1))
        assertEquals("0.0", formatFixed(5.0E-4, 1))
        assertEquals("-0.0", formatFixed(-5.0E-4, 1))
        assertEquals("0.0", formatFixed(1.0E-7, 1))
        assertEquals("-0.0", formatFixed(-1.0E-7, 1))
        assertEquals("0.0", formatFixed(Double.MIN_VALUE, 1))
        assertEquals("10000000.0", formatFixed(1.0E7, 1))
        assertEquals("-10000000.0", formatFixed(-1.0E7, 1))
        assertEquals("123000000.0", formatFixed(1.23E8, 1))
        assertEquals("100000000000000000000.0", formatFixed(1.0E20, 1))
        // FMT2
        assertEquals("0.00", formatFixed(1.66E-15, 2))
        assertEquals("-0.00", formatFixed(-1.66E-15, 2))
        assertEquals("0.00", formatFixed(3.55E-15, 2))
        assertEquals("0.00", formatFixed(4.0E-4, 2))
        assertEquals("-0.00", formatFixed(-4.0E-4, 2))
        assertEquals("0.00", formatFixed(9.9E-4, 2))
        assertEquals("-0.00", formatFixed(-9.9E-4, 2))
        assertEquals("0.00", formatFixed(1.0E-3, 2))
        assertEquals("0.00", formatFixed(5.0E-4, 2))
        assertEquals("-0.00", formatFixed(-5.0E-4, 2))
        assertEquals("0.00", formatFixed(1.0E-7, 2))
        assertEquals("-0.00", formatFixed(-1.0E-7, 2))
        assertEquals("0.00", formatFixed(Double.MIN_VALUE, 2))
        assertEquals("10000000.00", formatFixed(1.0E7, 2))
        assertEquals("-10000000.00", formatFixed(-1.0E7, 2))
        assertEquals("123000000.00", formatFixed(1.23E8, 2))
        assertEquals("100000000000000000000.00", formatFixed(1.0E20, 2))
    }

    /**
     * Corpus ADDENDUM, sections `=== SIGNED1 ===` and `=== BUCKET step=0.1 dec=2 ===` (34 rows).
     * Both route through [formatFixed], so both were broken by the same bail-out.
     */
    @Test
    fun scientificNotationSigned1AndBucketMatchJvmGolden() {
        // SIGNED1
        assertEquals("0.0", signed1(1.66E-15))
        assertEquals("0.0", signed1(-1.66E-15))
        assertEquals("0.0", signed1(3.55E-15))
        assertEquals("0.0", signed1(4.0E-4))
        assertEquals("0.0", signed1(-4.0E-4))
        assertEquals("0.0", signed1(9.9E-4))
        assertEquals("0.0", signed1(-9.9E-4))
        assertEquals("0.0", signed1(1.0E-3))
        assertEquals("0.0", signed1(5.0E-4))
        assertEquals("0.0", signed1(-5.0E-4))
        assertEquals("0.0", signed1(1.0E-7))
        assertEquals("0.0", signed1(-1.0E-7))
        assertEquals("0.0", signed1(Double.MIN_VALUE))
        assertEquals("+10000000.0", signed1(1.0E7))
        assertEquals("-10000000.0", signed1(-1.0E7))
        assertEquals("+123000000.0", signed1(1.23E8))
        assertEquals("+100000000000000000000.0", signed1(1.0E20))
        // BUCKET step=0.1 dec=2
        assertEquals("0.00", bucket(1.66E-15, 0.1, 2))
        assertEquals("0.00", bucket(-1.66E-15, 0.1, 2))
        assertEquals("0.00", bucket(3.55E-15, 0.1, 2))
        assertEquals("0.00", bucket(4.0E-4, 0.1, 2))
        assertEquals("0.00", bucket(-4.0E-4, 0.1, 2))
        assertEquals("0.00", bucket(9.9E-4, 0.1, 2))
        assertEquals("0.00", bucket(-9.9E-4, 0.1, 2))
        assertEquals("0.00", bucket(1.0E-3, 0.1, 2))
        assertEquals("0.00", bucket(5.0E-4, 0.1, 2))
        assertEquals("0.00", bucket(-5.0E-4, 0.1, 2))
        assertEquals("0.00", bucket(1.0E-7, 0.1, 2))
        assertEquals("0.00", bucket(-1.0E-7, 0.1, 2))
        assertEquals("0.00", bucket(Double.MIN_VALUE, 0.1, 2))
        assertEquals("10000000.00", bucket(1.0E7, 0.1, 2))
        assertEquals("-10000000.00", bucket(-1.0E7, 0.1, 2))
        assertEquals("123000000.00", bucket(1.23E8, 0.1, 2))
        // (1e20 / 0.1).roundToInt() saturates at Int.MAX_VALUE, so this formats 2147483647 * 0.1.
        assertEquals("214748364.70", bucket(1.0E20, 0.1, 2))
    }

    /** Corpus sections `=== BUCKET step=0.1 dec=2 ===` and `=== BUCKET step=50.0 dec=0 ===` (70 rows). */
    @Test
    fun bucketMatchesJvmGolden() {
        // BUCKET step=0.1 dec=2
        assertEquals("0.00", bucket(0.0, 0.1, 2))
        assertEquals("0.00", bucket(-0.0, 0.1, 2))
        assertEquals("1.00", bucket(1.0, 0.1, 2))
        assertEquals("-1.00", bucket(-1.0, 0.1, 2))
        assertEquals("0.50", bucket(0.5, 0.1, 2))
        assertEquals("-0.50", bucket(-0.5, 0.1, 2))
        assertEquals("0.10", bucket(0.05, 0.1, 2))
        assertEquals("0.00", bucket(-0.05, 0.1, 2))
        assertEquals("0.10", bucket(0.15, 0.1, 2))
        assertEquals("-0.10", bucket(-0.15, 0.1, 2))
        assertEquals("0.30", bucket(0.25, 0.1, 2))
        assertEquals("-0.20", bucket(-0.25, 0.1, 2))
        assertEquals("0.10", bucket(0.145, 0.1, 2))
        assertEquals("-0.10", bucket(-0.145, 0.1, 2))
        assertEquals("1.00", bucket(1.005, 0.1, 2))
        assertEquals("-1.00", bucket(-1.005, 0.1, 2))
        assertEquals("2.70", bucket(2.675, 0.1, 2))
        assertEquals("-2.70", bucket(-2.675, 0.1, 2))
        assertEquals("1.00", bucket(0.999, 0.1, 2))
        assertEquals("-1.00", bucket(-0.999, 0.1, 2))
        assertEquals("12.30", bucket(12.34, 0.1, 2))
        assertEquals("-12.30", bucket(-12.34, 0.1, 2))
        assertEquals("100.00", bucket(99.995, 0.1, 2))
        assertEquals("-100.00", bucket(-99.995, 0.1, 2))
        assertEquals("1234.60", bucket(1234.5678, 0.1, 2))
        assertEquals("-1234.60", bucket(-1234.5678, 0.1, 2))
        assertEquals("0.00", bucket(0.049, 0.1, 2))
        assertEquals("0.00", bucket(-0.049, 0.1, 2))
        assertEquals("0.10", bucket(0.051, 0.1, 2))
        assertEquals("-0.10", bucket(-0.051, 0.1, 2))
        assertEquals("7.00", bucket(7.0, 0.1, 2))
        assertEquals("-7.00", bucket(-7.0, 0.1, 2))
        assertEquals("100.00", bucket(100.0, 0.1, 2))
        assertEquals("0.00", bucket(0.001, 0.1, 2))
        assertEquals("0.00", bucket(-0.001, 0.1, 2))
        // BUCKET step=50.0 dec=0
        assertEquals("0", bucket(0.0, 50.0, 0))
        assertEquals("0", bucket(-0.0, 50.0, 0))
        assertEquals("0", bucket(1.0, 50.0, 0))
        assertEquals("0", bucket(-1.0, 50.0, 0))
        assertEquals("0", bucket(0.5, 50.0, 0))
        assertEquals("0", bucket(-0.5, 50.0, 0))
        assertEquals("0", bucket(0.05, 50.0, 0))
        assertEquals("0", bucket(-0.05, 50.0, 0))
        assertEquals("0", bucket(0.15, 50.0, 0))
        assertEquals("0", bucket(-0.15, 50.0, 0))
        assertEquals("0", bucket(0.25, 50.0, 0))
        assertEquals("0", bucket(-0.25, 50.0, 0))
        assertEquals("0", bucket(0.145, 50.0, 0))
        assertEquals("0", bucket(-0.145, 50.0, 0))
        assertEquals("0", bucket(1.005, 50.0, 0))
        assertEquals("0", bucket(-1.005, 50.0, 0))
        assertEquals("0", bucket(2.675, 50.0, 0))
        assertEquals("0", bucket(-2.675, 50.0, 0))
        assertEquals("0", bucket(0.999, 50.0, 0))
        assertEquals("0", bucket(-0.999, 50.0, 0))
        assertEquals("0", bucket(12.34, 50.0, 0))
        assertEquals("0", bucket(-12.34, 50.0, 0))
        assertEquals("100", bucket(99.995, 50.0, 0))
        assertEquals("-100", bucket(-99.995, 50.0, 0))
        assertEquals("1250", bucket(1234.5678, 50.0, 0))
        assertEquals("-1250", bucket(-1234.5678, 50.0, 0))
        assertEquals("0", bucket(0.049, 50.0, 0))
        assertEquals("0", bucket(-0.049, 50.0, 0))
        assertEquals("0", bucket(0.051, 50.0, 0))
        assertEquals("0", bucket(-0.051, 50.0, 0))
        assertEquals("0", bucket(7.0, 50.0, 0))
        assertEquals("0", bucket(-7.0, 50.0, 0))
        assertEquals("100", bucket(100.0, 50.0, 0))
        assertEquals("0", bucket(0.001, 50.0, 0))
        assertEquals("0", bucket(-0.001, 50.0, 0))
    }

    /** Corpus section `=== BUCKETINT step=5 ===` (9 rows). */
    @Test
    fun bucketIntMatchesJvmGolden() {
        assertEquals("0", bucketInt(0, 5))
        assertEquals("0", bucketInt(1, 5))
        assertEquals("0", bucketInt(2, 5))
        assertEquals("5", bucketInt(3, 5))
        assertEquals("5", bucketInt(7, 5))
        assertEquals("-5", bucketInt(-3, 5))
        assertEquals("-5", bucketInt(-7, 5))
        assertEquals("10", bucketInt(12, 5))
        assertEquals("100", bucketInt(100, 5))
    }

    /**
     * `groupedInt` replaces `String.format(Locale.US, "%,d", v)` (added with the `domain/review`
     * move). Values below are what the JVM `%,d` produces for `Locale.US`.
     */
    @Test
    fun groupedIntMatchesJvmGolden() {
        assertEquals("0", groupedInt(0))
        assertEquals("7", groupedInt(7))
        assertEquals("999", groupedInt(999))
        assertEquals("1,000", groupedInt(1000))
        assertEquals("9,200", groupedInt(9200))
        assertEquals("12,345", groupedInt(12345))
        assertEquals("123,456", groupedInt(123456))
        assertEquals("1,234,567", groupedInt(1234567))
        assertEquals("-1,000", groupedInt(-1000))
        assertEquals("-999", groupedInt(-999))
        assertEquals("2,147,483,647", groupedInt(Int.MAX_VALUE))
        assertEquals("-2,147,483,648", groupedInt(Int.MIN_VALUE))
    }

    /** Corpus section `=== PCT ===` (35 rows). */
    @Test
    fun pctMatchesJvmGolden() {
        assertEquals("0%", pct(0.0))
        assertEquals("0%", pct(-0.0))
        assertEquals("1%", pct(1.0))
        assertEquals("-1%", pct(-1.0))
        assertEquals("1%", pct(0.5))
        assertEquals("0%", pct(-0.5))
        assertEquals("0%", pct(0.05))
        assertEquals("0%", pct(-0.05))
        assertEquals("0%", pct(0.15))
        assertEquals("0%", pct(-0.15))
        assertEquals("0%", pct(0.25))
        assertEquals("0%", pct(-0.25))
        assertEquals("0%", pct(0.145))
        assertEquals("0%", pct(-0.145))
        assertEquals("1%", pct(1.005))
        assertEquals("-1%", pct(-1.005))
        assertEquals("3%", pct(2.675))
        assertEquals("-3%", pct(-2.675))
        assertEquals("1%", pct(0.999))
        assertEquals("-1%", pct(-0.999))
        assertEquals("12%", pct(12.34))
        assertEquals("-12%", pct(-12.34))
        assertEquals("100%", pct(99.995))
        assertEquals("-100%", pct(-99.995))
        assertEquals("1235%", pct(1234.5678))
        assertEquals("-1235%", pct(-1234.5678))
        assertEquals("0%", pct(0.049))
        assertEquals("0%", pct(-0.049))
        assertEquals("0%", pct(0.051))
        assertEquals("0%", pct(-0.051))
        assertEquals("7%", pct(7.0))
        assertEquals("-7%", pct(-7.0))
        assertEquals("100%", pct(100.0))
        assertEquals("0%", pct(0.001))
        assertEquals("0%", pct(-0.001))
    }

    /**
     * The exact production path from the final-review finding. `TrendCalculator.trendPerWeek` is an
     * OLS slope, so a flat weight series cancels down to a residue like `1.66E-15`, and
     * `BodyDetectors` / `TrainingDetectors` format that residue inside their "flat band" branches
     * (`BodyDetectors.kt:41`, `:154`, `TrainingDetectors.kt:55`). `formatFixed` used to throw there;
     * `CoachDigestCoordinator`'s `runCatching` swallowed it and the coach staged no signal that day.
     */
    @Test
    fun coachDetectorSupportFormatsOlsResidueInsteadOfThrowing() {
        assertEquals("0.00", CoachDetectorSupport.fmt(1.66E-15, 2))
        assertEquals("-0.00", CoachDetectorSupport.fmt(-1.66E-15, 2))
        assertEquals("0.0", CoachDetectorSupport.fmt(3.55E-15, 1))
        assertEquals("0.0", CoachDetectorSupport.signed1(1.66E-15))
        assertEquals("0.0", CoachDetectorSupport.signed1(-1.66E-15))
        assertEquals("0.00", CoachDetectorSupport.bucket(1.66E-15, 0.1, 2))
    }

    /** Corpus section `=== ISOWEEK ===` (14 rows). */
    @Test
    fun isoWeekMatchesJvmGolden() {
        assertEquals("2026-W01", isoWeek(LocalDate.parse("2026-01-01")))
        assertEquals("2026-W01", isoWeek(LocalDate.parse("2026-01-04")))
        assertEquals("2026-W02", isoWeek(LocalDate.parse("2026-01-05")))
        assertEquals("2026-W53", isoWeek(LocalDate.parse("2026-12-28")))
        assertEquals("2026-W53", isoWeek(LocalDate.parse("2026-12-31")))
        assertEquals("2025-W01", isoWeek(LocalDate.parse("2025-01-01")))
        assertEquals("2026-W01", isoWeek(LocalDate.parse("2025-12-29")))
        assertEquals("2026-W53", isoWeek(LocalDate.parse("2027-01-03")))
        assertEquals("2027-W01", isoWeek(LocalDate.parse("2027-01-04")))
        assertEquals("2020-W53", isoWeek(LocalDate.parse("2020-12-31")))
        assertEquals("2020-W53", isoWeek(LocalDate.parse("2021-01-01")))
        assertEquals("2021-W01", isoWeek(LocalDate.parse("2021-01-04")))
        assertEquals("2024-W09", isoWeek(LocalDate.parse("2024-02-29")))
        assertEquals("2026-W28", isoWeek(LocalDate.parse("2026-07-06")))
    }
}
