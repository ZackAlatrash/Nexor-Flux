# 07 — Onboarding, Profile, Settings, Integrations & Backup

Scope: first-run onboarding, the user profile/plan editors, the appearance/AI/integrations/
data-backup screens reached through **More**, and the preference stores behind them.

**Out of scope (cross-referenced only):** steps, daily step goal, `weeklyGymSessions`, and the
Health-Connect steps sync belong to `01-steps-and-activity.md`. This plan references them where
they share a screen (Profile, Integrations) but does not propose changes to them.

Verified against the codebase on the `develop` branch. File:line citations below.

---

## 1. Current state & problems

### What's solid (leave alone)
- **Onboarding** (`ui/onboarding/OnboardingViewModel.kt`, `OnboardingScreen.kt`) is a clean
  4-step flow with a single write point: `finish()` persists profile, plan, *and* the first
  daily-metrics row in one coroutine (`OnboardingViewModel.kt:179-216`). Unit conversion helpers
  are pure and testable (`parseHeightCm`/`parseWeightKg`/`parseWaistCm`, lines 33-52). Entry is
  gated by `uiPreferences.onboardingComplete` (`ui/RecompApp.kt:97-98,170-175`).
- **Backup / restore / reset** (`data/repository/BackupRepository.kt`) round-trips the DB +
  plan ledger and is now covered by tests (recent commits `1fe8fda`, `8e67606`, `3921bfd`).
  Do **not** refactor this.

### Real problems (verified)

**P1 — Settings are fragmented across 5 pushed screens with no settings hub.**
`More` is the only entry point (`ui/more/MoreScreen.kt:118-165`) and groups six destinations
under a "Setup" section: Profile, Plan, Appearance, AI & Coach, Integrations, Data & Backup.
Each is its own route (`ui/navigation/AppNavGraph.kt:88-95`, `461-695`). There is a
`SettingsViewModel` (`ui/settings/SettingsViewModel.kt`) but **no SettingsScreen** — the VM is a
shared backend consumed by `DataBackupScreen`, `IntegrationsScreen`, and the nav graph
(`grep` confirms only those three + `AppContainer` reference it). So "Settings" exists as a
data layer with no screen of its own; More improvises the IA. The naming is also split: the
section is called "Setup", the backing VM is `SettingsViewModel`, and there is no screen named
either.

**P2 — Three preference stores with overlapping ownership.**
- `PlanPreferences` via `AppPreferences` (DataStore `plan_preferences`) — plan targets, zones,
  thresholds, `healthConnectEnabled`, `maintenancePhaseStartDate` (`data/preferences/AppPreferences.kt:16-83`, `PlanPreferences.kt`).
- `UserProfilePreferences` via `UserProfilePreferencesStore` — name, photo, height, DOB, sex,
  activity, goal, gym sessions, step goal (`data/preferences/UserProfilePreferences.kt:8-18`).
- `UiPreferences` (DataStore `ui_preferences`) — font, accent, theme mode, onboarding flag, AI
  backend/model/cloud config, insight toggle (`AppPreferences.kt:85-199`).

`UiPreferences` is a 100+-line grab-bag mixing pure UI (font/accent/theme) with AI/cloud config
and the onboarding flag. `SettingsViewModel` has to inject all three plus six repositories
(`SettingsViewModel.kt:58-67`) — a sign the data ownership is diffuse.

**P3 — No post-onboarding macro recompute on goal/activity change.**
Onboarding computes targets via `PlanGenerator` (`OnboardingViewModel.kt:153-175`). After
onboarding, editing **Goal** or **Activity level** in Profile only writes to
`UserProfilePreferences` (`ui/profile/ProfileScreen.kt:287-318`, persisted by
`ProfileViewModel.update`, `ProfileViewModel.kt:70-72`); it does **not** touch
`PlanPreferences`. The targets the dashboard uses stay stale. The only recompute path is the
manual **"Generate from profile"** hero card in the *Plan* screen
(`ui/plan/PlanScreen.kt:110`, `PlanViewModel.generateFromProfile`, `PlanViewModel.kt:131-133`),
which the user must discover and run on a different screen, then confirm a preview dialog. There
is no prompt or link from the place the goal actually changes (Profile).

