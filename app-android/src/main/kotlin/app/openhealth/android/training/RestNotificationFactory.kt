package app.openhealth.android.training

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import app.openhealth.R
import app.openhealth.android.MainActivity
import app.openhealth.core.seam.RestTimerSnapshot

/**
 * Builds the rest timer's ongoing notification and its four actions (`DESIGN.md` §5.19).
 * It is separate from the service so the notification's wording, channel and pending
 * intents are one small unit, not another branch of the service's lifecycle.
 */
object RestNotificationFactory {
    const val CHANNEL = "rest_timer"
    const val NOTIFICATION_ID = 1001
    private const val STEP_SECONDS = 15
    private const val MINUTES = 60L

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.rest_timer_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, snapshot: RestTimerSnapshot): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setContentTitle(
                snapshot.label.ifBlank {
                    context.getString(R.string.rest_timer_title)
                },
            )
            .setContentText(clock(snapshot.remainingSeconds))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .addAction(minus(context))
            .addAction(plus(context))
            .addAction(pause(context, snapshot.paused))
            .addAction(skip(context))
            .build()
    }

    private fun minus(context: Context): NotificationCompat.Action = action(
        context,
        action = RestTimerService.ACTION_ADJUST,
        request = 1,
        title = context.getString(R.string.rest_timer_minus),
        extraSeconds = -STEP_SECONDS,
    )

    private fun plus(context: Context): NotificationCompat.Action = action(
        context,
        action = RestTimerService.ACTION_ADJUST,
        request = 2,
        title = context.getString(R.string.rest_timer_plus),
        extraSeconds = STEP_SECONDS,
    )

    private fun pause(context: Context, paused: Boolean): NotificationCompat.Action = action(
        context,
        action = RestTimerService.ACTION_PAUSE,
        request = 3,
        title = context.getString(
            if (paused) R.string.rest_timer_resume else R.string.rest_timer_pause,
        ),
    )

    private fun skip(context: Context): NotificationCompat.Action = action(
        context,
        action = RestTimerService.ACTION_STOP,
        request = 4,
        title = context.getString(R.string.rest_timer_skip),
    )

    private fun action(
        context: Context,
        action: String,
        request: Int,
        title: String,
        extraSeconds: Int = 0,
    ): NotificationCompat.Action = NotificationCompat.Action(
        R.drawable.ic_stat_rest,
        title,
        PendingIntent.getService(
            context,
            request,
            Intent(context, RestTimerService::class.java).setAction(action)
                .putExtra(RestTimerService.EXTRA_SECONDS, extraSeconds),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ),
    )

    fun clock(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        return "${safe / MINUTES}:${(safe % MINUTES).toString().padStart(2, '0')}"
    }
}
