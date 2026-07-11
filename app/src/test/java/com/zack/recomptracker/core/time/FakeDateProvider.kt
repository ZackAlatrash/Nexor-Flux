package com.zack.recomptracker.core.time

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Test [DateProvider] whose "today" can be advanced to simulate the calendar day rolling over.
 * One [MutableStateFlow] backs both [today] and [todayFlow], so [advanceTo] moves the synchronous
 * read and pushes to collectors at once — exactly what crossing midnight does in production.
 */
class FakeDateProvider(initial: LocalDate) : DateProvider {
    private val flow = MutableStateFlow(initial)
    override fun today(): LocalDate = flow.value
    override fun todayFlow(): Flow<LocalDate> = flow
    fun advanceTo(date: LocalDate) { flow.value = date }
}
