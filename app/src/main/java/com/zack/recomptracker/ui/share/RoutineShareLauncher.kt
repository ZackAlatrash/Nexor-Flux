package com.zack.recomptracker.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Launches the Android share sheet for an exported routine [uri]. Uses `application/octet-stream`
 * (the custom `.rtroutine` type has no registered mime) and grants the receiving app read access.
 * `FLAG_ACTIVITY_NEW_TASK` lets this run from a non-Activity context if needed.
 */
fun shareRoutineFile(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "Share routine").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
