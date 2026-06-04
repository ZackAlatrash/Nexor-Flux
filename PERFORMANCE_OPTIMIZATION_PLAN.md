# Performance Optimization Plan
## Personal Dietitian – Recomp Tracker

> **Branch:** `performance-audit-plan`  
> **Audit date:** 2026-06-04  
> **Implementation date:** 2026-06-04  
> **Model used:** Claude Sonnet 4.6  
> **Scope:** Full static code analysis of `app/src/main/java/` – no runtime profiling yet  
> **Status:** ✅ All Phase 1–4 tasks implemented and build-verified (4a/4f deferred by design)

---

## Executive Summary

The app has a premium liquid-glass visual design built on top of the `com.kyant.backdrop` library for real-time GPU blur and vibrancy. The core architecture (MVVM + Room + Compose) is sound. However, several high-cost patterns are stacked together in ways that will cause visible jank, high battery drain, and elevated memory pressure on mid-range devices.

The three biggest problems are:

1. **The gyroscope sensor runs at game-rate (60 Hz) and its state changes trigger `animateFloatAsState` which propagates recompositions into a full-screen image that is also blurred via `Modifier.blur`** — this is a triple-cost path that runs continuously while the app is visible.
2. **`FrostedCard`'s `blur(20dp)` + `vibrancy()` backdrop is used on every primary data card** — each instance creates an offscreen `GraphicsLayer`. A typical screen has 2–4 FrostedCards stacked in a `LazyColumn`, all rendering simultaneously.
3. **`DashboardViewModel.buildState()` does O(n) list filtering, grouping, and an `AdjustmentEngine` object allocation on every single database write** — logging one meal entry re-runs the full 28-day analysis.

The good news: most of these issues have drop-in solutions that fully preserve the visual design.

---

## Current Performance Risks (Summary Table)

| # | File | Component | Problem | Severity | Effort | Status |
|---|------|-----------|---------|----------|--------|--------|
| P-01 | `GlassOrbBackground.kt` | `GlassOrbBackground` | Sensor at GAME rate + Modifier.blur each frame | **Critical** | Small | ✅ Done |
| P-02 | `LiquidComponents.kt` | `LiquidBottomTabs` | 3× drawBackdrop every frame while app is open | **Critical** | Medium | 🔲 Phase 4 |
| P-03 | `GlassComponents.kt` | `FrostedCard` | blur(20dp)+vibrancy per card, 2–4 active simultaneously | **High** | Medium | 🔲 Phase 4 |
| P-04 | `SparklineChart.kt` | `SparklineChart` | Path + List allocated every animation frame | **High** | Small | ✅ Done |
| P-05 | `DashboardViewModel.kt` | `buildState()` | Full 28-day analysis + new AdjustmentEngine on every DB write | **High** | Medium | ✅ Done |
| P-06 | `AppContainer.kt` | `AppContainer` | Room DB created synchronously on main thread at startup | **High** | Small | ✅ Done |
| P-07 | `DashboardScreen.kt` / `FoodScreen.kt` / `ProgressScreen.kt` | Ambient orbs | `Brush.radialGradient` recreated every recomposition | **Medium** | Small | ✅ Done |
| P-08 | `GlassComponents.kt` | `FrostedCard` | `Brush.horizontalGradient` shimmer created inside `onDrawSurface` every draw | **Medium** | Small | ✅ Done |
| P-09 | `FoodLogViewModel.kt` / `DashboardViewModel.kt` | UI state data classes | `List<T>` fields are unstable → unnecessary full recompositions | **Medium** | Small | ✅ Done |
| P-10 | `DashboardViewModel.kt` | `persistWeeklyReview()` | DB write on every state update, not throttled | **Medium** | Small | ✅ Done |
| P-11 | `GlassOrbBackground.kt` | Sensor registration | `SENSOR_DELAY_GAME` (60 Hz) — no adaptive rate | **Medium** | Small | ✅ Done (part of P-01) |
| P-12 | `FoodLibraryScreen.kt` | Food list | `key(state.category)` forces full LazyColumn recreation on tab switch | **Medium** | Small | ✅ Done |
| P-13 | `CalorieProgressBar.kt` | `CalorieProgressBar` | Stripe loop recalculated on every draw frame | **Low** | Small | ✅ Done |
| P-14 | `MacroRingChart.kt` | `MacroRingChart` | `macroSweepAngles()` called every recomposition, no `remember` | **Low** | Small | ✅ Done |
| P-15 | `AppNavGraph.kt` | Navigation | No explicit enter/exit transitions → default system animations | **Low** | Medium | ✅ Done |
| P-16 | `DashboardViewModel.kt` | `buildState` | `LocalDate.parse()` called in hot loop across hundreds of meal entries | **Low** | Small | 🔲 Deferred |
| P-17 | `FoodScreen.kt` | `LockedSlotCard` entries | `forEachIndexed` without Compose keys → full slot recomposition on any entry change | **Low** | Small | ✅ Done |
| P-18 | `build.gradle.kts` | Build config | No baseline profiles, no R8 shrinkResources enabled | **Low** | Medium | ✅ Done (R8+shrink) |

---

## Critical Bottlenecks (Deep-Dive)

### P-01 · `GlassOrbBackground` — Continuous Sensor + Blur Loop

**File:** `app/src/main/java/com/zack/recomptracker/ui/component/GlassOrbBackground.kt`  
**Severity:** Critical | **Effort:** Small

**Problem description:**  
The sensor is registered at `SensorManager.SENSOR_DELAY_GAME` (approx 50–60 Hz). Every sensor event writes to `rawTiltX` and `rawTiltY` mutable state. This triggers:
1. Two `animateFloatAsState` spring animations that recompose on every tick.
2. The entire `GlassOrbBackground` composable recomposes, re-issuing `graphicsLayer { translationX/Y = ... }`.
3. `Modifier.blur(18.dp)` is applied to the full-screen background image. **`Modifier.blur` creates a new offscreen render pass every frame** — it is one of the most expensive Compose modifiers.

