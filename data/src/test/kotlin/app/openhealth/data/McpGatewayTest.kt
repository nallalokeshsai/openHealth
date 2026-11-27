package app.openhealth.data

import app.openhealth.core.ai.McpScope
import app.openhealth.core.ai.McpToken
import app.openhealth.core.ai.McpTool
import app.openhealth.core.ai.McpToolResult
import app.openhealth.core.ids.SeededIdGenerator
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.SourceKind
import app.openhealth.core.seam.FixedClock
import app.openhealth.data.LocalStore
import app.openhealth.data.repo.McpGateway
import app.openhealth.data.repo.McpRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The MCP writes run the same validation and provenance path as manual entry
 * (`pr.md` §6.30 acceptance).
 */
class McpGatewayTest {
    private val now = Instant.parse("2026-11-27T09:00:00Z")
    private val clock = FixedClock(now)

    private fun setup(): Triple<LocalStore, McpRepository, McpToken> {
        val opened = inMemoryStore()
        val store = LocalStore(opened.driver, opened.db)
        val mcp = McpRepository(store.db, clock, SeededIdGenerator(1))
        val issued = mcp.issue(
            label = "Laptop",
            scopes = setOf(
                McpScope.READ_PROFILE,
                McpScope.READ_TRAINING,
                McpScope.READ_DAY,
                McpScope.WRITE_BODY,
                McpScope.WRITE_NUTRITION,
                McpScope.WRITE_NOTES,
            ),
            tokenHash = "hash",
        )
        return Triple(store, mcp, issued.toProtocol())
    }

    private fun McpGateway.call(
        tool: McpTool,
        token: McpToken,
        args: JsonObject = buildJsonObject {
        },
    ): McpToolResult = host(token).call(tool, args)

    private fun json(text: String): JsonObject =
        Json.parseToJsonElement(text).let { it as JsonObject }

    @Test
    fun aWeightWriteCarriesProvenanceAndRebuildsTheDay() {
        val (store, mcp, token) = setup()
        val gateway = McpGateway(store.db, store, TEST_ZONE, clock, SeededIdGenerator(2))
        val result = gateway.call(
            McpTool.LOG_WEIGHT,
            token,
            buildJsonObject { put("weight_kg", 81.5) },
        )
        assertTrue(result is McpToolResult.Ok)
        val observation = assertNotNull(store.observations.latest(ObservationType.WEIGHT))
        assertEquals(81.5, observation.value.value)
        assertEquals(SourceKind.MCP, store.sources.byId(observation.provenance.sourceId)!!.kind)
        assertNotNull(observation.provenance.rawRecordId)
        assertEquals(LocalDate(2026, 11, 27), observation.localDate)
        assertEquals(now, observation.provenance.recordedAt)
        assertEquals(
            81.5,
            store.rollups.forDayAndType(
                LocalDate(2026, 11, 27),
                ObservationType.WEIGHT,
            )!!.value.value,
        )
        assertEquals(1, mcp.access().size)
        store.close()
    }

    @Test
    fun aBackfilledWeightKeepsItsOwnHappenedAtAndTheRecordingTime() {
        val (store, _, token) = setup()
        val gateway = McpGateway(store.db, store, TEST_ZONE, clock, SeededIdGenerator(3))
        val result = gateway.call(
            McpTool.LOG_WEIGHT,
            token,
            buildJsonObject {
                put("weight_kg", 80.0)
                put("date", "2026-11-20")
            },
        )
        assertTrue(result is McpToolResult.Ok)
        val observation = assertNotNull(store.observations.latest(ObservationType.WEIGHT))
        assertEquals(LocalDate(2026, 11, 20), observation.localDate)
        assertEquals(now, observation.provenance.recordedAt)
        assertTrue(observation.provenance.happenedAt < observation.provenance.recordedAt)
        store.close()
    }

    @Test
    fun anOutOfRangeValueIsRejectedAndNothingIsWritten() {
        val (store, _, token) = setup()
        val gateway = McpGateway(store.db, store, TEST_ZONE, clock, SeededIdGenerator(4))
        val result = gateway.call(
            McpTool.LOG_WEIGHT,
            token,
            buildJsonObject { put("weight_kg", 900) },
        )
        assertEquals(McpToolResult.Error("out_of_range"), result)
        assertNull(store.observations.latest(ObservationType.WEIGHT))
        assertTrue(store.mcp.access().single().ok.not())
        store.close()
    }

