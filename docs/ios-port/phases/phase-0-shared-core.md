# iOS Port Phase 0 — Shared Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract `domain/` into a Kotlin Multiplatform `:shared` module that compiles for Android
and iOS, with bit-exact behaviour proven by golden tests — then decide, on evidence, whether to keep
the shared core or port `domain/` to Swift instead.

**Architecture:** A new `:shared` KMP module holds ~5,550 LOC of pure synchronous domain logic in
`commonMain`. `:app` depends on it and is otherwise unchanged. `java.time` is replaced with
`kotlinx-datetime`; the three JVM-only formatting primitives (`String.format`, `IsoFields`,
`DayOfWeek.getDisplayName`) are hand-rolled in common code and pinned by golden tests captured from
current JVM output **before** any change. Existing JUnit4 tests stay JVM-only and move to
`shared/src/androidUnitTest`; a small new `commonTest` suite runs the risky primitives on the iOS
simulator too.

**Tech Stack:** Kotlin 2.2.21, Gradle 9.5.1, AGP 8.13.2, `kotlin("multiplatform")` +
`com.android.library`, `kotlinx-datetime`, `kotlinx-serialization` (already present), JUnit4
(androidUnitTest) + `kotlin.test` (commonTest), Xcode 26.5.

**Gate:** this phase ends in a **decision**, recorded in `decisions.md`. See Task 16.

---

## Context you need before starting

Read, in order:
1. `docs/ios-port/STATUS.md` — where we are
2. `docs/ios-port/decisions.md` — D1–D8 are binding
3. `docs/ios-port/reference/domain-port-notes.md` §3 — the blocker list this plan implements

**Non-obvious facts that shape this plan** (all verified 2026-08-01 against `develop` @ `d874aa5`):

- `domain/` has **zero** `android.*`/`androidx.*` imports and **zero** `suspend`/`Flow`/`StateFlow`.
- ✅ **No `BuildConfig` usage and no product flavors** anywhere in `:app` — both would complicate the
  module split; neither exists.
- ⚠️ **Three `:app` test files reference `internal` declarations** that move to `:shared`.
  `internal` is module-scoped, so those tests **must move with the code** or compilation breaks:
  `PatternDetectorsTest.kt`, `CrossSignalDiscoveryDetectorTest.kt`, `ExperimentEvaluationTest.kt`.
- ⚠️ **Four inline fully-qualified `java.time` uses** that an import rewrite will miss:
  `TrainingDetectors.kt:79`, `RebalanceEngine.kt:57`, `RebalanceEngine.kt:426`, and a descriptor
  string in `LocalDateTimeIsoSerializer.kt:18`.
- `domain/share`'s only mention of `ExerciseLibraryJson` is **a comment** — not a real dependency.
- `ExerciseLibraryJson.kt` is pure **except** the trailing `toEntity` extension. Splitting that one
  function frees the whole `workout` package.

**Dependency order** (measured; a package can only move after everything it imports has moved):

```
wave 0  adjustment · body                    (no deps, no java.time)
wave 1  trend(→adjustment) · adherence · activity · streak
wave 2  value types: MacroTotals · UserProfilePreferences · PlanPreferences
wave 3  plan(→prefs) · insight
wave 4  rebalance(→activity, plan, prefs)
wave 5  workout(after ExerciseLibraryJson split) · food(4 of 6 files)
wave 6  coach(→adjustment, insight, streak, trend, workout, MacroTotals)
wave 7  review(→adjustment, coach, insight, workout) · share(→workout)
never   export (19 Room entities) · foodimport (java.io; v1.1 anyway)
        food/RecentFoods.kt · food/RecipeWithIngredients.kt (Room-typed signatures)
```

**Blocker coverage** (IDs from `reference/domain-port-notes.md` §3 — every one has a home):

| Blocker | Handled by |
|---|---|
| B1 no KMP module | Task 3 |
| B2 `java.time` → kotlinx-datetime | Task 6 mapping table, applied in Tasks 6, 8, 9, 10, 11, 12 |
| B3 ISO week number 🔴 | Task 2 (golden corpus) + Task 4 Step 4 |
| B4 `String.format` 🔴 | Task 2 (golden corpus) + Task 4 Step 3 |
| B5 day-name localisation | Task 4 Step 5 + Task 8 Step 2 |
| B6 `java.io` CSV streaming | Excluded — `foodimport` is v1.1 |
| B7 `BackupModels` on 19 Room entities | Excluded — persistence DTO, stays in `:app` |
| B8 3 entity-typed files | Task 10 (split `ExerciseLibraryJson`; other 2 stay) |
| B9 `data.preferences` value types | Task 7 |
| B10 `core.model.MacroTotals` | Task 7 |
| B11 `LocalDate.now()` default arg | Task 8 Step 2 |

---

## File structure

**Created:**

| Path | Responsibility |
|---|---|
| `shared/build.gradle.kts` | KMP module config: android + iosArm64 + iosSimulatorArm64 |
| `shared/src/androidMain/AndroidManifest.xml` | Empty manifest AGP requires |
| `shared/src/commonMain/kotlin/.../domain/**` | The moved domain packages |
| `shared/src/commonMain/kotlin/.../core/model/MacroTotals.kt` | Moved value type |
| `shared/src/commonMain/kotlin/.../data/preferences/UserProfilePreferences.kt` | Moved value types |
| `shared/src/commonMain/kotlin/.../data/preferences/PlanPreferences.kt` | Moved value type |
| `shared/src/commonMain/kotlin/.../shared/format/DecimalFormat.kt` | HALF_UP fixed-decimal formatting (replaces `String.format`) |
| `shared/src/commonMain/kotlin/.../shared/time/IsoWeek.kt` | ISO-8601 week stamp (replaces `IsoFields`) |
| `shared/src/commonMain/kotlin/.../shared/time/DayNames.kt` | English day names (replaces `getDisplayName`) |
| `shared/src/commonTest/kotlin/.../format/GoldenFormatTest.kt` | Runs on JVM **and** iOS |
| `shared/src/androidUnitTest/kotlin/.../domain/**` | The 50 moved JUnit4 test files |

**Modified:** `settings.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`,
`app/src/main/java/.../data/local/entity/ExerciseEntityMapping.kt` (new home for `toEntity`).

**Deleted:** `domain/coach/LocalDateTimeIsoSerializer.kt` (kotlinx-datetime ships its own).

### Created in a SEPARATE repo (D11)

The iOS app lives **outside this project**, as a sibling folder and an independent git repo:

```
~/Desktop/
├── Personal Dietitian/          ← this repo (Android + :shared)
└── RecompTracker-iOS/           ← new, independent repo
    ├── RecompTracker.xcodeproj
    ├── RecompTracker/
    ├── Frameworks/Shared.xcframework   ← gitignored build product
    ├── scripts/sync-shared.sh          ← builds it from ../Personal Dietitian
    └── README.md                       ← documents the sibling precondition
```

