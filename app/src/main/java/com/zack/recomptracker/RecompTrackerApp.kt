package com.zack.recomptracker

import android.app.Application
import com.zack.recomptracker.core.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RecompTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Warm up the database on a background thread so the first navigation
        // doesn't pay the Room open cost on the main thread.
        appScope.launch { container.database }
    }
}
