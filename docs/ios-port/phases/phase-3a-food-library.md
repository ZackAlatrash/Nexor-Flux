# iOS Phase 3a — Food Library, Recipe Builder, and the logging loop

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Logging becomes real. Phase 2 can only log a meal by typing its macros; 3a lets you log
from your own food library, build recipes, and log portions of those.

**Architecture:** One `@Observable` model per screen (D19), with each sheet's input state as an
independent draft value type carrying its own `validated()`. Filtering is a stored property
recomputed on change, never a computed property. Pickers are sheets with completion closures (D22).

**Tech Stack:** Swift 6.3.2, Xcode 26.5, iOS 26.0, SwiftUI, GRDB 7.11.1, Swift Testing.

**Design spec:** [`docs/superpowers/specs/2026-08-03-ios-phase-3a-design.md`](../../superpowers/specs/2026-08-03-ios-phase-3a-design.md)
Decisions **D20–D23** are settled there and must not be re-litigated.

**One repo.** Everything lands in `~/Desktop/RecompTracker-IOS`. The Android repo is untouched.

---

## 🔴 Screenshots are a gate

Phase 2's most expensive errors came from building UI off the Kotlin alone. **A screen does not get
built without a reference.** Nine are in the iOS repo's `screenshots/`, named by content:

| File | Covers |
|---|---|
| `01-food-library-all-food-rows.jpg` | Task 4 — the list, header, chips, action buttons, Recents |
| `02-food-library-recipes-tab-recipe-rows.jpg` | Task 4 — the recipe row and its "Recipe" badge |
| `03-amount-sheet-grams-mode.jpg` | Task 6 |
| `04-amount-sheet-servings-mode.jpg` | Task 6 — note the stepper's trailing "servings · 100 g" |
| `05-new-food-sheet-empty.jpg` | Task 7 |
| `06-edit-food-sheet-populated.jpg` | Task 7 — same sheet, retitled, units visible |
| `07-quick-add-sheet.jpg` | Task 8 — **name is optional**, macros three-across |
| `08-recipe-builder-new-empty.jpg` | Task 9 |
| `09-recipe-builder-edit-with-ingredients.jpg` | Task 9 — red trash, ingredient rows, "Update Recipe" |

**Two states have no screenshot and were resolved from the composables instead** — the recipe
portion sheet (Task 10) and the ingredient editor (Task 11). Their structure is given in full in
those tasks. Do not invent beyond what is written there; if something is genuinely ambiguous, stop
and ask rather than guessing.

---

## Context you need before starting

Read, in order:
1. `docs/ios-port/STATUS.md`
2. `docs/ios-port/decisions.md` — **D6** (dates as strings), **D14** (per-key decoding, Kotlin name
   qualification), **D15–D19** (Phase 2's conventions), **D20–D23** (this phase)
3. `docs/ios-port/reference/shared-codec-api.md` — the `:shared` Swift surface
4. The design spec above

Phase 2 is complete: the design system, a four-tab shell, and Food Log's thin slice. **351 tests.**

### Established facts — do NOT rediscover

- Tests live **flat** in `RecompTracker/RecompTrackerTests/`. Buildable folders mean new `.swift`
  files need **no `project.pbxproj` edit**. Never edit it.
- `SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor`. Persistence types are `nonisolated`, **and so are
  their extensions** — `nonisolated` does not propagate.
- **A Debug run does not prove zero warnings.** Build Release too.
- `#expect` cannot appear inside a throwing closure — hoist the query out first.
- ⚠️ `#expect(cond, "a" + b)` does not compile; the message is a `Comment`, not a `String`.
- ⚠️ **SwiftUI localises `Text("\(anInt)")`** — `2550` renders as "2.550" on a Dutch locale. Use
  `Text(verbatim:)` for any bare number.
- A test marked `async` can make Swift pick GRDB's *async* `read` overload and then demand an
  `await` inside a throwing autoclosure. Drop `async` from the test signature if you hit that.
- `try await database.writer.write { }` in the **app** target; plain `try` in the **test** target
  (which does not set the MainActor default).
- 🔴 **Never run `git stash`.** `refs/stash` is shared across worktrees; two parallel agents each
  popped the other's files last phase.
- With `import Shared`, Kotlin types appear **without** the `Shared` ObjC prefix.
- 🔴 **Never put `didSet` on an `@Observable` stored property.** It compiles with no diagnostic and
  then **crashes the test runner mid-run** (`Restarting after unexpected exit`). `@Observable`
  rewrites the setter into `withMutation(keyPath:) { … }`, and the `didSet` observer fires *inside*
  that call — so the handler reads the property while its own mutation is still open and re-enters
  the observation registrar. Discovered in Task 4. Use an explicit get/set over a private stored
  property instead, and have the handler read the **private** property:

```swift
    private var storedQuery: String = ""
    var query: String {
        get { storedQuery }
        set { storedQuery = newValue; recompute() }   // recompute() reads storedQuery
    }
```

- The Kotlin `MealImpactResult` carries `swift_name("MealImpact.Result")` — in Swift it is
  **`MealImpact.Result`**, a nested type, not `MealImpactResult`.
- `LibraryItem.savedFood: SavedFood?` was added in Task 4 (nil for meals and recipes, which log
  whole). Use it to get from a row to an `AmountDraft`.

### The `:shared` API this phase leans on — verified from the header, not guessed

```swift
FoodScaling.shared.gramsForServings(servings: Double, servingGrams: Double) -> Double
FoodScaling.shared.scale(basePer100: FoodMacros, grams: Double) -> FoodMacros
FoodScaling.shared.MIN_GRAMS        // 1.0
FoodScaling.shared.MIN_SERVINGS     // 0.1
FoodScaling.shared.SERVING_STEP     // 0.5
FoodScaling.shared.DEFAULT_SERVING_GRAMS   // 100.0

FoodMacros(calories: Int32, proteinG: Double, carbsG: Double, fatG: Double)

MealImpact.shared.compute(
    eatenCalories: Int32, eatenProtein: Double, eatenCarbs: Double,
    targetCalories: Int32, targetProtein: Int32, targetCarbs: Int32,
    addCalories: Int32, addProtein: Double, addCarbs: Double
) -> MealImpactResult?          // NULLABLE — nil means "no strip"

// MealImpactResult: .calories/.protein/.carbs (each MealImpactMacroImpact), .hint
// MealImpactMacroImpact: .percent (Int32), .over (Bool), .hasTarget (Bool)
```

### Records already built in Phase 1a — do not redefine

`SavedFood(id:name:servingName:calories:proteinG:carbsG:fatG:householdServingName:householdServingGrams:)`
`SavedMeal(id:name:mealType:calories:proteinG:carbsG:fatG:)`
`Recipe(id:name:)` · `RecipeIngredient(id:recipeId:name:sortOrder:calories:proteinG:carbsG:fatG:amountGrams:basePer100Calories:basePer100ProteinG:basePer100CarbsG:basePer100FatG:entryServingName:entryServingGrams:loggedByServings:)`
`RecipeWithIngredients.fetchOne(_:recipeId:)` / `.fetchAll(_:)` — **ordered by `id ASC`, matching Android, not by `sortOrder`**
`Transactions.replaceIngredients(d:recipeId:ingredients:)` — T4, already built and tested
`MealEntry` — 18 stored properties; every snippet below spells all of them.

### Build and test

```bash
cd ~/Desktop/RecompTracker-IOS
xcodebuild test -project RecompTracker/RecompTracker.xcodeproj -scheme RecompTracker \
  -destination 'platform=iOS Simulator,name=iPhone 17' 2>&1 \
  | grep -E "error:|warning:|Test run with|✘|TEST (SUCCEEDED|FAILED)" | grep -v AppIntents
```

Baseline: **351 tests** (342 running + 9 armed).

---

# PART A — Persistence and pure logic

## Task 1: Query helpers for saved foods, meals and recipes

**Files:**
- Create: `RecompTracker/RecompTracker/Persistence/Queries/SavedFoodQueries.swift`
- Create: `RecompTracker/RecompTracker/Persistence/Queries/RecipeQueries.swift`
- Create: `RecompTracker/RecompTrackerTests/LibraryQueryTests.swift`

- [ ] **Step 1: Write the failing tests**

```swift
import Foundation
import GRDB
import Testing
@testable import RecompTracker

@Suite struct LibraryQueryTests {

    private func makeFood(_ name: String, cal: Int = 100, p: Double = 10, c: Double = 5,
                          f: Double = 2, servingName: String? = nil,
                          servingGrams: Double? = nil) -> SavedFood {
        SavedFood(id: nil, name: name, servingName: "100g", calories: cal, proteinG: p,
                  carbsG: c, fatG: f, householdServingName: servingName,
                  householdServingGrams: servingGrams)
    }

    @Test func savedFoodsComeBackAlphabetically() throws {
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            for name in ["Zucchini", "Apple", "Mango"] {
                var food = makeFood(name)
                try food.insertPreservingID(d)
            }
        }
        let names = try db.reader.read { try SavedFoodQueries.all(d: $0).map(\.name) }
        #expect(names == ["Apple", "Mango", "Zucchini"])
    }

    /// Recents are NOT a food query — they are reconstructed from meal_entries via the
    /// basePer100* columns, which is what those columns exist for. An entry logged without
    /// them cannot be reconstructed and must be skipped rather than yielding a zero-calorie food.
    @Test func recentsAreReconstructedFromMealEntriesAndSkipEntriesWithoutABase() throws {
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            var withBase = MealEntry(
                id: nil, date: "2026-08-03", mealType: "FOOD_LIBRARY", name: "Oats",
                calories: 350, proteinG: 12, carbsG: 60, fatG: 6, slotId: 1, amountGrams: 100,
                basePer100Calories: 350, basePer100ProteinG: 12, basePer100CarbsG: 60,
                basePer100FatG: 6, entryServingName: "bowl", entryServingGrams: 100,
                loggedByServings: true, planned: false)
            try withBase.insertPreservingID(d)

            var withoutBase = MealEntry(
                id: nil, date: "2026-08-03", mealType: "MEAL", name: "Typed in",
                calories: 200, proteinG: 5, carbsG: 20, fatG: 8, slotId: 1, amountGrams: nil,
                basePer100Calories: nil, basePer100ProteinG: nil, basePer100CarbsG: nil,
                basePer100FatG: nil, entryServingName: nil, entryServingGrams: nil,
                loggedByServings: false, planned: false)
            try withoutBase.insertPreservingID(d)
        }

        let recents = try db.reader.read { try SavedFoodQueries.recents(d: $0, limit: 10) }
        #expect(recents.count == 1)
        #expect(recents[0].name == "Oats")
        #expect(recents[0].calories == 350, "the per-100g base, not the logged amount")
        #expect(recents[0].householdServingName == "bowl")
        #expect(recents[0].householdServingGrams == 100)
    }

    /// The same food logged five times should appear once — a recents strip of duplicates is
    /// useless.
    @Test func recentsAreDeduplicatedByName() throws {
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            for day in 1...5 {
                var entry = MealEntry(
                    id: nil, date: "2026-08-0\(day)", mealType: "FOOD_LIBRARY", name: "Oats",
                    calories: 350, proteinG: 12, carbsG: 60, fatG: 6, slotId: 1,
                    amountGrams: 100, basePer100Calories: 350, basePer100ProteinG: 12,
                    basePer100CarbsG: 60, basePer100FatG: 6, entryServingName: nil,
                    entryServingGrams: nil, loggedByServings: false, planned: false)
                try entry.insertPreservingID(d)
            }
        }
        #expect(try db.reader.read { try SavedFoodQueries.recents(d: $0, limit: 10).count } == 1)
    }

    @Test func recipesComeBackWithTheirIngredients() throws {
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            var recipe = Recipe(id: nil, name: "Anabolic oats")
            try recipe.insertPreservingID(d)
            try Transactions.replaceIngredients(d: d, recipeId: recipe.id!, ingredients: [
                RecipeIngredient(id: nil, recipeId: 0, name: "Oats", sortOrder: 0, calories: 350,
                                 proteinG: 12, carbsG: 60, fatG: 6, amountGrams: 100,
                                 basePer100Calories: 350, basePer100ProteinG: 12,
                                 basePer100CarbsG: 60, basePer100FatG: 6, entryServingName: nil,
                                 entryServingGrams: nil, loggedByServings: false),
                RecipeIngredient(id: nil, recipeId: 0, name: "Whey", sortOrder: 1, calories: 120,
                                 proteinG: 24, carbsG: 3, fatG: 1, amountGrams: 30,
                                 basePer100Calories: 400, basePer100ProteinG: 80,
                                 basePer100CarbsG: 10, basePer100FatG: 3, entryServingName: nil,
                                 entryServingGrams: nil, loggedByServings: false),
            ])
        }
        let recipes = try db.reader.read { try RecipeQueries.allWithIngredients(d: $0) }
        #expect(recipes.count == 1)
        #expect(recipes[0].ingredients.count == 2)
        #expect(recipes[0].totalCalories == 470)
        #expect(recipes[0].totalProteinG == 36)
    }

    @Test func recipeTotalsAreZeroForAnEmptyRecipe() throws {
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            var recipe = Recipe(id: nil, name: "Empty")
            try recipe.insertPreservingID(d)
        }
        let recipes = try db.reader.read { try RecipeQueries.allWithIngredients(d: $0) }
        #expect(recipes[0].totalCalories == 0)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Expected: FAIL — "cannot find 'SavedFoodQueries' in scope".

- [ ] **Step 3: Implement `SavedFoodQueries.swift`**

```swift
import Foundation
import GRDB

/// Reads over the user's personal food library.
///
/// Note what is absent: nothing here touches `catalog_foods`. NEVO is not built on iOS
/// (decision D20) — the table and its backup key stay so round-tripping works, but no query
/// reads it.
nonisolated enum SavedFoodQueries {

    /// The whole personal library, alphabetically. Android orders by name; so do we.
    static func all(d: Database) throws -> [SavedFood] {
        try SavedFood.order(Column("name")).fetchAll(d)
    }

    /// Recently logged foods, reconstructed from `meal_entries`.
    ///
    /// Not a `saved_foods` query: Android rebuilds these from the `basePer100*` columns on the
    /// entry, so a recent food carries the serving it was actually logged with. An entry with no
    /// per-100g base cannot be reconstructed — a hand-typed quick-add, for instance — and is
    /// skipped rather than surfacing as a zero-calorie food.
    static func recents(d: Database, limit: Int = 12) throws -> [SavedFood] {
        let rows = try MealEntry
            .filter(Column("basePer100Calories") != nil)
            .order(Column("id").desc)
            .fetchAll(d)

        var seen = Set<String>()
        var result: [SavedFood] = []
        for entry in rows {
            guard let base = entry.basePer100Calories else { continue }
            let key = entry.name.lowercased()
            guard !seen.contains(key) else { continue }
            seen.insert(key)
            result.append(SavedFood(
                id: nil,
                name: entry.name,
                servingName: entry.entryServingName ?? "100g",
                calories: base,
                proteinG: entry.basePer100ProteinG ?? 0,
                carbsG: entry.basePer100CarbsG ?? 0,
                fatG: entry.basePer100FatG ?? 0,
                householdServingName: entry.entryServingName,
                householdServingGrams: entry.entryServingGrams))
            if result.count == limit { break }
        }
        return result
    }

    static func allMeals(d: Database) throws -> [SavedMeal] {
        try SavedMeal.order(Column("name")).fetchAll(d)
    }
}
```

- [ ] **Step 4: Implement `RecipeQueries.swift`**

```swift
import Foundation
import GRDB

nonisolated enum RecipeQueries {
    static func allWithIngredients(d: Database) throws -> [RecipeWithIngredients] {
        try RecipeWithIngredients.fetchAll(d)
    }

    static func one(d: Database, id: Int64) throws -> RecipeWithIngredients? {
        try RecipeWithIngredients.fetchOne(d, recipeId: id)
    }

    /// Deletes the recipe; `recipe_ingredients` follows via ON DELETE CASCADE.
    static func delete(d: Database, id: Int64) throws {
        try d.execute(sql: "DELETE FROM recipes WHERE id = ?", arguments: [id])
    }
}

nonisolated extension RecipeWithIngredients {
    var totalCalories: Int { ingredients.reduce(0) { $0 + $1.calories } }
    var totalProteinG: Double { ingredients.reduce(0) { $0 + $1.proteinG } }
    var totalCarbsG: Double { ingredients.reduce(0) { $0 + $1.carbsG } }
    var totalFatG: Double { ingredients.reduce(0) { $0 + $1.fatG } }
}
```

- [ ] **Step 5: Run, then commit**

```bash
git add -A && git commit -m "feat(library): query helpers for saved foods, meals and recipes"
```

---

## Task 2: Category filtering, with the asymmetry pinned

**Files:**
- Create: `RecompTracker/RecompTracker/Features/FoodLibrary/FoodCategory.swift`
- Create: `RecompTracker/RecompTrackerTests/FoodCategoryTests.swift`

- [ ] **Step 1: Write the failing tests**

```swift
import Testing
@testable import RecompTracker

@Suite struct FoodCategoryTests {

    private func food(p: Double, c: Double, f: Double) -> SavedFood {
        SavedFood(id: nil, name: "x", servingName: "100g", calories: 100, proteinG: p,
                  carbsG: c, fatG: f, householdServingName: nil, householdServingGrams: nil)
    }

    /// Four chips, in this order. NEVO and Open Food Facts are deliberately absent (D20, D21).
    @Test func thereAreFourCategoriesInAndroidsOrder() {
        #expect(FoodCategory.allCases == [.all, .proteins, .carbs, .recipes])
    }

    @Test func allMatchesEverything() {
        #expect(FoodCategory.all.matches(food(p: 0, c: 0, f: 0)))
        #expect(FoodCategory.all.matches(food(p: 50, c: 1, f: 1)))
    }

    @Test func proteinsNeedsProteinAtLeastEqualToBothOthers() {
        #expect(FoodCategory.proteins.matches(food(p: 30, c: 10, f: 5)))
        #expect(FoodCategory.proteins.matches(food(p: 10, c: 10, f: 10)), "ties count as protein")
        #expect(!FoodCategory.proteins.matches(food(p: 5, c: 30, f: 1)))
    }

    /// 🔴 The asymmetry is deliberate and load-bearing: protein compares with >=, fat with >.
    /// A food with equal carbs and fat is NOT carbs. Ported verbatim from
    /// `FoodLibraryViewModel.computeFilteredFoods`.
    @Test func carbsUsesGreaterOrEqualForProteinButStrictlyGreaterForFat() {
        #expect(FoodCategory.carbs.matches(food(p: 10, c: 60, f: 5)))
        #expect(FoodCategory.carbs.matches(food(p: 20, c: 20, f: 5)), "ties with protein count")
        #expect(!FoodCategory.carbs.matches(food(p: 5, c: 20, f: 20)),
                "a tie with FAT does not count — this is the asymmetry")
        #expect(!FoodCategory.carbs.matches(food(p: 30, c: 10, f: 1)))
    }

    /// A zero-macro food: protein ties both, so it is protein; carbs ties fat, so it is not carbs.
    @Test func anAllZeroFoodIsProteinButNotCarbs() {
        let zero = food(p: 0, c: 0, f: 0)
        #expect(FoodCategory.proteins.matches(zero))
        #expect(!FoodCategory.carbs.matches(zero))
    }

    @Test func recipesMatchesNoPlainFoodBecauseItIsADifferentList() {
        #expect(!FoodCategory.recipes.matches(food(p: 30, c: 1, f: 1)))
    }

    @Test func labelsMatchAndroid() {
        #expect(FoodCategory.allCases.map(\.label) == ["All", "Proteins", "Carbs", "Recipes"])
    }
}
```

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement**

```swift
import Foundation

