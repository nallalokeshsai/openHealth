package app.openhealth.core.source

import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.SourceKind
import kotlin.time.Duration
import kotlin.time.Instant

/** What a source is allowed to contribute. Declared, never inferred from a payload. */
enum class SourcePermission {
    READ_STEPS,
    READ_DISTANCE,
    READ_ENERGY,
    READ_HEART_RATE,
    READ_SLEEP,
    READ_WEIGHT,
    READ_BODY_COMPOSITION,
    READ_GLUCOSE,
    READ_BLOOD_PRESSURE,
    READ_NUTRITION,
    WRITE_EXERCISE,
    WRITE_WEIGHT,
    WRITE_NUTRITION,
}

/**
 * One entry of the source registry (`pr.md` §7.6): priority per data type, expected
 * cadence, reliability note, last sync and permissions. Priority is an ordering: the
 * **lower** number wins a day.
 */
data class Source(
    val id: SourceId,
    val kind: SourceKind,
    val label: String,
    val family: String = kind.defaultFamily,
    val device: String? = null,
    val priorities: Map<ObservationType, Int> = emptyMap(),
    val expectedCadence: Duration? = null,
    val reliabilityNote: String? = null,
    val lastSync: Instant? = null,
    val permissions: Set<SourcePermission> = emptySet(),
) {
    init {
        require(label.isNotBlank()) { "Source label must not be blank" }
    }

    fun priorityFor(type: ObservationType): Int =
        priorities[type] ?: SourcePriorityDefaults.priority(kind, type)

    fun hasPermission(permission: SourcePermission): Boolean = permission in permissions
}

/** Default priorities by source kind. A per-source override lives in [Source.priorities]. */
object SourcePriorityDefaults {
    private const val MANUAL_PRIORITY = 10
    private const val LAB_PRIORITY = 15
    private const val DEVICE_PRIORITY = 20
    private const val SECOND_DEVICE_PRIORITY = 30
    private const val PLATFORM_BUS_PRIORITY = 40
    private const val API_PRIORITY = 65
    private const val FILE_PRIORITY = 70
    private const val MCP_PRIORITY = 80
    private const val CGM_PRIORITY = 20

    fun priority(kind: SourceKind, type: ObservationType): Int = when (kind) {
        SourceKind.MANUAL -> MANUAL_PRIORITY
        SourceKind.LAB -> LAB_PRIORITY
        SourceKind.CGM -> if (type ==
            ObservationType.BLOOD_GLUCOSE
        ) {
            CGM_PRIORITY
        } else {
            DEVICE_PRIORITY
        }
        SourceKind.GARMIN -> DEVICE_PRIORITY
        SourceKind.FITBIT -> SECOND_DEVICE_PRIORITY
        SourceKind.HEALTH_CONNECT, SourceKind.HEALTHKIT -> PLATFORM_BUS_PRIORITY
        SourceKind.STRAVA -> API_PRIORITY
        SourceKind.API -> API_PRIORITY
        SourceKind.CSV -> FILE_PRIORITY
        SourceKind.MCP -> MCP_PRIORITY
    }
}

/** The registry itself. It is a plain map so it can be stored and edited per profile. */
class SourceRegistry(sources: List<Source> = emptyList()) {
    private val byId: Map<SourceId, Source> = sources.associateBy(Source::id)

    fun get(id: SourceId): Source? = byId[id]

    fun all(): List<Source> = byId.values.sortedBy { it.priorityFor(ObservationType.STEPS) }

    fun register(source: Source): SourceRegistry =
        SourceRegistry(byId.values.filterNot { it.id == source.id } + source)

    /** Lower is better; an unknown source is treated as the least trusted. */
    fun priorityOf(id: SourceId, type: ObservationType): Int =
        byId[id]?.priorityFor(type) ?: UNKNOWN_SOURCE_PRIORITY

    fun familyOf(id: SourceId): String = byId[id]?.family ?: id.value

    companion object {
        const val UNKNOWN_SOURCE_PRIORITY = 100
    }
}
