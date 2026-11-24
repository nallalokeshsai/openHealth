package app.openhealth.android.training

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.ContextCompat

/**
 * The rest timer as a foreground service with an ongoing notification and actions
 * (`pr.md` §6.5, `Tech-Stack.md` §4). The state is on disk before the service starts, so
 * `START_STICKY` rebuilds the same countdown after a process kill; the session itself is
 * already in the database, so nothing is lost either way.
 */
class RestTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: RestTimerStore
    private var lastSecond = -1L

    private val tick = object : Runnable {
        override fun run() {
            val snapshot = store.read(System.currentTimeMillis())
            when {
                snapshot == null -> stopService()
                snapshot.remainingSeconds <= 0L && !snapshot.paused -> {
                    alert()
                    store.clear()
                    stopService()
                }
                else -> {
                    if (snapshot.remainingSeconds != lastSecond) {
                        lastSecond = snapshot.remainingSeconds
                        notify(RestNotificationFactory.build(this@RestTimerService, snapshot))
                    }
                    handler.postDelayed(this, TICK_MILLIS)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = RestTimerStore(this)
        RestNotificationFactory.ensureChannel(this)
    }

    @Suppress("ReturnCount")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stepped = when (intent?.action) {
            ACTION_STOP -> {
                store.clear()
                true
            }

            ACTION_ADJUST -> {
                store.adjust(intent.getIntExtra(EXTRA_SECONDS, 0), System.currentTimeMillis())
                false
            }

            ACTION_PAUSE -> {
                store.togglePause(System.currentTimeMillis())
                false
            }

            else -> false
        }
        // startForegroundService promises the system a foreground notification within
        // five seconds even if the person skips the rest before the service is created.
        // Show the state first, then stop cleanly; never leave the promise unfulfilled.
        val snapshot = store.read(System.currentTimeMillis())
            ?: app.openhealth.core.seam.RestTimerSnapshot(0, 0, false, "")
        startForegroundCompat(RestNotificationFactory.build(this@RestTimerService, snapshot))
        if (stepped || snapshot.totalSeconds <= 0L) {
            stopService()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(tick)
        lastSecond = -1L
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notify(notification: android.app.Notification) {
        getSystemService(android.app.NotificationManager::class.java)
            .notify(RestNotificationFactory.NOTIFICATION_ID, notification)
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                RestNotificationFactory.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(RestNotificationFactory.NOTIFICATION_ID, notification)
        }
    }

    private fun stopService() {
        handler.removeCallbacks(tick)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun alert() {
        runCatching {
            RingtoneManager
                .getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                ?.play()
        }
        runCatching {
            val vibrator = getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(
                        VIBRATION_MILLIS,
                        VibrationEffect.DEFAULT_AMPLITUDE,
                    ),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(VIBRATION_MILLIS)
            }
        }
    }

    companion object {
        const val ACTION_START = "app.openhealth.REST_START"
        const val ACTION_ADJUST = "app.openhealth.REST_ADJUST"
        const val ACTION_PAUSE = "app.openhealth.REST_PAUSE"
        const val ACTION_STOP = "app.openhealth.REST_STOP"
        const val EXTRA_SECONDS = "seconds"
        private const val TICK_MILLIS = 500L
        private const val VIBRATION_MILLIS = 400L

        fun start(context: Context) {
            val intent = Intent(context, RestTimerService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
