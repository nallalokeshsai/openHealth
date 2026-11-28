package app.openhealth.android.wear

import app.openhealth.android.training.TrainingPresenter
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
import app.openhealth.core.watch.WatchCommand
import app.openhealth.core.watch.WatchCommandType
import app.openhealth.core.watch.WatchProtocol
import app.openhealth.data.LocalStore
import app.openhealth.ui.screens.train.LiveSetKind
import app.openhealth.ui.screens.train.LoggedSet
import java.util.Locale
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/** Which wrist sent a command; the two have different sources and devices. */
enum class WatchKind {
    WEAR,
    CONNECT_IQ,
    ;

    val device: String
        get() = when (this) {
            WEAR -> "wear"
            CONNECT_IQ -> "connect-iq"
        }
}

enum class WatchApply {
    APPLIED,
    DUPLICATE,
    REJECTED,
}

/**
 * Applies a watch command through the same training path as the phone (`docs/adr/0019`).
 *
 * A command is validated in `:core` before this class sees it, is idempotent by its own
 * id, and writes `happenedAt` from the wrist with `recordedAt` from the phone. It cannot
 * create a rollup, a plan or a second source of truth; a missing session is a rejection,
 * not a new session.
 */
@Suppress("LongParameterList")
class WatchCommandHandler(
    private val store: LocalStore,
    private val training: TrainingPresenter,
    private val clock: ClockSeam,
    private val zone: TimeZone,
    private val ids: IdGenerator,
    private val applied: WatchAppliedStore,
    private val wearSource: SourceId,
    private val garminSource: SourceId,
) {
    @Suppress("ReturnCount")
    fun apply(command: WatchCommand, kind: WatchKind): WatchApply {
        if (WatchProtocol.validate(command) != null) return WatchApply.REJECTED
        if (command.id in applied.recent()) return WatchApply.DUPLICATE
        val now = clock.now()
        val happened = Instant.fromEpochMilliseconds(
            command.happenedAtMillis.coerceIn(1L, now.toEpochMilliseconds()),
        )
        val source = if (kind == WatchKind.WEAR) wearSource else garminSource
        val outcome = when (command.type) {
            WatchCommandType.START_SESSION -> start(command, source, kind, happened)
            WatchCommandType.LOG_SET -> logSet(command, source, kind, happened)
            WatchCommandType.FINISH_SESSION -> finish(command, happened)
            WatchCommandType.HEART_RATE -> heartRate(command, source, kind, happened, now)
        }
        if (outcome == WatchApply.APPLIED) applied.record(command.id)
        return outcome
    }

    private fun start(
        command: WatchCommand,
        source: SourceId,
        kind: WatchKind,
        happened: Instant,
    ): WatchApply {
        val routineId = command.routineId?.takeIf { store.training.routineById(it) != null }
        training.startSession(
            routineId = routineId,
            sourceId = source,
            device = kind.device,
            idempotencyKey = "watch-session-${command.id}",
            startedAtMillis = happened.toEpochMilliseconds(),
        )
        return WatchApply.APPLIED
    }

    @Suppress("ReturnCount")
    private fun logSet(
        command: WatchCommand,
        source: SourceId,
        kind: WatchKind,
        happened: Instant,
    ): WatchApply {
        val requested = command.sessionId
        val session = if (requested != null) {
            store.training.sessionById(requested) ?: return WatchApply.REJECTED
        } else {
            store.training.openSession() ?: return WatchApply.REJECTED
        }
        if (session.ended_at != null) return WatchApply.REJECTED
        if (store.training.exerciseById(command.exerciseId.orEmpty()) == null) {
            return WatchApply.REJECTED
        }
        if (store.training.sessionExercises(session.id).none {
                it.exercise_id == command.exerciseId
            }
        ) {
            return WatchApply.REJECTED
        }
        training.logSet(
            sessionId = session.id,
            logged = LoggedSet(
                exerciseId = command.exerciseId.orEmpty(),
                weight = formatWeight(command.weightKg ?: 0.0),
                reps = (command.reps ?: 0).toString(),
                rir = command.rir?.let { trim(it) },
                kind = if (command.isWarmup) LiveSetKind.WARMUP else LiveSetKind.WORK,
                notes = null,
            ),
            sourceId = source,
            device = kind.device,
            happenedAtMillis = happened.toEpochMilliseconds(),
            idempotencyKey = "watch-set-${command.id}",
            weightKgOverride = command.weightKg,
            appVersionOverride = null,
            sessionExerciseId = command.sessionExerciseId,
        )
        return WatchApply.APPLIED
    }

    @Suppress("ReturnCount")
    private fun finish(command: WatchCommand, happened: Instant): WatchApply {
        val requested = command.sessionId
        val session = if (requested != null) {
            store.training.sessionById(requested) ?: return WatchApply.REJECTED
        } else {
            store.training.openSession() ?: return WatchApply.REJECTED
        }
        training.finishSession(session.id, endedAtMillis = happened.toEpochMilliseconds())
        return WatchApply.APPLIED
    }

    private fun heartRate(
        command: WatchCommand,
        source: SourceId,
        kind: WatchKind,
        happened: Instant,
        now: Instant,
    ): WatchApply {
        val bpm = command.heartRateBpm ?: return WatchApply.REJECTED
        val rawId = store.raw.recordManual(
            rawId = ids.newId(),
            sourceId = source,
            sourceKind = app.openhealth.core.observation.SourceKind.DEVICE,
            kind = "watch-hr",
            payload = "watch-hr;command=${command.id};bpm=$bpm;" +
                "at=${happened.toEpochMilliseconds()}",
            happenedAt = happened,
            recordedAt = now,
            appVersion = null,
        )
        store.observations.record(
            Observation.of(
                id = ObservationId(ids.newId()),
                type = ObservationType.HEART_RATE,
                value = Measure(bpm.toDouble(), UcumUnit.PER_MINUTE),
                start = happened,
                provenance = Provenance(
                    sourceId = source,
                    time = TimeProvenance.of(happened, now, zone),
                    idempotencyKey = "watch-hr-${command.id}",
                    device = kind.device,
                    appVersion = null,
                    method = MeasurementMethod.WATCH_OPTICAL,
                    rawRecordId = app.openhealth.core.ids.RawRecordId(rawId),
                ),
            ),
        )
        return WatchApply.APPLIED
    }

    private fun formatWeight(kg: Double): String =
        String.format(Locale.ROOT, "%.3f", kg).trimEnd('0').trimEnd('.')

    private fun trim(value: Double): String =
        String.format(Locale.ROOT, "%.1f", value).trimEnd('0').trimEnd('.')
}