`:shared` publishes an **XCFramework**; the iOS repo consumes a synced copy. Consequences:
- Xcode never invokes Gradle, so the SwiftUI inner loop is **faster** than an in-project setup.
- The iOS repo is **not standalone-buildable** — the Android repo must sit beside it. Documented in
  its README, and a gate criterion (Task 16, #8) measures whether the resync friction is acceptable.
- `embedAndSignAppleFrameworkForXcode` is **not** used — it assumes a single project.

---

## Task 1: Pin the toolchain

`kotlinx-datetime` 0.8.0 removed `kotlinx.datetime.Instant`/`Clock` in favour of `kotlin.time`
equivalents, which may require a newer Kotlin than this project's 2.2.21. Establish the working
version before writing any code against it.

**Files:** Modify `gradle/libs.versions.toml`

- [ ] **Step 1: Add the dependency at the newest version**

In `gradle/libs.versions.toml`, under `[versions]`:
```toml
kotlinxDatetime = "0.8.0"
```
Under `[libraries]`:
```toml
kotlinx-datetime = { group = "org.jetbrains.kotlinx", name = "kotlinx-datetime", version.ref = "kotlinxDatetime" }
```

- [ ] **Step 2: Prove it resolves against Kotlin 2.2.21**

Temporarily add to `app/build.gradle.kts` dependencies:
```kotlin
implementation(libs.kotlinx.datetime)
```
Run: `./gradlew :app:compileDebugKotlin`
Expected: **BUILD SUCCESSFUL**.
If it fails with a Kotlin-version or `kotlin.time.Instant` error, step down to `0.7.1` and re-run.
Record whichever version compiles.

- [ ] **Step 3: Remove the temporary dependency**

Delete the `implementation(libs.kotlinx.datetime)` line from `app/build.gradle.kts`. It belongs to
`:shared`, not `:app`.
Run: `./gradlew :app:compileDebugKotlin` — Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 4: Record the decision**

Append to `docs/ios-port/decisions.md`:
```markdown
## D9 · <date> · kotlinx-datetime pinned at <version>, Kotlin stays at 2.2.21

**Why:** Phase 0 shares only `domain/`, which needs no Room-KMP or DataStore-KMP, so no Kotlin
upgrade is required. <version> is the newest that compiles against Kotlin 2.2.21.
**Reversing:** a Kotlin upgrade is independent and can happen later.
```

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml docs/ios-port/decisions.md
git commit -m "chore(ios): pin kotlinx-datetime for the shared module"
```

---

## Task 2: Golden-value corpus for the three formatters

🔴 **The highest-risk work in this phase.** `isoWeek`, `fmt` and `signed1`
(`domain/coach/CoachDetectorSupport.kt:19-33`) feed `CoachSignal.dedupKey` and `fallbackText`.
A rounding or padding difference **silently** changes cooldown behaviour and user-visible copy
instead of failing loudly. Capture ground truth **before** touching anything.

Java's `String.format("%.2f", x)` rounds **HALF_UP** (away from zero). Kotlin's `roundToInt()`
rounds half **toward positive infinity**. They disagree on every negative half. Do not assume — measure.

**Files:** Create `app/src/test/java/com/zack/recomptracker/domain/coach/FormatGoldenCorpusTest.kt`

- [ ] **Step 1: Write a test that prints the corpus**

```kotlin
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
```

- [ ] **Step 2: Run it and capture the output**

Run: `./gradlew :app:testDebugUnitTest --tests "*FormatGoldenCorpusTest*" -i`
Expected: PASS, with the corpus in the log.
**Save the printed output to `docs/ios-port/phases/phase-0-golden-corpus.txt`** verbatim. This file
is the contract; commit it.

- [ ] **Step 3: Sanity-check three specific rows**

Confirm in the captured output:
- `-0.25 -> -0.3` under `FMT1` (HALF_UP away from zero, **not** `-0.2`)
- `-0.0 -> 0.0` under `SIGNED1` (the explicit `-0.0` normalisation at `CoachDetectorSupport.kt:32`)
- `2026-01-01 -> 2026-W01` and `2027-01-03 -> 2026-W53` under `ISOWEEK` (Jan 3 2027 is a Sunday and
  belongs to ISO week 53 of the **2026** week-based year — this is the case a naive implementation
  gets wrong)

If any differs from the above, **the captured output wins** — it is ground truth. Note the
discrepancy in the corpus file as a comment.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/zack/recomptracker/domain/coach/FormatGoldenCorpusTest.kt \
        docs/ios-port/phases/phase-0-golden-corpus.txt
git commit -m "test(ios): capture golden corpus for domain formatters before KMP migration"
```

---

## Task 3: Create the `:shared` module skeleton

**Files:**
- Create: `shared/build.gradle.kts`, `shared/src/androidMain/AndroidManifest.xml`
- Create: `shared/src/commonMain/kotlin/com/zack/recomptracker/shared/Placeholder.kt`
- Create: `shared/src/commonTest/kotlin/com/zack/recomptracker/shared/PlaceholderTest.kt`
- Modify: `settings.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`

- [ ] **Step 1: Register the module**

In `settings.gradle.kts`, after `include(":app")`:
```kotlin
include(":shared")
```

- [ ] **Step 2: Add the multiplatform plugin to the version catalog**

In `gradle/libs.versions.toml` under `[plugins]`:
```toml
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
android-library = { id = "com.android.library", version.ref = "agp" }
```

- [ ] **Step 3: Write `shared/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // Existing JUnit4 domain tests live here: JVM-only, and `internal` in commonMain is
        // visible because test compilations are associated with main compilations of the module.
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.junit)
            }
        }
    }
}

android {
    namespace = "com.zack.recomptracker.shared"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
```

- [ ] **Step 4: Add the empty manifest AGP requires**

Create `shared/src/androidMain/AndroidManifest.xml`:
```xml
<manifest />
```

- [ ] **Step 5: Add a placeholder and a placeholder test**

Create `shared/src/commonMain/kotlin/com/zack/recomptracker/shared/Placeholder.kt`:
```kotlin
package com.zack.recomptracker.shared

internal fun sharedModuleIsWired(): Boolean = true
```

Create `shared/src/commonTest/kotlin/com/zack/recomptracker/shared/PlaceholderTest.kt`:
```kotlin
package com.zack.recomptracker.shared

import kotlin.test.Test
import kotlin.test.assertTrue

class PlaceholderTest {
    @Test
    fun moduleIsWired() {
        assertTrue(sharedModuleIsWired())
    }
}
```

- [ ] **Step 6: Wire `:app` to `:shared`**

In `app/build.gradle.kts` dependencies, above the AndroidX block:
```kotlin
implementation(project(":shared"))
```

- [ ] **Step 7: Verify all three toolchains**

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64
./gradlew :shared:iosSimulatorArm64Test
./gradlew :app:assembleDebug
```
Expected: all three **BUILD SUCCESSFUL**, and the iOS test task reports 1 test passing.

If `iosSimulatorArm64Test` fails to link, that is the known `NativeSQLiteDriver`-class linker issue
—it should not apply here (no SQLite in `:shared`). Capture the exact error in STATUS before
attempting a fix; it is decision-relevant for the gate.

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml shared app/build.gradle.kts
git commit -m "feat(ios): add :shared KMP module targeting android + ios"
```

---

## Task 4: Port the three formatting primitives into `:shared`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/zack/recomptracker/shared/format/DecimalFormat.kt`
- Create: `shared/src/commonMain/kotlin/com/zack/recomptracker/shared/time/IsoWeek.kt`
- Create: `shared/src/commonMain/kotlin/com/zack/recomptracker/shared/time/DayNames.kt`
- Create: `shared/src/commonTest/kotlin/com/zack/recomptracker/shared/format/GoldenFormatTest.kt`

- [ ] **Step 1: Write the failing golden test**

Create `shared/src/commonTest/kotlin/com/zack/recomptracker/shared/format/GoldenFormatTest.kt`.
Populate every `assertEquals` from `docs/ios-port/phases/phase-0-golden-corpus.txt` — **the captured
values, not the ones written here from memory**. Skeleton with the rows that matter most:

```kotlin
package com.zack.recomptracker.shared.format

import com.zack.recomptracker.shared.time.isoWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class GoldenFormatTest {

    @Test
    fun signed1MatchesJvmGolden() {
        assertEquals("0.0", signed1(0.0))
        assertEquals("0.0", signed1(-0.0))
        assertEquals("+0.5", signed1(0.5))
        assertEquals("-0.5", signed1(-0.5))
        assertEquals("+0.2", signed1(0.15))
        assertEquals("-0.2", signed1(-0.15))
        // …every SIGNED1 row from the corpus file
    }

    @Test
    fun fixedDecimalMatchesJvmGolden() {
        assertEquals("0.3", formatFixed(0.25, 1))
        assertEquals("-0.3", formatFixed(-0.25, 1))   // HALF_UP away from zero
        assertEquals("12.34", formatFixed(12.34, 2))
        assertEquals("100", formatFixed(100.0, 0))
        // …every FMT0/FMT1/FMT2 row from the corpus file
    }

    @Test
    fun isoWeekMatchesJvmGolden() {
        assertEquals("2026-W01", isoWeek(LocalDate.parse("2026-01-01")))
        assertEquals("2026-W53", isoWeek(LocalDate.parse("2027-01-03")))
        assertEquals("2027-W01", isoWeek(LocalDate.parse("2027-01-04")))
        // …every ISOWEEK row from the corpus file
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :shared:allTests`
Expected: **FAIL** — "Unresolved reference: signed1 / formatFixed / isoWeek".

- [ ] **Step 3: Implement fixed-decimal formatting**

Create `shared/src/commonMain/kotlin/com/zack/recomptracker/shared/format/DecimalFormat.kt`:

> ⚠️ **This implementation was rewritten after Task 2 captured ground truth.** Two assumptions in
> the original draft were wrong, and the golden corpus caught both:
>
> 1. **Java rounds the *shortest decimal representation*, not the exact binary double.**
>    `String.format("%.2f", 1.005)` is `"1.01"`, even though the exact double is
>    `1.00499999999999989…`. A `floor(x * 100 + 0.5)` implementation yields `"1.00"` and diverges.
>    Same for `2.675 → "2.68"` and `99.995 → "100.00"`.
> 2. **`fmt()` emits negative zero.** `formatFixed(-0.049, 1)` is `"-0.0"` and
>    `formatFixed(-0.25, 0)` is `"-0"`. Only `signed1()` and `bucket()` normalise it away.
>
> Java also looks at **only the single digit past the requested precision**, which is why
> `0.145` is `"0.1"` at 1dp but `"0.15"` at 2dp.

```kotlin
package com.zack.recomptracker.shared.format

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Replacement for `String.format(Locale.US, "%.Nf", value)` — unavailable on Kotlin/Native.
 *
 * Reproduces `java.util.Formatter` exactly: it rounds the SHORTEST decimal representation of the
 * double (what `toString()` gives), HALF_UP on the single digit past the requested precision.
 * It does NOT round the exact binary value — that is why 1.005 formats as "1.01".
 * Negative zero is preserved ("-0.0"), matching Java. Pinned by GoldenFormatTest.
 */
fun formatFixed(value: Double, decimals: Int): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"

    // 1.0 / -0.0 is -Infinity, which is how negative zero is detected.
    val negative = value < 0.0 || (value == 0.0 && 1.0 / value < 0.0)

    val shortest = abs(value).toString()
    check(!shortest.contains('e', ignoreCase = true)) {
        "formatFixed cannot handle scientific notation: $value"
    }

    val intPart = shortest.substringBefore('.')
    val fracPart = shortest.substringAfter('.', "")

    // One digit string with `decimals` implied decimal places.
    var digits = intPart + fracPart.take(decimals).padEnd(decimals, '0')
    if (fracPart.length > decimals && fracPart[decimals] >= '5') {
        digits = incrementDigits(digits)
    }

    val body = if (decimals == 0) {
        digits
    } else {
        val cut = digits.length - decimals
        "${digits.substring(0, cut).ifEmpty { "0" }}.${digits.substring(cut)}"
    }
    return if (negative) "-$body" else body
}

/** Adds one to a non-negative decimal digit string, growing it on overflow ("99" -> "100"). */
private fun incrementDigits(s: String): String {
    val chars = s.toCharArray()
    var i = chars.lastIndex
    while (i >= 0) {
        if (chars[i] == '9') {
            chars[i] = '0'
            i--
        } else {
            chars[i]++
            return chars.concatToString()
        }
    }
    return "1" + chars.concatToString()
}

/**
 * Signed one-decimal string, e.g. "+0.4" / "-0.2". Mirrors `String.format("%+.1f", v)` followed by
 * the ±0.0 normalisation at the original `CoachDetectorSupport.kt:32`.
 */
fun signed1(value: Double): String {
    val body = formatFixed(value, 1)
    if (body == "0.0" || body == "-0.0") return "0.0"
    return if (body.startsWith("-")) body else "+$body"
}

/** Percent with no decimals, e.g. "83%". */
fun pct(value: Double): String = "${value.roundToInt()}%"

/** Bucket a continuous value to the nearest [step] for a stable dedup key. */
fun bucket(value: Double, step: Double, decimals: Int = 2): String {
    val bucketed = (value / step).roundToInt() * step
    val safe = if (bucketed == 0.0) 0.0 else bucketed
    return formatFixed(safe, decimals)
}

fun bucketInt(value: Int, step: Int): String {
    val safeStep = if (step <= 0) 1 else step
    return ((value.toDouble() / safeStep).roundToInt() * safeStep).toString()
}
```

- [ ] **Step 4: Implement the ISO week stamp**

Create `shared/src/commonMain/kotlin/com/zack/recomptracker/shared/time/IsoWeek.kt`:

```kotlin
package com.zack.recomptracker.shared.time

import com.zack.recomptracker.shared.format.formatFixed
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * ISO-8601 week stamp, e.g. "2026-W27". Replaces
 * `date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)` + `WEEK_BASED_YEAR`, which are JVM-only.
 *
 * ISO rules: weeks start Monday; week 1 is the week containing the first Thursday of the year.
 * Consequence: early-January dates can belong to week 52/53 of the *previous* week-based year,
 * and late-December dates to week 1 of the *next* one. Pinned by GoldenFormatTest.
 */
fun isoWeek(date: LocalDate): String {
    // The Thursday of this date's week determines both the week-based year and the week number.
    val monday = date.minus(date.mondayFirstIndex, DateTimeUnit.DAY)
    val thursday = monday.plus(3, DateTimeUnit.DAY)

    val weekBasedYear = thursday.year
    val jan1 = LocalDate(weekBasedYear, 1, 1)
    val week1Monday = jan1
        .minus(jan1.mondayFirstIndex, DateTimeUnit.DAY)
        .let { if (it.plus(3, DateTimeUnit.DAY).year < weekBasedYear) it.plus(7, DateTimeUnit.DAY) else it }

    val week = week1Monday.daysUntil(monday) / 7 + 1

    return "${weekBasedYear.toString().padStart(4, '0')}-W${week.toString().padStart(2, '0')}"
}

/**
 * 0 for Monday … 6 for Sunday. Deliberately NOT named `isoDayNumber` — kotlinx-datetime ships an
 * extension property by that name and shadowing it produces confusing resolution errors.
 */
private val LocalDate.mondayFirstIndex: Int
    get() = when (dayOfWeek) {
        DayOfWeek.MONDAY -> 0
        DayOfWeek.TUESDAY -> 1
        DayOfWeek.WEDNESDAY -> 2
        DayOfWeek.THURSDAY -> 3
        DayOfWeek.FRIDAY -> 4
        DayOfWeek.SATURDAY -> 5
        else -> 6
    }
```

