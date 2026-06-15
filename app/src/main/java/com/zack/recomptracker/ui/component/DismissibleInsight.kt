package com.zack.recomptracker.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

/**
 * Session-level dismiss wrapper for an insight card. When the user dismisses [content], it is
 * replaced by a compact "Insight dismissed · Undo" row; Undo restores it. The dismissed flag is
 * remembered for the session (survives recomposition/scroll); navigating away resets it.
 * Durable cross-session persistence is a deferred follow-up.
 */
@Composable
fun DismissibleInsight(
    modifier: Modifier = Modifier,
    content: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    var dismissed by rememberSaveable { mutableStateOf(false) }
    if (dismissed) {
        val appColors = LocalAppColors.current
        val accent = LocalAppAccent.current
        Row(
            modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Insight dismissed", fontSize = 12.sp, color = appColors.textMuted)
            TextButton(onClick = { dismissed = false }) {
                Text("Undo", fontSize = 12.sp, color = accent.inkLight)
            }
        }
    } else {
        content { dismissed = true }
    }
}
