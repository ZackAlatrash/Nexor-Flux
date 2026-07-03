package com.zack.recomptracker.data.coach

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zack.recomptracker.domain.coach.CoachAction
import com.zack.recomptracker.domain.coach.CoachActionType
import com.zack.recomptracker.domain.coach.CoachSignal
import com.zack.recomptracker.domain.coach.CoachSurface
import com.zack.recomptracker.domain.coach.Confidence
import com.zack.recomptracker.domain.coach.SignalCategory
import com.zack.recomptracker.domain.coach.SignalFacts
import com.zack.recomptracker.domain.coach.SignalKind
import com.zack.recomptracker.domain.coach.SignalRationale
import com.zack.recomptracker.domain.coach.SignalTier
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Light Robolectric I/O test: the thin DataStore wiring in [CoachInboxRepository]. The value logic
 * is covered exhaustively (pure) in [CoachInboxSerializationTest]; here we only confirm persistence
 * survives a round-trip and the clear paths work.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [33])
class CoachInboxRepositoryTest {

    private lateinit var context: Context
    private lateinit var repo: CoachInboxRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearStore()
        repo = CoachInboxRepository(context)
    }

    @After
    fun tearDown() = clearStore()

    private fun clearStore() {
        File(context.filesDir, "datastore").deleteRecursively()
    }

    private fun signal(dedupKey: String = "RECOMP_WIN|2026-W27") = CoachSignal(
        kind = SignalKind.RECOMP_WIN,
        tier = SignalTier.P1,
        category = SignalCategory.BODY,
        severity = 60,
        facts = SignalFacts(mapOf("weightTrendKgPerWk" to "-0.02")),
        verdict = "Hold calories.",
        action = CoachAction(CoachActionType.OPEN_WEEKLY_REVIEW, "See review"),
        rationale = SignalRationale(confidence = Confidence.HIGH),
        dedupKey = dedupKey,
        surface = CoachSurface.WEEKLY,
        fallbackText = "Weight flat, waist down.",
    )

    @Test
    fun `current is null initially`() = runTest {
        assertNull(repo.current.first())
    }

    @Test
    fun `stage then read current round-trips the signal`() = runTest {
        val s = signal()
        repo.stage(s)
        assertEquals(s, repo.current.first())
    }

    @Test
    fun `stage null clears the current signal`() = runTest {
        repo.stage(signal())
        repo.stage(null)
        assertNull(repo.current.first())
    }

    @Test
    fun `dismissCurrent clears the current signal`() = runTest {
        repo.stage(signal())
        repo.dismissCurrent()
        assertNull(repo.current.first())
    }

    @Test
    fun `markSeen persists to the seen ledger`() = runTest {
        val date = LocalDate.of(2026, 7, 1)
        repo.markSeen("RECOMP_WIN|2026-W27", date)
        repo.markSeen("NEW_PR|bench", date.minusDays(2))
        assertEquals(
            mapOf(
                "RECOMP_WIN|2026-W27" to date,
                "NEW_PR|bench" to date.minusDays(2),
            ),
            repo.seenLedger(),
        )
    }

    @Test
    fun `seenLedger is empty initially`() = runTest {
        assertEquals(emptyMap<String, LocalDate>(), repo.seenLedger())
    }

    @Test
    fun `lastRunDate round-trips and is null initially`() = runTest {
        assertNull(repo.lastRunDate())
        val date = LocalDate.of(2026, 7, 1)
        repo.setLastRunDate(date)
        assertEquals(date, repo.lastRunDate())
    }
}