- [ ] **Step 5: Implement day names**

Create `shared/src/commonMain/kotlin/com/zack/recomptracker/shared/time/DayNames.kt`:

```kotlin
package com.zack.recomptracker.shared.time

import kotlinx.datetime.DayOfWeek

/**
 * Replaces `DayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)`, which is JVM-only.
 * The app renders these US-English names in insight copy; there is no localisation today.
 */
fun DayOfWeek.fullNameEnglish(): String = when (this) {
    DayOfWeek.MONDAY -> "Monday"
    DayOfWeek.TUESDAY -> "Tuesday"
    DayOfWeek.WEDNESDAY -> "Wednesday"
    DayOfWeek.THURSDAY -> "Thursday"
    DayOfWeek.FRIDAY -> "Friday"
    DayOfWeek.SATURDAY -> "Saturday"
    DayOfWeek.SUNDAY -> "Sunday"
    else -> name.lowercase().replaceFirstChar { it.uppercase() }
}
```

- [ ] **Step 6: Run the golden test on BOTH platforms**

```bash
./gradlew :shared:testDebugUnitTest      # JVM
./gradlew :shared:iosSimulatorArm64Test  # Kotlin/Native
```
Expected: **both PASS.**

If a row fails, the implementation is wrong, not the corpus. Fix until the corpus passes verbatim.

🔴 **The one failure mode that is NOT an implementation bug:** `formatFixed` depends on
`Double.toString()` producing the same shortest decimal representation on Kotlin/Native as on the
JVM. If JVM passes but `iosSimulatorArm64` fails on rows like `1.005`, `2.675` or `0.145`, that is
**Kotlin/Native's `Double.toString()` disagreeing with the JVM's** — a platform difference, not a
logic error. Do not paper over it by special-casing those values.

