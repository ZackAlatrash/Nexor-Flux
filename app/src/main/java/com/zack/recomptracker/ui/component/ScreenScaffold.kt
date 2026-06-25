package com.zack.recomptracker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppColors
import com.zack.recomptracker.ui.theme.ScreenPaddingH
import com.zack.recomptracker.ui.theme.ScreenSpacing

/**
 * Standard screen frame. Wraps a LazyColumn with the canonical horizontal padding,
 * inter-section spacing, and a bottom inset that clears the floating nav bar.
 *
 * @param withNavBarInset true for tab destinations (reserves space for the floating nav);
 *        false for pushed sub-screens that have no nav bar.
 */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    withNavBarInset: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ScreenPaddingH,
                end = ScreenPaddingH,
                top = 4.dp,
                bottom = if (withNavBarInset) FloatingNavHeight + 16.dp else 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(ScreenSpacing),
            content = content,
        )
    }
}

/**
 * Tier-1 header for top-level tab destinations. Big display title, optional subtitle,
 * optional back button (for hub destinations reached by a push, e.g. More), optional
 * trailing slot (avatar / action).
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) BackButton(onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = AppType.screenTitle, color = appColors.textPrimary)
            if (subtitle != null) {
                Text(text = subtitle, style = AppType.screenSubtitle, color = appColors.textMuted)
            }
        }
        if (trailing != null) trailing()
    }
}

/**
 * Tier-2 header for pushed sub-screens. Standard back button + compact title, optional
 * subtitle, optional trailing slot.
 */
@Composable
fun SubScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BackButton(onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = AppType.screenTitleCompact, color = appColors.textPrimary)
            if (subtitle != null) {
                Text(text = subtitle, style = AppType.screenSubtitle, color = appColors.textMuted)
            }
        }
        if (trailing != null) trailing()
    }
}

/** Canonical 40dp circular back button used by every sub-screen header. */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val appColors = LocalAppColors.current
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .then(if (!enabled) Modifier.alpha(0.38f) else Modifier)
            .clip(CircleShape)
            .background(appColors.cardSurface)
            .border(1.dp, appColors.cardBorder, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Navigate back", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = appColors.textPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}
