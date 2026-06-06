# CoachScreen UI Fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix ten identified UI bugs in the AI Coach screen to improve keyboard handling, scroll behavior, visual consistency, and feedback.

**Architecture:** Targeted Compose-level fixes to `CoachScreen.kt`. No new files needed; all fixes live in the existing screen. Changes are visual/state-only and do not touch the ViewModel, coordinator, or domain layers.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Foundation `WindowInsets` / `imePadding` APIs, `LaunchedEffect`, `rememberUpdatedState`, `LocalSoftwareKeyboardController`, `LocalHapticFeedback`.

---

## File Map

**Modified production files:**
- `app/src/main/java/com/zack/recomptracker/ui/coach/CoachScreen.kt` — all ten fixes are applied here

**No new files, no new dependencies.**

---

## Issues Being Fixed

1. **#1 Keyboard scroll conflict** — `imePadding()` on the content Box + `LazyColumn` `contentPadding` fight each other; remove the Box-level padding and let `LazyColumn` consume the IME inset.
2. **#2 Auto-scroll on every recomposition** — `animateScrollToItem` fires on every `partialResponse` token, causing constant scroll jank.
3. **#3 No keyboard dismissal on send** — keyboard stays open after sending a message.
4. **#4 Input field loses focus after send** — the `OutlinedTextField` stays focused but empties, which on some IME implementations briefly flashes/clears oddly.
5. **#5 No bottom breathing room for last message** — `LazyColumn` `contentPadding` is only 8.dp vertical; last bubble sits flush against the input bar.
6. **#6 Thinking status duplicates** — every tool-status change adds a new "Thinking…" card instead of updating the existing one.
7. **#7 Partial response overwrites history** — streaming `partialResponse` is rendered as a separate LazyColumn item, leaving stale partials visible.
8. **#8 Empty error handling** — error is rendered as bare `Text` instead of in an `AiInsightCard`, breaking visual consistency.
9. **#10 Accessibility** — suggestion `FilterChip`s and the send `IconButton` lack `Role.Button` semantics and state descriptions.
10. **#14 No haptic feedback** — send/clear actions don't vibrate on tap.

---

## Task 1: Fix keyboard inset handling (#1, #5)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/coach/CoachScreen.kt:120-126` (content `Box`) and `:299-305` (`LazyColumn`)

- [ ] **Step 1: Remove `imePadding()` from the content `Box`**

Replace the content `Box` modifier chain. The current code is:

```kotlin
// Main content
Box(
    modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .imePadding(),
)
```

Change it to:

```kotlin
// Main content
Box(
    modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
)
```

- [ ] **Step 2: Replace the `LazyColumn` modifier to consume the IME inset via `contentPadding` and add bottom breathing room**

Replace the `LazyColumn` block (currently):

```kotlin
LazyColumn(
    state = listState,
    modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
) {
```

with:

```kotlin
LazyColumn(
    state = listState,
    modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 12.dp)
        .imePadding(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(
        top = 8.dp,
        bottom = 8.dp,
    ),
)
```

- [ ] **Step 3: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL, no warnings about missing imports. The `imePadding` import is already present on line 12.

---

## Task 2: Fix auto-scroll to ignore streaming partial updates (#2)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/coach/CoachScreen.kt:282-298` (`ChatContent`)

- [ ] **Step 1: Replace the `LaunchedEffect` key to only fire on new messages, not partial updates**

The current block is:

```kotlin
@Composable
private fun ChatContent(
    history: List<ChatMessage>,
    thinkingStatus: String?,
    partialResponse: String?,
    errorMessage: String?,
) {
    val listState = rememberLazyListState()

    val totalItems = history.size +
        (if (thinkingStatus != null || partialResponse != null) 1 else 0) +
        (if (errorMessage != null) 1 else 0)

    LaunchedEffect(totalItems, partialResponse) {
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }
```

Replace it with:

