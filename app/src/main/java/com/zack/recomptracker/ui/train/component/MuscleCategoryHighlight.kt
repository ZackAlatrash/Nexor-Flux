package com.zack.recomptracker.ui.train.component

import com.zack.recomptracker.domain.workout.MuscleCategory

/** Slugs to fill with the accent on each view for a given category. Slugs match body_*.json. */
data class CategoryHighlight(val front: Set<String>, val back: Set<String>)

/**
 * Real asset slugs per category.
 * Front slugs available: abs, adductors, biceps, calves, chest, deltoids, forearm, neck, obliques,
 *   quadriceps, trapezius, triceps, tibialis (+ non-muscle: ankles, feet, hair, hands, head, knees).
 * Back slugs available: adductors, calves, deltoids, forearm, gluteal, hamstring, lower-back, neck,
 *   trapezius, triceps, upper-back (+ non-muscle: ankles, feet, hair, hands, head).
 */
private val HIGHLIGHTS: Map<MuscleCategory, CategoryHighlight> = mapOf(
    MuscleCategory.CHEST to CategoryHighlight(front = setOf("chest"), back = emptySet()),
    MuscleCategory.BACK to CategoryHighlight(front = emptySet(), back = setOf("upper-back", "lower-back", "trapezius")),
    MuscleCategory.SHOULDERS to CategoryHighlight(front = setOf("deltoids"), back = setOf("deltoids")),
    MuscleCategory.ARMS to CategoryHighlight(front = setOf("biceps", "triceps", "forearm"), back = setOf("triceps", "forearm")),
    MuscleCategory.LEGS to CategoryHighlight(
        front = setOf("quadriceps", "calves", "adductors"),
        back = setOf("hamstring", "gluteal", "calves", "adductors"),
    ),
    MuscleCategory.CORE to CategoryHighlight(front = setOf("abs", "obliques"), back = emptySet()),
)

fun highlightFor(category: MuscleCategory): CategoryHighlight =
    HIGHLIGHTS[category] ?: CategoryHighlight(emptySet(), emptySet())

/** Reverse lookup: which category a tapped body slug belongs to (null for non-muscle slugs). */
private val SLUG_TO_CATEGORY: Map<String, MuscleCategory> = buildMap {
    HIGHLIGHTS.forEach { (category, h) ->
        (h.front + h.back).forEach { slug -> putIfAbsent(slug, category) }
    }
}

fun categoryForSlug(slug: String): MuscleCategory? = SLUG_TO_CATEGORY[slug]
