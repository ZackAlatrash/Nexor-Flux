package com.zack.recomptracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ui.RecompApp
import com.zack.recomptracker.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as RecompTrackerApp
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { !app.dbReady.value }
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
            RecompApp(container = app.container, darkMode = darkMode)
        }
    }
}
