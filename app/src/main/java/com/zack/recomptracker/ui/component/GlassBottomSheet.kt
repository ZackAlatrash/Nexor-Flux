package com.zack.recomptracker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.theme.LocalAppColors

/** Top corner radius for the app's bottom sheets. Larger than cards so it reads as a sheet. */
private val SheetCorner = 26.dp

/**
 * App-themed bottom sheet. Wraps the Material [ModalBottomSheet] (keeping its drag-to-dismiss,
 * scrim, and animation) but skins it as frosted glass: transparent Material container, the app's
 * [scrim][com.zack.recomptracker.ui.theme.AppColors.scrim], a frosted surface with a hairline
 * top border, rounded top corners, and a slim grab handle — so popups match [FrostedCard] and the
 * rest of the app instead of stock Material.
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
    val shape = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = shape,
        containerColor = Color.Transparent,
        scrimColor = appColors.scrim,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                // Solid themed base (adapts to dark/light + the chosen accent theme) so the
                // panel reads consistently over any of the colourful theme backgrounds, with a
                // thin frosted veil on top for a hint of glass.
                .background(MaterialTheme.colorScheme.surface)
                .background(appColors.frostedSurface)
                .border(1.dp, appColors.frostedBorder, shape)
                .padding(top = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(100))
                    .background(appColors.textVeryMuted),
            )
            content()
        }
    }
}
