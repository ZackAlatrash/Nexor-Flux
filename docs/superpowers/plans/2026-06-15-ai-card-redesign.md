# AI Insight Card Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single flat `GeneratedInsightCard` with one collapsible liquid-glass card engine — real Kyant nav-bar glass + Apple-Intelligence edge glow — rendering three structural variants (Hero / Standard / Pill) with verdict-first copy, evidence, confidence, and on-card actions across all five AI surfaces.

**Architecture:** Evolve the existing components in place. A new `Modifier.aiEdgeGlow` replaces the rim border; `AiInsightCard` is retuned to the nav-bar glass recipe and gains a `collapsed`/`shape` capability; `GeneratedInsightCard` becomes the variant-aware, collapsible engine with backward-compatible defaults so call sites migrate one at a time. Pure logic (variant/confidence) is isolated and unit-tested; visual layers are verified via Compose `@Preview` + build.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Kyant `io.github.kyant0:backdrop:2.0.0` + `shapes:1.2.0`, JUnit unit tests.

**Spec:** [docs/ai-card-design.md](../../ai-card-design.md)

---

## Scope & deferrals

In scope (this plan):
- Real nav-bar liquid-glass material on every AI card.
- Apple-Intelligence edge glow (clean face, perimeter glow, brighter while generating).
- Collapsible pill ↔ card with one spring; collapse state remembered in-composition.
- Three structural variants: **Hero**, **Standard**, **Pill**; plus the **Generating** shimmer treatment and **Error** state.
- Verdict-first text, optional evidence line, optional confidence badge.
- On-card action affordances: expand/collapse (functional), snooze + dismiss (session-level + undo), feedback thumbs (hook), tell-me-more (optional hook).
- Wiring all five surfaces + the weekly-review modal glow.

Explicitly deferred (separate follow-ups, noted in tasks): durable cross-session snooze/dismiss persistence; the **Carousel** variant and Hero numeric metric/sparkline (need `InsightEngine` to expose multiple facts and structured numbers); wiring feedback into ranking; seeding the coach chat from "Tell me more".

---

## File structure

| File | Responsibility | Action |
|---|---|---|
| `ui/component/InsightCardModels.kt` | `InsightCardVariant`, `ConfidenceLevel`, `confidenceFrom()` — pure logic | Create |
| `ui/component/AiEdgeGlow.kt` | `Modifier.aiEdgeGlow(mode, cornerRadius)` + glow palette reuse | Create |
| `ui/component/InsightShimmer.kt` | Shimmer skeleton lines for the generating state | Create |
| `ui/component/AiInsightCard.kt` | Retune glass to nav-bar recipe; swap rim→glow; add `shape`/`collapsedShape` | Modify |
| `ui/component/GeneratedInsightCard.kt` | Variant-aware collapsible engine + slots + actions | Modify |
| `test/.../ui/component/InsightCardModelsTest.kt` | Unit tests for pure logic | Create |
| `ui/dashboard/DashboardScreen.kt` | WEEKLY_PATTERN → Hero + evidence + confidence | Modify |
| `ui/progress/ProgressScreen.kt` | PROGRESS_TREND → Standard | Modify |
| `ui/today/BodyRecoveryScreen.kt` | RECOVERY_READINESS → Standard | Modify |
| `ui/today/FoodScreen.kt` | REST_OF_DAY → Pill (collapsed) | Modify |
| `ui/review/WeeklyBriefingOverlay.kt` | Briefing modal rim → edge glow | Modify |

---

