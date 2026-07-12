# Share a Training Routine — Design

**Date:** 2026-07-11
**Status:** Approved (design), pending implementation plan
**Branch:** `feat/share-routine`

## Goal

Let a user share one of their training routines with another user of the app. The
sender taps **Share** on a routine; the recipient receives a small file, taps it, and
the app opens to a preview of the routine with the option to save it into their own
routines.

## Key constraint

The app is **purely local** — Room + outbound cloud LLM/food APIs, no app-writable
backend, no Firebase/Supabase runtime dependency. There is no server to host a routine
and mint a short `https://` link. A true web link would require new backend
infrastructure and a verified domain (Android App Links).

**Chosen transport: Option B — file share.** Share exports a small self-contained file
through the Android share sheet; the recipient taps it to import. Zero backend, no
domain, no hosting, works offline. (Considered and rejected: Option A, a self-contained
link — a custom-scheme URL is not reliably tappable in messaging apps, and making it
tappable requires an `https://` App Link, i.e. owning a domain + static hosting.)

## User flow

**Sender**
1. In the Train section, on a routine, taps **Share**.
2. App serializes the routine to a small file and hands it to the Android share sheet
   (WhatsApp, email, Drive, etc.).

**Recipient**
1. Taps the received file.
2. App opens to a **Shared Routine preview screen**: routine name, note, and each
   exercise with its sets / target reps / target weights.
3. Taps **Save to my routines** → routine is added as a new routine → recipient lands
   in the Train section. Or taps **Discard** to back out.

## Data model — the share payload

New file `domain/share/RoutineShareModels.kt`, following the versioned, `@Serializable`
pattern already used by `domain/export/BackupModels.kt` but scoped to one routine.

```kotlin
@Serializable
data class RoutineSharePayload(
    val version: Int,                    // = 1, forward-compat gate
    val app: String,                     // "recomptracker" — reject foreign files
    val name: String,
    val note: String?,
    val exercises: List<SharedExercise>, // ordered
)

@Serializable
data class SharedExercise(
    val source: String,                  // "free-exercise-db" or "user"
    val externalId: String,              // stable cross-device id
    val name: String,                    // fallback match + display if unknown
    val note: String?,
    val sets: List<SharedSet>,
)

@Serializable
data class SharedSet(
    val setNumber: Int,
    val targetReps: Int?,
    val targetWeightKg: Double?,
)
```

- **Full copy:** the payload carries structure, order, target reps, target weights, and
  all notes. The recipient gets an exact clone and can adjust weights after saving.
- **Portability:** each exercise carries `(source, externalId, name)` — **not** the
  sender's local `exerciseId`, which is a device-assigned autoincrement and not portable.
  Standard exercises share the same `(source, externalId)` on every install because every
  device seeds the identical bundled `free-exercise-db`.

`CURRENT_SHARE_VERSION = 1`. Serialization uses the established
`Json { ignoreUnknownKeys = true }` config.

## The file + tap-to-open mechanism

- **Extension:** a distinctive `.rtroutine` (not generic `.json`) so the recipient's tap
  routes unambiguously to this app rather than a text editor.
- **Manifest:** add one `<intent-filter>` on `MainActivity` for `ACTION_VIEW` matching
  the routine file (content/file URIs). This extends the *same* intent-reading path
  `MainActivity` already uses for coach deep-links: read the incoming `Uri` in
  `onCreate` / `onNewIntent`, parse it, route to the preview screen.
- **Sharing:** reuse the `ACTION_SEND` + SAF/FileProvider machinery already present in
  `IntegrationsScreen` / `SettingsViewModel`. Write the file to cache and share its
  `content://` URI.

**Reliability expectation (stated up front):** a custom extension is the most reliable
"tap → this app" path, but a few transports (Gmail preview, some SMS apps) may not
surface an "open with" for an unknown type. Realistic outcome: rock-solid through
Drive / Files / WhatsApp-document / email attachment; occasionally the recipient must
"save then open." Accepted trade for zero backend.

## Exercise resolution on import

For each `SharedExercise`, resolve to a local `exerciseId` in order:

1. **Match by `(source, externalId)`** — the unique index. Standard exercises resolve
   exactly (~99% of cases).
2. **Fallback: match by name** — if `externalId` isn't found (e.g. a different DB
   version), reuse the library's existing name-match (as the coach does).
3. **Create as custom** — if still unresolved (a sender's user-created exercise,
   `source = "user"`), silently insert it as a custom exercise
   (`insertCustomOrGetExisting`) using the carried name, so the routine is never broken.

The **preview screen** shows a small badge on any exercise that will be *newly created*
on import, so the recipient isn't surprised by new library entries. Resolution is
automatic — no decisions required from the recipient.

## Save behavior

- **Save always creates a new routine** (a copy) via the existing
  `WorkoutRepository.saveWorkout(name, note, lines)`. Importing never edits or
  overwrites anything the recipient already has.
- **Name collisions allowed** — no auto-rename, no forced rename dialog. Duplicates are
  cheap; the recipient can rename in the builder. *(Decision: accepted no-suffix.)*

## Error handling

The preview screen is the single choke point; every failure degrades to a clear message,
never a crash (matches the "features degrade, never crash" doctrine):

- **Not our file / foreign app** (`app` field mismatch) → "This file isn't a Recomp
  routine."
- **Newer `version`** than understood → "This routine was shared from a newer version of
  the app. Update to import it."
- **Corrupt / unparseable JSON** → "This routine file is damaged and can't be opened."
- **Empty routine** (no exercises) → rejected at parse with the damaged-file message.

## Testing

Pure-Kotlin unit tests carry the weight (no Android needed):

- **Round-trip:** `WorkoutTemplate` → serialize → deserialize → assert structural
  equality (order, sets, reps, weights, notes).
- **Resolution:** externalId hit; name-fallback hit; unknown → custom-created.
- **Rejection:** foreign `app`, future `version`, corrupt JSON, empty routine each
  produce the correct typed error.
- **Portability:** deserialize with a shuffled/missing local exercise-id map to prove
  import does not depend on the sender's local ids.

Manifest / intent-filter wiring gets a manual on-device smoke test (share to self, tap,
import), consistent with how UI is verified in the running app.

## Components (new / touched)

- **New** `domain/share/RoutineShareModels.kt` — payload data classes + version const.
- **New** `domain/share/RoutineShareSerializer.kt` (or similar) — encode a
  `WorkoutTemplate` → payload → JSON string; decode + validate JSON → typed result
  (`Success` / typed errors). Pure Kotlin.
- **New** repository method(s) — export a routine to a shareable file (cache +
  FileProvider URI); import a payload (resolve exercises, `saveWorkout`).
- **New** Shared Routine preview screen + ViewModel under `ui/train/` (or
  `ui/share/`), using the design system (`SubScreenHeader`, `FrostedCard`,
  `LiquidPrimaryButton`).
- **Touched** `MainActivity` — new `ACTION_VIEW` intent-filter + Uri read/route.
- **Touched** `AndroidManifest.xml` — intent-filter + FileProvider path (if not already
  covering cache).
- **Touched** Train UI — a **Share** affordance on each routine (overflow / row action).

## Out of scope (YAGNI)

- No backend, no accounts, no `https://` links, no web preview.
- No two-way sync, no "shared with me" inbox, no edit-after-import linkage to the sender.
- No routine marketplace / discovery.
