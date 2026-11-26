@file:Suppress(
    "TooManyFunctions",
    "ReturnCount",
    "MagicNumber",
    "LongMethod",
    "CyclomaticComplexMethod",
)

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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.openhealth.R
import app.openhealth.android.AppContainer
import app.openhealth.android.MainActivity
import app.openhealth.android.OpenHealthApp
import app.openhealth.core.activity.YogaLibrary
import app.openhealth.core.activity.YogaLoad
import app.openhealth.core.activity.YogaPhase
import app.openhealth.core.activity.YogaPlayer
import app.openhealth.core.activity.YogaPlayerState
import app.openhealth.ui.R as UiR
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class YogaSessionSnapshot(
    val flowId: String,
    val stepIndex: Int,
    val phase: String,
    val secondsInPhase: Int,
    val elapsedSeconds: Int,
    val paused: Boolean,
    val totalSeconds: Int,
    val stepCount: Int,
    val startedAtMillis: Long,
    val voiceOn: Boolean = true,
)

/** The live yoga session and its finish event, observed by the screen. */
object YogaSessionBus {
    private val _snapshot = MutableStateFlow<YogaSessionSnapshot?>(null)
    val snapshot: StateFlow<YogaSessionSnapshot?> = _snapshot.asStateFlow()

    private val _finished = MutableStateFlow<YogaSessionSnapshot?>(null)
    val finished: StateFlow<YogaSessionSnapshot?> = _finished.asStateFlow()

    fun publish(snapshot: YogaSessionSnapshot?) {
        _snapshot.value = snapshot
    }

    fun finished(snapshot: YogaSessionSnapshot) {
        _snapshot.value = null
        _finished.value = snapshot
    }

    fun clearFinished() {
        _finished.value = null
    }
}

/** The yoga snapshot on disk, so a process death resumes the same pose and count. */
class PreferencesYogaStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): YogaSessionSnapshot? = preferences.getString(KEY, null)?.let {
        runCatching { json.decodeFromString(YogaSessionSnapshot.serializer(), it) }.getOrNull()
    }

    fun save(snapshot: YogaSessionSnapshot) {
        preferences.edit()
            .putString(KEY, json.encodeToString(YogaSessionSnapshot.serializer(), snapshot))
            .commit()
    }

    fun clear() {
        preferences.edit().remove(KEY).commit()
    }

    private companion object {
        const val PREFS = "yoga-session"
        const val KEY = "session"
    }
}

/**
 * A yoga flow's timer with the screen off (`pr.md` §6.7): a `specialUse` foreground
 * service that ticks the pure player, persists the state every second and speaks the
 * pose cue when the voice is on. The session is saved through the same activity
 * repository as every other movement, so it feeds recovery and the energy card.
 */
