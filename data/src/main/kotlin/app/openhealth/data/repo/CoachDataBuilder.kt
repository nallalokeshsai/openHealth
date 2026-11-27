package app.openhealth.data.repo

import app.openhealth.core.ai.CoachDaily
import app.openhealth.core.ai.CoachDataset
import app.openhealth.core.ai.CoachProfile
import app.openhealth.core.ai.CoachRecovery
import app.openhealth.core.ai.CoachSessionSummary
import app.openhealth.core.ai.CoachVital
import app.openhealth.core.body.LoadedSet
import app.openhealth.core.body.RecoveryMap
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.rollup.DailyRollup
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.SystemClock
import app.openhealth.core.source.Source
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Assembles the one structure the coach may send (B15). It reads the same repositories
 * every screen reads; the consent filter and the Health Connect rule are applied by
 * `CoachPrompt` in `:core`, using the `viaHealthConnect` flag this builder attaches from
 * the source registry.
 *
 * Nothing is cached: a request is built at send time, so a value logged a moment ago is
 * included and a backfill changes the answer.
 */
class CoachDataBuilder(
    private val store: app.openhealth.data.LocalStore,
    private val planApplier: CoachPlanApplier,
    private val zone: TimeZone,
    private val clock: ClockSeam = SystemClock,
) {
    fun dataset(): CoachDataset {
        val now = clock.now()
        val today = now.toLocalDateTime(zone).date
        val sources = store.sources.all().associateBy(Source::id)
        return CoachDataset(
            profile = profile(),
            plan = planApplier.plan(),
            sessions = sessions(),
            nutrition = daily(
                ObservationType.NUTRITION_ENERGY,
                today,
                sources,
                transform = { it },
            ),
            sleep = daily(
                ObservationType.SLEEP_DURATION,
                today,
                sources,
                transform = { it / SECONDS_PER_HOUR },
            ),
            recovery = recovery(now),
            vitals = vitals(sources),
        )
    }

    private fun profile(): CoachProfile? {
        val profile = store.profile.active() ?: return null
        return CoachProfile(
            sex = profile.sex_at_birth,
            birthYear = profile.date_of_birth?.take(YEAR_CHARS)?.toIntOrNull(),
            heightCm = profile.height_cm,
            units = profile.units,
            goalMode = profile.goal,
            trainingGoal = profile.training_experience,
        )
    }

    private fun sessions(): List<CoachSessionSummary> =
        store.training.finishedSessions(MAX_SESSIONS.toLong())
            .map { session ->
                val sets = store.training.setsForSession(session.id)
                CoachSessionSummary(
                    date = session.local_date,
                    routineName = session.title
                        ?: session.routine_id?.let { store.training.routineById(it)?.name },
                    sets = sets.size,
                    volumeKg = sets.filterNot { it.is_warmup }
                        .sumOf { it.weight_kg * it.reps },
                    effort = session.perceived_effort,
                )
            }

    private fun daily(
        type: ObservationType,
        today: LocalDate,
        sources: Map<SourceId, Source>,
        transform: (Double) -> Double,
    ): List<CoachDaily> {
        val from = today.minus(MAX_DAYS - 1, DateTimeUnit.DAY)
        return store.rollups.forTypeRange(type, from, today).map { rollup: DailyRollup ->
            CoachDaily(
                date = rollup.localDate.toString(),
                value = transform(rollup.value.value),
                viaHealthConnect = rollup.sourceId?.let { sources[it]?.viaHealthConnect } ?: false,
            )
        }
    }

    private fun recovery(now: Instant): List<CoachRecovery> {
        val sets: List<LoadedSet> = store.training.loadedSets(
            (now - HISTORY).toEpochMilliseconds(),
        )
        return RecoveryMap.compute(now, sets)
            .values
            .filter { it.hoursSinceLoad != null }
            .map { state ->
                CoachRecovery(
                    muscle = state.muscle.key,
                    state = state.step.name,
                    hoursSince = state.hoursSinceLoad ?: 0.0,
                )
            }
    }

    private fun vitals(sources: Map<SourceId, Source>): List<CoachVital> =
        VITAL_TYPES.mapNotNull { type ->
            val observation = store.observations.latest(type) ?: return@mapNotNull null
            CoachVital(
                type = type,
                value = observation.value.value,
                unit = observation.value.unit.ucum,
                at = observation.provenance.happenedAt.toString(),
                viaHealthConnect = sources[observation.provenance.sourceId]?.viaHealthConnect
                    ?: false,
            )
        }

    private companion object {
        const val MAX_SESSIONS = 20
        const val MAX_DAYS = 14
        const val SECONDS_PER_HOUR = 3_600.0
        const val YEAR_CHARS = 4
        val HISTORY = 7.days

        val VITAL_TYPES = listOf(
            ObservationType.WEIGHT,
            ObservationType.BODY_FAT_PERCENT,
            ObservationType.RESTING_HEART_RATE,
            ObservationType.BLOOD_PRESSURE_SYSTOLIC,
            ObservationType.BLOOD_PRESSURE_DIASTOLIC,
        )
    }
}
