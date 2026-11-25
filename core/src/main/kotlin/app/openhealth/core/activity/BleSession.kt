package app.openhealth.core.activity

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Reconnection for a paired device (`pr.md` §6.18: "a device that disconnects mid-workout
 * loses nothing"). The policy is pure so it can be tested without a radio: a short first
 * retry, doubling to a ceiling, and no retry at all when the person turned a device off.
 */
object BleRetry {
    val FIRST_DELAY: Duration = 1.seconds
    val MAX_DELAY: Duration = 30.seconds
    const val MAX_ATTEMPTS = 6

    fun delayFor(attempt: Int): Duration {
        if (attempt <= 0) return FIRST_DELAY
        val factor = 1L shl (attempt - 1).coerceAtMost(MAX_SHIFT)
        return minOf(FIRST_DELAY * factor.toInt(), MAX_DELAY)
    }

    fun shouldRetry(attempt: Int, autoConnect: Boolean): Boolean =
        autoConnect && attempt < MAX_ATTEMPTS

    private const val MAX_SHIFT = 5
}

/** Where a device connection currently is, for the Devices screen and the live chip. */
sealed interface DeviceLinkState {
    data object Idle : DeviceLinkState

    data class Scanning(val found: Int = 0) : DeviceLinkState

    data class Connecting(val address: String, val attempt: Int = 1) : DeviceLinkState

    data class Connected(val address: String, val kind: DeviceKind) : DeviceLinkState

    data class Reconnecting(val address: String, val attempt: Int, val inMillis: Long) :
        DeviceLinkState

    data class Unavailable(val reason: String) : DeviceLinkState
}

/**
 * Which measurements wait for the batched write and which go straight to the database.
 *
 * A heart-rate strap notifies about once a second. Writing each sample on its own is one
 * transaction and one day rebuild per second, which is 3,600 of each in an hour of
 * training and is exactly the kind of work the `pr.md` §6.26 budgets forbid during a
 * session. A strap's samples are therefore buffered and written as one batch on the
 * flush tick.
 *
 * A weight, a body composition or a blood pressure is a single reading the person is
 * standing there waiting for, so it is written the moment it is decoded (ADR 0013 §4).
 */
object BleWritePolicy {
    fun isStreaming(measurement: GattMeasurement): Boolean =
        measurement is GattMeasurement.HeartRate
}

/**
 * A bounded buffer for readings the radio produced while the database was busy or the
 * link had just dropped. It is small on purpose: a heart-rate sample every second for a
 * long workout is the worst case, and a full buffer drops the oldest reading rather than
 * growing without limit.
 */
class MeasurementBuffer<T>(private val capacity: Int = DEFAULT_CAPACITY) {
    private val items = ArrayDeque<T>()

    val size: Int get() = items.size

    fun add(item: T) {
        if (items.size >= capacity) items.removeFirst()
        items.addLast(item)
    }

    fun drain(): List<T> {
        val drained = items.toList()
        items.clear()
        return drained
    }

    fun clear() = items.clear()

    init {
        require(capacity > 0) { "a buffer needs room for at least one reading" }
    }

    companion object {
        /** Roughly twenty minutes of one-second heart rate. */
        const val DEFAULT_CAPACITY = 1_200
    }
}
