# Share a Training Routine — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user export one training routine to a small `.rtroutine` file via the Android share sheet; the recipient taps that file to open the app to a preview screen and save it as a new routine.

**Architecture:** A pure-Kotlin serializer + resolver in `domain/share/` (fully unit-tested, no Android deps) handle the payload format and exercise portability. A `RoutineShareRepository` in `data/` orchestrates cache-file export (via `FileProvider`) and import (resolve exercises → `WorkoutRepository.saveWorkout`). One new `ACTION_VIEW` intent-filter routes a tapped file through the existing `MainActivity` intent path into a new `ui/share/` preview screen. Portability is by `(source, externalId, name)` per exercise — never the sender's local `exerciseId`.

**Tech Stack:** Kotlin, kotlinx.serialization, Room, Jetpack Compose + Material3 (glass design system), AndroidX FileProvider, JUnit.

**Spec:** `docs/superpowers/specs/2026-07-11-share-training-routine-design.md`

---

## File Structure

**New files**
- `app/src/main/java/com/zack/recomptracker/domain/share/RoutineShareModels.kt` — `@Serializable` payload types, `APP_ID`, `CURRENT_SHARE_VERSION`, `RoutineShareResult` sealed type.
- `app/src/main/java/com/zack/recomptracker/domain/share/RoutineShareSerializer.kt` — pure `encode`/`decode` (typed errors).
- `app/src/main/java/com/zack/recomptracker/domain/share/RoutineShareResolver.kt` — pure exercise-resolution against a library snapshot.
- `app/src/main/java/com/zack/recomptracker/data/repository/RoutineShareRepository.kt` — file export + import orchestration.
- `app/src/main/java/com/zack/recomptracker/data/share/RoutineShareInbox.kt` — hands the tapped `Uri` from `MainActivity`/`RecompApp` to the import ViewModel.
- `app/src/main/java/com/zack/recomptracker/ui/share/SharedRoutineImportViewModel.kt` — decode + preview + save state machine.
- `app/src/main/java/com/zack/recomptracker/ui/share/SharedRoutineImportScreen.kt` — preview UI (design system).
- `app/src/main/java/com/zack/recomptracker/ui/share/RoutineShareLauncher.kt` — builds the `ACTION_SEND` chooser.
- `app/src/main/res/xml/file_paths.xml` — FileProvider cache path.
- `app/src/test/java/com/zack/recomptracker/domain/share/RoutineShareSerializerTest.kt`
- `app/src/test/java/com/zack/recomptracker/domain/share/RoutineShareResolverTest.kt`

**Modified files**
- `app/src/main/AndroidManifest.xml` — `<provider>` + `ACTION_VIEW` intent-filter(s).
- `app/src/main/java/com/zack/recomptracker/MainActivity.kt` — read the tapped `Uri`, expose as state.
- `app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt` — new `shareRoutineUri` param → navigate to import route.
- `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt` — `Routes.SharedRoutineImport` + composable; Train `onShareRoutine` wiring.
- `app/src/main/java/com/zack/recomptracker/ui/train/TrainViewModel.kt` — inject repo + `buildShareFile`.
- `app/src/main/java/com/zack/recomptracker/ui/train/TrainHomeScreen.kt` — `onShareRoutine` param + "Share" menu item.
- `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt` — construct repo + inbox; wire two ViewModels.

**Data note (why no domain change):** `WorkoutWithExercisesDb.exercises[n].exercise` is the full `ExerciseEntity`, which carries `source`, `externalId`, and `name`. So export reads `source` directly from the DB graph — the domain `Exercise` model (which has no `source` field, constructed in 74 test sites) is NOT modified.

---

## Task 1: Share payload models

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/share/RoutineShareModels.kt`

- [ ] **Step 1: Write the models file**

```kotlin
package com.zack.recomptracker.domain.share

import kotlinx.serialization.Serializable

/**
 * Marks a payload as produced by this app, so a foreign JSON file that happens to parse is still
 * rejected on import. Never change this string — old shared files carry it forever.
 */
const val APP_ID = "recomptracker"

/**
 * Schema version of the routine-share format. Bump only on a breaking change; additive fields with
 * defaults do not need a bump. Import rejects any payload whose [RoutineSharePayload.version] exceeds
 * this (the file came from a newer app).
 */
const val CURRENT_SHARE_VERSION = 1

/** A shareable, device-portable snapshot of one routine (full copy: structure + reps + weights + notes). */
@Serializable
data class RoutineSharePayload(
    val version: Int = CURRENT_SHARE_VERSION,
    val app: String = APP_ID,
    val name: String,
    val note: String? = null,
    val exercises: List<SharedExercise> = emptyList(),
)

/**
 * One exercise line. Carries the portable identity `(source, externalId, name)` — NOT the sender's
 * local exerciseId, which is a device-assigned autoincrement. `name` is both the display label and
 * the fallback match when `externalId` is unknown on the recipient's device.
 */
@Serializable
data class SharedExercise(
    val source: String,
    val externalId: String,
    val name: String,
    val note: String? = null,
    val sets: List<SharedSet> = emptyList(),
)

/** One planned set target. Reps/weight nullable = "no target" (blank in the builder). Weight in kg. */
@Serializable
data class SharedSet(
    val setNumber: Int,
    val targetReps: Int? = null,
    val targetWeightKg: Double? = null,
)

