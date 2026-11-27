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
    val watchLink: WatchLinkModel,
    val priority: PriorityModel,
    val note: String,
    val message: String? = null,
)

/** The Connect IQ direct link (`pr.md` §6.18; ADR 0019); off until the person turns it on. */
data class WatchLinkModel(val enabled: Boolean, val available: Boolean, val note: String)

data class StepsSourceModel(
    val available: Boolean,
    val tracking: Boolean,
    /** True when the Steps module is off, so the figures are hidden and nothing runs. */
    val moduleOff: Boolean,
    val todayText: String,
    val sourceLine: String?,
    /** Why the day resolved the way it did when it was not a plain win (`pr.md` §7.6). */
    val reasonNote: String?,
    val otherSources: List<String>,
    val lastReadingText: String?,
    val canCorrect: Boolean,
    val permissionNote: String,
    /** The lines the "where this number comes from" sheet shows. */
    val provenance: List<ProvenanceLine> = emptyList(),
)

data class ProvenanceLine(val label: String, val detail: String, val value: String?)

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
    /** Shown when the radio is off, so a disabled Scan button has a reason on screen. */
    val offNote: String? = null,
)

data class PairedDeviceModel(
    val id: String,
    val name: String,
    val kind: app.openhealth.core.activity.DeviceKindName,
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
enum class DevicePermissionRequest {
    ACTIVITY_RECOGNITION,
    BLUETOOTH,
    HEALTH_CONNECT,
    HEALTH_CONNECT_HISTORY,
}