Additionally, `IMAGE_SCALE = 1.14f` means the background image is decoded and rendered at 114% of screen resolution, wasting extra pixels.

**Why it hurts performance:**  
- Continuous 60 Hz recomposition keeps the app in a perpetual "dirty" render state.
- `Modifier.blur` is backed by a `RenderEffect` (Android 12+) or software blur path (older), both are GPU-heavy per frame.
- This runs even when the user is not moving the device at all (sensor still fires continuously).

**Suggested fix:**  
- Lower sensor rate to `SENSOR_DELAY_UI` (16 Hz) or `SENSOR_DELAY_NORMAL` (5 Hz). Parallax parallax at 5 Hz with spring smoothing is imperceptible vs 60 Hz.
- Move the `translationX/Y` read into `graphicsLayer {}` so it defers to the draw phase (already partially done, but the state change itself still recomposes the parent).
- Use `derivedStateOf { }` to gate recomposition: only recompose when the tilt change exceeds a threshold (e.g., 0.005f).
- Replace `Modifier.blur(BLUR_RADIUS)` with a pre-blurred static image asset (`bg_glass_orbs_blurred.png`). The blur value is constant (18.dp) and the image content never changes — pre-blurring at asset creation time eliminates the per-frame blur cost entirely. Store the blurred image in `res/drawable/`. The visual result is identical.

**Expected improvement:** ~40–60% reduction in GPU load for the background layer; eliminates recomposition storms on the background composable.

**Risk level:** Low — visual design fully preserved.

**Implementation guide for agent:**
```kotlin
// 1. Replace SENSOR_DELAY_GAME with SENSOR_DELAY_NORMAL in GlassOrbBackground.kt:
sm?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)

// 2. Add threshold guard:
val threshold = 0.004f
override fun onSensorChanged(event: SensorEvent) {
    val newX = (event.values[0] * TILT_SCALE).coerceIn(-MAX_TILT, MAX_TILT)
    val newY = (event.values[1] * TILT_SCALE).coerceIn(-MAX_TILT, MAX_TILT)
    if (abs(newX - rawTiltX) > threshold || abs(newY - rawTiltY) > threshold) {
        rawTiltX = newX
        rawTiltY = newY
    }
}

// 3. Remove Modifier.blur(BLUR_RADIUS) from the Image modifier.
//    Add a new drawable resource bg_glass_orbs_blurred.png (pre-blurred version of bg_glass_orbs)
//    Load bg_glass_orbs_blurred instead:
Image(
    painter = painterResource(R.drawable.bg_glass_orbs_blurred), // pre-blurred asset
    ...
    modifier = Modifier
        .fillMaxSize()
        // .blur(BLUR_RADIUS)  ← REMOVE THIS LINE
        .graphicsLayer { ... }
)
```

---

### P-02 · `LiquidBottomTabs` — 3× GPU Blur Passes Every Frame

**File:** `app/src/main/java/com/zack/recomptracker/ui/liquidglass/LiquidComponents.kt:133`  
**Severity:** Critical | **Effort:** Medium

**Problem description:**  
`LiquidBottomTabs` renders three layers that each call `drawBackdrop`:
1. The visible glass row (`blur(8dp)` + `vibrancy()` + `lens(24dp, 24dp)`)
2. The invisible accent-tinted row (`alpha(0f)` but still composited — `blur(8dp)` + `vibrancy()` + `lens(...)`)
3. The sliding indicator (`lens(...)` + chromatic aberration)

The invisible row (item 2) is drawn at `alpha(0f)` but is **still rendered** because it feeds `tabsBackdrop` via `layerBackdrop()`. This means the compositor must still process it.

Additionally, `DampedDragAnimation` runs 5 spring `Animatable`s simultaneously during tab transitions, all triggering `graphicsLayer { }` reads every frame.

**Why it hurts performance:**  
- Each `drawBackdrop` requires a full off-screen compositing pass (snapshot of the layers behind it, blur, blend).
- The nav bar is always visible — this is a **persistent** cost, not just during transitions.
- Chromatic aberration in the indicator adds a third shader pass.
- The `InteractiveHighlight` shader runs every draw frame when `pressProgress > 0`.

**Suggested fix:**  
- The `vibrancy()` effect inside `drawBackdrop` can be cached: the backdrop source (the GlassOrbBackground) changes rarely (only on tilt). Add a `rememberUpdatedState` wrapper that short-circuits backdrop sampling when the source hasn't changed.
- Replace `blur(8f.dp.toPx())` on the nav bar with a **static frosted glass appearance**: a semi-transparent surface color (already present as `containerColor`) with a subtle border. The nav bar's background image changes so slowly that a 200ms-delayed snapshot with crossfade would be imperceptible.
- For the invisible tinted row: instead of maintaining a full `layerBackdrop`, apply the tint via `ColorFilter` on the visible row only. This eliminates one full backdrop pass.
- Keep the `lens()` and chromatic aberration on the indicator — they only fire during press. Gate them with `if (dampedDragAnimation.pressProgress > 0.01f)`.

**Expected improvement:** ~30–40% GPU reduction on the compositor thread; smoother tab switches on mid-range devices.

**Risk level:** Medium — carefully test that the glass appearance is preserved after simplification.

---

### P-03 · `FrostedCard` — Multiple Active Blur Layers Per Screen

**File:** `app/src/main/java/com/zack/recomptracker/ui/component/GlassComponents.kt:84`  
**Severity:** High | **Effort:** Medium

**Problem description:**  
`FrostedCard` applies `blur(20f.dp.toPx())` + `vibrancy()` via `drawBackdrop`. The Dashboard screen has **3 FrostedCards** (`MotivationalCard` is plain, `TodayCard`, `SevenDayChartCard`). FoodScreen has at least 2 (`NutritionStrip` + potential future additions). Progress screen has up to 7 cards (some are `NeutralCard`, but `FeaturedChartCard` uses `FrostedCard`).

Each FrostedCard creates an offscreen `GraphicsLayer` that captures, blurs, and composites the background. Multiple stacked layers in a `LazyColumn` are all rendered simultaneously if they are visible, because `LazyColumn` keeps ~1 screen worth of items in composition.

