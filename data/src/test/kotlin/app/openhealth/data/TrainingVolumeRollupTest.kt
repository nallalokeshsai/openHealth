package app.openhealth.data

import app.openhealth.core.body.Muscle
import app.openhealth.core.formula.FormulaOutcome
import app.openhealth.core.formula.SessionVolumeCalculator
import app.openhealth.core.formula.VolumeSet
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.db.Exercise
import app.openhealth.data.db.Profile
import app.openhealth.data.db.Session
import app.openhealth.data.db.Session_exercise
import app.openhealth.data.db.Work_set
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant

/**
 * The day's `TRAINING_VOLUME` is the same number the session summary shows, so the layer-3
 * rollup uses the one formula in `:core` (`pr.md` §6.5, §7.3). A bodyweight set moves the
 * person's own mass less any assistance; counting it as zero kilograms made Stats and the
 * summary disagree about the same workout.
 */
class TrainingVolumeRollupTest {
    private val day = LocalDate(2026, 11, 23)
    private val ids = AtomicLong(0)

    private fun id(prefix: String) = "$prefix-${ids.incrementAndGet()}"

    private fun store(): LocalStore = inMemoryStore().let { LocalStore(it.driver, it.db) }

    private fun seedProfile(store: LocalStore, weightKg: Double?) {
        store.profile.upsert(
            Profile(
                id = "profile",
                name = "Sam",
                date_of_birth = "1990-04-12",
                sex_at_birth = "FEMALE",
                gender = null,
                height_cm = 170.0,
                weight_kg = weightKg,
                units = "metric",
                zone_id = TEST_ZONE.id,
                language = "en",
                goal = null,
                activity_level = null,
                training_experience = null,
                weight_neutral = false,
                intake_tier = 1L,
                created_at = 0L,
                updated_at = 0L,
                source_id = "manual",
                revision = 1L,
            ),
        )
    }

    private fun seedExercise(store: LocalStore, id: String, equipment: String) {
        store.training.upsertExercise(
            Exercise(
                id = id,
                name = id,
                primary_muscles = Muscle.UPPER_BACK.key,
                secondary_muscles = Muscle.BICEPS.key,
                equipment = equipment,
                instructions = null,
                level = "beginner",
                mechanic = "compound",
                force = "pull",
                met_activity = null,
                is_custom = false,
                source_id = "manual",
                created_at = 0L,
                updated_at = 0L,
                revision = 1L,
            ),
        )
    }

    /** One finished session on [day] whose sets are given as [VolumeSet]-shaped values. */
    private fun session(store: LocalStore, exerciseId: String, sets: List<VolumeSet>): String {
        val start = LocalDateTime(day, LocalTime(12, 0)).toInstant(TEST_ZONE)
        val sessionId = id("session")
        store.training.upsertSession(
            Session(
                id = sessionId,
                started_at = start.toEpochMilliseconds(),
                ended_at = (start + 1.hours).toEpochMilliseconds(),
                local_date = day.toString(),
                zone_id = TEST_ZONE.id,
                happened_at = start.toEpochMilliseconds(),
                recorded_at = (start + 1.hours).toEpochMilliseconds(),
                routine_id = null,
                programme_id = null,
                notes = null,
                source_id = "manual",
                idempotency_key = id("session-key"),
                observation_id = null,
                raw_record_id = null,
                device = "manual",
                app_version = "test",
                revision = 1L,
                updated_at = start.toEpochMilliseconds(),
                title = "Pull day",
                feeling = null,
                perceived_effort = null,
            ),
        )
        val entryId = id("entry")
        store.training.upsertSessionExercise(
            Session_exercise(
                id = entryId,
                session_id = sessionId,
                exercise_id = exerciseId,
                position = 0L,
                notes = null,
                group_index = null,
                target_sets = sets.size.toLong(),
                target_reps = 5L,
                target_reps_high = 8L,
                target_weight_kg = null,
                progression = "linear",
                rest_seconds = 120L,
            ),
        )
        sets.forEachIndexed { index, set ->
            val at = start + (index + 1).minutes
            store.training.upsertSet(
                Work_set(
                    id = id("set"),
                    session_id = sessionId,
                    session_exercise_id = entryId,
                    exercise_id = exerciseId,
                    position = index.toLong(),
                    weight_kg = set.weightKg,
                    reps = set.reps.toLong(),
                    rir = null,
                    rpe = null,
                    is_warmup = set.isWarmup,
                    is_backoff = false,
                    is_failure = false,
                    is_bodyweight = set.isBodyweight,
                    tempo = null,
                    rest_seconds = null,
                    notes = null,
                    completed_at = at.toEpochMilliseconds(),
                    happened_at = at.toEpochMilliseconds(),
                    recorded_at = at.toEpochMilliseconds(),
                    source_id = "manual",
                    idempotency_key = id("set-key"),
                    observation_id = null,
                    raw_record_id = null,
                    device = "manual",
                    app_version = "test",
                    revision = 1L,
                    is_drop = false,
                    is_assisted = set.assistanceKg > 0.0,
                    assistance_kg = set.assistanceKg.takeIf { it > 0.0 },
                    band_kg = set.bandKg.takeIf { it > 0.0 },
                    original_weight_kg = null,
                    original_reps = null,
                    edit_reason = null,
                    edited_at = null,
                    user_edited = false,
                ),
            )
        }
        return sessionId
    }

