package app.openhealth.data

import app.openhealth.core.activity.DeviceKindName
import app.openhealth.core.activity.PairedDevice
import app.openhealth.core.activity.StepCounterState
import app.openhealth.core.activity.SyncedSource
import app.openhealth.data.repo.SettingKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The devices layer (`BUILD-ROADMAP.md` §B8). A paired device, the counter state and the
 * sync book are configuration and runtime state: they round-trip, they survive a reopen,
 * and they never touch the health layers.
 */
class DeviceRepositoryTest {
    private val store = inMemoryStore().let { LocalStore(it.driver, it.db) }
    private val at = Instant.parse("2026-11-23T09:00:00Z")

    @Test
    fun aPairedDeviceRoundTripsThroughTheSettingsLayer() {
        val device = PairedDevice(
            id = "strap-1",
            address = "AA:BB:CC:DD:EE:FF",
            name = "Polar H10",
            kind = DeviceKindName.HEART_RATE,
            sourceId = "ble-hr-aabbccddeeff",
            lastSeenAt = at.toEpochMilliseconds(),
        )
        store.devices.pair(device)

        val read = store.devices.registry().byId("strap-1")
        assertEquals(device, read)
        assertEquals("Polar H10", read?.name)
        assertEquals("ble-hr-aabbccddeeff", read?.sourceId)
    }

    @Test
    fun pairingTheSameAddressTwiceKeepsOneRow() {
        store.devices.pair(device("strap-1"))
        val renamed = device("strap-2").copy(address = device("strap-1").address)
        store.devices.pair(renamed)

        assertEquals(1, store.devices.registry().devices.size)
        assertEquals("Scale strap-2", store.devices.registry().devices.single().name)
    }

    @Test
    fun unpairingRemovesOnlyThatDevice() {
        store.devices.save(
            app.openhealth.core.activity.DeviceRegistry(listOf(device("a"), device("b"))),
        )
        store.devices.unpair("a")
        assertEquals(listOf("b"), store.devices.registry().devices.map { it.id })
    }

    @Test
    fun touchingADeviceMovesItsLastSeenAndOptionallyItsLastReading() {
        store.devices.save(app.openhealth.core.activity.DeviceRegistry(listOf(device("a"))))
        store.devices.touch("a", at, reading = false)
        assertNull(store.devices.registry().byId("a")?.lastReadingAt)
        store.devices.touch("a", at.plus(kotlin.time.Duration.parse("1h")), reading = true)
        val read = store.devices.registry().byId("a")
        assertEquals(
            at.plus(kotlin.time.Duration.parse("1h")).toEpochMilliseconds(),
            read?.lastReadingAt,
        )
        assertEquals(
            at.plus(kotlin.time.Duration.parse("1h")).toEpochMilliseconds(),
            read?.lastSeenAt,
        )
    }

    @Test
    fun theStepCounterStateSurvivesAReopen() {
        val state = StepCounterState(
            bootEpochMillis = at.toEpochMilliseconds(),
            lastCount = 4_321,
            lastAt = at.plus(kotlin.time.Duration.parse("2h")),
        )
        store.devices.saveStepState(state)
        assertEquals(state, store.devices.stepState())

        // A second repository over the same database reads the same state.
        val reopened = store.devices.stepState()
        assertEquals(4_321, reopened.lastCount)
        assertTrue(store.devices.stepTrackingEnabled().not())
        store.devices.setStepTracking(true)
        assertTrue(store.devices.stepTrackingEnabled())
    }

    @Test
    fun theSyncBookKeepsTheLatestPerSource() {
        store.devices.recordSync(
            SyncedSource("garmin-fit", at.toEpochMilliseconds(), steps = 3_000),
        )
        store.devices.recordSync(
            SyncedSource(
                "garmin-fit",
                at.plus(kotlin.time.Duration.parse("1h")).toEpochMilliseconds(),
                steps = 4_000,
            ),
        )
        store.devices.recordSync(
            SyncedSource("health-connect", at.toEpochMilliseconds(), records = 120),
        )

        val book = store.devices.syncBook()
        assertEquals(2, book.sources.size)
        assertEquals(4_000, book.latest("garmin-fit")?.steps)
        assertEquals(120, book.latest("health-connect")?.records)
    }

    @Test
    fun healthConnectConsentRoundTrips() {
        assertFalse(store.devices.healthConnectEnabled())
        store.devices.setHealthConnectEnabled(true)
        store.devices.setHealthConnectGranted(setOf("health.read.STEPS"))
        store.devices.setHealthConnectBackfilled(true)

        assertTrue(store.devices.healthConnectEnabled())
        assertEquals(
            setOf("health.read.STEPS"),
            store.devices.healthConnectGranted(),
        )
        assertTrue(store.devices.healthConnectBackfilled())
        assertEquals("true", store.settings.get(SettingKeys.HEALTH_CONNECT_ENABLED))
    }

    @Test
    fun aCorruptRegistryReadsAsEmptyRatherThanCrashing() {
        store.settings.put(SettingKeys.DEVICE_REGISTRY, "{not json")
        assertTrue(store.devices.registry().devices.isEmpty())
    }

    private fun device(id: String) = PairedDevice(
        id = id,
        address = "AA:BB:CC:DD:EE:$id",
        name = "Scale $id",
        kind = DeviceKindName.SCALE,
        sourceId = "ble-scale-$id",
    )
}
