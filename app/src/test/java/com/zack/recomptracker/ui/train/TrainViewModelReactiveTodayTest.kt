package com.zack.recomptracker.ui.train

import com.zack.recomptracker.core.time.FakeDateProvider
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.WorkoutRepository
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * P1-10: the Train-home "Today" card must follow the calendar day. A ViewModel that froze "today"
 * kept reading recovery/plan against the opening day after midnight — this asserts recoveryLoggedToday
 * flips when [FakeDateProvider] rolls onto a day that has a recovery log.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainViewModelReactiveTodayTest {

    private val dispatcher = StandardTestDispatcher()
    private val day1 = LocalDate.of(2026, 7, 11)
    private val day2 = LocalDate.of(2026, 7, 12)
    private val provider = FakeDateProvider(day1)

    private lateinit var workoutRepo: WorkoutRepository
    private lateinit var sessionRepo: WorkoutSessionRepository
    private lateinit var exerciseRepo: ExerciseLibraryRepository
    private lateinit var logRepo: LogRepository
    private lateinit var profileStore: UserProfilePreferencesStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        workoutRepo = mock()
        sessionRepo = mock()
        exerciseRepo = mock()
        logRepo = mock()
        profileStore = mock()
        whenever(workoutRepo.observeAll()).thenReturn(flowOf(emptyList()))
        whenever(sessionRepo.observeActiveSession()).thenReturn(flowOf(null))
        whenever(sessionRepo.observeCompletedSessions()).thenReturn(flowOf(emptyList()))
        whenever(exerciseRepo.observeAll()).thenReturn(flowOf(emptyList()))
        // Only day2 carries a recovery signal.
        whenever(logRepo.observeDailyLogs())
            .thenReturn(flowOf(listOf(DailyLogEntity(date = day2.toString(), sorenessScore = 5))))
        whenever(profileStore.preferences).thenReturn(flowOf(UserProfilePreferences()))
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun buildVm() = TrainViewModel(
        workoutRepository = workoutRepo,
        sessionRepository = sessionRepo,
        exerciseLibraryRepository = exerciseRepo,
        logRepository = logRepo,
        userProfileStore = profileStore,
        dateProvider = provider,
        computeDispatcher = dispatcher,
    )

    @Test
    fun `recoveryLoggedToday follows the day across midnight`() = runTest {
        val vm = buildVm()
        val seen = mutableListOf<Boolean>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect { seen.add(it.recoveryLoggedToday) }
        }
        advanceUntilIdle()
        assertFalse("day1 seq=$seen", seen.last()) // day1: no recovery log for today

        provider.advanceTo(day2)
        advanceUntilIdle()
        assertTrue("day2 seq=$seen", seen.last()) // day2: recovery logged today
        job.cancel()
    }
}
