# Muscle-Group Icons + Active-Session Exercise Image Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show an auto-cropped muscle-group icon (theme-tinted) for each exercise on the Train-home routine cards, and show the real exercise photo (with a muscle-icon fallback) in the active-workout exercise rows.

**Architecture:** Vendor MIT body-silhouette SVG path data into assets; a cached `MuscleArt` loader parses each path with Compose `PathParser`; a pure `muscleTargetFor` mapping picks the worked muscle; a `MuscleGroupIcon` Canvas draws the faint body + accent-highlighted muscle, auto-cropped at runtime via path bounds. Train-home routine preview always uses the icon; the active session resolves the photo by exercise id (via the library the VM already holds) and falls back to the icon.

**Tech Stack:** Kotlin, Jetpack Compose (Canvas, PathParser, Coil `SubcomposeAsyncImage`), Room-backed repositories, JUnit4.

---

## File Structure

- **Create** `scripts/extract_muscle_art.py` — one-shot vendoring script (download + extract JSON).
- **Create** `app/src/main/assets/muscles/body_front.json`, `body_back.json` — `{slug,d}[]` art data.
- **Create** `docs/THIRD_PARTY_NOTICES.md` — MIT attribution.
- **Create** `app/src/main/java/com/zack/recomptracker/ui/train/component/MuscleGroup.kt` — `MuscleView`, `MuscleTarget`, `muscleTargetFor` (pure).
- **Create** `app/src/test/java/com/zack/recomptracker/ui/train/component/MuscleGroupTest.kt` — mapping tests.
- **Create** `app/src/main/java/com/zack/recomptracker/ui/train/component/MuscleArt.kt` — asset loader + cache.
- **Create** `app/src/main/java/com/zack/recomptracker/ui/train/component/MuscleGroupIcon.kt` — the composable.
- **Modify** `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt` — pre-warm `MuscleArt`.
- **Modify** `app/src/main/java/com/zack/recomptracker/ui/train/TrainHomeScreen.kt` — routine preview uses the icon.
- **Modify** `app/src/main/java/com/zack/recomptracker/ui/train/component/ExerciseCard.kt` — `fallbackMuscles` param + Coil error fallback.
- **Modify** `app/src/main/java/com/zack/recomptracker/ui/train/ActiveSessionViewModel.kt` — `exerciseVisuals`.
- **Modify** `app/src/main/java/com/zack/recomptracker/ui/train/ActiveSessionScreen.kt` — pass photo + fallback.

---

## Task 1: Vendor the muscle-art assets + attribution

**Files:**
- Create: `scripts/extract_muscle_art.py`
- Create: `app/src/main/assets/muscles/body_front.json`, `app/src/main/assets/muscles/body_back.json`
- Create: `docs/THIRD_PARTY_NOTICES.md`

- [ ] **Step 1: Write the extraction script**

Create `scripts/extract_muscle_art.py`:

```python
#!/usr/bin/env python3
"""Vendor muscle-region SVG path data from react-native-body-highlighter (MIT).
Pinned commit for reproducibility."""
import json, os, re, urllib.request

SHA = "15df9e2dbc621450001960bed5a30e6a75357faa"
BASE = f"https://raw.githubusercontent.com/HichamELBSI/react-native-body-highlighter/{SHA}/assets"
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "muscles")

def extract(ts_text):
    parts = re.split(r'slug:\s*"([a-z-]+)"', ts_text)
    out = []
    for i in range(1, len(parts), 2):
        slug = parts[i]
        for d in re.findall(r'"([Mm][^"]+)"', parts[i + 1]):
            out.append({"slug": slug, "d": d})
    return out

def main():
    os.makedirs(OUT, exist_ok=True)
    for src, dst in [("bodyFront.ts", "body_front.json"), ("bodyBack.ts", "body_back.json")]:
        text = urllib.request.urlopen(f"{BASE}/{src}").read().decode("utf-8")
        data = extract(text)
        assert len(data) > 20, f"too few paths parsed from {src}"
        with open(os.path.join(OUT, dst), "w") as f:
            json.dump(data, f)
        print(f"{dst}: {len(data)} paths")

if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Run the script to produce the asset files**

Run: `cd "/Users/zackalatrash/Desktop/Personal Dietitian" && python3 scripts/extract_muscle_art.py`
Expected: prints `body_front.json: <N> paths` and `body_back.json: <M> paths`, each N/M well over 20.

- [ ] **Step 3: Verify the JSON parses and contains expected slugs**

Run:
```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian" && python3 -c "
import json
f=json.load(open('app/src/main/assets/muscles/body_front.json'))
b=json.load(open('app/src/main/assets/muscles/body_back.json'))
fs={x['slug'] for x in f}; bs={x['slug'] for x in b}
assert {'chest','abs','deltoids','biceps','quadriceps','forearm','neck'} <= fs, fs
assert {'upper-back','lower-back','triceps','gluteal','hamstring','calves','trapezius'} <= bs, bs
assert all(x['d'].lstrip().startswith(('M','m')) for x in f+b)
print('OK front',len(f),'back',len(b))
"
```
Expected: `OK front <N> back <M>`.

- [ ] **Step 4: Create the attribution notice**

Create `docs/THIRD_PARTY_NOTICES.md`:

```markdown
# Third-Party Notices

