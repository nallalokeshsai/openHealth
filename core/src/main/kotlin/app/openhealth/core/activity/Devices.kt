package app.openhealth.core.activity

import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.source.Source
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** How much of a device address names its source; enough to be unique, not the whole id. */
private const val SOURCE_ID_ADDRESS_CHARS = 12

/**
 * A device the person paired over Bluetooth (`pr.md` §6.18, `BUILD-ROADMAP.md` §B8).
 * Its address is stored on this device only and never leaves it; the paired device is
 * also a [Source] in the registry, so every reading it writes keeps its provenance.
 */
@Serializable
data class PairedDevice(
    val id: String,
    val address: String,
    val name: String?,
    val kind: DeviceKindName,
    val sourceId: String,
    val autoConnect: Boolean = true,
    /** Epoch millis, so the record serialises without a datetime adapter. */
    val lastSeenAt: Long? = null,
    val lastReadingAt: Long? = null,
) {
    val deviceKind: DeviceKind? get() = DeviceKindName.toKind(kind)

    fun lastSeen(): Instant? = lastSeenAt?.let(Instant::fromEpochMilliseconds)

    fun lastReading(): Instant? = lastReadingAt?.let(Instant::fromEpochMilliseconds)
}

/** Every device the person paired. It is a setting, not health data, so it exports with them. */
@Serializable
data class DeviceRegistry(val devices: List<PairedDevice> = emptyList()) {
    fun byId(id: String): PairedDevice? = devices.firstOrNull { it.id == id }

    fun byAddress(address: String): PairedDevice? =
        devices.firstOrNull { it.address.equals(address, ignoreCase = true) }

    fun upsert(device: PairedDevice): DeviceRegistry =
        DeviceRegistry(devices.filterNot { it.id == device.id } + device)

    fun remove(id: String): DeviceRegistry = DeviceRegistry(devices.filterNot { it.id == id })

    fun touch(id: String, at: Instant, reading: Boolean): DeviceRegistry = DeviceRegistry(
        devices.map { device ->
            if (device.id != id) {
                device
            } else {
                device.copy(
                    lastSeenAt = at.toEpochMilliseconds(),
                    lastReadingAt = if (reading) {
                        at.toEpochMilliseconds()
                    } else {
                        device.lastReadingAt
                    },
                )
            }
        },
    )
}

/**
 * The last sync of one source, shown on the Devices screen (`BUILD-ROADMAP.md` §B8:
 * "last sync, what each provides"). It is a small summary, not a second copy of the data.
 */
@Serializable
data class SyncedSource(
    val sourceId: String,
    val atEpochMillis: Long,
    val records: Int = 0,
    val steps: Long = 0,
    val activities: Int = 0,
    val detail: String? = null,
    /** For a FIT import: whether the steps came from monitoring or the workout. */
    val stepsFrom: String? = null,
) {
    val at: Instant get() = Instant.fromEpochMilliseconds(atEpochMillis)
}

@Serializable
data class SyncBook(val sources: List<SyncedSource> = emptyList()) {
    fun latest(sourceId: String): SyncedSource? = sources.filter { it.sourceId == sourceId }
        .maxByOrNull { it.atEpochMillis }

    fun record(summary: SyncedSource): SyncBook {
        val kept = sources.filterNot { it.sourceId == summary.sourceId }
            .filterNot { it.atEpochMillis < summary.atEpochMillis - RETAIN_MILLIS }
        return SyncBook((kept + summary).sortedBy { it.atEpochMillis })
    }

    private companion object {
        /** One summary per source per 30 days is enough for the Devices screen. */
        const val RETAIN_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}

/** The serialisable name of [DeviceKind]; the enum itself stays in the domain layer. */
@Serializable
enum class DeviceKindName {
    HEART_RATE,
    SCALE,
    BLOOD_PRESSURE,
    ;