If that happens: record the exact failing rows in `docs/ios-port/STATUS.md`, implement the shortest-
repr conversion by hand (Ryū/Grisu-style) rather than relying on `toString()`, and flag it at the
gate — it means every double-formatting path in the shared core needs its own implementation, which
materially raises the cost of the shared-core approach.

- [ ] **Step 7: Delete the corpus generator**

```bash
rm app/src/test/java/com/zack/recomptracker/domain/coach/FormatGoldenCorpusTest.kt
```
Run: `./gradlew :app:testDebugUnitTest` — Expected: **BUILD SUCCESSFUL**.
(`phase-0-golden-corpus.txt` stays — it is the contract.)

- [ ] **Step 8: Commit**

```bash
git add shared app/src/test
git commit -m "feat(ios): port isoWeek/formatFixed/dayNames to commonMain, pinned by golden tests"
```

---

## Task 5: Move wave 0 — `adjustment` and `body`

These have no domain dependencies and no `java.time`. They prove the move pipeline with zero
migration risk.

**Files:**
- Move: `app/src/main/java/.../domain/adjustment/*` → `shared/src/commonMain/kotlin/.../domain/adjustment/`
- Move: `app/src/main/java/.../domain/body/*` → `shared/src/commonMain/kotlin/.../domain/body/`
- Move: `app/src/test/java/.../domain/body/*` → `shared/src/androidUnitTest/kotlin/.../domain/body/`

- [ ] **Step 1: Move the sources with git**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian"
mkdir -p shared/src/commonMain/kotlin/com/zack/recomptracker/domain
git mv app/src/main/java/com/zack/recomptracker/domain/adjustment \
       shared/src/commonMain/kotlin/com/zack/recomptracker/domain/adjustment
git mv app/src/main/java/com/zack/recomptracker/domain/body \
       shared/src/commonMain/kotlin/com/zack/recomptracker/domain/body
```

Use `git mv`, not copy-delete, so history follows the files.

- [ ] **Step 2: Move the matching tests**

```bash
mkdir -p shared/src/androidUnitTest/kotlin/com/zack/recomptracker/domain
git mv app/src/test/java/com/zack/recomptracker/domain/body \
       shared/src/androidUnitTest/kotlin/com/zack/recomptracker/domain/body
```
(`adjustment` has no test directory of its own — its engine is covered from other packages.)

- [ ] **Step 3: Verify both modules build and all tests pass**

```bash
./gradlew :shared:testDebugUnitTest :shared:iosSimulatorArm64Test :app:testDebugUnitTest
```
Expected: **all PASS.** Package names are unchanged, so no import edits are needed anywhere in `:app`.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "refactor(ios): move domain/adjustment + domain/body to :shared"
```

---

## Task 6: Move wave 1 — `trend`, `adherence`, `activity`, `streak`

First packages carrying `java.time`. Apply the mapping below; it is the same mapping for every
remaining task.

### The `java.time` → `kotlinx-datetime` mapping

| java.time | kotlinx-datetime |
|---|---|
| `import java.time.LocalDate` | `import kotlinx.datetime.LocalDate` |
| `import java.time.DayOfWeek` | `import kotlinx.datetime.DayOfWeek` |
| `import java.time.LocalDateTime` | `import kotlinx.datetime.LocalDateTime` |
| `import java.time.LocalTime` | `import kotlinx.datetime.LocalTime` |
| `import java.time.temporal.ChronoUnit` | *delete* (add `import kotlinx.datetime.daysUntil`) |
| `LocalDate.of(y, m, d)` | `LocalDate(y, m, d)` |
| `LocalTime.of(h, m)` | `LocalTime(h, m)` |
| `a.minusDays(n)` | `a.minus(n, DateTimeUnit.DAY)` |
| `a.plusDays(n)` | `a.plus(n, DateTimeUnit.DAY)` |
| `a.minusWeeks(n)` | `a.minus(n * 7, DateTimeUnit.DAY)` |
| `ChronoUnit.DAYS.between(a, b)` | `a.daysUntil(b)` |
| `a.isBefore(b)` | `a < b` |
| `a.isAfter(b)` | `a > b` |
| `date.monthValue` | `date.monthNumber` |
| `date.toEpochDay()` | `date.toEpochDays()` *(Int, not Long)* |
| `dt.toLocalDate()` | `dt.date` |
| `dt.toLocalTime()` | `dt.time` |
| `LocalDate.parse(s)` / `date.toString()` | unchanged (both ISO-8601) |
| `date.get(IsoFields.…)` | `isoWeek(date)` from `shared.time` |
| `dow.getDisplayName(TextStyle.FULL, Locale.US)` | `dow.fullNameEnglish()` from `shared.time` |
| `String.format(Locale.US, "%.Nf", v)` | `formatFixed(v, N)` from `shared.format` |
| `LocalDate.now()` | **remove** — the caller must pass `today` |

**Files:** Move `domain/{trend,adherence,activity,streak}` and their tests.

- [ ] **Step 1: Move sources and tests**

```bash
for p in trend adherence activity streak; do
  git mv app/src/main/java/com/zack/recomptracker/domain/$p \
         shared/src/commonMain/kotlin/com/zack/recomptracker/domain/$p
  [ -d app/src/test/java/com/zack/recomptracker/domain/$p ] && \
    git mv app/src/test/java/com/zack/recomptracker/domain/$p \
           shared/src/androidUnitTest/kotlin/com/zack/recomptracker/domain/$p
done
```

- [ ] **Step 2: Apply the import mapping**

Edit each moved file per the table above. Affected files and their APIs:
- `trend/TrendCalculator.kt` — `LocalDate`, `ChronoUnit`
- `trend/MovingAverage.kt` — `LocalDate`
- `adherence/AdherenceCalculator.kt` — `LocalDate`
- `activity/ActivitySummary.kt` — `LocalDate`
- `streak/StreakCalculator.kt` — `LocalDate`, `ChronoUnit`

- [ ] **Step 3: Verify nothing JVM-only remains**

```bash
grep -rn "java\.time\|java\.util\|java\.io\|String\.format" shared/src/commonMain/
```
Expected: **no output.** This grep catches the inline fully-qualified uses that an import rewrite
misses — run it after every move task.

- [ ] **Step 4: Build and test**

```bash
./gradlew :shared:testDebugUnitTest :shared:iosSimulatorArm64Test :app:testDebugUnitTest
```
Expected: **all PASS.**

If `:app` fails with unresolved `LocalDate`, a `:app` file is passing `java.time.LocalDate` into a
shared function that now takes `kotlinx.datetime.LocalDate`. **Do not change the shared signature.**
Convert at the call site in `:app`:
```kotlin
// java.time.LocalDate -> kotlinx.datetime.LocalDate
kotlinx.datetime.LocalDate.parse(javaDate.toString())
```
Record every such conversion site — the count is a gate input (Task 16).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "refactor(ios): move trend/adherence/activity/streak to :shared"
```

---

## Task 7: Move the shared value types

`MacroTotals` (B10) and the two pure preference files (B9) must precede `plan`, `food` and `coach`.

**Files:**
- Move: `core/model/MacroTotals.kt` → `shared/src/commonMain/kotlin/.../core/model/`
- Move: `data/preferences/UserProfilePreferences.kt`, `PlanPreferences.kt` → `shared/src/commonMain/kotlin/.../data/preferences/`

- [ ] **Step 1: Move the three files**

```bash
mkdir -p shared/src/commonMain/kotlin/com/zack/recomptracker/core/model \
         shared/src/commonMain/kotlin/com/zack/recomptracker/data/preferences
git mv app/src/main/java/com/zack/recomptracker/core/model/MacroTotals.kt \
       shared/src/commonMain/kotlin/com/zack/recomptracker/core/model/MacroTotals.kt
git mv app/src/main/java/com/zack/recomptracker/data/preferences/UserProfilePreferences.kt \
       shared/src/commonMain/kotlin/com/zack/recomptracker/data/preferences/UserProfilePreferences.kt
git mv app/src/main/java/com/zack/recomptracker/data/preferences/PlanPreferences.kt \
       shared/src/commonMain/kotlin/com/zack/recomptracker/data/preferences/PlanPreferences.kt
