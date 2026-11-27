package app.openhealth.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openhealth.android.coach.CoachPresenter
import app.openhealth.android.data.AndroidSecretVault
import app.openhealth.android.mcp.McpRuntime
import app.openhealth.core.ai.CoachCategory
import app.openhealth.core.ai.CoachProviderConfig
import app.openhealth.core.ai.McpScope
import app.openhealth.core.ai.McpTool
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.preferences.HealthModule
import app.openhealth.core.seam.HttpRequest
import app.openhealth.core.seam.HttpResponse
import app.openhealth.core.seam.HttpTransport
import app.openhealth.core.security.Sha256
import app.openhealth.data.db.Exercise
import app.openhealth.data.db.Routine
import app.openhealth.data.db.Routine_exercise
import app.openhealth.ui.screens.coach.CoachProposalStatusUi
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.Socket
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B15 exit test on the phone/emulator. It proves the two things the phase exists for:
 * a proposal is never applied without approval and is reversible, and the local MCP
 * endpoint authenticates, scopes and validates a write exactly as the UI does.
 */
@RunWith(AndroidJUnit4::class)
class CoachExitTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var container: AppContainer
    private lateinit var coach: CoachPresenter
    private var runtime: McpRuntime? = null

    private val answer = """
        {"note":"One change.","proposals":[{"summary":"Add a set",
        "rationale":"All planned sets were completed.","actions":[
        {"kind":"update_target","routine_id":"push","entry_id":"entry","sets":4}]}]}
    """.trimIndent()

    private class FakeTransport(private val text: String) : HttpTransport {
        override suspend fun get(request: HttpRequest): HttpResponse = HttpResponse(200, "{}")

        override suspend fun post(request: HttpRequest): HttpResponse {
            val body = buildJsonObject {
                putJsonArray("content") {
                    add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", text)
                        },
                    )
                }
                putJsonObject("usage") {
                    put("input_tokens", 10)
                    put("output_tokens", 3)
                }
            }
            return HttpResponse(200, body.toString())
        }
    }

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
        container = AppContainer(
            context,
            databaseName = TEST_DATABASE,
            vaultPreferences = TEST_VAULT,
        )
        val store = container.store
        store.training.upsertExercise(
            Exercise(
                id = "bench", name = "Bench press", primary_muscles = "chest",
                secondary_muscles = "triceps", equipment = "barbell", instructions = null,
                level = null, mechanic = null, force = null, met_activity = null,
                is_custom = false, source_id = null, created_at = 0, updated_at = 0,
                revision = 1,
            ),
        )
        store.training.upsertRoutine(
            Routine(
                id = "push", name = "Push", folder = null, notes = null, archived_at = null,
                created_at = 0, updated_at = 0, source_id = "manual", revision = 1,
                weekday = 1, rotation_index = null, favourite = false, progression = null,
                target_rest_seconds = null,
            ),
        )
        store.training.upsertRoutineExercise(
            Routine_exercise(
                id = "entry", routine_id = "push", exercise_id = "bench", position = 0,
                target_sets = 3, target_reps = 8, target_reps_high = 8,
                target_weight_kg = 80.0, notes = null, target_rir = 2.0,
                rest_seconds = null, progression = null, group_index = null,
            ),
        )
        val vault = AndroidSecretVault(context, TEST_VAULT)
        coach = CoachPresenter(
            context = context,
            store = store,
            vault = vault,
            preferences = container.preferences,
            data = container.coachData,
            applier = container.coachPlan,
            coach = store.coach,
            transport = FakeTransport(answer),
        )
        container.preferences.setCoachProvider(CoachProviderConfig())
        coach.setEnabled(true)
        coach.setCategory(CoachCategory.TRAINING, true)
        coach.setDisclosed()
        coach.saveKey("test-key-not-a-real-key")
        assertTrue(HealthModule.COACH in container.preferences.snapshot().modules)
    }

    @After
    fun tearDown() {
        runtime?.stop()
        context.deleteDatabase(TEST_DATABASE)
        context.getSharedPreferences(TEST_VAULT, android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun theCoachProposesAcceptsAndUndoes() = runBlocking {
        val sent = coach.send("Review my week.")
        assertNotNull(sent.messages.lastOrNull())
        val proposal = sent.messages.flatMap { it.proposals }.single()
        assertEquals(CoachProposalStatusUi.PROPOSED, proposal.status)
        assertTrue(proposal.rows.isNotEmpty())
        // Nothing was applied by asking.
        assertEquals(3L, container.store.training.routineEntryById("entry")!!.target_sets)

        assertEquals(null, coach.accept(proposal.id))
        assertEquals(4L, container.store.training.routineEntryById("entry")!!.target_sets)
        val accepted = coach.model().messages.flatMap { it.proposals }.single()
        assertEquals(CoachProposalStatusUi.ACCEPTED, accepted.status)
        assertTrue(accepted.undoable)

        assertEquals(null, coach.undo(proposal.id))
        assertEquals(3L, container.store.training.routineEntryById("entry")!!.target_sets)
        val undone = coach.model().messages.flatMap { it.proposals }.single()
        assertEquals(CoachProposalStatusUi.REVERTED, undone.status)
    }

    @Test
    fun anOutageLeavesTheNoteAndAppliesNothing() = runBlocking {
        val failing = CoachPresenter(
            context = context,
            store = container.store,
            vault = AndroidSecretVault(context, TEST_VAULT),
            preferences = container.preferences,
            data = container.coachData,
            applier = container.coachPlan,
            coach = container.store.coach,
            transport = object : HttpTransport {
                override suspend fun get(request: HttpRequest): HttpResponse =
                    HttpResponse(200, "{}")

                override suspend fun post(request: HttpRequest): HttpResponse =
                    throw IllegalStateException("offline")
            },
        )
        val model = failing.send("Plan my week.")
        assertTrue(model.messages.any { it.role.name == "PERSON" })
        assertTrue(model.messages.last().role.name == "ERROR")
        assertEquals(3L, container.store.training.routineEntryById("entry")!!.target_sets)
    }

    @Test
    fun theMcpEndpointNeedsATokenScopesAndAValidValue() {
        val store = container.store
        val plain = "ohm_test_token_$TEST_DATABASE"
        store.mcp.issue(
            label = "Laptop",
            scopes = setOf(McpScope.READ_TRAINING, McpScope.WRITE_BODY),
            tokenHash = Sha256.hexDigest(plain),
        )
        // The screen's minter stores only a digest; the plain value is shown once.
        val (minted, mintError) = container.mcp.create(
            "Minted on this device",
            setOf(McpScope.READ_DAY),
        )
        assertNull(mintError)
        assertNotNull(minted)
        val mintedRecord = store.mcp.tokens().first { it.label == "Minted on this device" }
        assertEquals(mintedRecord.id, store.mcp.byHash(Sha256.hexDigest(minted!!))!!.id)
        assertNull(store.mcp.byHash(minted))

        runtime = McpRuntime(store, container.mcpGateway)
        assertTrue(runtime!!.start(PORT))

        assertEquals(401, post(port = PORT, token = null, body = initialize()).first)
        val init = post(port = PORT, token = plain, body = initialize())
        assertEquals(200, init.first)
        assertTrue(init.second.contains("openhealth"))

        val list = post(
            PORT,
            plain,
            """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""",
        )
        assertTrue(list.second.contains(McpTool.GET_TRAINING_PLAN.wireName))
        assertTrue(list.second.contains(McpTool.LOG_WEIGHT.wireName))
        assertFalse(
            "a write:nutrition tool needs that scope",
            list.second.contains(McpTool.LOG_WATER.wireName),
        )

        val refused = post(
            PORT,
            plain,
            """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":
               {"name":"openhealth_log_water","arguments":{"millilitres":500}}}""",
        )
        assertTrue(refused.second.contains("Insufficient scope"))

        val invalid = post(
            PORT,
            plain,
            """{"jsonrpc":"2.0","id":4,"method":"tools/call","params":
               {"name":"openhealth_log_weight","arguments":{"weight_kg":900}}}""",
        )
        assertTrue(invalid.second.contains("out_of_range"))

        val written = post(
            PORT,
            plain,
            """{"jsonrpc":"2.0","id":5,"method":"tools/call","params":
               {"name":"openhealth_log_weight","arguments":{"weight_kg":80}}}""",
        )
        assertTrue(written.second.contains("logged"))
        assertEquals(
            80.0,
            container.store.observations.latest(ObservationType.WEIGHT)!!.value.value,
            0.001,
        )

        val plan = post(
            PORT,
            plain,
            """{"jsonrpc":"2.0","id":6,"method":"tools/call","params":
               {"name":"openhealth_get_training_plan","arguments":{}}}""",
        )
        assertEquals(200, plan.first)
        assertTrue(plan.second.contains("push"))

        store.mcp.tokens().forEach { assertTrue(store.mcp.revoke(it.id)) }
        assertEquals(401, post(PORT, plain, initialize()).first)
    }

    private fun initialize() = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}"""

    private fun post(port: Int, token: String?, body: String): Pair<Int, String> {
        Socket(InetAddress.getByName("127.0.0.1"), port).use { socket ->
            socket.soTimeout = 5_000
            val bytes = body.toByteArray(Charsets.UTF_8)
            val head = buildString {
                append("POST /mcp HTTP/1.1\r\n")
                append("Host: 127.0.0.1\r\n")
                token?.let { append("Authorization: Bearer ").append(it).append("\r\n") }
                append("Content-Type: application/json\r\n")
                append("Content-Length: ").append(bytes.size).append("\r\n")
                append("Connection: close\r\n\r\n")
            }
            socket.getOutputStream().apply {
                write(head.toByteArray(Charsets.ISO_8859_1))
                write(bytes)
                flush()
            }
            val text = BufferedInputStream(socket.getInputStream())
                .readBytes().toString(Charsets.UTF_8)
            val statusLine = text.substringBefore("\r\n")
            val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
            val responseBody = text.substringAfter("\r\n\r\n", "")
            return status to responseBody
        }
    }

    private companion object {
        const val TEST_DATABASE = "coach-exit.db"
        const val TEST_VAULT = "openhealth.vault.b15-test"
        const val PORT = 8791
    }
}
