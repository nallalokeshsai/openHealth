package app.openhealth.core.merge

import app.openhealth.core.sync.SyncSnapshot

/**
 * The merge policies B13's sync engine uses, declared here in `:core` and shared by the
 * app and the server (`BUILD-ROADMAP.md` §B13, `Tech-Stack.md` §6).
 *
 * A policy settles what it can. When it cannot, it returns [MergeAction.CONFLICT] with
 * both snapshots and a stable reason key; the engine records the conflict and the UI
 * shows both sides with their provenance. Nothing is resolved silently.
 */
class AppendOnlySnapshotPolicy(override val entity: String) : MergePolicy<SyncSnapshot> {
    override fun merge(
        local: SyncSnapshot,
        remote: SyncSnapshot,
        localClock: Long,
        remoteClock: Long,
    ): MergeOutcome<SyncSnapshot> = MergeOutcome.keepLocal(local)
}

/** Free text and notes: the later writer wins; both versions remain in the change log. */
class LastWriterWinsSnapshotPolicy(override val entity: String) : MergePolicy<SyncSnapshot> {
    override fun merge(
        local: SyncSnapshot,
        remote: SyncSnapshot,
        localClock: Long,
        remoteClock: Long,
    ): MergeOutcome<SyncSnapshot> = if (remoteClock > localClock) {
        MergeOutcome.takeRemote(remote)
    } else {
        MergeOutcome.keepLocal(local)
    }
}

/**
 * An editable document carrying a `revision` column. A higher revision wins. Two edits
 * made from the same revision on two devices are a real conflict, not a race the
 * clock can settle: the policy hands both back to the user.
 */
class RevisionSnapshotPolicy(override val entity: String) : MergePolicy<SyncSnapshot> {
    override fun merge(
        local: SyncSnapshot,
        remote: SyncSnapshot,
        localClock: Long,
        remoteClock: Long,
    ): MergeOutcome<SyncSnapshot> {
        if (local.fields == remote.fields) return MergeOutcome.keepLocal(local)
        val localRevision = local.fields["revision"]?.toLongOrNull() ?: 0L
        val remoteRevision = remote.fields["revision"]?.toLongOrNull() ?: 0L
        return when {
            remoteRevision > localRevision -> MergeOutcome.takeRemote(remote)
            localRevision > remoteRevision -> MergeOutcome.keepLocal(local)
            else -> MergeOutcome(
                action = MergeAction.CONFLICT,
                value = local,
                conflict = MergeConflict(
                    entity = entity,
                    local = local,
                    remote = remote,
                    localClock = localClock,
                    remoteClock = remoteClock,
                    reasonKey = "sync.conflict.same_revision",
                ),
            )
        }
    }
}

/**
 * Plans and settings merge field by field. Each field carries the clock of its last
 * write, so a units change on one device and an accent change on another both survive.
 * A field only one side wrote is taken; a field both wrote keeps the later clock.
 */
class FieldMergeSnapshotPolicy(override val entity: String) : MergePolicy<SyncSnapshot> {
    override fun merge(
        local: SyncSnapshot,
        remote: SyncSnapshot,
        localClock: Long,
        remoteClock: Long,
    ): MergeOutcome<SyncSnapshot> {
        val fields = local.fields.toMutableMap()
        val clocks = local.fieldClocks.toMutableMap()
        for ((field, remoteClockText) in remote.fieldClocks) {
            val localClockText = local.fieldClocks[field]
            if (localClockText == null || remoteClockText > localClockText) {
                fields[field] = remote.fields[field]
                clocks[field] = remoteClockText
            }
        }
        // A key present only in the remote row but absent from its field clocks is a
        // pre-B13 snapshot; still take it, because the local side never wrote it.
        for ((field, value) in remote.fields) {
            if (field !in fields) fields[field] = value
        }
        return MergeOutcome.merged(SyncSnapshot(fields, clocks))
    }
}

/** Derived rows are rebuilt from their inputs and never cross the wire. */
class NeverSyncedSnapshotPolicy(override val entity: String) : MergePolicy<SyncSnapshot> {
    override fun merge(
        local: SyncSnapshot,
        remote: SyncSnapshot,
        localClock: Long,
        remoteClock: Long,
    ): MergeOutcome<SyncSnapshot> = MergeOutcome.keepLocal(local)
}

/** The per-entity registry the app and the server both read. */
class SyncMergePolicyRegistry(policies: List<MergePolicy<SyncSnapshot>>) {
    private val byEntity: Map<String, MergePolicy<SyncSnapshot>> =
        policies.associateBy(MergePolicy<SyncSnapshot>::entity)

    fun policyFor(entity: String): MergePolicy<SyncSnapshot>? = byEntity[entity]

    fun entities(): List<String> = byEntity.keys.sorted()

    fun neverSynced(): List<String> = byEntity
        .filterValues { it is NeverSyncedSnapshotPolicy }
        .keys
        .sorted()

    companion object {
        private fun appendOnly(vararg entities: String) =
            entities.map { AppendOnlySnapshotPolicy(it) }

        private fun revision(vararg entities: String) = entities.map { RevisionSnapshotPolicy(it) }

        private fun lastWriterWins(vararg entities: String) =
            entities.map { LastWriterWinsSnapshotPolicy(it) }

        /** The one list. Adding an entity here is what makes it syncable. */
        fun default(): SyncMergePolicyRegistry = SyncMergePolicyRegistry(
            buildList {
                addAll(appendOnly("observation", "work_set", "activity_point", "medication_dose"))
                addAll(
                    revision(
                        "profile",
                        "exercise",
                        "routine",
                        "routine_exercise",
                        "programme",
                        "programme_routine",
                        "session",
                        "session_exercise",
                        "food",
                        "meal",
                        "entry",
                        "recipe_item",
                        "meal_template",
                        "meal_template_item",
                        "water_container",
                        "nutrition_target",
                        "fast_window",
                        "activity",
                        "medication",
                        "symptom",
                        "symptom_episode",
                        "lab_result",
                        "allergy",
                        "condition",
                        "vaccination",
                    ),
                )
                addAll(lastWriterWins("note", "source"))
                add(FieldMergeSnapshotPolicy("settings"))
                add(NeverSyncedSnapshotPolicy("daily_rollup"))
                add(NeverSyncedSnapshotPolicy("raw_record"))
                add(NeverSyncedSnapshotPolicy("tombstone"))
            },
        )
    }
}