/// The library's filter chips.
///
/// Four, not Android's six: NEVO is not built on iOS (D20) and Open Food Facts moves to Phase 4
/// with the scanner (D21). Android's enum spells the recipes case `MEALS` and labels it "Recipes";
/// this uses the label as the case name, since the legacy spelling means nothing here.
nonisolated enum FoodCategory: String, CaseIterable, Hashable, Sendable {
    case all, proteins, carbs, recipes

    var label: String {
        switch self {
        case .all: "All"
        case .proteins: "Proteins"
        case .carbs: "Carbs"
        case .recipes: "Recipes"
        }
    }

    /// Whether a food belongs to this category.
    ///
    /// ⚠️ The comparison operators are **not** symmetric, and that is faithful to Android:
    /// `carbs` requires `carbs >= protein` but `carbs > fat`. A food with equal carbs and fat is
    /// therefore *not* carbs. Do not "tidy" this into two `>=` — the test will catch you, which is
    /// why the test exists.
    func matches(_ food: SavedFood) -> Bool {
        switch self {
        case .all:
            return true
        case .proteins:
            return food.proteinG >= food.carbsG && food.proteinG >= food.fatG
        case .carbs:
            return food.carbsG >= food.proteinG && food.carbsG > food.fatG
        case .recipes:
            // Recipes are a separate list, not a predicate over foods.
            return false
        }
    }
}
```

- [ ] **Step 4: Run, then commit**

```bash
git add -A && git commit -m "feat(library): four filter categories with Android's asymmetric carbs rule"
```

---

## Task 3: The four drafts

**Files:**
- Create: `RecompTracker/RecompTracker/Features/FoodLibrary/Drafts.swift`
- Create: `RecompTracker/RecompTrackerTests/LibraryDraftTests.swift`
- Modify: `RecompTracker/RecompTracker/Features/FoodLog/QuickAddSheet.swift` — move and amend

Each sheet's input state is an independent value type with one `validated()`. That single definition
drives both the confirm button's enabled state and the write path, and it is testable without
presenting anything.

- [ ] **Step 1: Write the failing tests**

```swift
import Testing
@testable import RecompTracker

@Suite struct AmountDraftTests {

    private let withServing = SavedFood(
        id: 1, name: "Oats", servingName: "bowl", calories: 350, proteinG: 12, carbsG: 60,
        fatG: 6, householdServingName: "bowl", householdServingGrams: 80)

    private let withoutServing = SavedFood(
        id: 2, name: "Rice", servingName: "100g", calories: 130, proteinG: 3, carbsG: 28,
        fatG: 0, householdServingName: nil, householdServingGrams: nil)

    /// Servings mode is only offered when the food defines a real household serving. Android
    /// requires BOTH a name and >= 1g; either alone is not enough.
    @Test func servingsModeIsOfferedOnlyWithACompleteHouseholdServing() {
        #expect(AmountDraft(food: withServing).canUseServings)
        #expect(!AmountDraft(food: withoutServing).canUseServings)

        let namedButZero = SavedFood(
            id: 3, name: "x", servingName: "100g", calories: 100, proteinG: 1, carbsG: 1,
            fatG: 1, householdServingName: "scoop", householdServingGrams: 0)
        #expect(!AmountDraft(food: namedButZero).canUseServings)

        let sizedButUnnamed = SavedFood(
            id: 4, name: "x", servingName: "100g", calories: 100, proteinG: 1, carbsG: 1,
            fatG: 1, householdServingName: "  ", householdServingGrams: 50)
        #expect(!AmountDraft(food: sizedButUnnamed).canUseServings)
    }

    @Test func openingDefaultsToServingsWhenAvailableAndGramsOtherwise() {
        #expect(AmountDraft(food: withServing).mode == .servings)
        #expect(AmountDraft(food: withoutServing).mode == .grams)
        #expect(AmountDraft(food: withServing).servingsText == "1")
        #expect(AmountDraft(food: withoutServing).gramsText == "100")
    }

    @Test func resolvedGramsConvertsServingsThroughTheSharedScaler() {
        var draft = AmountDraft(food: withServing)
        draft.servingsText = "2"
        #expect(draft.resolvedGrams == 160, "2 x 80g")
        draft.mode = .grams
        draft.gramsText = "250"
        #expect(draft.resolvedGrams == 250)
    }

    @Test func rejectsAmountsBelowTheSharedMinimums() {
        var draft = AmountDraft(food: withoutServing)
        draft.gramsText = "0"
        #expect(draft.resolvedGrams == nil)
        draft.gramsText = "abc"
        #expect(draft.resolvedGrams == nil)
        draft.gramsText = ""
        #expect(draft.resolvedGrams == nil)

        var servings = AmountDraft(food: withServing)
        servings.servingsText = "0"
        #expect(servings.resolvedGrams == nil)
    }

    @Test func previewScalesMacrosFromThePer100Base() {
        var draft = AmountDraft(food: withoutServing)
        draft.gramsText = "200"
        let preview = draft.preview
        #expect(preview?.calories == 260)
        #expect(preview?.carbsG == 56)
    }

    /// Both decimal separators — a decimal-pad keyboard emits the locale's.
    @Test func acceptsEitherDecimalSeparator() {
        var draft = AmountDraft(food: withoutServing)
        draft.gramsText = "12.5"
        #expect(draft.resolvedGrams == 12.5)
        draft.gramsText = "12,5"
        #expect(draft.resolvedGrams == 12.5)
    }

    /// Reopening an existing entry must restore the mode it was logged in and back-compute the
    /// servings count. Getting this wrong silently changes the amount when the user saves.
    @Test func editingRestoresTheModeAndBackComputesServings() {
        let entry = MealEntry(
            id: 9, date: "2026-08-03", mealType: "FOOD_LIBRARY", name: "Oats", calories: 700,
            proteinG: 24, carbsG: 120, fatG: 12, slotId: 1, amountGrams: 160,
            basePer100Calories: 350, basePer100ProteinG: 12, basePer100CarbsG: 60,
            basePer100FatG: 6, entryServingName: "bowl", entryServingGrams: 80,
            loggedByServings: true, planned: false)

        let draft = AmountDraft(editing: entry)
        #expect(draft != nil)
        #expect(draft?.mode == .servings)
        #expect(draft?.servingsText == "2", "160g / 80g per serving")
        #expect(draft?.food.calories == 350, "the per-100g base, not the logged total")
    }

    @Test func editingAGramsLoggedEntryRestoresGramsMode() {
        let entry = MealEntry(
            id: 9, date: "2026-08-03", mealType: "FOOD_LIBRARY", name: "Rice", calories: 260,
            proteinG: 6, carbsG: 56, fatG: 0, slotId: 1, amountGrams: 200,
            basePer100Calories: 130, basePer100ProteinG: 3, basePer100CarbsG: 28,
            basePer100FatG: 0, entryServingName: nil, entryServingGrams: nil,
            loggedByServings: false, planned: false)

        let draft = AmountDraft(editing: entry)
        #expect(draft?.mode == .grams)
        #expect(draft?.gramsText == "200")
    }

    /// An entry with no per-100g base cannot be reopened in this sheet — there is nothing to
    /// scale from. The caller must fall back rather than showing a sheet full of zeroes.
    @Test func editingAnEntryWithoutABaseYieldsNil() {
        let entry = MealEntry(
            id: 9, date: "2026-08-03", mealType: "MEAL", name: "Typed", calories: 200,
            proteinG: 5, carbsG: 20, fatG: 8, slotId: 1, amountGrams: nil,
            basePer100Calories: nil, basePer100ProteinG: nil, basePer100CarbsG: nil,
            basePer100FatG: nil, entryServingName: nil, entryServingGrams: nil,
            loggedByServings: false, planned: false)
        #expect(AmountDraft(editing: entry) == nil)
    }
}

