package com.zack.recomptracker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

/** Top corner radius for the app's bottom sheets. Larger than cards so it reads as a sheet. */
private val SheetCorner = 26.dp

/**
 * App-themed bottom sheet. A thin skin over Material [ModalBottomSheet] (keeping its
 * drag-to-dismiss, scrim, animation, and — importantly — its native nested-scroll handling for
 * scrollable content): the sheet's own container is painted as a solid panel tinted by the current
 * accent theme, the scrim is the app scrim, the top corners are rounded, and a slim grab handle is
 * supplied via the `dragHandle` slot.
 *
 * The caller's [content] is the sheet's DIRECT content (a `ColumnScope`), so a scrollable child
 * (LazyColumn / verticalScroll Column) participates in the sheet's nested scroll cleanly. Do NOT
 * re-wrap the content in another height-wrapping container — that breaks the sheet's measurement
 * and makes scrollable content jitter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    // Solid panel tinted by the current accent theme (dark-tinted in dark mode, light-tinted in
    // light mode) so the sheet matches whatever theme is active instead of always reading purple.
    val panelColor = lerp(
        if (appColors.isDark) Color.Black else Color.White,
        accent.accent,
        if (appColors.isDark) 0.16f else 0.10f,
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner),
        containerColor = panelColor,
        scrimColor = appColors.scrim,
        dragHandle = { GlassGrabHandle() },
        content = content,
    )
}

@Composable
private fun GlassGrabHandle() {
    val appColors = LocalAppColors.current
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(100))
                .background(appColors.textVeryMuted),
        )
    }
}
