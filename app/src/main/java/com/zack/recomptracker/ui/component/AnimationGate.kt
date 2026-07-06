package com.zack.recomptracker.ui.component

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the system's reduced-motion setting allows animations to run. Mirrors the same
 * [Settings.Global.ANIMATOR_DURATION_SCALE] gate used by [aiEdgeGlow] and `PrBanner` — new
 * animations built on top of the AI-card stack should degrade to a fade/snap when this is `false`
 * rather than adding their own ad-hoc check.
 *
 * Not a refactor of the existing gates in `AiEdgeGlow`/`PrBanner` (out of scope here) — this is the
 * shared primitive future call sites should read instead of duplicating the `Settings.Global` call.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
}