**P4 — `maintenancePhaseStartDate` is set with no explanation and minimal effect.**
It is an Advanced field on the Plan screen (`PlanScreen.kt:178-181`, `PhaseStartField` 308-341)
with label "PHASE START DATE" and zero helper text. Its only real consumer is a single line in
`DashboardViewModel.kt:290` (`weeksSincePhaseStart = …`). The user has no UI that explains what
a maintenance phase is, when to set the date, or what changes when they do. It reads as an
orphaned power-user knob.

**P5 — No re-onboarding / edit-baseline flow.**
Once `onboardingComplete = true`, the only way to change baseline inputs is field-by-field in
Profile. There is no "redo setup" action and no way to re-trigger the guided plan reveal. The
onboarding flow is well-built but single-use.

**P6 — Samsung Health import file detection is a fragile heuristic.**
`openFoodInfoStream` (`SettingsViewModel.kt:502-519`) decides ZIP-vs-CSV by
`mimeType.contains("zip") || fileName.endsWith(".zip")`, both of which are unreliable for
SAF/`content://` URIs (`lastPathSegment` is often a document id, not a filename; the picker MIME
can be `application/octet-stream`). If a ZIP arrives mislabeled, it's read as a bare CSV and
fails confusingly. Two separate "no food found" messages exist and read differently
(`SettingsViewModel.kt:289-293` for the file-scan path vs `448-454` for the HC-history path),
the latter being a long paragraph about meal-aggregate filtering.

**P7 — Photo URI persisted-permission edge case.**
`ProfileScreen.kt:103-115` calls `takePersistableUriPermission` inside `runCatching` (good) but
if the permission is later revoked (or the grant silently fails), `profilePhotoUri` still points
at an unreadable `content://` URI. `AsyncImage` (`ProfileScreen.kt:392-400`) has no error/
fallback, so the avatar silently shows nothing with no way to tell why.

**P8 — Full reset does not re-import the NEVO / personal food catalog.**
`resetEverything` (`BackupRepository.kt:75-86`) re-seeds three meal slots but leaves the food
library empty; NEVO and personal foods are gone until the user re-imports manually. There is no
prompt or affordance pointing them to Integrations afterward.

### Audit findings that did NOT verify (correcting the record)
- **`waistSkinfoldMm` is NOT dead.** It is captured in the body check-in
  (`ui/body/BodyCheckInSheet.kt:116`), surfaced as a tile in BodyRecovery
  (`ui/today/BodyRecoveryScreen.kt:361`), shown in history
  (`ui/body/BodyHistoryScreen.kt:92`), and editable (`ui/body/BodyEditScreen.kt`). The column
  (`DailyLogEntity.kt:13`, migration `RecompDatabase.kt:113`) is wired end-to-end. **Do not
  remove it.** Treat it as a non-issue.

---

## 2. UX improvements

1. **Introduce a single Settings hub** (rename the "Setup" group into a real screen). One pushed
   `SettingsScreen` lists every configuration destination with grouped rows (Account/Profile,
   Plan & targets, Appearance, AI & Coach, Integrations, Data & Backup). More keeps only its
   *Insights* content (Calorie Decision card + Trends) and gains one "Settings" row. This gives a
   stable mental model and a place future settings actually belong.
2. **Macro-recompute prompt on goal/activity change.** When Goal or Activity level changes in
   Profile *after onboarding*, show an inline prompt ("Your goal changed — recalculate targets?")
   that reuses the existing `PlanGenerator` preview/confirm dialog already built for Plan
   (`PlanGenerationDialog.Preview`). User keeps explicit control (no silent overwrite) but never
   has to leave to another screen to find the recompute button.
3. **Explain the maintenance phase.** Add a one-line subtitle/help on the phase-start field
   ("Set the date you switched to maintenance; used to time review nudges") and consider moving
   it behind a short explainer. If product decides phase detection isn't surfaced anywhere
   meaningful, demote it further or remove the field (see §6).
4. **Edit-baseline / re-onboarding.** Add a "Redo guided setup" action in the Settings hub (or
   Profile) that re-enters the onboarding flow pre-filled from the current profile, ending in the
   same plan reveal. Lower-effort alternative: a "Recalculate my plan" entry point that jumps
   straight to `generateFromProfile`.