    private fun volumeOn(store: LocalStore): Double? =
        store.rollups.forDayAndType(day, ObservationType.TRAINING_VOLUME)?.value?.value

    @Test
    fun aBodyweightSetCountsThePersonsMassAndAnAssistedSetSubtractsTheMachine() {
        val store = store()
        seedProfile(store, weightKg = 80.0)
        seedExercise(store, "pull-up", equipment = "bodyweight")
        session(
            store,
            "pull-up",
            listOf(
                VolumeSet(weightKg = 0.0, reps = 5, isBodyweight = true),
                VolumeSet(weightKg = 0.0, reps = 5, isBodyweight = true, assistanceKg = 20.0),
            ),
        )
        // 80 kg x 5 + (80 - 20) kg x 5
        assertEquals(700.0, assertNotNull(volumeOn(store)), 1e-9)
        store.close()
    }

    @Test
    fun theRollupAgreesWithTheSessionSummaryFormula() {
        val store = store()
        seedProfile(store, weightKg = 80.0)
        seedExercise(store, "pull-up", equipment = "bodyweight")
        seedExercise(store, "row", equipment = "barbell")
        val sets = listOf(
            VolumeSet(weightKg = 0.0, reps = 8, isBodyweight = true, bodyweightKg = 80.0),
            VolumeSet(
                weightKg = 10.0,
                reps = 6,
                isBodyweight = true,
                assistanceKg = 15.0,
                bodyweightKg = 80.0,
            ),
            VolumeSet(weightKg = 20.0, reps = 10, isWarmup = true, bodyweightKg = 80.0),
        )
        session(store, "pull-up", sets)
        session(store, "row", listOf(VolumeSet(weightKg = 60.0, reps = 5, bandKg = 10.0)))
        val summary = SessionVolumeCalculator.compute(
            sets + VolumeSet(weightKg = 60.0, reps = 5, bandKg = 10.0),
        )
        assertEquals(
            (summary as FormulaOutcome.Computed).value.externalLoadKg,
            assertNotNull(volumeOn(store)),
            1e-9,
        )
        store.close()
    }

    @Test
    fun theDaysWeighInWinsOverTheProfileWeight() {
        val store = store()
        seedProfile(store, weightKg = 80.0)
        seedExercise(store, "pull-up", equipment = "bodyweight")
        val at = LocalDateTime(day, LocalTime(7, 0)).toInstant(TEST_ZONE)
        store.observations.recordAll(
            listOf(
                testObservation(
                    id = "weigh-in",
                    type = ObservationType.WEIGHT,
                    value = 90.0,
                    unit = UcumUnit.KILOGRAM,
                    start = at,
                    sourceId = "manual",
                ),
            ),
        )
        session(
            store,
            "pull-up",
            listOf(VolumeSet(weightKg = 0.0, reps = 5, isBodyweight = true)),
        )
        assertEquals(450.0, assertNotNull(volumeOn(store)), 1e-9)
        store.close()
    }

    @Test
    fun anUnknownBodyweightAddsNoInventedKilograms() {
        val store = store()
        seedProfile(store, weightKg = null)
        seedExercise(store, "pull-up", equipment = "bodyweight")
        session(
            store,
            "pull-up",
            listOf(
                VolumeSet(weightKg = 0.0, reps = 5, isBodyweight = true),
                VolumeSet(weightKg = 40.0, reps = 5),
            ),
        )
        assertEquals(200.0, assertNotNull(volumeOn(store)), 1e-9)
        store.close()
    }
}
