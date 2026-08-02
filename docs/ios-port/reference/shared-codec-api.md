# Shared codec API — the real Swift surface

Captured from the generated Objective-C header after Phase 1b Task A3, not guessed:

```
Frameworks/Shared.xcframework/ios-arm64-simulator/Shared.framework/Headers/Shared.h
```

Regenerate this doc whenever `:shared` gains or renames a public declaration:
`./scripts/sync-shared.sh` then re-read the header. **Guessing a `swift_name` cost a build
cycle in Phase 0 — read, don't infer.**

---

## The four rules that surprise you

**1. A Kotlin `object` is not a Swift enum of statics — it is a singleton you go through.**

```swift
SharedRebalanceSerialization.shared.decode(raw: json)   // ✅
SharedRebalanceSerialization.decode(raw: json)          // ❌ does not compile
```

Every codec below is a Kotlin `object`, so every call is `Shared<Name>.shared.<method>`. The
generated `init()` returns the same singleton, so `SharedRebalanceSerialization()` also works —
prefer `.shared`, it reads as what it is.

**2. Parameter labels are the Kotlin parameter names, including on single-argument calls.**
`decode(raw:)`, `encode(state:)`, `encode(events:)`, `encode(experiment:)`,
`markSeen(ledger:dedupKey:date:)`. There is no unlabelled overload.

**3. Dates cross as Kotlin date objects — not `Date`, not `String`.**

```swift
let d = SharedKotlinx_datetimeLocalDate(year: 2026, month: 8, day: 2)
let t = SharedKotlinx_datetimeLocalDateTime(
    year: 2026, month: 8, day: 2, hour: 22, minute: 0, second: 0, nanosecond: 0)
```

`LocalDateTime` also has `init(date:time:)`. Read back with `.year` / `.monthNumber` / `.day`
(`.month` returns a `SharedKotlinx_datetimeMonth` object, not an `Int`).

This is why D6 holds on both sides: the wire format is the ISO string the codec writes, and the
Kotlin date object is only the in-memory carrier.

**4. Kotlin enum entries are lowerCamelCase properties; `.name` gives you the Kotlin name.**

```swift
SharedRebalanceIntensity.standard          // the entry
SharedRebalanceIntensity.standard.name     // "STANDARD" — the persisted spelling
SharedRebalanceStatus.endedEarly.name      // "ENDED_EARLY"
```

`isEqual:` is implemented, so Swift `==` works and compares by identity — entries are singletons.
Assert on `.name` when a test is really about the persisted string, and on the entry itself when
it is about the value.

---

## The five preference codecs (decision D12)

### `SharedCoachInboxSerialization`

| Swift | Returns |
|---|---|
| `encodeSignal(signal:)` | `String` |
| `decodeSignal(raw:)` | `SharedCoachSignal?` — `nil` on blank/malformed |
| `encodeLedger(ledger:)` | `String` |
| `decodeLedger(raw:)` | `[String: SharedKotlinx_datetimeLocalDate]` — empty on blank/malformed |
| `markSeen(ledger:dedupKey:date:)` | ledger + entry, **pruned** |
| `prune(ledger:reference:)` | pruned ledger |
| `LEDGER_RETENTION_DAYS` | `Int64` = 30 |

⚠️ `decodeLedger` does **not** prune. Pruning happens on write only, through `markSeen`. A read-time
prune would shorten every cooldown window.

### `SharedCoachJourneySerialization`

`appendFired(history:record:)` · `encodeFired(history:)` · `decodeFired(raw:)` ·
`appendVerdict(verdicts:record:)` · `encodeVerdicts(verdicts:)` · `decodeVerdicts(raw:)` ·
`hasRecurred(history:kind:)` · `recurredKinds(history:)` · `narrative(verdicts:history:)`

Caps: `FIRED_HISTORY_CAP` = 24, `VERDICT_CAP` = 8, `NARRATIVE_MIN_VERDICTS` = 2 (all `Int32`).
`appendVerdict` is idempotent per `weekSignature`.

### `SharedCoachExperimentSerialization`

`encode(experiment:)` → `String` · `decode(raw:)` → `SharedCoachExperiment?`

### `SharedPushHistorySerialization`

`encode(events:)` · `decode(raw:)` · `appendPruned(events:event:now:)` · `prune(events:now:)`
Caps: `MAX_EVENTS` = 14 (`Int32`), `RETENTION_DAYS` = 14 (`Int64`).

⚠️ The asymmetry: `prune` (age) is safe to call on read **and** write; the `MAX_EVENTS` count cap
lives only inside `appendPruned`. Do not write a read-time prune back to disk.

### `SharedRebalanceSerialization`

`encode(state:)` → `String` · `decode(raw:)` → `SharedRebalanceState` (never optional — a blank or
malformed payload decodes to the empty default state).

⚠️ Hand-rolled, not kotlinx-equivalent: `active` is **omitted entirely** when nil rather than written
as `"active": null`, and a plan missing `intensity` defaults to `.standard` instead of nulling the
whole plan, so pre-feature backups still decode.

## `SharedRoutineShareSerializer` (moved in Phase 0, used by Task B7)

`encode(payload:)` → `String` · `decode(raw:)` → `id<SharedRoutineShareResult>`

The result is a sealed interface, so Swift sees a protocol. Switch on the concrete types:
`SharedRoutineShareResultSuccess`, `SharedRoutineShareResultNotARoutineFile`,
`SharedRoutineShareResultUnsupportedVersion`, `SharedRoutineShareResultDamaged`.

---

## Constructors Part B needs

```swift
SharedPushEvent(timestamp: SharedKotlinx_datetimeLocalDateTime, isCelebration: Bool)
SharedFiredSignalRecord(kind: SharedSignalKind, dedupKey: String,
                        weekSignature: String, dateIso: String)
SharedWeeklyVerdictRecord(weekSignature: String, weekEndDateIso: String, verdict: String)
SharedCoachExperiment(correlationId: String, hypothesis: String, trackedMetric: String,
                      baselineValue: Double, startDateIso: String)
SharedRebalanceState(active: SharedRebalancePlan?, history: [SharedRebalancePlan],
                     mode: SharedRebalanceMode)
SharedRebalancePlan(id:triggerDateIso:startDateIso:endDateIso:lengthDays:mode:…)  // 19 properties
```

Enum entries: `SharedRebalanceIntensity` `.light` `.standard` `.full` ·
`SharedRebalanceMode` `.eatLess` `.balanced` `.moveMore` ·
`SharedRebalanceStatus` `.offered` `.active` `.completed` `.endedEarly` `.declined` `.noAdjustment`

## Also already exported — do not reimplement

`SharedPlanPreferences` and `SharedUserProfilePreferences` exist as Kotlin types. Swift still needs
its own `Codable` mirrors (Tasks B2/B3) because a Kotlin class cannot be conformed to `Codable` —
but the Kotlin types are the authority for **field names and defaults**. Check against them rather
than against the Android DataStore keys.
