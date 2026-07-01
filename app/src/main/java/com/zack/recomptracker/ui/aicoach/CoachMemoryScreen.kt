package com.zack.recomptracker.ui.aicoach

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ui.component.GlassInputField
import com.zack.recomptracker.ui.component.NeutralCard
import com.zack.recomptracker.ui.component.ScreenScaffold
import com.zack.recomptracker.ui.component.SubScreenHeader
import com.zack.recomptracker.ui.liquidglass.LiquidActionButton
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppColors

@Composable
fun CoachMemoryScreen(viewModel: CoachMemoryViewModel, onBack: () -> Unit) {
    val appColors = LocalAppColors.current
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }

    ScreenScaffold(withNavBarInset = false) {
        item { SubScreenHeader(title = "Coach memory", onBack = onBack) }
        item {
            Text(
                text = "Facts the coach remembers about you. It reads these in every chat — add, edit, or remove anything.",
                style = AppType.cardSubtitle,
                color = appColors.textSecondary,
            )
        }
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                GlassInputField(
                    label = "Add a fact",
                    value = draft,
                    onValueChange = { draft = it },
                    keyboardType = KeyboardType.Text,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                LiquidActionButton(
                    text = "Add",
                    onClick = {
                        if (draft.isNotBlank()) {
                            viewModel.add(draft)
                            draft = ""
                        }
                    },
                    isPrimary = true,
                    small = true,
                )
            }
        }
        if (entries.isEmpty()) {
            item {
                Text(
                    text = "The coach doesn't know anything about you yet — add a fact above, or tell it in chat (\"remember I'm vegetarian\").",
                    style = AppType.cardSubtitle,
                    color = appColors.textMuted,
                )
            }
        } else {
            items(entries, key = { it.id }) { entry ->
                var editing by remember(entry.id) { mutableStateOf(entry.text) }
                val dirty = editing.isNotBlank() && editing.trim() != entry.text
                NeutralCard {
                    Row(verticalAlignment = Alignment.Bottom) {
                        GlassInputField(
                            label = "",
                            value = editing,
                            onValueChange = { editing = it },
                            keyboardType = KeyboardType.Text,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete",
                            tint = appColors.textVeryMuted,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(20.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { viewModel.delete(entry.id) },
                                ),
                        )
                    }
                    if (dirty) {
                        Spacer(Modifier.height(10.dp))
                        LiquidActionButton(
                            text = "Save",
                            onClick = { viewModel.update(entry.id, editing) },
                            isPrimary = true,
                            small = true,
                        )
                    }
                }
            }
        }
    }
}
