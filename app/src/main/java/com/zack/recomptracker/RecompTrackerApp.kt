package com.zack.recomptracker

import android.app.Application
import com.zack.recomptracker.core.AppContainer

class RecompTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
