package app.openhealth.android.sync

import android.content.Context
import app.openhealth.core.preferences.DisplayPolicy
import app.openhealth.core.preferences.SensitiveMetric
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
import app.openhealth.ui.R as UiR
import app.openhealth.ui.screens.sync.SyncConflictModel
import app.openhealth.ui.screens.sync.SyncFieldModel
import app.openhealth.ui.screens.sync.SyncModel
import app.openhealth.ui.screens.sync.SyncSideModel

/**
 * The device half of B13: pairing, the sync pass and the conflict model. It is the only
 * place the bearer token is read, and it never logs or returns it (`pr.md` §6.23).
 */
@Suppress("TooManyFunctions")
class SyncPresenter(
    private val context: Context,
    private val store: LocalStore,
    private val vault: SecretVault,
    private val clock: ClockSeam,
    /** The one policy every surface asks before it prints a sensitive number (§6.25). */
    private val policy: () -> DisplayPolicy = { DisplayPolicy.Standard },
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
        if (!isPaired()) return context.getString(UiR.string.settings_sync_local)
        val status = store.settings.get(SettingKeys.SYNC_STATUS)
            ?.let { runCatching { SyncStatus.valueOf(it) }.getOrNull() }
            ?: SyncStatus.LOCAL_ONLY
        val pending = store.sync.pendingCount()
        val conflicts = store.sync.openConflictCount()
        return when (status) {
            SyncStatus.SYNCED -> if (pending == 0) {
                context.getString(UiR.string.settings_sync_synced)
            } else {
                context.getString(UiR.string.settings_sync_waiting, pending)
            }
            SyncStatus.SYNCING -> context.getString(UiR.string.sync_status_syncing)
            SyncStatus.OFFLINE -> context.getString(UiR.string.settings_sync_offline, pending)
            SyncStatus.ERROR -> context.getString(UiR.string.settings_sync_error)
            SyncStatus.LOCAL_ONLY -> context.getString(UiR.string.settings_sync_local)
        }.let { line ->
            if (conflicts == 0) {
                line
            } else {
                "$line · " + context.resources.getQuantityString(
                    UiR.plurals.sync_conflict_count,
                    conflicts,
                    conflicts,
                )
            }
        }
    }

    /** Exchange a one-time code for a scoped token; returns null when it did not work. */
    suspend fun pair(serverUrl: String, code: String): SyncModel? {
        val base = runCatching { KtorSyncTransport.requireSecureBase(serverUrl, true) }
            .getOrNull()
        val paired = base?.let { url ->
            val client = KtorSyncTransport(url, token = "")
            try {
                runCatching { client.pair(PairingRequest(code, "Android")) }.getOrNull()
            } finally {
                client.close()
            }
        }
        if (base != null && paired != null) {
            closeTransport()
            vault.put(TOKEN, paired.token.toByteArray())
            store.settings.put(SettingKeys.SYNC_SERVER_URL, base)
            // A different server has its own sequence; a cursor from the old one would
            // skip its whole log. Pairing starts the pull from the beginning.
            store.settings.put(SettingKeys.SYNC_PULL_CURSOR, "0")
            store.settings.put(SettingKeys.SYNC_ENABLED, "true")
            store.settings.put(SettingKeys.SYNC_STATUS, SyncStatus.LOCAL_ONLY.name)
            store.settings.put(SettingKeys.SYNC_LAST_ERROR, "")
            store.sync.backfillIfNeeded()
        }
        return if (base != null && paired != null) model() else null
    }

    suspend fun syncNow(): SyncResult {
        val url = serverUrl()
        val token = token()
        val result = if (url != null && token != null) {
            val engine = SyncEngine(
                store = store.sync,
                // One pooled client per (server, token), closed on pair or disconnect: a
                // fresh HttpClient per sync would leak its CIO pool and its threads.
                transport = transport(url, token),
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
        closeTransport()
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

    private var client: KtorSyncTransport? = null
    private var clientKey: String? = null

    /** The pooled client, or null when none is open. Visible to the leak regression test. */
    internal fun pooledTransport(): KtorSyncTransport? = client

    /** One client per (server, token). Visible so a test can prove it is not rebuilt. */
    internal fun transport(url: String, token: String): KtorSyncTransport {
        val key = "$url|$token"
        if (client == null || clientKey != key) {
            closeTransport()
            client = KtorSyncTransport(url, token)
            clientKey = key
        }
        return client!!
    }

    private fun closeTransport() {
        client?.close()
        client = null
        clientKey = null
    }

    private fun conflict(conflict: SyncConflict): SyncConflictModel {
        val display = policy()
        return SyncConflictModel(
            id = conflict.id,
            entity = conflict.entity,
            entityId = conflict.entityId,
            local = side(conflict.local, display),
            remote = side(conflict.remote, display),
        )
    }

    private fun side(change: ChangeRecord, display: DisplayPolicy): SyncSideModel = SyncSideModel(
        device = change.deviceId.take(DEVICE_PREFIX_CHARS),
        whenLabel = stamp(change.clock().wallMillis),
        fields = change.fields.entries
            .filter { (key, _) -> key !in HIDDEN_FIELDS }
            .filter { (key, _) ->
                metricOf(change.entity, key, change.fields)?.let(display::shows) != false
            }
            .take(MAX_CONFLICT_FIELDS)
            .map { (key, value) -> SyncFieldModel(key, value.orEmpty()) },
    )

    /**
     * Which sensitive metric a column carries, so weight-neutral mode hides the same
     * numbers on a conflict card as everywhere else. Null means the column is not a
     * sensitive metric at all and is always shown.
     */
    private fun metricOf(
        entity: String,
        column: String,
        fields: Map<String, String?>,
    ): SensitiveMetric? = when {
        column in WEIGHT_COLUMNS -> SensitiveMetric.WEIGHT
        column in BODY_FAT_COLUMNS -> SensitiveMetric.BODY_FAT
        column in ENERGY_COLUMNS -> SensitiveMetric.CALORIES
        // A measurement row names its own metric; `value` means whatever `type` says.
        entity == "measurement" && column == "value" -> when (fields["type"]?.lowercase()) {
            in WEIGHT_MEASUREMENT_TYPES -> SensitiveMetric.WEIGHT
            in BODY_FAT_MEASUREMENT_TYPES -> SensitiveMetric.BODY_FAT
            else -> null
        }
        else -> null
    }

    private fun stamp(millis: Long): String {
        val locale = context.resources.configuration.locales[0]
        val formatter = java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.SHORT,
            java.text.DateFormat.SHORT,
            locale,
        )
        return formatter.format(java.util.Date(millis))
    }

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
        val WEIGHT_COLUMNS = setOf("weight_kg", "lean_mass_kg", "goal_weight_kg")
        val BODY_FAT_COLUMNS = setOf("body_fat_percent", "body_fat_pct", "body_fat")
        val ENERGY_COLUMNS = setOf("energy_kcal", "kcal", "calories", "energy_in_kcal")
        val WEIGHT_MEASUREMENT_TYPES = setOf("weight", "lean_mass", "fat_mass")
        val BODY_FAT_MEASUREMENT_TYPES = setOf("body_fat", "body_fat_percent")
    }
}