    companion object {
        fun of(kind: DeviceKind): DeviceKindName = when (kind) {
            DeviceKind.HEART_RATE -> HEART_RATE
            DeviceKind.SCALE -> SCALE
            DeviceKind.BLOOD_PRESSURE -> BLOOD_PRESSURE
        }

        fun toKind(name: DeviceKindName): DeviceKind = when (name) {
            HEART_RATE -> DeviceKind.HEART_RATE
            SCALE -> DeviceKind.SCALE
            BLOOD_PRESSURE -> DeviceKind.BLOOD_PRESSURE
        }
    }
}

/** What one kind of device contributes, in observation types. */
object DeviceCapabilities {
    fun types(kind: DeviceKind): List<ObservationType> = when (kind) {
        DeviceKind.HEART_RATE -> listOf(
            ObservationType.HEART_RATE,
            ObservationType.RESTING_HEART_RATE,
        )

        DeviceKind.SCALE -> listOf(
            ObservationType.WEIGHT,
            ObservationType.BODY_FAT_PERCENT,
            ObservationType.LEAN_MASS,
        )

        DeviceKind.BLOOD_PRESSURE -> listOf(
            ObservationType.BLOOD_PRESSURE_SYSTOLIC,
            ObservationType.BLOOD_PRESSURE_DIASTOLIC,
        )
    }

    /** The source id a kind writes under, one per device address, so two straps differ. */
    fun sourceIdFor(kind: DeviceKind, address: String): SourceId {
        val prefix = when (kind) {
            DeviceKind.HEART_RATE -> "ble-hr"
            DeviceKind.SCALE -> "ble-scale"
            DeviceKind.BLOOD_PRESSURE -> "ble-bp"
        }
        val suffix = address.lowercase().replace(":", "").take(SOURCE_ID_ADDRESS_CHARS)
        return SourceId("$prefix-$suffix")
    }

    /** The registry row for a paired device: its family is the device, not the profile. */
    fun source(device: PairedDevice): Source = Source(
        id = SourceId(device.sourceId),
        kind = app.openhealth.core.observation.SourceKind.API,
        label = device.name?.takeIf { it.isNotBlank() } ?: defaultLabel(device.kind),
        family = "ble",
        device = device.name,
        reliabilityNote = "Bluetooth ${device.kind.name.lowercase().replace('_', ' ')}",
        lastSync = device.lastReading(),
    )

    private fun defaultLabel(kind: DeviceKindName): String = when (kind) {
        DeviceKindName.HEART_RATE -> "Heart-rate strap"
        DeviceKindName.SCALE -> "Scale"
        DeviceKindName.BLOOD_PRESSURE -> "Blood-pressure cuff"
    }
}

/**
 * The source priority list of `pr.md` §7.6 as an editable order. The UI moves one row
 * and the plan writes an explicit priority for every source of that type, so the result
 * does not depend on the order rows happened to be created in.
 */
object SourcePriorityPlan {
    /** Priorities are 10, 20, 30…; a lower number wins. */
    const val PRIORITY_STEP = 10

    /** Moves [sourceId] one place up or down. An edge move is a no-op. */
    @Suppress("ReturnCount")
    fun move(order: List<SourceId>, sourceId: SourceId, up: Boolean): List<SourceId> {
        val index = order.indexOf(sourceId)
        if (index < 0) return order
        val target = if (up) index - 1 else index + 1
        if (target !in order.indices) return order
        val mutable = order.toMutableList()
        val moved = mutable.removeAt(index)
        mutable.add(target, moved)
        return mutable
    }

    /**
     * The per-source priority rows for [type] after a reorder. A source keeps every other
     * type's priority it already had, so moving the steps order does not silently
     * reorder heart rate.
     */
    fun priorities(
        order: List<SourceId>,
        type: ObservationType,
        current: Map<SourceId, Source>,
    ): Map<SourceId, Source> {
        val ranked = order.withIndex().associate { (index, id) ->
            id to (index + 1) * PRIORITY_STEP
        }
        return order.mapNotNull { id ->
            val source = current[id] ?: return@mapNotNull null
            source.copy(priorities = source.priorities + (type to ranked.getValue(id)))
        }.associateBy(Source::id)
    }

    /** The order a type currently has, lowest priority number first. */
    fun order(sources: List<Source>, type: ObservationType): List<SourceId> = sources
        .sortedWith(
            compareBy({ it.priorityFor(type) }, { it.label.lowercase() }, { it.id.value }),
        )
        .map(Source::id)
}
