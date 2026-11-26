@file:Suppress(
    "TooManyFunctions",
    "ReturnCount",
    "MagicNumber",
    "LongMethod",
    "CyclomaticComplexMethod",
)

package app.openhealth.android.activity

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.openhealth.R
import app.openhealth.android.AppContainer
import app.openhealth.android.MainActivity
import app.openhealth.android.OpenHealthApp
import app.openhealth.core.activity.ActivityType
import app.openhealth.core.activity.GpsFix
import app.openhealth.core.activity.GpsRecorder
import app.openhealth.core.activity.GpsRecordingStore
import app.openhealth.core.activity.GpsSnapshot
import app.openhealth.core.activity.GpsSnapshotCodec
import app.openhealth.ui.R as UiR
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.toLocalDateTime

/** The live snapshot a screen observes; null when nothing is recording. */
object GpsSessionBus {
    private val _snapshot = MutableStateFlow<GpsSnapshot?>(null)
    val snapshot: StateFlow<GpsSnapshot?> = _snapshot.asStateFlow()

    private val _recovered = MutableStateFlow(false)
    val recovered: StateFlow<Boolean> = _recovered.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun publish(snapshot: GpsSnapshot?) {
        _snapshot.value = snapshot
        if (snapshot != null) _message.value = null
    }

    fun recovered(value: Boolean) {
        _recovered.value = value
    }

    fun message(value: String?) {
        _message.value = value
    }
}

/** SharedPreferences storage for the in-progress snapshot (never the encrypted database). */
class PreferencesGpsStore(context: Context) : GpsRecordingStore {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): String? = preferences.getString(KEY, null)

    override fun save(json: String) {
        preferences.edit().putString(KEY, json).commit()
    }

    override fun clear() {
        preferences.edit().remove(KEY).commit()
    }

    private companion object {
        const val PREFS = "gps-recording"
        const val KEY = "snapshot"
    }
}

/**
 * The `location` foreground service (`pr.md` §6.11, §6.18): it keeps the GPS recording
 * alive with the screen off and across a process death because every fix is persisted
 * before the notification is updated. A pause stops adding fixes and excludes the paused
 * interval from elapsed time; a finish writes the activity through the same repository
 * the manual and imported paths use.
 */
class GpsRecordingService : Service() {
    private lateinit var store: GpsRecordingStore
    private lateinit var locationManager: LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private var snapshot: GpsSnapshot? = null
    private var saving = false

    private val listener = LocationListener { location -> onFix(location) }