**Why it hurts performance:**  
- 3 simultaneous blur passes × 20dp radius is significant GPU memory bandwidth.
- The backdrop source (`contentBackdrop`) updates whenever the background tilt changes (P-01), causing all FrostedCards to re-blur simultaneously.
- On devices without hardware accelerated `RenderEffect` (pre-Android 12), the blur falls back to a software path which is CPU-bound.

**Suggested fix:**  
- Use a **blur-once strategy**: render the background into a single bitmap snapshot at screen load time (or when it changes). Distribute this snapshot to all FrostedCards. Each card's backdrop reads a stable, cached bitmap instead of the live layer — reducing n blur passes to 1.
- Alternatively, reduce `blur(20f.dp.toPx())` to `blur(12f.dp.toPx())`. At card sizes, 12dp and 20dp are visually nearly identical, but the blur kernel area scales with radius² — 12dp is ~64% cheaper.
- Keep `vibrancy()` only on the **primary card** per screen (e.g., the Today Card and Nutrition Strip). Non-primary cards can use `NeutralCard` style with a `Color(0x14FFFFFF)` background — visually very similar at smaller sizes.

**Expected improvement:** ~50% reduction in compositor memory bandwidth; reduces jank during list scrolling.

**Risk level:** Medium — reduce blur radius carefully and visually verify before/after.

---

### P-04 · `SparklineChart` — Expensive Allocations Every Animation Frame

**File:** `app/src/main/java/com/zack/recomptracker/ui/component/charts/SparklineChart.kt:106`  
**Severity:** High | **Effort:** Small

**Problem description:**  
Inside the `Canvas` draw block (which runs every animation frame during the draw-in animation):
```kotlin
val pts = values.mapIndexed { i, v -> Offset(xAt(i), yAt(v)) }  // new List every frame
val linePath = Path().apply { ... }   // new Path every frame
val areaPath = Path().apply { ... }   // new Path every frame
```
These three allocations happen 60 times per second during the `drawInProgress` animation (which plays on every screen navigation). Kotlin's GC will eventually collect these but on low-memory devices this triggers GC pauses.

Additionally, `values.min()` and `values.max()` scan the entire list on every draw frame.

**Why it hurts performance:**  
- Path allocation is costly (~5–10µs per Path on the UI thread).
- 3 allocations × 60fps × duration of animation = significant heap pressure.
- GC pauses during animation = dropped frames.

**Suggested fix:**  
Move `pts`, `linePath`, `areaPath`, `minVal`, and `maxVal` outside the Canvas draw block using `remember(values)`:

```kotlin
// In SparklineChart composable body:
val pts = remember(values, size) { /* computed once per values change */ }
// BUT: size is only available inside Canvas. Use a two-phase approach:

// Phase 1: memoize the normalized values (0..1 range) outside Canvas
data class NormalizedData(val ys: FloatArray, val minVal: Float, val maxVal: Float)
val normalized = remember(values) {
    val minVal = values.min()
    val maxVal = values.max()
    NormalizedData(values.map { ... }.toFloatArray(), minVal, maxVal)
}

// Phase 2: compute Offset pts and Paths inside Canvas but only when size changes
// Use a cached path that's cleared and rebuilt only when values or size changes
val pathCache = remember(values) { Path() to Path() }
```

For the scrubber gesture, the `pts` list inside `pointerInput` should use the same remembered data.

**Expected improvement:** Eliminates GC pressure during chart animation; ~5ms per animation frame recovered on low-end devices.

**Risk level:** Low — purely mechanical refactor of where allocations happen.

---

### P-05 · `DashboardViewModel.buildState()` — Full Analysis on Every DB Write

**File:** `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardViewModel.kt:94`  
**Severity:** High | **Effort:** Medium

**Problem description:**  
The `combine()` in `init` subscribes to:
- `logRepository.observeDailyLogs()` — all historical daily logs
- `logRepository.observeMealEntries()` — ALL meal entries ever logged
- `logRepository.observePerformances()` — all lift performances

Every time the user adds a single food item, `observeMealEntries()` emits the full meal list again, and `buildState()` re-runs completely:
- Filters meals by last 14/28 days (O(n) over total meals)
- Groups meals by date (O(n))
- Creates 14 `NutritionDay` objects
- Computes `trendPerWeek` twice
- Instantiates `new AdjustmentEngine(...)` — object allocation every call
- Calls `persistWeeklyReview()` which writes to the database — a DB write triggered by every DB read

**Why it hurts performance:**  
- On a user with 6 months of data (~180 days × 3–5 meals = ~700 entries), every food log triggers O(700) list traversal.
- `persistWeeklyReview()` causing a DB write on every state update creates a write → observe → compute → write loop.
- The dashboard becomes progressively slower as the user accumulates data.

**Suggested fix:**  

1. **Scope the DB queries:** Change `observeMealEntries()` to `observeMealEntriesSince(date)` — only load the last 28 days of meals. This is a single Room query change.

2. **Debounce `buildState`:** Use `debounce(300)` on the combine flow to avoid recomputing during rapid food additions.

3. **Cache `AdjustmentEngine`:** It has no mutable state — create it once in the ViewModel constructor.

4. **Throttle `persistWeeklyReview`:** Only persist when the verdict or calorie change recommendation actually changes:
```kotlin
private var lastPersistedVerdict: AdjustmentVerdict? = null
private suspend fun persistWeeklyReview(state: DashboardUiState) {
    if (state.result.verdict == lastPersistedVerdict &&
        state.result.recommendedCalorieChange == lastPersistedChange) return
    lastPersistedVerdict = state.result.verdict
    // ... existing persist logic
}
```

5. **Separate meal and trend subscriptions:** The 7-day calorie chart only needs `observeMealEntriesSince(7 days)`, not the full 28-day window.

**Expected improvement:** ~80% reduction in `buildState()` execution time as data grows; eliminates the write-loop; dashboard remains snappy at 1+ year of data.

**Risk level:** Medium — verify that the 28-day window query still produces correct adjustment verdicts.