## react-native-body-highlighter

Muscle-group icon path data (`app/src/main/assets/muscles/*.json`) is derived from
**react-native-body-highlighter** by ELABBASSI Hicham, used under the MIT License.

Source: https://github.com/HichamELBSI/react-native-body-highlighter
Pinned commit: 15df9e2dbc621450001960bed5a30e6a75357faa

```
MIT License

Copyright (c) 2022 ELABBASSI Hicham

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
```

- [ ] **Step 5: Commit**

```bash
git add scripts/extract_muscle_art.py app/src/main/assets/muscles/ docs/THIRD_PARTY_NOTICES.md
git commit -m "feat(workout): vendor MIT muscle-silhouette art + attribution"
```

---

## Task 2: Muscle-group mapping (TDD, pure)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/train/component/MuscleGroup.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ui/train/component/MuscleGroupTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ui/train/component/MuscleGroupTest.kt`:

```kotlin
package com.zack.recomptracker.ui.train.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MuscleGroupTest {

    @Test
    fun `maps front-view muscles`() {
        assertEquals(MuscleTarget(MuscleView.FRONT, setOf("chest")), muscleTargetFor(listOf("chest")))
        assertEquals(MuscleTarget(MuscleView.FRONT, setOf("deltoids")), muscleTargetFor(listOf("shoulders")))
        assertEquals(MuscleTarget(MuscleView.FRONT, setOf("abs", "obliques")), muscleTargetFor(listOf("abdominals")))
        assertEquals(MuscleTarget(MuscleView.FRONT, setOf("biceps")), muscleTargetFor(listOf("biceps")))
        assertEquals(MuscleTarget(MuscleView.FRONT, setOf("forearm")), muscleTargetFor(listOf("forearms")))
        assertEquals(MuscleTarget(MuscleView.FRONT, setOf("quadriceps")), muscleTargetFor(listOf("quadriceps")))
        assertEquals(MuscleTarget(MuscleView.FRONT, setOf("adductors")), muscleTargetFor(listOf("adductors")))
        assertEquals(MuscleTarget(MuscleView.FRONT, setOf("neck")), muscleTargetFor(listOf("neck")))
    }

    @Test
    fun `maps back-view muscles`() {
        assertEquals(MuscleTarget(MuscleView.BACK, setOf("triceps")), muscleTargetFor(listOf("triceps")))
        assertEquals(MuscleTarget(MuscleView.BACK, setOf("upper-back")), muscleTargetFor(listOf("lats")))
        assertEquals(MuscleTarget(MuscleView.BACK, setOf("upper-back")), muscleTargetFor(listOf("middle back")))
        assertEquals(MuscleTarget(MuscleView.BACK, setOf("trapezius")), muscleTargetFor(listOf("traps")))
        assertEquals(MuscleTarget(MuscleView.BACK, setOf("lower-back")), muscleTargetFor(listOf("lower back")))
        assertEquals(MuscleTarget(MuscleView.BACK, setOf("gluteal")), muscleTargetFor(listOf("glutes")))
        assertEquals(MuscleTarget(MuscleView.BACK, setOf("hamstring")), muscleTargetFor(listOf("hamstrings")))
        assertEquals(MuscleTarget(MuscleView.BACK, setOf("calves")), muscleTargetFor(listOf("calves")))
        assertEquals(MuscleTarget(MuscleView.BACK, setOf("gluteal")), muscleTargetFor(listOf("abductors")))
    }

    @Test
    fun `uses first muscle, is case and space insensitive`() {
        assertEquals(MuscleTarget(MuscleView.FRONT, setOf("chest")), muscleTargetFor(listOf("  Chest ", "triceps")))
        assertEquals(MuscleTarget(MuscleView.BACK, setOf("upper-back")), muscleTargetFor(listOf("LATS")))
    }

    @Test
    fun `unknown or empty returns null`() {
        assertNull(muscleTargetFor(emptyList()))
        assertNull(muscleTargetFor(listOf("eyeballs")))
        assertNull(muscleTargetFor(listOf("")))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.train.component.MuscleGroupTest"`
