package app.openhealth.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.db.DatabaseOpener
import app.openhealth.data.db.MigrationBackup
import app.openhealth.data.db.OpenHealthStore
import java.util.Properties
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

fun inMemoryStore(backup: MigrationBackup? = null): OpenHealthStore =
    DatabaseOpener(backup).open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY))

fun fileStore(path: String): OpenHealthStore = DatabaseOpener().open(
    JdbcSqliteDriver("jdbc:sqlite:$path", Properties()),
)

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