/// ⚠️ `FoodDraft`'s property names mirror `SavedFood`'s exactly, and the distinction matters:
/// `servingName` is the *display* serving ("100g"), while `householdServingName` /
/// `householdServingGrams` are the optional household portion ("bowl", 80). Naming the draft
/// fields anything else invites passing one where the other belongs — they are both `String`.
@Suite struct FoodDraftTests {

    @Test func acceptsAWellFormedFood() {
        let draft = FoodDraft(name: "Oats", servingName: "100g", calories: "350", protein: "12",
                              carbs: "60", fat: "6")
        #expect(draft.validated() != nil)
    }

    @Test func rejectsABlankNameOrMissingCalories() {
        #expect(FoodDraft(name: "  ", calories: "350").validated() == nil)
        #expect(FoodDraft(name: "Oats", calories: "").validated() == nil)
        #expect(FoodDraft(name: "Oats", calories: "-1").validated() == nil)
    }

    /// Empty macro fields mean zero. The household serving is optional, but supplying a name
    /// without grams would make servings mode unusable later, so both or neither.
    @Test func householdServingNameAndGramsMustBeSuppliedTogether() {
        #expect(FoodDraft(name: "x", calories: "10", householdServingName: "scoop",
                          householdServingGrams: "30").validated() != nil)
        #expect(FoodDraft(name: "x", calories: "10", householdServingName: "scoop",
                          householdServingGrams: "").validated() == nil)
        #expect(FoodDraft(name: "x", calories: "10", householdServingName: "",
                          householdServingGrams: "30").validated() == nil)
        #expect(FoodDraft(name: "x", calories: "10").validated() != nil, "neither is fine")
    }

    @Test func roundTripsAnExistingFoodForEditing() {
        let food = SavedFood(id: 7, name: "Oats", servingName: "100g", calories: 350,
                             proteinG: 12, carbsG: 60, fatG: 6, householdServingName: "bowl",
                             householdServingGrams: 80)
        let draft = FoodDraft(editing: food)
        #expect(draft.name == "Oats")
        #expect(draft.calories == "350")
        #expect(draft.servingName == "100g", "the display serving")
        #expect(draft.householdServingName == "bowl")
        #expect(draft.householdServingGrams == "80")
        #expect(draft.validated()?.householdServingGrams == 80)
        #expect(draft.validated()?.id == 7, "editing keeps the id, so confirm updates")
    }
}

@Suite struct PortionDraftTests {

    @Test func defaultsToOneWholeRecipe() {
        #expect(PortionDraft().portionsText == "1")
        #expect(PortionDraft().factor == 1)
    }

    @Test func rejectsPortionsBelowTheSharedMinimum() {
        var draft = PortionDraft()
        draft.portionsText = "0"
        #expect(draft.factor == nil)
        draft.portionsText = "nope"
        #expect(draft.factor == nil)
    }

    @Test func scalesWholeRecipeMacros() {
        var draft = PortionDraft()
        draft.portionsText = "0.5"
        let scaled = draft.scale(calories: 800, protein: 60, carbs: 80, fat: 20)
        #expect(scaled?.calories == 400)
        #expect(scaled?.proteinG == 30)
    }
}
```

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement `Drafts.swift`**

```swift
import Foundation
import Shared

nonisolated enum AmountMode: String, Hashable, Sendable { case servings, grams }

/// Scaled macros for a pending amount.
nonisolated struct ScaledMacros: Equatable, Sendable {
    let calories: Int
    let proteinG: Double
    let carbsG: Double
    let fatG: Double
}

/// The amount sheet's input state.
nonisolated struct AmountDraft: Equatable, Sendable {
    /// The per-100g base being scaled. On edit this is *reconstructed* from the entry.
    var food: SavedFood
    var mode: AmountMode
    var servingsText: String = "1"
    var gramsText: String = "100"
    /// Set when reopening an existing entry, so confirm updates rather than inserts.
    var editingEntryId: Int64?

    init(food: SavedFood) {
        self.food = food
        self.mode = Self.canUseServings(food) ? .servings : .grams
    }

    /// Reopens an existing entry. Returns `nil` when the entry has no per-100g base — there is
    /// nothing to scale from, and a sheet full of zeroes would be worse than no sheet.
    init?(editing entry: MealEntry) {
        guard let base = entry.basePer100Calories else { return nil }
        let reconstructed = SavedFood(
            id: nil,
            name: entry.name,
            servingName: entry.entryServingName ?? "100g",
            calories: base,
            proteinG: entry.basePer100ProteinG ?? 0,
            carbsG: entry.basePer100CarbsG ?? 0,
            fatG: entry.basePer100FatG ?? 0,
            householdServingName: entry.entryServingName,
            householdServingGrams: entry.entryServingGrams)

        self.food = reconstructed
        self.editingEntryId = entry.id

        let servingGrams = entry.entryServingGrams
        // Android: servings mode if it was logged that way, OR if the entry carries both a
        // serving name and size (an older entry predating the flag).
        let useServings = (entry.loggedByServings && servingGrams != nil)
            || (servingGrams != nil && entry.entryServingName != nil)
        self.mode = useServings ? .servings : .grams

        if useServings, let per = servingGrams, per >= 1 {
            let servings = max((entry.amountGrams ?? 0) / per, 1)
            self.servingsText = Self.trimmed(servings)
        } else {
            self.servingsText = "1"
        }
        self.gramsText = String(Int(entry.amountGrams ?? 100))
    }

    var canUseServings: Bool { Self.canUseServings(food) }

    private static func canUseServings(_ food: SavedFood) -> Bool {
        (food.householdServingGrams ?? 0) >= 1
            && !(food.householdServingName ?? "").trimmingCharacters(in: .whitespaces).isEmpty
    }

    /// The amount in grams, or `nil` when the input is not yet loggable.
    var resolvedGrams: Double? {
        switch mode {
        case .servings:
            guard let servings = Self.number(servingsText),
                  servings >= FoodScaling.shared.MIN_SERVINGS else { return nil }
            let per = food.householdServingGrams ?? FoodScaling.shared.DEFAULT_SERVING_GRAMS
            guard per >= 1 else { return nil }
            return FoodScaling.shared.gramsForServings(servings: servings, servingGrams: per)
        case .grams:
            guard let grams = Self.number(gramsText),
                  grams >= FoodScaling.shared.MIN_GRAMS else { return nil }
            return grams
        }
    }

    /// Scaled through the shared Kotlin scaler, so iOS and Android cannot disagree on a macro.
    var preview: ScaledMacros? {
        guard let grams = resolvedGrams else { return nil }
        let base = FoodMacros(calories: Int32(food.calories), proteinG: food.proteinG,
                              carbsG: food.carbsG, fatG: food.fatG)
        let scaled = FoodScaling.shared.scale(basePer100: base, grams: grams)
        return ScaledMacros(calories: Int(scaled.calories), proteinG: scaled.proteinG,
                            carbsG: scaled.carbsG, fatG: scaled.fatG)
    }

    mutating func step(_ delta: Double) {
        switch mode {
        case .servings:
            let current = Self.number(servingsText) ?? 1
            servingsText = Self.trimmed(max(current + delta, FoodScaling.shared.MIN_SERVINGS))
        case .grams:
            let current = Self.number(gramsText) ?? 100
            gramsText = String(Int(max(current + delta, FoodScaling.shared.MIN_GRAMS)))
        }
    }

    /// Accepts both separators — a decimal-pad keyboard emits the locale's.
    static func number(_ raw: String) -> Double? {
        let trimmed = raw.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return nil }
        guard let value = Double(trimmed.replacingOccurrences(of: ",", with: ".")),
              value.isFinite else { return nil }
        return value
    }

    /// "2" rather than "2.0" for whole numbers, matching Android's stepper text.
    static func trimmed(_ value: Double) -> String {
        value == value.rounded() && abs(value) < 1e9
            ? String(Int(value))
            : String(value)
    }
}

/// The new/edit food sheet's input state.
///
/// Field names mirror `SavedFood`'s deliberately: `servingName` is the **display** serving
/// ("100g"), `householdServingName`/`householdServingGrams` the optional household portion
/// ("bowl", 80). Two `String` fields whose meanings are one rename apart from being swapped.
nonisolated struct FoodDraft: Equatable, Sendable, Identifiable {
    var name: String = ""
    var servingName: String = "100g"
    var calories: String = ""
    var protein: String = ""
    var carbs: String = ""
    var fat: String = ""
    var householdServingName: String = ""
    var householdServingGrams: String = ""
    /// Set when editing, so confirm updates rather than inserts.
    var editingFoodId: Int64?

    /// Stable for `.sheet(item:)`. A new food has no id, so `0` stands in — the sheet is
    /// presented by *setting* the optional, never by identity changing underneath it.
    var id: Int64 { editingFoodId ?? 0 }

    init(name: String = "", servingName: String = "100g", calories: String = "",
         protein: String = "", carbs: String = "", fat: String = "",
         householdServingName: String = "", householdServingGrams: String = "",
         editingFoodId: Int64? = nil) {
        self.name = name; self.servingName = servingName; self.calories = calories
        self.protein = protein; self.carbs = carbs; self.fat = fat
        self.householdServingName = householdServingName
        self.householdServingGrams = householdServingGrams
        self.editingFoodId = editingFoodId
    }

    init(editing food: SavedFood) {
        self.init(name: food.name,
                  servingName: food.servingName,
                  calories: String(food.calories),
                  protein: AmountDraft.trimmed(food.proteinG),
                  carbs: AmountDraft.trimmed(food.carbsG),
                  fat: AmountDraft.trimmed(food.fatG),
                  householdServingName: food.householdServingName ?? "",
                  householdServingGrams: food.householdServingGrams.map(AmountDraft.trimmed) ?? "",
                  editingFoodId: food.id)
    }

    /// `nil` when not yet saveable. Empty macro fields mean zero.
    ///
    /// The household name and grams must be supplied together: a name without a size makes
    /// servings mode unusable later, and a size without a name gives the stepper nothing to label
    /// itself with. Both or neither.
    func validated() -> SavedFood? {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else { return nil }
        guard let cal = Int(calories.trimmingCharacters(in: .whitespaces)), cal >= 0 else {
            return nil
        }
        guard let p = optional(protein), let c = optional(carbs), let f = optional(fat) else {
            return nil
        }

        let household = householdServingName.trimmingCharacters(in: .whitespaces)
        let householdGrams = AmountDraft.number(householdServingGrams)
        switch (household.isEmpty, householdGrams) {
        case (true, .none): break
        case (false, .some(let grams)) where grams > 0: break
        default: return nil
        }

        return SavedFood(
            id: editingFoodId,
            name: trimmedName,
            servingName: servingName.trimmingCharacters(in: .whitespaces),
            calories: cal, proteinG: p, carbsG: c, fatG: f,
            householdServingName: household.isEmpty ? nil : household,
            householdServingGrams: household.isEmpty ? nil : householdGrams)
    }

    private func optional(_ raw: String) -> Double? {
        if raw.trimmingCharacters(in: .whitespaces).isEmpty { return 0 }
        guard let value = AmountDraft.number(raw), value >= 0 else { return nil }
        return value
    }
}

/// The recipe portion sheet's input state. 1 = the whole recipe.
nonisolated struct PortionDraft: Equatable, Sendable {
    var portionsText: String = "1"

    var factor: Double? {
        guard let value = AmountDraft.number(portionsText),
              value >= FoodScaling.shared.MIN_SERVINGS else { return nil }
        return value
    }

    func scale(calories: Int, protein: Double, carbs: Double, fat: Double) -> ScaledMacros? {
        guard let factor else { return nil }
        return ScaledMacros(
            calories: Int((Double(calories) * factor).rounded()),
            proteinG: protein * factor,
            carbsG: carbs * factor,
            fatG: fat * factor)
    }

    mutating func step(_ delta: Double) {
        let current = factor ?? 1
        portionsText = AmountDraft.trimmed(max(current + delta, FoodScaling.shared.MIN_SERVINGS))
    }
}
```

Also add the two `Identifiable` conformances the sheets need, next to their types:

```swift
nonisolated extension AmountDraft: Identifiable {
    /// Stable across edits of the same draft — the food (or the entry being edited) is what the
    /// sheet is *about*, so the id must not change when the user types.
    var id: String { editingEntryId.map { "entry_\($0)" } ?? "food_\(food.id ?? 0)_\(food.name)" }
}

nonisolated extension RecipeWithIngredients: Identifiable {
    var id: Int64 { recipe.id ?? -1 }
}
```

- [ ] **Step 4: Move and amend `QuickAddSheet`**

Move `Features/FoodLog/QuickAddSheet.swift` → `Features/FoodLibrary/QuickAddSheet.swift`, then make
two corrections the screenshots caught (`07-quick-add-sheet.jpg`):

1. **The name becomes optional.** Android's subtitle is *"Log calories without creating a food"*.
   In `QuickAddDraft.validated()`, replace the blank-name guard with a fallback:

```swift
    func validated() -> Validated? {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        // Android's name field is explicitly optional — "Log calories without creating a food".
        // A blank name becomes "Quick add" rather than blocking the save.
        let resolvedName = trimmed.isEmpty ? "Quick add" : trimmed
        guard let calories = Int(calories.trimmingCharacters(in: .whitespaces)), calories >= 0
        else { return nil }
        guard let protein = optionalDouble(proteinG),
              let carbs = optionalDouble(carbsG),
              let fat = optionalDouble(fatG)
        else { return nil }
        return Validated(name: resolvedName, calories: calories,
                         proteinG: protein, carbsG: carbs, fatG: fat)
    }
```

2. **The three macro fields sit in one row**, not stacked. Replace the `Section` containing the
   three `macroField` calls with:

```swift
                Section {
                    HStack(spacing: Spacing.md) {
                        compactMacroField("Protein", text: $draft.proteinG, field: .protein)
                        compactMacroField("Carbs", text: $draft.carbsG, field: .carbs)
                        compactMacroField("Fat", text: $draft.fatG, field: .fat)
                    }
                } header: {
                    Text("Macros")
                } footer: {
                    Text("Leave a macro blank to log it as zero.")
                }
