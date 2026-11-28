package app.openhealth.android.notifications

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.openhealth.R
import app.openhealth.android.MainActivity
import app.openhealth.android.OpenHealthApp
import app.openhealth.core.preferences.NotificationPrefs
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * The local check-ins of `pr.md` §6.19: one daily nudge to weigh in, drink or write a
 * note, and one weekly review prompt. Both are alarms on this phone; nothing is sent, no
 * account is involved, and every type can be turned off in Settings. Quiet hours silence
 * the check-ins; medicine reminders are never silenced, because a missed dose is not a
 * convenience.
 */
@Suppress("TooManyFunctions")
object HealthCheckIns {
    const val CHANNEL = "check_ins"
    const val ACTION_DAILY = "app.openhealth.CHECK_IN_DAILY"
    const val ACTION_WEEKLY = "app.openhealth.CHECK_IN_WEEKLY"
    private const val DAILY_ID = 2001
    private const val WEEKLY_ID = 2002
    private const val DAILY_HOUR = 9
    private const val WEEKLY_HOUR = 18

    fun reschedule(context: Context) {
        val prefs = prefs(context) ?: return
        cancel(context, ACTION_DAILY)
        cancel(context, ACTION_WEEKLY)
        if (!prefs.enabled) return
        if (prefs.daily) {
            scheduleNext(context, ACTION_DAILY, nextAt(LocalTime.of(DAILY_HOUR, 0), null))
        }
        if (prefs.weekly) {
            scheduleNext(
                context,
                ACTION_WEEKLY,
                nextAt(LocalTime.of(WEEKLY_HOUR, 0), DayOfWeek.SUNDAY),
            )
        }
    }

    fun postDaily(context: Context, prefs: NotificationPrefs) {
        if (!prefs.enabled || !prefs.daily) return
        // A quiet alarm is skipped, not dropped: the next day is still scheduled.
        if (!isQuiet(prefs, ZonedDateTime.now())) {
            post(
                context = context,
                id = DAILY_ID,
                title = context.getString(R.string.notify_daily_title),
                body = context.getString(R.string.notify_daily_body),
            )
        }
        scheduleNext(context, ACTION_DAILY, nextAt(LocalTime.of(DAILY_HOUR, 0), null))
    }

    fun postWeekly(context: Context, prefs: NotificationPrefs) {
        if (!prefs.enabled || !prefs.weekly) return
        if (!isQuiet(prefs, ZonedDateTime.now())) {
            post(
                context = context,
                id = WEEKLY_ID,
                title = context.getString(R.string.notify_weekly_title),
                body = context.getString(R.string.notify_weekly_body),
            )
        }
        scheduleNext(
            context,
            ACTION_WEEKLY,
            nextAt(LocalTime.of(WEEKLY_HOUR, 0), DayOfWeek.SUNDAY),
        )
    }

    /** True inside the person's quiet window, including one that crosses midnight. */
    fun isQuiet(prefs: NotificationPrefs, now: ZonedDateTime): Boolean =
        prefs.silences(kotlinx.datetime.LocalTime(now.hour, now.minute))

    fun prefs(context: Context): NotificationPrefs? {
        val app = context.applicationContext as? OpenHealthApp ?: return null
        return runCatching { app.container.preferences.snapshot().notifications }.getOrNull()
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.notify_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { setShowBadge(true) },
        )
    }

    private fun nextAt(time: LocalTime, day: DayOfWeek?): ZonedDateTime {
        val now = ZonedDateTime.now()
        var candidate = now.with(time)
        if (day != null) candidate = candidate.with(TemporalAdjusters.nextOrSame(day))
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        return candidate
    }

    private fun scheduleNext(context: Context, action: String, at: ZonedDateTime) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.canScheduleExactAlarms()
        runCatching {
            if (canExact) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    at.toInstant().toEpochMilli(),
                    pending(context, action),
                )
            } else {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    at.toInstant().toEpochMilli(),
                    pending(context, action),
                )
            }
        }
    }

    private fun cancel(context: Context, action: String) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(pending(context, action))
    }

    private fun pending(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, CheckInReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    @SuppressLint("MissingPermission") // Guarded by areNotificationsEnabled, and caught.
    private fun post(context: Context, id: Int, title: String, body: String) {
        ensureChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_checkin)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}

/** Receives the two check-in alarms. */
class CheckInReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = HealthCheckIns.prefs(context) ?: return
        when (intent.action) {
            HealthCheckIns.ACTION_DAILY -> HealthCheckIns.postDaily(context, prefs)
            HealthCheckIns.ACTION_WEEKLY -> HealthCheckIns.postWeekly(context, prefs)
        }
    }
}

/** Puts the check-ins back after a reboot, an update or a clock change. */
class CheckInBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
            )
        ) {
            return
        }
        HealthCheckIns.reschedule(context)
    }
}
