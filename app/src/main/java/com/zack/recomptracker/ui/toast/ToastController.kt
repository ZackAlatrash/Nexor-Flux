package com.zack.recomptracker.ui.toast

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class ToastController {
    private val _channel = Channel<ToastMessage>(capacity = Channel.BUFFERED)
    val messages = _channel.receiveAsFlow()

    suspend fun show(message: ToastMessage) {
        _channel.send(message)
    }
}

val LocalToastController = staticCompositionLocalOf<ToastController> {
    error("No ToastController provided")
}