```

and add:

```swift
    private func compactMacroField(_ label: String, text: Binding<String>,
                                   field: Field) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(label).appType(AppType.macroLabel)
                .foregroundStyle(AppColor.textMuted.color)
            TextField("0", text: text)
                .keyboardType(.decimalPad)
                .focused($focus, equals: field)
        }
    }
```

Update the two now-wrong tests in `QuickAddValidationTests.swift`:

```swift
    /// Android's name is optional — the subtitle says so. A blank name becomes "Quick add".
    @Test func aBlankNameIsAcceptedAndFallsBackToQuickAdd() {
        #expect(QuickAddDraft(name: "", calories: "100").validated()?.name == "Quick add")
        #expect(QuickAddDraft(name: "   ", calories: "100").validated()?.name == "Quick add")
    }
```

Delete `rejectsABlankOrWhitespaceName`, which asserted the opposite.

- [ ] **Step 5: Run, then commit**

```bash
git add -A && git commit -m "feat(library): the four input drafts; quick-add name becomes optional"
```

---

# PART B — The Food Library

## Task 4: `FoodLibraryModel`

**Files:**
- Create: `RecompTracker/RecompTracker/Features/FoodLibrary/FoodLibraryModel.swift`
- Create: `RecompTracker/RecompTrackerTests/FoodLibraryModelTests.swift`

Mirrors `FoodLibraryViewModel` minus NEVO and Open Food Facts.

- [ ] **Step 1: Write the failing tests**

```swift
import Foundation
import GRDB
import Shared
import Testing
@testable import RecompTracker

@Suite struct FoodLibraryModelTests {

    private func seedFoods(_ db: AppDatabase) throws {
        try db.writer.write { d in
            for (name, p, c, f) in [("Chicken", 30.0, 0.0, 3.0),
                                    ("Rice", 3.0, 28.0, 0.0),
                                    ("Olive oil", 0.0, 0.0, 100.0)] {
                var food = SavedFood(id: nil, name: name, servingName: "100g", calories: 100,
                                     proteinG: p, carbsG: c, fatG: f,
                                     householdServingName: nil, householdServingGrams: nil)
                try food.insertPreservingID(d)
            }
        }
    }

    /// 🔴 The two stores MUST be injected. `loadTargets()` otherwise opens the *real*
    /// `PlanPreferencesStore`, so every impact and remaining-calorie assertion would depend on
    /// whatever plan happens to be on the machine running the suite. Phase 2's `FoodLogModelTests`
    /// established this pattern — follow it.
    private func model(_ db: AppDatabase, slotId: Int64? = 1,
                       date: String = "2026-08-03",
                       pickerMode: Bool = false) -> FoodLibraryModel {
        FoodLibraryModel(
            database: db, slotId: slotId, slotName: "Breakfast",
            logDate: CalendarDay(date)!, today: CalendarDay("2026-08-03")!,
            pickerMode: pickerMode,
            planStore: PlanPreferencesStore(store: .temporary(default: PlanPreferences())),
            rebalanceStore: .temporary())
    }

    @Test func loadsTheLibraryAlphabetically() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedFoods(db)
        let m = model(db)
        try await m.loadOnce()
        #expect(m.items.map(\.name) == ["Chicken", "Olive oil", "Rice"])
    }

    @Test func searchFiltersByNameCaseInsensitively() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedFoods(db)
        let m = model(db)
        try await m.loadOnce()
        m.query = "ric"
        #expect(m.items.map(\.name) == ["Rice"])
        m.query = "RICE"
        #expect(m.items.map(\.name) == ["Rice"])
    }

    @Test func categoryFiltersUseTheSharedPredicate() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedFoods(db)
        let m = model(db)
        try await m.loadOnce()
        m.category = .proteins
        #expect(m.items.map(\.name) == ["Chicken"])
        m.category = .carbs
        #expect(m.items.map(\.name) == ["Rice"])
    }

    /// Filtering must be a stored property recomputed on change, not computed per render.
    /// This asserts the observable consequence: changing an input updates `items` immediately.
    @Test func changingQueryOrCategoryRecomputesItemsImmediately() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedFoods(db)
        let m = model(db)
        try await m.loadOnce()
        #expect(m.items.count == 3)
        m.query = "zzz"
        #expect(m.items.isEmpty)
        m.query = ""
        #expect(m.items.count == 3)
    }

    @Test func recipesTabShowsRecipesRatherThanFoods() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedFoods(db)
        try db.writer.write { d in
            var recipe = Recipe(id: nil, name: "Anabolic oats")
            try recipe.insertPreservingID(d)
        }
        let m = model(db)
        try await m.loadOnce()
        m.category = .recipes
        #expect(m.items.isEmpty, "the food list is empty on the recipes tab")
        #expect(m.recipes.map(\.recipe.name) == ["Anabolic oats"])
    }

    /// 🔴 Logging onto a FUTURE date creates a plan, not an eaten entry.
    @Test func loggingOntoAFutureDateCreatesAPlannedEntry() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedFoods(db)
        let m = model(db, date: "2026-08-10")
        try await m.loadOnce()

        var draft = AmountDraft(food: m.items[0])
        draft.gramsText = "100"
        try await m.confirm(draft)

        let stored = try db.reader.read { try MealEntry.fetchOne($0) }
        #expect(stored?.planned == true)
        #expect(stored?.date == "2026-08-10")
    }

    @Test func loggingOntoTodayCreatesAnEatenEntry() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedFoods(db)
        let m = model(db)
        try await m.loadOnce()

        var draft = AmountDraft(food: m.items[0])
        draft.gramsText = "100"
        try await m.confirm(draft)

        #expect(try db.reader.read { try MealEntry.fetchOne($0) }?.planned == false)
    }

    /// The entry must carry full provenance, or reopening it in the amount sheet is impossible
    /// and it never appears in Recents.
    @Test func aLoggedEntryCarriesItsPer100BaseAndServing() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            var food = SavedFood(id: nil, name: "Oats", servingName: "bowl", calories: 350,
                                 proteinG: 12, carbsG: 60, fatG: 6,
                                 householdServingName: "bowl", householdServingGrams: 80)
            try food.insertPreservingID(d)
        }
        let m = model(db)
        try await m.loadOnce()

        var draft = AmountDraft(food: m.items[0])
        draft.servingsText = "2"
        try await m.confirm(draft)

        let stored = try #require(try db.reader.read { try MealEntry.fetchOne($0) })
        #expect(stored.amountGrams == 160)
        #expect(stored.calories == 560, "350 per 100g x 160g")
        #expect(stored.basePer100Calories == 350)
        #expect(stored.entryServingName == "bowl")
        #expect(stored.entryServingGrams == 80)
        #expect(stored.loggedByServings == true)
        #expect(stored.mealType == "FOOD_LIBRARY")
        #expect(stored.slotId == 1)
    }

    @Test func confirmingAnEditUpdatesRatherThanInserting() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedFoods(db)
        let m = model(db)
        try await m.loadOnce()

        var draft = AmountDraft(food: m.items[0])
        draft.gramsText = "100"
        try await m.confirm(draft)

        let entry = try #require(try db.reader.read { try MealEntry.fetchOne($0) })
        var edit = try #require(AmountDraft(editing: entry))
        edit.gramsText = "250"
        try await m.confirm(edit)

        let all = try db.reader.read { try MealEntry.fetchAll($0) }
        #expect(all.count == 1, "updated, not duplicated")
        #expect(all[0].amountGrams == 250)
    }

    /// The impact strip is today-only. On another day it must be nil, because projecting onto
    /// today's remaining plan would be misleading.
    @Test func mealImpactIsNilOnAnyDayButToday() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedFoods(db)

        let todayModel = model(db)
        try await todayModel.loadOnce()
        var draft = AmountDraft(food: todayModel.items[0])
        draft.gramsText = "100"
        #expect(todayModel.impact(for: draft) != nil)

        let futureModel = model(db, date: "2026-08-10")
        try await futureModel.loadOnce()
        #expect(futureModel.impact(for: draft) == nil)
    }

    /// And nil in picker mode — the ingredient is going into a recipe, not onto today's plan.
    @Test func mealImpactIsNilInPickerMode() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedFoods(db)
        let m = model(db, slotId: nil, pickerMode: true)
        try await m.loadOnce()
        var draft = AmountDraft(food: m.items[0])
        draft.gramsText = "100"
        #expect(m.impact(for: draft) == nil)
    }

    @Test func remainingCaloriesNeverGoesNegative() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            var entry = MealEntry(
                id: nil, date: "2026-08-03", mealType: "MEAL", name: "Huge", calories: 9000,
                proteinG: 0, carbsG: 0, fatG: 0, slotId: 1, amountGrams: nil,
                basePer100Calories: nil, basePer100ProteinG: nil, basePer100CarbsG: nil,
                basePer100FatG: nil, entryServingName: nil, entryServingGrams: nil,
                loggedByServings: false, planned: false)
            try entry.insertPreservingID(d)
        }
        let m = model(db)
        try await m.loadOnce()
        #expect(m.remainingCalories == 0)
    }
}
```

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement**

```swift
import Foundation
import GRDB
import Observation
import Shared

/// One row in the library list, whatever its source.
///
/// A single type rather than three: Android's food, meal and recipe rows are structurally
/// identical and differ only in the badge and whether editing is offered.
nonisolated struct LibraryItem: Identifiable, Equatable, Sendable {
    enum Kind: Equatable, Sendable {
        case food(SavedFood)
        /// Read-only. `saved_meals` has no creation path on either platform — rows arrive only
        /// from old data or a backup restore — so these are loggable but not editable.
        case meal(SavedMeal)
        case recipe(RecipeWithIngredients)
    }

    let id: String
    let kind: Kind

    var name: String {
        switch kind {
        case .food(let f): f.name
        case .meal(let m): m.name
        case .recipe(let r): r.recipe.name
        }
    }

    var calories: Int {
        switch kind {
        case .food(let f): f.calories
        case .meal(let m): m.calories
        case .recipe(let r): r.totalCalories
        }
    }

    var proteinG: Double {
        switch kind {
        case .food(let f): f.proteinG
        case .meal(let m): m.proteinG
        case .recipe(let r): r.totalProteinG
        }
    }

    var carbsG: Double {
        switch kind {
        case .food(let f): f.carbsG
        case .meal(let m): m.carbsG
        case .recipe(let r): r.totalCarbsG
        }
    }

    var fatG: Double {
        switch kind {
        case .food(let f): f.fatG
        case .meal(let m): m.fatG
        case .recipe(let r): r.totalFatG
        }
    }

    /// `nil` for a personal food — only non-personal sources are badged.
    var badge: String? {
        switch kind {
        case .food: nil
        case .meal: "Meal"
        case .recipe: "Recipe"
        }
    }

    /// Saved meals cannot be edited: there is no editor for a row type nothing can create.
    var isEditable: Bool {
        switch kind {
        case .food, .recipe: true
        case .meal: false
        }
    }

    /// Recipes get "N ingredients · " before the macros, matching Android.
    var subtitle: String {
        let macros = "\(Int(proteinG))P \(Int(carbsG))C \(Int(fatG))F"
        if case .recipe(let r) = kind {
            return "\(r.ingredients.count) ingredients · \(macros)"
        }
        return macros
    }
}

/// The Food Library screen's state.
///
/// Mirrors Android's `FoodLibraryViewModel` minus NEVO (D20) and Open Food Facts (D21) — which
/// removes the debounced network search and the thousands-of-rows catalogue filter, the two most
/// complex paths in that file.
@MainActor
@Observable
final class FoodLibraryModel {

    // MARK: - Inputs

    /// Recomputes `items` on every change — never filter in a computed property, which would
    /// re-run on every render pass.
    var query: String = "" { didSet { recompute() } }
    var category: FoodCategory = .all { didSet { recompute() } }

    // MARK: - Published state

    private(set) var items: [LibraryItem] = []
    private(set) var recipes: [RecipeWithIngredients] = []
    private(set) var recents: [SavedFood] = []
    private(set) var remainingCalories = 0
    private(set) var errorMessage: String?

    let slotId: Int64?
    let slotName: String
    let logDate: CalendarDay
    let pickerMode: Bool

    /// Adding onto a future day creates plans, not eaten entries.
    var isPlannedDate: Bool { logDate > today }

    // MARK: - Init

    private let database: AppDatabase
    private let today: CalendarDay
    private let planStore: PlanPreferencesStore?
    private let rebalanceStore: RebalanceStore?
    private var allFoods: [SavedFood] = []
    private var allMeals: [SavedMeal] = []
    private var targets = PlanTargetsSnapshot.default
    private var eaten = MacroSum()

    /// The two stores are injectable for the same reason as in `FoodLogModel` — `nil` means
    /// "open the real one", which is what the app does.
    init(database: AppDatabase, slotId: Int64?, slotName: String, logDate: CalendarDay,
         today: CalendarDay = .today, pickerMode: Bool = false,
         planStore: PlanPreferencesStore? = nil, rebalanceStore: RebalanceStore? = nil) {
        self.database = database
        self.slotId = slotId
        self.slotName = slotName
        self.logDate = logDate
        self.today = today
        self.pickerMode = pickerMode
        self.planStore = planStore
        self.rebalanceStore = rebalanceStore
    }

    // MARK: - Activation

    func activate() async {
        await loadTargets()
        // The injected `today`, not `CalendarDay.today` — tests pin it, and reading the clock
        // here would make every impact-strip test depend on the day it runs.
        let todayISO = today.iso
        do {
            for try await snapshot in database.observe({ d in
                LibrarySnapshot(
                    foods: try SavedFoodQueries.all(d: d),
                    meals: try SavedFoodQueries.allMeals(d: d),
                    recipes: try RecipeQueries.allWithIngredients(d: d),
                    recents: try SavedFoodQueries.recents(d: d),
                    todayEntries: try MealEntryQueries.between(
                        d: d, start: todayISO, end: todayISO))
            }) {
                apply(snapshot)
                errorMessage = nil
            }
        } catch {
            guard !Task.isCancelled else { return }
            errorMessage = "Couldn't load your food library."
        }
    }

