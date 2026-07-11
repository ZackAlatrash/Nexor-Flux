package com.zack.recomptracker.data.share

import android.net.Uri
import java.util.concurrent.atomic.AtomicReference

/**
 * One-shot hand-off of a tapped shared-routine [Uri] from the Activity/nav layer to the import
 * ViewModel. Single-slot: a second incoming file before the first is consumed replaces it (the user
 * is opening one file at a time). [consume] atomically claims and clears the slot so a rotation /
 * recomposition can't re-import the same file.
 */
class RoutineShareInbox {
    private val ref = AtomicReference<Uri?>(null)

    fun offer(uri: Uri) { ref.set(uri) }

    fun consume(): Uri? = ref.getAndSet(null)
}
