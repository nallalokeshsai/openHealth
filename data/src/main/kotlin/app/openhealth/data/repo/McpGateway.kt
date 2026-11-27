package app.openhealth.data.repo

import app.openhealth.core.ai.McpScope
import app.openhealth.core.ai.McpToken
import app.openhealth.core.ai.McpTool
import app.openhealth.core.ai.McpToolHost
import app.openhealth.core.ai.McpToolResult
import app.openhealth.core.ids.IdGenerator
import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.RandomIdGenerator
import app.openhealth.core.ids.RawRecordId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.ingestion.RawRecord
import app.openhealth.core.observation.MeasurementMethod
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.SourceKind
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.SystemClock
import app.openhealth.core.security.Sha256
import app.openhealth.core.source.Source
import app.openhealth.core.source.SourcePermission
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import app.openhealth.core.validate.ObservationValidator
import app.openhealth.data.db.Note
import app.openhealth.data.db.OpenHealthDb
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * The local MCP tool surface (B15, `pr.md` §6.15 V1, §6.30). Every read labels the layer
 * it came from; every write runs the same plausibility check as the manual entry, carries
 * provenance under an `mcp:<token>` source, and lands in the normal repositories so the
 * day's rollups rebuild exactly as they do for a typed value.
 *
 * A host is bound to one token, so a write always names the token that made it and the
 * audit never has to guess.
 */
