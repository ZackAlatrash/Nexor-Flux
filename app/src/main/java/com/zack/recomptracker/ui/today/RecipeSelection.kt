package com.zack.recomptracker.ui.today

import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import com.zack.recomptracker.data.repository.toRecipeIngredient

/** Active "save as recipe" selection within a single meal slot. */
data class RecipeSelection(
    val slotId: Long,
    val selectedIds: Set<Long>,
)

/**
 * Maps the entries ticked in [selection]'s slot into recipe ingredients, preserving the
 * slot's display order and assigning sequential sortOrder. Returns empty when nothing is
 * selected or the slot is gone.
 */
fun recipeIngredientsFromSelection(
    slots: List<MealSlotWithEntries>,
    selection: RecipeSelection?,
): List<RecipeIngredientEntity> {
    if (selection == null || selection.selectedIds.isEmpty()) return emptyList()
    val slot = slots.firstOrNull { it.slot.id == selection.slotId } ?: return emptyList()
    return slot.entries
        .filter { it.id in selection.selectedIds }
        .mapIndexed { index, entry -> entry.toRecipeIngredient(index) }
}
