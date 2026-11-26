package app.openhealth.server

import app.cash.sqldelight.Query
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.openhealth.core.ids.NoteId
import app.openhealth.core.sync.ChangeRecord
import app.openhealth.core.sync.PullRequest
import app.openhealth.core.sync.PullResponse
import app.openhealth.core.sync.PushRequest
import app.openhealth.core.sync.PushResponse
import app.openhealth.data.LocalStore
import app.openhealth.data.db.DatabaseDriverFactory
import app.openhealth.data.db.DatabaseOpener
import app.openhealth.sync.KtorSyncTransport
import app.openhealth.sync.RetryPolicy
import app.openhealth.sync.SyncEngine
import app.openhealth.sync.SyncTransport
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.io.IOException
import java.net.ServerSocket
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * The B13 exit against the real pieces: two SQLite stores (the same `:data` code the app
 * runs), the real Ktor application over a real socket, the real JSON transport, and the
 * real merge engine. One device edits offline while the other does; they then converge.
 * A response lost after the server committed is retried without a duplicate.
 */
class SyncIntegrationTest {
    private val config = ServerConfig(
        origin = "http://127.0.0.1:1",
        allowedUsers = setOf("owner"),
    )
    private val authStore = InMemoryAuthStore()
    private val account = authStore.createAccount(
        Account("11111111-1111-1111-1111-111111111111", "owner", "Owner", createdAt = 0),
    )
    private val rawToken = "integration-token-0123456789"
    private val auth = AuthService(config, authStore) { 1_000L }
    private val changes = InMemoryChangeStore()
    private val servers = mutableListOf<io.ktor.server.engine.EmbeddedServer<*, *>>()
    private val directories = mutableListOf<java.nio.file.Path>()
    private val oneThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    /** sqlite-jdbc pins a connection to its opening thread; the whole test stays on one. */
    private fun <T> onOneThread(block: suspend () -> T): T = runBlocking(oneThread) { block() }

    init {
        authStore.createToken(
            StoredToken(
                id = "22222222-2222-2222-2222-222222222222",
                accountId = account.id,
                tokenHash = Tokens.hash(rawToken),
                scopes = SCOPE_SYNC,
                label = "integration",
                deviceId = "device-a",
                createdAt = 0,
                expiresAt = null,
            ),
        )
    }

    @AfterTest
    fun cleanUp() {
        servers.forEach { it.stop(0, 0) }
        directories.forEach { directory ->
            java.nio.file.Files.walk(directory).sorted(Comparator.reverseOrder()).forEach {
                java.nio.file.Files.deleteIfExists(it)
            }
        }
    }

    private fun startServer(): String {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(
            Netty,
            port = port,
            host = "127.0.0.1",
        ) {
            openHealthModule(config.copy(origin = "http://127.0.0.1:$port"), auth, changes)
        }.start(wait = false)
        servers += server
        return "http://127.0.0.1:$port"
    }

    /** sqlite-jdbc's own driver pins a connection to its opening thread; this one does
     * not, so the Ktor client may resume the engine on any thread. */
    private class PerCallSqliteDriver(
        private val url: String,
        private val properties: Properties,
    ) : JdbcDriver() {
        override fun getConnection(): Connection = DriverManager.getConnection(url, properties)

        override fun closeConnection(connection: Connection) = connection.close()

        override fun close() = Unit

        override fun addListener(vararg queryKeys: String, listener: Query.Listener) = Unit

        override fun removeListener(vararg queryKeys: String, listener: Query.Listener) = Unit

        override fun notifyListeners(vararg queryKeys: String) = Unit
    }

    private fun store(label: String): LocalStore {
        val directory = Files.createTempDirectory("openhealth-sync-$label")
        directories.add(directory)
        val path = directory.resolve("$label.db").toString()
        val factory = DatabaseDriverFactory { foreignKeys ->
            PerCallSqliteDriver(
                "jdbc:sqlite:$path",
                Properties().apply { setProperty("foreign_keys", foreignKeys.toString()) },
            )
        }
        val opened = DatabaseOpener().open(factory)
        val store = LocalStore(opened.driver, opened.db)
        store.sources.upsert(
            app.openhealth.core.source.Source(
                id = app.openhealth.core.ids.SourceId("manual"),
                kind = app.openhealth.core.observation.SourceKind.MANUAL,
                label = "Manual",
            ),
        )
        return store
    }

    private fun note(store: LocalStore, id: String, body: String) {
        val zone = TimeZone.of("Europe/Berlin")
        val happened = kotlinx.datetime.LocalDateTime(
            LocalDate(2026, 11, 26),
            kotlinx.datetime.LocalTime(12, 0),
        ).toInstant(zone).toEpochMilliseconds()
        store.notes.insert(
            app.openhealth.data.db.Note(
                id = id,
                body = body,
                local_date = "2026-11-26",
                zone_id = zone.id,
                happened_at = happened,
                recorded_at = happened,
                edited_at = null,
                source_id = "manual",
                raw_record_id = null,
                idempotency_key = id,
                device = null,
                app_version = null,
                revision = 1L,
            ),
        )
    }