@Suppress("LongParameterList")
class McpGateway(
    @Suppress("UNUSED_PARAMETER") db: OpenHealthDb,
    private val store: app.openhealth.data.LocalStore,
    private val zone: TimeZone,
    private val clock: ClockSeam = SystemClock,
    private val ids: IdGenerator = RandomIdGenerator(),
    private val appVersion: String = "",
    /** Weight-neutral mode hides loads and volume here as on every other surface. */
    private val weightNeutral: () -> Boolean = { false },
) {
    fun host(token: McpToken): McpToolHost = BoundHost(token)

    private inner class BoundHost(private val token: McpToken) : McpToolHost {
        override fun call(tool: McpTool, arguments: JsonObject): McpToolResult {
            val outcome = runCatching { dispatch(tool, arguments, token) }
                .getOrElse { McpToolResult.Error("internal_error") }
            store.mcp.log(
                tokenId = token.id,
                tool = tool.wireName,
                scope = tool.scope.key,
                ok = outcome is McpToolResult.Ok,
                detail = (outcome as? McpToolResult.Error)?.code,
            )
            return outcome
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun dispatch(tool: McpTool, arguments: JsonObject, token: McpToken): McpToolResult =
        when (tool) {
            McpTool.GET_PROFILE -> McpToolResult.Ok(profile())
            McpTool.GET_TRAINING_PLAN -> McpToolResult.Ok(trainingPlan())
            McpTool.LIST_RECENT_SESSIONS -> McpToolResult.Ok(
                recentSessions(arguments.int("limit") ?: DEFAULT_SESSIONS),
            )

            McpTool.GET_DAY_SUMMARY -> {
                val date = arguments.string("date")
                val day = if (date == null) {
                    today()
                } else {
                    parseDate(date)
                        ?: return McpToolResult.Error("bad_date")
                }
                McpToolResult.Ok(daySummary(day))
            }

            McpTool.LOG_WEIGHT -> {
                val kg =
                    arguments.double("weight_kg") ?: return McpToolResult.Error("missing_value")
                val at =
                    happenedAt(arguments.string("date")) ?: return McpToolResult.Error("bad_date")
                val check = ObservationValidator.validateNumber(ObservationType.WEIGHT, kg)
                if (!check.isValid) return McpToolResult.Error("out_of_range")
                val id = writeObservation(ObservationType.WEIGHT, kg, UcumUnit.KILOGRAM, at, token)
                McpToolResult.Ok(buildJsonObject { put("logged", id.value) }.toString())
            }

            McpTool.LOG_WATER -> {
                val millilitres = arguments.double("millilitres")
                    ?: return McpToolResult.Error("missing_value")
                if (millilitres <= 0.0) return McpToolResult.Error("out_of_range")
                val at =
                    happenedAt(arguments.string("date")) ?: return McpToolResult.Error("bad_date")
                val check = ObservationValidator.validateNumber(ObservationType.WATER, millilitres)
                if (!check.isValid) return McpToolResult.Error("out_of_range")
                val id = writeObservation(
                    ObservationType.WATER,
                    millilitres,
                    UcumUnit.MILLILITRE,
                    at,
                    token,
                    end = at,
                )
                McpToolResult.Ok(buildJsonObject { put("logged", id.value) }.toString())
            }

            McpTool.ADD_NOTE -> {
                val text = arguments.string("text")
                    ?: return McpToolResult.Error("missing_text")
                if (text.isBlank() || text.length > MAX_NOTE) return McpToolResult.Error("bad_text")
                val id = writeNote(text, happenedAt(null) ?: clock.now(), token)
                McpToolResult.Ok(buildJsonObject { put("note", id) }.toString())
            }
        }

    private fun profile(): String {
        val profile = store.profile.active()
            ?: return buildJsonObject { put("profile", "none") }.toString()
        return buildJsonObject {
            put("sex_at_birth", profile.sex_at_birth)
            put("birth_year", profile.date_of_birth?.take(YEAR_CHARS)?.toIntOrNull())
            put("height_cm", profile.height_cm)
            put("units", profile.units)
            put("goal", profile.goal)
            put("training_experience", profile.training_experience)
        }.toString()
    }

    private fun trainingPlan(): String = buildJsonObject {
        putJsonArray("routines") {
            store.training.routines().filter { it.archived_at == null }.forEach { routine ->
                add(
                    buildJsonObject {
                        put("id", routine.id)
                        put("name", routine.name)
                        put("weekday", routine.weekday)
                        put("favourite", routine.favourite)
                        putJsonArray("entries") {
                            store.training.routineEntries(routine.id).forEach { entry ->
                                add(
                                    buildJsonObject {
                                        put("id", entry.id)
                                        put("exercise_id", entry.exercise_id)
                                        put(
                                            "exercise_name",
                                            store.training.exerciseById(entry.exercise_id)?.name,
                                        )
                                        put("position", entry.position)
                                        put("target_sets", entry.target_sets)
                                        put("target_reps", entry.target_reps)
                                        put("target_reps_high", entry.target_reps_high)
                                        if (!weightNeutral()) {
                                            put("target_weight_kg", entry.target_weight_kg)
                                        }
                                        put("target_rir", entry.target_rir)
                                        put("rest_seconds", entry.rest_seconds)
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
    }.toString()

    private fun recentSessions(limit: Int): String = buildJsonObject {
        putJsonArray("sessions") {
            store.training.finishedSessions(limit.coerceIn(1, MAX_SESSIONS).toLong())
                .forEach { session ->
                    val sets = store.training.setsForSession(session.id)
                    add(
                        buildJsonObject {
                            put("id", session.id)
                            put("date", session.local_date)
                            put(
                                "routine",
                                session.routine_id?.let { store.training.routineById(it)?.name },
                            )
                            put("sets", sets.size)
                            if (!weightNeutral()) {
                                put(
                                    "volume_kg",
                                    sets.filterNot { it.is_warmup }
                                        .sumOf { it.weight_kg * it.reps },
                                )
                            }
                            put("perceived_effort", session.perceived_effort)
                        },
                    )
                }
        }
    }.toString()

    private fun daySummary(date: LocalDate): String {
        val sources = store.sources.all()
        fun rollup(type: ObservationType): Double? {
            val row = store.rollups.forDayAndType(date, type) ?: return null
            // A Health Connect-derived rollup is not sent to an external assistant
            // without a dedicated opt-in (the MCP scopes are not that opt-in).
            val platform = row.sourceId?.let { id ->
                sources.firstOrNull { it.id == id }?.viaHealthConnect
            } ?: true
            return if (platform) null else row.value.value
        }
        val sessions = store.training.sessionsForDay(date)
        val volume = sessions.sumOf { session ->
            store.training.setsForSession(session.id)
                .filterNot { it.is_warmup }
                .sumOf { it.weight_kg * it.reps }
        }
        return buildJsonObject {
            put("date", date.toString())
            put("steps", rollup(ObservationType.STEPS))
            put("active_kcal", rollup(ObservationType.ACTIVE_CALORIES))
            put("water_ml", rollup(ObservationType.WATER))
            put(
                "sleep_hours",
                rollup(ObservationType.SLEEP_DURATION)?.div(SECONDS_PER_HOUR),
            )
            put("training_sets", rollup(ObservationType.TRAINING_SETS))
            put("training_volume_kg", if (volume > 0.0) volume else null)
        }.toString()
    }

    @Suppress("LongParameterList")
    private fun writeObservation(
        type: ObservationType,
        value: Double,
        unit: UcumUnit,
        at: Instant,
        token: McpToken,
        end: Instant? = null,
    ): ObservationId {
        val sourceId = sourceId(token)
        val now = clock.now()
        val key = "mcp:${token.id}:${ids.newId()}"
        val payload = buildJsonObject {
            put("type", type.name)
            put("value", value)
            put("unit", unit.ucum)
            put("happened_at", at.toString())
        }.toString()
        val raw = RawRecord(
            id = RawRecordId(ids.newId()),
            sourceId = sourceId,
            sourceKind = SourceKind.MCP,
            receivedAt = now,
            recordedAt = now,
            happenedAt = at,
            payloadHash = Sha256.hexDigest(payload),
            idempotencyKey = key,
            schemaVersion = RAW_SCHEMA,
            device = DEVICE,
            appVersion = appVersion,
            payload = payload,
        )
        val observation = Observation.of(
            id = ObservationId(ids.newId()),
            type = type,
            value = Measure(value, unit),
            start = at,
            provenance = app.openhealth.core.observation.Provenance(
                sourceId = sourceId,
                time = TimeProvenance.of(at, now, zone),
                idempotencyKey = key,
                device = DEVICE,
                appVersion = appVersion,
                method = MeasurementMethod.OTHER,
                rawRecordId = raw.id,
            ),
        ).copy(end = end)
        store.observations.record(observation, raw)
        return observation.id
    }

    private fun writeNote(text: String, at: Instant, token: McpToken): String {
        val now = clock.now()
        val id = ids.newId()
        val key = "mcp:${token.id}:$id"
        val raw = RawRecord(
            id = RawRecordId(ids.newId()),
            sourceId = sourceId(token),
            sourceKind = SourceKind.MCP,
            receivedAt = now,
            recordedAt = now,
            happenedAt = at,
            payloadHash = Sha256.hexDigest(text),
            idempotencyKey = "$key:raw",
            schemaVersion = RAW_SCHEMA,
            device = DEVICE,
            appVersion = appVersion,
            payload = text.take(MAX_RAW_PAYLOAD),
        )
        val source = sourceId(token)
        return store.db.transactionWithResult {
            store.raw.insert(raw)
            store.notes.insert(
                Note(
                    id = id,
                    body = text,
                    local_date = at.toLocalDateTime(zone).date.toString(),
                    zone_id = zone.id,
                    happened_at = at.toEpochMilliseconds(),
                    recorded_at = now.toEpochMilliseconds(),
                    edited_at = null,
                    source_id = source.value,
                    raw_record_id = raw.id.value,
                    idempotency_key = key,
                    device = DEVICE,
                    app_version = appVersion,
                    revision = 1,
                ),
            )
            id
        }
    }

    private fun sourceId(token: McpToken): SourceId {
        val id = SourceId("mcp:${token.id}")
        if (store.sources.byId(id) == null) {
            store.sources.upsert(
                Source(
                    id = id,
                    kind = SourceKind.MCP,
                    label = "Assistant · ${token.label}",
                    family = "mcp",
                    device = DEVICE,
                    permissions = buildSet {
                        if (McpScope.WRITE_BODY in token.scopes) {
                            add(SourcePermission.WRITE_WEIGHT)
                        }
                        if (McpScope.WRITE_NUTRITION in token.scopes) {
                            add(SourcePermission.WRITE_NUTRITION)
                        }
                    },
                ),
            )
        }
        return id
    }

    private fun today(): LocalDate = clock.now().toLocalDateTime(zone).date

    @Suppress("ReturnCount")
    private fun parseDate(value: String): LocalDate? {
        val date = runCatching { LocalDate.parse(value) }.getOrNull() ?: return null
        val today = today()
        if (date > today) return null
        if (date < today.minus(MAX_BACKFILL_DAYS, DateTimeUnit.DAY)) return null
        return date
    }

    @Suppress("ReturnCount")
    private fun happenedAt(date: String?): Instant? {
        if (date == null) return clock.now()
        val day = parseDate(date) ?: return null
        if (day == today()) return clock.now()
        return LocalDateTime(day.year, day.monthNumber, day.dayOfMonth, NOON, 0)
            .toInstant(zone)
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.double(name: String): Double? =
        (this[name] as? JsonPrimitive)?.doubleOrNull

    private companion object {
        const val DEFAULT_SESSIONS = 10
        const val MAX_SESSIONS = 30
        const val MAX_NOTE = 2_000
        const val MAX_RAW_PAYLOAD = 4_096
        const val MAX_BACKFILL_DAYS = 400L
        const val SECONDS_PER_HOUR = 3_600.0
        const val NOON = 12
        const val YEAR_CHARS = 4
        const val RAW_SCHEMA = 1
        const val DEVICE = "mcp"
    }
}
