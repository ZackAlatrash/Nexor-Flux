package com.zack.recomptracker.data.coach

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.zack.recomptracker.core.time.DateProvider
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachMemoryStoreTest {
    private val tmp = File.createTempFile("coach_memory_test", ".preferences_pb").apply { delete() }
    private fun newStore(): CoachMemoryStore {
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(produceFile = { tmp })
        return CoachMemoryStore(ds, object : DateProvider { override fun today() = LocalDate.of(2026, 6, 5) })
    }
    @After fun cleanup() { tmp.delete() }

    @Test fun `add stores a trimmed entry and returns it`() = runTest {
        val store = newStore()
        val e = store.add("  Vegetarian  ")
        assertEquals("Vegetarian", e!!.text)
        assertEquals(listOf("Vegetarian"), store.all().map { it.text })
    }

    @Test fun `add ignores blank text`() = runTest {
        val store = newStore()
        assertNull(store.add("   "))
        assertTrue(store.all().isEmpty())
    }

    @Test fun `add dedupes case-insensitive duplicates`() = runTest {
        val store = newStore()
        store.add("Vegetarian")
        store.add("vegetarian")
        assertEquals(1, store.all().size)
    }

    @Test fun `update changes an entry's text`() = runTest {
        val store = newStore()
        val e = store.add("Bad knee")!!
        store.update(e.id, "Bad left knee — no barbell squats")
        assertEquals("Bad left knee — no barbell squats", store.all().single().text)
    }

    @Test fun `delete removes by id`() = runTest {
        val store = newStore()
        val e = store.add("Trains at home")!!
        store.delete(e.id)
        assertTrue(store.all().isEmpty())
    }

    @Test fun `removeMatching removes the best word match and returns it`() = runTest {
        val store = newStore()
        store.add("Vegetarian")
        store.add("Trains at home")
        val removed = store.removeMatching("i am vegetarian")
        assertEquals("Vegetarian", removed!!.text)
        assertEquals(listOf("Trains at home"), store.all().map { it.text })
    }

    @Test fun `removeMatching returns null when nothing matches`() = runTest {
        val store = newStore()
        store.add("Vegetarian")
        assertNull(store.removeMatching("deadlift PR"))
        assertEquals(1, store.all().size)
    }
}
