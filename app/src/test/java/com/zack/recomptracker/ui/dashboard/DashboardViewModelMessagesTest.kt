package com.zack.recomptracker.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardViewModelMessagesTest {

    @Test
    fun motivationalMessagesListIsNotEmpty() {
        val messages = DashboardViewModel.MOTIVATIONAL_MESSAGES
        assertFalse("Message list must not be empty", messages.isEmpty())
    }

    @Test
    fun everyMessageIsNonBlank() {
        DashboardViewModel.MOTIVATIONAL_MESSAGES.forEachIndexed { index, msg ->
            assertFalse("Message at index $index is blank", msg.isBlank())
        }
    }

    @Test
    fun defaultUiStateHasEmptyMessage() {
        val state = DashboardUiState()
        assertTrue("Default motivationalMessage should be empty", state.motivationalMessage.isEmpty())
    }

    @Test
    fun allMessagesAreUnique() {
        val messages = DashboardViewModel.MOTIVATIONAL_MESSAGES
        assertEquals(messages.size, messages.toSet().size)
    }
}
