package app.openhealth.android.activity

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.PowerManager
import app.openhealth.core.activity.StepCounter
import app.openhealth.core.activity.StepCounterSample
import app.openhealth.core.activity.StepCounterState
import app.openhealth.core.activity.StepInterval
import app.openhealth.core.activity.StepSampling
import app.openhealth.core.ids.IdGenerator
import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.MeasurementMethod
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.LocalStore
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/**
 * Passive step tracking (`pr.md` §6.6, `BUILD-ROADMAP.md` §B8): the hardware counter,
 * batched, with the delta written per interval so a reboot, a process death or a lost
 * service costs nothing the sensor still remembers.
 *
 * Battery is not spent on sampling rate: `TYPE_STEP_COUNTER` is a hardware counter that
 * reports on its own, and the registration asks the platform to batch events
 * ([StepSampling], 30 s to 15 min). The service exists to keep the process alive long
 * enough to persist an interval, not to poll.
 */
@Suppress("LongParameterList")
class StepTracker(
    private val context: Context,
    private val store: LocalStore,
    private val clock: ClockSeam,
    private val zone: TimeZone,
    private val ids: IdGenerator,
    private val appVersion: String,
    private val sourceId: SourceId = PHONE_SOURCE,
) : SensorEventListener,
    AutoCloseable {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val battery = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    private var registered = false
    private var cached: StepCounterState? = null

    fun isAvailable(): Boolean = counter() != null

    fun start() {
        val sensor = counter() ?: return
        if (registered) return
        val micros = interval().inWholeMicroseconds.coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
        registered = sensorManager?.registerListener(this, sensor, micros, micros) ?: false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val count = event.values.firstOrNull()?.toLong() ?: return
        persist(StepCounterSampleFactory.of(count, event.timestamp, clock.now()))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /**
     * Applies one reading. It is public so the service, the app's foreground catch-up and
     * the instrumented exit test all run the same path.
     */
    fun persist(sample: StepCounterSample): Boolean {
        val state = cached ?: store.devices.stepState().also { cached = it }
        val update = StepCounter.apply(state, sample, zone)
        cached = update.state
        store.devices.saveStepState(update.state)
        if (update.intervals.isEmpty()) return false
        val observations = update.intervals.map { interval -> observation(interval, sample.at) }
        return store.observations.recordAll(observations) > 0
    }

    private fun interval() = StepSampling.interval(
        screenOn = power?.isInteractive ?: false,
        workoutActive = false,
        batteryPercent = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
    )

    private fun observation(interval: StepInterval, recordedAt: Instant): Observation {
        val provenance = Provenance(
            sourceId = sourceId,
            time = TimeProvenance.of(interval.start, recordedAt, zone),
            idempotencyKey = "steps:${sourceId.value}:${interval.start.epochSeconds}:" +
                "${interval.end.epochSeconds}:${interval.steps}",
            method = MeasurementMethod.DEVICE_SENSOR,
            appVersion = appVersion,
            device = "This phone",
        )
        return Observation(
            id = ObservationId(ids.newId()),
            type = ObservationType.STEPS,
            value = Measure(interval.steps.toDouble(), UcumUnit.STEP),
            start = interval.start,
            end = interval.end.takeIf { it > interval.start },
            provenance = provenance,
        )
    }

    private fun counter(): Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    override fun close() {
        if (registered) sensorManager?.unregisterListener(this)
        registered = false
    }

    companion object {
        /** One source per phone, not per boot: it is the same sensor throughout. */
        val PHONE_SOURCE = SourceId("phone-steps")
    }
}

/** Builds the reading from a sensor event; split out so a test can feed the same shape. */
object StepCounterSampleFactory {
    private const val NANOS_PER_MILLI = 1_000_000L
    private const val BOOT_ROUNDING_MILLIS = 60_000L

    fun of(count: Long, nanosSinceBoot: Long, at: Instant): StepCounterSample = StepCounterSample(
        count = count,
        bootEpochMillis = bootEpochFor(nanosSinceBoot, at),
        at = at,
    )

    /**
     * The boot identity. The sensor event's timestamp is nanoseconds since boot, so the
     * wall clock at boot is now minus that; rounding to the minute makes two readings of
     * the same boot agree instead of differing by a millisecond.
     */
    fun bootEpochFor(nanosSinceBoot: Long, now: Instant): Long {
        val boot = now - (nanosSinceBoot / NANOS_PER_MILLI).milliseconds
        return boot.toEpochMilliseconds() / BOOT_ROUNDING_MILLIS * BOOT_ROUNDING_MILLIS
    }
}