```kotlin
@Composable
private fun ChatContent(
    history: List<ChatMessage>,
    thinkingStatus: String?,
    partialResponse: String?,
    errorMessage: String?,
) {
    val listState = rememberLazyListState()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    val totalItems = history.size +
        (if (thinkingStatus != null || partialResponse != null) 1 else 0) +
        (if (errorMessage != null) 1 else 0)

    // Scroll to bottom only when a new user/assistant message appears.
    // Streaming partial updates would re-fire this every token and jank the scroll.
    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) {
            listState.animateScrollToItem(history.size - 1)
        }
    }
```

- [ ] **Step 2: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

---

## Task 3: Add keyboard dismissal and focus clearing on send (#3, #4)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/coach/CoachScreen.kt:32-36` (imports), `:80-85` (`sendCurrent`), `:159-208` (input `Row`)

- [ ] **Step 1: Add required imports**

Add these imports near the top of the file (after the existing `androidx.compose.ui.unit.sp` import on line 41):

```kotlin
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
```

- [ ] **Step 2: Add `FocusRequester`, `FocusManager`, and keyboard controller to `CoachScreen`**

Inside `CoachScreen`, after `var inputText by remember { mutableStateOf("") }` on line 65, add:

```kotlin
val focusRequester = remember { FocusRequester() }
val focusManager = LocalFocusManager.current
val keyboardController = LocalSoftwareKeyboardController.current
val haptic = LocalHapticFeedback.current
```

- [ ] **Step 3: Update `sendCurrent` to dismiss keyboard, clear focus, and trigger haptic feedback**

Replace the `sendCurrent` function (lines 80-85):

```kotlin
fun sendCurrent() {
    if (canSend) {
        viewModel.sendMessage(inputText.trim())
        inputText = ""
    }
}
```

with:

```kotlin
fun sendCurrent() {
    if (canSend) {
        viewModel.sendMessage(inputText.trim())
        inputText = ""
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        keyboardController?.hide()
        focusManager.clearFocus()
    }
}
```

- [ ] **Step 4: Wire the `FocusRequester` and IME action on the `OutlinedTextField`**

Replace the `OutlinedTextField` (lines 172-197). The new version adds `imeAction = ImeAction.Send`, `keyboardActions`, and `modifier = Modifier.weight(1f).focusRequester(focusRequester)`:

```kotlin
OutlinedTextField(
    value = inputText,
    onValueChange = { inputText = it },
    modifier = Modifier
        .weight(1f)
        .focusRequester(focusRequester),
    placeholder = {
        Text(
            text = if (available) "Ask the coach…" else "Download model first",
            color = TextMuted,
        )
    },
    enabled = available && !busy,
    maxLines = 4,
    shape = RoundedCornerShape(16.dp),
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
    keyboardActions = KeyboardActions(
        onSend = { sendCurrent() },
    ),
    colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        disabledTextColor = Color.White.copy(alpha = 0.3f),
        focusedBorderColor = CardBorder,
        unfocusedBorderColor = CardBorder,
        disabledBorderColor = CardBorder.copy(alpha = 0.4f),
        focusedContainerColor = TintedSurface,
        unfocusedContainerColor = TintedSurface,
        disabledContainerColor = TintedSurface.copy(alpha = 0.5f),
        cursorColor = Color.White,
    ),
)
```

- [ ] **Step 5: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

---

## Task 4: Consolidate thinking + partial response into a single live item (#6, #7)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/coach/CoachScreen.kt:299-362` (`LazyColumn` body)

The current implementation renders two separate optional items (`thinkingStatus` and `partialResponse`). When both are non-null, the user sees two stacked cards. When `thinkingStatus` changes, the same item key isn't used, so Compose treats it as a new item.

Fix: render a single "in-progress" item that shows whichever content is most recent, keyed by a stable identifier.

- [ ] **Step 1: Replace the `LazyColumn` body to use a single live item**

