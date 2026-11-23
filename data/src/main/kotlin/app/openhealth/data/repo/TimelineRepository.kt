package app.openhealth.data.repo

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.openhealth.data.db.OpenHealthDb
import kotlinx.datetime.LocalDate

/** One row of the timeline (`pr.md` §6.27). */
data class TimelineItem(
    val kind: String,
    val entityId: String,
    val localDate: LocalDate,
    val happenedAt: Long,
    val recordedAt: Long,
    val sourceId: String,
    val detail: String?,
    val edited: Boolean,
)

/**
 * The timeline reads each table newest-first through its own `happened_at` index, then
 * merges in memory. A UNION view sorts every branch to answer a global ORDER BY, which
 * is what made the page query take 23 ms on the five-year corpus; five indexed reads
 * and a 50-item merge stay far under the 16 ms budget.
 */
class TimelineRepository(private val db: OpenHealthDb, private val driver: SqlDriver) {
    fun page(limit: Long): List<TimelineItem> {
        val perTable = limit.coerceAtLeast(1)
        return (
            newest(
                "SELECT 'observation', id, local_date, happened_at, recorded_at, source_id, " +
                    "type, user_edited FROM observation ORDER BY happened_at DESC LIMIT ?",
                perTable,
            ) +
                newest(
                    "SELECT 'session', id, local_date, happened_at, recorded_at, source_id, " +
                        "routine_id, 0 FROM session ORDER BY happened_at DESC LIMIT ?",
                    perTable,
                ) +
                newest(
                    "SELECT 'entry', id, local_date, happened_at, recorded_at, source_kind, " +
                        "meal_id, user_edited FROM entry ORDER BY happened_at DESC LIMIT ?",
                    perTable,
                ) +
                newest(
                    "SELECT 'measurement', id, local_date, happened_at, recorded_at, source_id, " +
                        "type, 0 FROM measurement ORDER BY happened_at DESC LIMIT ?",
                    perTable,
                ) +
                newest(
                    "SELECT 'vital', id, local_date, happened_at, recorded_at, source_id, " +
                        "type, 0 FROM vital ORDER BY happened_at DESC LIMIT ?",
                    perTable,
                ) +
                newest(
                    "SELECT 'meal', id, local_date, created_at, created_at, 'manual', " +
                        "kind, 0 FROM meal ORDER BY created_at DESC LIMIT ?",
                    perTable,
                )
            )
            .sortedByDescending { it.happenedAt }
            .take(limit.toInt())
    }

    fun forDay(date: LocalDate): List<TimelineItem> =
        db.timelineQueries.timelineForDay(date.toString()).executeAsList().map { row ->
            TimelineItem(
                kind = row.kind,
                entityId = row.entity_id,
                localDate = LocalDate.parse(row.local_date),
                happenedAt = row.happened_at,
                recordedAt = row.recorded_at,
                sourceId = row.source_id,
                detail = row.detail,
                edited = row.edited,
            )
        }

    fun count(): Long = db.timelineQueries.countTimelineRows().executeAsOne()

    private fun newest(sql: String, limit: Long): List<TimelineItem> = driver.executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            val items = mutableListOf<TimelineItem>()
            while (cursor.next().value) {
                items += TimelineItem(
                    kind = cursor.getString(COLUMN_KIND).orEmpty(),
                    entityId = cursor.getString(COLUMN_ID).orEmpty(),
                    localDate = LocalDate.parse(cursor.getString(COLUMN_LOCAL_DATE).orEmpty()),
                    happenedAt = cursor.getLong(COLUMN_HAPPENED_AT) ?: 0L,
                    recordedAt = cursor.getLong(COLUMN_RECORDED_AT) ?: 0L,
                    sourceId = cursor.getString(COLUMN_SOURCE_ID).orEmpty(),
                    detail = cursor.getString(COLUMN_DETAIL),
                    edited = (cursor.getLong(COLUMN_EDITED) ?: 0L) != 0L,
                )
            }
            QueryResult.Value(items)
        },
        parameters = 1,
    ) {
        bindLong(0, limit)
    }.value

    private companion object {
        const val COLUMN_KIND = 0
        const val COLUMN_ID = 1
        const val COLUMN_LOCAL_DATE = 2
        const val COLUMN_HAPPENED_AT = 3
        const val COLUMN_RECORDED_AT = 4
        const val COLUMN_SOURCE_ID = 5
        const val COLUMN_DETAIL = 6
        const val COLUMN_EDITED = 7
    }
}