```

- [ ] **Step 2: Fix `ageYears()`**

`UserProfilePreferences.kt` ends with `ageYears()`, which uses `java.time.LocalDate`/`Period` and
already takes `today` as a parameter. Replace the body:

```kotlin
fun UserProfilePreferences.ageYears(today: LocalDate): Int? {
    val birth = birthDate?.let { LocalDate.parse(it) } ?: return null
    var age = today.year - birth.year
    if (today.monthNumber < birth.monthNumber ||
        (today.monthNumber == birth.monthNumber && today.dayOfMonth < birth.dayOfMonth)
    ) {
        age -= 1
    }
    return age.takeIf { it >= 0 }
}
```
with `import kotlinx.datetime.LocalDate` at the top.

- [ ] **Step 3: Verify and test**

```bash
grep -rn "java\.time\|java\.util\|String\.format" shared/src/commonMain/   # expect no output
./gradlew :shared:testDebugUnitTest :shared:iosSimulatorArm64Test :app:testDebugUnitTest
```
Expected: **all PASS.** `:app` call sites of `ageYears` may need a `java.time`→`kotlinx.datetime`
conversion — apply the pattern from Task 6 Step 4.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "refactor(ios): move MacroTotals + pure preference value types to :shared"
```

---

## Task 8: Move `plan` and `insight`

`insight` is the first consumer of `fullNameEnglish()`, and `PatternDetectorsTest` references
`internal` functions, so it **must** move too.

**Files:** Move `domain/plan`, `domain/insight`, and both test directories.

- [ ] **Step 1: Move sources and tests**

```bash
for p in plan insight; do
  git mv app/src/main/java/com/zack/recomptracker/domain/$p \
         shared/src/commonMain/kotlin/com/zack/recomptracker/domain/$p
  git mv app/src/test/java/com/zack/recomptracker/domain/$p \
         shared/src/androidUnitTest/kotlin/com/zack/recomptracker/domain/$p
done
```

- [ ] **Step 2: Apply the mapping, plus two specific edits**

In `insight/PatternDetectors.kt`, replace line 44's day-name call:
```kotlin
// was: it.first.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
it.first.date.dayOfWeek.fullNameEnglish()
```
and drop the `java.time.format.TextStyle` and `java.util.Locale` imports, adding
`import com.zack.recomptracker.shared.time.fullNameEnglish`.

In `plan/PlanGenerator.kt:24`, remove the `LocalDate.now()` default argument (B11) so the parameter
is required:
```kotlin
// was: today: LocalDate = LocalDate.now(),
today: LocalDate,
```
Then fix the `:app` call sites the compiler flags, passing `dateProvider.today()`.

- [ ] **Step 3: Verify and test**

```bash
grep -rn "java\.time\|java\.util\|String\.format" shared/src/commonMain/   # expect no output
./gradlew :shared:testDebugUnitTest :shared:iosSimulatorArm64Test :app:testDebugUnitTest
```
Expected: **all PASS**, including `PatternDetectorsTest` now running from `:shared`.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "refactor(ios): move domain/plan + domain/insight to :shared"
```

---

## Task 9: Move `rebalance`

🔴 763 LOC, the densest algorithm in the app, and it holds **two of the four inline fully-qualified
`java.time` uses** (`RebalanceEngine.kt:57` and `:426`) that an import rewrite will not catch.

**Files:** Move `domain/rebalance` + `app/src/test/java/.../domain/rebalance` (3 test files).

- [ ] **Step 1: Move sources and tests**

```bash
git mv app/src/main/java/com/zack/recomptracker/domain/rebalance \
       shared/src/commonMain/kotlin/com/zack/recomptracker/domain/rebalance
git mv app/src/test/java/com/zack/recomptracker/domain/rebalance \
       shared/src/androidUnitTest/kotlin/com/zack/recomptracker/domain/rebalance
```

- [ ] **Step 2: Fix the two inline fully-qualified uses first**

`RebalanceEngine.kt:57`:
```kotlin
// was: val daysSince = java.time.temporal.ChronoUnit.DAYS.between(referenceDate(mostRecent), input.today)
val daysSince = referenceDate(mostRecent).daysUntil(input.today).toLong()
```
`RebalanceEngine.kt:426`:
```kotlin
// was: (java.time.temporal.ChronoUnit.DAYS.between(from, end) + 1).toInt()
(from.daysUntil(end) + 1)
```
Note `daysUntil` returns `Int`; `ChronoUnit.DAYS.between` returned `Long`. Adjust the surrounding
types rather than adding casts that could truncate.

- [ ] **Step 3: Apply the import mapping to the rest of the package**

Files: `RebalanceEngine.kt`, `EffectiveTargets.kt`, `RebalanceEvaluationInput.kt`.
`EffectiveTargets.kt` also uses `ChronoUnit`.

- [ ] **Step 4: Make `EffectiveTargets` date parsing tolerant (fixes open bug P2-10)**

`EffectiveTargets.kt:61,87-88` use strict `LocalDate.parse` on persisted plan dates, so a corrupt
stored plan crash-loops the dashboard, coach and streak paths. While the file is open, wrap each:
```kotlin
private fun parseDateOrNull(raw: String): LocalDate? =
    try { LocalDate.parse(raw) } catch (_: IllegalArgumentException) { null }
```
and treat `null` as "this plan does not override" — the same outcome as an absent plan.
⚠️ `kotlinx.datetime.LocalDate.parse` throws `IllegalArgumentException`, **not**
`java.time.format.DateTimeParseException`. Catching the wrong type here is a silent no-op.

- [ ] **Step 5: Verify and test**

```bash
grep -rn "java\.time\|java\.util\|String\.format" shared/src/commonMain/   # expect no output
./gradlew :shared:testDebugUnitTest :shared:iosSimulatorArm64Test :app:testDebugUnitTest
```
Expected: **all PASS.** The rebalance suite is the most thorough in the codebase — if `size()`
regressed, it will say so.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "refactor(ios): move domain/rebalance to :shared; tolerate bad plan dates"
```

---

## Task 10: Split `ExerciseLibraryJson`, then move `workout` and `food`

`ExerciseLibraryJson.kt` is pure except its trailing `toEntity` extension, which is typed on
`ExerciseEntity`. Splitting that one function frees the whole `workout` package.

**Files:**
- Create: `app/src/main/java/.../data/local/entity/ExerciseEntityMapping.kt`
- Modify: `domain/workout/ExerciseLibraryJson.kt` (remove the extension)
- Move: `domain/workout` (all 10 files), `domain/food` (4 of 6 files)

- [ ] **Step 1: Extract the entity mapper into `:app`**

Create `app/src/main/java/com/zack/recomptracker/data/local/entity/ExerciseEntityMapping.kt`:

```kotlin
package com.zack.recomptracker.data.local.entity

import com.zack.recomptracker.domain.workout.ExerciseLibraryJson
import com.zack.recomptracker.domain.workout.FreeExerciseDbExerciseDto

/**
 * Maps the pure library DTO onto the Room entity. Lives in :app because ExerciseEntity is a Room
 * type; the DTO and its codec stay in :shared.
 */
fun FreeExerciseDbExerciseDto.toEntity(source: String, sourceVersion: String): ExerciseEntity =
    ExerciseEntity(
        source = source,
        sourceVersion = sourceVersion,
        externalId = id,
        name = name,
        category = category,
        force = force,
        level = level,
        mechanic = mechanic,
        equipment = equipment,
        primaryMuscles = ExerciseLibraryJson.encodeList(primaryMuscles),
        secondaryMuscles = ExerciseLibraryJson.encodeList(secondaryMuscles),
        instructions = ExerciseLibraryJson.encodeList(instructions),
        images = ExerciseLibraryJson.encodeList(images),
        userCreated = false,
    )
```

- [ ] **Step 2: Remove the extension and the Room import from the domain file**

In `domain/workout/ExerciseLibraryJson.kt`, delete the trailing `fun FreeExerciseDbExerciseDto.toEntity(...)`
block **and** the `import com.zack.recomptracker.data.local.entity.ExerciseEntity` line. Everything
above stays.

