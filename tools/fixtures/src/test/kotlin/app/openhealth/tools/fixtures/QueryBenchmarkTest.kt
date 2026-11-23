package app.openhealth.tools.fixtures

import app.openhealth.core.source.SourceRegistry
import app.openhealth.data.corpus.CorpusGenerator
import app.openhealth.data.corpus.CorpusRequest
import app.openhealth.data.db.DatabaseOpener
import app.openhealth.data.repo.ObservationRepository
import app.openhealth.data.repo.RollupRepository
import app.openhealth.data.repo.TimelineRepository
import app.openhealth.data.rollup.RollupEngine
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/**
 * The database budget of `Tech-Stack.md` §12: on a five-year corpus, no UI query takes
 * more than 16 ms. This benchmark builds the corpus once, rebuilds every rollup through
 * the real engine, warms each query, then takes the best of five runs. Best-of-five is
 * the reproducible number: it removes CI scheduling noise while still proving the index
 * is there, which a missing index would miss by orders of magnitude.
 */
class QueryBenchmarkTest {
    private val endDate = LocalDate(2026, 11, 22)
    private val budgetMillis = 16.0

    @Test
    fun noUiQueryExceedsTheBudgetOnAFiveYearCorpus() {
        val directory = Files.createTempDirectory("openhealth-benchmark")
        val path = directory.resolve("corpus.db").toString()
        val store = DatabaseOpener().open(jdbcFactory("jdbc:sqlite:$path"))
        val generationStarted = System.nanoTime()
        val stats = CorpusGenerator(store.db).generate(
            CorpusRequest(days = FIVE_YEARS, seed = 42L, endDate = endDate),
        )
        val generationMillis = elapsedMillis(generationStarted)
        println("corpus: $stats built in ${"%.0f".format(generationMillis)} ms")

        val observations = ObservationRepository(store.db)
        val rollups = RollupRepository(store.db)
        val engine = RollupEngine(
            observations,
            rollups,
            store.db,
            SourceRegistry(app.openhealth.data.repo.SourceRepository(store.db).all()),
        )
        val rollupStarted = System.nanoTime()
        val rollupRows = engine.rebuildAll()
        println(
            "rollups: $rollupRows rows rebuilt in " +
                "${"%.0f".format(elapsedMillis(rollupStarted))} ms",
        )

        val timeline = TimelineRepository(store.db, store.driver)
        val today = endDate
        val monthAgo = today.minus(DatePeriod(days = 30))
        val weight = observations.latest(app.openhealth.core.observation.ObservationType.WEIGHT)!!
        val session = store.db.trainingQueries.recentSessions(1).executeAsOne()
        val exercise = store.db.trainingQueries.allExercises().executeAsList().first()
        val fromMillis = monthAgo.atStartOfDayMillis()
        val toMillis = today.plus(DatePeriod(days = 1)).atStartOfDayMillis()

        val queries = linkedMapOf<String, () -> Unit>(
            "observationsForDay" to {
                observations.forDay(today)
            },
            "observationsForDayAndType" to {
                store.db.observationQueries
                    .observationsForDayAndType(today.toString(), "WEIGHT")
                    .executeAsList()
            },
            "weightSeries30d" to {
                observations.forTypeBetween(
                    app.openhealth.core.observation.ObservationType.WEIGHT,
                    weight.start.minus(30.days),
                    weight.start,
                )
            },
            "latestWeight" to {
                observations.latest(app.openhealth.core.observation.ObservationType.WEIGHT)
            },
            "rollups30d" to {
                rollups.forRange(monthAgo, today)
            },
            "timelineForDay" to {
                store.db.timelineQueries.timelineForDay(today.toString()).executeAsList()
            },
            "timelinePage50" to {
                timeline.page(50)
            },
            "setsForSession" to {
                store.db.trainingQueries.setsForSession(session.id).executeAsList()
            },
            "setsForExercise30d" to {
                store.db.trainingQueries
                    .setsForExerciseBetween(exercise.id, fromMillis, toMillis)
                    .executeAsList()
            },
            "entriesForDay" to {
                store.db.nutritionQueries.entriesForDay(today.toString()).executeAsList()
            },
            "mealsForDay" to {
                store.db.nutritionQueries.mealsForDay(today.toString()).executeAsList()
            },
            "measurementsForDay" to {
                store.db.bodyQueries.measurementsForDay(today.toString()).executeAsList()
            },
            "searchFoods" to {
                store.db.nutritionQueries.searchFoods("Indexed food 1", 20).executeAsList()
            },
            "vitals30d" to {
                store.db.bodyQueries.vitalsForTypeBetween(
                    "BLOOD_PRESSURE_SYSTOLIC",
                    fromMillis,
                    toMillis,
                ).executeAsList()
            },
        )

        val measurements = queries.mapValues { (name, query) ->
            repeat(WARMUP_RUNS) { query() }
            val runs = (1..MEASURED_RUNS).map {
                val started = System.nanoTime()
                query()
                elapsedMillis(started)
            }
            runs.min()
        }
        measurements.forEach { (name, millis) ->
            println("query $name: ${"%.2f".format(millis)} ms")
        }
        measurements.forEach { (name, millis) ->
            assertTrue(
                millis <= budgetMillis,
                "$name took ${"%.2f".format(millis)} ms, over the ${budgetMillis}ms budget",
            )
        }
        assertTrue(timeline.count() > 50_000)
        store.close()
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(java.nio.file.Path.of("$path-wal"))
        Files.deleteIfExists(java.nio.file.Path.of("$path-shm"))
        Files.deleteIfExists(directory)
    }

    private fun elapsedMillis(startedNanos: Long): Double =
        (System.nanoTime() - startedNanos) / NANOS_PER_MILLI

    private fun LocalDate.atStartOfDayMillis(): Long =
        kotlinx.datetime.LocalDateTime(this, kotlinx.datetime.LocalTime(0, 0))
            .toInstant(TimeZone.UTC)
            .toEpochMilliseconds()

    private companion object {
        const val FIVE_YEARS = 365 * 5
        const val WARMUP_RUNS = 2
        const val MEASURED_RUNS = 5
        const val NANOS_PER_MILLI = 1_000_000.0
    }
}
