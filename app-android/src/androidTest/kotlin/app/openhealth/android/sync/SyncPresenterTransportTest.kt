package app.openhealth.android.sync

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openhealth.android.data.AndroidDatabaseFactory
import app.openhealth.android.data.AndroidSecretVault
import app.openhealth.core.preferences.DisplayPolicy
import app.openhealth.core.seam.SystemClock
import app.openhealth.core.sync.ChangeOperation
import app.openhealth.core.sync.ChangeRecord
import app.openhealth.data.LocalStore
import app.openhealth.data.repo.SettingKeys
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A sync pass must reuse one pooled HTTP client per (server, token) and close it when
 * the pairing changes (`docs/adr/0018-sync-server.md` §6). Building a fresh
 * `KtorSyncTransport` inside every pass creates a new CIO engine with its own thread
 * pool and never closes it, so a day of syncing leaks one pool per sync.
 */
@RunWith(AndroidJUnit4::class)
class SyncPresenterTransportTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "sync-transport-test.db"
    private val vaultName = "sync-transport-test.vault"

    @Before
    @After
    fun clean() {
        context.deleteDatabase(name)
        context.getSharedPreferences(vaultName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun presenter(store: LocalStore, policy: DisplayPolicy = DisplayPolicy.Standard) =
        SyncPresenter(
            context = context,
            store = store,
            vault = AndroidSecretVault(context, vaultName),
            clock = SystemClock,
            policy = { policy },
        )

    private fun change(name: String, weight: String, key: String) = ChangeRecord(
        entity = "profile",
        entityId = "me",
        operation = ChangeOperation.UPSERT,
        fields = mapOf("name" to name, "weight_kg" to weight, "revision" to "3"),
        fieldClocks = emptyMap(),
        idempotencyKey = key,
        hlc = "0000000000000001-00000000",
        deviceId = key,
        schemaVersion = 11,
        recordedAt = 1L,
    )

    private fun seedProfileConflict(store: LocalStore) {
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        store.db.syncQueries.insertConflict(
            "profile:me:remote",
            "profile",
            "me",
            json.encodeToString(ChangeRecord.serializer(), change("Sam", "82.4", "local")),
            json.encodeToString(ChangeRecord.serializer(), change("Alex", "79.1", "remote")),
            "sync.conflict.same_revision",
            1L,
        )
    }

    /**
     * `pr.md` §6.25 and `DESIGN.md` §7.6: one switch hides weight everywhere. A conflict
     * card prints field values, so it has to ask the same policy every other surface
     * asks; the name still identifies the record.
     */
    @Test
    fun weightNeutralModeHidesTheWeightOnAConflictCard() {
        AndroidDatabaseFactory(context, AndroidSecretVault(context), name).open().use { opened ->
            val store = LocalStore(opened.driver, opened.db)
            seedProfileConflict(store)

            val shown = presenter(store, DisplayPolicy.Standard).model().conflicts.single()
            assertEquals(
                listOf("name", "weight_kg"),
                shown.local.fields.map { it.key },
            )

            val neutral = presenter(store, DisplayPolicy.WeightNeutral).model().conflicts.single()
            assertEquals(listOf("name"), neutral.local.fields.map { it.key })
            assertEquals(listOf("name"), neutral.remote.fields.map { it.key })
        }
    }

    @Test
    fun aSyncPassUsesThePooledClientInsteadOfBuildingAndLeakingANewOne() {
        AndroidDatabaseFactory(context, AndroidSecretVault(context), name).open().use { opened ->
            val store = LocalStore(opened.driver, opened.db)
            val presenter = presenter(store)
            // Pair by hand: the server is unreachable, which is enough — the pass still
            // has to go through the pool to get there.
            store.settings.put(SettingKeys.SYNC_SERVER_URL, UNREACHABLE)
            AndroidSecretVault(context, vaultName)
                .put("sync.token", "token-one".toByteArray())

            runBlocking { presenter.syncNow() }
            val pooled = presenter.pooledTransport()
            assertNotNull("a sync pass must open its client through the pool", pooled)

            runBlocking { presenter.syncNow() }
            assertSame(
                "a second pass must reuse the same client",
                pooled,
                presenter.pooledTransport(),
            )

            presenter.disconnect()
            assertNull("disconnect closes and clears the pool", presenter.pooledTransport())
        }
    }

    @Test
    fun theClientIsReplacedWhenTheServerOrTheTokenChanges() {
        AndroidDatabaseFactory(context, AndroidSecretVault(context), name).open().use { opened ->
            val presenter = presenter(LocalStore(opened.driver, opened.db))
            val first = presenter.transport(SERVER, "token-one")
            assertSame(first, presenter.transport(SERVER, "token-one"))

            val afterRotation = presenter.transport(SERVER, "token-two")
            assertNotSame("a new token must build a new client", first, afterRotation)

            val afterMove = presenter.transport("https://other.example", "token-two")
            assertNotSame("a new server must build a new client", afterRotation, afterMove)
            presenter.disconnect()
        }
    }

    private companion object {
        const val SERVER = "https://sync.example"

        /** Closed loopback port: the pass fails fast without leaving the device. */
        const val UNREACHABLE = "https://127.0.0.1:1"
    }
}
