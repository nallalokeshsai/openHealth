package app.openhealth.core.merge

/**
 * Per-entity merge rules, declared in `:core` and used by `:sync` when it lands
 * (`Tech-Stack.md` §6, `pr.md` §7.7). A policy may settle a conflict; a conflict it
 * cannot settle is surfaced with both sides and never resolved silently.
 */
enum class MergeAction {
    KEEP_LOCAL,
    TAKE_REMOTE,
    MERGED,
    CONFLICT,
}

data class MergeConflict<T>(
    val entity: String,
    val local: T?,
    val remote: T?,
    val localClock: Long,
    val remoteClock: Long,
    val reasonKey: String,
)

data class MergeOutcome<T>(
    val action: MergeAction,
    val value: T?,
    val conflict: MergeConflict<T>? = null,
) {
    init {
        require((action == MergeAction.CONFLICT) == (conflict != null)) {
            "a conflict outcome carries the conflict, and only a conflict carries one"
        }
    }

    companion object {
        fun <T> keepLocal(local: T): MergeOutcome<T> = MergeOutcome(MergeAction.KEEP_LOCAL, local)

        fun <T> takeRemote(remote: T): MergeOutcome<T> =
            MergeOutcome(MergeAction.TAKE_REMOTE, remote)

        fun <T> merged(value: T): MergeOutcome<T> = MergeOutcome(MergeAction.MERGED, value)
    }
}

/** One entity type, one rule. `localClock` and `remoteClock` are hybrid logical clocks. */
interface MergePolicy<T> {
    val entity: String

    fun merge(local: T, remote: T, localClock: Long, remoteClock: Long): MergeOutcome<T>
}

/** Measurements and sets are append-only: a key seen before is a duplicate, not an edit. */
class AppendOnlyPolicy<T>(override val entity: String) : MergePolicy<T> {
    override fun merge(local: T, remote: T, localClock: Long, remoteClock: Long): MergeOutcome<T> =
        MergeOutcome.keepLocal(local)
}

/** Free text and notes: the later clock wins, and both versions stay in the change log. */
class LastWriterWinsPolicy<T>(override val entity: String) : MergePolicy<T> {
    override fun merge(local: T, remote: T, localClock: Long, remoteClock: Long): MergeOutcome<T> =
        if (remoteClock >
            localClock
        ) {
            MergeOutcome.takeRemote(remote)
        } else {
            MergeOutcome.keepLocal(local)
        }
}

/**
 * Settings merge key by key: a key only the remote device has is added, and a key both
 * changed keeps the local value (the conflict is visible in the change log). The
 * transport that carries per-field clocks is B13's; this is the rule it must implement.
 */
class SettingsFieldMergePolicy(override val entity: String = "settings") :
    MergePolicy<Map<String, String>> {
    override fun merge(
        local: Map<String, String>,
        remote: Map<String, String>,
        localClock: Long,
        remoteClock: Long,
    ): MergeOutcome<Map<String, String>> =
        MergeOutcome.merged(local + remote.filterKeys { it !in local })
}

/** Declared for completeness: rollups are derived and never synced (`Tech-Stack.md` §6). */
class NeverSyncedPolicy<T>(override val entity: String) : MergePolicy<T> {
    override fun merge(local: T, remote: T, localClock: Long, remoteClock: Long): MergeOutcome<T> =
        MergeOutcome.keepLocal(local)
}

/** The per-entity registry every sync client reads. */
class MergePolicyRegistry(policies: List<MergePolicy<*>>) {
    private val byEntity: Map<String, MergePolicy<*>> = policies.associateBy(MergePolicy<*>::entity)

    fun get(entity: String): MergePolicy<*>? = byEntity[entity]

    fun entities(): List<String> = byEntity.keys.sorted()

    fun neverSynced(): List<String> = byEntity
        .filterValues { it is NeverSyncedPolicy<*> }
        .keys
        .sorted()

    companion object {
        fun default(): MergePolicyRegistry = MergePolicyRegistry(
            listOf(
                AppendOnlyPolicy<Any>("observation"),
                AppendOnlyPolicy<Any>("work_set"),
                AppendOnlyPolicy<Any>("raw_record"),
                LastWriterWinsPolicy<Any>("note"),
                LastWriterWinsPolicy<Any>("journal_entry"),
                SettingsFieldMergePolicy(),
                NeverSyncedPolicy<Any>("daily_rollup"),
            ),
        )
    }
}
