# Logging Consistency UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Surface the logging-consistency signal on the Dashboard (as "N / 14" food-logged days) and on the Progress screen (as a per-day logging chart), paired with adherence, and reconcile the existing loose "Days logged" count to the real 14-day windowed metric.

**Architecture:** Both ViewModels already compute the per-day food-logged data they need (`nutritionDays` on Dashboard, `calValues` on Progress). This change adds a windowed count to `DashboardUiState`, a `logging` `ChartSeries` to `ProgressUiState`, and updates the screens to display them. No new domain code — `AdherenceCalculator.loggingConsistency` already exists and is tested. Pure presentation + state wiring.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit4, Gradle.

**Reference spec:** `docs/superpowers/specs/2026-06-11-logging-consistency-ui-design.md`

**Working directory (isolated worktree):** `/Users/zackalatrash/Desktop/Personal Dietitian/.claude/worktrees/adherence-redesign` — run everything there; the branch is `adherence-redesign`.

**Commands:**
- Type-check: `./gradlew :app:compileDebugKotlin`
- Unit tests: `./gradlew :app:testDebugUnitTest`

**Testing note (read before starting):** The Dashboard/Progress ViewModels are not unit-tested via flow harnesses in this repo (only domain logic and static/default state are tested — see `DashboardViewModelMessagesTest`). This plan follows that convention: the new field/series are verified by `compileDebugKotlin`, the full existing unit suite staying green, and the already-passing `AdherenceCalculatorTest` (which covers the identical `count { calories > 0 }` logic). We do NOT stand up a new VM flow-harness. Each task's "verify" step is a compile + suite run.

---

### Task 1: Dashboard — windowed "N / 14" logged-days metric

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardViewModel.kt` (state field ~62, populate ~183/236, engine comment ~199)
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt` (display sites ~485, ~702)

- [ ] **Step 1: Add the state field**

In `DashboardViewModel.kt`, in the `DashboardUiState` data class, REPLACE the line:
```kotlin
    val daysLogged: Int = 0,
```
with:
```kotlin
    val loggedDaysInWindow: Int = 0,   // food-logged days within the last 14 (matches adherence window)
```

- [ ] **Step 2: Compute it where adherence is computed**

In `DashboardViewModel.kt`, find the line (~183):
```kotlin
        val adherence    = adherenceCalculator.calculate(nutritionDays, preferences.targetCalories)
```
Add directly below it:
```kotlin
        val loggedDaysInWindow = nutritionDays.count { it.calories > 0 }
```

