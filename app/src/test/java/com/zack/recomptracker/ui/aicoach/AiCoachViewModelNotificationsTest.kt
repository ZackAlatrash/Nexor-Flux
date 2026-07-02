package com.zack.recomptracker.ui.aicoach

import androidx.test.core.app.ApplicationProvider
import com.zack.recomptracker.ai.StubInsightCoordinator
import com.zack.recomptracker.data.coach.CoachDigestCoordinator
import com.zack.recomptracker.data.coach.CoachNotificationPreferences
import com.zack.recomptracker.data.coach.CoachNotifierPreferences
import com.zack.recomptracker.data.preferences.SecureKeyStore
import com.zack.recomptracker.data.preferences.UiPreferences
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.domain.coach.QuietHours
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.flow.StateFlow
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the Phase-5 notification-preference surface the [AiCoachViewModel] exposes to the
 * "Notifications" settings group. The notification prefs are injected as the [CoachNotifierPreferences]
 * interface, so we drive them with a trivial in-memory fake and assert the ViewModel mirrors current
 * values into its UI state and routes setter calls straight through to the pref.
 *
 * The ViewModel's other collaborators are Android-bound (DataStore-backed [UiPreferences] /
 * [SecureKeyStore]) or irrelevant here; Robolectric supplies a real application context for those,
 * and the AI coordinator / cloud client / digest coordinator are stubbed or mocked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [33])
class AiCoachViewModelNotificationsTest {

    private val dispatcher = StandardTestDispatcher()

    /** In-memory [CoachNotifierPreferences] fake; records setter calls and serves current values. */
    private class FakeNotifierPreferences(
        weeklyEnabled: Boolean = true,
        ambientEnabled: Boolean = false,
        private var quiet: QuietHours = QuietHours(),
    ) : CoachNotifierPreferences {
        override val weeklyCheckInPushEnabled = MutableStateFlow(weeklyEnabled)
        override val ambientNudgesEnabled = MutableStateFlow(ambientEnabled)

        var setWeeklyCalls = mutableListOf<Boolean>()
        var setAmbientCalls = mutableListOf<Boolean>()

        override suspend fun quietHours(): QuietHours = quiet

        override suspend fun setWeeklyCheckInPushEnabled(enabled: Boolean) {
            setWeeklyCalls += enabled
            weeklyCheckInPushEnabled.value = enabled
        }

        override suspend fun setAmbientNudgesEnabled(enabled: Boolean) {
            setAmbientCalls += enabled
            ambientNudgesEnabled.value = enabled
        }

        override suspend fun setQuietHours(startHour: Int, endHour: Int) {
            quiet = QuietHours(LocalTime.of(startHour, 0), LocalTime.of(endHour, 0))
        }

        override suspend fun lastPushedWeeklySignature(): String = ""
        override suspend fun setLastPushedWeeklySignature(signature: String) = Unit
    }

    private fun buildViewModel(
        notifierPrefs: CoachNotifierPreferences,
    ): AiCoachViewModel {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val scope = CoroutineScope(dispatcher)
        // SecureKeyStore touches the AndroidKeyStore in its init, which isn't available under
        // Robolectric — mock it and serve constant "no key saved" flows (unused by these tests).
        val secureKeyStore = mock<SecureKeyStore> {
            on { hasKey } doReturn (MutableStateFlow(false) as StateFlow<Boolean>)
            on { hasWebSearchKey } doReturn (MutableStateFlow(false) as StateFlow<Boolean>)
        }
        return AiCoachViewModel(
            uiPreferences = UiPreferences(context),
            aiInsightCoordinator = StubInsightCoordinator(
                aiEnabledFlow = MutableStateFlow(false) as Flow<Boolean>,
                scope = scope,
            ),
            secureKeyStore = secureKeyStore,
            openAiCompatClient = mock<OpenAiCompatClient>(),
            coachDigestCoordinator = mock<CoachDigestCoordinator>(),
            coachNotificationPreferences = notifierPrefs,
        )
    }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `ui state reflects pref defaults - weekly on, ambient off`() = runTest(dispatcher) {
        val prefs = FakeNotifierPreferences(weeklyEnabled = true, ambientEnabled = false)
        val vm = buildViewModel(prefs)
        advanceUntilIdle()

        val state = vm.uiState.first()
        assertTrue("weekly check-in should reflect pref default true", state.weeklyCheckInPushEnabled)
        assertFalse("ambient nudges should reflect pref default false", state.ambientNudgesEnabled)
    }

    @Test
    fun `ui state reflects non-default pref values`() = runTest(dispatcher) {
        val prefs = FakeNotifierPreferences(weeklyEnabled = false, ambientEnabled = true)
        val vm = buildViewModel(prefs)
        advanceUntilIdle()

        val state = vm.uiState.first()
        assertFalse(state.weeklyCheckInPushEnabled)
        assertTrue(state.ambientNudgesEnabled)
    }

    @Test
    fun `setWeeklyCheckInPush calls pref setter`() = runTest(dispatcher) {
        val prefs = FakeNotifierPreferences()
        val vm = buildViewModel(prefs)
        advanceUntilIdle()

        vm.setWeeklyCheckInPush(false)
        advanceUntilIdle()

        assertEquals(listOf(false), prefs.setWeeklyCalls)
    }

    @Test
    fun `setAmbientNudges calls pref setter`() = runTest(dispatcher) {
        val prefs = FakeNotifierPreferences()
        val vm = buildViewModel(prefs)
        advanceUntilIdle()

        vm.setAmbientNudges(true)
        advanceUntilIdle()

        assertEquals(listOf(true), prefs.setAmbientCalls)
    }

    @Test
    fun `quiet hours window is exposed as a formatted display string`() = runTest(dispatcher) {
        val prefs = FakeNotifierPreferences(
            quiet = QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0)),
        )
        val vm = buildViewModel(prefs)
        advanceUntilIdle()

        val state = vm.uiState.first()
        assertEquals("10 PM – 7 AM", state.quietHoursDisplay)
    }
}