    func loadOnce() async throws {
        await loadTargets()
        let todayISO = today.iso
        let snapshot = try database.reader.read { d in
            LibrarySnapshot(
                foods: try SavedFoodQueries.all(d: d),
                meals: try SavedFoodQueries.allMeals(d: d),
                recipes: try RecipeQueries.allWithIngredients(d: d),
                recents: try SavedFoodQueries.recents(d: d),
                todayEntries: try MealEntryQueries.between(d: d, start: todayISO, end: todayISO))
        }
        apply(snapshot)
    }

    // MARK: - Writes

    /// Logs, or updates when the draft carries an editing id.
    ///
    /// Nothing updates state here: the write lands in GRDB and the observation recomputes.
    func confirm(_ draft: AmountDraft) async throws {
        guard let grams = draft.resolvedGrams, let preview = draft.preview else {
            errorMessage = "Enter a valid amount (min \(Int(FoodScaling.shared.MIN_GRAMS))g)."
            throw LibraryError.invalidAmount
        }
        let food = draft.food
        let planned = isPlannedDate
        let date = logDate.iso
        let slot = slotId
        let byServings = draft.mode == .servings

        if let editingId = draft.editingEntryId {
            do {
                try await database.writer.write { d in
                    // A missing row must NOT be swallowed: silently returning would look exactly
                    // like a successful save that lost the edit.
                    guard var existing = try MealEntry.fetchOne(d, key: editingId) else {
                        throw LibraryError.entryNotFound
                    }
                    existing.name = food.name
                    existing.calories = preview.calories
                    existing.proteinG = preview.proteinG
                    existing.carbsG = preview.carbsG
                    existing.fatG = preview.fatG
                    existing.amountGrams = grams
                    existing.basePer100Calories = food.calories
                    existing.basePer100ProteinG = food.proteinG
                    existing.basePer100CarbsG = food.carbsG
                    existing.basePer100FatG = food.fatG
                    existing.entryServingName = food.householdServingName
                    existing.entryServingGrams = food.householdServingGrams
                    existing.loggedByServings = byServings
                    try existing.update(d)
                }
            } catch LibraryError.entryNotFound {
                errorMessage = "Couldn't find that entry to update."
                throw LibraryError.entryNotFound
            }
        } else {
            try await database.writer.write { d in
                var entry = MealEntry(
                    id: nil, date: date, mealType: "FOOD_LIBRARY", name: food.name,
                    calories: preview.calories, proteinG: preview.proteinG,
                    carbsG: preview.carbsG, fatG: preview.fatG, slotId: slot,
                    amountGrams: grams,
                    basePer100Calories: food.calories, basePer100ProteinG: food.proteinG,
                    basePer100CarbsG: food.carbsG, basePer100FatG: food.fatG,
                    entryServingName: food.householdServingName,
                    entryServingGrams: food.householdServingGrams,
                    loggedByServings: byServings, planned: planned)
                try entry.insertPreservingID(d)
            }
        }
    }

    func saveFood(_ food: SavedFood) async throws {
        try await database.writer.write { d in
            var copy = food
            if food.id == nil { try copy.insertPreservingID(d) } else { try copy.update(d) }
        }
    }

    /// The confirmation wording changes with the date, matching Android.
    func confirmationMessage(for name: String) -> String {
        isPlannedDate ? "Planned \(name) for \(logDate.readableShort)" : "Added \(name) to \(slotName)"
    }

    // MARK: - Impact

    /// The deterministic impact preview, or `nil` to hide the strip.
    ///
    /// Today-only and never in picker mode: on another day it would project onto today's
    /// remaining plan, and in picker mode the food is going into a recipe rather than onto a day.
    func impact(for draft: AmountDraft) -> MealImpactResult? {
        guard !pickerMode, logDate == today, let preview = draft.preview else { return nil }
        return MealImpact.shared.compute(
            eatenCalories: Int32(eaten.calories),
            eatenProtein: eaten.proteinG,
            eatenCarbs: eaten.carbsG,
            targetCalories: Int32(targets.calories),
            targetProtein: Int32(targets.proteinG),
            targetCarbs: Int32(targets.carbsG),
            addCalories: Int32(preview.calories),
            addProtein: preview.proteinG,
            addCarbs: preview.carbsG)
    }

    // MARK: - Internals

    nonisolated struct LibrarySnapshot: Sendable {
        let foods: [SavedFood]
        let meals: [SavedMeal]
        let recipes: [RecipeWithIngredients]
        let recents: [SavedFood]
        let todayEntries: [MealEntry]
    }

    enum LibraryError: Error, Equatable { case invalidAmount, entryNotFound }

    private func apply(_ snapshot: LibrarySnapshot) {
        allFoods = snapshot.foods
        allMeals = snapshot.meals
        recipes = snapshot.recipes
        recents = snapshot.recents
        eaten = MacroSum(snapshot.todayEntries)
        remainingCalories = max(0, targets.zoneLowerBound - eaten.calories)
        recompute()
    }

    /// Filtering runs here, on the main actor, and that is safe *because* NEVO is not built:
    /// Android pushes this to a background dispatcher only to filter a catalogue of thousands.
    /// What remains is a few hundred personal foods. If it ever feels slow, debounce `query`.
    private func recompute() {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()

        func matchesQuery(_ name: String) -> Bool {
            q.isEmpty || name.lowercased().contains(q)
        }

        if category == .recipes {
            items = []
            return
        }

        let foods = allFoods
            .filter { matchesQuery($0.name) && category.matches($0) }
            .map { LibraryItem(id: "food_\($0.id ?? 0)", kind: .food($0)) }

        // Saved meals have no macro breakdown to categorise, so they appear only under All.
        let meals = category == .all
            ? allMeals.filter { matchesQuery($0.name) }
                .map { LibraryItem(id: "meal_\($0.id ?? 0)", kind: .meal($0)) }
            : []

        items = foods + meals
    }

    /// Resolves **today's** effective targets — today, not `logDate`, because the only consumers
    /// are the impact strip and the remaining-calorie line, both of which are today-only.
    ///
    /// 🔴 The rebalance overlay is not optional. During a rebalance the agreed target is
    /// *reduced*, and an impact strip computed against the base number would tell the user a meal
    /// fits when it does not. `FoodLogModel.loadTargets()` does exactly this; the two must agree.
    private func loadTargets() async {
        guard let plans = planStore ?? (try? PlanPreferencesStore()),
              let rebalances = rebalanceStore ?? (try? RebalanceStore())
        else { return }   // keep the defaults rather than blocking the first paint

        let plan = await plans.value()
        let state = await rebalances.current()
        let base = PlanTargets(calories: Int32(plan.targetCalories),
                               proteinG: Int32(plan.targetProteinG),
                               carbsG: Int32(plan.targetCarbsG),
                               fatG: Int32(plan.targetFatG),
                               zoneLowerBound: Int32(plan.calorieZoneLowerBound),
                               zoneUpperBound: Int32(plan.calorieZoneUpperBound))
        targets = PlanTargetsSnapshot(EffectiveTargets.shared.resolve(
            base: base, date: today.kotlin, state: state))
    }
}
```

> **Two supporting changes this depends on.**
>
> 1. `FoodLogModel` already converts `PlanTargets` → `PlanTargetsSnapshot`, but through a
>    `private static func snapshot(_:)`. Promote it to an initialiser in
>    `Features/FoodLog/DayCalorieSummary.swift` so both models share one conversion, and replace
>    the three `Self.snapshot(…)` call sites in `FoodLogModel` with it:
>
> ```swift
> nonisolated extension PlanTargetsSnapshot {
>     /// The Kotlin resolution result, flattened into view state (D14 — Kotlin types stay out of
>     /// the models).
>     init(_ t: PlanTargets) {
>         self.init(calories: Int(t.calories), proteinG: Int(t.proteinG),
>                   carbsG: Int(t.carbsG), fatG: Int(t.fatG),
>                   zoneLowerBound: Int(t.zoneLowerBound), zoneUpperBound: Int(t.zoneUpperBound))
>     }
> }
> ```
>
> 2. `CalendarDay.readableShort` does not exist yet — add it to `DesignSystem/CalendarDay.swift`:
>
> ```swift
>     /// "Wed, Aug 5" — the form Android uses in its planned-meal confirmation.
>     var readableShort: String {
>         let weekdays = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
>         let months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun",
>                       "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
>         let parts = iso.split(separator: "-")
>         guard let month = Int(parts[1]), (1...12).contains(month),
>               let day = Int(parts[2]) else { return iso }
>         var components = DateComponents()
>         components.year = Int(parts[0]); components.month = month
>         components.day = day; components.hour = 12
>         let calendar = Calendar(identifier: .gregorian)
>         guard let date = calendar.date(from: components) else { return iso }
>         let weekday = weekdays[calendar.component(.weekday, from: date) - 1]
>         return "\(weekday), \(months[month - 1]) \(day)"
>     }
> ```
>
> Add a test for it in `CalendarDayTests.swift`:
> ```swift
>     @Test func readableShortMatchesAndroidsPlannedWording() {
>         #expect(CalendarDay("2026-08-05")!.readableShort == "Wed, Aug 5")
>     }
> ```

- [ ] **Step 4: Run, then commit**

```bash
git add -A && git commit -m "feat(library): the Food Library model, filtering and write paths"
```

---

## Task 5: The library screen — 🖼️ GATE

**Files:**
- Create: `RecompTracker/RecompTracker/Features/FoodLibrary/FoodLibraryScreen.swift`
- Create: `RecompTracker/RecompTracker/Features/FoodLibrary/LibraryRow.swift`

**Reference: `screenshots/01-food-library-all-food-rows.jpg` and `02-…recipes-tab-…jpg`.** Open
them before writing. No tests — this is rendering, verified visually (standing rule 5).

- [ ] **Step 1: `LibraryRow.swift`**

```swift
import SwiftUI

/// One library row. All three kinds share this shape — Android's three row composables differ
/// only in the badge and whether a pencil appears.
struct LibraryRow: View {
    let item: LibraryItem
    let onLog: () -> Void
    let onEdit: (() -> Void)?

    @Environment(\.appAccent) private var accent

    var body: some View {
        HStack(spacing: Spacing.sm) {
            VStack(alignment: .leading, spacing: 1) {
                HStack(spacing: Spacing.xs) {
                    Text(item.name).appType(AppType.cardTitle)
                        .foregroundStyle(AppColor.textPrimary.color)
                        .lineLimit(1)
                    if let badge = item.badge {
                        Text(badge).appType(AppType.metaLabel)
                            .foregroundStyle(accent.inkBase.opacity(0.70))
                            .padding(.horizontal, Spacing.sm)
                            .padding(.vertical, 2)
                            .overlay { Capsule().strokeBorder(accent.inkBase.opacity(0.30),
                                                              lineWidth: 1) }
                    }
                }
                Text(item.subtitle).appType(AppType.metaLabel)
                    .foregroundStyle(AppColor.textMuted.color)
            }
            Spacer(minLength: Spacing.sm)

            Text(verbatim: "\(item.calories) kcal")
                .appType(AppType.label)
                .foregroundStyle(AppColor.textDim.color)

            if let onEdit {
                Button(action: onEdit) { Image(systemName: "pencil") }
                    .buttonStyle(.glass)
                    .controlSize(.small)
                    .accessibilityLabel("Edit \(item.name)")
            }
            Button(action: onLog) { Image(systemName: "plus") }
                .accentProminentButton()
                .controlSize(.small)
                .accessibilityLabel("Add \(item.name)")
        }
        .padding(.vertical, Spacing.sm)
        .padding(.horizontal, Spacing.lg)
    }
}

#Preview("Three row kinds") {
    VStack(spacing: 0) {
        LibraryRow(item: LibraryItem(id: "1", kind: .food(
            SavedFood(id: 1, name: "Air fried fries", servingName: "100g", calories: 155,
                      proteinG: 2, carbsG: 27, fatG: 3, householdServingName: nil,
                      householdServingGrams: nil))), onLog: {}, onEdit: {})
        Divider()
        LibraryRow(item: LibraryItem(id: "2", kind: .meal(
            SavedMeal(id: 1, name: "Post-workout", mealType: "MEAL", calories: 480,
                      proteinG: 40, carbsG: 50, fatG: 8))), onLog: {}, onEdit: nil)
    }
    .background(AppColor.cardSurface.color)
    .preferredColorScheme(.dark)
}
```

- [ ] **Step 2: `FoodLibraryScreen.swift`**

Header, search, four chips, three action buttons, Recents, then the list. The header's subtitle is
the live remaining-calorie count, exactly as in the screenshot.

```swift
import SwiftUI

struct FoodLibraryScreen: View {
    @State private var model: FoodLibraryModel
    @State private var amountDraft: AmountDraft?
    @State private var foodDraft: FoodDraft?
    @State private var portionTarget: RecipeWithIngredients?
    @State private var showingQuickAdd = false

    /// Non-nil in picker mode: the ingredient goes back to Recipe Builder through this closure
    /// rather than into the database (D22).
    let onIngredientPicked: ((RecipeIngredient) -> Void)?
    let onCreateRecipe: () -> Void
    let onEditRecipe: (Int64) -> Void

    @Environment(\.dismiss) private var dismiss