- [ ] **Step 3: Verify `:app` still builds before moving anything**

Run: `./gradlew :app:assembleDebug`
Expected: **BUILD SUCCESSFUL** — call sites resolve `toEntity` from its new package via the existing
`data.local.entity` import, or need one import added. Fix any that the compiler flags.

- [ ] **Step 4: Move `workout` (all files) and `food` (4 of 6)**

```bash
git mv app/src/main/java/com/zack/recomptracker/domain/workout \
       shared/src/commonMain/kotlin/com/zack/recomptracker/domain/workout
git mv app/src/test/java/com/zack/recomptracker/domain/workout \
       shared/src/androidUnitTest/kotlin/com/zack/recomptracker/domain/workout

mkdir -p shared/src/commonMain/kotlin/com/zack/recomptracker/domain/food
for f in FoodScaling MealEntryTypes MealImpact MealSuggester; do
  git mv app/src/main/java/com/zack/recomptracker/domain/food/$f.kt \
         shared/src/commonMain/kotlin/com/zack/recomptracker/domain/food/$f.kt
done
```

**`RecentFoods.kt` and `RecipeWithIngredients.kt` stay in `:app`** — their signatures take and return
Room entities. Both are verified leaves; nothing in `domain/` references them.

- [ ] **Step 5: Move only the food tests whose subjects moved**

`domain/food` splits across two modules, so its 5 test files must split the same way. Determine
which is which mechanically rather than by filename:

```bash
mkdir -p shared/src/androidUnitTest/kotlin/com/zack/recomptracker/domain/food
cd "/Users/zackalatrash/Desktop/Personal Dietitian"
for f in app/src/test/java/com/zack/recomptracker/domain/food/*.kt; do
  if grep -q "RecentFoods\|RecipeWithIngredients\|data\.local\.entity" "$f"; then
    echo "STAYS in :app  -> $f"
  else
    echo "MOVES to shared -> $f"
    git mv "$f" "shared/src/androidUnitTest/kotlin/com/zack/recomptracker/domain/food/$(basename "$f")"
  fi
done
```

A test that references a Room entity or either of the two files left behind must stay in `:app`;
everything else moves. Review the printed classification before continuing — if a file is classified
`MOVES` but fails to compile in Step 6, move it back.

- [ ] **Step 6: Apply the mapping, verify, test**

`workout` files using `java.time`: `ExerciseStatsCalculator.kt`, `MuscleTrainingAggregator.kt`,
`TrainingPlanBuilder.kt` (the last two also use `ChronoUnit`).

```bash
grep -rn "java\.time\|java\.util\|java\.io\|String\.format" shared/src/commonMain/   # expect no output
./gradlew :shared:testDebugUnitTest :shared:iosSimulatorArm64Test :app:testDebugUnitTest
```
Expected: **all PASS.**

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "refactor(ios): split ExerciseLibraryJson; move workout + food to :shared"
```

---

## Task 11: Move `coach`

The biggest package (16 files, 2,477 LOC). Holds the remaining two inline `java.time` uses, the
serializer to delete, and two `internal`-referencing tests.

**Files:** Move `domain/coach` (16 files) + `app/src/test/java/.../domain/coach` (13 files).
Delete `domain/coach/LocalDateTimeIsoSerializer.kt`.

- [ ] **Step 1: Move sources and tests**

```bash
git mv app/src/main/java/com/zack/recomptracker/domain/coach \
       shared/src/commonMain/kotlin/com/zack/recomptracker/domain/coach
git mv app/src/test/java/com/zack/recomptracker/domain/coach \
       shared/src/androidUnitTest/kotlin/com/zack/recomptracker/domain/coach
```

- [ ] **Step 2: Rewire `CoachDetectorSupport` onto the shared primitives**

In `shared/src/commonMain/kotlin/.../domain/coach/CoachDetectorSupport.kt`, replace the three
JVM-only helpers with delegations, keeping the object's public surface identical so no caller changes:

```kotlin
package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.domain.trend.MeasurementPoint
import com.zack.recomptracker.shared.format.bucket as sharedBucket
import com.zack.recomptracker.shared.format.bucketInt as sharedBucketInt
import com.zack.recomptracker.shared.format.formatFixed
import com.zack.recomptracker.shared.format.pct as sharedPct
import com.zack.recomptracker.shared.format.signed1 as sharedSigned1
import com.zack.recomptracker.shared.time.isoWeek as sharedIsoWeek
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

internal object CoachDetectorSupport {

    fun isoWeek(date: LocalDate): String = sharedIsoWeek(date)

    fun fmt(value: Double, decimals: Int = 2): String = formatFixed(value, decimals)

    fun signed1(value: Double): String = sharedSigned1(value)

    fun pct(value: Double): String = sharedPct(value)

    fun bucket(value: Double, step: Double, decimals: Int = 2): String =
        sharedBucket(value, step, decimals)

    fun bucketInt(value: Int, step: Int): String = sharedBucketInt(value, step)

    fun toMeasurementPoints(series: List<MetricPoint>): List<MeasurementPoint> =
        series.map { MeasurementPoint(date = it.date, value = it.value) }

    fun severityFromDistance(distance: Double, span: Double): Int {
        if (span <= 0.0) return 0
        return ((abs(distance) / span) * 100.0).roundToInt().coerceIn(0, 100)
    }
}
```

- [ ] **Step 3: Fix the inline fully-qualified use in `TrainingDetectors.kt:79`**

```kotlin
// was: private data class PrCandidate(val name: String, val date: java.time.LocalDate, …)
private data class PrCandidate(val name: String, val date: LocalDate, val latest: Double, val priorMax: Double)
```
with `import kotlinx.datetime.LocalDate` at the top.

- [ ] **Step 4: Delete the custom serializer and switch `RateLimiter` to the built-in**

```bash
rm shared/src/commonMain/kotlin/com/zack/recomptracker/domain/coach/LocalDateTimeIsoSerializer.kt
```
In `RateLimiter.kt`, remove the `@Serializable(with = LocalDateTimeIsoSerializer::class)` annotation
on `PushEvent.timestamp` (kotlinx-datetime's `LocalDateTime` is `@Serializable` already), and apply:
```kotlin
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
// now.toLocalTime() -> now.time
// now.toLocalDate() -> now.date
// LocalTime.of(22, 0) -> LocalTime(22, 0)
// LocalTime.of(7, 0)  -> LocalTime(7, 0)
```

- [ ] **Step 5: Prove the persisted push-history format is unchanged**

⚠️ `PushEvent` is persisted as JSON in the `coach_push_history` DataStore. If the serialized
`LocalDateTime` string shape changes, existing users' push history silently fails to decode (the
tolerant decoder returns an empty list, which would reset rate limiting).

Add to `shared/src/androidUnitTest/kotlin/.../domain/coach/PushEventFormatTest.kt`:
```kotlin
package com.zack.recomptracker.domain.coach

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PushEventFormatTest {
    @Test
    fun timestampSerializesAsIsoLocalDateTime() {
        val json = Json.encodeToString(LocalDateTime.serializer(), LocalDateTime(2026, 8, 1, 22, 0))
        assertEquals("\"2026-08-01T22:00\"", json)
    }

    @Test
    fun timestampWithSecondsRoundTrips() {
        val original = LocalDateTime(2026, 8, 1, 22, 0, 30)
        val encoded = Json.encodeToString(LocalDateTime.serializer(), original)
        assertEquals("\"2026-08-01T22:00:30\"", encoded)
        assertEquals(original, Json.decodeFromString(LocalDateTime.serializer(), encoded))
    }
}
```
Run: `./gradlew :shared:testDebugUnitTest --tests "*PushEventFormatTest*"`
Expected: **PASS.** If the encoded shape differs from Java's `LocalDateTime.toString()` (which omits
`:00` seconds), record it in STATUS — it is a real but low-stakes compat break (14-day retention).

- [ ] **Step 6: Apply the mapping to the remaining 9 java.time files, verify, test**

Files: `CoachContext.kt`, `CrossDomainDetectors.kt`, `CrossSignalDiscovery.kt`,
`RecoveryActivityDetectors.kt`, `SignalSelector.kt`, `TrainingDerivations.kt`.

```bash
grep -rn "java\.time\|java\.util\|java\.io\|String\.format" shared/src/commonMain/   # expect no output
./gradlew :shared:testDebugUnitTest :shared:iosSimulatorArm64Test :app:testDebugUnitTest
```
Expected: **all PASS**, including `CrossSignalDiscoveryDetectorTest` and `ExperimentEvaluationTest`
now compiling against `internal` members from inside `:shared`.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "refactor(ios): move domain/coach to :shared; drop custom LocalDateTime serializer"
```

