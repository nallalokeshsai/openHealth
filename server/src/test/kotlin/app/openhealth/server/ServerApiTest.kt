package app.openhealth.server

import app.openhealth.core.sync.ChangeOperation
import app.openhealth.core.sync.ChangeRecord
import app.openhealth.core.sync.PairingRequest
import app.openhealth.core.sync.PullRequest
import app.openhealth.core.sync.PushRequest
import app.openhealth.core.sync.ServerHealth
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerApiTest {
    private val config = ServerConfig(
        allowedUsers = setOf("owner"),
        allowPassword = true,
        dbUrl = null,
    )

    private fun change(
        key: String,
        value: String,
        entity: String = "note",
        id: String = "note-1",
        schemaVersion: Int = 11,
    ): ChangeRecord = ChangeRecord(
        entity = entity,
        entityId = id,
        operation = ChangeOperation.UPSERT,
        fields = mapOf("body" to value, "unknown_field" to "kept"),
        fieldClocks = mapOf("body" to "0000000000000001-00000000"),
        idempotencyKey = key,
        hlc = "0000000000000001-00000000",
        deviceId = "phone-a",
        schemaVersion = schemaVersion,
        localDate = "2026-11-26",
        recordedAt = 1L,
    )

    private class Fixture(
        val authStore: InMemoryAuthStore,
        val changes: InMemoryChangeStore,
        val account: Account,
        val token: String,
        val auth: AuthService,
    )

    private fun ApplicationTestBuilder.fixture(): Fixture {
        val authStore = InMemoryAuthStore()
        val account = authStore.createAccount(
            Account("11111111-1111-1111-1111-111111111111", "owner", "Owner", createdAt = 0),
        )
        val raw = "test-token-0123456789"
        authStore.createToken(
            StoredToken(
                id = "22222222-2222-2222-2222-222222222222",
                accountId = account.id,
                tokenHash = Tokens.hash(raw),
                scopes = SCOPE_SYNC,
                label = "test",
                deviceId = "phone-a",
                createdAt = 0,
                expiresAt = null,
            ),
        )
        val auth = AuthService(config, authStore) { 1_000L }
        val changes = InMemoryChangeStore()
        application { openHealthModule(config, auth, changes) }
        return Fixture(authStore, changes, account, raw, auth)
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json() }
    }

    @Test
    fun healthIsPublicAndNamesTheSchemaWindow() = testApplication {
        val fixture = fixture()
        val health = jsonClient().get("/health").body<ServerHealth>()
        assertEquals("openhealth-sync", health.service)
        assertEquals(12, health.schemaVersion)
        assertEquals(fixture.account.id, fixture.auth.validate(fixture.token, SCOPE_SYNC_READ)?.id)
    }

    @Test
    fun aPushIsIdempotentAndAPullReturnsUnknownFieldsVerbatim() = testApplication {
        val fixture = fixture()
        val client = jsonClient()
        val first = client.post("/sync/push") {
            bearerAuth(fixture.token)
            contentType(ContentType.Application.Json)
            setBody(PushRequest("phone-a", listOf(change("k1", "one"))))
        }.body<app.openhealth.core.sync.PushResponse>()
        assertEquals(listOf("k1"), first.acknowledged)

        val retry = client.post("/sync/push") {
            bearerAuth(fixture.token)
            contentType(ContentType.Application.Json)
            setBody(PushRequest("phone-a", listOf(change("k1", "one"))))
        }.body<app.openhealth.core.sync.PushResponse>()
        assertEquals(listOf("k1"), retry.acknowledged)
        assertEquals(1, fixture.changes.pull(fixture.account.id, 0, 10).changes.size)

        val pull = client.post("/sync/pull") {
            bearerAuth(fixture.token)
            contentType(ContentType.Application.Json)
            setBody(PullRequest(0, 10))
        }.body<app.openhealth.core.sync.PullResponse>()
        val record = pull.changes.single()
        assertEquals("kept", record.fields["unknown_field"])
        assertEquals("one", record.fields["body"])
        assertEquals(1L, pull.nextCursor)
    }

    @Test
    fun aSchemaOutsideTheWindowIsRejectedNotSilentlyDropped() = testApplication {
        val fixture = fixture()
        val response = jsonClient().post("/sync/push") {
            bearerAuth(fixture.token)
            contentType(ContentType.Application.Json)
            setBody(PushRequest("phone-a", listOf(change("k-new", "x", schemaVersion = 99))))
        }.body<app.openhealth.core.sync.PushResponse>()
        assertTrue(response.rejected.isNotEmpty())
        assertEquals(0, fixture.changes.pull(fixture.account.id, 0, 10).changes.size)
    }

    @Test
    fun aReadOnlyTokenCannotPush() = testApplication {
        val fixture = fixture()
        val readOnly = "read-only-token"
        fixture.authStore.createToken(
            StoredToken(
                id = "33333333-3333-3333-3333-333333333333",
                accountId = fixture.account.id,
                tokenHash = Tokens.hash(readOnly),
                scopes = setOf(SCOPE_SYNC_READ),
                label = null,
                deviceId = "phone-b",
                createdAt = 0,
                expiresAt = null,
            ),
        )
        val response = jsonClient().post("/sync/push") {
            bearerAuth(readOnly)
            contentType(ContentType.Application.Json)
            setBody(PushRequest("phone-b", listOf(change("k2", "two"))))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun accountsCannotSeeEachOthersChanges() = testApplication {
        val fixture = fixture()
        val other = fixture.authStore.createAccount(
            Account("44444444-4444-4444-4444-444444444444", "partner", "Partner", createdAt = 0),
        )
        val otherToken = "partner-token"
        fixture.authStore.createToken(
            StoredToken(
                id = "55555555-5555-5555-5555-555555555555",
                accountId = other.id,
                tokenHash = Tokens.hash(otherToken),
                scopes = SCOPE_SYNC,
                label = null,
                deviceId = "phone-c",
                createdAt = 0,
                expiresAt = null,
            ),
        )
        val client = jsonClient()
        client.post("/sync/push") {
            bearerAuth(fixture.token)
            contentType(ContentType.Application.Json)
            setBody(PushRequest("phone-a", listOf(change("mine", "mine"))))
        }
        val theirs = client.post("/sync/pull") {
            bearerAuth(otherToken)
            contentType(ContentType.Application.Json)
            setBody(PullRequest(0, 10))
        }.body<app.openhealth.core.sync.PullResponse>()
        assertTrue(theirs.changes.isEmpty())
    }

    @Test
    fun thePairingCodeIsSingleUseAndMintsAScopedToken() {
        val authStore = InMemoryAuthStore()
        val account = authStore.createAccount(
            Account("11111111-1111-1111-1111-111111111111", "owner", "Owner", createdAt = 0),
        )
        val auth = AuthService(config, authStore) { 1_000L }
        val code = auth.createPairingCode(account)
        val paired = auth.completePairing(code.code, "Pixel")
        assertEquals(SCOPE_SYNC.toList(), paired.scopes)
        assertNotNull(auth.validate(paired.token, SCOPE_SYNC_WRITE))
        assertNull(auth.validate(paired.token, SCOPE_SESSION))
        assertTrue(runCatching { auth.completePairing(code.code, "Pixel") }.isFailure)
    }

    @Test
    fun anExpiredPairingCodeIsRefused() {
        val authStore = InMemoryAuthStore()
        val account = authStore.createAccount(
            Account("11111111-1111-1111-1111-111111111111", "owner", "Owner", createdAt = 0),
        )
        var clock = 0L
        val auth = AuthService(config, authStore) { clock }
        val code = auth.createPairingCode(account)
        clock = code.expiresAt + 1
        assertTrue(runCatching { auth.completePairing(code.code, "Pixel") }.isFailure)
    }

    @Test
    fun onlyTheAllowlistedUserMayRegisterAPasskey() {
        val authStore = InMemoryAuthStore()
        val auth = AuthService(config, authStore) { 0L }
        assertNotNull(auth.registrationOptions("owner", "Owner"))
        assertNull(auth.registrationOptions("stranger", "Stranger"))
        val options = auth.registrationOptions("owner", "Owner")!!
        assertTrue(options.options.toString().contains("challenge"))
        assertTrue(options.options.toString().contains("pubKeyCredParams"))
    }

    @Test
    fun thePasswordFallbackIsOffUnlessEnabledAndRequiresTwelveCharacters() {
        val off = AuthService(config.copy(allowPassword = false), InMemoryAuthStore()) { 0L }
        assertTrue(
            runCatching {
                off.passwordRegister(PasswordBody("owner", "a-long-password"))
            }.isFailure,
        )

        val on = AuthService(config, InMemoryAuthStore()) { 0L }
        assertTrue(runCatching { on.passwordRegister(PasswordBody("owner", "short")) }.isFailure)
        val result = on.passwordRegister(PasswordBody("owner", "a-long-password"))
        assertNotNull(on.validate(result.token, SCOPE_SESSION))
        assertTrue(
            runCatching {
                on.passwordLogin(PasswordBody("owner", "wrong-password"))
            }.isFailure,
        )
        assertNotNull(on.passwordLogin(PasswordBody("owner", "a-long-password")))
    }

    @Test
    fun aRevokedTokenStopsWorkingImmediately() {
        val authStore = InMemoryAuthStore()
        val account = authStore.createAccount(
            Account("11111111-1111-1111-1111-111111111111", "owner", "Owner", createdAt = 0),
        )
        val auth = AuthService(config, authStore) { 0L }
        val code = auth.createPairingCode(account)
        val paired = auth.completePairing(code.code, "Pixel")
        val token = auth.tokenFor(paired.token)!!
        assertTrue(auth.revoke(account, token.id))
        assertNull(auth.validate(paired.token, SCOPE_SYNC_READ))
    }

    @Test
    fun aPairRequestOverHttpUsesTheSameCodeExchange() = testApplication {
        val fixture = fixture()
        val code = fixture.auth.createPairingCode(fixture.account)
        val paired = jsonClient().post("/pair/complete") {
            contentType(ContentType.Application.Json)
            setBody(PairingRequest(code.code, "Pixel"))
        }.body<app.openhealth.core.sync.PairingResponse>()
        assertNotNull(fixture.auth.validate(paired.token, SCOPE_SYNC_WRITE))

        val pushed = jsonClient().post("/sync/push") {
            bearerAuth(paired.token)
            contentType(ContentType.Application.Json)
            setBody(PushRequest(paired.deviceId, listOf(change("paired-key", "p"))))
        }
        assertEquals(HttpStatusCode.OK, pushed.status)
    }
}