5. **Clarify Samsung import messaging.** Collapse the two divergent "no foods" messages into one
   consistent, short message, and make the file-type guidance actionable ("Pick the Samsung
   Health export ZIP, or the `food_info` CSV directly").

---

## 3. UI improvements

1. **Build the Settings hub with design-system primitives.** Use `ScreenScaffold(withNavBarInset
   = false)` + `SubScreenHeader(title = "Settings", onBack = …)`, `SectionLabel` groups, and
   `NeutralCard` row containers — matching the Profile screen's existing tier-2 pattern
   (`ProfileScreen.kt:138-150`).
2. **Fix the MoreScreen design-system violation.** `MoreScreen` hand-rolls its row container with
   raw `Modifier.clip().background().border()` in `MenuCard` (`MoreScreen.kt:243-255`) instead of
   `NeutralCard`, and `MenuRow` uses a manual 14dp inset divider. Per `docs/design-system.md`
   ("One card family… Don't build a raw `Column.clip().background().border()` card"), replace
   `MenuCard` with `NeutralCard`. The same row component can back the new Settings hub so More and
   Settings look identical.
3. **Profile screen conformance.** `OptionSheet` (`ProfileScreen.kt:480-510`) uses a bare
   `ModalBottomSheet` with `containerColor = surface` rather than the mandated `GlassBottomSheet`
   (design-system: "Never use a bare `ModalBottomSheet`"). Migrate the three option sheets
   (Goal/Activity/Sex) to `GlassBottomSheet`. This also aligns with the memory note about
   draggable children inside Material sheets.
4. **Avatar fallback.** Give `AsyncImage` an error/placeholder so a revoked photo URI (P7) falls
   back to the gradient + person icon instead of blank.

---

## 4. Data / model improvements

1. **Split `UiPreferences` by concern (incremental).** It currently mixes pure UI (font, accent,
   theme), the onboarding flag, and AI/cloud config (`AppPreferences.kt:85-199`). Extract an
   `AiPreferences` facade for the AI/cloud keys so the AI layer stops depending on a UI-named
   store and `SettingsViewModel`'s dependency list shrinks. Keep the same DataStore file to avoid
   migration; this is a code-organization split, not a storage change.
2. **Clarify preference-store ownership, don't merge storage.** Keep the three DataStore files
   (merging risks the working onboarding/backup paths) but document the contract: `PlanPreferences`
   = computed plan + review rules; `UserProfilePreferences` = raw user baseline; `Ui/AiPreferences`
   = app config. The macro-recompute gap (P3) is fundamentally that profile edits don't propagate
   into the computed `PlanPreferences` — solve it with the explicit recompute prompt (§2.2), not
   by collapsing stores.
3. **Robust Samsung import detection.** Replace the extension/MIME guess in `openFoodInfoStream`
   (`SettingsViewModel.kt:502-519`) with content sniffing: peek the first bytes for the ZIP magic
   number (`PK\x03\x04`) via a `BufferedInputStream` + `mark/reset`, and fall back to CSV
   otherwise. Removes the dependency on unreliable SAF filenames.
4. **`maintenancePhaseStartDate`: surface or cut.** It has exactly one consumer
   (`DashboardViewModel.kt:290`). Either give it real UI meaning (an explainer + visible
   "weeks since phase start" somewhere) or remove the field from the Plan editor and keep it
   programmatic. Don't leave it as an unexplained editable knob.
5. **Re-seed foods after full reset.** After `resetEverything` (`BackupRepository.kt:75-86`),
   either re-run the bundled NEVO seed (if one exists) or return a result that lets the UI prompt
   "Food library is empty — re-import NEVO?" with a deep link to Integrations.

---

## 5. AI opportunities (minor)

- **Recompute summary line.** When the macro-recompute prompt (§2.2) fires, the existing on-device
  coach could produce a one-line rationale ("Switching to Lean Bulk raised calories ~250 kcal").
  Low priority — the deterministic `PlanGenerator` diff already conveys this; only worth it if it
  reuses the insight-card path with no new tool.
- **No new AI tools.** Per `docs/ai-coach.md`, profile/plan settings are static, session-invariant
  data already in the system prompt — do **not** add coach tools to edit them here.

---

## 6. Quick wins

- [ ] Replace `MenuCard`'s raw card with `NeutralCard` in `MoreScreen.kt:243-255` (design-system fix).
- [ ] Add a subtitle/helper to the phase-start field (`PlanScreen.kt:308-341`) explaining the
      maintenance phase (P4).
- [ ] Unify the two "no foods found" messages and shorten the meal-aggregate paragraph
      (`SettingsViewModel.kt:289-293`, `448-454`) (P6).
- [ ] Add an error/placeholder to the Profile `AsyncImage` (`ProfileScreen.kt:392-400`) for
      revoked photo URIs (P7).
- [ ] After `resetEverything`, surface a "food library is empty" hint pointing to Integrations (P8).
- [ ] Drop the "dead `waistSkinfoldMm`" item from the backlog — it's live (see §1 correction).

## 7. Medium improvements

- [ ] **Settings hub screen** (`ui/settings/SettingsScreen.kt`) + nav route, fed by the existing
      `SettingsViewModel`; restructure More to delegate config to it (§2.1, §3.1).
- [ ] **Macro-recompute prompt** on Goal/Activity change in Profile, reusing
      `PlanGenerationDialog.Preview` (§2.2, P3). Needs a small bridge so Profile can invoke the
      generator + confirm path.
- [ ] **Content-sniff Samsung import** detection (§4.3).
- [ ] **`GlassBottomSheet` migration** for Profile's option sheets (§3.3).
- [ ] **Re-onboarding entry point** — minimum viable: a "Recalculate my plan" action; stretch:
      full prefilled re-entry into onboarding (§2.4, P5).

## 8. Bigger refactors

- [ ] **Extract `AiPreferences` from `UiPreferences`** (§4.1) — decouples the AI layer from a
      UI-named store and trims `SettingsViewModel`'s constructor. Same DataStore file, no
      migration. Largest blast radius; do last.
- [ ] **Full prefilled re-onboarding flow** (vs. the MVP recalculate button), if product wants the
      guided reveal repeatable.

---

## 9. What to avoid for now

- **Do not touch `BackupRepository` export/import/restore logic** — it's freshly test-covered
  (`1fe8fda`, `8e67606`, `3921bfd`). The only backup-adjacent change here is the post-reset food
  re-seed *prompt* (§4.5), which is additive and outside the round-trip path.
- **Do not change the onboarding write sequence** (`OnboardingViewModel.finish()`,
  `OnboardingViewModel.kt:179-216`). Re-onboarding must funnel through the same `finish()` path,
  not a parallel one.
- **Do not merge the three DataStore files** — sync/migration risk against working flows. Split by
  concern in code only (§4.1).
- **Do not remove `waistSkinfoldMm`** — it is surfaced end-to-end (§1 correction).
- **Do not auto-overwrite plan targets** when the goal changes — always prompt (§2.2). Silent
  recompute would clobber any hand-tuned macros the user set in Plan.
- **Do not add coach tools** for settings (§5).

---

## 10. Suggested implementation order

1. **Quick wins** (§6): `NeutralCard` swap, phase-start helper text, unified import messages,
   avatar fallback, post-reset food hint. Low risk, immediate polish, no new screens.
2. **Settings hub screen** (§7) using the existing `SettingsViewModel` — establishes the IA the
   rest builds on; restructure `MoreScreen` to delegate.
3. **Macro-recompute prompt** on Goal/Activity change (§7, P3) — the highest-value correctness
   fix; depends only on the generator path that already exists.
4. **Samsung import content-sniffing** + **`GlassBottomSheet` migration** (§7) — independent,
   parallelizable.
5. **Re-onboarding MVP** ("Recalculate my plan", §7) once the Settings hub exists to host it.
6. **`AiPreferences` extraction** (§8) — last, largest surface area, after the screens that
   consume preferences have stabilized.
7. **Maintenance-phase decision** (§4.4) — resolve surface-vs-cut once product weighs in; pairs
   with the §6 helper-text quick win.