---

## Task 12: Move `review` and `share`

The last two packages. Both depend only on already-moved code.

**Files:** Move `domain/review` (4 files, 2 tests), `domain/share` (3 files, 2 tests).

- [ ] **Step 1: Move sources and tests**

```bash
for p in review share; do
  git mv app/src/main/java/com/zack/recomptracker/domain/$p \
         shared/src/commonMain/kotlin/com/zack/recomptracker/domain/$p
  git mv app/src/test/java/com/zack/recomptracker/domain/$p \
         shared/src/androidUnitTest/kotlin/com/zack/recomptracker/domain/$p
done
```

- [ ] **Step 2: Apply the mapping**

Only `review/WeeklyTrainingBuilder.kt` uses `java.time` (`LocalDate`). `share` uses none.

- [ ] **Step 3: Final verification of the whole move**

```bash
grep -rn "java\.time\|java\.util\|java\.io\|String\.format" shared/src/commonMain/
echo "--- remaining in :app domain/ (expect only export + foodimport + 2 food files) ---"
find app/src/main/java/com/zack/recomptracker/domain -name "*.kt" | sort
./gradlew :shared:testDebugUnitTest :shared:iosSimulatorArm64Test :app:testDebugUnitTest :app:assembleDebug
```
Expected: first grep silent; the `find` lists **only** `export/BackupModels.kt`, the 7
`foodimport/*.kt`, `food/RecentFoods.kt` and `food/RecipeWithIngredients.kt`; all Gradle tasks
**BUILD SUCCESSFUL**.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "refactor(ios): move domain/review + domain/share to :shared — domain extraction complete"
```

---

## Task 13: Measure what moved

The gate needs numbers, not impressions.

- [ ] **Step 1: Collect the metrics**

```bash
echo "shared commonMain LOC: $(find shared/src/commonMain -name '*.kt' | xargs cat | wc -l)"
echo "shared commonMain files: $(find shared/src/commonMain -name '*.kt' | wc -l)"
echo "shared androidUnitTest LOC: $(find shared/src/androidUnitTest -name '*.kt' | xargs cat | wc -l)"
echo "app domain remaining LOC: $(find app/src/main/java/com/zack/recomptracker/domain -name '*.kt' | xargs cat | wc -l)"
echo "app test count: $(find app/src/test -name '*.kt' | wc -l)"
```

- [ ] **Step 2: Time a clean build of each target**

```bash
./gradlew clean
time ./gradlew :app:assembleDebug
time ./gradlew :shared:compileKotlinIosSimulatorArm64
```
Record both. Kotlin/Native link time is not incremental and is a real day-to-day cost — it is a gate input.

- [ ] **Step 3: Record in STATUS**

Append the metrics to the Phase 0 session-log entry in `docs/ios-port/STATUS.md`.

- [ ] **Step 4: Commit**

```bash
git add docs/ios-port/STATUS.md
git commit -m "docs(ios): record Phase 0 shared-module metrics"
```

---

## Task 14: Create the iOS repo and prove the XCFramework workflow

Two measurements at once: what the Kotlin API feels like from Swift, **and** whether the two-repo
sync workflow is tolerable. Both feed the gate. Build the real thing, not a throwaway — if this
workflow is annoying, that is a finding, and the gate is where it should surface.

**Files:**
- Modify: `shared/build.gradle.kts` (XCFramework output)
- Create (in the **sibling repo** `~/Desktop/RecompTracker-IOS/`): Xcode project, `scripts/sync-shared.sh`,
  `.gitignore`, `README.md`

- [ ] **Step 1: Configure an XCFramework output**

In `shared/build.gradle.kts`, add the import at the top of the file:
```kotlin
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
```
and inside `kotlin { }`, replace the bare `iosArm64()` / `iosSimulatorArm64()` lines with:
```kotlin
val xcf = XCFramework("Shared")
listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
    target.binaries.framework {
        baseName = "Shared"
        isStatic = true
        xcf.add(this)
    }
}
```

Run: `./gradlew :shared:assembleSharedDebugXCFramework`
Expected: **BUILD SUCCESSFUL**, output at `shared/build/XCFrameworks/debug/Shared.xcframework`.

⚠️ This builds **both** architectures, so it is slower than a single-target framework link. That is
the price of the two-repo split; Step 7 measures it.

- [ ] **Step 2: Create the sibling repo**

```bash
mkdir -p ~/Desktop/RecompTracker-IOS/scripts ~/Desktop/RecompTracker-IOS/Frameworks
cd ~/Desktop/RecompTracker-IOS
git init
```

- [ ] **Step 3: Write the sync script**

Create `~/Desktop/RecompTracker-IOS/scripts/sync-shared.sh`:

```bash
#!/usr/bin/env bash
# Rebuilds Shared.xcframework from the sibling Android repo and installs it here.
# Precondition: the Android repo sits beside this one.
set -euo pipefail

ANDROID_REPO="${ANDROID_REPO:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/Personal Dietitian}"
DEST="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/Frameworks"
CONFIG="${1:-debug}"

if [ ! -d "$ANDROID_REPO" ]; then
  echo "error: Android repo not found at: $ANDROID_REPO" >&2
  echo "       Set ANDROID_REPO=/path/to/repo, or place it beside this one." >&2
  exit 1
fi

echo "==> Building Shared.xcframework ($CONFIG) from $ANDROID_REPO"
if [ "$CONFIG" = "release" ]; then
  ( cd "$ANDROID_REPO" && ./gradlew :shared:assembleSharedReleaseXCFramework )
else
  ( cd "$ANDROID_REPO" && ./gradlew :shared:assembleSharedDebugXCFramework )
fi

SRC="$ANDROID_REPO/shared/build/XCFrameworks/$CONFIG/Shared.xcframework"
[ -d "$SRC" ] || { echo "error: expected framework at $SRC" >&2; exit 1; }

echo "==> Installing into $DEST"
rm -rf "$DEST/Shared.xcframework"
mkdir -p "$DEST"
cp -R "$SRC" "$DEST/"
echo "==> Done. Clean the Xcode build folder (Cmd-Shift-K) if symbols look stale."
```

```bash
chmod +x ~/Desktop/RecompTracker-IOS/scripts/sync-shared.sh
~/Desktop/RecompTracker-IOS/scripts/sync-shared.sh
```
Expected: `Frameworks/Shared.xcframework` exists.

- [ ] **Step 4: Add `.gitignore` and `README.md`**

`~/Desktop/RecompTracker-IOS/.gitignore`:
```gitignore
# Synced build product — regenerate with scripts/sync-shared.sh
Frameworks/Shared.xcframework/

.DS_Store
build/
DerivedData/
*.xcuserstate
**/xcuserdata/
*.xcworkspace/xcuserdata/
```

`~/Desktop/RecompTracker-IOS/README.md`:
```markdown
# Recomp Tracker — iOS

Native SwiftUI client. Shares its domain engines with the Android app via a Kotlin Multiplatform
XCFramework.

## Prerequisite

This repo is **not standalone-buildable**. The Android repo must sit beside it:

    ~/Desktop/
    ├── Personal Dietitian/     <- github.com/<you>/personal-dietitian
    └── RecompTracker-IOS/      <- this repo

Override with `ANDROID_REPO=/path/to/repo` if it lives elsewhere.

## Setup

    ./scripts/sync-shared.sh
    open RecompTracker.xcodeproj

Re-run `sync-shared.sh` whenever `domain/` changes on the Android side. Ordinary SwiftUI work needs
no Gradle at all.