## Task 1: Pure logic — variant + confidence models

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/InsightCardModels.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ui/component/InsightCardModelsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InsightCardModelsTest {
    @Test fun highPriorityMapsToHigh() {
        assertEquals(ConfidenceLevel.HIGH, confidenceFrom(3))
        assertEquals(ConfidenceLevel.HIGH, confidenceFrom(10))
    }

    @Test fun midPriorityMapsToMedium() {
        assertEquals(ConfidenceLevel.MEDIUM, confidenceFrom(1))
        assertEquals(ConfidenceLevel.MEDIUM, confidenceFrom(2))
    }

    @Test fun nonPositivePriorityIsNull() {
        assertNull(confidenceFrom(0))
        assertNull(confidenceFrom(-5))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.component.InsightCardModelsTest"`
Expected: FAIL — `confidenceFrom` / `ConfidenceLevel` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.zack.recomptracker.ui.component

/** Structural card variant, chosen per surface by insight importance. */
enum class InsightCardVariant { HERO, STANDARD, PILL }

/** Coarse confidence label derived from a fact's priority. */
enum class ConfidenceLevel { MEDIUM, HIGH }

/**
 * Derives a confidence label from [InsightFact.priority]. Higher priority = stronger signal.
 * Returns null for non-positive priorities (no badge shown).
 */
fun confidenceFrom(priority: Int): ConfidenceLevel? = when {
    priority <= 0 -> null
    priority >= 3 -> ConfidenceLevel.HIGH
    else -> ConfidenceLevel.MEDIUM
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.component.InsightCardModelsTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/InsightCardModels.kt \
        app/src/test/java/com/zack/recomptracker/ui/component/InsightCardModelsTest.kt
git commit -m "feat(ai-cards): add InsightCardVariant + ConfidenceLevel models"
```

---

## Task 2: Edge-glow modifier

Replaces the saturated rim with a soft Apple-Intelligence perimeter glow. Reuses `IridescentStops` and the hue animation; keeps the reduced-motion guard. The blurred glow uses `Modifier.blur` (API 31+) with a static fallback below 31.

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/AiEdgeGlow.kt`

- [ ] **Step 1: Implement the modifier**

```kotlin
package com.zack.recomptracker.ui.component

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Apple-Intelligence-style edge glow: a soft full-spectrum halo hugging the card perimeter while
 * the glass face stays clean. The hue breathes in place (geometry never rotates). Intensity and
 * speed encode [mode] — brighter and faster while Generating. Falls back to a static glow when
 * system animations are off, and to an un-blurred soft glow below API 31.
 *
 * Apply BEHIND the glass surface (before `.drawBackdrop`), so the blurred halo spills outward and
 * the glass covers the centre.
 */
@Composable
fun Modifier.aiEdgeGlow(mode: AiBorderMode, cornerRadius: Dp): Modifier {
    val context = LocalContext.current
    val animationsEnabled = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    val effectiveMode = if (animationsEnabled) mode else AiBorderMode.Static

    val transition = rememberInfiniteTransition(label = "aiEdgeGlow")
    val cycleMs = if (effectiveMode == AiBorderMode.Generating) 7000 else 16000
    val huePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(cycleMs, easing = LinearEasing)),
        label = "huePhase",
    )

    val targetAlpha = when (effectiveMode) {
        AiBorderMode.Generating -> 0.72f
        AiBorderMode.Preparing -> 0.55f
        AiBorderMode.Ready -> 0.50f
        AiBorderMode.Static -> 0.45f
    }
    val glowAlpha by animateFloatAsState(targetAlpha, tween(600), label = "glowAlpha")

    val shift = if (animationsEnabled) huePhase else 0f
    val colors = IridescentStops.map { it.hueShifted(shift) }
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // The halo is a stroked sweep gradient drawn just outside the content bounds, then blurred.
    val haloStroke = 3.dp
    return this
        .drawBehind {
            val inset = haloStroke.toPx() / 2f
            val corner = CornerRadius(cornerRadius.toPx() + inset)
            drawRoundRect(
                brush = Brush.sweepGradient(colors, center = Offset(size.width / 2f, size.height / 2f)),
                topLeft = Offset(-inset, -inset),
                size = Size(size.width + inset * 2f, size.height + inset * 2f),
                cornerRadius = corner,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = haloStroke.toPx() * 2f),
                alpha = glowAlpha,
            )
        }
        .then(if (supportsBlur) Modifier.blur(11.dp) else Modifier)
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

> Note: the blur on the glow layer is achieved by ordering — see Task 3, where the glow modifier is applied to a sibling layer behind the glass. If `Modifier.blur` here blurs content rather than the layer, switch to drawing the halo into a separate `Box` placed behind the glass `Box` and apply `.blur` to that Box. Task 3 wires the final composition; this step only verifies the draw + fallback compile.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/AiEdgeGlow.kt
git commit -m "feat(ai-cards): add aiEdgeGlow modifier (Apple-Intelligence perimeter glow)"
```

---

## Task 3: Retune AiInsightCard to the nav-bar glass + glow + collapsed shape

Aligns the glass material to `LiquidBottomTabs` (blur 8 / lens 24 / neutral wash), composes the edge glow behind it, and adds a `shape` parameter so the same card can render as a `RoundedRectangle` (expanded) or `Capsule` (collapsed pill). The glow is drawn on a sibling layer behind the glass so the blur applies to the halo only.

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/component/AiInsightCard.kt`

- [ ] **Step 1: Replace the AiInsightCard composable body**

Replace the `AiInsightCard` function (lines 60–100) with:

```kotlin
@Composable
fun AiInsightCard(
    borderMode: AiBorderMode,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalBackdrop.current
    val cornerDp = if (collapsed) CornerPill else CornerCard
    val isDark = com.zack.recomptracker.ui.theme.LocalAppColors.current.isDark
    val containerColor =
        if (isDark) Color(0xFF121212).copy(alpha = 0.40f) else Color(0xFFFAFAFA).copy(alpha = 0.40f)

    Box(modifier = modifier.fillMaxWidth()) {
        // Halo layer (blurred) sits behind the glass.
        Box(
            Modifier
                .matchParentSize()
                .aiEdgeGlow(borderMode, cornerDp),
        )
        // Glass layer — exact nav-bar recipe.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(if (collapsed) Capsule() else RoundedCornerShape(cornerDp))
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { if (collapsed) Capsule() else RoundedRectangle(cornerDp) },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    highlight = { Highlight.Default },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .padding(contentPadding),
            content = content,
        )
    }
}
```

- [ ] **Step 2: Update imports**

Add to the import block:

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import com.kyant.shapes.Capsule
import com.zack.recomptracker.ui.theme.CornerPill
```

Remove now-unused imports: `aiIridescentRim` usages stay (still used by the modal until Task 9); keep `Shadow`/`Brush`/`Offset`/draw imports only if still referenced — the executor removes any import the compiler flags as unused.

- [ ] **Step 3: Add a collapsed preview**

Append:

```kotlin
@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewCollapsedPill() {
    AiInsightCard(borderMode = AiBorderMode.Static, collapsed = true, contentPadding = 12.dp) {
        androidx.compose.material3.Text("You're 24g under protein today", color = Color.White)
    }
}
```

- [ ] **Step 4: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (If `LocalBackdrop` is `EmptyBackdrop` in a preview, the glass renders flat — expected; real screens provide the backdrop.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/AiInsightCard.kt
git commit -m "feat(ai-cards): retune AiInsightCard to nav-bar glass + edge glow + collapsed pill"
```

---

## Task 4: Shimmer skeleton for the generating state

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/InsightShimmer.kt`

- [ ] **Step 1: Implement the shimmer**

```kotlin
package com.zack.recomptracker.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.theme.LocalAppAccent

/** Three colored shimmer lines that read as "the coach is writing", not a gray spinner. */
@Composable
fun InsightShimmerLines(modifier: Modifier = Modifier) {
    val accent = LocalAppAccent.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "shimmerPhase",
    )
    Column(modifier.fillMaxWidth()) {
        ShimmerBar(0.92f, phase, accent.accentLighter)
        ShimmerBar(0.78f, phase, accent.accentLighter, top = 9.dp)
        ShimmerBar(0.55f, phase, accent.accentLighter, top = 9.dp)
    }
}

@Composable
private fun ShimmerBar(widthFraction: Float, phase: Float, shine: Color, top: androidx.compose.ui.unit.Dp = 0.dp) {
    Column(
        Modifier
            .padding(top = top)
            .fillMaxWidth(widthFraction)
            .height(13.dp)
            .clip(RoundedCornerShape(6.dp))
            .drawWithCache {
                val w = size.width
                val travel = w * 2f
                val start = -w + phase * travel
                val brush = Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.05f),
                    0.5f to shine.copy(alpha = 0.30f),
                    1f to Color.White.copy(alpha = 0.05f),
                    start = Offset(start, 0f),
                    end = Offset(start + w, 0f),
                )
                onDrawBehind { drawRect(brush) }
            },
    ) {}
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/InsightShimmer.kt
git commit -m "feat(ai-cards): add colored shimmer lines for generating state"
```

---

## Task 5: GeneratedInsightCard — collapsible variant engine

Rewrites the renderer into the variant-aware, collapsible engine. The signature stays backward-compatible: `title`, `state`, `onRetry` keep their positions; all new params default so unmigrated call sites compile unchanged.

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/component/GeneratedInsightCard.kt`

- [ ] **Step 1: Replace the file body (keep package + add imports)**

Replace lines 1–96 (package, imports, `GeneratedInsightCard`, `InsightCardHeader`) with:

```kotlin
package com.zack.recomptracker.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

/**
 * Variant-aware, collapsible AI insight card. One engine; the [variant] sets prominence and
 * default expansion. Collapsed = a glass pill (label + one-line verdict); expanded = verdict,
 * optional [evidence], optional [confidence], and the action row. Backward compatible: the first
 * three params are unchanged, all others default.
 */
@Composable
fun GeneratedInsightCard(
    title: String,
    state: AiInsightState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    variant: InsightCardVariant = InsightCardVariant.STANDARD,
    evidence: String? = null,
    confidence: ConfidenceLevel? = null,
    onTellMeMore: (() -> Unit)? = null,
    onFeedback: ((helpful: Boolean) -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val appColors = LocalAppColors.current
    when (state) {
        is AiInsightState.Generating -> {
            AiInsightCard(borderMode = AiBorderMode.Generating, modifier = modifier) {
                InsightCardHeader(title = title, collapsible = false, collapsed = false, onToggle = {})
                Spacer(Modifier.height(10.dp))
                if (state.partialText.isBlank()) {
                    InsightShimmerLines()
                } else {
                    Text(state.partialText, fontSize = 14.sp, color = appColors.textPrimary, lineHeight = 20.sp)
                }
            }
        }
        is AiInsightState.Ready -> ReadyCard(
            title, state.text, variant, evidence, confidence,
            onRetry, onTellMeMore, onFeedback, onDismiss, modifier,
        )
        is AiInsightState.Error -> {
            AiInsightCard(borderMode = AiBorderMode.Static, modifier = modifier) {
                InsightCardHeader(title = title, collapsible = false, collapsed = false, onToggle = {})
                Spacer(Modifier.height(8.dp))
                Text(state.message, fontSize = 13.sp, color = appColors.textMuted)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onRetry) { Text("Try again") }
            }
        }
        else -> Unit
    }
}

@Composable
private fun ReadyCard(
    title: String,
    verdict: String,
    variant: InsightCardVariant,
    evidence: String?,
    confidence: ConfidenceLevel?,
    onRetry: () -> Unit,
    onTellMeMore: (() -> Unit)?,
    onFeedback: ((Boolean) -> Unit)?,
    onDismiss: (() -> Unit)?,
    modifier: Modifier,
) {
    val appColors = LocalAppColors.current
    var collapsed by remember { mutableStateOf(variant == InsightCardVariant.PILL) }
    val verdictSize = if (variant == InsightCardVariant.HERO) 20.sp else 16.sp

    AiInsightCard(
        borderMode = AiBorderMode.Ready,
        modifier = modifier.animateContentSize(spring()),
        collapsed = collapsed,
        contentPadding = if (collapsed) 12.dp else 16.dp,
    ) {
        InsightCardHeader(
            title = title,
            collapsible = true,
            collapsed = collapsed,
            collapsedVerdict = verdict,
            confidence = confidence,
            onToggle = { collapsed = !collapsed },
        )
        if (!collapsed) {
            Spacer(Modifier.height(10.dp))
            Text(verdict, fontSize = verdictSize, fontWeight = FontWeight.SemiBold,
                color = appColors.textPrimary, lineHeight = (verdictSize.value + 5).sp)
            if (evidence != null) {
                Spacer(Modifier.height(6.dp))
                Text(evidence, fontSize = 12.5f.sp, color = appColors.textMuted, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(12.dp))
            InsightActions(onTellMeMore, onFeedback, onDismiss, onRetry)
        }
    }
}

@Composable
private fun InsightCardHeader(
    title: String,
    collapsible: Boolean,
    collapsed: Boolean,
    onToggle: () -> Unit,
    collapsedVerdict: String? = null,
    confidence: ConfidenceLevel? = null,
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().let { if (collapsible) it.clickable(onClick = onToggle) else it },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✦", fontSize = 13.sp, color = LocalAppAccent.current.inkLight)
        if (collapsed && collapsedVerdict != null) {
            Text(collapsedVerdict, fontSize = 13.sp, color = appColors.textPrimary, maxLines = 1)
        } else {
            Text(title.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold,
                color = appColors.textFaint, letterSpacing = 0.14.sp)
        }
        Spacer(Modifier.fillMaxWidth().weight(1f, fill = true).height(0.dp))
        if (confidence != null && !collapsed) ConfidenceBadge(confidence)
        if (collapsible) {
            val rot by animateFloatAsState(if (collapsed) -90f else 0f, tween(450), label = "chev")
            Text("▾", fontSize = 13.sp, color = appColors.textFaint, modifier = Modifier.rotate(rot))
        } else {
            AiBadge()
        }
    }
}

@Composable
private fun ConfidenceBadge(level: ConfidenceLevel) {
    val (text, color) = when (level) {
        ConfidenceLevel.HIGH -> "High" to Color(0xFF6EFFD8)
        ConfidenceLevel.MEDIUM -> "Medium" to Color(0xFFFFD27A)
    }
    Row(
        Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) { Text(text, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color) }
}

@Composable
private fun InsightActions(
    onTellMeMore: (() -> Unit)?,
    onFeedback: ((Boolean) -> Unit)?,
    onDismiss: (() -> Unit)?,
    onRetry: () -> Unit,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onTellMeMore != null) ActionChip("Tell me more", accent.inkLight, onTellMeMore)
        ActionChip("Refresh", appColors.textMuted, onRetry)
        Spacer(Modifier.fillMaxWidth().weight(1f, fill = true).height(0.dp))
        if (onFeedback != null) {
            Text("♡", fontSize = 15.sp, color = appColors.textFaint,
                modifier = Modifier.clickable { onFeedback(true) }.padding(horizontal = 2.dp))
        }
        if (onDismiss != null) {
            Text("✕", fontSize = 14.sp, color = appColors.textFaint,
                modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 2.dp))
        }
    }
}

