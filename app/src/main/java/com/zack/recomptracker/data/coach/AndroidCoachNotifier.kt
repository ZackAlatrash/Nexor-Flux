package com.zack.recomptracker.data.coach

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zack.recomptracker.MainActivity
import com.zack.recomptracker.R

/**
 * The Android-framework implementation of [CoachNotifier]. Owns all of the platform mechanics the pure
 * [CoachPushEmitter] must not touch: the two [NotificationChannel]s, the [NotificationCompat.Builder],
 * and the tap [PendingIntent] that deep-links into [MainActivity].
 *
 * Channel importance follows §11: `coaching` is LOW (ambient nudges + celebrations), `weekly_check_in`
 * is DEFAULT (the spine's one weekly push). Every notification is `setAutoCancel(true)` and carries a
 * [DEEP_LINK_EXTRA] string extra ([com.zack.recomptracker.domain.coach.CoachActionType.name]) so the tap
 * routes to the mapped screen — mirroring the dashboard `onCoachAction` mapping.
 *
 * [show] returns `false` (and posts nothing) when the app lacks notification permission / notifications
 * are disabled, so the emitter never records a push that did not actually go out.
 *
 * Boundary rule (§5): imports nothing from the legacy `ai/local` stack.
 */
class AndroidCoachNotifier(
    private val context: Context,
) : CoachNotifier {

    private val manager = NotificationManagerCompat.from(context)

    override fun ensureChannels() {
        val coaching = NotificationChannel(
            CoachPushChannel.COACHING.channelId,
            "Coaching nudges",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Rare, decision-attached coaching alerts and celebrations." }

        val weekly = NotificationChannel(
            CoachPushChannel.WEEKLY_CHECK_IN.channelId,
            "Weekly check-in",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Your once-a-week coaching check-in." }

        manager.createNotificationChannel(coaching)
        manager.createNotificationChannel(weekly)
    }

    override fun show(payload: CoachPushPayload): Boolean {
        // Defensive re-check of the decision-attached gate: a payload must carry text + a destination.
        if (payload.title.isBlank() || payload.body.isBlank()) return false
        if (!manager.areNotificationsEnabled()) return false

        ensureChannels()

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(DEEP_LINK_EXTRA, payload.target.name)
        }
        val pending = PendingIntent.getActivity(
            context,
            payload.channel.ordinal,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, payload.channel.channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(
                if (payload.channel == CoachPushChannel.WEEKLY_CHECK_IN) NotificationCompat.PRIORITY_DEFAULT
                else NotificationCompat.PRIORITY_LOW,
            )
            .build()

        return runCatching {
            // POST_NOTIFICATIONS is checked above via areNotificationsEnabled(); guard the SecurityException
            // corner anyway so a revoked permission never crashes the digest.
            manager.notify(payload.channel.notificationId, notification)
            true
        }.getOrDefault(false)
    }

    companion object {
        /** Intent extra key carrying the tapped push's [com.zack.recomptracker.domain.coach.CoachActionType] name. */
        const val DEEP_LINK_EXTRA = "coach_push_action"
    }
}

/** Stable per-channel notification id so a new push of the same kind replaces the previous one. */
private val CoachPushChannel.notificationId: Int
    get() = 4200 + ordinal
