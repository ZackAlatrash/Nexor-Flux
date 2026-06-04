package com.zack.recomptracker

import android.app.Application
import com.zack.recomptracker.core.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RecompTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _dbReady = MutableStateFlow(false)
    val dbReady: StateFlow<Boolean> get() = _dbReady

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Warm up the database on a background thread so the first navigation
        // doesn't pay the Room open cost on the main thread. Signal dbReady
        // so the splash screen can dismiss as soon as the DB is open.
        appScope.launch {
            container.database
            _dbReady.value = true
        }
    }
}