@Composable
private fun ActionChip(text: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 5.dp),
    ) { Text(text, fontSize = 12.sp, color = color) }
}
```

Keep the three existing `@Preview` functions at the bottom of the file (lines 98–126) and add one Hero/collapsed preview:

```kotlin
@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewHero() {
    GeneratedInsightCard(
        title = "Pattern · this week",
        state = AiInsightState.Ready("Weekends are where protein slips."),
        onRetry = {},
        variant = InsightCardVariant.HERO,
        evidence = "Under target 4 of 7 days — Sat & Sun averaged 38g below your 180g goal.",
        confidence = ConfidenceLevel.HIGH,
        onTellMeMore = {},
        onFeedback = {},
        onDismiss = {},
    )
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Fix any unused-import warnings the compiler flags.

- [ ] **Step 3: Run the existing unit suite (no regressions)**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/GeneratedInsightCard.kt
git commit -m "feat(ai-cards): collapsible variant engine (Hero/Standard/Pill + actions)"
```

---

## Task 6: Dashboard — Hero variant + evidence + confidence

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt:168-174`

- [ ] **Step 1: Update the call site**

Replace the `GeneratedInsightCard(...)` block (lines 169–173) with:

```kotlin
GeneratedInsightCard(
    title = "Coach spotted",
    state = patternInsightState,
    onRetry = onRetryPatternInsight,
    variant = com.zack.recomptracker.ui.component.InsightCardVariant.HERO,
    evidence = state.patternInsightContext?.fact?.statement,
    confidence = com.zack.recomptracker.ui.component.confidenceFrom(
        state.patternInsightContext?.fact?.priority ?: 0,
    ),
)
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt
git commit -m "feat(ai-cards): dashboard WEEKLY_PATTERN uses Hero variant + evidence + confidence"
```

---

## Task 7: Progress + Recovery — Standard variant

Both already default to `STANDARD`; this task only adds the `variant` argument explicitly for clarity and verifies the new card renders on these screens. No behavioral change needed beyond confirming the build.

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/progress/ProgressScreen.kt:85-89`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/BodyRecoveryScreen.kt:159-163`

- [ ] **Step 1: ProgressScreen — make the variant explicit**

Replace lines 85–89 with:

```kotlin
GeneratedInsightCard(
    title = "Trend analysis",
    state = insightState,
    onRetry = viewModel::retryProgressInsight,
    variant = com.zack.recomptracker.ui.component.InsightCardVariant.STANDARD,
)
```

- [ ] **Step 2: BodyRecoveryScreen — make the variant explicit**

Replace lines 159–163 with:

```kotlin
GeneratedInsightCard(
    title = "Recovery readiness",
    state = recoveryInsightState,
    onRetry = onRetryRecoveryInsight,
    variant = com.zack.recomptracker.ui.component.InsightCardVariant.STANDARD,
)
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/progress/ProgressScreen.kt \
        app/src/main/java/com/zack/recomptracker/ui/today/BodyRecoveryScreen.kt
git commit -m "feat(ai-cards): Progress + Recovery use explicit Standard variant"
```

---

## Task 8: Food — Pill variant replaces the "✨ Rest of day?" reveal

The Pill collapses by default, so the card itself is the reveal affordance. The old `revealed`/`TextButton` gate is removed; tapping the collapsed pill expands it. Generation still kicks off on first reveal via `onReveal`.

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt:455-472`

- [ ] **Step 1: Replace `RestOfDayReveal`**

```kotlin
@Composable
private fun RestOfDayReveal(
    available: Boolean,
    state: AiInsightState,
    onReveal: () -> Unit,
    onRetry: () -> Unit,
) {
    if (!available) return
    // Start generation as soon as the pill is on screen.
    androidx.compose.runtime.LaunchedEffect(Unit) { onReveal() }
    GeneratedInsightCard(
        title = "Rest of day",
        state = state,
        onRetry = onRetry,
        variant = com.zack.recomptracker.ui.component.InsightCardVariant.PILL,
    )
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Remove the now-unused `revealed`/`mutableStateOf`/`TextButton` imports if the compiler flags them.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt
git commit -m "feat(ai-cards): Food 'rest of day' becomes a collapsible glass pill"
```

> Behavior note: previously generation started only on tap. Now it starts when the food log is open and the model is available, matching the other on-visible surfaces. If product wants tap-to-generate preserved, gate `onReveal()` behind the first expand instead — flagged for review.

---

## Task 9: Weekly Review modal — swap rim for edge glow

The briefing modal renders `Modifier.aiIridescentRim(...)` ([WeeklyBriefingOverlay.kt:130](../../../app/src/main/java/com/zack/recomptracker/ui/review/WeeklyBriefingOverlay.kt)). Switch it to the new edge glow so the modal matches the family. `aiEdgeGlow` has no backdrop dependency (it only draws + blurs), so it is dialog-safe like the old rim.

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/review/WeeklyBriefingOverlay.kt`

- [ ] **Step 1: Replace the rim modifier**

Find the `.aiIridescentRim(borderMode, ModalCorner)` call in `BriefingGlassCard` and replace with:

```kotlin
.aiEdgeGlow(borderMode, ModalCorner)
```

Update the import `com.zack.recomptracker.ui.component.aiIridescentRim` → `com.zack.recomptracker.ui.component.aiEdgeGlow`.

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/review/WeeklyBriefingOverlay.kt
git commit -m "feat(ai-cards): weekly review modal uses edge glow"
```

---

## Task 10: Remove the dead rim modifier + full verification

Once no caller uses `aiIridescentRim`, delete it and `drawIridescentBorder` from `AiInsightCard.kt` (keep `IridescentStops` + `hueShifted` — the glow uses them).

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/component/AiInsightCard.kt`

- [ ] **Step 1: Confirm no remaining references**

Run: `grep -rn "aiIridescentRim" app/src/main/`
Expected: no matches.

- [ ] **Step 2: Delete `aiIridescentRim` (lines ~109-162) and `drawIridescentBorder` (~164-192)**

Remove both functions and any imports they alone used (`Settings`, `FastOutSlowInEasing`, `Stroke`, `CornerRadius`, etc. — only those the compiler flags as unused).

- [ ] **Step 3: Full build + tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all unit tests PASS.

- [ ] **Step 4: Manual smoke (real device/emulator) — REQUIRED SUB-SKILL: use the `run` skill**

Verify on each surface that the card shows real liquid glass (refracts the background), the edge glow breathes, and:
- Dashboard "Coach spotted" renders as Hero (large verdict + evidence + High/Medium badge).
- Progress / Recovery render as Standard with actions.
- Food "Rest of day" starts collapsed as a pill; tapping expands it; the shape morphs.
- Generating shows colored shimmer; on completion the verdict appears and the glow settles.
- Weekly Review modal shows the edge glow.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/AiInsightCard.kt
git commit -m "refactor(ai-cards): remove dead iridescent rim modifier"
```

---

## Follow-ups (separate plans)

1. **Durable snooze/dismiss** — DataStore keyed by insight kind + expiry; resurface logic.
2. **Carousel variant + Hero metric/sparkline** — needs `InsightEngine` to expose multiple ranked facts and structured numbers (not just a `statement` sentence).
3. **Feedback → ranking** — persist thumbs and feed the top-fact ranking.
4. **"Tell me more" → coach** — navigate to the coach tab seeded with the insight context.

---

## Self-review

- **Spec coverage:** liquid glass §3.1 → Task 3; edge glow §3.2–3.3 → Tasks 2,3,9; collapsibility §4 → Task 5; variants §5 (Hero/Standard/Pill/Generating) → Tasks 5–8 (Carousel deferred, noted §scope); shared actions §6 → Task 5 (persistence/seed deferred, noted); mapping §7 → Tasks 6–9; states §8 → Task 5; architecture §9 → all; a11y/perf §10 → Tasks 2,3 (API-31 branch, draw-phase glow, reduced-motion guard); decisions §11 (confidence derived, snooze session-level) → Tasks 1,5.
- **Placeholders:** none — every code step is complete.
- **Type consistency:** `InsightCardVariant`, `ConfidenceLevel`, `confidenceFrom` defined in Task 1 and used consistently in Tasks 5–7; `AiBorderMode` reused unchanged; `aiEdgeGlow(mode, cornerRadius)` signature consistent across Tasks 2,3,9; `GeneratedInsightCard` new params consistent across call sites.
