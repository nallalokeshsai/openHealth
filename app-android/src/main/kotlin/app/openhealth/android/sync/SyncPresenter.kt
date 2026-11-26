package app.openhealth.android.sync

import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.SecretVault
import app.openhealth.core.sync.ChangeRecord
import app.openhealth.core.sync.PairingRequest
import app.openhealth.core.sync.SyncConflict
import app.openhealth.core.sync.SyncStatus
import app.openhealth.data.LocalStore
import app.openhealth.data.repo.SettingKeys
import app.openhealth.sync.KtorSyncTransport
import app.openhealth.sync.RetryPolicy
import app.openhealth.sync.SyncEngine
import app.openhealth.sync.SyncResult
import app.openhealth.ui.screens.sync.SyncConflictModel
import app.openhealth.ui.screens.sync.SyncFieldModel
import app.openhealth.ui.screens.sync.SyncModel
import app.openhealth.ui.screens.sync.SyncSideModel
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The device half of B13: pairing, the sync pass and the conflict model. It is the only
 * place the bearer token is read, and it never logs or returns it (`pr.md` §6.23).
 */
@Suppress("TooManyFunctions")
class SyncPresenter(
    private val store: LocalStore,
    private val vault: SecretVault,
    private val clock: ClockSeam,
    private val zone: TimeZone,
) {
    fun serverUrl(): String? = store.settings.get(SettingKeys.SYNC_SERVER_URL)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    fun isPaired(): Boolean = serverUrl() != null && token() != null

    fun model(busy: Boolean = false): SyncModel {
        val status = store.settings.get(SettingKeys.SYNC_STATUS)
            ?.let { runCatching { SyncStatus.valueOf(it) }.getOrNull() }
            ?: SyncStatus.LOCAL_ONLY
        val last = store.sync.lastSyncedAt()
        return SyncModel(
            paired = isPaired(),
            serverUrl = serverUrl().orEmpty(),
            status = status,
            lastSyncedLabel = last?.let(::stamp),
            pending = store.sync.pendingCount(),
            error = store.settings.get(SettingKeys.SYNC_LAST_ERROR)?.takeIf { it.isNotBlank() },
            conflicts = store.sync.conflicts().map(::conflict),
            busy = busy,
        )
    }

    /** What the Settings row says in one line; local-first is never a warning. */
    fun summary(): String {
        if (!isPaired()) return "Local only"
        val status = store.settings.get(SettingKeys.SYNC_STATUS)
            ?.let { runCatching { SyncStatus.valueOf(it) }.getOrNull() }
            ?: SyncStatus.LOCAL_ONLY
        val pending = store.sync.pendingCount()
        return when (status) {
            SyncStatus.SYNCED -> if (pending == 0) "Synced" else "$pending waiting"
            SyncStatus.SYNCING -> "Syncing"
            SyncStatus.OFFLINE -> "Offline · $pending waiting"
            SyncStatus.ERROR -> "Needs attention"
            SyncStatus.LOCAL_ONLY -> "Local only"
        }
    }

    /** Exchange a one-time code for a scoped token; returns null when it did not work. */
    suspend fun pair(serverUrl: String, code: String): SyncModel? {
        val base = runCatching { KtorSyncTransport.requireSecureBase(serverUrl, true) }
            .getOrNull()
        val paired = base?.let { url ->
            runCatching { KtorSyncTransport(url, token = "").pair(PairingRequest(code, "Android")) }
                .getOrNull()
        }
        if (base != null && paired != null) {
            vault.put(TOKEN, paired.token.toByteArray())
            store.settings.put(SettingKeys.SYNC_SERVER_URL, base)
            // A different server has its own sequence; a cursor from the old one would
            // skip its whole log. Pairing starts the pull from the beginning.
            store.settings.put(SettingKeys.SYNC_PULL_CURSOR, "0")
            store.settings.put(SettingKeys.SYNC_ENABLED, "true")
            store.settings.put(SettingKeys.SYNC_STATUS, SyncStatus.LOCAL_ONLY.name)
            store.settings.put(SettingKeys.SYNC_LAST_ERROR, "")
        }
        return if (base != null && paired != null) model() else null
    }

    suspend fun syncNow(): SyncResult {
        val url = serverUrl()
        val token = token()
        val result = if (url != null && token != null) {
            val engine = SyncEngine(
                store = store.sync,
                transport = KtorSyncTransport(url, token),
                now = { clock.now().toEpochMilliseconds() },
                retry = RetryPolicy(attempts = RETRY_ATTEMPTS),
            )
            engine.syncOnce()
        } else {
            SyncResult(0, 0, 0, SyncStatus.LOCAL_ONLY, "this device is not paired")
        }
        return result
    }

    fun disconnect() {
        vault.remove(TOKEN)
        store.settings.put(SettingKeys.SYNC_ENABLED, "false")
        store.settings.put(SettingKeys.SYNC_STATUS, SyncStatus.LOCAL_ONLY.name)
        store.settings.put(SettingKeys.SYNC_LAST_ERROR, "")
        // The server URL stays visible for a re-pair; the token is gone, so nothing can
        // leave the device until a new code is exchanged.
    }

    /** Resolve one conflict and return the change to push, or null when it is gone. */
    fun keep(conflictId: String, keepLocal: Boolean): ChangeRecord? = store.sync.resolveConflict(
        id = conflictId,
        keepLocal = keepLocal,
        atMillis = clock.now().toEpochMilliseconds(),
    )

    private fun token(): String? = vault.get(TOKEN)?.toString(Charsets.UTF_8)

    private fun conflict(conflict: SyncConflict): SyncConflictModel = SyncConflictModel(
        id = conflict.id,
        entityLabel = entityLabel(conflict.entity),
        entityId = conflict.entityId,
        reason = "Both devices edited the same revision. Choose the version to keep.",
        local = side(conflict.local, "This device"),
        remote = side(conflict.remote, "Other device"),
    )

    private fun side(change: ChangeRecord, label: String): SyncSideModel = SyncSideModel(
        device = "$label · ${change.deviceId.take(DEVICE_PREFIX_CHARS)}",
        whenLabel = stamp(change.clock().wallMillis),
        fields = change.fields.entries
            .filter { (key, _) -> key !in HIDDEN_FIELDS }
            .take(MAX_CONFLICT_FIELDS)
            .map { (key, value) -> SyncFieldModel(humanize(key), value.orEmpty().ifBlank { "—" }) },
    )

    private fun stamp(millis: Long): String {
        val local = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
        return "%04d-%02d-%02d %02d:%02d".format(
            local.year,
            local.monthNumber,
            local.dayOfMonth,
            local.hour,
            local.minute,
        )
    }

    private fun entityLabel(entity: String): String = when (entity) {
        "profile" -> "Profile"
        "settings" -> "Setting"
        "note" -> "Note"
        "routine" -> "Routine"
        "session" -> "Workout"
        "food" -> "Food"
        "entry" -> "Diary entry"
        "measurement" -> "Measurement"
        "vital" -> "Vital"
        "activity" -> "Activity"
        else -> "Record"
    }

    private fun humanize(key: String): String = key
        .replace('_', ' ')
        .replaceFirstChar { it.uppercase() }

    private companion object {
        const val TOKEN = "sync.token"
        const val RETRY_ATTEMPTS = 4
        const val DEVICE_PREFIX_CHARS = 8
        const val MAX_CONFLICT_FIELDS = 6
        val HIDDEN_FIELDS = setOf(
            "revision",
            "idempotency_key",
            "created_at",
            "updated_at",
            "recorded_at",
            "zone_id",
            "source_id",
            "raw_record_id",
            "observation_id",
            "app_version",
        )
    }
}
