package app.openhealth.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.observation.SourceKind
import app.openhealth.core.source.Source
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.db.DatabaseDriverFactory
import app.openhealth.data.db.DatabaseOpener
import app.openhealth.data.db.MigrationBackup
import app.openhealth.data.db.OpenHealthStore
import app.openhealth.data.db.openHealthDatabase
import app.openhealth.data.repo.SourceRepository
import java.util.Properties
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Foreign keys are a connection property for sqlite-jdbc; `PRAGMA foreign_keys` on an
 * open connection is ignored. Tests therefore build drivers the same way the app does.
 */
fun jdbcFactory(url: String): DatabaseDriverFactory = DatabaseDriverFactory { foreignKeys ->
    JdbcSqliteDriver(
        url,
        Properties().apply { setProperty("foreign_keys", foreignKeys.toString()) },
    )
}

fun inMemoryStore(backup: MigrationBackup? = null): OpenHealthStore =
    DatabaseOpener(backup).open(jdbcFactory(JdbcSqliteDriver.IN_MEMORY)).also(::seedSources)

fun fileStore(path: String): OpenHealthStore =
    DatabaseOpener().open(jdbcFactory("jdbc:sqlite:$path")).also(::seedSources)

/**
 * The sources the fixtures write against. `observation.source_id` and every domain row's
 * source is a real foreign key, so a test that writes a value needs the source that
 * produced it to exist, exactly as the app does.
 */
val TEST_SOURCES: List<Source> = listOf(
    Source(id = SourceId("manual"), kind = SourceKind.MANUAL, label = "Manual entry"),
    Source(id = SourceId("garmin"), kind = SourceKind.GARMIN, label = "Garmin"),
    Source(id = SourceId("fitbit"), kind = SourceKind.FITBIT, label = "Fitbit"),
)

fun seedSources(store: OpenHealthStore, sources: List<Source> = TEST_SOURCES) {
    SourceRepository(openHealthDatabase(store.driver)).upsertAll(sources)
}

val TEST_ZONE: TimeZone = TimeZone.of("Europe/Berlin")

fun testObservation(
    id: String,
    type: ObservationType,
    value: Double,
    unit: UcumUnit,
    start: Instant,
    sourceId: String = "garmin",
    recordedAt: Instant = start.plus(1.hours),
    end: Instant? = null,
): Observation = Observation(
    id = ObservationId(id),
    type = type,
    value = Measure(value, unit).convertTo(type.canonicalUnit),
    start = start,
    end = end,
    provenance = Provenance(
        sourceId = SourceId(sourceId),
        time = TimeProvenance.of(start, recordedAt, TEST_ZONE),
        idempotencyKey = id,
    ),
)

fun dayStart(date: LocalDate): Instant =
    kotlinx.datetime.LocalDateTime(date, kotlinx.datetime.LocalTime(0, 0))
        .toInstant(TEST_ZONE)

fun hourlySteps(
    idPrefix: String,
    date: LocalDate,
    sourceId: String,
    perHour: Double,
): List<Observation> = (0 until 24).map { hour ->
    testObservation(
        id = "$idPrefix-$hour",
        type = ObservationType.STEPS,
        value = perHour,
        unit = UcumUnit.STEP,
        start = dayStart(date).plus(hour.hours),
        end = dayStart(date).plus((hour + 1).hours),
        sourceId = sourceId,
    )
}

/**
 * The parent rows a layer-4 value now needs. `entry.food_id`, `work_set.session_id` and
 * `work_set.exercise_id` are real foreign keys, so a fixture that writes a set or a diary
 * entry must first write the session, exercise or food it names, as the app does.
 */
fun seedFood(store: OpenHealthStore, id: String, name: String = "Oats") {
    app.openhealth.data.repo.NutritionRepository(store.db).upsertFood(
        app.openhealth.data.repo.FoodDraft(
            id = id,
            name = name,
            sourceKind = "manual",
            per100g = mapOf(
                app.openhealth.core.nutrition.Nutrient.ENERGY to 379.0,
                app.openhealth.core.nutrition.Nutrient.PROTEIN to 13.0,
                app.openhealth.core.nutrition.Nutrient.CARBS to 68.0,
                app.openhealth.core.nutrition.Nutrient.FAT to 6.5,
                app.openhealth.core.nutrition.Nutrient.FIBRE to 10.1,
            ),
        ),
    )
}

fun seedSessionAndExercise(
    store: OpenHealthStore,
    sessionId: String,
    exerciseId: String,
    happenedAt: Instant,
    date: LocalDate,
) {
    val training = app.openhealth.data.repo.TrainingRepository(store.db)
    training.upsertExercise(
        app.openhealth.data.db.Exercise(
            id = exerciseId,
            name = exerciseId,
            primary_muscles = "quadriceps",
            secondary_muscles = "gluteal",
            equipment = "barbell",
            instructions = null,
            level = null,
            mechanic = null,
            force = null,
            met_activity = null,
            is_custom = false,
            source_id = null,
            created_at = happenedAt.toEpochMilliseconds(),
            updated_at = happenedAt.toEpochMilliseconds(),
            revision = 1L,
        ),
    )
    training.upsertSession(
        app.openhealth.data.db.Session(
            id = sessionId,
            started_at = happenedAt.toEpochMilliseconds(),
            ended_at = null,
            local_date = date.toString(),
            zone_id = TEST_ZONE.id,
            happened_at = happenedAt.toEpochMilliseconds(),
            recorded_at = happenedAt.toEpochMilliseconds(),
            routine_id = null,
            programme_id = null,
            notes = null,
            source_id = "manual",
            idempotency_key = "session-$sessionId",
            observation_id = null,
            raw_record_id = null,
            device = "manual",
            app_version = "test",
            revision = 1L,
            updated_at = happenedAt.toEpochMilliseconds(),
            title = null,
            feeling = null,
            perceived_effort = null,
        ),
    )
}
