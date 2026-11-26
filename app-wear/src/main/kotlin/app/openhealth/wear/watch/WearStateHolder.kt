package app.openhealth.wear.watch

import app.openhealth.core.watch.WatchCommand
import app.openhealth.core.watch.WatchCommandType
import app.openhealth.core.watch.WatchLiveController
import app.openhealth.core.watch.WatchLiveState
import app.openhealth.core.watch.WatchSnapshot
import app.openhealth.wear.heart.WearHeartRate
import app.openhealth.wear.link.WearLink
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The watch's one state holder. It is deliberately thin: [WatchLiveController] owns the
 * arithmetic, [WatchStore] owns durability and [WearLink] owns the transport. Each action
 * is persisted before it is sent, and the next snapshot is the acknowledgement.
 */
@Suppress("LongParameterList", "TooManyFunctions")
class WearStateHolder(
    private val store: WatchStore,
    private val link: WearLink,
    private val heart: WearHeartRate,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val vibrate: () -> Unit = {},
    private val locale: () -> String = { java.util.Locale.getDefault().toString() },
) {
    private val _state = MutableStateFlow(loadInitial())
    val state: StateFlow<WatchLiveState> = _state.asStateFlow()

    private var resumed = false
    private var lastHeartRateSentAt = 0L

    init {
        scope.launch {
            link.snapshots.collect { snapshot -> onSnapshot(snapshot) }
        }
        scope.launch {
            link.connected.collect { connected ->
                _state.update { WatchLiveController.onConnection(it, connected) }
            }
        }
        scope.launch {
            while (true) {
                delay(TICK_MILLIS)
                _state.update { WatchLiveController.tick(it, now()) }
            }
        }
        scope.launch {
            while (true) {
                delay(SNAPSHOT_POLL_MILLIS)
                if (resumed) link.requestSnapshot()
            }
        }
    }

    fun onResume() {
        resumed = true
        link.requestSnapshot()
        if (heart.hasPermission()) startHeartRate()
    }

    fun onPause() {
        resumed = false
        heart.stop()
    }

    fun startHeartRate() {
        heart.start(::onHeartRate)
    }

    fun adjustWeight(steps: Int) {
        _state.update { WatchLiveController.adjustWeight(it, steps) }
    }

    fun adjustReps(steps: Int) {
        _state.update { WatchLiveController.adjustReps(it, steps) }
    }

    fun logSet() {
        val result = WatchLiveController.logSet(
            state = _state.value,
            commandId = UUID.randomUUID().toString(),
            nowMillis = now(),
            locale = locale(),
        )
        result.command?.let(::send)
        _state.value = result.state.copy(pendingCount = store.pendingCount())
        if (result.vibrate) vibrate()
    }

    fun start(routineId: String?) {
        val result = WatchLiveController.startSession(
            state = _state.value,
            commandId = UUID.randomUUID().toString(),
            nowMillis = now(),
            routineId = routineId,
            locale = locale(),
        )
        result.command?.let(::send)
        _state.value = result.state.copy(pendingCount = store.pendingCount())
    }

    fun finish() {
        val result = WatchLiveController.finishSession(
            state = _state.value,
            commandId = UUID.randomUUID().toString(),
            nowMillis = now(),
        )
        result.command?.let(::send)
        _state.value = result.state.copy(pendingCount = store.pendingCount())
    }

    fun extendRest() {
        _state.update { WatchLiveController.extendRest(it, now()) }
    }

    fun skipRest() {
        _state.update { WatchLiveController.skipRest(it, now()) }
    }

    private fun onSnapshot(snapshot: WatchSnapshot) {
        store.saveSnapshot(snapshot)
        store.acknowledge(snapshot.appliedCommandIds)
        _state.update { current ->
            WatchLiveController.onSnapshot(current, snapshot, now(), connected = true)
                .copy(pendingCount = store.pendingCount())
        }
        // Anything the phone has not acknowledged is offered again; the phone's
        // idempotency key makes a re-offer free.
        store.pending().forEach(link::send)
    }

    private fun onHeartRate(bpm: Int) {
        _state.update { WatchLiveController.heartRate(it, bpm) }
        val session = _state.value.session ?: return
        val at = now()
        if (at - lastHeartRateSentAt < HEART_RATE_INTERVAL_MILLIS) return
        lastHeartRateSentAt = at
        send(
            WatchCommand(
                id = UUID.randomUUID().toString(),
                type = WatchCommandType.HEART_RATE,
                happenedAtMillis = at,
                sessionId = session.id,
                heartRateBpm = bpm,
                locale = locale(),
            ),
        )
    }

    private fun send(command: WatchCommand) {
        store.addPending(command)
        link.send(command)
        _state.update { it.copy(pendingCount = store.pendingCount()) }
    }

    private fun loadInitial(): WatchLiveState {
        val snapshot = store.snapshot() ?: return WatchLiveState(
            nowMillis = now(),
            pendingCount = store.pendingCount(),
        )
        return WatchLiveController.onSnapshot(
            WatchLiveState(),
            snapshot,
            now(),
            connected = false,
        ).copy(pendingCount = store.pendingCount())
    }

    companion object {
        const val TICK_MILLIS: Long = 1_000L
        const val SNAPSHOT_POLL_MILLIS: Long = 10_000L
        const val HEART_RATE_INTERVAL_MILLIS: Long = 60_000L
    }
}
