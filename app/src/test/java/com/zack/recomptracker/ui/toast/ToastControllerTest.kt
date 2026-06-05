package com.zack.recomptracker.ui.toast

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ToastControllerTest {

    @Test
    fun `show emits message to flow`() = runTest {
        val controller = ToastController()
        val received = mutableListOf<ToastMessage>()
        val job = launch { controller.messages.take(1).toList(received) }
        controller.show(ToastMessage("Plan saved", ToastType.Success))
        job.join()
        assertEquals(1, received.size)
        assertEquals("Plan saved", received[0].text)
        assertEquals(ToastType.Success, received[0].type)
    }

    @Test
    fun `show preserves action label`() = runTest {
        val controller = ToastController()
        val received = mutableListOf<ToastMessage>()
        val job = launch { controller.messages.take(1).toList(received) }
        controller.show(ToastMessage("Could not save", ToastType.Error, actionLabel = "Retry"))
        job.join()
        assertEquals("Retry", received[0].actionLabel)
    }

    @Test
    fun `multiple messages are queued in order`() = runTest {
        val controller = ToastController()
        val received = mutableListOf<ToastMessage>()
        val job = launch { controller.messages.take(2).toList(received) }
        controller.show(ToastMessage("First", ToastType.Info))
        controller.show(ToastMessage("Second", ToastType.Success))
        job.join()
        assertEquals("First", received[0].text)
        assertEquals("Second", received[1].text)
    }
}
