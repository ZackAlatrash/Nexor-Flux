# User Profile + AI Context Design

**Date:** 2026-06-07
**Branch:** feature/ai-verdict-explanation
**Status:** Approved

---

## Problem

The AI coach currently has no static knowledge of who the user is. It knows calorie/macro targets and daily logs, but not height, age, sex, activity level, training frequency, or goal. This means it can't calibrate advice meaningfully — it doesn't know if a weight reading is healthy, can't estimate TDEE, and doesn't know whether to optimise for fat loss or muscle gain.

---

## Solution

Add a **User Profile** stored in its own DataStore, surfaced in Settings as a "My Profile" section, and injected into the AI coach's system prompt at conversation start as static context.

---

## 1. Data Model

### New file: `data/preferences/UserProfilePreferences.kt`

```kotlin
@Serializable
data class UserProfilePreferences(
    val heightCm: Int? = null,
    val ageYears: Int? = null,
    val biologicalSex: BiologicalSex? = null,
    val activityLevel: ActivityLevel? = null,
    val weeklyGymSessions: Int? = null,   // 0–7
    val goal: FitnessGoal? = null,
)

enum class BiologicalSex { MALE, FEMALE }

enum class ActivityLevel {
    SEDENTARY,
    LIGHTLY_ACTIVE,
    MODERATELY_ACTIVE,
    VERY_ACTIVE,
}

enum class FitnessGoal {
    AGGRESSIVE_CUT,
    MODERATE_CUT,
    MINI_CUT,
    RECOMP,
    LEAN_BULK,
    MODERATE_BULK,
    AGGRESSIVE_BULK,
}
```

All fields are **nullable**. The profile is entirely optional — missing fields are simply omitted from the AI context. No defaults that pretend to be real data.

Height is always stored internally as **cm**. The UI adapts the label and input to `useMetricUnits` (cm vs. ft/in) but converts to cm before saving.

---

## 2. Storage

### New file: `data/preferences/UserProfilePreferencesStore.kt`

Follows the exact same pattern as `AppPreferences`:

- Own DataStore: `user_profile_preferences`
- `val preferences: Flow<UserProfilePreferences>` — reads all keys, falls back to null for unset fields
- `suspend fun save(profile: UserProfilePreferences)` — writes all keys; removes a key when its field is null
- Wired into `AppContainer` alongside `AppPreferences`

Enums are stored as strings via `stringPreferencesKey`. Nulls are stored by removing the key.

---

## 3. Settings UI

A **"My Profile"** section added to the existing Settings screen.

### Fields and controls

| Field | Control | Notes |
|---|---|---|
| **Goal** | Vertical radio button list (7 options) | Aggressive Cut / Moderate Cut / Mini Cut / Recomp / Lean Bulk / Moderate Bulk / Aggressive Bulk |
| **Biological sex** | Chip/segmented row | Male / Female |
| **Activity level** | Chip/segmented row (2 rows if needed) | Sedentary / Lightly Active / Moderately Active / Very Active |
| **Weekly gym sessions** | +/− stepper (0–7) | |
| **Height** | Number text field | Label: "cm" or "ft/in" based on `useMetricUnits` |
| **Age** | Number text field | Years |

### Behaviour

- All fields are optional — leaving blank is valid.
- No explicit "Save" button — changes persist on the fly (same pattern as existing plan settings).
- Height input: if `useMetricUnits = false`, accept ft + in as two separate fields and convert to cm on save.

---

## 4. AI Integration

### Change: `GemmaCoachCoordinator.createConversation()`

Read `UserProfilePreferencesStore.preferences` once (alongside the existing `planRepository.preferences` read) and pass the result to `buildSystemPrompt()`.

### Change: `buildSystemPrompt()`

Add a new static section immediately after the plan block:

```
=== USER PROFILE ===
Goal: Lean Bulk | Sex: Male | Age: 26 | Height: 178 cm
Activity: Moderately Active | Gym sessions/week: 4
=== END PROFILE ===
```

- Only fields that have been set by the user appear. Missing fields are skipped entirely.
- If no profile fields are set at all, the section is omitted.

### No new tool required

Profile data is read-only and static for the session — baking it into the system prompt is more efficient than a tool call (saves one tool iteration per conversation).

---

## 5. Files Changed

| File | Change |
|---|---|
| `data/preferences/UserProfilePreferences.kt` | **New** — data class + 3 enums |
| `data/preferences/UserProfilePreferencesStore.kt` | **New** — DataStore wrapper |
| `core/AppContainer.kt` | Wire in `UserProfilePreferencesStore` |
| `ui/settings/SettingsScreen.kt` | Add "My Profile" section |
| `ui/settings/SettingsViewModel.kt` | Expose profile state + save logic |
| `ai/GemmaCoachCoordinator.kt` | Pass profile to `buildSystemPrompt()` |

---

## 6. Out of Scope

- Lift performance history in the AI context (separate feature)
- Weekly review results in the AI context (separate feature)
- Auto-calculating TDEE or suggesting calorie targets from the profile (the AI can reason about it conversationally; no algorithmic change needed)
- Dietary restrictions / food preferences (deprioritised for now)