    @Test
    fun waterUsesTheCanonicalUnit() {
        val (store, _, token) = setup()
        val gateway = McpGateway(store.db, store, TEST_ZONE, clock, SeededIdGenerator(5))
        val result = gateway.call(
            McpTool.LOG_WATER,
            token,
            buildJsonObject { put("millilitres", 500) },
        )
        assertTrue(result is McpToolResult.Ok)
        val water = assertNotNull(store.observations.latest(ObservationType.WATER))
        assertEquals(500.0, water.value.value)
        assertEquals("mL", water.value.unit.ucum)
        store.close()
    }

    @Test
    fun aNoteIsStoredWithTheTokenSource() {
        val (store, _, token) = setup()
        val gateway = McpGateway(store.db, store, TEST_ZONE, clock, SeededIdGenerator(6))
        val result = gateway.call(
            McpTool.ADD_NOTE,
            token,
            buildJsonObject { put("text", "Felt strong today.") },
        )
        assertTrue(result is McpToolResult.Ok)
        val note = store.notes.latest(1L).single()
        assertEquals("Felt strong today.", note.body)
        assertEquals(
            SourceKind.MCP,
            store.sources.byId(
                app.openhealth.core.ids.SourceId(note.source_id!!),
            )!!.kind,
        )
        assertNotNull(note.raw_record_id)
        store.close()
    }

    @Test
    fun aFutureDateIsRejected() {
        val (store, _, token) = setup()
        val gateway = McpGateway(store.db, store, TEST_ZONE, clock, SeededIdGenerator(7))
        val result = gateway.call(
            McpTool.GET_DAY_SUMMARY,
            token,
            buildJsonObject { put("date", "2030-01-01") },
        )
        assertEquals(McpToolResult.Error("bad_date"), result)
        store.close()
    }

    @Test
    fun theReadsExposeThePlanAndTheDay() {
        val (store, _, token) = setup()
        store.training.upsertExercise(
            app.openhealth.data.db.Exercise(
                id = "squat", name = "Squat", primary_muscles = "quadriceps",
                secondary_muscles = "", equipment = "barbell", instructions = null,
                level = null, mechanic = null, force = null, met_activity = null,
                is_custom = false, source_id = null, created_at = 0, updated_at = 0,
                revision = 1,
            ),
        )
        store.training.upsertRoutine(
            app.openhealth.data.db.Routine(
                id = "legs", name = "Legs", folder = null, notes = null, archived_at = null,
                created_at = 0, updated_at = 0, source_id = "manual", revision = 1,
                weekday = 2, rotation_index = null, favourite = false, progression = null,
                target_rest_seconds = null,
            ),
        )
        store.training.upsertRoutineExercise(
            app.openhealth.data.db.Routine_exercise(
                id = "e1", routine_id = "legs", exercise_id = "squat", position = 0,
                target_sets = 5, target_reps = 5, target_reps_high = 5,
                target_weight_kg = 100.0, notes = null, target_rir = 2.0,
                rest_seconds = null, progression = null, group_index = null,
            ),
        )
        val gateway = McpGateway(store.db, store, TEST_ZONE, clock, SeededIdGenerator(8))
        val plan = gateway.call(McpTool.GET_TRAINING_PLAN, token) as McpToolResult.Ok
        val routines = json(plan.json).getValue("routines").let {
            it as kotlinx.serialization.json.JsonArray
        }
        assertEquals(1, routines.size)
        assertEquals(
            "Legs",
            json(routines.single().toString()).getValue("name")
                .let { it as kotlinx.serialization.json.JsonPrimitive }.content,
        )

        val day = gateway.call(McpTool.GET_DAY_SUMMARY, token) as McpToolResult.Ok
        assertTrue(day.json.contains("2026-11-27"))
        store.close()
    }

    @Test
    fun aRevokedTokenIsNoLongerUsable() {
        val (store, mcp, token) = setup()
        assertTrue(mcp.revoke(token.id))
        assertEquals(true, mcp.tokens().single().revoked)
        assertEquals(false, mcp.revoke(token.id))
        store.close()
    }
}
