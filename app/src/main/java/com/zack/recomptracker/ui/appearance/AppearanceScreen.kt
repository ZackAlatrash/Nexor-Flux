package com.zack.recomptracker.ui.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.component.AccentThemePicker
import com.zack.recomptracker.ui.component.FontPicker
import com.zack.recomptracker.ui.component.ThemeModePicker
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.TintedCard
import com.zack.recomptracker.ui.component.VioletBadge
import com.zack.recomptracker.ui.liquidglass.LiquidPrimaryButton
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

/**
 * Appearance settings: merges font selection and accent-colour selection. Both write to the
 * global [com.zack.recomptracker.data.preferences.UiPreferences], so the live preview (and the
 * whole app) update immediately when the accent or font changes.
 */
@Composable
fun AppearanceScreen(
    viewModel: AppearanceViewModel,
    onBack: () -> Unit,
) {
    val font by viewModel.font.collectAsStateWithLifecycle()
    val accent by viewModel.accent.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val theme = LocalAppAccent.current
    val appColors = LocalAppColors.current

    val ambientOrb1 = remember(theme.accent) {
        Brush.radialGradient(listOf(theme.accent.copy(alpha = 0.15f), Color.Transparent))
    }
    val ambientOrb2 = remember(theme.accentLight) {
        Brush.radialGradient(listOf(theme.accentLight.copy(alpha = 0.08f), Color.Transparent))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = (-80).dp, y = (-60).dp)
                .background(ambientOrb1),
        )
        Box(
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 60.dp, y = (-180).dp)
                .background(ambientOrb2),
        )

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = FloatingNavHeight + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ── Header ────────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = appColors.textPrimary,
                        )
                    }
                    Text(
                        text = "Appearance",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = appColors.textPrimary,
                        letterSpacing = (-0.8).sp,
                    )
                }
            }

            // ── Live preview ──────────────────────────────────────────────────
            item {
                TintedCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Preview",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = appColors.textPrimary,
                            letterSpacing = (-0.4).sp,
                        )
                        VioletBadge(text = "ACCENT")
                    }
                    Text(
                        text = "This is how buttons, badges, and highlights look with your current accent.",
                        fontSize = 12.sp,
                        color = appColors.textMuted,
                    )
                    LiquidPrimaryButton(
                        text = "Sample button",
                        onClick = {},
                    )
                }
            }

            // ── Theme mode ────────────────────────────────────────────────────
            item { SectionLabel("Theme") }
            item {
                ThemeModePicker(selected = themeMode, onSelect = viewModel::setThemeMode)
            }

            // ── Font ──────────────────────────────────────────────────────────
            item { SectionLabel("Font") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FontPicker(selected = font, onSelect = viewModel::setFont)
                }
            }

            // ── Accent color ──────────────────────────────────────────────────
            item { SectionLabel("Accent color") }
            item {
                AccentThemePicker(selected = accent, onSelect = viewModel::setAccent)
            }
        }
    }
}