/** Result of decoding a shared-routine file. Exactly one of these; the UI maps each to a message. */
sealed interface RoutineShareResult {
    data class Success(val payload: RoutineSharePayload) : RoutineShareResult
    /** File isn't ours: wrong/absent `app` marker. */
    data object NotARoutineFile : RoutineShareResult
    /** Payload `version` is newer than this app understands. */
    data object UnsupportedVersion : RoutineShareResult
    /** Unparseable JSON, or structurally empty (no exercises). */
    data object Damaged : RoutineShareResult
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/share/RoutineShareModels.kt
git commit -m "feat(share): routine-share payload models"
```

---

## Task 2: Serializer (encode / decode) — TDD

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/share/RoutineShareSerializer.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/share/RoutineShareSerializerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.domain.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineShareSerializerTest {

    private val sample = RoutineSharePayload(
        name = "Push Day",
        note = "Chest focus",
        exercises = listOf(
            SharedExercise(
                source = "free-exercise-db",
                externalId = "Barbell_Bench_Press",
                name = "Barbell Bench Press",
                note = "Slow eccentric",
                sets = listOf(
                    SharedSet(setNumber = 1, targetReps = 8, targetWeightKg = 60.0),
                    SharedSet(setNumber = 2, targetReps = 8, targetWeightKg = null),
                ),
            ),
            SharedExercise(
                source = "user",
                externalId = "user_my_cable_thing",
                name = "My Cable Thing",
                note = null,
                sets = listOf(SharedSet(setNumber = 1, targetReps = null, targetWeightKg = null)),
            ),
        ),
    )

    @Test
    fun `round-trips structure, reps, weights, notes and order`() {
        val decoded = RoutineShareSerializer.decode(RoutineShareSerializer.encode(sample))
        assertTrue(decoded is RoutineShareResult.Success)
        assertEquals(sample, (decoded as RoutineShareResult.Success).payload)
    }

    @Test
    fun `rejects a foreign app marker`() {
        val foreign = """{"version":1,"app":"someOtherApp","name":"X","exercises":[{"source":"s","externalId":"e","name":"E","sets":[]}]}"""
        assertEquals(RoutineShareResult.NotARoutineFile, RoutineShareSerializer.decode(foreign))
    }

    @Test
    fun `rejects a future version`() {
        val future = RoutineShareSerializer.encode(sample.copy(version = CURRENT_SHARE_VERSION + 1))
        assertEquals(RoutineShareResult.UnsupportedVersion, RoutineShareSerializer.decode(future))
    }

    @Test
    fun `rejects corrupt json`() {
        assertEquals(RoutineShareResult.Damaged, RoutineShareSerializer.decode("{not json"))
    }

    @Test
    fun `rejects an empty routine`() {
        val empty = RoutineShareSerializer.encode(sample.copy(exercises = emptyList()))
        assertEquals(RoutineShareResult.Damaged, RoutineShareSerializer.decode(empty))
    }

    @Test
    fun `tolerates unknown fields for forward compat`() {
        val withExtra = """{"version":1,"app":"recomptracker","name":"X","futureField":true,"exercises":[{"source":"s","externalId":"e","name":"E","sets":[]}]}"""
        assertTrue(RoutineShareSerializer.decode(withExtra) is RoutineShareResult.Success)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.share.RoutineShareSerializerTest"`
Expected: FAIL — `RoutineShareSerializer` unresolved.

- [ ] **Step 3: Write the serializer**

```kotlin
package com.zack.recomptracker.domain.share

import kotlinx.serialization.json.Json

/**
 * Pure (Android-free) encode/decode for the routine-share file format. Encoding is deterministic;
 * decoding validates ownership (`app`), version, and structural sanity, returning a typed
 * [RoutineShareResult] so callers never crash on a bad file (spec: "features degrade, never crash").
 *
 * The version check ordering matters: we parse leniently first (so a future version whose JSON shape
 * still fits decodes), then gate on the marker and version explicitly.
 */
object RoutineShareSerializer {

    // Lenient like the rest of the app (see BackupRepository/ExerciseLibraryJson): ignore unknown keys
    // so a newer app's additive fields don't hard-fail an older reader.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(payload: RoutineSharePayload): String =
        json.encodeToString(RoutineSharePayload.serializer(), payload)

    fun decode(raw: String): RoutineShareResult {
        val payload = runCatching {
            json.decodeFromString(RoutineSharePayload.serializer(), raw)
        }.getOrElse { return RoutineShareResult.Damaged }

        if (payload.app != APP_ID) return RoutineShareResult.NotARoutineFile
        if (payload.version > CURRENT_SHARE_VERSION) return RoutineShareResult.UnsupportedVersion
        if (payload.exercises.isEmpty()) return RoutineShareResult.Damaged
        return RoutineShareResult.Success(payload)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.share.RoutineShareSerializerTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/share/RoutineShareSerializer.kt app/src/test/java/com/zack/recomptracker/domain/share/RoutineShareSerializerTest.kt
git commit -m "feat(share): typed encode/decode with validation"
```

---

## Task 3: Exercise resolver — TDD

Resolves each `SharedExercise` to an existing local exercise id, or flags it for creation. Pure: takes a library snapshot (`List<Exercise>`), returns decisions. Matching order: exact `externalId`, then case-insensitive `name`, else "will create".

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/share/RoutineShareResolver.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/share/RoutineShareResolverTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.domain.share

import com.zack.recomptracker.domain.workout.Exercise
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineShareResolverTest {

    private fun lib(id: Long, externalId: String, name: String) = Exercise(
        id = id, externalId = externalId, name = name, category = null, force = null, level = null,
        mechanic = null, equipment = null, primaryMuscles = emptyList(), secondaryMuscles = emptyList(),
        instructions = emptyList(), images = emptyList(), userCreated = false,
    )

    private fun shared(externalId: String, name: String) =
        SharedExercise(source = "free-exercise-db", externalId = externalId, name = name, sets = emptyList())

    private val library = listOf(
        lib(10, "Barbell_Bench_Press", "Barbell Bench Press"),
        lib(20, "Squat", "Squat"),
    )

    @Test
    fun `matches by externalId first`() {
        val r = RoutineShareResolver.resolve(listOf(shared("Barbell_Bench_Press", "wrong name")), library)
        assertEquals(listOf(Resolution(shared("Barbell_Bench_Press", "wrong name"), existingId = 10L)), r)
    }

    @Test
    fun `falls back to case-insensitive name`() {
        val r = RoutineShareResolver.resolve(listOf(shared("unknown_ext", "squat")), library)
        assertEquals(20L, r.single().existingId)
    }

    @Test
    fun `flags an unknown exercise for creation`() {
        val r = RoutineShareResolver.resolve(listOf(shared("user_novel", "Novel Move")), library)
        assertEquals(null, r.single().existingId)
        assertEquals(true, r.single().willCreate)
    }

    @Test
    fun `preserves input order`() {
        val input = listOf(shared("Squat", "Squat"), shared("Barbell_Bench_Press", "Barbell Bench Press"))
        val r = RoutineShareResolver.resolve(input, library)
        assertEquals(listOf(20L, 10L), r.map { it.existingId })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.share.RoutineShareResolverTest"`
Expected: FAIL — `RoutineShareResolver` / `Resolution` unresolved.

- [ ] **Step 3: Write the resolver**

```kotlin
package com.zack.recomptracker.domain.share

import com.zack.recomptracker.domain.workout.Exercise

/**
 * A per-exercise import decision. [existingId] non-null = resolved to that local exercise;
 * null = not in the recipient's library, so import will create it as a custom exercise.
 */
data class Resolution(
    val shared: SharedExercise,
    val existingId: Long?,
) {
    val willCreate: Boolean get() = existingId == null
}

/**
 * Pure resolution of a shared routine's exercises against the recipient's library snapshot. Match
 * order: exact `externalId` (stable across installs that share the bundled free-exercise-db), then
 * case-insensitive `name` (survives a library-version drift), else unresolved → create on import.
 *
 * `source` is intentionally not part of the match: the domain [Exercise] model doesn't carry it, and
 * `externalId` is effectively unique across the two namespaces in practice (free-exercise-db slugs vs
 * `user_`-prefixed ids). It stays in the payload as self-describing metadata for future importers.
 */
object RoutineShareResolver {

    fun resolve(exercises: List<SharedExercise>, library: List<Exercise>): List<Resolution> {
        val byExternalId = library.associateBy { it.externalId }
        val byName = library.associateBy { it.name.lowercase() }
        return exercises.map { shared ->
            val existing = byExternalId[shared.externalId] ?: byName[shared.name.lowercase()]
            Resolution(shared = shared, existingId = existing?.id)
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.share.RoutineShareResolverTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/share/RoutineShareResolver.kt app/src/test/java/com/zack/recomptracker/domain/share/RoutineShareResolverTest.kt
git commit -m "feat(share): pure exercise resolver for import"
```

---

## Task 4: FileProvider configuration

**Files:**
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml` (inside `<application>`, after the `activity-alias`, before `</application>`)

- [ ] **Step 1: Create the FileProvider paths**

`app/src/main/res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <!-- Exported routine files live under cacheDir/shared_routines and are shared read-only. -->
    <cache-path name="shared_routines" path="shared_routines/" />
</paths>
```

- [ ] **Step 2: Register the provider + the VIEW intent-filter**

In `app/src/main/AndroidManifest.xml`, add the `<provider>` inside `<application>` (e.g. immediately before `</application>`):

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

And add these two intent-filters INSIDE the existing `<activity android:name=".MainActivity" …>` block (after the Health Connect rationale filter, before `</activity>`):

```xml
            <!-- Import a shared .rtroutine file. Two filters because custom-extension matching on
                 Android is unreliable through a single filter: one matches the content mime we send,
                 the other matches the file extension for file:// deliveries. A transport that re-types
                 the file to something else may not match (documented limitation in the spec). -->
            <intent-filter android:label="Import routine">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="content" />
                <data android:mimeType="application/octet-stream" />
            </intent-filter>
            <intent-filter android:label="Import routine">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="file" />
                <data android:scheme="content" />
                <data android:host="*" />
                <data android:mimeType="*/*" />
                <data android:pathPattern=".*\\.rtroutine" />
                <data android:pathSuffix=".rtroutine" />
            </intent-filter>
```

- [ ] **Step 3: Verify the manifest builds**

Run: `./gradlew :app:processDebugManifest`
Expected: BUILD SUCCESSFUL (no manifest-merger error).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/xml/file_paths.xml app/src/main/AndroidManifest.xml
git commit -m "feat(share): FileProvider + VIEW intent-filter for .rtroutine"
```

---

## Task 5: Share inbox holder

Hands the tapped `Uri` from `MainActivity`/`RecompApp` to the import ViewModel (one-shot, consume-once). Mirrors the coach-handoff store pattern.

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/share/RoutineShareInbox.kt`

- [ ] **Step 1: Write the inbox**

```kotlin
package com.zack.recomptracker.data.share

import android.net.Uri
import java.util.concurrent.atomic.AtomicReference

/**
 * One-shot hand-off of a tapped shared-routine [Uri] from the Activity/nav layer to the import
 * ViewModel. Single-slot: a second incoming file before the first is consumed replaces it (the user
 * is opening one file at a time). [consume] atomically claims and clears the slot so a rotation /
 * recomposition can't re-import the same file.
 */
class RoutineShareInbox {
    private val ref = AtomicReference<Uri?>(null)

    fun offer(uri: Uri) { ref.set(uri) }

    fun consume(): Uri? = ref.getAndSet(null)
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/share/RoutineShareInbox.kt
git commit -m "feat(share): one-shot Uri inbox for import hand-off"
```

---

## Task 6: RoutineShareRepository (export + import)

Orchestrates: build a `.rtroutine` cache file and return a shareable `content://` Uri; read a Uri and decode; import a payload (resolve → create-missing → `saveWorkout`).

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/repository/RoutineShareRepository.kt`

- [ ] **Step 1: Write the repository**

```kotlin
package com.zack.recomptracker.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.zack.recomptracker.data.local.dao.WorkoutDao
import com.zack.recomptracker.domain.share.RoutineShareResolver
import com.zack.recomptracker.domain.share.RoutineShareResult
import com.zack.recomptracker.domain.share.RoutineShareSerializer
import com.zack.recomptracker.domain.share.RoutineSharePayload
import com.zack.recomptracker.domain.share.SharedExercise
import com.zack.recomptracker.domain.share.SharedSet
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Export/import of a single routine as a self-contained `.rtroutine` file. Export reads the DB graph
 * directly (so it can carry each exercise's `source`, which the domain model drops) and writes a
 * cache file shared read-only via [FileProvider]. Import resolves each exercise to a local id —
 * creating a custom exercise for anything the recipient doesn't have — then saves a NEW routine.
 * All disk/CPU work is off the main dispatcher.
 */
class RoutineShareRepository(
    private val appContext: Context,
    private val workoutDao: WorkoutDao,
    private val workoutRepository: WorkoutRepository,
    private val exerciseLibraryRepository: ExerciseLibraryRepository,
) {
    private val fileProviderAuthority = "${appContext.packageName}.fileprovider"

    /**
     * Builds a shareable `.rtroutine` file for [workoutId] and returns its FileProvider Uri, or null
     * if the routine no longer exists. `source`/`externalId`/`name` come straight off the joined
     * ExerciseEntity in the workout graph — never the local exerciseId.
     */
    suspend fun buildShareFile(workoutId: Long): Uri? = withContext(Dispatchers.IO) {
        val db = workoutDao.getWithExercises(workoutId) ?: return@withContext null
        val payload = RoutineSharePayload(
            name = db.workout.name,
            note = db.workout.note,
            exercises = db.exercises
                .sortedBy { it.workoutExercise.sortOrder }
                .map { line ->
                    SharedExercise(
                        source = line.exercise.source,
                        externalId = line.exercise.externalId,
                        name = line.exercise.name,
                        note = line.workoutExercise.note,
                        sets = line.plannedSets
                            .sortedBy { it.setNumber }
                            .map { SharedSet(it.setNumber, it.targetReps, it.targetWeightKg) },
                    )
                },
        )
        val json = RoutineShareSerializer.encode(payload)
        val dir = File(appContext.cacheDir, "shared_routines").apply { mkdirs() }
        val file = File(dir, "${sanitize(db.workout.name)}.rtroutine")
        file.writeText(json)
        FileProvider.getUriForFile(appContext, fileProviderAuthority, file)
    }

    /** Reads and decodes a tapped file. Any read failure degrades to [RoutineShareResult.Damaged]. */
    suspend fun readPayload(uri: Uri): RoutineShareResult = withContext(Dispatchers.IO) {
        val raw = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        }.getOrNull() ?: return@withContext RoutineShareResult.Damaged
        RoutineShareSerializer.decode(raw)
    }

    /**
     * The per-exercise import decisions against the current library — drives the preview "new" badge.
     */
    suspend fun resolve(payload: RoutineSharePayload) =
        RoutineShareResolver.resolve(payload.exercises, exerciseLibraryRepository.all())

    /**
     * Imports [payload] as a brand-new routine and returns its id. Never edits/overwrites existing
     * routines. Unresolved exercises are created as custom (idempotent via insertCustomOrGetExisting).
     */
    suspend fun importRoutine(payload: RoutineSharePayload): Long {
        val resolutions = resolve(payload)
        val lines = resolutions.map { r ->
            val exerciseId = r.existingId ?: exerciseLibraryRepository.addCustomExercise(r.shared.name)
            NewWorkoutLine(
                exerciseId = exerciseId,
                plannedSets = r.shared.sets.map { PlannedSetDraft(it.targetReps, it.targetWeightKg) },
                note = r.shared.note,
            )
        }
        return workoutRepository.saveWorkout(payload.name, payload.note, lines)
    }

    // Cache filename only — strip anything that isn't filename-safe; the display name lives in JSON.
    private fun sanitize(name: String): String =
        name.trim().ifBlank { "routine" }.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(40)
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (If `line.exercise.source` fails to resolve, confirm `WorkoutExerciseWithExercise.exercise` is an `ExerciseEntity` — it is, per `WorkoutMappers.kt`.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/repository/RoutineShareRepository.kt
git commit -m "feat(share): RoutineShareRepository export + import"
```

---

## Task 7: Wire repository + inbox + ViewModels into AppContainer

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`

- [ ] **Step 1: Construct the repository + inbox**

After line 196 (`val workoutRepository = WorkoutRepository(database.workoutDao())`) add:

```kotlin
    val routineShareInbox = com.zack.recomptracker.data.share.RoutineShareInbox()
    val routineShareRepository = com.zack.recomptracker.data.repository.RoutineShareRepository(
        appContext = context.applicationContext,
        workoutDao = database.workoutDao(),
        workoutRepository = workoutRepository,
        exerciseLibraryRepository = exerciseLibraryRepository,
    )
```

(`exerciseLibraryRepository` is declared on line 195, just above — order is fine.)

- [ ] **Step 2: Pass the repo into `TrainViewModel` in the factory**

In `AppViewModelFactory.create`, change the `TrainViewModel::class.java ->` branch (currently ends at the `dateProvider` arg) to add the repo:

```kotlin
            TrainViewModel::class.java -> TrainViewModel(
                workoutRepository = container.workoutRepository,
                sessionRepository = container.workoutSessionRepository,
                exerciseLibraryRepository = container.exerciseLibraryRepository,
                logRepository = container.logRepository,
                userProfileStore = container.userProfilePreferencesStore,
                dateProvider = container.dateProvider,
                routineShareRepository = container.routineShareRepository,
            )
```

- [ ] **Step 3: Add the import ViewModel branch**

Immediately before the `else -> error("Unknown ViewModel class: ${modelClass.name}")` line, add:

```kotlin
            com.zack.recomptracker.ui.share.SharedRoutineImportViewModel::class.java ->
                com.zack.recomptracker.ui.share.SharedRoutineImportViewModel(
                    routineShareRepository = container.routineShareRepository,
                    inbox = container.routineShareInbox,
                )
```

- [ ] **Step 4: Verify (will fail to compile until Tasks 8–9 add the ViewModel + TrainViewModel arg)**

This task intentionally references types created in Tasks 8 and 9. Do NOT run a full compile here; commit and proceed. (Subagent note: if your workflow requires green between tasks, reorder to do Task 8 and Task 9's ViewModel edits first, then this task — the code blocks are independent.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat(share): wire share repo, inbox, and import VM in AppContainer"
```

---

## Task 8: SharedRoutineImportViewModel

State machine: Loading → Preview (payload + resolutions) → Saved(newId) / Error(message).

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/share/SharedRoutineImportViewModel.kt`

- [ ] **Step 1: Write the ViewModel**

```kotlin
package com.zack.recomptracker.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.repository.RoutineShareRepository
import com.zack.recomptracker.data.share.RoutineShareInbox
import com.zack.recomptracker.domain.share.Resolution
import com.zack.recomptracker.domain.share.RoutineSharePayload
import com.zack.recomptracker.domain.share.RoutineShareResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One previewed exercise row: display name + whether importing will create it new. */
data class ImportExerciseRow(val name: String, val setCount: Int, val willCreate: Boolean)

sealed interface ImportUiState {
    data object Loading : ImportUiState
    data class Preview(
        val name: String,
        val note: String?,
        val exercises: List<ImportExerciseRow>,
        val saving: Boolean = false,
    ) : ImportUiState
    data class Saved(val workoutId: Long) : ImportUiState
    data class Error(val message: String) : ImportUiState
}

class SharedRoutineImportViewModel(
    private val routineShareRepository: RoutineShareRepository,
    inbox: RoutineShareInbox,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Loading)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private var payload: RoutineSharePayload? = null

    init {
        val uri = inbox.consume()
        if (uri == null) {
            _state.value = ImportUiState.Error("This routine couldn't be opened. Try tapping the file again.")
        } else {
            viewModelScope.launch {
                when (val result = routineShareRepository.readPayload(uri)) {
                    is RoutineShareResult.Success -> {
                        payload = result.payload
                        val resolutions = routineShareRepository.resolve(result.payload)
                        _state.value = ImportUiState.Preview(
                            name = result.payload.name,
                            note = result.payload.note,
                            exercises = toRows(result.payload, resolutions),
                        )
                    }
                    RoutineShareResult.NotARoutineFile ->
                        _state.value = ImportUiState.Error("This file isn't a Recomp routine.")
                    RoutineShareResult.UnsupportedVersion ->
                        _state.value = ImportUiState.Error("This routine was shared from a newer version of the app. Update to import it.")
                    RoutineShareResult.Damaged ->
                        _state.value = ImportUiState.Error("This routine file is damaged and can't be opened.")
                }
            }
        }
    }

    fun save() {
        val current = _state.value
        val toSave = payload
        if (current !is ImportUiState.Preview || current.saving || toSave == null) return
        _state.value = current.copy(saving = true)
        viewModelScope.launch {
            runCatching { routineShareRepository.importRoutine(toSave) }
                .onSuccess { newId -> _state.value = ImportUiState.Saved(newId) }
                .onFailure { _state.value = ImportUiState.Error(it.message ?: "Couldn't save this routine.") }
        }
    }

    private fun toRows(payload: RoutineSharePayload, resolutions: List<Resolution>): List<ImportExerciseRow> =
        payload.exercises.mapIndexed { i, ex ->
            ImportExerciseRow(
                name = ex.name,
                setCount = ex.sets.size,
                willCreate = resolutions.getOrNull(i)?.willCreate ?: false,
            )
        }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Task 7's factory branch now resolves; TrainViewModel arg is added in Task 9 — if compiling standalone, expect the TrainViewModel arg error until Task 9).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/share/SharedRoutineImportViewModel.kt
git commit -m "feat(share): import preview/save ViewModel"
```

---

## Task 9: TrainViewModel — buildShareFile

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/TrainViewModel.kt`

- [ ] **Step 1: Add the repo to the constructor**

Add the import near the other repository imports (after line 10):

```kotlin
import com.zack.recomptracker.data.repository.RoutineShareRepository
```

Add a constructor parameter. Change the constructor to include `routineShareRepository` (place it before the `dateProvider` param to keep injected repos together):

```kotlin
class TrainViewModel(
    private val workoutRepository: WorkoutRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val exerciseLibraryRepository: ExerciseLibraryRepository,
    private val logRepository: LogRepository,
    private val userProfileStore: UserProfilePreferencesStore,
    private val routineShareRepository: RoutineShareRepository,
    dateProvider: DateProvider,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
```

- [ ] **Step 2: Add the share-file builder**

Add this method inside the class body (e.g. just after the constructor's `init`/first `MutableStateFlow` declarations — anywhere in the class is fine):

```kotlin
    /**
     * Builds a shareable `.rtroutine` file for [templateId] and returns its Uri, or null if the
     * routine no longer exists. The caller (UI) launches the Android share sheet with it.
     */
    suspend fun buildShareFile(templateId: Long): android.net.Uri? =
        routineShareRepository.buildShareFile(templateId)
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Task 7 already passes `routineShareRepository` from the factory).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/TrainViewModel.kt
git commit -m "feat(share): TrainViewModel.buildShareFile"
```

---

## Task 10: Share launcher helper

Builds the `ACTION_SEND` chooser for a routine file Uri. Kept out of the composable so the intent flags are in one place.

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/share/RoutineShareLauncher.kt`

- [ ] **Step 1: Write the helper**

```kotlin
package com.zack.recomptracker.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Launches the Android share sheet for an exported routine [uri]. Uses `application/octet-stream`
 * (the custom `.rtroutine` type has no registered mime) and grants the receiving app read access.
 * `FLAG_ACTIVITY_NEW_TASK` lets this run from a non-Activity context if needed.
 */
fun shareRoutineFile(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "Share routine").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/share/RoutineShareLauncher.kt
git commit -m "feat(share): share-sheet launcher helper"
```

---

## Task 11: "Share" affordance in the routine card

Add a Share row to the routine overflow menu and thread a callback out of `TrainHomeScreen`.

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/TrainHomeScreen.kt`

- [ ] **Step 1: Add an import for the Share icon**

Add near the other `material.icons.filled` imports (after line 32, `import androidx.compose.material.icons.filled.MoreVert`):

```kotlin
import androidx.compose.material.icons.filled.Share
```

- [ ] **Step 2: Add `onShareRoutine` param to `TrainHomeScreen`**

In the `TrainHomeScreen(...)` signature, add a callback (after `onLogRecovery`):

```kotlin
    onLogRecovery: () -> Unit = {},
    onShareRoutine: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
```

- [ ] **Step 3: Pass it into `RoutineCard`**

In the `items(state.routines, key = { it.id })` block, add `onShareClick` to the `RoutineCard(...)` call (after `onDeleteClick`):

```kotlin
                    RoutineCard(
                        template = template,
                        onCardClick = { onEditRoutine(template.id) },
                        onStart = {
                            requestStart {
                                viewModel.startSession(template)
                                onStart()
                            }
                        },
                        onEditClick = { onEditRoutine(template.id) },
                        onDeleteClick = { viewModel.deleteRoutine(template.id) },
                        onShareClick = { onShareRoutine(template.id) },
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                    )
```

- [ ] **Step 4: Add the param + menu item to `RoutineCard`**

In the `private fun RoutineCard(...)` signature, add (after `onDeleteClick: () -> Unit,`):

```kotlin
    onShareClick: () -> Unit,
```

Then in its `DropdownMenu`, add a Share item BETWEEN the "Edit" and "Delete" `DropdownMenuItem`s:

```kotlin
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = {
                            Icon(Icons.Default.Share, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onShareClick()
                        },
                    )
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/TrainHomeScreen.kt
git commit -m "feat(share): Share item in routine overflow menu"
```

---

## Task 12: SharedRoutineImportScreen

The preview screen: `SubScreenHeader`, routine name + note, exercise rows with a "New" badge where `willCreate`, `LiquidPrimaryButton` to save, error/saved handling. Uses only design-system components.

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/share/SharedRoutineImportScreen.kt`

- [ ] **Step 1: Write the screen**

```kotlin
package com.zack.recomptracker.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.SubScreenHeader
import com.zack.recomptracker.ui.liquidglass.LiquidPrimaryButton
import com.zack.recomptracker.ui.liquidglass.LiquidSecondaryButton
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

@Composable
fun SharedRoutineImportScreen(
    viewModel: SharedRoutineImportViewModel,
    onClose: () -> Unit,
    onImported: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appColors = LocalAppColors.current

    // Navigate away once the routine is saved.
    LaunchedEffect(state) {
        (state as? ImportUiState.Saved)?.let { onImported(it.workoutId) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SubScreenHeader(title = "Shared routine", onBack = onClose) }

        when (val s = state) {
            is ImportUiState.Loading -> item {
                Text("Opening routine…", style = AppType.body, color = appColors.textMuted)
            }

            is ImportUiState.Error -> item {
                FrostedCard(contentPadding = 16.dp) {
                    Text(s.message, style = AppType.body, color = appColors.textPrimary)
                    Spacer(Modifier.height(12.dp))
                    LiquidSecondaryButton(text = "Close", onClick = onClose)
                }
            }

            is ImportUiState.Preview -> {
                item {
                    FrostedCard(contentPadding = 16.dp) {
                        Text(s.name, style = AppType.cardTitle, color = appColors.textPrimary)
                        s.note?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, style = AppType.cardSubtitle, color = appColors.textMuted)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${s.exercises.size} exercises",
                            style = AppType.label,
                            color = appColors.textMuted,
                        )
                    }
                }
                item { SectionLabel("Exercises") }
                items(s.exercises) { row -> ImportExerciseRowView(row) }
                item {
                    Spacer(Modifier.height(4.dp))
                    LiquidPrimaryButton(
                        text = if (s.saving) "Saving…" else "Save to my routines",
                        onClick = { viewModel.save() },
                    )
                }
            }

            is ImportUiState.Saved -> item {
                Text("Saved.", style = AppType.body, color = appColors.textMuted)
            }
        }
    }
}

@Composable
private fun ImportExerciseRowView(row: ImportExerciseRow) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    FrostedCard(contentPadding = 13.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.name,
                    style = AppType.body,
                    color = appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.willCreate) {
                    Text(
                        "New — will be added to your library",
                        style = AppType.metaLabel,
                        color = accent.inkLight,
                    )
                }
            }
            Text("${row.setCount} sets", style = AppType.label, color = appColors.textMuted)
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (If `LiquidSecondaryButton`/`LiquidPrimaryButton` import paths differ, match the imports used in `IntegrationsScreen.kt` — `com.zack.recomptracker.ui.liquidglass.*`.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/share/SharedRoutineImportScreen.kt
git commit -m "feat(share): shared-routine import preview screen"
```

---

## Task 13: Navigation route + Train share wiring

Add the import route to the nav graph and implement `onShareRoutine` on `TrainHomeScreen`.

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt`

- [ ] **Step 1: Add the route constant**

In `object Routes`, add (near the other Train routes, after line 107 `const val Train = "train"`):

```kotlin
    const val SharedRoutineImport = "shared_routine_import"
```

- [ ] **Step 2: Add imports**

Add near the top imports:

```kotlin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import com.zack.recomptracker.ui.share.SharedRoutineImportScreen
import com.zack.recomptracker.ui.share.SharedRoutineImportViewModel
import com.zack.recomptracker.ui.share.shareRoutineFile
import kotlinx.coroutines.launch
```

- [ ] **Step 3: Implement `onShareRoutine` in the Train composable**

In the `composable(route = Routes.Train)` block, add a context + scope and the callback. Replace the existing `TrainHomeScreen(...)` call's argument list by inserting `onShareRoutine` (after `onLogRecovery = { ... }`), and add the two `val`s just above `TrainHomeScreen(`:

```kotlin
            val trainViewModel = viewModel<TrainViewModel>(factory = factory)
            val shareContext = LocalContext.current
            val shareScope = rememberCoroutineScope()
            TrainHomeScreen(
                viewModel = trainViewModel,
                onCreateRoutine = { navController.navigate(Routes.routineBuilder()) },
                onEditRoutine = { id -> navController.navigate(Routes.routineBuilder(id)) },
                onStart = { navController.navigate(Routes.ActiveSession) },
                onResume = { navController.navigate(Routes.ActiveSession) },
                onOpenSession = { id -> navController.navigate(Routes.sessionDetail(id)) },
                onOpenExerciseStats = { exerciseId -> navController.navigate(Routes.exerciseStats(exerciseId)) },
                onLogRecovery = {
                    navController.navigate(TopLevelDestination.Body.route) {
                        popUpTo(TopLevelDestination.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onShareRoutine = { id ->
                    shareScope.launch {
                        trainViewModel.buildShareFile(id)?.let { uri -> shareRoutineFile(shareContext, uri) }
                    }
                },
                modifier = Modifier,
            )
```

- [ ] **Step 4: Register the import screen composable**

Add a new `composable` block inside the `NavHost` (e.g. right after the `Routes.Train` block, before `Routes.ActiveSession`):

```kotlin
        composable(
            route = Routes.SharedRoutineImport,
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) {
            SharedRoutineImportScreen(
                viewModel = viewModel<SharedRoutineImportViewModel>(factory = factory),
                onClose = {
                    // Land on Train whether opened cold (empty back stack) or over an existing screen.
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.Train) { launchSingleTop = true }
                    }
                },
                onImported = {
                    navController.navigate(Routes.Train) {
                        popUpTo(Routes.SharedRoutineImport) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                modifier = Modifier,
            )
        }
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt
git commit -m "feat(share): import route + Train share-sheet wiring"
```

---

## Task 14: Route the tapped file through MainActivity → RecompApp

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/MainActivity.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt`

- [ ] **Step 1: MainActivity — read the VIEW Uri**

Add an import:

```kotlin
import android.net.Uri
```

Add a second pending-state field next to `pendingDeepLink` (after line 31):

```kotlin
    /** A tapped shared-routine file's Uri; consumed once by RecompApp then cleared. */
    private val pendingShareUri = mutableStateOf<Uri?>(null)
```

In `onCreate`, after `pendingDeepLink.value = intent.readCoachAction()` (line 47), add:

```kotlin
        pendingShareUri.value = intent.readShareUri()
```

In the `RecompApp(...)` call (lines 66–71), add the two new args (after `onDeepLinkHandled`):

```kotlin
            RecompApp(
                container = app.container,
                darkMode = darkMode,
                deepLinkAction = pendingDeepLink as State<CoachActionType?>,
                onDeepLinkHandled = { pendingDeepLink.value = null },
                shareRoutineUri = pendingShareUri as State<Uri?>,
                onShareRoutineHandled = { pendingShareUri.value = null },
            )
```

In `onNewIntent`, after `intent.readCoachAction()?.let { ... }` (line 79), add:

```kotlin
        intent.readShareUri()?.let { pendingShareUri.value = it }
```

Add the reader helper next to `readCoachAction` (after line 86):

```kotlin
    /** The Uri of a tapped shared-routine file, or null for any non-VIEW intent. */
    private fun Intent.readShareUri(): Uri? =
        if (action == Intent.ACTION_VIEW) data else null
```

- [ ] **Step 2: RecompApp — accept the Uri and navigate**

Add imports:

```kotlin
import android.net.Uri
import com.zack.recomptracker.ui.navigation.Routes
```

(`Routes` is already imported at line 49 — do NOT duplicate; only add `android.net.Uri`.)

Add the two params to `RecompApp` (after `onDeepLinkHandled`):

```kotlin
    deepLinkAction: State<CoachActionType?> = remember { mutableStateOf(null) },
    onDeepLinkHandled: () -> Unit = {},
    shareRoutineUri: State<Uri?> = remember { mutableStateOf(null) },
    onShareRoutineHandled: () -> Unit = {},
) {
```

Add a navigation effect right after the existing deep-link `LaunchedEffect` (after line 172, the block that ends with `onDeepLinkHandled()`):

```kotlin
        // Tapped shared-routine file: hand the Uri to the import inbox, then navigate to the preview
        // screen. Gated on onboardingComplete for the same reason as the coach deep-link above (the
        // NavHost isn't composed until the flag loads, and we never route over onboarding). Waits for
        // the first back-stack entry so we don't race setGraph().
        val pendingShareUriValue by shareRoutineUri
        LaunchedEffect(pendingShareUriValue, onboardingComplete) {
            val uri = pendingShareUriValue ?: return@LaunchedEffect
            if (onboardingComplete != true) return@LaunchedEffect
            container.routineShareInbox.offer(uri)
            navController.currentBackStackEntryFlow.first()
            navController.navigate(Routes.SharedRoutineImport) { launchSingleTop = true }
            onShareRoutineHandled()
        }
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Full build + full unit-test suite**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all tests pass (existing ~1300 + the new serializer/resolver tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/MainActivity.kt app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt
git commit -m "feat(share): route tapped .rtroutine file to import screen"
```

---

## Task 15: Manual on-device smoke test

Intent-filter/FileProvider wiring can't be unit-tested; verify end-to-end on a device/emulator.

- [ ] **Step 1: Install the debug build**

Run: `./gradlew :app:installDebug`
Expected: installed on the running emulator/device.

- [ ] **Step 2: Export**

In the app: Train → a routine's ⋮ menu → **Share**. Confirm the Android share sheet appears. Save the file to Google Drive or Files (or send to yourself via email/WhatsApp document).
Expected: a `<name>.rtroutine` file is produced.

- [ ] **Step 3: Import**

Open the saved file from Files/Drive (tap it). Confirm the app opens to the **Shared routine** preview with the correct name, exercises, and set counts; any exercise not in your library shows the "New" badge. Tap **Save to my routines**.
Expected: lands on Train with the routine added as a new entry; opening it shows the exercises/sets/reps/weights.

- [ ] **Step 4: Error path**

Rename any small text file to `bad.rtroutine` (or edit an exported file to corrupt the JSON) and open it.
Expected: the preview screen shows a clear "damaged"/"isn't a Recomp routine" message and a Close button — no crash.

- [ ] **Step 5: Record results**

Note any transport where "tap to open" didn't offer the app (expected for some, per the spec's reliability caveat). No commit needed unless a fix is required.

---

## Self-Review

**Spec coverage:**
- File-share transport (no backend) → Tasks 4, 6, 10, 14. ✓
- User flow (share → sheet → tap → preview → save) → Tasks 11, 13, 12, 14. ✓
- Payload shape `(version, app, name, note, exercises[source, externalId, name, note, sets])` → Task 1. ✓
- Full copy (reps + weights + notes) → Task 1 models + Task 6 export/import. ✓
- Portable by `(source, externalId, name)`, not local id → Task 6 export reads `ExerciseEntity.source`; Task 3 resolver. ✓
- `.rtroutine` extension + intent-filter extending MainActivity path → Tasks 4, 14. ✓
- Exercise resolution: externalId → name → create custom → Task 3 + Task 6. ✓
- Preview "new" badge → Tasks 8, 12. ✓
- Save always creates new; collisions allowed → Task 6 (`saveWorkout`, no rename). ✓
- Error handling (foreign/newer/corrupt/empty) → Task 2 (typed) + Task 8 (messages) + Task 12 (UI). ✓
- Tests: round-trip, resolution, rejection, portability → Tasks 2, 3. ✓
- Manual intent-filter smoke test → Task 15. ✓

**Type consistency:** `RoutineSharePayload`/`SharedExercise`/`SharedSet`/`RoutineShareResult` (Task 1) used identically in Tasks 2, 6, 8. `Resolution.willCreate` (Task 3) consumed in Tasks 6, 8. `RoutineShareRepository` methods `buildShareFile`/`readPayload`/`resolve`/`importRoutine` (Task 6) called consistently in Tasks 8, 9. `routineShareInbox.offer`/`consume` (Task 5) used in Tasks 8, 14. `SharedRoutineImportViewModel` ctor `(routineShareRepository, inbox)` matches Task 7 factory branch. `onShareRoutine: (Long)->Unit` (Task 11) wired in Task 13. `shareRoutineUri`/`onShareRoutineHandled` (Task 14 RecompApp) match MainActivity's call. ✓

**Build-order note:** Task 7 references types from Tasks 8–9; its Step 4 documents this and says a green-between-tasks workflow should implement Task 8 + Task 9's ViewModel edit before Task 7. Full green is guaranteed by Task 14 Step 4.

**Known limitation (documented, not a gap):** custom-extension tap-to-open is not 100% reliable across all transports — stated in the spec and verified in Task 15.
