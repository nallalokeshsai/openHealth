package app.openhealth.android.activity

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.openhealth.R
import app.openhealth.android.MainActivity
import app.openhealth.android.OpenHealthApp

/**
 * Passive step tracking as a foreground service of type `health` (`BUILD-ROADMAP.md`
 * §B8, `pr.md` §6.6). The service does no computation of its own: it keeps the process
 * alive so [StepTracker] can persist an interval when the hardware counter reports one.
 * The notification is silent and ongoing; there is no polling loop and no wakelock.
 */
class StepTrackingService : Service() {
    private var tracker: StepTracker? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        val container = (application as OpenHealthApp).container
        tracker = container.newStepTracker()
    }

    /**
     * A health foreground service needs `ACTIVITY_RECOGNITION`, and newer releases also
     * refuse some background starts. Either refusal throws, and an uncaught throw here
     * is a crash on the person's phone, so the service stops itself instead. Nothing is
     * lost: `TYPE_STEP_COUNTER` keeps counting with nothing running and the next app
     * start persists the interval (`pr.md` §6.6, ADR 0013 §3).
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = build(this)
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        tracker?.start()
        return START_STICKY
    }

    override fun onDestroy() {
        tracker?.close()
        tracker = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "step_tracking"
        const val NOTIFICATION_ID = 41

        /** Starts tracking if the person enabled it; safe to call repeatedly. */
        fun ensureRunning(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, StepTrackingService::class.java),
                )
            }
        }

        fun ensureStopped(context: Context) {
            runCatching {
                context.stopService(Intent(context, StepTrackingService::class.java))
            }
        }

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.steps_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.steps_channel_detail)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        fun build(context: Context): Notification {
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_steps)
                .setContentTitle(context.getString(R.string.steps_service_title))
                .setContentText(context.getString(R.string.steps_service_body))
                .setContentIntent(open)
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
        }
    }
}

/**
 * Android 15 and later refuse to start most foreground service types from
 * `BOOT_COMPLETED`. The attempt is made (it is allowed on earlier releases), and on a
 * refusal the counter is simply read on the next app start: `TYPE_STEP_COUNTER` keeps
 * counting while nothing is running, so the steps are recovered rather than lost.
 */
class StepBootReceiver : android.content.BroadcastReceiver() {
    @Suppress("ReturnCount")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val app = context.applicationContext as? OpenHealthApp ?: return
        val enabled = runCatching { app.container.store.devices.stepTrackingEnabled() }
            .getOrDefault(false)
        if (!enabled) return
        StepTrackingService.ensureRunning(context)
    }
}
