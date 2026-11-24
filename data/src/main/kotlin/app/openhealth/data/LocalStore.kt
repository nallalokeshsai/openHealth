package app.openhealth.data

import app.cash.sqldelight.db.SqlDriver
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.SystemClock
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.repo.BodyRepository
import app.openhealth.data.repo.NoteRepository
import app.openhealth.data.repo.NutritionRepository
import app.openhealth.data.repo.ObservationRepository
import app.openhealth.data.repo.ProfileRepository
import app.openhealth.data.repo.RollupRepository
import app.openhealth.data.repo.SettingsRepository
import app.openhealth.data.repo.SourceRepository
import app.openhealth.data.repo.TimelineRepository
import app.openhealth.data.repo.TrainingRepository
import app.openhealth.data.repo.TrashService
import app.openhealth.data.rollup.RollupEngine

/**
 * The only production way to write: every repository that can change a day is wired to
 * the same rollup engine, so a write and its rebuild are one transaction (`pr.md` §7.3,
 * §6.28). A source-priority change refreshes the registry and rebuilds every day, because
 * the winner of a day may have changed.
 *
 * B3 constructs this at application start; the plain repositories stay available for
 * importers and tests that do their own batching.
 */
class LocalStore(val driver: SqlDriver, val db: OpenHealthDb, clock: ClockSeam = SystemClock) :
    AutoCloseable {
    val sources = SourceRepository(db, clock)
    val observations = ObservationRepository(db, clock)
    val rollups = RollupRepository(db)
    val engine = RollupEngine(
        observations = observations,
        rollups = rollups,
        db = db,
        registry = sources.registry(),
        clock = clock,
        registryProvider = { sources.registry() },
    )
    val nutrition = NutritionRepository(db, clock)
    val body = BodyRepository(db)
    val training = TrainingRepository(db)
    val profile = ProfileRepository(db)
    val settings = SettingsRepository(db, clock)
    val trash = TrashService(db, clock)
    val notes = NoteRepository(db)
    val timeline = TimelineRepository(db, driver)

    init {
        observations.invalidateRollupsWith(engine::rebuildDay)
        nutrition.invalidateRollupsWith(engine::rebuildDay)
        body.invalidateRollupsWith(engine::rebuildDay)
        training.invalidateRollupsWith(engine::rebuildDay)
        trash.invalidateRollupsWith(engine::rebuildDay)
        sources.invalidateAllRollupsWith {
            engine.refreshRegistry()
            engine.rebuildAll()
        }
    }

    override fun close() {
        driver.close()
    }
}
