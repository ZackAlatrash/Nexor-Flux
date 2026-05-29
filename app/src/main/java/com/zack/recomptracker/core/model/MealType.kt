package com.zack.recomptracker.core.model

enum class MealType(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack"),
    OTHER("Other"),
    ;

    companion object {
        fun fromStored(value: String): MealType = entries.firstOrNull { it.name == value } ?: OTHER
    }
}