    init(database: AppDatabase, slotId: Int64?, slotName: String, logDate: CalendarDay,
         onIngredientPicked: ((RecipeIngredient) -> Void)? = nil,
         onCreateRecipe: @escaping () -> Void = {},
         onEditRecipe: @escaping (Int64) -> Void = { _ in }) {
        _model = State(initialValue: FoodLibraryModel(
            database: database, slotId: slotId, slotName: slotName, logDate: logDate,
            pickerMode: onIngredientPicked != nil))
        self.onIngredientPicked = onIngredientPicked
        self.onCreateRecipe = onCreateRecipe
        self.onEditRecipe = onEditRecipe
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: Layout.screenSpacing) {
                searchField
                categoryChips
                actionButtons
                if !model.recents.isEmpty { recentsStrip }
                listBody
            }
            .padding(.vertical, Layout.screenSpacing)
        }
        .navigationTitle(model.slotId != nil ? "Add to \(model.slotName)" : "Food Log")
        .navigationBarTitleDisplayMode(.large)
        .safeAreaInset(edge: .top) {
            if model.remainingCalories > 0 {
                Text(verbatim: "\(model.remainingCalories) kcal remaining to zone")
                    .appType(AppType.screenSubtitle)
                    .foregroundStyle(AppColor.textMuted.color)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .screenGutter()
                    .padding(.bottom, Spacing.sm)
            }
        }
        .task { await model.activate() }
        // The sheet takes ONE completion and knows nothing about picker mode — the branch lives
        // here, where `onIngredientPicked` already is.
        .sheet(item: $amountDraft) { draft in
            AmountSheet(draft: draft, impact: { model.impact(for: $0) }) { confirmed in
                if let onIngredientPicked {
                    guard let ingredient = confirmed.asIngredient() else { return }
                    onIngredientPicked(ingredient)
                    dismiss()
                } else {
                    try await model.confirm(confirmed)
                }
            }
        }
        .sheet(item: $foodDraft) { draft in
            FoodEditorSheet(draft: draft) { food in try await model.saveFood(food) }
        }
        .sheet(item: $portionTarget) { recipe in
            RecipeAmountSheet(recipe: recipe, model: model)
        }
        .sheet(isPresented: $showingQuickAdd) {
            QuickAddSheet(slotName: model.slotName,
                          remainingToZone: model.remainingCalories) { entry in
                try await model.quickAdd(entry)
            }
        }
    }

    // …search field, chips, action buttons, recents strip and list body follow the screenshot.
}
```

> ⚠️ The body above is deliberately partial — **the remaining subviews must be written against
> `screenshots/01-…jpg`**, which shows the search field with its trailing divider, the chip row's
> scroll behaviour, the two-then-one action button layout, and the Recents chip strip. Do not
> invent that layout from this plan; read the screenshot.
>
> The camera button inside the search field is **Phase 4** (D21) and must not appear.
>
> `AmountDraft`, `FoodDraft` and `RecipeWithIngredients` need `Identifiable` conformances for
> `.sheet(item:)`. Add them next to their definitions, keyed on a stable id.

- [ ] **Step 3: Build, then hand off for the visual check**

Build and launch, then stop and report. This is **GATE 1** — the library list, chips, and Recents
strip get looked at before the sheets are built on top of them.

```bash
git add -A && git commit -m "feat(library): the library screen and its one row type"
```

---

## Task 6: Amount sheet — 🖼️ GATE

**Files:**
- Create: `RecompTracker/RecompTracker/Features/FoodLibrary/AmountSheet.swift`
- Create: `RecompTracker/RecompTracker/Features/FoodLibrary/MealImpactStrip.swift`

**Reference: `screenshots/03-amount-sheet-grams-mode.jpg` and `04-…servings-mode.jpg`.**

Structure, from those two: food name (`screenTitleCompact`) · a subtitle reading
`1 serving = 100 g · 155 kcal / 100 g` · the **Servings/Grams segmented toggle** · a `− [value] +`
stepper whose trailing hint reads `g` in grams mode and `servings · 100 g` in servings mode · four
macro tiles (`155 KCAL`, `2 P`, `27 C`, `3 F`) · the impact strip · a full-width
**Add to \(slotName)** button.

- [ ] **Step 1: The sheet's contract**

```swift
struct AmountSheet: View {
    /// Seeded once; the sheet owns its editing copy from then on.
    let draft: AmountDraft
    /// Injected rather than taking the model, so the sheet has no opinion about picker mode or
    /// which day is selected — it just renders whatever strip it is handed.
    let impact: (AmountDraft) -> MealImpactResult?
    let onConfirm: (AmountDraft) async throws -> Void

    @State private var editing: AmountDraft
    @State private var errorMessage: String?
    @State private var isSaving = false
    @Environment(\.dismiss) private var dismiss

    init(draft: AmountDraft,
         impact: @escaping (AmountDraft) -> MealImpactResult?,
         onConfirm: @escaping (AmountDraft) async throws -> Void) {
        self.draft = draft
        self.impact = impact
        self.onConfirm = onConfirm
        _editing = State(initialValue: draft)
    }

    // body follows screenshots 03 and 04 — see Step 2 onward.
}
```

**A failed save keeps the sheet open with what the user typed** (spec rule): `onConfirm` throwing
sets `errorMessage` and clears `isSaving`, and must not call `dismiss()`. The same rule applies to
`FoodEditorSheet`, `QuickAddSheet` and `RecipeAmountSheet`.

- [ ] **Step 2: The toggle is conditional**

Show the Servings/Grams control **only** when `draft.canUseServings`. A food with no household
serving gets the stepper alone, in grams. Getting this wrong offers a mode that cannot resolve an
amount.

- [ ] **Step 3: The impact strip**

```swift
import SwiftUI
import Shared

/// The deterministic impact preview. Hidden entirely when `result` is nil — which happens on any
/// day but today, in picker mode, or before a valid amount is entered.
struct MealImpactStrip: View {
    let result: MealImpactResult

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(headline).appType(AppType.cardSubtitle)
                .foregroundStyle(AppColor.textSecondary.color)
            Text(result.hint).appType(AppType.metaLabel)
                .foregroundStyle(isOver ? AppColor.textPrimary.color
                                        : AppColor.textMuted.color)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .tintedCard(padding: Spacing.md)
    }

    /// "Puts you at 39% protein, 31% carbs for today" — only macros with a target appear.
    private var headline: String {
        var parts: [String] = []
        if result.protein.hasTarget { parts.append("\(result.protein.percent)% protein") }
        if result.carbs.hasTarget { parts.append("\(result.carbs.percent)% carbs") }
        return "Puts you at \(parts.joined(separator: ", ")) for today"
    }

    private var isOver: Bool { result.calories.over || result.carbs.over }
}
```

- [ ] **Step 4: Build, then hand off — GATE 2**

Both modes, plus the impact strip in a normal and an over state.

```bash
git add -A && git commit -m "feat(library): amount sheet with conditional servings mode and impact strip"
```

---

## Task 7: Food editor sheet — 🖼️ GATE

**Files:** Create `RecompTracker/RecompTracker/Features/FoodLibrary/FoodEditorSheet.swift`

**Reference: `screenshots/05-new-food-sheet-empty.jpg` and `06-edit-food-sheet-populated.jpg`.**

One sheet, retitled: **"New food" / "Save food"** versus **"Edit: \(name)" / "Update food"**. Subtitle
*"Macros are per 100 g"*. Fields: Name · Serving (default `100g`) · a 2×2 grid of
Calories/Protein/Carbs/Fat · Serving name (opt.) and Serving grams. The populated screenshot shows
unit suffixes (`kcal`, `g`) inside each field.

- [ ] **Steps 1–3:** build it, verify against both screenshots, hand off — **GATE 3**.

```bash
git add -A && git commit -m "feat(library): the new/edit food sheet"
```

---

## Task 8: Quick add relocation — 🖼️ GATE

Already amended in Task 3. Here it is wired into the library's **Quick add** action button and
checked against `screenshots/07-quick-add-sheet.jpg` — name optional, macros three-across.

- [ ] Add `FoodLibraryModel.quickAdd(_:)`, writing an entry with **no** per-100g base (it is not a
  library food) and `planned: isPlannedDate`:

```swift
    /// Logs calories without creating a food. Carries no per-100g base by design, which is why
    /// such an entry never appears in Recents and cannot be reopened in the amount sheet.
    func quickAdd(_ entry: QuickAddDraft.Validated) async throws {
        let date = logDate.iso
        let slot = slotId
        let planned = isPlannedDate
        try await database.writer.write { d in
            var row = MealEntry(
                id: nil, date: date, mealType: "MEAL", name: entry.name,
                calories: entry.calories, proteinG: entry.proteinG, carbsG: entry.carbsG,
                fatG: entry.fatG, slotId: slot, amountGrams: nil,
                basePer100Calories: nil, basePer100ProteinG: nil, basePer100CarbsG: nil,
                basePer100FatG: nil, entryServingName: nil, entryServingGrams: nil,
                loggedByServings: false, planned: planned)
            try row.insertPreservingID(d)
        }
    }
```

- [ ] Build, hand off — **GATE 4**.

```bash
git add -A && git commit -m "feat(library): wire quick add into the library"
```

---

# PART C — Recipes

## Task 9: `RecipeBuilderModel` and screen — 🖼️ GATE

**Files:**
- Create: `RecompTracker/RecompTracker/Features/RecipeBuilder/RecipeBuilderModel.swift`
- Create: `RecompTracker/RecompTracker/Features/RecipeBuilder/RecipeBuilderScreen.swift`
- Create: `RecompTracker/RecompTrackerTests/RecipeBuilderModelTests.swift`

**Reference: `screenshots/08-recipe-builder-new-empty.jpg` and `09-…edit-with-ingredients.jpg`.**

From those: title **"New recipe"** / **"Edit recipe"**, a red trash top-right only when editing ·
a "Recipe name" field · ingredient rows showing `50g · 138 kcal  P7g  C2g  F11g` with a red ✕ each ·
an empty state *"No ingredients yet. Tap below to add some."* · **+ Add ingredient** · a full-width
**Save Recipe** / **Update Recipe**.

**The ✨ button in the name field is the AI namer and is Phase 5 — do not build it** (D-note in the
spec). Leave the field plain.

**`isDirty` is not ported.** Android sets it in five places and never reads it; there is no discard
confirmation, and Recipe Builder is a pushed screen on both platforms, so the interactive pop is the
equivalent of Android's back button.

- [ ] **Step 1: Write the failing tests**

```swift
import Foundation
import GRDB
import Testing
@testable import RecompTracker

@Suite struct RecipeBuilderModelTests {

    private func ingredient(_ name: String, cal: Int = 100, grams: Double? = 100)
        -> RecipeIngredient {
        RecipeIngredient(id: nil, recipeId: 0, name: name, sortOrder: 0, calories: cal,
                         proteinG: 10, carbsG: 5, fatG: 2, amountGrams: grams,
                         basePer100Calories: cal, basePer100ProteinG: 10,
                         basePer100CarbsG: 5, basePer100FatG: 2, entryServingName: nil,
                         entryServingGrams: nil, loggedByServings: false)
    }

    @Test func aNewBuilderStartsEmpty() {
        let db = try! AppDatabase.inMemoryForTesting()
        let m = RecipeBuilderModel(database: db)
        #expect(m.name.isEmpty)
        #expect(m.ingredients.isEmpty)
        #expect(!m.canSave, "a recipe needs a name and at least one ingredient")
    }

    @Test func savingRequiresANameAndAtLeastOneIngredient() {
        let db = try! AppDatabase.inMemoryForTesting()
        let m = RecipeBuilderModel(database: db)
        m.name = "Anabolic oats"
        #expect(!m.canSave, "name alone is not enough")
        m.add(ingredient("Oats"))
        #expect(m.canSave)
        m.name = "   "
        #expect(!m.canSave, "a whitespace name is blank")
    }

    @Test func savingWritesTheRecipeAndItsIngredients() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        let m = RecipeBuilderModel(database: db)
        m.name = "Anabolic oats"
        m.add(ingredient("Oats", cal: 350))
        m.add(ingredient("Whey", cal: 120))
        try await m.save()

        let recipes = try db.reader.read { try RecipeQueries.allWithIngredients(d: $0) }
        #expect(recipes.count == 1)
        #expect(recipes[0].recipe.name == "Anabolic oats")
        #expect(recipes[0].ingredients.count == 2)
        #expect(recipes[0].totalCalories == 470)
    }

    /// Editing replaces the ingredient list wholesale rather than diffing — the incoming list
    /// IS the new list, so `Transactions.replaceIngredients` is the right primitive.
    @Test func editingAnExistingRecipeReplacesItsIngredients() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        let first = RecipeBuilderModel(database: db)
        first.name = "Oats"
        first.add(ingredient("Oats"))
        first.add(ingredient("Whey"))
        try await first.save()

        let saved = try db.reader.read { try RecipeQueries.allWithIngredients(d: $0) }[0]
        let second = RecipeBuilderModel(database: db, editing: saved)
        #expect(second.ingredients.count == 2)
        second.removeIngredient(at: 1)
        try await second.save()

        let after = try db.reader.read { try RecipeQueries.allWithIngredients(d: $0) }
        #expect(after.count == 1, "updated, not duplicated")
        #expect(after[0].ingredients.count == 1)
        #expect(after[0].ingredients[0].name == "Oats")
    }

    /// The second entry point: seeded from a Food Log slot selection.
    @Test func aSeededBuilderStartsWithItsIngredients() {
        let db = try! AppDatabase.inMemoryForTesting()
        let m = RecipeBuilderModel(database: db, seed: [ingredient("Oats"), ingredient("Whey")])
        #expect(m.ingredients.count == 2)
        #expect(m.name.isEmpty, "a seeded recipe still needs a name")
    }

    @Test func deletingRemovesTheRecipeAndCascadesItsIngredients() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        let m = RecipeBuilderModel(database: db)
        m.name = "Doomed"
        m.add(ingredient("Oats"))
        try await m.save()

        let saved = try db.reader.read { try RecipeQueries.allWithIngredients(d: $0) }[0]
        let editor = RecipeBuilderModel(database: db, editing: saved)
        try await editor.delete()

        #expect(try db.reader.read { try Recipe.fetchCount($0) } == 0)
        #expect(try db.reader.read { try RecipeIngredient.fetchCount($0) } == 0,
                "ON DELETE CASCADE")
    }

    @Test func sortOrderIsDensifiedFromZeroOnSave() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        let m = RecipeBuilderModel(database: db)
        m.name = "Ordered"
        m.add(ingredient("A"))
        m.add(ingredient("B"))
        m.add(ingredient("C"))
        m.removeIngredient(at: 1)
        try await m.save()

        let saved = try db.reader.read { try RecipeQueries.allWithIngredients(d: $0) }[0]
        #expect(saved.ingredients.map(\.sortOrder) == [0, 1])
    }
}
```

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement the model**

```swift
import Foundation
import GRDB
import Observation

