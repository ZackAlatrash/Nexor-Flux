package com.zack.recomptracker.ui.toast

enum class ToastType { Success, Error, Info }

data class ToastMessage(
    val text: String,
    val type: ToastType,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)
