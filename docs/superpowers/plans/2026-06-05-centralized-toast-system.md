# Centralized Toast System — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hidden-behind-nav `SnackbarHost` with a centralized `ToastController` that renders themed tinted-pill toasts above the floating nav bar.

**Architecture:** A `Channel`-backed `ToastController` is provided at the root via `LocalToastController` (CompositionLocal). A `ToastOverlay` composable placed in the outermost `Box` of `RecompApp` — above the `LiquidBottomTabs` layer — collects messages and renders `AnimatedVisibility`-wrapped tinted pill toasts. The old `LocalSnackbarHostState` and `SnackbarHost` are removed entirely.

**Tech Stack:** Kotlin, Jetpack Compose, `kotlinx.coroutines.channels.Channel`, `kotlinx.coroutines.flow.receiveAsFlow`, `AnimatedVisibility`, `slideInVertically`/`fadeIn`

---

## File Map

| Action | Path |
|--------|------|
| CREATE | `app/src/main/java/com/zack/recomptracker/ui/toast/ToastMessage.kt` |
| CREATE | `app/src/main/java/com/zack/recomptracker/ui/toast/ToastController.kt` |
| CREATE | `app/src/main/java/com/zack/recomptracker/ui/toast/ToastOverlay.kt` |
| MODIFY | `app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt` |
| MODIFY | `app/src/main/java/com/zack/recomptracker/ui/today/BodyRecoveryScreen.kt` |
| MODIFY | `app/src/main/java/com/zack/recomptracker/ui/plan/PlanScreen.kt` |
| MODIFY | `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt` |
| CREATE | `app/src/test/java/com/zack/recomptracker/ui/toast/ToastControllerTest.kt` |

---

## Task 1: Data model

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/toast/ToastMessage.kt`

- [ ] **Step 1: Create `ToastMessage.kt`**

```kotlin
package com.zack.recomptracker.ui.toast

enum class ToastType { Success, Error, Info }

data class ToastMessage(
    val text: String,
    val type: ToastType,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/toast/ToastMessage.kt
git commit -m "feat(toast): add ToastMessage data model and ToastType enum"
```

---

## Task 2: ToastController

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/toast/ToastController.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ui/toast/ToastControllerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ui/toast/ToastControllerTest.kt`:

```kotlin
package com.zack.recomptracker.ui.toast

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ToastControllerTest {

    @Test
    fun `show emits message to flow`() = runTest {
        val controller = ToastController()
        val received = mutableListOf<ToastMessage>()
        val job = launch { controller.messages.take(1).toList(received) }
        controller.show(ToastMessage("Plan saved", ToastType.Success))
        job.join()
        assertEquals(1, received.size)
        assertEquals("Plan saved", received[0].text)
        assertEquals(ToastType.Success, received[0].type)
    }

    @Test
    fun `show preserves action label`() = runTest {
        val controller = ToastController()
        val received = mutableListOf<ToastMessage>()
        val job = launch { controller.messages.take(1).toList(received) }
        controller.show(ToastMessage("Could not save", ToastType.Error, actionLabel = "Retry"))
        job.join()
        assertEquals("Retry", received[0].actionLabel)
    }

    @Test
    fun `multiple messages are queued in order`() = runTest {
        val controller = ToastController()
        val received = mutableListOf<ToastMessage>()
        val job = launch { controller.messages.take(2).toList(received) }
        controller.show(ToastMessage("First", ToastType.Info))
        controller.show(ToastMessage("Second", ToastType.Success))
        job.join()
        assertEquals("First", received[0].text)
        assertEquals("Second", received[1].text)
    }
}
```

- [ ] **Step 2: Run test — expect compile error (ToastController not defined yet)**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian"
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.toast.ToastControllerTest" 2>&1 | tail -20
```

Expected: compile error — `Unresolved reference: ToastController`

- [ ] **Step 3: Create `ToastController.kt`**

```kotlin
package com.zack.recomptracker.ui.toast

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class ToastController {
    private val _channel = Channel<ToastMessage>(capacity = Channel.BUFFERED)
    val messages = _channel.receiveAsFlow()

    suspend fun show(message: ToastMessage) {
        _channel.send(message)
    }
}

val LocalToastController = staticCompositionLocalOf<ToastController> {
    error("No ToastController provided")
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.toast.ToastControllerTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` — 3 tests passed

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/toast/ToastController.kt \
        app/src/test/java/com/zack/recomptracker/ui/toast/ToastControllerTest.kt
git commit -m "feat(toast): add ToastController with Channel-backed message queue"
```

---

## Task 3: ToastOverlay composable

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/toast/ToastOverlay.kt`

- [ ] **Step 1: Create `ToastOverlay.kt`**

```kotlin
package com.zack.recomptracker.ui.toast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.theme.CornerPill
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.Violet300
import kotlinx.coroutines.delay

@Composable
fun ToastOverlay(modifier: Modifier = Modifier) {
    val controller = LocalToastController.current
    var currentToast by remember { mutableStateOf<ToastMessage?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(controller) {
        controller.messages.collect { message ->
            if (visible) {
                visible = false
                delay(200L)
            }
            currentToast = message
            visible = true
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            delay(3000L)
            visible = false
            delay(300L)
            currentToast = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = FloatingNavHeight + 8.dp)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220)),
            exit  = slideOutVertically(tween(180)) { it / 2 } + fadeOut(tween(180)),
        ) {
            currentToast?.let { toast ->
                ToastItem(toast = toast, onDismiss = { visible = false })
            }
        }
    }
}

