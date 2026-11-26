package app.openhealth.integrations.connectiq

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The per-device compatibility table for the Connect IQ app (`pr.md` §6.18;
 * `docs/adr/0019`). Garmin's list changes, so the table is data, not an assumption: the
 * app may only be built for a model that has Bluetooth Low Energy, an optical heart-rate
 * sensor and API 3.1 or later. The same file is the source for `manifest.xml`.
 */
@Serializable
data class ConnectIqTable(
    val source: String,
    val minApiLevel: String,
    val devices: List<ConnectIqDevice>,
)

@Serializable
data class ConnectIqDevice(
    val productId: String,
    val name: String,
    val width: Int,
    val height: Int,
    val shape: String,
    val screen: String,
    val apiLevel: String,
    val ble: Boolean,
    val opticalHeartRate: Boolean,
    val manifestTarget: Boolean,
    val status: String,
)

object ConnectIqCompatibility {
    val json: Json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): ConnectIqTable =
        json.decodeFromString(ConnectIqTable.serializer(), text)

    fun apiAtLeast(apiLevel: String, minimum: String): Boolean =
        compareVersions(apiLevel, minimum) >= 0

    /** The reasons a table or its manifest would fail; empty means buildable. */
    fun problems(table: ConnectIqTable, manifestProductIds: Set<String>): List<String> {
        val problems = mutableListOf<String>()
        val targets = table.devices.filter { it.manifestTarget }
        if (table.devices.map { it.productId }.toSet().size != table.devices.size) {
            problems += "duplicate product id"
        }
        table.devices.forEach { device ->
            if (device.productId.isBlank() || device.name.isBlank()) {
                problems += "blank device id or name"
            }
            if (device.width <= 0 || device.height <= 0) {
                problems += "${device.productId}: empty screen"
            }
            if (device.shape !in setOf("round", "rectangle", "semi-round", "semi-octagon")) {
                problems += "${device.productId}: unknown shape ${device.shape}"
            }
        }
        targets.forEach { device ->
            if (!device.ble) problems += "${device.productId}: no BLE"
            if (!device.opticalHeartRate) problems += "${device.productId}: no optical heart rate"
            if (!apiAtLeast(device.apiLevel, table.minApiLevel)) {
                problems += "${device.productId}: API ${device.apiLevel} below ${table.minApiLevel}"
            }
        }
        val targetIds = targets.map { it.productId }.toSet()
        if (targetIds != manifestProductIds) {
            problems += "manifest targets $manifestProductIds do not match table targets $targetIds"
        }
        return problems
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split('.').map { it.toIntOrNull() ?: 0 }
        val b = right.split('.').map { it.toIntOrNull() ?: 0 }
        val size = maxOf(a.size, b.size)
        for (index in 0 until size) {
            val diff = (a.getOrNull(index) ?: 0) - (b.getOrNull(index) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }
}