/// Recipe Builder's state. Two entry points: empty, or seeded from a Food Log slot selection.
@MainActor
@Observable
final class RecipeBuilderModel {

    var name: String = ""
    private(set) var ingredients: [RecipeIngredient] = []
    private(set) var errorMessage: String?

    let editingRecipeId: Int64?

    private let database: AppDatabase

    init(database: AppDatabase, seed: [RecipeIngredient] = []) {
        self.database = database
        self.editingRecipeId = nil
        self.ingredients = seed
    }

    init(database: AppDatabase, editing recipe: RecipeWithIngredients) {
        self.database = database
        self.editingRecipeId = recipe.recipe.id
        self.name = recipe.recipe.name
        self.ingredients = recipe.ingredients
    }

    /// A recipe needs a name and at least one ingredient — Android rejects both cases, and an
    /// ingredient-less recipe would log as zero calories.
    var canSave: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !ingredients.isEmpty
    }

    var totalCalories: Int { ingredients.reduce(0) { $0 + $1.calories } }

    func add(_ ingredient: RecipeIngredient) { ingredients.append(ingredient) }

    func removeIngredient(at index: Int) {
        guard ingredients.indices.contains(index) else { return }
        ingredients.remove(at: index)
    }

    func replaceIngredient(at index: Int, with updated: RecipeIngredient) {
        guard ingredients.indices.contains(index) else { return }
        ingredients[index] = updated
    }

    func save() async throws {
        guard canSave else { return }
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        // Densified from 0 so a removal in the middle does not leave a gap.
        let ordered = ingredients.enumerated().map { index, ingredient -> RecipeIngredient in
            var copy = ingredient
            copy.sortOrder = index
            return copy
        }
        let existingId = editingRecipeId

        try await database.writer.write { d in
            let recipeId: Int64
            if let existingId {
                var recipe = Recipe(id: existingId, name: trimmed)
                try recipe.update(d)
                recipeId = existingId
            } else {
                var recipe = Recipe(id: nil, name: trimmed)
                try recipe.insertPreservingID(d)
                recipeId = recipe.id!
            }
            // Whole-list replace, not a diff: the incoming list IS the new list.
            try Transactions.replaceIngredients(d: d, recipeId: recipeId, ingredients: ordered)
        }
    }

    func delete() async throws {
        guard let id = editingRecipeId else { return }
        try await database.writer.write { d in
            try RecipeQueries.delete(d: d, id: id)
        }
    }
}
```

- [ ] **Step 4: Build the screen against both screenshots, then hand off — GATE 5**

```bash
git add -A && git commit -m "feat(recipes): the recipe builder model and screen"
```

---

## Task 10: Recipe portion sheet

**Files:** Create `RecompTracker/RecompTracker/Features/FoodLibrary/RecipeAmountSheet.swift`

**No screenshot exists.** Structure from `FoodLibraryScreen.kt:943-988`: the recipe name in
`screenTitleCompact` · a subtitle line · an `AmountStepper` over **portions** · the shared message
line · a full-width **Add to \(slotName)** button (or plain **Add** when there is no slot).

**There is no Servings/Grams toggle** — a recipe scales by whole-recipe multiples only, `1` meaning
the whole thing.

- [ ] **Step 1: Add the write path to `FoodLibraryModel`**

```swift
    /// Logs a multiple of a whole recipe. Flattened to one entry, as on Android — the ingredient
    /// breakdown lives on the recipe, not on the log.
    func logRecipe(_ recipe: RecipeWithIngredients, portions: PortionDraft) async throws {
        guard let scaled = portions.scale(calories: recipe.totalCalories,
                                          protein: recipe.totalProteinG,
                                          carbs: recipe.totalCarbsG,
                                          fat: recipe.totalFatG) else {
            errorMessage = "Enter a valid number of portions."
            throw LibraryError.invalidAmount
        }
        let date = logDate.iso
        let slot = slotId
        let planned = isPlannedDate
        let recipeName = recipe.recipe.name
        try await database.writer.write { d in
            var entry = MealEntry(
                id: nil, date: date, mealType: "FOOD_LIBRARY", name: recipeName,
                calories: scaled.calories, proteinG: scaled.proteinG, carbsG: scaled.carbsG,
                fatG: scaled.fatG, slotId: slot, amountGrams: nil,
                basePer100Calories: nil, basePer100ProteinG: nil, basePer100CarbsG: nil,
                basePer100FatG: nil, entryServingName: nil, entryServingGrams: nil,
                loggedByServings: false, planned: planned)
            try entry.insertPreservingID(d)
        }
    }
```

- [ ] **Step 2: Add its test to `FoodLibraryModelTests`**

```swift
    @Test func loggingHalfARecipeHalvesItsMacros() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            var recipe = Recipe(id: nil, name: "Big bowl")
            try recipe.insertPreservingID(d)
            try Transactions.replaceIngredients(d: d, recipeId: recipe.id!, ingredients: [
                RecipeIngredient(id: nil, recipeId: 0, name: "Oats", sortOrder: 0, calories: 800,
                                 proteinG: 60, carbsG: 80, fatG: 20, amountGrams: 200,
                                 basePer100Calories: 400, basePer100ProteinG: 30,
                                 basePer100CarbsG: 40, basePer100FatG: 10,
                                 entryServingName: nil, entryServingGrams: nil,
                                 loggedByServings: false),
            ])
        }
        let m = model(db)
        try await m.loadOnce()

        var portions = PortionDraft()
        portions.portionsText = "0.5"
        try await m.logRecipe(m.recipes[0], portions: portions)

        let stored = try #require(try db.reader.read { try MealEntry.fetchOne($0) })
        #expect(stored.calories == 400)
        #expect(stored.proteinG == 30)
        #expect(stored.name == "Big bowl")
        #expect(stored.basePer100Calories == nil, "a recipe entry has no per-100g base")
    }
```

- [ ] **Step 3: Build the sheet, run, commit**

```bash
git add -A && git commit -m "feat(recipes): portion sheet for logging a multiple of a recipe"
```

---

## Task 11: Ingredient editor sheet and the picker flow

**Files:**
- Create: `RecompTracker/RecompTracker/Features/RecipeBuilder/IngredientEditorSheet.swift`
- Create: `RecompTracker/RecompTrackerTests/IngredientDraftTests.swift`

**No screenshot exists.** Structure from `RecipeBuilderScreen.kt:312-374`: the ingredient name in
bold `cardTitle`, then **one of two bodies**, then a full-width **Save**.

🔴 **The two bodies are the point.** `IngredientEditorState.scalable` decides:
- **scalable** (the ingredient carries per-100g bases) → an `AmountStepper`, and macros recompute
- **not scalable** → four editable fields — *Calories (kcal)*, *Protein (g)*, *Carbs (g)*, *Fat (g)*

- [ ] **Step 1: Write the failing tests**

```swift
import Testing
@testable import RecompTracker

@Suite struct IngredientDraftTests {

    private let scalable = RecipeIngredient(
        id: nil, recipeId: 0, name: "Oats", sortOrder: 0, calories: 350, proteinG: 12,
        carbsG: 60, fatG: 6, amountGrams: 100, basePer100Calories: 350,
        basePer100ProteinG: 12, basePer100CarbsG: 60, basePer100FatG: 6,
        entryServingName: nil, entryServingGrams: nil, loggedByServings: false)

    private let unscalable = RecipeIngredient(
        id: nil, recipeId: 0, name: "Grandma's sauce", sortOrder: 0, calories: 200,
        proteinG: 4, carbsG: 18, fatG: 12, amountGrams: nil, basePer100Calories: nil,
        basePer100ProteinG: nil, basePer100CarbsG: nil, basePer100FatG: nil,
        entryServingName: nil, entryServingGrams: nil, loggedByServings: false)

    /// An ingredient is scalable only if it has a per-100g base to scale FROM.
    @Test func scalabilityIsDecidedByThePresenceOfAPer100Base() {
        #expect(IngredientDraft(scalable).isScalable)
        #expect(!IngredientDraft(unscalable).isScalable)
    }

    @Test func aScalableIngredientRecomputesMacrosFromGrams() {
        var draft = IngredientDraft(scalable)
        draft.gramsText = "200"
        let result = draft.validated()
        #expect(result?.calories == 700)
        #expect(result?.proteinG == 24)
        #expect(result?.amountGrams == 200)
    }

    /// An unscalable ingredient keeps whatever macros the user types — there is nothing to scale.
    @Test func anUnscalableIngredientTakesItsMacrosVerbatim() {
        var draft = IngredientDraft(unscalable)
        draft.caloriesText = "250"
        draft.proteinText = "5"
        let result = draft.validated()
        #expect(result?.calories == 250)
        #expect(result?.proteinG == 5)
        #expect(result?.amountGrams == nil)
        #expect(result?.basePer100Calories == nil, "it stays unscalable")
    }

    @Test func rejectsInvalidInputInEitherMode() {
        var scalableDraft = IngredientDraft(scalable)
        scalableDraft.gramsText = "0"
        #expect(scalableDraft.validated() == nil)

        var unscalableDraft = IngredientDraft(unscalable)
        unscalableDraft.caloriesText = "abc"
        #expect(unscalableDraft.validated() == nil)
    }

    @Test func preservesTheNameAndSortOrder() {
        var draft = IngredientDraft(scalable)
        draft.gramsText = "150"
        #expect(draft.validated()?.name == "Oats")
        #expect(draft.validated()?.sortOrder == 0)
    }
}
```

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement `IngredientDraft`** (in `IngredientEditorSheet.swift`)

```swift
import SwiftUI
import Shared

/// The ingredient editor's input state.
///
/// Two modes, keyed on whether the ingredient carries a per-100g base. With one, grams drive the
/// macros; without one, the macros are typed directly, because there is nothing to scale from.
nonisolated struct IngredientDraft: Equatable, Sendable {
    let original: RecipeIngredient
    var gramsText: String
    var caloriesText: String
    var proteinText: String
    var carbsText: String
    var fatText: String

    init(_ ingredient: RecipeIngredient) {
        self.original = ingredient
        self.gramsText = ingredient.amountGrams.map { String(Int($0)) } ?? "100"
        self.caloriesText = String(ingredient.calories)
        self.proteinText = AmountDraft.trimmed(ingredient.proteinG)
        self.carbsText = AmountDraft.trimmed(ingredient.carbsG)
        self.fatText = AmountDraft.trimmed(ingredient.fatG)
    }

    var isScalable: Bool { original.basePer100Calories != nil }

    func validated() -> RecipeIngredient? {
        var result = original
        if isScalable {
            guard let grams = AmountDraft.number(gramsText),
                  grams >= FoodScaling.shared.MIN_GRAMS else { return nil }
            let base = FoodMacros(
                calories: Int32(original.basePer100Calories ?? 0),
                proteinG: original.basePer100ProteinG ?? 0,
                carbsG: original.basePer100CarbsG ?? 0,
                fatG: original.basePer100FatG ?? 0)
            let scaled = FoodScaling.shared.scale(basePer100: base, grams: grams)
            result.amountGrams = grams
            result.calories = Int(scaled.calories)
            result.proteinG = scaled.proteinG
            result.carbsG = scaled.carbsG
            result.fatG = scaled.fatG
        } else {
            guard let cal = Int(caloriesText.trimmingCharacters(in: .whitespaces)), cal >= 0,
                  let p = AmountDraft.number(proteinText), p >= 0,
                  let c = AmountDraft.number(carbsText), c >= 0,
                  let f = AmountDraft.number(fatText), f >= 0 else { return nil }
            result.calories = cal
            result.proteinG = p
            result.carbsG = c
            result.fatG = f
        }
        return result
    }
}
```

- [ ] **Step 4: Build the sheet with its two bodies, and wire the picker**

`+ Add ingredient` presents `FoodLibraryScreen` as a **sheet** with `onIngredientPicked` (D22). The
closure carries the value directly — Android's Base64/JSON encoding exists only because nav
arguments must be strings.

Add to `AmountDraft`:

```swift
    /// The picked food as a recipe ingredient, at the resolved amount.
    func asIngredient(sortOrder: Int = 0) -> RecipeIngredient? {
        guard let grams = resolvedGrams, let preview = preview else { return nil }
        return RecipeIngredient(
            id: nil, recipeId: 0, name: food.name, sortOrder: sortOrder,
            calories: preview.calories, proteinG: preview.proteinG, carbsG: preview.carbsG,
            fatG: preview.fatG, amountGrams: grams,
            basePer100Calories: food.calories, basePer100ProteinG: food.proteinG,
            basePer100CarbsG: food.carbsG, basePer100FatG: food.fatG,
            entryServingName: food.householdServingName,
            entryServingGrams: food.householdServingGrams,
            loggedByServings: mode == .servings)
    }
