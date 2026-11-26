package app.openhealth.ui.screens.sync

import app.openhealth.core.sync.SyncStatus

/** One field of one side of a conflict; the screen owns its label. */
data class SyncFieldModel(val key: String, val value: String)

/** One side of a conflict: which device wrote it, when, and what it changed. */
data class SyncSideModel(
    val device: String,
    val whenLabel: String,
    val fields: List<SyncFieldModel>,
)

/** A conflict the merge policy could not settle (`pr.md` §7.7). */
data class SyncConflictModel(
    val id: String,
    val entity: String,
    val entityId: String,
    val local: SyncSideModel,
    val remote: SyncSideModel,
) {
    /** The value a button can name, from the first differing field. */
    fun headline(side: SyncSideModel): String =
        side.fields.firstOrNull()?.value?.takeIf { it.isNotBlank() } ?: ""
}

/**
 * The Sync page. [paired] is false until a server URL and token exist; the server is
 * optional, so the page never blocks a local action (`pr.md` §2.3, §6.24).
 */
data class SyncModel(
    val paired: Boolean,
    val serverUrl: String,
    val status: SyncStatus,
    val lastSyncedLabel: String?,
    val pending: Int,
    val error: String?,
    val conflicts: List<SyncConflictModel>,
    val busy: Boolean,
    /** A pairing attempt that failed, shown under the code field. */
    val pairError: String? = null,
)