Port plan and reference docs live in the Android repo under `docs/ios-port/`.
```

- [ ] **Step 5: Create the Xcode project and link the framework**

In Xcode: File → New → Project → iOS App. Product name `RecompTracker`, interface **SwiftUI**,
language **Swift**, saved to `~/Desktop/RecompTracker-IOS/`. Set the deployment target to
**iOS 26.0** (D5).

Target → General → Frameworks, Libraries, and Embedded Content → **+** → Add Other → Add Files →
select `Frameworks/Shared.xcframework`. Set it to **Do Not Embed** (the framework is static).

Then Build Settings → Framework Search Paths → add `$(SRCROOT)/Frameworks`.

- [ ] **Step 6: Call three domain functions from SwiftUI**

Replace `ContentView.swift` with:

```swift
import SwiftUI
import Shared

struct ContentView: View {
    private let today = Kotlinx_datetimeLocalDate(year: 2026, monthNumber: 8, dayOfMonth: 1)

    var body: some View {
        List {
            Section("Formatting") {
                Text("signed1(-0.15) = \(DecimalFormatKt.signed1(value: -0.15))")
                Text("formatFixed(-0.25, 1) = \(DecimalFormatKt.formatFixed(value: -0.25, decimals: 1))")
            }
            Section("ISO week") {
                Text("isoWeek(2026-08-01) = \(IsoWeekKt.isoWeek(date: today))")
            }
            Section("Plan math") {
                Text(planSummary)
            }
        }
    }

    private var planSummary: String {
        let targets = PlanCalculator().calculate(
            weightKg: 82.0, heightCm: 183, ageYears: 34,
            sex: BiologicalSex.male, activityLevel: ActivityLevel.moderate,
            goal: FitnessGoal.recomp
        )
        return "\(targets.targetCalories) kcal · \(targets.targetProteinG)P"
    }
}
```

⚠️ **The exact Swift symbol names above are predictions, not verified.** Kotlin top-level functions
land in a `<FileName>Kt` class, and `kotlinx.datetime.LocalDate` is exported under a mangled prefix.
Use Xcode autocomplete against the real framework and **correct the code to match**. If
`PlanCalculator.calculate` has default arguments, Swift requires all of them — supply every one.

- [ ] **Step 7: Run in the simulator and record the ergonomics**

Build and run on an iOS 26 simulator. Confirm the printed values match
`docs/ios-port/phases/phase-0-golden-corpus.txt`.

Write down, in `docs/ios-port/STATUS.md`:
- The **actual** Swift symbol names you had to use versus the predictions above
- Whether autocomplete was usable
- Whether enums (`BiologicalSex`, `FitnessGoal`) came through as Swift enums or as classes
- Whether any default argument forced you to pass values you did not care about
- Honest wall-clock time from empty folder to a running app

This is the qualitative half of the gate. Be specific; a future session cannot re-derive it.

- [ ] **Step 8: Time a realistic resync — this is gate criterion 8**

Simulate the daily workflow after a domain change:

```bash
# 1. Make a trivial change in the Android repo
cd "/Users/zackalatrash/Desktop/Personal Dietitian"
# e.g. add a doc comment to shared/src/commonMain/.../domain/adjustment/AdjustmentEngine.kt

# 2. Resync and rebuild iOS
cd ~/Desktop/RecompTracker-IOS
time ./scripts/sync-shared.sh
# 3. Rebuild in Xcode (Cmd-B) and note whether a clean was required
```
Record the wall-clock time and whether Xcode picked up the new framework without a manual clean.
**Revert the trivial change afterwards.**

- [ ] **Step 9: Commit both repos**

```bash
cd ~/Desktop/RecompTracker-IOS
git add -A
git commit -m "feat: bare SwiftUI app consuming the shared Kotlin domain core"

cd "/Users/zackalatrash/Desktop/Personal Dietitian"
git add shared/build.gradle.kts docs/ios-port/STATUS.md
git commit -m "feat(ios): publish Shared.xcframework for the external iOS repo"
```

---

## Task 15: Update the port documentation

- [ ] **Step 1: Update `parity-ledger.md`**

Mark the Foundations rows complete: `:shared` module, `java.time` migration, ISO-week + formatters.
Update the summary counts.

- [ ] **Step 2: Update `STATUS.md`**

Set current phase, tick Phase 0 on the board, add the session-log entry with metrics and surprises,
and list anything under *Needs visual check*.

- [ ] **Step 3: Update `reference/domain-port-notes.md` §3**

Mark blockers B1–B11 resolved, or record what actually happened where it differed from the estimate.

- [ ] **Step 4: Commit**

```bash
git add docs/ios-port
git commit -m "docs(ios): Phase 0 complete — update status, ledger, and blocker list"
```

---

## Task 16: The gate — decide, and record it

**This is the point of the phase.** Do not skip it, and do not default to "keep going because we
built it."

- [ ] **Step 1: Answer each criterion with evidence**

| # | Criterion | Pass if | Result |
|---|---|---|---|
| 1 | All 1,300 Android tests still green | `:app:testDebugUnitTest` BUILD SUCCESSFUL | |
| 2 | Shared tests green on **both** JVM and Kotlin/Native | both test tasks pass | |
| 3 | Golden corpus reproduced **exactly** on iOS | `GoldenFormatTest` passes on `iosSimulatorArm64` | |
| 4 | `java.time` conversion sites in `:app` | ≤ 10 — more means the boundary is leaky | |
| 5 | Kotlin/Native compile time | < 3 min clean — more is a real daily tax | |
| 6 | Swift call sites readable without a wrapper layer | subjective; Task 14 Step 7 notes | |
| 7 | No Kotlin/AGP/Gradle upgrade **and** no unacceptable dependency downgrade | see 7a below | |
| 7a | *(known, resolved)* Kotlin/Native klibs are strictly ABI-gated, so staying on Kotlin 2.2.21 **caps every `commonMain` dependency** at releases built with Kotlin ≤ 2.2.x. kotlinx-serialization was pinned **1.11.0 → 1.9.0 project-wide** (56 files in `:app`). No post-1.9.0 API is used and all tests pass — but every future shared dependency needs the same check. Weigh at the gate whether this standing tax is preferable to moving to Kotlin 2.3.x+. | judgement | accepted |
| 8 | **Two-repo resync is tolerable** | `sync-shared.sh` + Xcode rebuild < 2 min, one command, no manual clean needed (Task 14 Step 8) | |

- [ ] **Step 2: Decide**

**All 7 pass → keep the shared core.** Proceed to Phase 1.

**3 fails → stop and investigate.** A bit-exactness failure on iOS is the one result that
invalidates the whole approach, because the entire argument for sharing `domain/` is guaranteed
convergence. Do not paper over it.

**1, 2 or 7 fails → the cost is higher than modelled.** Seriously consider the Swift-port
alternative in `reference/domain-port-notes.md` §6.

**4, 5, 6 or 8 fails → judgement call.** Weigh against the fallback: porting `domain/` to Swift
test-first is a known-cost, known-tooling path that also delivers a working iOS app — and note that
**8 failing is a stronger signal than it looks.** The whole value of the shared core is "one place
to evolve the engines"; if propagating a domain change across two repos is slow or fiddly, that
value is largely gone and the Swift port becomes the better trade.

- [ ] **Step 3: Record the decision either way**

Append to `docs/ios-port/decisions.md` as **D10**, with the seven results inline. If the answer is
"revert to a Swift port", say so explicitly, and note that Tasks 5–12 are cleanly revertible
(`git revert` the move commits) while Tasks 2 and 4 — the golden corpus and the formatter semantics —
**stay valuable either way**, because a Swift port needs exactly the same golden values.

- [ ] **Step 4: Commit and merge**

```bash
git add docs/ios-port/decisions.md
git commit -m "docs(ios): record Phase 0 gate decision"
```

Then merge the phase branch to `develop` per D8.

---

## Rollback

Every task is a separate commit and Tasks 5–12 are pure `git mv` + import rewrites. To abandon the
shared core:

```bash
git revert --no-commit <first-move-sha>..<last-move-sha>
git commit -m "revert(ios): abandon :shared module after Phase 0 gate"
```

Keep `docs/ios-port/phases/phase-0-golden-corpus.txt` and the `GoldenFormatTest` values regardless —
they are the specification for whichever implementation ships.
