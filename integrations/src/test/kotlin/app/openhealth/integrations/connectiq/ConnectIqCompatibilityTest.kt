package app.openhealth.integrations.connectiq

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Connect IQ table is checked against the manifest, so a model that cannot run the
 * app can never be advertised as a target (`docs/adr/0019`).
 */
class ConnectIqCompatibilityTest {
    private val devices: File = File(
        System.getProperty("openhealth.connectiq.devices")
            ?: error("openhealth.connectiq.devices is not set"),
    )
    private val manifest: File = File(
        System.getProperty("openhealth.connectiq.manifest")
            ?: error("openhealth.connectiq.manifest is not set"),
    )

    private fun table(): ConnectIqTable = ConnectIqCompatibility.parse(devices.readText())

    private fun manifestIds(): Set<String> = Regex("<iq:product\\s+id=\"([^\"]+)\"")
        .findAll(manifest.readText())
        .map { it.groupValues[1] }
        .toSet()

    @Test
    fun theOwnersVivoactive5IsTheReferenceTarget() {
        val device = assertNotNull(
            table().devices.firstOrNull { it.productId == "vivoactive5" },
        )
        assertEquals("vívoactive 5", device.name)
        assertEquals(390, device.width)
        assertEquals(390, device.height)
        assertEquals("round", device.shape)
        assertEquals("AMOLED", device.screen)
        assertEquals("5.2", device.apiLevel)
        assertTrue(device.ble)
        assertTrue(device.opticalHeartRate)
        assertTrue(device.manifestTarget)
        assertEquals("reference", device.status)
    }

    @Test
    fun everyManifestTargetIsBLEHeartRateAndNewEnough() {
        val table = table()
        val problems = ConnectIqCompatibility.problems(table, manifestIds())
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun aDeviceWithoutOpticalHeartRateIsNotATarget() {
        val edge = table().devices.first { it.productId == "edge1040" }
        assertTrue(!edge.opticalHeartRate)
        assertTrue(!edge.manifestTarget)
    }

    @Test
    fun anUnverifiedIdStaysOutOfTheManifest() {
        val candidate = table().devices.first { it.productId == "vivoactive6" }
        assertTrue(!candidate.manifestTarget)
        assertTrue(candidate.status.startsWith("candidate"))
        assertTrue("vivoactive6" !in manifestIds())
    }

    @Test
    fun aMissingBLEFlagWouldFailTheTable() {
        val table = table()
        val broken = table.copy(
            devices = table.devices.map {
                if (it.productId == "vivoactive5") it.copy(ble = false) else it
            },
        )
        val problems = ConnectIqCompatibility.problems(broken, manifestIds())
        assertTrue(problems.any { it.contains("vivoactive5: no BLE") })
    }

    @Test
    fun theVersionComparisonIsNotLexical() {
        assertTrue(ConnectIqCompatibility.apiAtLeast("5.2", "3.1.0"))
        assertTrue(ConnectIqCompatibility.apiAtLeast("10.0", "9.9"))
        assertTrue(!ConnectIqCompatibility.apiAtLeast("3.0", "3.1"))
    }
}