class YogaSessionService :
    Service(),
    TextToSpeech.OnInitListener {
    private lateinit var store: PreferencesYogaStore
    private val handler = Handler(Looper.getMainLooper())
    private var snapshot: YogaSessionSnapshot? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var lastPoseId: String? = null
    private var saving = false

    private val ticker = object : Runnable {
        override fun run() {
            val current = snapshot
            if (current != null) {
                tick()
                handler.postDelayed(this, TICK_MILLIS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = PreferencesYogaStore(this)
        ensureChannel()
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START || intent?.action == ACTION_RECOVER) {
            beginForegroundPlaceholder()
        }
        when (intent?.action) {
            ACTION_START -> start(
                intent.getStringExtra(EXTRA_FLOW),
                intent.getBooleanExtra(EXTRA_VOICE, true),
            )
            ACTION_RECOVER -> recover()
            ACTION_PAUSE -> setPaused(true)
            ACTION_RESUME -> setPaused(false)
            ACTION_SKIP -> skip()
            ACTION_PREVIOUS -> previous()
            ACTION_REPEAT -> repeat()
            ACTION_FINISH -> finish()
            ACTION_VOICE -> setVoice(intent.getBooleanExtra(EXTRA_VOICE, true))
            else -> if (snapshot == null) recover()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts?.language = Locale.getDefault()
    }

    private fun start(flowId: String?, voiceOn: Boolean) {
        if (snapshot != null) return
        val flow = YogaLibrary.flow(flowId.orEmpty()) ?: run {
            stopSelf()
            return
        }
        val player = YogaPlayer.start(flow)
        val started = System.currentTimeMillis()
        val created = player.toSnapshot(flow.id, started, voiceOn)
        snapshot = created
        persist(created)
        beginForeground(created)
        handler.post(ticker)
        YogaSessionBus.publish(created)
        speakPose(created)
    }

    private fun recover() {
        val loaded = store.load() ?: run {
            stopSelf()
            return
        }
        snapshot = loaded
        beginForeground(loaded)
        handler.post(ticker)
        YogaSessionBus.publish(loaded)
    }

    private fun tick() {
        val current = snapshot ?: return
        val flow = YogaLibrary.flow(current.flowId) ?: run {
            finish()
            return
        }
        if (current.paused) return
        val state = current.toState()
        val next = YogaPlayer.tick(flow, state, 1)
        if (next.isDone) {
            finish()
            return
        }
        val updated = next.toSnapshot(current.flowId, current.startedAtMillis, current.voiceOn)
        snapshot = updated
        persist(updated)
        updateNotification(updated)
        YogaSessionBus.publish(updated)
        if (next.stepIndex != current.stepIndex) speakPose(updated)
    }

    private fun setVoice(on: Boolean) {
        val current = snapshot ?: return
        val updated = current.copy(voiceOn = on)
        snapshot = updated
        persist(updated)
        YogaSessionBus.publish(updated)
    }

    private fun setPaused(paused: Boolean) {
        val current = snapshot ?: return
        val updated = current.copy(paused = paused)
        snapshot = updated
        persist(updated)
        updateNotification(updated)
        YogaSessionBus.publish(updated)
    }

    private fun previous() {
        val current = snapshot ?: return
        val flow = YogaLibrary.flow(current.flowId) ?: return
        val next = YogaPlayer.previous(flow, current.toState())
        val updated = next.toSnapshot(current.flowId, current.startedAtMillis, current.voiceOn)
        snapshot = updated
        persist(updated)
        updateNotification(updated)
        YogaSessionBus.publish(updated)
        speakPose(updated)
    }

    private fun repeat() {
        val current = snapshot ?: return
        val flow = YogaLibrary.flow(current.flowId) ?: return
        val next = YogaPlayer.repeatStep(flow, current.toState())
        val updated = next.toSnapshot(current.flowId, current.startedAtMillis, current.voiceOn)
        snapshot = updated
        persist(updated)
        updateNotification(updated)
        YogaSessionBus.publish(updated)
        speakPose(updated)
    }

    private fun skip() {
        val current = snapshot ?: return
        val flow = YogaLibrary.flow(current.flowId) ?: return
        val next = YogaPlayer.skip(flow, current.toState())
        if (next.isDone) {
            finish()
            return
        }
        val updated = next.toSnapshot(current.flowId, current.startedAtMillis, current.voiceOn)
        snapshot = updated
        persist(updated)
        updateNotification(updated)
        YogaSessionBus.publish(updated)
        speakPose(updated)
    }

    private fun finish() {
        val current = snapshot ?: run {
            stopSelf()
            return
        }
        if (saving) return
        saving = true
        handler.removeCallbacks(ticker)
        val flow = YogaLibrary.flow(current.flowId)
        val container = container()
        if (flow != null && container != null) {
            val ended = kotlin.time.Instant.fromEpochMilliseconds(
                current.startedAtMillis + current.elapsedSeconds * 1_000L,
            )
            runCatching {
                container.activityLog.saveYoga(
                    flowId = flow.id,
                    startedAt = kotlin.time.Instant.fromEpochMilliseconds(current.startedAtMillis),
                    endedAt = ended,
                    muscles = YogaLoad.of(flow),
                )
            }
        }
        store.clear()
        snapshot = null
        YogaSessionBus.finished(current)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun speakPose(snapshot: YogaSessionSnapshot) {
        if (!snapshot.voiceOn || !ttsReady) return
        val flow = YogaLibrary.flow(snapshot.flowId) ?: return
        val pose = YogaPlayer.currentPose(flow, snapshot.toState()) ?: return
        if (pose.id == lastPoseId) return
        lastPoseId = pose.id
        tts?.speak("${pose.name}. ${pose.breathCue}", TextToSpeech.QUEUE_FLUSH, null, pose.id)
    }

    private fun persist(snapshot: YogaSessionSnapshot) {
        store.save(snapshot)
    }

    private fun container(): AppContainer? =
        runCatching { (application as OpenHealthApp).container }.getOrNull()

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                getString(R.string.yoga_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.yoga_channel_body)
                setShowBadge(false)
            },
        )
    }

    private fun beginForegroundPlaceholder() {
        val stub = YogaSessionSnapshot(
            flowId = "pending",
            stepIndex = 0,
            phase = YogaPhase.HOLD.name,
            secondsInPhase = 0,
            elapsedSeconds = 0,
            paused = false,
            totalSeconds = 0,
            stepCount = 0,
            startedAtMillis = System.currentTimeMillis(),
        )
        startForegroundCompat(buildNotification(stub))
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun beginForeground(snapshot: YogaSessionSnapshot) {
        val notification = buildNotification(snapshot)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(snapshot: YogaSessionSnapshot) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(snapshot))
    }

    private fun buildNotification(snapshot: YogaSessionSnapshot): Notification {
        val flow = YogaLibrary.flow(snapshot.flowId)
        val pose = flow?.let { YogaPlayer.currentPose(it, snapshot.toState()) }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val toggle = PendingIntent.getService(
            this,
            1,
            Intent(this, YogaSessionService::class.java).setAction(
                if (snapshot.paused) ACTION_RESUME else ACTION_PAUSE,
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val skip = PendingIntent.getService(
            this,
            2,
            Intent(this, YogaSessionService::class.java).setAction(ACTION_SKIP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            3,
            Intent(this, YogaSessionService::class.java).setAction(ACTION_FINISH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val flowName = flow?.name ?: getString(UiR.string.yoga_title)
        val text = pose?.let { "${it.name} · ${formatClock(remaining(snapshot, flow))}" }
            ?: getString(UiR.string.yoga_player_hold)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setContentTitle(flowName)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .addAction(
                0,
                getString(if (snapshot.paused) UiR.string.gps_resume else UiR.string.gps_pause),
                toggle,
            )
            .addAction(0, getString(UiR.string.yoga_player_skip), skip)
            .addAction(0, getString(UiR.string.gps_finish), stop)
            .build()
    }

    private fun remaining(
        snapshot: YogaSessionSnapshot,
        flow: app.openhealth.core.activity.YogaFlow?,
    ): Int {
        if (flow == null) return 0
        return YogaPlayer.phaseRemaining(flow, snapshot.toState())
    }

    private fun formatClock(seconds: Int): String =
        "%d:%02d".format(seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)

    private fun YogaPlayerState.toSnapshot(
        flowId: String,
        startedAtMillis: Long,
        voiceOn: Boolean,
    ): YogaSessionSnapshot = YogaSessionSnapshot(
        flowId = flowId,
        stepIndex = stepIndex,
        phase = phase.name,
        secondsInPhase = secondsInPhase,
        elapsedSeconds = elapsedSeconds,
        paused = paused,
        totalSeconds = totalSeconds,
        stepCount = stepCount,
        startedAtMillis = startedAtMillis,
        voiceOn = voiceOn,
    )

    private fun YogaSessionSnapshot.toState(): YogaPlayerState = YogaPlayerState(
        flowId = flowId,
        stepIndex = stepIndex,
        phase = runCatching { YogaPhase.valueOf(phase) }.getOrDefault(YogaPhase.HOLD),
        secondsInPhase = secondsInPhase,
        elapsedSeconds = elapsedSeconds,
        paused = paused,
        totalSeconds = totalSeconds,
        stepCount = stepCount,
    )

    companion object {
        const val ACTION_START = "app.openhealth.action.YOGA_START"
        const val ACTION_RECOVER = "app.openhealth.action.YOGA_RECOVER"
        const val ACTION_PAUSE = "app.openhealth.action.YOGA_PAUSE"
        const val ACTION_RESUME = "app.openhealth.action.YOGA_RESUME"
        const val ACTION_SKIP = "app.openhealth.action.YOGA_SKIP"
        const val ACTION_PREVIOUS = "app.openhealth.action.YOGA_PREVIOUS"
        const val ACTION_REPEAT = "app.openhealth.action.YOGA_REPEAT"
        const val ACTION_FINISH = "app.openhealth.action.YOGA_FINISH"
        const val ACTION_VOICE = "app.openhealth.action.YOGA_VOICE"
        const val EXTRA_FLOW = "flow"
        const val EXTRA_VOICE = "voice"
        private const val CHANNEL = "yoga_session"
        private const val NOTIFICATION_ID = 43
        private const val TICK_MILLIS = 1_000L

        fun start(context: Context, flowId: String, voiceOn: Boolean = true) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, YogaSessionService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_FLOW, flowId)
                    .putExtra(EXTRA_VOICE, voiceOn),
            )
        }

        fun pause(context: Context) = command(context, ACTION_PAUSE)
        fun resume(context: Context) = command(context, ACTION_RESUME)
        fun skip(context: Context) = command(context, ACTION_SKIP)
        fun previous(context: Context) = command(context, ACTION_PREVIOUS)
        fun repeat(context: Context) = command(context, ACTION_REPEAT)
        fun finish(context: Context) = command(context, ACTION_FINISH)
        fun setVoice(context: Context, on: Boolean) {
            context.startService(
                Intent(context, YogaSessionService::class.java)
                    .setAction(ACTION_VOICE)
                    .putExtra(EXTRA_VOICE, on),
            )
        }

        fun recoverIfNeeded(context: Context) {
            if (PreferencesYogaStore(context).load() == null) return
            ContextCompat.startForegroundService(
                context,
                Intent(context, YogaSessionService::class.java).setAction(ACTION_RECOVER),
            )
        }

        private fun command(context: Context, action: String) {
            context.startService(Intent(context, YogaSessionService::class.java).setAction(action))
        }
    }
}