Expected: FAIL — unresolved references `MuscleTarget`, `MuscleView`, `muscleTargetFor`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/zack/recomptracker/ui/train/component/MuscleGroup.kt`:

```kotlin
package com.zack.recomptracker.ui.train.component

/** Which body silhouette a muscle is highlighted on. */
enum class MuscleView { FRONT, BACK }

/** A resolved highlight: which view, and which body-art slugs to fill with the accent. */
data class MuscleTarget(val view: MuscleView, val slugs: Set<String>)

/**
 * Maps a free-exercise-db `primaryMuscles` list to a highlight target.
 * Uses the first entry (case/space-insensitive). Returns null if empty or unmapped
 * — callers render the generic dumbbell fallback in that case.
 */
fun muscleTargetFor(primaryMuscles: List<String>): MuscleTarget? {
    val key = primaryMuscles.firstOrNull()?.trim()?.lowercase() ?: return null
    return when (key) {
        "chest" -> MuscleTarget(MuscleView.FRONT, setOf("chest"))
        "shoulders" -> MuscleTarget(MuscleView.FRONT, setOf("deltoids"))
        "abdominals" -> MuscleTarget(MuscleView.FRONT, setOf("abs", "obliques"))
        "biceps" -> MuscleTarget(MuscleView.FRONT, setOf("biceps"))
        "forearms" -> MuscleTarget(MuscleView.FRONT, setOf("forearm"))
        "quadriceps" -> MuscleTarget(MuscleView.FRONT, setOf("quadriceps"))
        "adductors" -> MuscleTarget(MuscleView.FRONT, setOf("adductors"))
        "neck" -> MuscleTarget(MuscleView.FRONT, setOf("neck"))
        "triceps" -> MuscleTarget(MuscleView.BACK, setOf("triceps"))
        "lats", "middle back" -> MuscleTarget(MuscleView.BACK, setOf("upper-back"))
        "traps" -> MuscleTarget(MuscleView.BACK, setOf("trapezius"))
        "lower back" -> MuscleTarget(MuscleView.BACK, setOf("lower-back"))
        "glutes", "abductors" -> MuscleTarget(MuscleView.BACK, setOf("gluteal"))
        "hamstrings" -> MuscleTarget(MuscleView.BACK, setOf("hamstring"))
        "calves" -> MuscleTarget(MuscleView.BACK, setOf("calves"))
        else -> null
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.train.component.MuscleGroupTest"`
Expected: PASS — all 4 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/component/MuscleGroup.kt \
        app/src/test/java/com/zack/recomptracker/ui/train/component/MuscleGroupTest.kt
git commit -m "feat(workout): muscle-group mapping from primaryMuscles"
```

---

## Task 3: `MuscleArt` asset loader

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/train/component/MuscleArt.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`

- [ ] **Step 1: Create the loader**

Create `app/src/main/java/com/zack/recomptracker/ui/train/component/MuscleArt.kt`:

```kotlin
package com.zack.recomptracker.ui.train.component

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import org.json.JSONArray

/**
 * Loads and caches the muscle-silhouette art (front/back) shipped in
 * `assets/muscles/*.json`. Each entry is a slug + a Compose [Path] parsed from
 * the SVG `d` string. Thread-safe and idempotent; parse happens once.
 */
object MuscleArt {

    data class MusclePath(val slug: String, val path: Path)

    @Volatile private var front: List<MusclePath> = emptyList()
    @Volatile private var back: List<MusclePath> = emptyList()
    @Volatile private var loaded = false

    /** Parses both views once. Safe to call repeatedly and from any thread. */
    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        front = parse(context, "muscles/body_front.json")
        back = parse(context, "muscles/body_back.json")
        loaded = true
    }

    fun front(): List<MusclePath> = front
    fun back(): List<MusclePath> = back

    private fun parse(context: Context, asset: String): List<MusclePath> = runCatching {
        val text = context.assets.open(asset).bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val path = PathParser().parsePathString(o.getString("d")).toPath()
                add(MusclePath(o.getString("slug"), path))
            }
        }
    }.onFailure {
        Log.w("MuscleArt", "Failed to load $asset — muscle icons will fall back", it)
    }.getOrDefault(emptyList())
}
```

- [ ] **Step 2: Pre-warm in `AppContainer`**

In `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`, add an import near the other imports:

```kotlin
import com.zack.recomptracker.ui.train.component.MuscleArt
```

Then inside the existing `init { ... }` block (currently at `AppContainer.kt:146-156`), add a second pre-warm launch right after the existing `appScope.launch { ... }` for the exercise library:

```kotlin
        appScope.launch {
            runCatching { MuscleArt.load(context.applicationContext) }
        }