- [ ] **Step 3: Add a clarifying comment at the engine-input count (so the two counts aren't confused)**

In `DashboardViewModel.kt`, find the `AdjustmentInput(...)` construction line (~199):
```kotlin
            daysLogged = loggedDates.count { LocalDate.parse(it) in last14Start..today },
```
Insert a comment line immediately ABOVE it:
```kotlin
            // Engine data-sufficiency gate: counts ANY logged day (incl. body-only) in the window.
            // Distinct from the UI's loggedDaysInWindow, which counts only food-logged days.
            daysLogged = loggedDates.count { LocalDate.parse(it) in last14Start..today },
```

- [ ] **Step 4: Populate the state field**

In `DashboardViewModel.kt`, in the `return DashboardUiState(...)` block (~236), REPLACE:
```kotlin
            daysLogged = loggedDates.size,
```
with:
```kotlin
            loggedDaysInWindow = loggedDaysInWindow,
```

- [ ] **Step 5: Update the first display site**

In `DashboardScreen.kt` (~485), REPLACE:
```kotlin
                value = "${state.daysLogged}",
```
with:
```kotlin
                value = "${state.loggedDaysInWindow} / 14",
```

- [ ] **Step 6: Update the second display site**

In `DashboardScreen.kt` (~702), REPLACE:
```kotlin
                StatRow("Logged days", state.daysLogged.toString())
```
with:
```kotlin
                StatRow("Logged days", "${state.loggedDaysInWindow} / 14")
```

- [ ] **Step 7: Verify no stale `daysLogged` state references remain**

Run: `grep -rn "state.daysLogged\|\.daysLogged" app/src/main/java/com/zack/recomptracker/ui/dashboard/`
Expected: the only match is `AdjustmentInput`'s `daysLogged =` assignment inside `DashboardViewModel.kt` (the engine input, which is correct and stays). No `state.daysLogged` / UiState references remain. If any remain, update them to `loggedDaysInWindow`.

- [ ] **Step 8: Type-check and run the full suite**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass. (`DashboardUiState()` is still default-constructible, so `DashboardViewModelMessagesTest` stays green.)

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardViewModel.kt app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt
git commit -m "feat: dashboard shows food-logged days as N/14 over the adherence window

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Progress — logging chart + layout tidy

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/progress/ProgressViewModel.kt` (state field ~43, build series ~154)
- Modify: `app/src/main/java/com/zack/recomptracker/ui/progress/ProgressScreen.kt` (sections ~121-128)

- [ ] **Step 1: Add the `logging` field to `ProgressUiState`**

In `ProgressViewModel.kt`, find in the `ProgressUiState` data class (~43):
```kotlin
    val adherence: ChartSeries = ChartSeries("Adherence", "%", emptyList()),
```
Add directly below it:
```kotlin
    val logging: ChartSeries = ChartSeries("Logging", "%", emptyList()),
```

- [ ] **Step 2: Build the logging series in the state-construction block**

In `ProgressViewModel.kt`, find the `adherence = ChartSeries(...)` block inside the `ProgressUiState(...)` construction (~154-159):
```kotlin
                    adherence = ChartSeries(
                        "Adherence", "%", adherenceValues,
                        currentValue = adherenceLast,
                        trendLabel = adherenceLast?.let { "${"%.0f".format(it)}%" } ?: "",
                        trendIsGood = (adherenceLast ?: 0f) >= 80f,
                    ),
```
Add a new `logging = ChartSeries(...)` block immediately AFTER that closing `),`:
```kotlin
                    logging = run {
                        val loggedFlags = calValues.map { if (it > 0f) 100f else 0f }
                        val pct = if (loggedFlags.isNotEmpty()) {
                            loggedFlags.count { it > 0f }.toFloat() / loggedFlags.size * 100f
                        } else 0f
                        ChartSeries(
                            "Logging", "%", loggedFlags,
                            currentValue = pct,
                            trendLabel = "${"%.0f".format(pct)}%",
                            trendIsGood = pct >= 80f,
                        )
                    },
```

- [ ] **Step 3: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Update the Progress screen layout**

In `ProgressScreen.kt`, find the Nutrition + Performance section block (~121-128):
```kotlin
            // Nutrition section
            item { SectionLabel("Nutrition") }
            item { FeaturedChartCard(state.calories) }
            item { MiniChartPair(state.protein, state.carbs) }
            item { ShortChartCard(state.fat) }

            // Performance section
            item { SectionLabel("Performance") }
            item { MiniChartPair(state.adherence, state.lifts) }
```
REPLACE it with:
```kotlin
            // Nutrition section
            item { SectionLabel("Nutrition") }
            item { FeaturedChartCard(state.calories) }
            item { MiniChartPair(state.protein, state.carbs) }
            item { ShortChartCard(state.fat) }
            item { MiniChartPair(state.adherence, state.logging) }

            // Performance section
            item { SectionLabel("Performance") }
            item { ShortChartCard(state.lifts) }
```

- [ ] **Step 5: Type-check and run the full suite**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/progress/ProgressViewModel.kt app/src/main/java/com/zack/recomptracker/ui/progress/ProgressScreen.kt
git commit -m "feat: progress screen shows logging consistency chart beside adherence

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Final verification

- [ ] **Full suite + type-check**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL on both.

---

## Self-Review Notes

- **Spec coverage:** Dashboard "N / 14" + reconcile loose count → Task 1 (replaces `daysLogged` with `loggedDaysInWindow`, both display sites, engine count left untouched with clarifying comment). Progress logging `ChartSeries` + layout move → Task 2. The metric definition (food-logged over 14 / over range) is honored in both.
- **Testing deviation (transparent):** the spec's "extend the DashboardViewModel test harness" assumed a flow-harness that does not exist in this repo (VMs aren't unit-tested via fakes here). The plan instead verifies via compile + the full green suite + the already-tested domain `loggingConsistency`/counting logic, matching repo convention. No behavior-bearing logic is left uncovered (the count is the domain function's numerator). If a heavier harness is wanted, that's a separate follow-up.
- **Type consistency:** `loggedDaysInWindow: Int` used identically in state def, populate, and both display sites; `logging: ChartSeries` matches the existing `ChartSeries(title, unit, values, currentValue, trendLabel, trendIsGood)` signature; `trendLabel` uses the same `"%.0f".format(...)` idiom as the adjacent adherence series.
- **No placeholders:** every step shows exact before/after.
