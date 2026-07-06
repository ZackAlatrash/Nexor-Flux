package com.zack.recomptracker.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.theme.LocalAppColors

/**
 * Compact 32dp circular close affordance — not the 48dp min touch target — for low-stakes,
 * reversible dismiss actions (e.g. clearing a coaching card, closing an AI dialog header). Shared
 * so every AI-surface dismiss control looks and behaves identically.
 */
@Composable
fun DismissButton(
    onDismiss: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onDismiss)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = null,
            tint = appColors.textVeryMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}
