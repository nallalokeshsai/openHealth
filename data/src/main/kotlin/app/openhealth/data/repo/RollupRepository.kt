package app.openhealth.data.repo

import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.rollup.DailyRollup
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.db.Daily_rollup
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.mapping.enumValueOrNull
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

/** Layer 3 reads and writes. The only writer is the rollup engine. */
class RollupRepository(private val db: OpenHealthDb) {
    fun upsert(rollup: DailyRollup) {
        db.dailyRollupQueries.upsertRollup(
            localDate = rollup.localDate.toString(),
            type = rollup.type.name,
            value = rollup.value.value,
            unit = rollup.value.unit.ucum,
            secondaryValue = rollup.secondaryValue,
            sourceId = rollup.sourceId?.value,
            contributingSources = rollup.contributingSources.toLong(),
            dedupReason = rollup.dedupReason,
            watermark = rollup.watermark.toEpochMilliseconds(),
            computedAt = rollup.computedAt.toEpochMilliseconds(),
            detail = rollup.detail,
        )
    }

    fun forDay(date: LocalDate): List<DailyRollup> =
        db.dailyRollupQueries.rollupForDay(date.toString()).executeAsList().map(::toDomain)

    fun forDayAndType(date: LocalDate, type: ObservationType): DailyRollup? =
        db.dailyRollupQueries.rollupForDayAndType(date.toString(), type.name)
            .executeAsOneOrNull()
            ?.let(::toDomain)

    fun forRange(from: LocalDate, toInclusive: LocalDate): List<DailyRollup> =
        db.dailyRollupQueries.rollupsForRange(from.toString(), toInclusive.toString())
            .executeAsList()
            .map(::toDomain)

    fun forTypeRange(
        type: ObservationType,
        from: LocalDate,
        toInclusive: LocalDate,
    ): List<DailyRollup> = db.dailyRollupQueries.rollupsForTypeRange(
        type.name,
        from.toString(),
        toInclusive.toString(),
    )
        .executeAsList()
        .map(::toDomain)

    fun deleteDay(date: LocalDate) {
        db.dailyRollupQueries.deleteRollupsForDay(date.toString())
    }

    fun deleteAll() {
        db.dailyRollupQueries.deleteAllRollups()
    }

    fun count(): Long = db.dailyRollupQueries.countRollups().executeAsOne()

    fun days(): List<LocalDate> =
        db.dailyRollupQueries.rollupDays().executeAsList().map(LocalDate::parse)

    private fun toDomain(row: Daily_rollup): DailyRollup {
        val type = enumValueOrNull<ObservationType>(row.type)
            ?: error("Rollup ${row.local_date}/${row.type} has an unknown type")
        return DailyRollup(
            localDate = LocalDate.parse(row.local_date),
            type = type,
            value = Measure(row.value_, UcumUnit.byUcum(row.unit)),
            secondaryValue = row.secondary_value,
            sourceId = row.source_id?.let(::SourceId),
            contributingSources = row.contributing_sources.toInt(),
            dedupReason = row.dedup_reason,
            watermark = Instant.fromEpochMilliseconds(row.watermark),
            computedAt = Instant.fromEpochMilliseconds(row.computed_at),
            detail = row.detail,
        )
    }

    /** Detail is a JSON object so a later phase can add sleep stages without a migration. */
    fun encodeDetail(map: Map<String, String>): String = Json.encodeToString(map)
}