@Composable
private fun ToastItem(
    toast: ToastMessage,
    onDismiss: () -> Unit,
) {
    val bgColor = when (toast.type) {
        ToastType.Success -> Color(0x388B5CF6)
        ToastType.Error   -> Color(0x2EFB7185)
        ToastType.Info    -> Color(0x14FFFFFF)
    }
    val borderColor = when (toast.type) {
        ToastType.Success -> Color(0x728B5CF6)
        ToastType.Error   -> Color(0x66FB7185)
        ToastType.Info    -> Color(0x26FFFFFF)
    }
    val iconText = when (toast.type) {
        ToastType.Success -> "✓"
        ToastType.Error   -> "✕"
        ToastType.Info    -> "ℹ"
    }
    val iconTint = when (toast.type) {
        ToastType.Success -> Violet300
        ToastType.Error   -> ErrorRed
        ToastType.Info    -> Color(0x99FFFFFF)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(CornerPill))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(CornerPill))
            .padding(horizontal = 18.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = iconText,
            color = iconTint,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = toast.text,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        if (toast.actionLabel != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = toast.actionLabel,
                color = Violet300,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    toast.onAction?.invoke()
                    onDismiss()
                },
            )
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|warning:|BUILD" | tail -20
```

Expected: `BUILD SUCCESSFUL` with no errors

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/toast/ToastOverlay.kt
git commit -m "feat(toast): add ToastOverlay composable with tinted pill style"
```

---

## Task 4: Wire up in RecompApp

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt`

The changes:
1. Remove `LocalSnackbarHostState` val and its `SnackbarHostState` import
2. Remove `val snackbarHostState = remember { SnackbarHostState() }`
3. Remove `LocalSnackbarHostState provides snackbarHostState` from `CompositionLocalProvider`
4. Remove `snackbarHost = { SnackbarHost(snackbarHostState) }` from `Scaffold`
5. Add `val toastController = remember { ToastController() }`
6. Add `LocalToastController provides toastController` to `CompositionLocalProvider`
7. Add `ToastOverlay()` after the nav bar block

- [ ] **Step 1: Apply all changes to `RecompApp.kt`**

The final `RecompApp.kt` should look like this (only showing the changed sections, keep everything else identical):

**Remove these lines (imports block):**
```kotlin
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
```

**Remove these declarations** (top-level, before `RecompApp`):
```kotlin
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}
```

**Add this import** alongside the other `com.zack.recomptracker.ui.toast` imports:
```kotlin
import com.zack.recomptracker.ui.toast.LocalToastController
import com.zack.recomptracker.ui.toast.ToastController
import com.zack.recomptracker.ui.toast.ToastOverlay
```

**Inside `RecompApp`, replace:**
```kotlin
val snackbarHostState = remember { SnackbarHostState() }
```
**With:**
```kotlin
val toastController = remember { ToastController() }
```

**Inside `CompositionLocalProvider`, replace:**
```kotlin
LocalSnackbarHostState provides snackbarHostState,
```
**With:**
```kotlin
LocalToastController provides toastController,
```

**Inside `Scaffold`, replace:**
```kotlin
snackbarHost = { SnackbarHost(snackbarHostState) },
```
**With** (remove the parameter entirely — use default empty snackbarHost):
```kotlin
// no snackbarHost parameter
```

The `Scaffold` call becomes:
```kotlin
Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = Color.Transparent,
) { innerPadding ->
    AppNavGraph(
        navController = navController,
        modifier = Modifier.padding(innerPadding),
    )
}
```

**After the nav bar block** (after the closing `}` of the `if (currentRoute in topLevelRoutes)` block), add:
```kotlin
// Toast overlay — always above nav
ToastOverlay()
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|warning:|BUILD" | tail -20
```

Expected: `BUILD SUCCESSFUL` — may show warnings about unused imports in the migrated screens (those come next)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt
git commit -m "feat(toast): wire ToastController and ToastOverlay into RecompApp"
```

