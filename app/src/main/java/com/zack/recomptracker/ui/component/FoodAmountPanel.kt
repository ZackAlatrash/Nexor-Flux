package com.zack.recomptracker.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.domain.food.FoodMacros
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppColors

/**
 * The shared "choose an amount and log" panel body. Rendered identically by the food-library
 * amount sheet and the barcode-scan product sheet so both look and behave the same: a title,
 * a supporting line, an optional Servings/Grams toggle, a +/- amount stepper, a live-updating
 * macro preview, an optional message, and a screen-supplied action button area.
 *
 * Callers wrap this in [GlassBottomSheet] and supply their own [actions] (the two flows differ:
 * the library logs an already-saved food; the scanner can also save the scanned food to the
 * library), but everything above the actions is identical.
 */
@Composable
fun FoodAmountPanel(
    title: String,
    subtitle: String,
    showServingToggle: Boolean,
    servingsSelected: Boolean,
    onModeSelected: (servings: Boolean) -> Unit,
    amountValue: String,
    onAmountChange: (String) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    amountSuffix: String,
    preview: FoodMacros?,
    message: String?,
    messageKind: MessageKind,
    warning: String? = null,
    actions: @Composable ColumnScope.() -> Unit,
) {
    val appColors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = AppType.screenTitleCompact, color = appColors.textPrimary)
        Text(
            subtitle,
            style = AppType.cardSubtitle,
            color = appColors.textMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (warning != null) {
            Text(warning, style = AppType.cardSubtitle, color = MaterialTheme.colorScheme.error)
        }
        if (showServingToggle) {
            GlassSegmentedToggle(
                options = listOf("Servings", "Grams"),
                selectedIndex = if (servingsSelected) 0 else 1,
                onSelect = { onModeSelected(it == 0) },
            )
        }
        AmountStepper(
            value = amountValue,
            onValueChange = onAmountChange,
            onMinus = onMinus,
            onPlus = onPlus,
            caption = "",
            suffix = amountSuffix,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountPreviewStat("kcal", preview?.calories?.toString() ?: "—", Modifier.weight(1f))
            AmountPreviewStat("P", preview?.proteinG?.toInt()?.toString() ?: "—", Modifier.weight(1f))
            AmountPreviewStat("C", preview?.carbsG?.toInt()?.toString() ?: "—", Modifier.weight(1f))
            AmountPreviewStat("F", preview?.fatG?.toInt()?.toString() ?: "—", Modifier.weight(1f))
        }
        MessageText(message, messageKind)
        actions()
    }
}
