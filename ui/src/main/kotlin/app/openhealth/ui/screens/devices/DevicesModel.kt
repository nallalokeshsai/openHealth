package app.openhealth.ui.screens.devices

/**
 * The Devices surface (`BUILD-ROADMAP.md` §B8, `DESIGN.md` §14.2 Settings): connected
 * sources, what each provides, the last sync, the priority list and the manual fallback.
 * Every string is resolved by the presenter from a resource, so this file stays a shape
 * and the wording lives with the rest of the product copy.
 */
data class DevicesModel(
    val steps: StepsSourceModel,
    val healthConnect: HealthConnectModel,
    val garmin: GarminModel,
    val bluetooth: BluetoothModel,
    val priority: PriorityModel,
    val note: String,
    val message: String? = null,
)

data class StepsSourceModel(
    val available: Boolean,
    val tracking: Boolean,
    val needsPermission: Boolean,
    val todayText: String,
    val sourceLine: String?,
    val otherSources: List<String>,
    val lastReadingText: String?,
    val canCorrect: Boolean,
    val permissionNote: String,
)

data class HealthConnectModel(
    val available: Boolean,
    val enabled: Boolean,
    val grantedCount: Int,
    val totalCount: Int,
    val statusText: String,
    val lastSyncText: String?,
    val provides: List<HealthProvideModel>,
    val historyImported: Boolean,
    val note: String,
    val vendorNote: String,
)

data class HealthProvideModel(val label: String, val granted: Boolean)

data class GarminModel(
    val lastImportText: String?,
    val lastImportDetail: String?,
    val stepsFrom: String?,
    val note: String,
    val readOnlyNote: String,
)

data class BluetoothModel(
    val available: Boolean,
    val needsPermission: Boolean,
    val scanning: Boolean,
    val devices: List<PairedDeviceModel>,
    val found: List<FoundDeviceModel>,
    val liveHeartRate: Int?,
    val note: String,
)

data class PairedDeviceModel(
    val id: String,
    val name: String,
    val kindLabel: String,
    val statusText: String,
    val lastSeenText: String?,
    val providesText: String,
    val autoConnect: Boolean,
)

data class FoundDeviceModel(
    val address: String,
    val name: String,
    val kindLabel: String,
    val kind: app.openhealth.core.activity.DeviceKindName,
)

data class PriorityModel(val rows: List<PriorityRowModel>, val typeLabel: String, val note: String)

data class PriorityRowModel(
    val sourceId: String,
    val label: String,
    val detail: String,
    val isFirst: Boolean,
    val isLast: Boolean,
    val isManual: Boolean,
)

/** The permission the surface is about to ask for, launched by the app shell. */
enum class DevicePermissionRequest { ACTIVITY_RECOGNITION, BLUETOOTH, HEALTH_CONNECT }
