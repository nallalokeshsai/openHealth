package app.openhealth.data

import app.openhealth.core.observation.ObservationType
import app.openhealth.core.source.SourceRegistry
import app.openhealth.core.validate.ObservationValidator
import app.openhealth.data.corpus.CorpusGenerator
import app.openhealth.data.corpus.CorpusRequest
import app.openhealth.data.repo.NutritionRepository
import app.openhealth.data.repo.ObservationRepository
import app.openhealth.data.repo.RollupRepository
import app.openhealth.data.repo.SourceRepository
import app.openhealth.data.rollup.RollupEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

class CorpusTest {
    private val endDate = LocalDate(2026, 3, 1)

    @Test
    fun everyGeneratedObservationPassesThePlausibilityScreen() {
        val store = inMemoryStore()
        CorpusGenerator(store.db).generate(CorpusRequest(days = 30, seed = 3L, endDate = endDate))
        val repository = ObservationRepository(store.db)
        val now = Instant.parse("2026-03-05T00:00:00Z")
        repository.daysWithObservations().forEach { date ->
            repository.forDay(date).forEach { observation ->
                val result = ObservationValidator.validate(observation, now)
                assertTrue(result.isValid, "${observation.id} failed ${result.issues}")
            }
        }
        store.close()
    }

    @Test
    fun aDaysNutritionRollupIsTheExactSumOfItsEntries() {
        val store = inMemoryStore()
        CorpusGenerator(store.db).generate(CorpusRequest(days = 30, seed = 4L, endDate = endDate))
        val engine = RollupEngine(
            ObservationRepository(store.db),
            RollupRepository(store.db),
            store.db,
            SourceRegistry(SourceRepository(store.db).all()),
        )
        val date = endDate.minus(DatePeriod(days = 1))
        engine.rebuildDay(date)
        val entries = NutritionRepository(store.db).entriesForDay(date)
        assertTrue(entries.isNotEmpty())
        val energy = RollupRepository(store.db)
            .forDayAndType(date, ObservationType.NUTRITION_ENERGY)!!
        assertEquals(entries.sumOf { it.energy_kcal ?: 0.0 }, energy.value.value, 1e-9)
        assertEquals(entries.size.toDouble(), energy.secondaryValue)
        store.close()
    }

    @Test
    fun aSecondCorpusOnTheSameDatabaseIsRefused() {
        val store = inMemoryStore()
        val generator = CorpusGenerator(store.db)
        generator.generate(CorpusRequest(days = 5, seed = 1L, endDate = endDate))
        assertFailsWith<IllegalArgumentException> {
            generator.generate(CorpusRequest(days = 5, seed = 2L, endDate = endDate))
        }
        store.close()
    }
}