---

## Task 5: Migrate the three call sites

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/BodyRecoveryScreen.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/plan/PlanScreen.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`

- [ ] **Step 1: Migrate `BodyRecoveryScreen.kt`**

**Remove import:**
```kotlin
import com.zack.recomptracker.ui.LocalSnackbarHostState
```

**Add imports:**
```kotlin
import com.zack.recomptracker.ui.toast.LocalToastController
import com.zack.recomptracker.ui.toast.ToastMessage
import com.zack.recomptracker.ui.toast.ToastType
```

**Replace inside `BodyRecoveryScreen` composable** (lines 70–74):
```kotlin
val snackbarHostState = LocalSnackbarHostState.current
LaunchedEffect(viewModel) {
    viewModel.savedEvent.collect {
        snackbarHostState.showSnackbar("Check-in saved")
    }
}
```
**With:**
```kotlin
val toastController = LocalToastController.current
LaunchedEffect(viewModel) {
    viewModel.savedEvent.collect {
        toastController.show(ToastMessage("Check-in saved", ToastType.Success))
    }
}
```

- [ ] **Step 2: Migrate `PlanScreen.kt`**

**Remove import:**
```kotlin
import com.zack.recomptracker.ui.LocalSnackbarHostState
```

**Add imports:**
```kotlin
import com.zack.recomptracker.ui.toast.LocalToastController
import com.zack.recomptracker.ui.toast.ToastMessage
import com.zack.recomptracker.ui.toast.ToastType
```

**Replace inside `PlanScreen` composable** (lines 62–66):
```kotlin
val snackbarHostState = LocalSnackbarHostState.current
LaunchedEffect(viewModel) {
    viewModel.savedEvent.collect {
        snackbarHostState.showSnackbar("Plan saved")
    }
}
```
**With:**
```kotlin
val toastController = LocalToastController.current
LaunchedEffect(viewModel) {
    viewModel.savedEvent.collect {
        toastController.show(ToastMessage("Plan saved", ToastType.Success))
    }
}
```

- [ ] **Step 3: Migrate `FoodLibraryScreen.kt`**

**Remove import:**
```kotlin
import com.zack.recomptracker.ui.LocalSnackbarHostState
```

**Add imports:**
```kotlin
import com.zack.recomptracker.ui.toast.LocalToastController
import com.zack.recomptracker.ui.toast.ToastMessage
import com.zack.recomptracker.ui.toast.ToastType
```

**Replace inside `FoodLibraryScreen` composable** (lines 103–107):
```kotlin
val snackbarHostState = LocalSnackbarHostState.current
LaunchedEffect(viewModel) {
    viewModel.loggedEvent.collect { message ->
        snackbarHostState.showSnackbar(message)
    }
}
```
**With:**
```kotlin
val toastController = LocalToastController.current
LaunchedEffect(viewModel) {
    viewModel.loggedEvent.collect { message ->
        toastController.show(ToastMessage(message, ToastType.Success))
    }
}
```

- [ ] **Step 4: Full build and unit tests**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest 2>&1 | grep -E "error:|FAILED|BUILD|tests were" | tail -20
```

Expected: `BUILD SUCCESSFUL` — all tests pass, no compile errors

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/BodyRecoveryScreen.kt \
        app/src/main/java/com/zack/recomptracker/ui/plan/PlanScreen.kt \
        app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt
git commit -m "feat(toast): migrate all call sites from LocalSnackbarHostState to LocalToastController"
```
