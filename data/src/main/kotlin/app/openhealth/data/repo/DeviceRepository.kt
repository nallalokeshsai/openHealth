package app.openhealth.data.repo

import app.openhealth.core.activity.DeviceRegistry
import app.openhealth.core.activity.PairedDevice
import app.openhealth.core.activity.StepCounterState
import app.openhealth.core.activity.SyncBook
import app.openhealth.core.activity.SyncedSource
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.SystemClock
import app.openhealth.data.db.OpenHealthDb
import kotlin.time.Instant
import kotlinx.serialization.json.Json

/**
 * The devices layer of `BUILD-ROADMAP.md` §B8: paired Bluetooth devices, the step
 * counter's runtime state and the last sync per source. None of it is health data — a
 * device is a setting and a counter state is runtime state — so it rides on layer 5 and
 * leaves the observation, rollup and raw layers untouched.
 *
 * The registry is a small JSON document rather than a table: it is read whole, written
 * whole, and a new field must never need a schema migration. The paired device's source
 * row still lives in `source`, so every reading keeps real provenance.
 */
class DeviceRepository(
    private val db: OpenHealthDb,
    private val settings: SettingsRepository,
    private val clock: ClockSeam = SystemClock,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun registry(): DeviceRegistry {
        val raw = settings.get(SettingKeys.DEVICE_REGISTRY) ?: return DeviceRegistry()
        return runCatching { json.decodeFromString<DeviceRegistry>(raw) }
            .getOrDefault(DeviceRegistry())
    }

    fun save(registry: DeviceRegistry) {
        settings.put(SettingKeys.DEVICE_REGISTRY, json.encodeToString(registry))
    }

    fun pair(device: PairedDevice): DeviceRegistry {
        val updated = registry().upsert(device)
        save(updated)
        return updated
    }

    fun unpair(id: String): DeviceRegistry {
        val updated = registry().remove(id)
        save(updated)
        return updated
    }

    fun touch(id: String, at: Instant, reading: Boolean = false): DeviceRegistry {
        val updated = registry().touch(id, at, reading)
        save(updated)
        return updated
    }

    /** The counter's high-water mark, so a reboot or a process death loses no step. */
    fun stepState(): StepCounterState = StepCounterState(
        bootEpochMillis = settings.get(SettingKeys.STEPS_BOOT_MS)?.toLongOrNull(),
        lastCount = settings.get(SettingKeys.STEPS_LAST_COUNT)?.toLongOrNull(),
        lastAt = settings.get(SettingKeys.STEPS_LAST_AT)?.toLongOrNull()
            ?.let(Instant::fromEpochMilliseconds),
    )

    fun saveStepState(state: StepCounterState) {
        state.bootEpochMillis?.let { settings.put(SettingKeys.STEPS_BOOT_MS, it.toString()) }
        state.lastCount?.let { settings.put(SettingKeys.STEPS_LAST_COUNT, it.toString()) }
        state.lastAt?.let {
            settings.put(SettingKeys.STEPS_LAST_AT, it.toEpochMilliseconds().toString())
        }
    }

    fun stepTrackingEnabled(): Boolean = settings.get(SettingKeys.STEPS_TRACKING) == "true"

    fun setStepTracking(enabled: Boolean) {
        settings.put(SettingKeys.STEPS_TRACKING, enabled.toString())
    }

    fun syncBook(): SyncBook {
        val raw = settings.get(SettingKeys.SYNC_BOOK) ?: return SyncBook()
        return runCatching { json.decodeFromString<SyncBook>(raw) }.getOrDefault(SyncBook())
    }

    fun recordSync(summary: SyncedSource) {
        settings.put(SettingKeys.SYNC_BOOK, json.encodeToString(syncBook().record(summary)))
    }

    fun healthConnectEnabled(): Boolean = settings.get(SettingKeys.HEALTH_CONNECT_ENABLED) == "true"

    fun setHealthConnectEnabled(enabled: Boolean) {
        settings.put(SettingKeys.HEALTH_CONNECT_ENABLED, enabled.toString())
    }

    fun healthConnectGranted(): Set<String> = settings.get(SettingKeys.HEALTH_CONNECT_GRANTED)
        ?.split(',')
        ?.filter { it.isNotBlank() }
        ?.toSet()
        .orEmpty()

    fun setHealthConnectGranted(permissions: Set<String>) {
        settings.put(SettingKeys.HEALTH_CONNECT_GRANTED, permissions.joinToString(","))
    }

    fun healthConnectBackfilled(): Boolean =
        settings.get(SettingKeys.HEALTH_CONNECT_BACKFILL) == "true"

    fun setHealthConnectBackfilled(done: Boolean) {
        settings.put(SettingKeys.HEALTH_CONNECT_BACKFILL, done.toString())
    }

    fun noteNow(): Instant = clock.now()

    /** Counts used by the Devices screen without opening a second query path. */
    fun rawRecordCount(): Long = db.rawRecordQueries.countRawRecords().executeAsOne()
}
