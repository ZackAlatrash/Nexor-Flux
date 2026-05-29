package com.zack.recomptracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.zack.recomptracker.ui.RecompApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as RecompTrackerApp).container
        setContent {
            RecompApp(container = container)
        }
    }
}
