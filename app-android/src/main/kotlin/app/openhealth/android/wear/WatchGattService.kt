package app.openhealth.android.wear

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
import app.openhealth.android.MainActivity
import app.openhealth.android.OpenHealthApp
import app.openhealth.core.watch.WatchBleCommand
import app.openhealth.core.watch.WatchBlePacket
import app.openhealth.core.watch.WatchBleSnapshot
import app.openhealth.core.watch.WatchCommand
import app.openhealth.core.watch.WatchCommandType
import app.openhealth.data.repo.SettingKeys

/**
 * Keeps the Connect IQ GATT server alive while a workout is open and the person turned
 * the watch link on (`docs/adr/0019`). It is a foreground service with a quiet ongoing
 * notification, so Android does not kill the advertisement mid-set; it stops with the
 * session or the switch.
 */
class WatchGattService : Service() {
    private var server: AndroidWatchGattServer? = null
    private var lastAckId = 0

    override fun onCreate() {
        super.onCreate()
        channel()
    }

    @Suppress("ReturnCount")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startForegroundCompat()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val container = runCatching {
            (application as? OpenHealthApp)?.container
        }.getOrNull()
        if (container == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (server == null) {
            server = AndroidWatchGattServer(
                context = this,
                onCommand = { command -> apply(command, container) },
                snapshot = { WatchBlePacket.encodeSnapshot(bleSnapshot(container)) },
            )
            if (!server!!.start()) {
                notify(
                    title = getString(app.openhealth.R.string.watch_link_permission_title),
                    text = getString(app.openhealth.R.string.watch_link_permission_body),
                )
            }
        }
        server?.publish()
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }

    /**
     * The phone owns the session and the current exercise, so the packet only names the
     * action. A command that cannot be filed (no open session) is not acknowledged; the
     * watch keeps it queued.
     */
    private fun apply(packet: WatchBleCommand, container: app.openhealth.android.AppContainer) {
        val snapshot = container.watchSnapshots.build()
        val session = snapshot.session ?: return
        val exercise = session.exercises.getOrNull(session.currentExerciseIndex)
        val command = when (packet.type) {
            WatchBlePacket.Type.LOG_SET -> {
                if (exercise == null) return
                WatchCommand(
                    id = "connectiq-${packet.id}",
                    type = WatchCommandType.LOG_SET,
                    happenedAtMillis = packet.happenedAtMillis,
                    sessionId = session.id,
                    exerciseId = exercise.id,
                    sessionExerciseId = exercise.entryId.ifBlank { null },
                    weightKg = packet.weightKg,
                    reps = packet.reps,
                    isWarmup = packet.isWarmup,
                    locale = "en",
                )
            }

            WatchBlePacket.Type.FINISH -> WatchCommand(
                id = "connectiq-${packet.id}",
                type = WatchCommandType.FINISH_SESSION,
                happenedAtMillis = packet.happenedAtMillis,
                sessionId = session.id,
                locale = "en",
            )

            WatchBlePacket.Type.HEART_RATE -> WatchCommand(
                id = "connectiq-${packet.id}",
                type = WatchCommandType.HEART_RATE,
                happenedAtMillis = packet.happenedAtMillis,
                sessionId = session.id,
                heartRateBpm = packet.bpm,
                locale = "en",
            )
        }
        val outcome = container.watchCommands.apply(command, WatchKind.CONNECT_IQ)
        if (outcome != WatchApply.REJECTED) {
            lastAckId = packet.id
            server?.publish()
        }
    }

    private fun bleSnapshot(container: app.openhealth.android.AppContainer): WatchBleSnapshot {
        val snapshot = container.watchSnapshots.build()
        val session = snapshot.session
        val exercise = session?.exercises?.getOrNull(session.currentExerciseIndex)
        val last = exercise?.sets?.lastOrNull()
        val rest = snapshot.rest
        return WatchBleSnapshot(
            sessionOpen = session != null,
            restRunning = rest != null && rest.remainingSeconds > 0 && !rest.paused,
            connected = true,
            weightUnit = snapshot.weightUnit,
            weightKg = last?.weightKg ?: exercise?.targetWeightKg ?: 0.0,
            reps = last?.reps ?: exercise?.targetReps ?: 8,
            doneSets = exercise?.sets?.size ?: 0,
            targetSets = exercise?.targetSets ?: 0,
            restRemainingSeconds = rest?.remainingSeconds ?: 0,
            restTotalSeconds = rest?.totalSeconds ?: 0,
            weightIncrementKg = snapshot.weightIncrementKg,
            ackCommandId = lastAckId,
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat(): Boolean = runCatching {
        val notification = notification(
            title = getString(app.openhealth.R.string.watch_link_title),
            text = getString(app.openhealth.R.string.watch_link_body),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    }.getOrDefault(false)

    private fun notify(title: String, text: String) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            notification(title, text),
        )
    }

    private fun notification(title: String, text: String): android.app.Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(app.openhealth.R.drawable.ic_stat_rest)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun channel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                getString(app.openhealth.R.string.watch_link_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    companion object {
        private const val CHANNEL = "openhealth.watch_link"
        private const val NOTIFICATION_ID = 1002

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, WatchGattService::class.java),
                )
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchGattService::class.java))
        }

        /** Called when a session opens or closes; obeys the Settings switch. */
        fun sessionChanged(context: Context, open: Boolean) {
            val app = context.applicationContext as? OpenHealthApp ?: return
            val enabled = runCatching {
                app.container.store.settings.get(SettingKeys.WATCH_LINK) == "true"
            }.getOrDefault(false)
            if (open && enabled) {
                // A WearableListenerService can start a session while the phone is in
                // the background; Android 12+ may refuse the foreground start. The link
                // comes back when the app is next visible (`recoverIfNeeded`), and the
                // watch keeps its queue, so a refused start is not a crash.
                runCatching { start(context) }
            } else if (!open) {
                stop(context)
            }
        }

        /** After a process death, a still-open session restores the link. */
        fun recoverIfNeeded(context: Context) {
            val app = context.applicationContext as? OpenHealthApp ?: return
            val open = runCatching { app.container.store.training.openSession() != null }
                .getOrDefault(false)
            if (open) sessionChanged(context, true)
        }
    }
}
