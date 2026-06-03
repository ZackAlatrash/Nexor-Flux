# Button Glass Consistency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace 4 non-glass button patterns with correct `LiquidActionButton` / `LiquidSecondaryButton` equivalents so every action button in the app uses the Liquid Glass design system.

**Architecture:** All changes are pure in-place composable swaps inside `AppNavGraph`. Every replaced component reads `LocalBackdrop.current` = `contentBackdrop` (the gradient-only layer) — no circular GraphicsLayer read, no new backdrop wiring, no crash risk. See `docs/` for the Liquid Glass architecture doc before touching any glass component.

**Tech Stack:** Kotlin, Jetpack Compose, `io.github.kyant0:backdrop:2.0.0`, `LiquidActionButton` / `LiquidSecondaryButton` from `ui/liquidglass/LiquidComponents.kt`.

---

## File Map

| File | Change |
|---|---|
| `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt` | 2 button replacements + import cleanup |
| `app/src/main/java/com/zack/recomptracker/ui/foods/FoodsScreen.kt` | 4 button replacements + import cleanup |

---

## Note on Testing

These changes are pure composable swaps — the `onClick` lambdas and all surrounding logic are untouched. There is no new business logic to unit-test. Each task ends with a build verification step rather than a test run.

---

## Task 1: FoodScreen — Replace "＋ Add" slot button

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt:459-467`

The slot card header currently uses a solid `Violet500` paint box. Replace it with a compact tinted glass pill.

- [ ] **Step 1: Open `FoodScreen.kt` and locate `LockedSlotCard`**

  Find the `Box` at lines 459–467 inside the `LockedSlotCard` composable's header `Row`:

  ```kotlin
  Box(
      modifier = Modifier
          .clip(RoundedCornerShape(CornerSmall))
          .background(Violet500)
          .clickable(onClick = onAddClick)
          .padding(horizontal = 12.dp, vertical = 5.dp),
  ) {
      Text("＋ Add", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
  }
  ```

- [ ] **Step 2: Replace the `Box` with `LiquidActionButton`**

  Delete lines 459–467 and write:

  ```kotlin
  LiquidActionButton(
      text = "＋ Add",
      onClick = onAddClick,
      isPrimary = true,
  )
  ```

- [ ] **Step 3: Add the import**

  In the import block at the top of `FoodScreen.kt`, add:

  ```kotlin
  import com.zack.recomptracker.ui.liquidglass.LiquidActionButton
  ```

- [ ] **Step 4: Remove the now-unused `CornerSmall` import**

  Delete this line from the import block (verified unused — was only referenced in the `clip` call just removed):

  ```kotlin
  import com.zack.recomptracker.ui.theme.CornerSmall
  ```

  > `Violet500` stays — it is still used at line 406 in a gradient. `border` stays — used in 4 other places. `TextButton` stays — used in `AlertDialog` buttons throughout the file.

- [ ] **Step 5: Build the project**

  ```bash
  cd "/Users/zackalatrash/Desktop/Personal Dietitian"
  ./gradlew :app:compileDebugKotlin
  ```

  Expected: `BUILD SUCCESSFUL`. If there are unused-import warnings for other symbols, ignore them — they are pre-existing.

---

## Task 2: FoodScreen — Replace "Add meal slot" ghost box

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt:199-221`

The full-width outlined box at the bottom of the meal slot list uses a ghost border + two nested `Text` composables. Replace it with the standard secondary glass pill.

- [ ] **Step 1: Locate the `item { Box(...) }` block**

  Find lines 199–221 in the `LazyColumn` inside `FoodContent`:

  ```kotlin
  item {
      Box(
          modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
              .clickable { showAddSlotDialog = true }
              .padding(13.dp),
          contentAlignment = Alignment.Center,
      ) {
          Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
              Text("＋", fontSize = 16.sp, color = Color(0x80FFFFFF))
              Text(
                  "Add meal slot",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Medium,
                  color = TextVeryMuted,
              )
          }
      }
  }
  ```

- [ ] **Step 2: Replace with `LiquidSecondaryButton`**

  Delete the entire block above and write:

  ```kotlin
  item {
      LiquidSecondaryButton(
          text = "+ Add meal slot",
          onClick = { showAddSlotDialog = true },
      )
  }
  ```

- [ ] **Step 3: Add the import**

  In the import block, add:

  ```kotlin
  import com.zack.recomptracker.ui.liquidglass.LiquidSecondaryButton
  ```

- [ ] **Step 4: Remove the now-unused `TextVeryMuted` import**

  Delete this line (verified unused — was only referenced in the `Text` just removed):

  ```kotlin
  import com.zack.recomptracker.ui.theme.TextVeryMuted
  ```

- [ ] **Step 5: Build the project**

  ```bash
  cd "/Users/zackalatrash/Desktop/Personal Dietitian"
  ./gradlew :app:compileDebugKotlin
  ```

  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit both FoodScreen changes together**

  ```bash
  git add app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt
  git commit -m "feat(glass): replace solid/ghost buttons in FoodScreen with glass equivalents"
  ```

---

## Task 3: FoodsScreen — Replace TextButtons in SavedFoodRow

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foods/FoodsScreen.kt:162-163`

The `SavedFoodRow` composable uses Material3 `TextButton` for its "Add" and "Delete" inline actions.

- [ ] **Step 1: Locate `SavedFoodRow` in `FoodsScreen.kt`**

  Find lines 162–163 inside the `Row` in `SavedFoodRow`:

  ```kotlin
  TextButton(onClick = { onAdd(food) }) { Text("Add") }
  TextButton(onClick = { showDeleteConfirm = true }) { Text("Delete") }
  ```

- [ ] **Step 2: Replace both `TextButton` calls**

  Delete lines 162–163 and write:

  ```kotlin
  LiquidActionButton(text = "Add", onClick = { onAdd(food) }, isPrimary = true)
  LiquidActionButton(text = "Delete", onClick = { showDeleteConfirm = true }, isPrimary = false)
  ```

- [ ] **Step 3: Add the import**

  In the import block at the top of `FoodsScreen.kt`, add:

  ```kotlin
  import com.zack.recomptracker.ui.liquidglass.LiquidActionButton
  ```

  > Do NOT remove `TextButton` yet — it is also used in `SavedMealRow` (lines 193–194), which is changed in Task 4.

- [ ] **Step 4: Build the project**

  ```bash
  cd "/Users/zackalatrash/Desktop/Personal Dietitian"
  ./gradlew :app:compileDebugKotlin
  ```

  Expected: `BUILD SUCCESSFUL`.

---

## Task 4: FoodsScreen — Replace TextButtons in SavedMealRow

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foods/FoodsScreen.kt:193-194`

- [ ] **Step 1: Locate `SavedMealRow` in `FoodsScreen.kt`**

  Find lines 193–194 inside the `Row` in `SavedMealRow`:

  ```kotlin
  TextButton(onClick = { onAdd(meal) }) { Text("Add") }
  TextButton(onClick = { showDeleteConfirm = true }) { Text("Delete") }
  ```

- [ ] **Step 2: Replace both `TextButton` calls**

  Delete lines 193–194 and write:

  ```kotlin
  LiquidActionButton(text = "Add", onClick = { onAdd(meal) }, isPrimary = true)
  LiquidActionButton(text = "Delete", onClick = { showDeleteConfirm = true }, isPrimary = false)
  ```

- [ ] **Step 3: Remove the now-unused `TextButton` import**

  `TextButton` was only used in `SavedFoodRow` and `SavedMealRow` — both are now replaced. Delete this line:

  ```kotlin
  import androidx.compose.material3.TextButton
  ```

- [ ] **Step 4: Build the project**

  ```bash
  cd "/Users/zackalatrash/Desktop/Personal Dietitian"
  ./gradlew :app:compileDebugKotlin
  ```

  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit both FoodsScreen changes together**

  ```bash
  git add app/src/main/java/com/zack/recomptracker/ui/foods/FoodsScreen.kt
  git commit -m "feat(glass): replace TextButton rows in FoodsScreen with LiquidActionButton"
  ```

---

## Task 5: Final verification

- [ ] **Step 1: Full debug build**

  ```bash
  cd "/Users/zackalatrash/Desktop/Personal Dietitian"
  ./gradlew assembleDebug
  ```

  Expected: `BUILD SUCCESSFUL` with no errors.

- [ ] **Step 2: Run existing unit tests**

  ```bash
  ./gradlew testDebugUnitTest
  ```

  Expected: all tests pass (no logic was changed — these tests cover ViewModels and domain logic, not the UI composables we edited).

- [ ] **Step 3: Manual smoke-test checklist**

  Install on device/emulator and verify:
  - [ ] **FoodScreen (Log tab):** Each meal slot shows a glass "＋ Add" pill (violet-tinted, not a solid rectangle)
  - [ ] **FoodScreen (Log tab):** The bottom "Add meal slot" button is a clear glass pill (not a ghost outline box)
  - [ ] **FoodScreen:** Tapping "＋ Add" opens the food library as before
  - [ ] **FoodScreen:** Tapping "+ Add meal slot" opens the dialog as before
  - [ ] **FoodsScreen (Foods/Meals management):** Each saved food row shows glass "Add" (violet) and "Delete" (clear) buttons
  - [ ] **FoodsScreen:** Each saved meal row shows glass "Add" (violet) and "Delete" (clear) buttons
  - [ ] **No crashes** on navigating to/from any of these screens
  - [ ] **Nav bar glass effect** is unaffected (it uses `navBackdrop`, not `contentBackdrop`)