```

- [ ] **Step 3: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — no unresolved references.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/component/MuscleArt.kt \
        app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat(workout): MuscleArt asset loader + startup pre-warm"
```

---

## Task 4: `MuscleGroupIcon` composable

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/train/component/MuscleGroupIcon.kt`

- [ ] **Step 1: Create the composable**

Create `app/src/main/java/com/zack/recomptracker/ui/train/component/MuscleGroupIcon.kt`:

```kotlin
package com.zack.recomptracker.ui.train.component

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.runtime.remember
import kotlin.math.max

private val FaintBody = Color.White.copy(alpha = 0.13f)
private const val CROP_PAD = 1.55f

/**
 * Draws the worked muscle highlighted on a faint body silhouette, auto-cropped to
 * that muscle. [tint] colours the worked muscle (use the theme accent). When the
 * muscles can't be mapped or the art is unavailable, renders a generic dumbbell.
 */
@Composable
fun MuscleGroupIcon(
    primaryMuscles: List<String>,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val target = remember(primaryMuscles) { muscleTargetFor(primaryMuscles) }

    val paths = remember(target) {
        if (target == null) {
            emptyList()
        } else {
            MuscleArt.load(context)
            if (target.view == MuscleView.FRONT) MuscleArt.front() else MuscleArt.back()
        }
    }

    val crop = remember(paths, target) {
        if (target == null || paths.isEmpty()) return@remember null
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        paths.filter { it.slug in target.slugs }.forEach {
            val bb = it.path.getBounds()
            l = minOf(l, bb.left); t = minOf(t, bb.top)
            r = maxOf(r, bb.right); b = maxOf(b, bb.bottom)
        }
        if (l > r) return@remember null
        val side = max(r - l, b - t) * CROP_PAD
        val cx = (l + r) / 2f; val cy = (t + b) / 2f
        Rect(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f)
    }

    if (target == null || crop == null) {
        Icon(
            imageVector = Icons.Default.FitnessCenter,
            contentDescription = null,
            tint = tint,
            modifier = modifier,
        )
        return
    }

    Canvas(modifier) {
        val scale = size.minDimension / crop.width
        clipRect(0f, 0f, size.width, size.height) {
            withTransform({
                scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset.Zero)
                translate(-crop.left, -crop.top)
            }) {
                paths.forEach { mp ->
                    drawPath(mp.path, if (mp.slug in target.slugs) tint else FaintBody)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — no unresolved references.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/component/MuscleGroupIcon.kt
git commit -m "feat(workout): MuscleGroupIcon auto-cropped body-highlight composable"
```

---

## Task 5: Routine-preview wiring (Train home)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/TrainHomeScreen.kt`

- [ ] **Step 1: Replace the thumbnail box with the muscle icon**

In `app/src/main/java/com/zack/recomptracker/ui/train/TrainHomeScreen.kt`, replace the thumbnail `Box` block (currently at lines 396-419: the `val imageUrl = ex.exercise.images.firstOrNull()` + `Box { if (imageUrl != null) AsyncImage(...) else Icon(FitnessCenter...) }`) with:

```kotlin
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(CornerSmall))
                                .background(Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            MuscleGroupIcon(
                                primaryMuscles = ex.exercise.primaryMuscles,
                                tint = accent.accentLighter,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
```

- [ ] **Step 2: Add the import and remove the now-unused AsyncImage import**

Add (with the other `com.zack.recomptracker.ui.train.component` / project imports):

```kotlin
import com.zack.recomptracker.ui.train.component.MuscleGroupIcon
```

Remove the line `import coil.compose.AsyncImage` (the preview was its only use — confirm with `grep -n AsyncImage app/src/main/java/com/zack/recomptracker/ui/train/TrainHomeScreen.kt` returning only the import line before deleting). Leave the `FitnessCenter` import as-is even if now unused (a warning, not an error) unless the compiler/lint fails the build on it.

- [ ] **Step 3: Type-check, then build the debug APK**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.
Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/TrainHomeScreen.kt
git commit -m "feat(workout): muscle-group icons in Train-home routine preview"
```

---

## Task 6: `ExerciseCard` muscle-icon fallback

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/component/ExerciseCard.kt`

- [ ] **Step 1: Add the `fallbackMuscles` parameter**

In `ExerciseCard.kt`, add a parameter to the `ExerciseCard` composable signature, right after `imageUrl: String?,`:

```kotlin
    imageUrl: String?,
    fallbackMuscles: List<String>? = null,
```

- [ ] **Step 2: Add the accent + new imports**

Add imports:

```kotlin
import coil.compose.SubcomposeAsyncImage
import com.zack.recomptracker.ui.theme.LocalAppAccent
```

Inside `ExerciseCard`, just below `val appColors = LocalAppColors.current`, add:

```kotlin
    val accent = LocalAppAccent.current
```

- [ ] **Step 3: Replace the thumbnail body with photo-or-fallback**

Replace the thumbnail `Box` body (currently lines ~117-140: `val resolvedUrl = imageUrl?.let { ... }` + `Box { if (resolvedUrl != null) AsyncImage(...) else Icon(FitnessCenter...) }`) with:

```kotlin
            // Thumbnail
            val resolvedUrl = imageUrl?.takeIf { it.isNotBlank() }?.let { exerciseImageUrl(it) }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(CornerSmall))
                    .background(appColors.cardSurface),
                contentAlignment = Alignment.Center,
            ) {
                if (resolvedUrl != null) {
                    SubcomposeAsyncImage(
                        model = resolvedUrl,
                        contentDescription = exerciseName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = { ExerciseThumbFallback(fallbackMuscles, appColors.textMuted, accent.accentLighter) },
                        error = { ExerciseThumbFallback(fallbackMuscles, appColors.textMuted, accent.accentLighter) },
                    )
                } else {
                    ExerciseThumbFallback(fallbackMuscles, appColors.textMuted, accent.accentLighter)
                }
            }
```

- [ ] **Step 4: Add the fallback helper composable**

At the bottom of `ExerciseCard.kt` (top level, after the `ExerciseCard` function), add:

```kotlin
@Composable
private fun ExerciseThumbFallback(
    fallbackMuscles: List<String>?,
    dumbbellTint: Color,
    accentTint: Color,
) {
    if (fallbackMuscles != null) {
        MuscleGroupIcon(
            primaryMuscles = fallbackMuscles,
            tint = accentTint,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Icon(
            imageVector = Icons.Default.FitnessCenter,
            contentDescription = null,
            tint = dumbbellTint,
            modifier = Modifier.size(22.dp),
        )
    }
}
```

(`MuscleGroupIcon`, `FitnessCenter`, `Icon`, `Color`, `fillMaxSize`, `size` are already imported or in the same package. Add `import androidx.compose.foundation.layout.fillMaxSize` only if missing — it is already imported in this file.)

- [ ] **Step 5: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (Routine Builder's existing `ExerciseCard(...)` calls compile unchanged — `fallbackMuscles` defaults to null, so they still show the dumbbell when an image is absent.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/component/ExerciseCard.kt
git commit -m "feat(workout): ExerciseCard muscle-icon fallback for missing/failed images"
```

---

## Task 7: Active-session image resolution + wiring

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/ActiveSessionViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/ActiveSessionScreen.kt`

- [ ] **Step 1: Expose `exerciseVisuals` from the ViewModel**

In `ActiveSessionViewModel.kt`, add the data class at the top level (above the class or just inside it) and a populated state flow. First add the model just after the imports, above the class:

```kotlin
/** Per-exercise visual info resolved from the library for the active session. */
data class ExerciseVisual(val imagePath: String?, val primaryMuscles: List<String>)
```

Then inside the class, after the `prevMap` declarations (around line 49), add:

```kotlin
    /** Map of exerciseId → resolved image path + primary muscles, for thumbnails. */
    private val _exerciseVisuals = MutableStateFlow<Map<Long, ExerciseVisual>>(emptyMap())
    val exerciseVisuals: StateFlow<Map<Long, ExerciseVisual>> = _exerciseVisuals
```

In the existing `init { viewModelScope.launch { session.collect { s -> ... } } }` block (lines 51-59), extend the collector body so it also resolves visuals. Replace the existing collector body with:

```kotlin
            session.collect { s ->
                if (s != null && s.workoutId != null && _prevMap.value.isEmpty()) {
                    loadPrevMap(s.workoutId)
                }
                if (s != null) {
                    resolveVisuals(s)
                }
            }
```

Then add the helper method (near `loadPrevMap`):

```kotlin
    private suspend fun resolveVisuals(session: WorkoutSession) {
        val current = _exerciseVisuals.value
        val missing = session.exercises.map { it.exerciseId }.distinct()
            .filter { it !in current }
        if (missing.isEmpty()) return
        val resolved = current.toMutableMap()
        missing.forEach { id ->
            val ex = exerciseLibraryRepository.getById(id)
            resolved[id] = ExerciseVisual(
                imagePath = ex?.images?.firstOrNull(),
                primaryMuscles = ex?.primaryMuscles ?: emptyList(),
            )
        }
        _exerciseVisuals.value = resolved
    }
```

- [ ] **Step 2: Type-check the ViewModel change**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Collect and pass visuals in the screen**

In `ActiveSessionScreen.kt`, after `val prevMap by viewModel.prevMap.collectAsStateWithLifecycle()` (line 90), add:

```kotlin
    val exerciseVisuals by viewModel.exerciseVisuals.collectAsStateWithLifecycle()
```

In the `items(displayExercises, ...)` block, update the `ExerciseCard(...)` call (lines 251-256) so the first arguments become:

```kotlin
                val visual = exerciseVisuals[se.exerciseId]
                ExerciseCard(
                    exerciseName = se.exerciseName,
                    imageUrl = visual?.imagePath,
                    fallbackMuscles = visual?.primaryMuscles,
                    subtitle = "",
```

Leave the rest of the `ExerciseCard(...)` arguments (onMoveUp/onMoveDown/onRemove/content/etc.) exactly as they are.

- [ ] **Step 4: Type-check, then build the debug APK**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.
Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/ActiveSessionViewModel.kt \
        app/src/main/java/com/zack/recomptracker/ui/train/ActiveSessionScreen.kt
git commit -m "feat(workout): show real exercise photo with muscle-icon fallback in active session"
```

- [ ] **Step 6: Manual verification (hand off to user)**

Install the debug build. (a) Train home: each routine card's exercises show a theme-accent muscle silhouette (chest/back/legs/etc.), no broken image squares. (b) Start a workout: exercise rows show the real exercise photo; for an exercise with no image (or while offline), the muscle-group icon shows instead of the dumbbell.

---

## Self-Review Notes

- **Spec coverage:** asset vendoring + attribution (Task 1); `MuscleArt` loader + pre-warm (Task 3); `muscleTargetFor` mapping incl. full table (Task 2); `MuscleGroupIcon` with runtime crop + dumbbell fallback (Task 4); Train-home preview only (Task 5); `ExerciseCard` fallback + Coil error state (Task 6); active-session `exerciseVisuals` + photo + fallback (Task 7). Edge cases (empty/unknown muscles → dumbbell; missing art → dumbbell; offline → SubcomposeAsyncImage error slot) covered.
- **Type consistency:** `MuscleTarget(view, slugs)`, `MuscleView.FRONT/BACK`, `muscleTargetFor(List<String>): MuscleTarget?`, `MuscleArt.load/front/back` with `MusclePath(slug, path)`, `MuscleGroupIcon(primaryMuscles, tint, modifier)`, `ExerciseVisual(imagePath, primaryMuscles)`, `ExerciseCard(..., fallbackMuscles)` — all consistent across tasks.
- **No placeholders:** every code step is complete.
- **Scope:** Routine Builder / exercise picker untouched (still photos); no workout-tracking logic changed.
```
