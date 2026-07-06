package com.zack.recomptracker.ui.train.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Weekly training-STATUS heat, keyed on a muscle's ABSOLUTE weekly set count (not a relative share):
//   0 sets      → faint / uncolored (callers render this — never passed here)
//   building    → dim green → bright green as volume climbs into the productive range
//   optimal     → a bright-green plateau (the healthy weekly-volume band)
//   overtrained → green → amber → red once volume runs past what recovers well
// Green means "trained well", red means "you're piling on too much". A green/red hue, distinct from
// the violet tap-selection, so the two never clash on the body map.
//
// Anchors are per-muscle-group weekly sets, loosely following common volume guidance (~10-20 sets
// productive for most groups, 24+ where recovery starts to suffer).
private const val GREEN_LOW = 10f   // reach full green by here
private const val GREEN_HIGH = 18f  // still comfortably green up to here (healthy band)
private const val OVERTRAIN = 26f   // full red at / past here

private val TrainedDim = Color(0xFF2F6B4E)  // barely trained — dim green
private val TrainedGood = Color(0xFF43D07A) // well trained — bright green (optimal)
private val OverAmber = Color(0xFFE0A73E)   // creeping past optimal — amber
private val OverRed = Color(0xFFE0574B)      // overtrained — red

/**
 * Maps a muscle's ABSOLUTE weekly [weeklySets] to a training-status colour: dim→bright green through
 * the productive range, a green plateau across the healthy band, then green→amber→red into
 * overtraining. Callers render 0 sets faint (uncolored) — this should only be called with >0.
 */
fun heatColor(weeklySets: Float): Color {
    val s = weeklySets.coerceAtLeast(0f)
    return when {
        s <= GREEN_LOW -> lerp(TrainedDim, TrainedGood, (s / GREEN_LOW).coerceIn(0f, 1f))
        s <= GREEN_HIGH -> TrainedGood
        else -> {
            val over = ((s - GREEN_HIGH) / (OVERTRAIN - GREEN_HIGH)).coerceIn(0f, 1f)
            if (over < 0.5f) lerp(TrainedGood, OverAmber, over / 0.5f)
            else lerp(OverAmber, OverRed, (over - 0.5f) / 0.5f)
        }
    }
}