```

- [ ] **Step 5: Run, commit**

```bash
git add -A && git commit -m "feat(recipes): two-mode ingredient editor and the sheet-based picker"
```

---

# PART D — Food Log changes

## Task 12: Rewire `+ Add`, and fix the frozen `today`

**Files:**
- Modify: `RecompTracker/RecompTracker/Features/FoodLog/FoodLogScreen.swift`
- Modify: `RecompTracker/RecompTracker/Features/FoodLog/FoodLogModel.swift`
- Modify: `RecompTracker/RecompTrackerTests/FoodLogModelTests.swift`

- [ ] **Step 1: `+ Add` opens the library**

Replace the `QuickAddSheet` presentation with a `.navigationDestination` (or sheet) presenting
`FoodLibraryScreen(database:slotId:slotName:logDate:)`. Quick add is now reached from *inside* the
library, matching Android.

- [ ] **Step 2: Write the failing test for the midnight rollover**

```swift
    /// 🔴 `today` was frozen at init in Phase 2. 3a makes that materially worse: planned entries
    /// are keyed on FUTURE dates, so a stale today mis-classifies a plan as eaten.
    @Test func advancingTodayReclassifiesFutureAndPastDays() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        let model = FoodLogModel(database: db, today: CalendarDay("2026-08-03")!)

        model.selectDate(CalendarDay("2026-08-04")!)
        #expect(model.isFuture)
        #expect(!model.isToday)

        model.advanceToday(CalendarDay("2026-08-04")!)
        #expect(model.isToday, "the selected day is now today")
        #expect(!model.isFuture)
    }
```

- [ ] **Step 3: Implement**

```swift
    /// Advances the model's notion of "today".
    ///
    /// Android does this through `dateProvider.todayFlow()`. Without it, a tab left open across
    /// midnight keeps a stale today — which breaks `isToday`, the week strip's right-hand end, and
    /// the ±30-day clamp, and (since 3a) mis-classifies a planned entry as eaten.
    func advanceToday(_ newToday: CalendarDay) {
        guard newToday != today else { return }
        today = newToday
    }
```

Make `today` a `private(set) var` rather than a `let`, and drive it from the screen:

```swift
        .task {
            // Re-check at every foreground and on a timer; a calendar day is a long interval,
            // so a coarse check is enough and costs nothing.
            while !Task.isCancelled {
                model.advanceToday(.today)
                try? await Task.sleep(for: .seconds(60))
            }
        }
```

- [ ] **Step 4: Run, commit**

```bash
git add -A && git commit -m "feat(foodlog): + Add opens the library; today advances across midnight"
```

---

## Task 13: Reconcile banner — 🖼️ GATE

**Files:**
- Create: `RecompTracker/RecompTracker/Features/FoodLog/ReconcileBanner.swift`
- Modify: `FoodLogModel.swift`, `FoodLogScreen.swift`
- Modify: `RecompTracker/RecompTrackerTests/FoodLogModelTests.swift`

**No screenshot** — it is conditional and none of the nine captured a day with planned entries.
Build it from `FoodScreen.kt:458-496` and the established card language, then it goes to the visual
check like everything else.

Confirm-all and confirm-one only. Postpone and the stale-plan nudge stay deferred.

- [ ] **Step 1: Write the failing tests**

```swift
    @Test func plannedEntriesAreCountedSeparatelyFromEatenOnes() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedSlots(db)
        try seed(db, date: "2026-08-02", slotId: 1, calories: 400)
        try db.writer.write { d in
            var planned = MealEntry(
                id: nil, date: "2026-08-02", mealType: "FOOD_LIBRARY", name: "Planned dinner",
                calories: 700, proteinG: 40, carbsG: 60, fatG: 20, slotId: 2,
                amountGrams: nil, basePer100Calories: nil, basePer100ProteinG: nil,
                basePer100CarbsG: nil, basePer100FatG: nil, entryServingName: nil,
                entryServingGrams: nil, loggedByServings: false, planned: true)
            try planned.insertPreservingID(d)
        }

        let model = FoodLogModel(database: db, today: CalendarDay("2026-08-02")!)
        try await model.loadOnce()

        #expect(model.totals.calories == 400, "planned entries are excluded from eaten totals")
        #expect(model.plannedCount == 1)
        #expect(model.plannedTotals.calories == 700)
    }

    @Test func confirmingAllPlannedMovesThemIntoTheEatenTotal() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedSlots(db)
        try db.writer.write { d in
            for name in ["A", "B"] {
                var planned = MealEntry(
                    id: nil, date: "2026-08-02", mealType: "FOOD_LIBRARY", name: name,
                    calories: 300, proteinG: 10, carbsG: 20, fatG: 5, slotId: 1,
                    amountGrams: nil, basePer100Calories: nil, basePer100ProteinG: nil,
                    basePer100CarbsG: nil, basePer100FatG: nil, entryServingName: nil,
                    entryServingGrams: nil, loggedByServings: false, planned: true)
                try planned.insertPreservingID(d)
            }
        }
        let model = FoodLogModel(database: db, today: CalendarDay("2026-08-02")!)
        try await model.loadOnce()
        #expect(model.totals.calories == 0)

        try await model.confirmAllPlanned()
        try await model.loadOnce()

        #expect(model.totals.calories == 600)
        #expect(model.plannedCount == 0)
    }

    @Test func confirmingOnePlannedEntryLeavesTheOthers() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedSlots(db)
        var firstId: Int64 = 0
        try db.writer.write { d in
            for name in ["A", "B"] {
                var planned = MealEntry(
                    id: nil, date: "2026-08-02", mealType: "FOOD_LIBRARY", name: name,
                    calories: 300, proteinG: 10, carbsG: 20, fatG: 5, slotId: 1,
                    amountGrams: nil, basePer100Calories: nil, basePer100ProteinG: nil,
                    basePer100CarbsG: nil, basePer100FatG: nil, entryServingName: nil,
                    entryServingGrams: nil, loggedByServings: false, planned: true)
                try planned.insertPreservingID(d)
                if name == "A" { firstId = planned.id! }
            }
        }
        let model = FoodLogModel(database: db, today: CalendarDay("2026-08-02")!)
        try await model.loadOnce()

        try await model.confirmPlanned(entryId: firstId)
        try await model.loadOnce()

        #expect(model.totals.calories == 300)
        #expect(model.plannedCount == 1)
    }
```

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement the model additions**

```swift
    private(set) var plannedCount = 0
    private(set) var plannedTotals = MacroSum()

    /// Marks every planned entry on the selected day as eaten. The observation recomputes the
    /// totals — nothing is updated by hand here.
    func confirmAllPlanned() async throws {
        let date = selectedDate.iso
        try await database.writer.write { d in
            try MealEntryQueries.confirmAllPlanned(d: d, date: date)
        }
    }

    func confirmPlanned(entryId: Int64) async throws {
        try await database.writer.write { d in
            try MealEntryQueries.confirmPlanned(d: d, entryId: entryId)
        }
    }
```

`confirmAllPlanned(d:date:)` **already exists** in `MealEntryQueries` from Phase 1a — use it rather
than writing the SQL again. Its single-entry sibling does not; add it directly beneath:

```swift
    /// Confirms one planned entry. The per-entry counterpart to `confirmAllPlanned`, for the
    /// banner's per-row checkmark.
    static func confirmPlanned(d: Database, entryId: Int64) throws {
        try d.execute(sql: "UPDATE meal_entries SET planned = 0 WHERE id = ?",
                      arguments: [entryId])
    }
```

In `apply(entries:slots:)`, add:

```swift
        let planned = entries.filter(\.planned)
        self.plannedCount = planned.count
        // MacroSum skips planned entries by design, so sum these directly.
        self.plannedTotals = MacroSum(planned.map { var c = $0; c.planned = false; return c })
```

- [ ] **Step 4: Build the banner, hand off — GATE 6**

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(foodlog): reconcile banner for planned entries"
```

---

## Task 14: Slot selection seeds a recipe — 🖼️ GATE

**Files:** Modify `SlotCard.swift`, `FoodLogScreen.swift`, `FoodLogModel.swift`

The second entry point into Recipe Builder. A slot enters selection mode, entries get checkboxes,
and confirming opens Recipe Builder pre-filled.

- [ ] **Step 1: Add the conversion to `FoodLogModel`**

```swift
    nonisolated enum SelectionError: Error, Equatable { case emptySlot }

    /// Converts selected slot entries into recipe ingredients.
    ///
    /// The per-100g base carries over when the entry has one, which is what makes the ingredient
    /// scalable in the recipe editor. Entries without one become fixed-macro ingredients.
    ///
    /// Throws on an empty selection rather than opening an empty builder — Android's message is
    /// *"No foods in slot to save."* and the caller shows it verbatim.
    func ingredients(for entryIds: Set<Int64>) throws -> [RecipeIngredient] {
        let selected = slots.flatMap(\.entries).filter { entryIds.contains($0.id ?? -1) }
        guard !selected.isEmpty else {
            errorMessage = "No foods in slot to save."
            throw SelectionError.emptySlot
        }
        return selected.enumerated().map { index, entry in
            RecipeIngredient(
                id: nil, recipeId: 0, name: entry.name, sortOrder: index,
                calories: entry.calories, proteinG: entry.proteinG, carbsG: entry.carbsG,
                fatG: entry.fatG, amountGrams: entry.amountGrams,
                basePer100Calories: entry.basePer100Calories,
                basePer100ProteinG: entry.basePer100ProteinG,
                basePer100CarbsG: entry.basePer100CarbsG,
                basePer100FatG: entry.basePer100FatG,
                entryServingName: entry.entryServingName,
                entryServingGrams: entry.entryServingGrams,
                loggedByServings: entry.loggedByServings)
        }
    }
```

- [ ] **Step 2: Add its test**

```swift
    @Test func selectedEntriesConvertToIngredientsPreservingTheirBase() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedSlots(db)
        try db.writer.write { d in
            var entry = MealEntry(
                id: nil, date: "2026-08-02", mealType: "FOOD_LIBRARY", name: "Oats",
                calories: 350, proteinG: 12, carbsG: 60, fatG: 6, slotId: 1, amountGrams: 100,
                basePer100Calories: 350, basePer100ProteinG: 12, basePer100CarbsG: 60,
                basePer100FatG: 6, entryServingName: "bowl", entryServingGrams: 80,
                loggedByServings: true, planned: false)
            try entry.insertPreservingID(d)
        }
        let model = FoodLogModel(database: db, today: CalendarDay("2026-08-02")!)
        try await model.loadOnce()

        let id = try #require(model.slots[0].entries.first?.id)
        let ingredients = try model.ingredients(for: [id])

        #expect(ingredients.count == 1)
        #expect(ingredients[0].name == "Oats")
        #expect(ingredients[0].basePer100Calories == 350, "stays scalable in the recipe editor")
        #expect(ingredients[0].sortOrder == 0)
    }

    /// An empty selection must not open an empty builder.
    @Test func anEmptySelectionThrowsAndSetsAndroidsMessage() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedSlots(db)
        let model = FoodLogModel(database: db, today: CalendarDay("2026-08-02")!)
        try await model.loadOnce()

        #expect(throws: FoodLogModel.SelectionError.emptySlot) {
            try model.ingredients(for: [])
        }
        #expect(model.errorMessage == "No foods in slot to save.")
    }
```

- [ ] **Step 3: Build selection mode, hand off — GATE 7**

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat(foodlog): slot selection seeds the recipe builder"
```

---

# PART E — Verification

## Task 15: Full verification and docs

- [ ] **Step 1: Both configurations**

```bash
cd ~/Desktop/RecompTracker-IOS
xcodebuild test -project RecompTracker/RecompTracker.xcodeproj -scheme RecompTracker \
  -destination 'platform=iOS Simulator,name=iPhone 17'
xcodebuild -project RecompTracker/RecompTracker.xcodeproj -scheme RecompTracker \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17' \
  -configuration Release build
```

Expected: TEST SUCCEEDED, BUILD SUCCEEDED, **zero warnings in first-party code**.

- [ ] **Step 2: Design-system leak checks**

```bash
cd ~/Desktop/RecompTracker-IOS/RecompTracker/RecompTracker
grep -rn "\.font(\.system(size:" Features Shell | grep -v DesignSystem
grep -rn "Color(\.sRGB\|Color(red:" Features Shell
```
Expected: both silent.

- [ ] **Step 3: Confirm NEVO stayed out**

```bash
grep -rn "CatalogFood\|catalog_foods" Features Shell
```
Expected: silent. The record and table exist for backup round-tripping (D20); **no feature code may
read them.** A hit means NEVO crept back in.

- [ ] **Step 4: Record D20–D23** in `docs/ios-port/decisions.md`, and tick the
  *"Replacement for the 4 `savedStateHandle` reverse-result flows"* convention — noting the
  correction that **three** are Train-only, not two.

- [ ] **Step 5: Update the port docs**

`parity-ledger.md` — Food Library ✅, Recipe Builder ✅, Food Log from 🔨 toward ✅, and the
Amount/CreateFood/QuickAdd sheet row.
`STATUS.md` — phase board, a session-log entry, and the *Needs visual check* list.

- [ ] **Step 6: Hand off for the visual check**

The library list at real length · the amount sheet in both modes · the impact strip's normal and
over states · the recipe editor's two bodies · the reconcile banner · slot selection mode.

- [ ] **Step 7: Commit both repos**

---

## What Phase 3a deliberately does NOT do

- **No NEVO** (D20) — no tab, no catalogue query, no CSV importer.
- **No Open Food Facts and no camera button** (D21) — Phase 4, with the scanner.
- **No ✨ recipe namer** — Phase 5, gated on `aiAvailable`.
- **No postpone and no stale-plan nudge** — the reconcile banner is confirm-only.
- **No `JSONStore` change stream** — plan targets are still read once. **3c must add it before
  shipping Plan or Settings.**
- **No 3b or 3c screens.**

## Rollback

Additive in the iOS repo on its own branch, except for three modified Food Log files and two new
query files. Nothing in `Persistence/Records` or `Persistence/Schema` changes. The Android repo
receives documentation only.
