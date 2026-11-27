package app.openhealth.data

import app.cash.sqldelight.db.SqlDriver
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.SystemClock
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.repo.ActivityRepository
import app.openhealth.data.repo.BodyRepository
import app.openhealth.data.repo.ClinicalRepository
import app.openhealth.data.repo.CoachRepository
import app.openhealth.data.repo.DeviceRepository
import app.openhealth.data.repo.McpRepository
import app.openhealth.data.repo.NoteRepository
import app.openhealth.data.repo.NutritionRepository
import app.openhealth.data.repo.ObservationRepository
import app.openhealth.data.repo.ProfileRepository
import app.openhealth.data.repo.RawRecordRepository
import app.openhealth.data.repo.RollupRepository
import app.openhealth.data.repo.SettingsRepository
import app.openhealth.data.repo.SourceRepository
import app.openhealth.data.repo.TimelineRepository
import app.openhealth.data.repo.TrainingRepository
import app.openhealth.data.repo.TrashService
import app.openhealth.data.rollup.RollupEngine
import app.openhealth.data.sync.SyncRepository
import kotlin.time.Instant

/**
 * The only production way to write: every repository that can change a day is wired to
 * the same rollup engine, so a write and its rebuild are one transaction (`pr.md` §7.3,
 * §6.28). A source-priority change refreshes the registry and rebuilds every day, because
 * the winner of a day may have changed.
 *
 * B3 constructs this at application start; the plain repositories stay available for
 * importers and tests that do their own batching.
 */
class LocalStore(
    val driver: SqlDriver,
    val db: OpenHealthDb,
    private val clock: ClockSeam = SystemClock,
) : AutoCloseable {
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
    val clinical = ClinicalRepository(db)
    val training = TrainingRepository(db)
    val activities = ActivityRepository(db, clock)
    val profile = ProfileRepository(db)
    val settings = SettingsRepository(db, clock)
    val devices = DeviceRepository(db, settings, clock)
    val trash = TrashService(db, clock)
    val notes = NoteRepository(db)
    val raw = RawRecordRepository(db)
    val timeline = TimelineRepository(db, driver)

    /** B15: the coach thread and the local MCP tokens. Neither syncs. */
    val coach = CoachRepository(db, clock)
    val mcp = McpRepository(db, clock)

    /** The change log and merge state (B13); the app builds the transport around it. */
    val sync: SyncRepository

    init {
        val deviceId = settings.get(app.openhealth.data.repo.SettingKeys.SYNC_DEVICE_ID)
            ?: kotlin.uuid.Uuid.random().toString().also {
                settings.put(app.openhealth.data.repo.SettingKeys.SYNC_DEVICE_ID, it)
            }
        sync = SyncRepository(
            db = db,
            driver = driver,
            clock = clock,
            device = deviceId,
            schemaVersion = OpenHealthDb.Schema.version.toInt(),
        )
        observations.recordChangesWith(sync)
        nutrition.recordChangesWith(sync)
        body.recordChangesWith(sync)
        training.recordChangesWith(sync)
        activities.recordChangesWith(sync)
        profile.recordChangesWith(sync)
        settings.recordChangesWith(sync)
        notes.recordChangesWith(sync)
        sources.recordChangesWith(sync)
        clinical.recordChangesWith(sync)
        trash.recordChangesWith(sync)
        sync.invalidateRollupsWith(engine::rebuildDay)

        observations.invalidateRollupsWith(engine::rebuildDay)
        nutrition.invalidateRollupsWith(engine::rebuildDay)
        body.invalidateRollupsWith(engine::rebuildDay)
        training.invalidateRollupsWith(engine::rebuildDay)
        activities.invalidateRollupsWith(engine::rebuildDay)
        trash.invalidateRollupsWith(engine::rebuildDay)
        sources.invalidateAllRollupsWith {
            engine.refreshRegistry()
            engine.rebuildAll()
        }
    }

    /**
     * What the app owes the database every time it opens: the 30-day Trash purge
     * (`pr.md` §6.29) and the 90-day raw-payload compaction (`pr.md` §7.1). Both are
     * policies with a deadline, so neither may wait for a screen to ask; the audit fields
     * of a compacted record stay, only the payload goes.
     */
    fun runStartupMaintenance(now: Instant = clock.now()) = StartupMaintenance(
        trashPurged = trash.purgeExpired(now),
        payloadsCompacted = raw.compactExpired(now),
    )

    override fun close() {
        driver.close()
    }
}

/** What one [LocalStore.runStartupMaintenance] pass removed, so a test can assert it. */
data class StartupMaintenance(val trashPurged: Int, val payloadsCompacted: Long)
