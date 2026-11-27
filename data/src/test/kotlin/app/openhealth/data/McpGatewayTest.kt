package app.openhealth.data

import app.openhealth.core.ai.McpScope
import app.openhealth.core.ai.McpToken
import app.openhealth.core.ai.McpTool
import app.openhealth.core.ai.McpToolResult
import app.openhealth.core.ids.SeededIdGenerator
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.MeasurementMethod
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.SourceKind
import app.openhealth.core.rollup.DailyRollup
import app.openhealth.core.seam.FixedClock
import app.openhealth.core.source.Source
import app.openhealth.core.units.Measure
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
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
        assertEquals(MeasurementMethod.OTHER, observation.provenance.method)
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
    fun twoWaterCallsSumRatherThanReplaceTheDay() {
        val (store, _, token) = setup()
        val gateway = McpGateway(store.db, store, TEST_ZONE, clock, SeededIdGenerator(60))
        repeat(2) {
            val result = gateway.call(
                McpTool.LOG_WATER,
                token,
                buildJsonObject { put("millilitres", 500) },
            )
            assertTrue(result is McpToolResult.Ok)
        }
        val today = now.toLocalDateTime(TEST_ZONE).date
        assertEquals(
            1_000.0,
            store.rollups.forDayAndType(today, ObservationType.WATER)!!.value.value,
            0.001,
        )
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
    fun zeroWaterIsRejectedLikeTheManualPath() {
        val (store, _, token) = setup()
        val gateway = McpGateway(store.db, store, TEST_ZONE, clock, SeededIdGenerator(30))
        val result = gateway.call(
            McpTool.LOG_WATER,
            token,
            buildJsonObject { put("millilitres", 0) },
        )
        assertEquals(McpToolResult.Error("out_of_range"), result)
        assertNull(store.observations.latest(ObservationType.WATER))
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
    fun weightNeutralModeHidesTheLoadsFromTheReadTools() {
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
        val gateway = McpGateway(
            store.db,
            store,
            TEST_ZONE,
            clock,
            SeededIdGenerator(40),
            weightNeutral = { true },
        )
        val plan = gateway.call(McpTool.GET_TRAINING_PLAN, token) as McpToolResult.Ok
        assertTrue(!plan.json.contains("target_weight_kg"))
        assertTrue(plan.json.contains("target_sets"))
        store.close()
    }

    /**
     * Weight-neutral mode hides calories and load-derived volume on every other surface
     * (`pr.md` §6.25); the day summary is not an exception. The sibling assertions in
     * [weightNeutralModeHidesTheLoadsFromTheReadTools] cover the plan.
     */
    @Test
    fun weightNeutralModeHidesEnergyAndVolumeFromTheDaySummary() {
        val (store, _, token) = setup()
        val today = now.toLocalDateTime(TEST_ZONE).date
        store.sources.upsert(
            Source(
                id = SourceId("phone"),
                kind = SourceKind.DEVICE,
                label = "Phone",
                family = "phone",
            ),
        )
        // The session first: writing training rows rebuilds the day, which would drop a
        // rollup seeded before it.
        seedFinishedSession(store, today)
        store.rollups.upsert(
            DailyRollup(
                localDate = today,
                type = ObservationType.ACTIVE_CALORIES,
                value = Measure(430.0, ObservationType.ACTIVE_CALORIES.canonicalUnit),
                sourceId = SourceId("phone"),
                watermark = now,
                computedAt = now,
            ),
        )

        val plain = McpGateway(store.db, store, TEST_ZONE, clock, SeededIdGenerator(60))
        val shown = json((plain.call(McpTool.GET_DAY_SUMMARY, token) as McpToolResult.Ok).json)
        assertEquals(430.0, (shown.getValue("active_kcal") as JsonPrimitive).content.toDouble())
        assertEquals(
            1_000.0,
            (shown.getValue("training_volume_kg") as JsonPrimitive).content.toDouble(),
        )

        val neutral = McpGateway(
            store.db,
            store,
            TEST_ZONE,
            clock,
            SeededIdGenerator(61),
            weightNeutral = { true },
        )
        val hidden = json((neutral.call(McpTool.GET_DAY_SUMMARY, token) as McpToolResult.Ok).json)
        assertNull(hidden["active_kcal"], "weight-neutral mode hides energy")
        assertNull(hidden["training_volume_kg"], "weight-neutral mode hides load volume")
        // The values that are not weight-derived still answer.
        assertNotNull(hidden["training_sets"])
        assertTrue(hidden.getValue("date").toString().contains(today.toString()))

        val sessions = neutral.call(McpTool.LIST_RECENT_SESSIONS, token) as McpToolResult.Ok
        assertTrue(!sessions.json.contains("volume_kg"))
        store.close()
    }

    /** One finished session with one 100 kg × 10 working set (1000 kg) and one warm-up. */
    private fun seedFinishedSession(store: LocalStore, day: LocalDate) {
        val start = now.toEpochMilliseconds()
        store.training.upsertExercise(
            app.openhealth.data.db.Exercise(
                id = "squat", name = "Squat", primary_muscles = "quadriceps",
                secondary_muscles = "", equipment = "barbell", instructions = null,
                level = null, mechanic = null, force = null, met_activity = null,
                is_custom = false, source_id = null, created_at = 0, updated_at = 0,
                revision = 1,
            ),
        )
        store.training.upsertSession(
            app.openhealth.data.db.Session(
                id = "s1", started_at = start, ended_at = start + 1, local_date = day.toString(),
                zone_id = TEST_ZONE.id, happened_at = start, recorded_at = start,
                routine_id = null, programme_id = null, notes = null, source_id = "manual",
                idempotency_key = "s1-key", observation_id = null, raw_record_id = null,
                device = "test", app_version = "test", revision = 1L, updated_at = start,
                title = "Legs", feeling = null, perceived_effort = 7.0,
            ),
        )
        store.training.upsertSessionExercise(
            app.openhealth.data.db.Session_exercise(
                id = "se1", session_id = "s1", exercise_id = "squat", position = 0L,
                notes = null, group_index = null, target_sets = 2L, target_reps = 10L,
                target_reps_high = 10L, target_weight_kg = null, progression = null,
                rest_seconds = null,
            ),
        )
        listOf(true to 20.0, false to 100.0).forEachIndexed { index, (warmup, weight) ->
            store.training.upsertSet(
                app.openhealth.data.db.Work_set(
                    id = "set$index", session_id = "s1", session_exercise_id = "se1",
                    exercise_id = "squat", position = index.toLong(), weight_kg = weight,
                    reps = 10L, rir = null, rpe = null, is_warmup = warmup,
                    is_backoff = false, is_failure = false, is_bodyweight = false,
                    tempo = null, rest_seconds = null, notes = null, completed_at = start,
                    happened_at = start, recorded_at = start, source_id = "manual",
                    idempotency_key = "set$index-key", observation_id = null,
                    raw_record_id = null, device = "test", app_version = "test", revision = 1L,
                    is_drop = false, is_assisted = false, assistance_kg = null, band_kg = null,
                    original_weight_kg = null, original_reps = null, edit_reason = null,
                    edited_at = null, user_edited = false,
                ),
            )
        }
    }

    @Test
    fun aHealthConnectRollupIsNotExposedToAnAssistant() {
        val (store, _, token) = setup()
        store.sources.upsert(
            Source(
                id = SourceId("garmin-hc"),
                kind = SourceKind.GARMIN,
                label = "Garmin via Health Connect",
                family = "garmin",
                viaHealthConnect = true,
            ),
        )
        val today = now.toLocalDateTime(TEST_ZONE).date
        store.rollups.upsert(
            DailyRollup(
                localDate = today,
                type = ObservationType.STEPS,
                value = Measure(9_000.0, ObservationType.STEPS.canonicalUnit),
                sourceId = SourceId("garmin-hc"),
                watermark = now,
                computedAt = now,
            ),
        )
        val gateway = McpGateway(store.db, store, TEST_ZONE, clock, SeededIdGenerator(41))
        val day = gateway.call(McpTool.GET_DAY_SUMMARY, token) as McpToolResult.Ok
        assertTrue((json(day.json)["steps"] as? JsonNull) != null)
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
