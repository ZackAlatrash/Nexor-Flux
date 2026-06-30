package com.zack.recomptracker

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.zack.recomptracker.core.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RecompTrackerApp : Application(), ImageLoaderFactory {
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
        // Foreground auto-sync: when Health Connect is enabled + permitted, refresh today's
        // steps/weight/sleep each time the app comes to the foreground (debounced inside the
        // coordinator), so streaks stay live without the user opening Settings to "Sync now".
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    container.healthSyncCoordinator.syncIfDue()
                }
            },
        )
    }

    /**
     * App-wide Coil image loader tuned for the small exercise thumbnails shown in scrolling lists.
     *  - allowHardware: decode straight into GPU-resident HARDWARE bitmaps, so drawing a thumbnail is
     *    a cheap texture reference with no per-frame upload (the scroll-jank hot path).
     *  - crossfade off: no extra fade compositing as cards scroll in.
     *  - generous memory cache: keep decoded thumbnails resident so scrolling back doesn't re-decode.
     * Combined with explicit decode sizing + preloading (see ActiveSessionScreen), this moves the
     * image GPU cost off the critical scroll frames. No visual change.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .allowHardware(true)
            .crossfade(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .build()
}