    private val ticker = object : Runnable {
        override fun run() {
            val current = snapshot
            if (current != null && !current.paused) {
                pollLastKnown()
                updateNotification(snapshot ?: current)
            }
            handler.postDelayed(this, TICK_MILLIS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = PreferencesGpsStore(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START || intent?.action == ACTION_RECOVER) {
            // Android requires startForeground within a few seconds of the request even
            // when the permission turns out to be missing. Put a placeholder up first,
            // then stop cleanly rather than letting the platform kill the process.
            beginForegroundPlaceholder()
        }
        when (intent?.action) {
            ACTION_START -> start(intent.getStringExtra(EXTRA_TYPE))
            ACTION_RECOVER -> recover()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> finish()
            else -> {
                if (snapshot == null) recover()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        runCatching { locationManager.removeUpdates(listener) }
        super.onDestroy()
    }

    private fun start(typeKey: String?) {
        if (snapshot != null) return
        if (!hasLocationPermission()) {
            GpsSessionBus.message(getString(UiR.string.gps_permission_denied))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val type = ActivityType.fromKey(typeKey.orEmpty()) ?: ActivityType.RUN
        val container = container() ?: run {
            stopSelf()
            return
        }
        val at = container.clock.now()
        val zone = container.zone
        val local = at.toLocalDateTime(zone).date
        val record = GpsRecorder.start(
            id = container.ids.newId(),
            type = type,
            startedAt = at,
            zoneId = zone.id,
            localDate = local,
        )
        snapshot = record
        persist(record)
        beginForeground(record)
        requestUpdates()
        handler.post(ticker)
        GpsSessionBus.publish(record)
    }

    private fun recover() {
        val loaded = store.load()?.let { runCatching { GpsSnapshotCodec.decode(it) }.getOrNull() }
        if (loaded == null) {
            stopSelf()
            return
        }
        if (!hasLocationPermission()) {
            GpsSessionBus.message(getString(UiR.string.gps_permission_denied))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        snapshot = loaded
        GpsSessionBus.recovered(true)
        beginForeground(loaded)
        requestUpdates()
        handler.post(ticker)
        GpsSessionBus.publish(loaded)
    }

    private fun pause() {
        val current = snapshot ?: return
        val paused = GpsRecorder.pause(current, System.currentTimeMillis())
        snapshot = paused
        persist(paused)
        runCatching { locationManager.removeUpdates(listener) }
        updateNotification(paused)
        GpsSessionBus.publish(paused)
    }

    private fun resume() {
        val current = snapshot ?: return
        val resumed = GpsRecorder.resume(current, System.currentTimeMillis())
        snapshot = resumed
        persist(resumed)
        if (hasLocationPermission()) requestUpdates()
        updateNotification(resumed)
        GpsSessionBus.publish(resumed)
    }

    private fun finish() {
        val current = snapshot ?: run {
            stopSelf()
            return
        }
        if (saving) return
        saving = true
        handler.removeCallbacks(ticker)
        runCatching { locationManager.removeUpdates(listener) }
        val record = GpsRecorder.finish(current, System.currentTimeMillis())
        val container = container()
        if (container != null) {
            runCatching {
                container.activityLog.finishRecording(record, deviceLabel())
            }
        }
        store.clear()
        snapshot = null
        GpsSessionBus.publish(null)
        GpsSessionBus.recovered(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** A last-known fix covers a provider that updates its cache but not every listener. */
    @android.annotation.SuppressLint("MissingPermission")
    private fun pollLastKnown() {
        if (!hasLocationPermission()) return
        val last = runCatching {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }.getOrNull() ?: return
        // A last-known fix from another city or another day must not become the first
        // sample and make every real fix look like an impossible jump.
        val ageMillis = android.os.SystemClock.elapsedRealtime() -
            last.elapsedRealtimeNanos / NANOS_PER_MILLISECOND
        if (ageMillis in 0..LAST_KNOWN_MAX_AGE_MILLIS) onFix(last)
    }

    private fun onFix(location: Location) {
        val current = snapshot ?: return
        val fix = GpsFix(
            latitude = location.latitude,
            longitude = location.longitude,
            // The receive time, not the provider's fix time: emulator and some phones
            // stamp a batch of fixes with the same provider time, which would otherwise
            // make every fix after the first look like a duplicate.
            timeEpochMillis = System.currentTimeMillis(),
            accuracyM = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
            elevationM = if (location.hasAltitude()) location.altitude else null,
            speedMps = if (location.hasSpeed()) location.speed.toDouble() else null,
        )
        val updated = GpsRecorder.addFix(current, fix)
        if (updated === current || updated.points.size == current.points.size) return
        snapshot = updated
        persist(updated)
        updateNotification(updated)
        GpsSessionBus.publish(updated)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun requestUpdates() {
        if (!hasLocationPermission()) return
        runCatching {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME_MILLIS,
                MIN_DISTANCE_METRES,
                listener,
                Looper.getMainLooper(),
            )
        }
    }

    private fun persist(record: GpsSnapshot) {
        store.save(GpsSnapshotCodec.encode(record))
    }

    private fun container(): AppContainer? =
        runCatching { (application as OpenHealthApp).container }.getOrNull()

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun deviceLabel(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                getString(R.string.gps_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.gps_channel_body)
                setShowBadge(false)
            },
        )
    }

    private fun beginForegroundPlaceholder() {
        val now = System.currentTimeMillis()
        val stub = GpsSnapshot(
            id = "pending",
            typeKey = ActivityType.RUN.name,
            startedAtEpochMillis = now,
            zoneId = "",
            localDate = "1970-01-01",
        )
        startForegroundCompat(buildNotification(stub))
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                },
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun beginForeground(record: GpsSnapshot) {
        val notification = buildNotification(record)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                },
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(record: GpsSnapshot) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(record))
    }

    private fun buildNotification(record: GpsSnapshot): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pauseLabel = if (record.paused) UiR.string.gps_resume else UiR.string.gps_pause
        val toggle = PendingIntent.getService(
            this,
            1,
            Intent(this, GpsRecordingService::class.java).setAction(
                if (record.paused) ACTION_RESUME else ACTION_PAUSE,
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, GpsRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val elapsed = GpsRecorder.elapsedSeconds(record, System.currentTimeMillis())
        val title = getString(
            if (record.paused) UiR.string.gps_paused else UiR.string.gps_title,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_gps)
            .setContentTitle(title)
            .setContentText(
                getString(
                    R.string.gps_notification_text,
                    formatClock(elapsed),
                    "%.2f".format(record.distanceM / 1_000.0),
                ),
            )
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(pauseLabel), toggle)
            .addAction(0, getString(UiR.string.gps_finish), stop)
            .build()
    }

    private fun formatClock(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

    companion object {
        const val ACTION_START = "app.openhealth.action.GPS_START"
        const val ACTION_RECOVER = "app.openhealth.action.GPS_RECOVER"
        const val ACTION_PAUSE = "app.openhealth.action.GPS_PAUSE"
        const val ACTION_RESUME = "app.openhealth.action.GPS_RESUME"
        const val ACTION_STOP = "app.openhealth.action.GPS_STOP"
        const val EXTRA_TYPE = "type"
        private const val CHANNEL = "gps_recording"
        private const val NOTIFICATION_ID = 42
        private const val TICK_MILLIS = 1_000L
        private const val MIN_TIME_MILLIS = 2_000L
        private const val MIN_DISTANCE_METRES = 3f
        private const val LAST_KNOWN_MAX_AGE_MILLIS = 30_000L
        private const val NANOS_PER_MILLISECOND = 1_000_000L

        fun start(context: Context, type: ActivityType) {
            val intent = Intent(context, GpsRecordingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_TYPE, type.name)
            ContextCompat.startForegroundService(context, intent)
        }

        fun pause(context: Context) {
            context.startService(
                Intent(context, GpsRecordingService::class.java).setAction(ACTION_PAUSE),
            )
        }

        fun resume(context: Context) {
            context.startService(
                Intent(context, GpsRecordingService::class.java).setAction(ACTION_RESUME),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, GpsRecordingService::class.java).setAction(ACTION_STOP),
            )
        }

        /** Called at app start; a snapshot left behind is a recording that was interrupted. */
        fun recoverIfNeeded(context: Context) {
            val saved = PreferencesGpsStore(context).load() ?: return
            if (saved.isBlank()) return
            ContextCompat.startForegroundService(
                context,
                Intent(context, GpsRecordingService::class.java).setAction(ACTION_RECOVER),
            )
        }
    }
}