    private fun profile(store: LocalStore, name: String) {
        store.profile.upsert(
            app.openhealth.data.db.Profile(
                id = "profile",
                name = name,
                date_of_birth = null,
                sex_at_birth = null,
                gender = null,
                height_cm = null,
                weight_kg = null,
                units = "metric",
                zone_id = "Europe/Berlin",
                language = "en",
                goal = null,
                activity_level = null,
                training_experience = null,
                weight_neutral = false,
                intake_tier = 1L,
                created_at = 0,
                updated_at = 0,
                source_id = "manual",
                revision = 2L,
            ),
        )
    }

    private fun engine(
        store: LocalStore,
        url: String,
        transport: SyncTransport = KtorSyncTransport(url, rawToken),
    ) = SyncEngine(
        store = store.sync,
        transport = transport,
        now = { 1_000L },
        retry = RetryPolicy(attempts = 3, baseDelayMillis = 0),
        sleep = {},
    )

    @Test
    fun twoStoresConvergeAfterOfflineEditsAndAConflictIsResolved() = onOneThread {
        val url = startServer()
        val a = store("a")
        val b = store("b")

        // Both edit with no connection at all.
        note(a, "note-a", "A wrote this offline")
        note(b, "note-b", "B wrote this offline")
        a.settings.put("appearance.accent", "blue")
        b.settings.put("appearance.theme", "light")
        profile(a, "Alex")
        profile(b, "Sam")

        engine(a, url).syncOnce()
        engine(b, url).syncOnce()
        engine(a, url).syncOnce()
        engine(b, url).syncOnce()

        assertTrue(a.sync.pendingChanges(10).isEmpty())
        assertTrue(b.sync.pendingChanges(10).isEmpty())
        // Append-only notes both survive; settings merge key by key.
        assertNotNull(a.notes.byId(NoteId("note-b")))
        assertNotNull(b.notes.byId(NoteId("note-a")))
        assertEquals("blue", b.settings.get("appearance.accent"))
        assertEquals("light", a.settings.get("appearance.theme"))
        // The same revision on both devices is a real conflict, not a silent pick.
        assertEquals(1, a.sync.openConflictCount())
        assertEquals(1, b.sync.openConflictCount())
        val conflict = a.sync.conflicts().single()
        assertEquals("Alex", conflict.local.fields["name"])
        assertEquals("Sam", conflict.remote.fields["name"])

        a.sync.resolveConflict(conflict.id, keepLocal = false, atMillis = 2_000L)
        engine(a, url).syncOnce()
        engine(b, url).syncOnce()
        engine(a, url).syncOnce()
        assertEquals(0, a.sync.openConflictCount())
        assertEquals(0, b.sync.openConflictCount())
        assertEquals("Sam", a.db.profileQueries.profileById("profile").executeAsOne().name)
        assertEquals("Sam", b.db.profileQueries.profileById("profile").executeAsOne().name)
    }

    @Test
    fun aResponseLostAfterTheServerCommittedIsRetriedWithoutADuplicate() = onOneThread {
        val url = startServer()
        val a = store("retry-a")
        val b = store("retry-b")
        repeat(5) { index -> note(a, "retry-$index", "offline $index") }
        val before = changes.pull(account.id, 0, 1_000).changes.size
        val queued = a.sync.pendingCount()

        val real = KtorSyncTransport(url, rawToken)
        val loseOnce = object : SyncTransport {
            var lost = false
            override suspend fun push(request: PushRequest): PushResponse {
                val response = real.push(request)
                if (!lost) {
                    lost = true
                    throw IOException("connection lost after the server committed")
                }
                return response
            }

            override suspend fun pull(request: PullRequest): PullResponse = real.pull(request)
        }
        val result = engine(a, url, loseOnce).syncOnce()
        assertEquals(app.openhealth.core.sync.SyncStatus.SYNCED, result.status)
        assertTrue(a.sync.pendingChanges(10).isEmpty())
        val after = changes.pull(account.id, 0, 1_000).changes.size
        assertEquals(before + queued, after, "the retry must not duplicate a committed change")

        engine(b, url).syncOnce()
        repeat(5) { index -> assertNotNull(b.notes.byId(NoteId("retry-$index"))) }
        val pushCount = changes.pull(account.id, 0, 1_000).changes
            .count { it.entity == "note" && it.entityId.startsWith("retry-") }
        assertEquals(5, pushCount)
    }

    @Test
    fun theServerNeverMergesOrDropsAnUnknownField() = onOneThread {
        val url = startServer()
        val a = store("fields-a")
        val transport = KtorSyncTransport(url, rawToken)
        val opaque: ChangeRecord = ChangeRecord(
            entity = "future_entity",
            entityId = "future-1",
            operation = app.openhealth.core.sync.ChangeOperation.UPSERT,
            fields = mapOf("known" to "1", "invented" to "kept"),
            idempotencyKey = "future-key",
            hlc = "0000000000000001-00000000",
            deviceId = a.sync.deviceId(),
            schemaVersion = 11,
            recordedAt = 1,
        )
        transport.push(PushRequest(a.sync.deviceId(), listOf(opaque)))
        val pulled = transport.pull(PullRequest(0, 10))
        val record = pulled.changes.single { it.idempotencyKey == "future-key" }
        assertEquals("kept", record.fields["invented"])
    }
}
