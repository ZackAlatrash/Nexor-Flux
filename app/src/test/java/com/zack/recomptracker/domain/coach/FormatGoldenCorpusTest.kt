package com.zack.recomptracker.domain.coach

import java.time.LocalDate
import org.junit.Test

/**
 * Not an assertion test — a ground-truth generator. Run it, copy the printed block into
 * shared/src/commonTest/.../GoldenFormatTest.kt, then delete this file (Task 4).
 */
class FormatGoldenCorpusTest {

    private val doubles = listOf(
        0.0, -0.0, 1.0, -1.0, 0.5, -0.5, 0.05, -0.05, 0.15, -0.15, 0.25, -0.25,
        0.145, -0.145, 1.005, -1.005, 2.675, -2.675, 0.999, -0.999,
        12.34, -12.34, 99.995, -99.995, 1234.5678, -1234.5678,
        0.049, -0.049, 0.051, -0.051, 7.0, -7.0, 100.0, 0.001, -0.001,
    )

    private val dates = listOf(
        "2026-01-01", "2026-01-04", "2026-01-05", "2026-12-28", "2026-12-31",
        "2025-01-01", "2025-12-29", "2027-01-03", "2027-01-04",
        "2020-12-31", "2021-01-01", "2021-01-04", "2024-02-29", "2026-07-06",
    ).map(LocalDate::parse)

    @Test
    fun printCorpus() {
        println("=== SIGNED1 ===")
        doubles.forEach { println("$it -> ${CoachDetectorSupport.signed1(it)}") }
        println("=== FMT1 ===")
        doubles.forEach { println("$it -> ${CoachDetectorSupport.fmt(it, 1)}") }
        println("=== FMT2 ===")
        doubles.forEach { println("$it -> ${CoachDetectorSupport.fmt(it, 2)}") }
        println("=== FMT0 ===")
        doubles.forEach { println("$it -> ${CoachDetectorSupport.fmt(it, 0)}") }
        println("=== BUCKET step=0.1 dec=2 ===")
        doubles.forEach { println("$it -> ${CoachDetectorSupport.bucket(it, 0.1, 2)}") }
        println("=== BUCKET step=50.0 dec=0 ===")
        doubles.forEach { println("$it -> ${CoachDetectorSupport.bucket(it, 50.0, 0)}") }
        println("=== BUCKETINT step=5 ===")
        listOf(0, 1, 2, 3, 7, -3, -7, 12, 100).forEach {
            println("$it -> ${CoachDetectorSupport.bucketInt(it, 5)}")
        }
        println("=== ISOWEEK ===")
        dates.forEach { println("$it -> ${CoachDetectorSupport.isoWeek(it)}") }
        println("=== PCT ===")
        doubles.forEach { println("$it -> ${CoachDetectorSupport.pct(it)}") }
    }
}