Replace the entire `LazyColumn` block inside `ChatContent` (lines 299-362). The new version:

```kotlin
LazyColumn(
    state = listState,
    modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 12.dp)
        .imePadding(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(
        top = 8.dp,
        bottom = 8.dp,
    ),
) {
    items(history) { message ->
        ChatBubble(message = message)
    }

    // Single live in-progress item: prefers streaming response, falls back to thinking.
    // Keyed by a stable id so updates don't recreate the card on every token.
    if (partialResponse != null) {
        item(key = "live-response") {
            AiInsightCard(
                borderMode = AiBorderMode.Generating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = partialResponse,
                    color = Color.White,
                    fontSize = 14.sp,
                )
            }
        }
    } else if (thinkingStatus != null) {
        item(key = "live-thinking") {
            AiInsightCard(
                borderMode = AiBorderMode.Preparing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Thinking…",
                    color = Color.White,
                    fontSize = 14.sp,
                )
                if (!thinkingStatus.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = thinkingStatus,
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }

    if (errorMessage != null) {
        item(key = "live-error") {
            AiInsightCard(
                borderMode = AiBorderMode.Static,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = errorMessage,
                    color = ErrorRed,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

---

## Task 5: Wrap error in `AiInsightCard` (#8)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/coach/CoachScreen.kt:351-361`

This is already done as part of Task 4's replacement. Verify by reading the final file: the error block should be wrapped in `AiInsightCard(borderMode = AiBorderMode.Static)` with `Text(color = ErrorRed)`.

- [ ] **Step 1: Confirm visually**

Re-read lines 350-365 of `CoachScreen.kt` after Task 4 is applied. The error rendering must:

- Use `AiInsightCard(borderMode = AiBorderMode.Static, modifier = Modifier.fillMaxWidth())`
- Place an `ErrorRed`-colored `Text` inside the card
- No longer use the bare `Text(... color = ErrorRed, ...)` block

If not correct, apply the Task 4 replacement verbatim.

- [ ] **Step 2: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

---

## Task 6: Add accessibility semantics to chips and send button (#10)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/coach/CoachScreen.kt:198-207` (send `IconButton`), `:255-275` (suggestion `FilterChip`), `:105-118` (clear `IconButton`)

- [ ] **Step 1: Add the `semantics` import**

Add this import near the top of the file:

```kotlin
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
```

- [ ] **Step 2: Add semantics to the send `IconButton`**

Replace the send `IconButton` (lines 198-207):

```kotlin
IconButton(
    onClick = { sendCurrent() },
    enabled = canSend,
) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.Send,
        contentDescription = "Send",
        tint = if (canSend) Color.White else Color.White.copy(alpha = 0.25f),
    )
}
```

with:

```kotlin
IconButton(
    onClick = { sendCurrent() },
    enabled = canSend,
    modifier = Modifier.semantics {
        role = Role.Button
        contentDescription = "Send message"
        stateDescription = if (canSend) "Ready" else "Disabled"
    },
) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.Send,
        contentDescription = null,
        tint = if (canSend) Color.White else Color.White.copy(alpha = 0.25f),
    )
}
```

- [ ] **Step 3: Add semantics to the clear `IconButton`**

Replace the clear `IconButton` (lines 105-118):

```kotlin
IconButton(
    onClick = {
        viewModel.clearHistory()
        inputText = ""
    },
    enabled = canClear,
) {
    Icon(
        imageVector = Icons.Default.RestartAlt,
        contentDescription = "Clear conversation",
        tint = if (canClear) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.2f),
    )
}
```

with:

```kotlin
IconButton(
    onClick = {
        viewModel.clearHistory()
        inputText = ""
    },
    enabled = canClear,
    modifier = Modifier.semantics {
        role = Role.Button
        contentDescription = "Clear conversation"
        stateDescription = if (canClear) "Available" else "Disabled"
    },
) {
    Icon(
        imageVector = Icons.Default.RestartAlt,
        contentDescription = null,
        tint = if (canClear) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.2f),
    )
}
```