---

### P-06 · `AppContainer` — Room Database Opened on Main Thread

**File:** `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt:39`  
**Severity:** High | **Effort:** Small

**Problem description:**  
```kotlin
val database: RecompDatabase = RecompDatabase.create(context) // main thread, Application.onCreate()
```
Room database creation involves disk I/O: checking if the database file exists, running schema migrations if needed, and WAL initialization. On first install this can take 80–200ms, directly adding to cold-start time. On subsequent launches with pending migrations, this is also slow.

**Why it hurts performance:**  
- The main thread is blocked during `Application.onCreate()`.
- Android's ANR threshold is 5 seconds for Application startup, but even 200ms of main-thread I/O is noticeable as a white flash before content appears.
- Room's documentation explicitly recommends building the database off the main thread.

**Suggested fix:**  
Use `lazy` initialization combined with a coroutine scope to perform the initial DB open off the main thread. Since Room's DAO calls are already `suspend` functions, the first actual query will trigger the open if needed:

```kotlin
// In AppContainer:
private val _database = lazy { RecompDatabase.create(context) }
val database: RecompDatabase get() = _database.value

// In Application.onCreate(), warm up the DB asynchronously:
override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
    // Trigger DB open on a background thread so first navigation is fast:
    GlobalScope.launch(Dispatchers.IO) { container.database }
}
```

Alternatively, use Room's `createInBackground()` (not yet stable as of Room 2.6) or simply touch the DB in a `CoroutineScope(Dispatchers.IO)` inside `AppContainer.init {}`.

**Expected improvement:** ~100–200ms cold-start improvement; eliminates main-thread I/O.

**Risk level:** Low — Room is already thread-safe; this changes when not how the DB is opened.

---

## Medium-Priority Optimizations

### P-07 · Ambient Orb Brushes Recreated Every Recomposition

**Files:** `DashboardScreen.kt:81`, `FoodScreen.kt:123`, `ProgressScreen.kt:55`  
**Severity:** Medium | **Effort:** Small

**Problem description:**  
All three screens have ambient orb decorations that create `Brush.radialGradient(...)` inline:
```kotlin
Box(
    modifier = Modifier
        .size(300.dp)
        .background(
            Brush.radialGradient(   // ← new object every recomposition
                colors = listOf(Color(0x338B5CF6), Color.Transparent),
            ),
        ),
)
```
`Brush.radialGradient` creates a new `RadialGradientShader` each time. While inexpensive individually, these Box composables sit high in the composition tree and recompose whenever any parent state changes.

**Suggested fix:**  
Extract into `remember`'d constants at the top of each composable:
```kotlin
val orbBrush = remember { Brush.radialGradient(listOf(Color(0x338B5CF6), Color.Transparent)) }
Box(modifier = Modifier.size(300.dp).background(orbBrush))
```
Or extract into `companion object` / top-level `val` since the brushes are constant.

**Expected improvement:** Eliminates unnecessary shader object creation; prevents brush invalidation propagating to child composables.

**Risk level:** Low.

---

### P-08 · `FrostedCard` Shimmer Brush Created Inside `onDrawSurface`

**File:** `GlassComponents.kt:103`  
**Severity:** Medium | **Effort:** Small

**Problem description:**  
```kotlin
onDrawSurface = {
    drawRect(Color(0x33000000))
    val shimmerY = 1.dp.toPx() / 2f
    drawLine(
        brush = Brush.horizontalGradient(   // ← new Brush object every draw call
            colors = listOf(Color.Transparent, Color(0x33FFFFFF), ...),
            startX = size.width * 0.12f,
            endX   = size.width * 0.88f,
        ),
        ...
    )
}
```
`onDrawSurface` is called inside `drawBackdrop` every frame. The `Brush.horizontalGradient` with `startX`/`endX` tied to `size.width` means it cannot be statically cached, but it can be cached per-size using a `remember` inside the composable body.

**Suggested fix:**  
Capture `size` via a `Modifier.onSizeChanged` and `remember(shimmerBrushWidth)`:
```kotlin
var cardWidth by remember { mutableIntStateOf(0) }
val shimmerBrush = remember(cardWidth) {
    Brush.horizontalGradient(
        colors = listOf(Color.Transparent, Color(0x33FFFFFF), Color(0x33FFFFFF), Color.Transparent),
        startX = cardWidth * 0.12f,
        endX   = cardWidth * 0.88f,
    )
}
```
Same applies to `TintedCard.kt`.

**Expected improvement:** Eliminates shader reallocation on every backdrop draw.

**Risk level:** Low.

---

### P-09 · Unstable `List<T>` Fields in UI State Data Classes

**Files:** `FoodLogViewModel.kt:24`, `DashboardViewModel.kt:45`, `ProgressViewModel.kt`  
**Severity:** Medium | **Effort:** Small

