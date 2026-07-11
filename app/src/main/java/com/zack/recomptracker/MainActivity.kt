package com.zack.recomptracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.zack.recomptracker.data.coach.AndroidCoachNotifier
import com.zack.recomptracker.domain.coach.CoachActionType
import com.zack.recomptracker.ui.RecompApp
import com.zack.recomptracker.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** The deep-link a tapped coach push wants to open; consumed once by RecompApp then cleared. */
    private val pendingDeepLink = mutableStateOf<CoachActionType?>(null)

    /** A tapped shared-routine file's Uri; consumed once by RecompApp then cleared. */
    private val pendingShareUri = mutableStateOf<Uri?>(null)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* denied is fine — push just won't show */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as RecompTrackerApp
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { !app.dbReady.value }

        // Greenfield push (Phase 5): make sure the channels exist and, on Android 13+, request
        // POST_NOTIFICATIONS when AI is enabled. Non-blocking: a denial leaves the app fully functional.
        app.container.coachNotifier.ensureChannels()
        maybeRequestNotificationPermission(app)

        pendingDeepLink.value = intent.readCoachAction()
        pendingShareUri.value = intent.readShareUri()

        setContent {
            val themeMode by app.container.uiPreferences.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()
            val darkMode = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            // capture outside the lambda — same window backs the whole Activity lifetime
            val window = window
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                // Light status-bar icons (dark glyphs) when the app is in light mode.
                controller.isAppearanceLightStatusBars = !darkMode
                controller.isAppearanceLightNavigationBars = !darkMode
            }
            RecompApp(
                container = app.container,
                darkMode = darkMode,
                deepLinkAction = pendingDeepLink as State<CoachActionType?>,
                onDeepLinkHandled = { pendingDeepLink.value = null },
                shareRoutineUri = pendingShareUri as State<Uri?>,
                onShareRoutineHandled = { pendingShareUri.value = null },
            )
        }
    }

    /** A push tapped while the Activity is already running (SINGLE_TOP) delivers its target here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.readCoachAction()?.let { pendingDeepLink.value = it }
        intent.readShareUri()?.let { pendingShareUri.value = it }
    }

    private fun Intent.readCoachAction(): CoachActionType? {
        val name = getStringExtra(AndroidCoachNotifier.DEEP_LINK_EXTRA) ?: return null
        return runCatching { CoachActionType.valueOf(name) }.getOrNull()
            ?.takeIf { it != CoachActionType.NONE }
    }

    /** The Uri of a tapped shared-routine file, or null for any non-VIEW intent. */
    private fun Intent.readShareUri(): Uri? =
        if (action == Intent.ACTION_VIEW) data else null

    private fun maybeRequestNotificationPermission(app: RecompTrackerApp) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        lifecycleScope.launch {
            // Only prompt once AI is on — proactive coaching is the only source of pushes.
            if (app.container.uiPreferences.aiInsightsEnabled.first()) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
