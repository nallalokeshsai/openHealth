package app.openhealth.data.repo

import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.SourceKind
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.SystemClock
import app.openhealth.core.source.Source
import app.openhealth.core.source.SourcePermission
import app.openhealth.core.source.SourceRegistry
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.db.Source as SourceRow
import app.openhealth.data.mapping.enumValueOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** The stored source registry (`pr.md` §7.6). Priorities and permissions round-trip. */
class SourceRepository(private val db: OpenHealthDb, private val clock: ClockSeam = SystemClock) {
    private val json = Json { ignoreUnknownKeys = true }
    private val prioritiesSerializer = MapSerializer(String.serializer(), Int.serializer())
    private var rebuildAllRollups: (() -> Unit)? = null
    private var sync: app.openhealth.data.sync.SyncRepository? = null

    fun recordChangesWith(recorder: app.openhealth.data.sync.SyncRepository?) {
        sync = recorder
    }

    fun invalidateAllRollupsWith(rebuild: (() -> Unit)?) {
        rebuildAllRollups = rebuild
    }

    fun upsert(source: Source) {
        upsertInternal(source)
        rebuildAllRollups?.invoke()
    }

    private fun upsertInternal(source: Source) {
        val now = clock.now()
        val createdAt = db.sourceQueries.sourceById(source.id.value).executeAsOneOrNull()
            ?.created_at ?: now.toEpochMilliseconds()
        db.sourceQueries.upsertSource(
            id = source.id.value,
            kind = source.kind.name,
            label = source.label,
            family = source.family,
            device = source.device,
            priorities = json.encodeToString(
                prioritiesSerializer,
                source.priorities.mapKeys { it.key.name },
            ),
            expectedCadenceSeconds = source.expectedCadence?.inWholeSeconds,
            reliabilityNote = source.reliabilityNote,
            lastSync = source.lastSync?.toEpochMilliseconds(),
            permissions = source.permissions.joinToString(",") { it.name },
            createdAt = createdAt,
            updatedAt = now.toEpochMilliseconds(),
        )
        sync?.capture("source", source.id.value)
    }

    fun upsertAll(sources: List<Source>) {
        db.transaction { sources.forEach(::upsertInternal) }
        rebuildAllRollups?.invoke()
    }

    fun byId(id: SourceId): Source? =
        db.sourceQueries.sourceById(id.value).executeAsOneOrNull()?.toDomain()

    fun all(): List<Source> = db.sourceQueries.allSources().executeAsList().map { it.toDomain() }

    fun registry(): SourceRegistry = SourceRegistry(all())

    fun inFamily(family: String): List<Source> =
        db.sourceQueries.sourcesInFamily(family).executeAsList().map { it.toDomain() }

    fun markSynced(id: SourceId, at: Instant) {
        db.sourceQueries.updateSourceLastSync(
            at.toEpochMilliseconds(),
            at.toEpochMilliseconds(),
            id.value,
        )
        sync?.capture("source", id.value)
    }

    private fun SourceRow.toDomain(): Source {
        val parsedPriorities = priorities?.takeIf { it.isNotBlank() }?.let {
            json.decodeFromString(prioritiesSerializer, it)
        }.orEmpty()
        val permissions = permissions
            ?.split(',')
            ?.mapNotNull { name -> enumValueOrNull<SourcePermission>(name) }
            .orEmpty()
        return Source(
            id = SourceId(id),
            kind = enumValueOrNull<SourceKind>(kind) ?: SourceKind.API,
            label = label,
            family = family,
            device = device,
            priorities = parsedPriorities.mapNotNull { (name, priority) ->
                enumValueOrNull<ObservationType>(name)?.let { it to priority }
            }.toMap(),
            expectedCadence = expected_cadence_seconds?.seconds,
            reliabilityNote = reliability_note,
            lastSync = last_sync?.let(Instant::fromEpochMilliseconds),
            permissions = permissions.toSet(),
        )
    }
}
