package com.zack.recomptracker.domain.workout

/**
 * Moves the element whose key equals [fromKey] to the current index of the element
 * whose key equals [toKey], shifting the others. Pure, in-place, no Android deps.
 *
 * @return true if both keys were found and the move was applied; false otherwise
 *         (list left untouched).
 */
fun <T> MutableList<T>.moveByKey(fromKey: Any?, toKey: Any?, keyOf: (T) -> Any?): Boolean {
    val from = indexOfFirst { keyOf(it) == fromKey }
    val to = indexOfFirst { keyOf(it) == toKey }
    if (from == -1 || to == -1 || from == to) return false
    add(to, removeAt(from))
    return true
}
