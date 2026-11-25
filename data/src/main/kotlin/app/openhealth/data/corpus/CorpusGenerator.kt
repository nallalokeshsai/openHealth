package app.openhealth.data.corpus

import app.openhealth.core.ids.IdGenerator
import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.RandomIdGenerator
import app.openhealth.core.ids.RawRecordId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.ingestion.RawRecord
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.observation.SourceKind
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.SystemClock
import app.openhealth.core.source.Source
import app.openhealth.core.source.SourcePermission
import app.openhealth.core.time.Days
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.db.Exercise
import app.openhealth.data.db.Measurement
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.db.Profile
import app.openhealth.data.db.Programme
import app.openhealth.data.db.Routine
import app.openhealth.data.db.Routine_exercise
import app.openhealth.data.db.Session
import app.openhealth.data.db.Session_exercise
import app.openhealth.data.db.Vital
import app.openhealth.data.db.Work_set
import app.openhealth.data.ingestion.PayloadHash
import app.openhealth.data.mapping.enumValueOrNull
import app.openhealth.data.repo.BodyRepository
import app.openhealth.data.repo.EntryDraft
import app.openhealth.data.repo.MealDraft
import app.openhealth.data.repo.NutritionRepository
import app.openhealth.data.repo.ObservationRepository
import app.openhealth.data.repo.ProfileRepository
import app.openhealth.data.repo.SourceRepository
import app.openhealth.data.repo.TrainingRepository
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

data class CorpusRequest(
    val days: Int = FIVE_YEARS_OF_DAYS,
    val seed: Long = 42L,
    val endDate: LocalDate,
    val zone: TimeZone = TimeZone.of("Europe/Berlin"),
    val profileId: String = "profile-demo",
    /**
     * Write the demo profile as part of the corpus. False when the corpus fills a
     * profile the person already created (demo mode after onboarding): the sample days
     * must never overwrite their name, goal or units.
     */
    val writeProfile: Boolean = true,
) {
    companion object {
        const val FIVE_YEARS_OF_DAYS = 365 * 5
    }
}

data class CorpusStats(
    val days: Int,
    val observations: Int,
    val rawRecords: Int,
    val sessions: Int,
    val sets: Int,
    val meals: Int,
    val entries: Int,
    val measurements: Int,
    val vitals: Int,
    val exercises: Int,
)

private data class SourcePoint(
    val sourceId: String,
    val kind: SourceKind,
    val recordedAt: Instant,
    val method: String? = null,
    val rawRecordId: String? = null,
)

private data class ExerciseSeed(
    val name: String,
    val muscle: String,
    val met: String,
    val baseWeightKg: Double,
)

private data class TrainingContext(
    val zone: TimeZone,
    val seeds: List<ExerciseSeed>,
    val exerciseIds: List<String>,
)

/**
 * The seeded five-year corpus (`BUILD-ROADMAP.md` §B2, `Tech-Stack.md` §10). It writes
 * through the same repositories the app uses, so the benchmark measures real queries
 * against realistic row counts: hourly steps, a daily weight, nightly sleep, three meals
 * with entries, three weekly sessions with sets, weekly tape measurements and occasional
 * vitals.
 *
 * Deterministic for a given seed and end date, so a benchmark failure is reproducible.
 */