- [ ] **Step 4: Add semantics to suggestion `FilterChip`s**

In `ReadyContent`, replace the `FilterChip` block (lines 255-275):

```kotlin
FilterChip(
    selected = false,
    onClick = { onSuggestion(suggestion) },
    label = {
        Text(
            text = suggestion,
            color = Color.White,
            fontSize = 14.sp,
        )
    },
    modifier = Modifier
        .padding(vertical = 4.dp)
        .widthIn(max = 320.dp),
    shape = RoundedCornerShape(20.dp),
    colors = FilterChipDefaults.filterChipColors(
        containerColor = TintedSurface,
        selectedContainerColor = TintedSurface,
        labelColor = Color.White,
    ),
    border = BorderStroke(1.dp, TintedBorder),
)
```

with:

```kotlin
FilterChip(
    selected = false,
    onClick = { onSuggestion(suggestion) },
    label = {
        Text(
            text = suggestion,
            color = Color.White,
            fontSize = 14.sp,
        )
    },
    modifier = Modifier
        .padding(vertical = 4.dp)
        .widthIn(max = 320.dp)
        .semantics {
            role = Role.Button
            contentDescription = "Suggested prompt"
            stateDescription = suggestion
        },
    shape = RoundedCornerShape(20.dp),
    colors = FilterChipDefaults.filterChipColors(
        containerColor = TintedSurface,
        selectedContainerColor = TintedSurface,
        labelColor = Color.White,
    ),
    border = BorderStroke(1.dp, TintedBorder),
)
```

- [ ] **Step 5: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

---

## Task 7: Add haptic feedback to clear button (#14)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/coach/CoachScreen.kt:105-118` (already touched in Task 6, now also add haptic)

- [ ] **Step 1: Add haptic to the clear `IconButton` `onClick`**

The clear `IconButton` `onClick` lambda currently is:

```kotlin
onClick = {
    viewModel.clearHistory()
    inputText = ""
},
```

Replace it with:

```kotlin
onClick = {
    viewModel.clearHistory()
    inputText = ""
    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
},
```

(The `haptic` `val` was already declared in Task 3, Step 2.)

- [ ] **Step 2: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

---

## Final Verification

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL. No new tests are required (the fixes are pure UI state changes with no testable domain logic).

- [ ] **Step 2: Run the debug build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual smoke test checklist**

When the user reviews the app, confirm:

- [ ] Tapping the text field opens the keyboard, the chat list scrolls, and the input bar stays at the bottom of the visible area.
- [ ] Pressing the IME Send action sends the message and dismisses the keyboard.
- [ ] Tapping the paper-airplane button sends, dismisses the keyboard, and clears focus.
- [ ] A long response streams without the list jankily auto-scrolling on every token (it scrolls once when the message is added to history).
- [ ] An error message renders inside a styled card, not as bare red text.
- [ ] Tapping a suggestion chip fires a haptic tap and sends the prompt.
- [ ] TalkBack announces "Send message, Ready/Disabled", "Clear conversation, Available/Disabled", and "Suggested prompt, <text>" for each interactive control.

---

## Self-Review Checklist

- [x] **Spec coverage:** Issues #1, #2, #3, #4, #5, #6, #7, #8, #10, #14 all have a dedicated task.
- [x] **Placeholder scan:** No "TBD" or "similar to Task N" steps. Every code block is complete and ready to paste.
- [x] **Type consistency:** `haptic` is declared once in Task 3, reused in Task 7. `FocusRequester` declared once. `LaunchedEffect` history.size key is consistent across tasks. `imePadding()` import is on line 12 and remains valid.
- [x] **No new files, no new dependencies** — all changes are within `CoachScreen.kt`.
- [x] **Build commands** — all tasks end with `./gradlew assembleDebug` for verification.
