package com.zack.recomptracker.ui.train.component

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppColors
import com.zack.recomptracker.ui.train.PrCalloutFormatter
import kotlinx.coroutines.delay

/** How long the banner stays on screen before auto-dismissing. */
private const val PR_BANNER_VISIBLE_MS = 3500L

/**
 * Transient celebratory PR banner shown over the active-session screen when a completed set beats the
 * best-ever e1RM for its exercise. Reuses the app's gold celebration skin (`celebration*` tokens +
 * trophy) so it matches the Today's-Coaching "a win" card, and [PrCalloutFormatter] for the copy.
 *
 * Stateless: the caller owns the "is a PR pending" state and clears it via [onDismiss]. Auto-dismisses
 * after ~3.5s. Slide+fade honour the system reduced-motion setting (crossfade only when motion is off).
 * Non-blocking — this sits above the content but never intercepts touches, so logging continues.
 *
 * @param headline  the PR headline (e.g. "New Bench Press record — 82×5"); null hides the banner.
 * @param onDismiss called when the banner should disappear (auto-timeout).
 */
@Composable
fun PrBanner(
    headline: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val context = LocalContext.current
    val animationsEnabled = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }

    // Auto-dismiss timer, keyed on the headline so each new PR restarts the clock.
    LaunchedEffect(headline) {
        if (headline != null) {
            delay(PR_BANNER_VISIBLE_MS)
            onDismiss()
        }
    }

    val enter = if (animationsEnabled) {
        slideInVertically(initialOffsetY = { -it }) + fadeIn()
    } else {
        fadeIn()
    }
    val exit = if (animationsEnabled) {
        slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    } else {
        fadeOut()
    }

    AnimatedVisibility(
        visible = headline != null,
        enter = enter,
        exit = exit,
        modifier = modifier,
    ) {
        // Keep the last non-null headline during the exit animation so the text doesn't blank out.
        val shown = remember(headline) { headline }
        FrostedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentPadding = 12.dp,
            surfaceTint = appColors.celebrationSurface,
            borderColor = appColors.celebrationBorder,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.EmojiEvents,
                    contentDescription = null,
                    tint = appColors.celebrationInk,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "New personal record".uppercase(),
                        style = AppType.sectionLabel,
                        color = appColors.celebrationInk,
                    )
                    Text(
                        text = shown.orEmpty(),
                        style = AppType.cardTitle,
                        color = appColors.textPrimary,
                    )
                }
            }
        }
    }
}