@Suppress("LargeClass")
class CorpusGenerator(
    private val db: OpenHealthDb,
    private val idGenerator: IdGenerator = RandomIdGenerator(),
    private val clock: ClockSeam = SystemClock,
) {
    fun generate(request: CorpusRequest): CorpusStats {
        require(db.observationQueries.countObservations().executeAsOne() == 0L) {
            "CorpusGenerator writes a fresh database; this one already has observations"
        }
        val random = Random(request.seed)
        val startDate = request.endDate.minus(DatePeriod(days = request.days - 1))
        val days = Days.range(startDate, request.endDate)
        val observations = mutableListOf<Observation>()
        val rawRecords = mutableListOf<RawRecord>()

        var sessions = 0
        var sets = 0
        var meals = 0
        var entries = 0
        var measurements = 0
        var vitals = 0

        val context: TrainingContext = db.transactionWithResult {
            val sources = SourceRepository(db, clock)
            seedSources(sources, request.endDate)
            if (request.writeProfile) seedProfile(request)
            val training = seedTraining(request)
            days.forEachIndexed { dayIndex, date ->
                val dayStart = LocalDateTime(date, LocalTime(0, 0)).toInstant(request.zone)
                val dailyRandom = Random(random.nextLong())
                val steps = 5_500 + dailyRandom.nextInt(0, 4_500) +
                    if (date.dayOfWeek.name.startsWith("S")) 1_200 else 0
                val rawRecord = if (dayIndex % 7 == 0) {
                    rawStepsRecord(date, dayStart, steps).also { rawRecords += it }
                } else {
                    null
                }
                appendSteps(
                    observations,
                    dayStart,
                    steps,
                    dailyRandom,
                    dayIndex,
                    request.zone,
                    rawRecord?.id?.value,
                )
                appendBodyAndSleep(observations, dayStart, dailyRandom, dayIndex, request.zone)
                val nutrition = seedNutrition(date, dayStart, dailyRandom, request.zone)
                meals += nutrition.meals
                entries += nutrition.entries
                if (dayIndex % 30 == 0) {
                    measurements += seedMeasurement(date, dayStart, dailyRandom, request.zone)
                }
                if (dayIndex % 14 == 0) {
                    vitals += seedVital(date, dayStart, dailyRandom, request.zone)
                }
                if (dayIndex % 2 == 0 && date.dayOfWeek.name in TRAINING_DAYS) {
                    sets += seedSession(date, dayStart, training, dailyRandom)
                    sessions += 1
                }
            }
            training
        }

        val inserted = ObservationRepository(db).recordAll(observations, rawRecords)
        check(inserted == observations.size) {
            "The corpus repeated an idempotency key: inserted $inserted of ${observations.size}"
        }
        return CorpusStats(
            days = days.size,
            observations = observations.size,
            rawRecords = rawRecords.size,
            sessions = sessions,
            sets = sets,
            meals = meals,
            entries = entries,
            measurements = measurements,
            vitals = vitals,
            exercises = context.seeds.size,
        )
    }

    private fun appendSteps(
        sink: MutableList<Observation>,
        dayStart: Instant,
        steps: Int,
        random: Random,
        dayIndex: Int,
        zone: TimeZone,
        rawRecordId: String?,
    ) {
        var used = 0
        repeat(HOURS_TRACKED) { hour ->
            val remainingHours = HOURS_TRACKED - hour
            val remainingSteps = (steps - used).coerceAtLeast(0)
            val hourValue = if (remainingHours == 1) {
                remainingSteps
            } else {
                (remainingSteps / remainingHours + random.nextInt(-250, 250))
                    .coerceIn(0, remainingSteps)
            }
            used += hourValue
            val at = dayStart.plus((hour + FIRST_TRACKED_HOUR).hours)
            val secondSource = dayIndex % 21 == 0
            sink += observation(
                type = ObservationType.STEPS,
                value = Measure(hourValue.toDouble(), UcumUnit.STEP),
                start = at,
                end = at.plus(1.hours),
                point = SourcePoint(
                    sourceId = if (secondSource) "fitbit" else "garmin",
                    kind = if (secondSource) SourceKind.FITBIT else SourceKind.GARMIN,
                    recordedAt = at.plus(2.hours),
                    rawRecordId = rawRecordId,
                ),
                zone = zone,
            )
        }
    }

    private fun appendBodyAndSleep(
        sink: MutableList<Observation>,
        dayStart: Instant,
        random: Random,
        dayIndex: Int,
        zone: TimeZone,
    ) {
        val weight = 85.0 - dayIndex * WEIGHT_TREND_PER_DAY + random.nextDouble(-0.35, 0.35)
        sink += observation(
            type = ObservationType.WEIGHT,
            value = Measure(weight, UcumUnit.KILOGRAM),
            start = dayStart.plus(7.hours),
            end = null,
            point = SourcePoint(
                sourceId = if (dayIndex % 5 == 0) "manual" else "scale",
                kind = if (dayIndex % 5 == 0) SourceKind.MANUAL else SourceKind.GARMIN,
                recordedAt = dayStart.plus(7.hours),
                method = if (dayIndex % 5 == 0) null else "SCALE",
            ),
            zone = zone,
        )
        val sleepMinutes = 390 + random.nextInt(0, 120)
        sink += observation(
            type = ObservationType.SLEEP_DURATION,
            value = Measure(sleepMinutes * 60.0, UcumUnit.SECOND),
            start = dayStart.minus(6.hours),
            end = dayStart.plus((sleepMinutes - 360).toLong().hours),
            point = SourcePoint("garmin", SourceKind.GARMIN, dayStart.plus(8.hours)),
            zone = zone,
        )
        run {
            sink += observation(
                type = ObservationType.RESTING_HEART_RATE,
                value = Measure(52.0 + random.nextInt(0, 10), UcumUnit.PER_MINUTE),
                start = dayStart.plus(6.hours),
                end = null,
                point = SourcePoint("garmin", SourceKind.GARMIN, dayStart.plus(6.hours)),
                zone = zone,
            )
            sink += observation(
                type = ObservationType.HEART_RATE_VARIABILITY,
                value = Measure(0.045 + random.nextDouble(0.0, 0.02), UcumUnit.SECOND),
                start = dayStart.plus(6.hours),
                end = null,
                point = SourcePoint("garmin", SourceKind.GARMIN, dayStart.plus(6.hours)),
                zone = zone,
            )
        }
    }

    private fun observation(
        type: ObservationType,
        value: Measure,
        start: Instant,
        end: Instant?,
        point: SourcePoint,
        zone: TimeZone,
    ): Observation {
        val id = idGenerator.newId()
        return Observation(
            id = ObservationId(id),
            type = type,
            value = value.convertTo(type.canonicalUnit),
            start = start,
            end = end,
            provenance = Provenance(
                sourceId = SourceId(point.sourceId),
                time = TimeProvenance.of(start, point.recordedAt, zone),
                idempotencyKey = "corpus-$id",
                method = point.method?.let { enumValueOrNull(it) },
                rawRecordId = point.rawRecordId?.let(::RawRecordId),
            ),
        )
    }

    private fun rawStepsRecord(date: LocalDate, dayStart: Instant, steps: Int): RawRecord {
        val payload = """{"steps":$steps,"date":"$date"}"""
        return RawRecord(
            id = RawRecordId(idGenerator.newId()),
            sourceId = SourceId("garmin"),
            sourceKind = SourceKind.GARMIN,
            receivedAt = dayStart.plus(3.hours),
            recordedAt = dayStart.plus(3.hours),
            happenedAt = dayStart,
            payloadHash = PayloadHash.sha256(payload),
            idempotencyKey = "raw-steps-$date",
            schemaVersion = 2,
            device = "watch",
            payload = payload,
        )
    }

    private fun seedSources(sources: SourceRepository, endDate: LocalDate) {
        sources.upsertAll(
            listOf(
                Source(
                    id = SourceId("garmin"),
                    kind = SourceKind.GARMIN,
                    label = "Garmin",
                    device = "watch",
                    permissions = setOf(SourcePermission.READ_STEPS, SourcePermission.READ_SLEEP),
                ),
                Source(
                    id = SourceId("fitbit"),
                    kind = SourceKind.FITBIT,
                    label = "Fitbit",
                    permissions = setOf(SourcePermission.READ_STEPS),
                ),
                Source(
                    id = SourceId("manual"),
                    kind = SourceKind.MANUAL,
                    label = "Manual",
                    permissions = SourcePermission.entries.toSet(),
                ),
                Source(
                    id = SourceId("scale"),
                    kind = SourceKind.GARMIN,
                    label = "Scale",
                    family = "garmin",
                    permissions = setOf(SourcePermission.READ_WEIGHT),
                ),
            ),
        )
        sources.markSynced(SourceId("garmin"), endDate.atStartOfDayIn(TimeZone.UTC))
    }

    private fun seedProfile(request: CorpusRequest) {
        val now = clock.now().toEpochMilliseconds()
        ProfileRepository(db).upsert(
            Profile(
                id = request.profileId,
                name = "Demo",
                date_of_birth = "1990-04-12",
                sex_at_birth = "MALE",
                gender = null,
                height_cm = 180.0,
                weight_kg = 82.0,
                units = "metric",
                zone_id = request.zone.id,
                language = "en",
                goal = "lose_fat",
                activity_level = "MODERATELY_ACTIVE",
                training_experience = "intermediate",
                weight_neutral = false,
                intake_tier = 3L,
                created_at = now,
                updated_at = now,
                source_id = "manual",
                revision = 1L,
            ),
        )
    }

    private fun seedTraining(request: CorpusRequest): TrainingContext {
        val now = clock.now().toEpochMilliseconds()
        val training = TrainingRepository(db)
        val seeds = listOf(
            ExerciseSeed("Barbell squat", "quadriceps", "WEIGHT_LIFTING_MODERATE", 100.0),
            ExerciseSeed("Bench press", "chest", "WEIGHT_LIFTING_MODERATE", 80.0),
            ExerciseSeed("Deadlift", "hamstrings", "WEIGHT_LIFTING_MODERATE", 130.0),
            ExerciseSeed("Barbell row", "upper-back", "WEIGHT_LIFTING_MODERATE", 70.0),
            ExerciseSeed("Overhead press", "deltoids", "WEIGHT_LIFTING_MODERATE", 50.0),
        )
        val exerciseIds = seedCorpusExercises(training, seeds, idGenerator, now)
        val routineId = idGenerator.newId()
        training.upsertRoutine(
            Routine(
                id = routineId,
                name = "Full body A",
                folder = null,
                notes = null,
                archived_at = null,
                created_at = now,
                updated_at = now,
                source_id = "manual",
                revision = 1L,
                weekday = null,
                rotation_index = 1L,
                favourite = true,
                progression = "linear",
                target_rest_seconds = 120L,
            ),
        )
        seeds.forEachIndexed { index, seed ->
            training.upsertRoutineExercise(
                Routine_exercise(
                    id = idGenerator.newId(),
                    routine_id = routineId,
                    exercise_id = exerciseIds[index],
                    position = index.toLong(),
                    target_sets = 3L,
                    target_reps = 5L,
                    target_reps_high = 8L,
                    target_weight_kg = seed.baseWeightKg,
                    notes = null,
                    target_rir = 2.0,
                    rest_seconds = 120L,
                    progression = null,
                    group_index = null,
                ),
            )
        }
        val programmeId = idGenerator.newId()
        training.upsertProgramme(
            Programme(
                id = programmeId,
                name = "Five-year block",
                weeks = (request.days / 7).toLong(),
                notes = null,
                archived_at = null,
                created_at = now,
                updated_at = now,
                source_id = "manual",
                revision = 1L,
            ),
        )
        training.upsertProgrammeRoutine(idGenerator.newId(), programmeId, routineId, 1L, 0L)
        return TrainingContext(request.zone, seeds, exerciseIds)
    }

    private fun seedSession(
        date: LocalDate,
        dayStart: Instant,
        context: TrainingContext,
        random: Random,
    ): Int {
        val training = TrainingRepository(db)
        val sessionId = idGenerator.newId()
        val started = dayStart.plus(18.hours)
        training.upsertSession(
            Session(
                id = sessionId,
                started_at = started.toEpochMilliseconds(),
                ended_at = started.plus(1.hours).toEpochMilliseconds(),
                local_date = date.toString(),
                zone_id = context.zone.id,
                happened_at = started.toEpochMilliseconds(),
                recorded_at = started.plus(1.hours).toEpochMilliseconds(),
                routine_id = null,
                programme_id = null,
                notes = null,
                source_id = "manual",
                idempotency_key = "corpus-session-$sessionId",
                observation_id = null,
                raw_record_id = null,
                device = "manual",
                app_version = "fixture",
                revision = 1L,
                updated_at = started.plus(1.hours).toEpochMilliseconds(),
                title = "Full body",
                feeling = null,
                perceived_effort = null,
            ),
        )
        var position = 0L
        var sets = 0
        context.seeds.forEachIndexed { exerciseIndex, seed ->
            val sessionExerciseId = idGenerator.newId()
            training.upsertSessionExercise(
                Session_exercise(
                    id = sessionExerciseId,
                    session_id = sessionId,
                    exercise_id = context.exerciseIds[exerciseIndex],
                    position = exerciseIndex.toLong(),
                    notes = null,
                    group_index = null,
                    target_sets = 3L,
                    target_reps = 5L,
                    target_reps_high = 8L,
                    target_weight_kg = 100.0,
                    progression = "linear",
                    rest_seconds = 120L,
                ),
            )
            repeat(3) { setIndex ->
                val weight = seed.baseWeightKg + random.nextInt(-5, 6)
                val completedAt = started.plus((exerciseIndex * 8 + setIndex * 2).minutes)
                training.upsertSet(
                    Work_set(
                        id = idGenerator.newId(),
                        session_id = sessionId,
                        session_exercise_id = sessionExerciseId,
                        exercise_id = context.exerciseIds[exerciseIndex],
                        position = position++,
                        weight_kg = weight,
                        reps = 5L,
                        rir = (3 - setIndex).toDouble(),
                        rpe = null,
                        is_warmup = false,
                        is_backoff = false,
                        is_failure = false,
                        is_bodyweight = false,
                        tempo = null,
                        rest_seconds = 120L,
                        notes = null,
                        completed_at = completedAt.toEpochMilliseconds(),
                        happened_at = completedAt.toEpochMilliseconds(),
                        recorded_at = completedAt.toEpochMilliseconds(),
                        source_id = "manual",
                        idempotency_key = "corpus-set-${idGenerator.newId()}",
                        observation_id = null,
                        raw_record_id = null,
                        device = "manual",
                        app_version = "fixture",
                        revision = 1L,
                        is_drop = false,
                        is_assisted = false,
                        assistance_kg = null,
                        band_kg = null,
                        original_weight_kg = null,
                        original_reps = null,
                        edit_reason = null,
                        edited_at = null,
                        user_edited = false,
                    ),
                )
                sets += 1
            }
        }
        return sets
    }

    private data class NutritionCount(val meals: Int, val entries: Int)

    private fun seedNutrition(
        date: LocalDate,
        dayStart: Instant,
        random: Random,
        zone: TimeZone,
    ): NutritionCount {
        val nutrition = NutritionRepository(db)
        val entryFoods = foods.take(BASE_FOOD_COUNT)
        var entries = 0
        listOf("breakfast" to 7, "lunch" to 12, "dinner" to 19).forEach { (kind, hour) ->
            val mealId = idGenerator.newId()
            val at = dayStart.plus(hour.toLong().hours)
            nutrition.upsertMeal(
                MealDraft(
                    id = mealId,
                    name = kind,
                    kind = app.openhealth.data.repo.MealKind.fromKey(kind)
                        ?: app.openhealth.data.repo.MealKind.CUSTOM,
                    localDate = date,
                    zoneId = zone,
                    happenedAt = at,
                    recordedAt = at,
                    sourceId = "manual",
                    idempotencyKey = "corpus-meal-$mealId",
                ),
            )
            repeat(2 + random.nextInt(0, 3)) {
                val food = entryFoods[random.nextInt(entryFoods.size)]
                val quantity = 80 + random.nextInt(0, 220)
                nutrition.upsertEntry(
                    EntryDraft(
                        id = idGenerator.newId(),
                        mealId = mealId,
                        foodId = food.id,
                        localDate = date,
                        zoneId = zone,
                        happenedAt = at,
                        recordedAt = at.plus(5.minutes),
                        quantityG = quantity.toDouble(),
                        foodName = food.name,
                        totals = app.openhealth.core.nutrition.PortionMath.forGrams(
                            food.per100g,
                            quantity.toDouble(),
                        ),
                        sourceKind = "manual",
                        sourceId = "manual",
                        verified = true,
                        idempotencyKey = "corpus-entry-${idGenerator.newId()}",
                        device = "manual",
                        appVersion = "fixture",
                    ),
                )
                entries += 1
            }
        }
        return NutritionCount(meals = 3, entries = entries)
    }

    private val foods: List<app.openhealth.data.repo.FoodRecord> by lazy { createFoods() }

    private fun createFoods(): List<app.openhealth.data.repo.FoodRecord> {
        val nutrition = NutritionRepository(db)
        // Energy, protein, carbohydrate, fat and fibre per 100 g; approximate values for
        // a demo day, never presented as a measured food.
        val items = listOf(
            DemoFood("Oats", 379.0, 13.0, 68.0, 6.5, 10.1),
            DemoFood("Milk", 42.0, 3.4, 5.0, 1.0, 0.0),
            DemoFood("Banana", 89.0, 1.1, 22.8, 0.3, 2.6),
            DemoFood("Chicken breast", 165.0, 31.0, 0.0, 3.6, 0.0),
            DemoFood("Rice", 130.0, 2.7, 28.2, 0.3, 0.4),
            DemoFood("Broccoli", 34.0, 2.8, 6.6, 0.4, 2.6),
            DemoFood("Salmon", 208.0, 20.0, 0.0, 13.0, 0.0),
            DemoFood("Potato", 77.0, 2.0, 17.0, 0.1, 2.2),
            DemoFood("Yoghurt", 59.0, 10.0, 3.6, 0.4, 0.0),
            DemoFood("Apple", 52.0, 0.3, 13.8, 0.2, 2.4),
        )
        val base = items.map { food ->
            app.openhealth.data.repo.FoodDraft(
                id = "food-${food.name}",
                name = food.name,
                sourceKind = "bundled",
                foodType = app.openhealth.data.repo.FoodType.BUNDLED,
                per100g = mapOf(
                    app.openhealth.core.nutrition.Nutrient.ENERGY to food.energy,
                    app.openhealth.core.nutrition.Nutrient.PROTEIN to food.protein,
                    app.openhealth.core.nutrition.Nutrient.CARBS to food.carbs,
                    app.openhealth.core.nutrition.Nutrient.FAT to food.fat,
                    app.openhealth.core.nutrition.Nutrient.FIBRE to food.fibre,
                ),
                verified = true,
            )
        }
        // A few thousand rows so the food search benchmark measures an index, not a
        // ten-row table (the real index is B6's; this is the right shape).
        val indexed = (1..INDEXED_FOOD_COUNT).map { index ->
            app.openhealth.data.repo.FoodDraft(
                id = "food-indexed-$index",
                name = "Indexed food %04d".format(index),
                sourceKind = "synthetic",
                foodType = app.openhealth.data.repo.FoodType.BUNDLED,
                per100g = mapOf(
                    app.openhealth.core.nutrition.Nutrient.ENERGY to 100.0,
                    app.openhealth.core.nutrition.Nutrient.PROTEIN to 5.0,
                    app.openhealth.core.nutrition.Nutrient.CARBS to 10.0,
                    app.openhealth.core.nutrition.Nutrient.FAT to 3.0,
                    app.openhealth.core.nutrition.Nutrient.FIBRE to 1.0,
                ),
            )
        }
        return (base + indexed).map { nutrition.upsertFood(it) }
    }

    private fun seedMeasurement(
        date: LocalDate,
        dayStart: Instant,
        random: Random,
        zone: TimeZone,
    ): Int {
        BodyRepository(db).upsertMeasurement(
            Measurement(
                id = idGenerator.newId(),
                type = "waist",
                value_ = 84.0 + random.nextDouble(-2.0, 2.0),
                unit = "cm",
                local_date = date.toString(),
                zone_id = zone.id,
                happened_at = dayStart.plus(9.hours).toEpochMilliseconds(),
                recorded_at = dayStart.plus(9.hours).toEpochMilliseconds(),
                source_id = "manual",
                method = "MANUAL_ENTRY",
                notes = null,
                excluded_from_trends = false,
                observation_id = null,
                raw_record_id = null,
                idempotency_key = "corpus-measurement-${idGenerator.newId()}",
                device = "manual",
                app_version = "fixture",
                revision = 1L,
            ),
        )
        return 1
    }

    private fun seedVital(
        date: LocalDate,
        dayStart: Instant,
        random: Random,
        zone: TimeZone,
    ): Int {
        BodyRepository(db).upsertVital(
            Vital(
                id = idGenerator.newId(),
                type = "BLOOD_PRESSURE_SYSTOLIC",
                value_ = 118.0 + random.nextInt(0, 14),
                unit = "mm[Hg]",
                local_date = date.toString(),
                zone_id = zone.id,
                happened_at = dayStart.plus(10.hours).toEpochMilliseconds(),
                recorded_at = dayStart.plus(10.hours).toEpochMilliseconds(),
                source_id = "manual",
                method = "CUFF",
                context = "RESTING",
                note = null,
                excluded_from_trends = false,
                observation_id = null,
                raw_record_id = null,
                idempotency_key = "corpus-vital-${idGenerator.newId()}",
                device = "manual",
                app_version = "fixture",
                revision = 1L,
            ),
        )
        return 1
    }

    private companion object {
        const val BASE_FOOD_COUNT = 10
        const val INDEXED_FOOD_COUNT = 2_000
        const val HOURS_TRACKED = 16
        const val FIRST_TRACKED_HOUR = 7
        const val WEIGHT_TREND_PER_DAY = 0.0025
        val TRAINING_DAYS = setOf("MONDAY", "WEDNESDAY", "FRIDAY")
    }
}

/** The demo corpus's five lifts, written as layer-4 exercises with their muscles. */
private fun seedCorpusExercises(
    training: TrainingRepository,
    seeds: List<ExerciseSeed>,
    idGenerator: IdGenerator,
    now: Long,
): List<String> = seeds.map { seed ->
    val id = idGenerator.newId()
    training.upsertExercise(
        Exercise(
            id = id,
            name = seed.name,
            primary_muscles = seed.muscle,
            secondary_muscles = "",
            equipment = "barbell",
            instructions = null,
            level = "intermediate",
            mechanic = "compound",
            force = null,
            met_activity = seed.met,
            is_custom = false,
            source_id = "manual",
            created_at = now,
            updated_at = now,
            revision = 1L,
        ),
    )
    id
}

private data class DemoFood(
    val name: String,
    val energy: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fibre: Double,
)