**Problem description:**  
Kotlin's `List<T>` is **not stable** from Compose's perspective (Compose cannot guarantee it won't change). Data classes with `List` fields like:
```kotlin
data class FoodLogUiState(
    val slots: List<MealSlotWithEntries> = emptyList(),         // unstable
    val weekSummary: List<DayCalorieSummary> = emptyList(),     // unstable
    ...
)
data class DashboardUiState(
    val last7DaysCalories: List<DayCalories> = emptyList(),     // unstable
    ...
)
```
Compose's Skippability optimization cannot skip recomposition of composables that take these as parameters, even when the data hasn't changed.

**Suggested fix:**  
Add the `kotlinx-collections-immutable` dependency and use `ImmutableList<T>`:
```kotlin
// build.gradle.kts:
implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7")

// Data class:
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

data class FoodLogUiState(
    val slots: ImmutableList<MealSlotWithEntries> = persistentListOf(),
    val weekSummary: ImmutableList<DayCalorieSummary> = persistentListOf(),
)
```
Or annotate data classes with `@Immutable` if they are truly immutable at the Compose boundary.

**Expected improvement:** Enables Compose to skip recomposition of screens whose state hasn't meaningfully changed; reduces cascading recompositions.

**Risk level:** Low — requires adding one dependency and migrating list types in state classes.

---

### P-10 · `persistWeeklyReview` Triggers a DB Write on Every State Update

**File:** `DashboardViewModel.kt:184`  
**Severity:** Medium | **Effort:** Small

**Problem description:**  
```kotlin
init {
    viewModelScope.launch {
        combine(...).collect { state ->
            _uiState.value = state
            persistWeeklyReview(state)   // DB write on EVERY emission
        }
    }
}
```
Every meal entry addition emits a new state, which calls `persistWeeklyReview`. The weekly review data (verdict + recommended change) is unlikely to change more than once per day. But the DB write still happens on every food log.

**Why it hurts performance:**  
- Extra database write on every food addition.
- The write triggers Room's flow collectors, which could cause additional downstream emissions.
- Creates unnecessary write amplification in Room's WAL file.

**Suggested fix:** Cache the last-written verdict+change tuple and skip the write if unchanged (see P-05 fix #4 above).

**Expected improvement:** ~95% reduction in `persistWeeklyReview` write frequency.

**Risk level:** Low.

---

### P-11 · `GlassOrbBackground` Sensor Rate

**File:** `GlassOrbBackground.kt:103`  
**Severity:** Medium | **Effort:** Small  
(Covered in detail under P-01. Listed separately because P-01 addresses the blur; this item addresses the sensor rate only.)

Even after removing `Modifier.blur` (P-01), the sensor still runs at `SENSOR_DELAY_GAME`. At game rate, the `animateFloatAsState` spring animations for tilt produce meaninglessly smooth updates for what is a slow parallax effect. `SENSOR_DELAY_NORMAL` (5 Hz) is more than sufficient.

**Suggested fix:** Change `SENSOR_DELAY_GAME` → `SENSOR_DELAY_NORMAL` in both the `ON_RESUME` registration in `GlassOrbBackground.kt`.

---

### P-12 · `FoodLibraryScreen` — `key(state.category)` Forces Full List Recreation

**File:** `FoodLibraryScreen.kt:203`  
**Severity:** Medium | **Effort:** Small

**Problem description:**  
```kotlin
key(state.category) {
    LazyColumn(...) { ... }
}
```
The `key()` composable wrapper instructs Compose to completely tear down and recreate the `LazyColumn` (and all its items) when `state.category` changes. This was added to prevent key-space conflicts across category transitions — a valid concern — but it means switching categories causes full item recreation rather than smart diffing.

**Why it hurts performance:**  
- All item composable instances are discarded and recreated from scratch.
- All item animations restart.
- On a list with hundreds of food items, the recomposition cost is proportional to the number of visible items.

**Suggested fix:**  
Instead of `key(state.category)`, prefix item keys with the category:
```kotlin
// No key() wrapper needed
LazyColumn(...) {
    itemsIndexed(
        items = filteredFoods,
        key = { _, item -> "${state.category}_${item.key}" },  // category-scoped key
    ) { ... }
}
```
This preserves key stability within a category while allowing safe transitions between categories.

**Expected improvement:** Smooth category switching without full list teardown; preserves scroll position better.

**Risk level:** Low — test that items correctly hide/show after category switch.

---

## Low-Priority Optimizations

### P-13 · `CalorieProgressBar` — Stripe Loop Recalculated Every Draw

**File:** `CalorieProgressBar.kt:44`  
**Severity:** Low | **Effort:** Small

The diagonal stripe loop inside `Canvas` runs on every animation frame of the progress bar fill animation. The stripes are always at the same pitch and angle; only the clip rect changes. Cache the stripe positions:

```kotlin
val stripes = remember(size) {
    val stripeW = 4.dp.toPx(); val step = 7.dp.toPx()
    buildList { var x = -size.height; while (x < size.width + size.height) { add(x); x += step } }
}
```

---

### P-14 · `MacroRingChart` — `macroSweepAngles()` Not Memoized

**File:** `MacroRingChart.kt:52`  
**Severity:** Low | **Effort:** Small

```kotlin
val (targetProtein, targetCarbs, targetFat) = macroSweepAngles(proteinKcal, carbsKcal, fatKcal)
```
This is called every recomposition. Wrap in `remember(proteinKcal, carbsKcal, fatKcal)`:
```kotlin
val (targetProtein, targetCarbs, targetFat) = remember(proteinKcal, carbsKcal, fatKcal) {
    macroSweepAngles(proteinKcal, carbsKcal, fatKcal)
}
```

---

### P-15 · Navigation — No Custom Transition Animations

**File:** `AppNavGraph.kt`  
**Severity:** Low | **Effort:** Medium

All `composable()` destinations use the default system transition (slide + fade). For an app with a dark glass aesthetic, a fade-through transition would feel more premium and be GPU-cheaper (no slide = no layout pass during animation). Add explicit `enterTransition`/`exitTransition` using `fadeIn + fadeOut`:

```kotlin
composable(
    route = TopLevelDestination.Home.route,
    enterTransition = { fadeIn(tween(220)) },
    exitTransition = { fadeOut(tween(200)) },
) { ... }
```
Apply to all top-level destinations. Sub-screens (FoodLibrary, BodyEdit) can use a lighter `slideInVertically + fadeIn`.

---

### P-16 · `buildState` — `LocalDate.parse()` in Hot Loop

**File:** `DashboardViewModel.kt:198`  
**Severity:** Low | **Effort:** Small

Extension functions called in hot loops:
```kotlin
private fun DailyLogEntity.localDate(): LocalDate = LocalDate.parse(date)
private fun MealEntryEntity.localDate(): LocalDate = LocalDate.parse(date)
```
These are called on every entity in every `buildState` execution. `LocalDate.parse()` is not expensive, but on lists of 200+ entries it adds measurable overhead. Consider storing dates as `LocalDate` or `epochDay: Long` in Room entities directly, or caching the parse result.

---

### P-17 · `LockedSlotCard` Entries — No Compose Keys

**File:** `FoodScreen.kt:486`  
**Severity:** Low | **Effort:** Small

```kotlin
slotWithEntries.entries.forEachIndexed { i, entry ->
    SlotEntryRow(entry = entry, ...)
}
```
The entries inside a slot use `forEachIndexed` (index-based identity). If the user deletes the first entry, all subsequent `SlotEntryRow` instances are recomposed. Use a keyed `items()` call instead by wrapping in a `Column` built with explicit keys, or switch to a nested `LazyColumn` with `key = { entry.id }`.

Since slots rarely have more than 10 entries, the practical impact is small.

---

### P-18 · Build Config — No Baseline Profiles or Resource Shrinking

**File:** `build.gradle.kts`  
**Severity:** Low | **Effort:** Medium

1. **No `shrinkResources = true`** in `buildTypes.release` — unused resources (images, strings) are included in the APK/AAB.
2. **No Baseline Profile** — Compose startup and common hot paths are JIT-compiled on first run, causing visible jank for 1–5 seconds after install or after an OS-level JIT cache clear. A Baseline Profile pre-compiles these paths using ART's AOT compiler.
3. **No ProGuard/R8 obfuscation** verified — check that `minifyEnabled = true` is set in the release build type.

**Suggested fix:**
```kotlin
// build.gradle.kts
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
}

// Add BaselineProfiler plugin and generate a baseline profile:
// (use Macrobenchmark with BaselineProfileRule targeting cold/warm start + key screen navigations)
```

---

## Visual-Effects Optimization Strategy

**Goal:** Maintain the premium liquid-glass aesthetic while reducing GPU/CPU cost.

### Tier 1 — Safe replacements (no visible difference)
- Replace `Modifier.blur(18.dp)` on `GlassOrbBackground` with a pre-blurred asset (P-01).
- Cache `Brush.radialGradient` ambient orbs (P-07).
- Cache shimmer `Brush.horizontalGradient` in `FrostedCard` (P-08).
- Cache `Path` objects in `SparklineChart` (P-04).

### Tier 2 — Minor visual trade-off (test carefully)
- Reduce `FrostedCard` blur radius from `20dp` → `12dp` (P-03). Barely visible at card widths.
- Reduce nav bar to a single `drawBackdrop` pass instead of three (P-02).

### Tier 3 — Architecture change (significant impact)
- Implement blur-once strategy: one shared backdrop snapshot distributed to all FrostedCards (P-03 extended).
- Lower sensor rate (P-01/P-11) — no visual difference due to spring smoothing.

### What NOT to change
- `lens()` effect on nav bar indicator during press — this is a key premium interaction.
- `vibrancy()` on the nav bar — this is the hallmark of the liquid glass design.
- `InteractiveHighlight` shader — runs only during press gestures, not constantly.
- Chromatic aberration on indicator press — momentary, not a steady-state cost.
- Spring animation physics in `DampedDragAnimation` — these are already efficient.

---

## Startup Optimization Strategy

### Current cold-start path
```
Application.onCreate()
  └── AppContainer()
        └── RecompDatabase.create()  ← blocking I/O ~100ms
        └── OpenFoodFactsApi()       ← creates OkHttp client
  └── MainActivity.onCreate()
        └── setContent { RecompApp() }
              └── GlassOrbBackground() ← loads bg_glass_orbs image via painterResource
              └── NavHost()            ← instantiates DashboardViewModel
                    └── DashboardViewModel.init { combine(...) } ← first Room queries
```

### Recommended improvements

1. **P-06:** Move `RecompDatabase.create()` off the main thread.
2. **Lazy ViewModel initialization:** ViewModels are currently created eagerly when navigating to a route. Consider using `viewModel()` with a factory that delays heavy init until the first `LazyColumn` scroll.
3. **Preload the background image:** Schedule `painterResource(R.drawable.bg_glass_orbs)` in a background coroutine on `Application.onCreate()` so the Compose image cache is warm before the first frame.
4. **Splash screen:** Use `androidx.core.splashscreen` to hold the splash until the first `DashboardUiState` is ready, eliminating the "empty screen flash."

**Estimated cold-start improvement:** 150–300ms.

---

## Memory Optimization Strategy

### Current memory pressure sources
| Source | Estimated RAM | Notes |
|--------|---------------|-------|
| `bg_glass_orbs` PNG (uncompressed) | ~5–15 MB | Depends on resolution; scaled to 114% |
| Active GraphicsLayers (backdrops) | ~10–30 MB | contentBackdrop + navBackdrop + 2–4 FrostedCard layers |
| Compose snapshot state (all screens retained) | ~2–5 MB | Navigation back-stack keeps ViewModels alive |
| Room entity lists in ViewModels | ~1–5 MB | Grows with data; DashboardViewModel loads all meals |

### Recommendations

1. **P-05:** Scope `observeMealEntries()` to last 28 days — prevents unbounded memory growth in `DashboardViewModel`.
2. **Reduce active GraphicsLayers:** Each `layerBackdrop` + `drawBackdrop` pair consumes ~1–3 screen texture worth of GPU memory. Unify FrostedCard blur into one shared cached layer.
3. **BitmapFactory options for background image:** Load `bg_glass_orbs` at a reduced resolution (e.g., `inSampleSize = 2`) since it is immediately blurred anyway. At 18dp blur, halving the source resolution is imperceptible.
4. **Navigation ViewModels:** Currently all ViewModels live for the duration of the navigation back-stack entry. This is correct behavior but confirm that `FoodLibraryViewModel` (which holds potentially large food catalog results) is cleared when leaving the FoodLibrary screen.

---

## State / Rebuild Optimization Strategy

### Current recomposition hot paths

1. **GlassOrbBackground tilt state** (P-01) → recomposes background + all overlaid composables. Fix: threshold guard + lower sensor rate.
2. **DashboardUiState emitted on every meal add** (P-05) → recomposes entire Dashboard. Fix: scoped queries + debounce.
3. **Unstable `List<T>` in state** (P-09) → Compose cannot skip any composable receiving these. Fix: `ImmutableList` or `@Immutable`.

### Compose Stability Checklist

Run `./gradlew assembleRelease -PcomposeCompilerReports=true` to generate Compose compiler stability reports. Look for classes marked `unstable` that are used as composable parameters.

Key data classes to annotate:
- `FoodLogUiState` → `@Stable` (mutable state container)
- `DashboardUiState` → `@Stable`
- `MealSlotWithEntries` → `@Immutable` if all fields are immutable
- `DayCalorieSummary` → `@Immutable`
- `DayCalories` → `@Immutable`

---

## Prioritized Implementation Roadmap

### Phase 1 — Quick Wins ✅ COMPLETE

| Task | File | Change | Status |
|------|------|--------|--------|
| 1a | `GlassOrbBackground.kt` | Change `SENSOR_DELAY_GAME` → `SENSOR_DELAY_NORMAL` | ✅ Done |
| 1b | `GlassOrbBackground.kt` | Add tilt threshold guard (0.004f) in `onSensorChanged` | ✅ Done |
| 1c | `GlassOrbBackground.kt` | Remove `Modifier.blur(BLUR_RADIUS)` + pre-blurred asset (sigma=40, created via ImageMagick) | ✅ Done |
| 1d | `DashboardScreen.kt` | Extract ambient orb Brushes into top-level `private val` constants | ✅ Done |
| 1e | `FoodScreen.kt` | Same as 1d | ✅ Done |
| 1f | `ProgressScreen.kt` | Same as 1d | ✅ Done |
| 1g | `SparklineChart.kt` | Hoist `values.min/max` into `remember(values)`, reuse Path objects with `reset()` | ✅ Done |
| 1h | `MacroRingChart.kt` | Wrap `macroSweepAngles()` in `remember(keys)` | ✅ Done |
| 1i | `DashboardViewModel.kt` | Cache `AdjustmentEngine` with threshold-equality guard | ✅ Done |
| 1j | `DashboardViewModel.kt` | Add verdict-change guard to `persistWeeklyReview()` | ✅ Done |
| 1k | `GlassComponents.kt` | Cache shimmer Brush per card width + `AmbientOrb` top-level brush | ✅ Done |

### Phase 2 — High Impact ✅ COMPLETE

| Task | File | Change | Status |
|------|------|--------|--------|
| 2a | `AppContainer.kt` + `RecompTrackerApp.kt` | Move DB creation off main thread via `lazy` + background warm-up | ✅ Done |
| 2b | `LogRepository.kt` + `DashboardViewModel.kt` | Scope meal query to last 28 days via `observeMealEntriesSince()` | ✅ Done |
| 2c | `DashboardViewModel.kt` | Add `debounce(300ms)` to the `combine()` flow | ✅ Done |
| 2d | All UI state classes | `kotlinx-collections-immutable` added; `List<T>` → `ImmutableList<T>` + `@Immutable`/`@Stable` | ✅ Done |
| 2e | `FoodLibraryScreen.kt` | Replace `key(state.category)` with category-scoped item keys | ✅ Done |

### Phase 3 — Medium Impact ✅ COMPLETE

| Task | File | Change | Status |
|------|------|--------|--------|
| 3a | `GlassComponents.kt` | Reduce FrostedCard blur radius 20dp → 12dp | 🔲 Deferred (visual review needed on device) |
| 3b | `LiquidComponents.kt` | Eliminate the invisible tinted Row backdrop pass | 🔲 Phase 4 |
| 3c | `AppNavGraph.kt` | Add explicit `fadeIn/fadeOut` transitions on all destinations | ✅ Done |
| 3d | `build.gradle.kts` | Enable `shrinkResources`, `minifyEnabled` in release + proguard-rules.pro | ✅ Done |
| 3e | Macrobenchmark module | Add Baseline Profile generation (startup + navigation) | 🔲 Phase 4 |

### Phase 4 — Remaining Architectural Items

| Task | Description | Status |
|------|-------------|--------|
| 4a | Implement blur-once shared backdrop: distribute single blurred texture to all FrostedCards | 🔲 Open |
| 4b | Add `androidx.core.splashscreen` with data-ready condition | ✅ Done |
| 4c | Baseline Profile infrastructure (`:macrobenchmark` module + generator) | ✅ Done — run `./gradlew :macrobenchmark:connectedBenchmarkAndroidTest` on device to generate profile |
| 4d | Scope `observeMealEntries()` to last 28 days (new DAO query + ViewModel debounce) | ✅ Done |
| 4e | Reduce FrostedCard blur radius 20dp → 12dp | ✅ Done |
| 4f | Eliminate invisible tinted Row backdrop pass in `LiquidBottomTabs` | 🔲 Open (load-bearing for indicator; skip) |

---

## Step-by-Step Tasks for the Implementation Agent

### Task Set 1: Sensor & Background (P-01, P-11)
**Target file:** `GlassOrbBackground.kt`

1. Change `SensorManager.SENSOR_DELAY_GAME` → `SensorManager.SENSOR_DELAY_NORMAL` on line ~103.
2. Add a `private const val TILT_THRESHOLD = 0.004f` constant.
3. In `onSensorChanged`, gate the state write:
   ```kotlin
   val newX = (event.values[0] * TILT_SCALE).coerceIn(-MAX_TILT, MAX_TILT)
   val newY = (event.values[1] * TILT_SCALE).coerceIn(-MAX_TILT, MAX_TILT)
   if (kotlin.math.abs(newX - rawTiltX) > TILT_THRESHOLD ||
       kotlin.math.abs(newY - rawTiltY) > TILT_THRESHOLD) {
       rawTiltX = newX
       rawTiltY = newY
   }
   ```
4. Remove `Modifier.blur(BLUR_RADIUS)` from the `Image` modifier chain.
5. Create a pre-blurred version of `res/drawable/bg_glass_orbs.png` (blur at 18px in any image editor) and save as `res/drawable/bg_glass_orbs_blurred.png`.
6. Change `painterResource(R.drawable.bg_glass_orbs)` → `painterResource(R.drawable.bg_glass_orbs_blurred)`.
7. Delete the `BLUR_RADIUS` constant (now unused).

### Task Set 2: Ambient Orb Brushes (P-07)
**Target files:** `DashboardScreen.kt`, `FoodScreen.kt`, `ProgressScreen.kt`

In each file, extract the inline `Brush.radialGradient` from the Box `background()` modifier into a top-level `val` or a `remember { }` at the composable's call site. The colors are hardcoded constants so a top-level `val` is preferred:

```kotlin
// At file top-level (not inside any composable):
private val AmbientOrbBrush1 = Brush.radialGradient(
    colors = listOf(Color(0x338B5CF6), Color.Transparent)
)
```

Replace all inline `Brush.radialGradient(...)` calls with the cached val.

### Task Set 3: SparklineChart Allocations (P-04)
**Target file:** `SparklineChart.kt`

1. Add a `data class ChartGeometry(val pts: List<Offset>, val linePath: Path, val areaPath: Path, val min: Float, val max: Float)`.
2. Compute geometry lazily outside Canvas:
   ```kotlin
   // NOTE: size is only known inside Canvas. Use two-stage:
   // Stage 1: compute Y-normalized values (independent of size)
   val (minVal, maxVal) = remember(values) { values.min() to values.max() }
   
   // Stage 2: use DrawScope's size inside Canvas to build pts + paths
   // Cache keyed on values + measuredSize using a local var + SideEffect
   ```
   A practical approach: use `var cachedGeometry by remember { mutableStateOf<Pair<Size, ChartGeometry>?>(null) }` and rebuild inside Canvas only when `size` changes.
3. Verify the scrubber gesture `pts` calculation uses the same cached geometry.

### Task Set 4: Dashboard ViewModel (P-05, P-10)
**Target file:** `DashboardViewModel.kt`

1. Move `val adjustmentEngine = AdjustmentEngine(...)` out of `buildState()` and into a field initialized with the constructor's `AdjustmentThresholds.default`.
2. Add `debounce(300L)` after `combine(...)`:
   ```kotlin
   combine(...).debounce(300L).collect { ... }
   ```
3. Add guard fields:
   ```kotlin
   private var lastPersistedVerdict: AdjustmentVerdict? = null
   private var lastPersistedChange: Int = Int.MIN_VALUE
   ```
4. In `persistWeeklyReview()`, return early if neither has changed.

### Task Set 5: Immutable Collections (P-09)
**Target files:** All `*UiState` data classes

1. Add to `build.gradle.kts`:
   ```kotlin
   implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7")
   ```
2. In each `UiState` data class, change `List<T>` → `ImmutableList<T>` and default values to `persistentListOf()`.
3. In ViewModels, call `.toImmutableList()` when building the state.
4. Annotate data classes with only immutable fields with `@Immutable`.

---

## Testing Checklist

### Before starting any optimization:
- [ ] Record a System Trace (Android Studio Profiler) for: cold start, Dashboard scroll, Food tab → Food Library, tab navigation.
- [ ] Note the Choreographer frame count and Jank % from the trace.
- [ ] Run Layout Inspector and screenshot recomposition counts for Dashboard and Food screens.

### After each Phase 1 task:
- [ ] Verify the visual appearance is unchanged on a Pixel 6a (or similar mid-range device).
- [ ] Run the app in Release mode (`./gradlew assembleRelease`) — never profile Debug.

### After Phase 1 complete:
- [ ] Re-record System Trace and compare frame times.
- [ ] Re-run Layout Inspector; verify recomposition counts dropped.
- [ ] Confirm the parallax tilt effect still feels natural at `SENSOR_DELAY_NORMAL`.
- [ ] Confirm `bg_glass_orbs_blurred` looks identical to the runtime-blurred version.

### After Phase 2 complete:
- [ ] Add a `MealEntry` while observing Android Profiler "Memory" tab. Confirm no large GC event.
- [ ] Confirm Dashboard loads correctly after adding many food entries (200+).
- [ ] Confirm FoodLibrary category switching still shows correct items.

### After Phase 3 complete:
- [ ] Confirm Release build size is smaller (resource shrinking).
- [ ] Visually compare FrostedCard with blur=12dp vs blur=20dp side-by-side on device.
- [ ] Navigate through all 5 tabs and verify fade transitions look smooth.

---

## Before / After Metrics to Measure Success

| Metric | How to Measure | Target |
|--------|----------------|--------|
| Cold start time (TTID) | `adb shell am start -W com.zack.recomptracker/.MainActivity` | < 800ms |
| Average frame time (scrolling Dashboard) | Android Studio CPU Profiler → Frame Rendering | < 16ms p95 |
| Jank % (90-frame window) | Perfetto → `FrameTimeline` | < 5% janky frames |
| GlassOrbBackground recomposition count (1 min idle) | Layout Inspector | 0 (no tilt → no recompose) |
| Dashboard recomposition after adding 1 meal | Layout Inspector | Only NutritionStrip + slot rows recompose; not entire screen |
| Peak memory (RSS) during food browsing | Android Profiler → Memory | < 200 MB |
| SparklineChart GC events during animation | Android Profiler → GC Events | 0 during 1s animation |
| `persistWeeklyReview()` DB writes per session | Add log counter | ≤ 1 per session (verdict-change only) |
| Release APK size | `./gradlew bundleRelease && bundletool build-apks` | < 25 MB |

---

## Appendix: Dependency Notes

| Library | Version concern | Notes |
|---------|----------------|-------|
| `com.kyant.backdrop` | Unlocked version | Core of the glass effects; treat as a fixed constraint |
| `com.kyant.shapes` | Unlocked version | Used for `Capsule()` shape in liquid components |
| `kotlinx-collections-immutable` | 0.3.7 | Add for P-09 fix |
| `vico.compose.m3` | Present but not observed in active screens | Verify if unused; if so, remove to reduce DEX size |
| `compose.material.icons.extended` | Full icon set | Consider switching to `compose.material.icons` (core only) to reduce APK size; check which icons beyond Material default are used |

---

*This plan was generated by static analysis. Before implementing, always validate findings with runtime profiling on a physical device in Release mode.*
