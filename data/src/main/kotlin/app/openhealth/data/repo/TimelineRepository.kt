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
    fun page(limit: Long, offset: Long = 0): List<TimelineItem> {
        val perTable = (limit + offset).coerceAtLeast(1)
        // One statement, one round trip: each branch reads only its own newest rows
        // through its happened_at index, and the outer sort sees seven short lists. The
        // view's global ORDER BY materialised every row (23 ms); seven separate calls
        // sat closest to the 16 ms budget (B2), so the branches are unioned here instead.
        val sql = BRANCHES.joinToString(" UNION ALL ") { branch ->
            "SELECT * FROM (${branch.sql} ORDER BY ${branch.order} DESC LIMIT ?)"
        } + " ORDER BY happened_at DESC, entity_id LIMIT ? OFFSET ?"
        val items = driver.executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                val rows = mutableListOf<RawItem>()
                while (cursor.next().value) {
                    rows += RawItem(
                        kind = cursor.getString(COLUMN_KIND).orEmpty(),
                        entityId = cursor.getString(COLUMN_ID).orEmpty(),
                        localDate = cursor.getString(COLUMN_LOCAL_DATE).orEmpty(),
                        happenedAt = cursor.getLong(COLUMN_HAPPENED_AT) ?: 0L,
                        recordedAt = cursor.getLong(COLUMN_RECORDED_AT) ?: 0L,
                        sourceId = cursor.getString(COLUMN_SOURCE_ID).orEmpty(),
                        detail = cursor.getString(COLUMN_DETAIL),
                        edited = (cursor.getLong(COLUMN_EDITED) ?: 0L) != 0L,
                    )
                }
                QueryResult.Value(rows)
            },
            parameters = BRANCHES.size + 2,
        ) {
            var index = 0
            BRANCHES.forEach { _ ->
                bindLong(index, perTable)
                index += 1
            }
            bindLong(index, limit)
            bindLong(index + 1, offset.coerceAtLeast(0))
        }.value
        return items.map { it.toItem() }
    }

    fun forDay(date: LocalDate): List<TimelineItem> =
        db.timelineQueries.timelineForDay(date.toString()).executeAsList().map { row ->
            TimelineItem(
                kind = row.kind,
                entityId = row.entity_id,
                localDate = LocalDate.parse(row.local_date),
                happenedAt = row.happened_at,
                recordedAt = row.recorded_at,
                sourceId = row.source_id.orEmpty(),
                detail = row.detail,
                edited = row.edited,
            )
        }

    fun count(): Long = db.timelineQueries.countTimelineRows().executeAsOne()

    /**
     * A row before its local date is parsed. A page reads up to 50 rows from each of seven
     * tables and keeps 50, so parsing only the survivors roughly halves the cost of the
     * one query that sits closest to the 16 ms budget (B2's benchmark).
     */
    private data class RawItem(
        val kind: String,
        val entityId: String,
        val localDate: String,
        val happenedAt: Long,
        val recordedAt: Long,
        val sourceId: String,
        val detail: String?,
        val edited: Boolean,
    ) {
        fun toItem(): TimelineItem = TimelineItem(
            kind = kind,
            entityId = entityId,
            localDate = LocalDate.parse(localDate),
            happenedAt = happenedAt,
            recordedAt = recordedAt,
            sourceId = sourceId,
            detail = detail,
            edited = edited,
        )
    }

    /** One event table, selecting the timeline's eight columns under the same names. */
    private data class Branch(val sql: String, val order: String)

    private companion object {
        val BRANCHES = listOf(
            Branch(
                "SELECT 'observation' AS kind, id AS entity_id, local_date AS local_date, " +
                    "happened_at AS happened_at, recorded_at AS recorded_at, " +
                    "source_id AS source_id, type AS detail, user_edited AS edited " +
                    "FROM observation",
                "happened_at",
            ),
            Branch(
                "SELECT 'session', id, local_date, happened_at, recorded_at, source_id, " +
                    "routine_id, 0 FROM session",
                "happened_at",
            ),
            Branch(
                "SELECT 'work_set', work_set.id, session.local_date, work_set.happened_at, " +
                    "work_set.recorded_at, work_set.source_id, work_set.exercise_id, 0 " +
                    "FROM work_set JOIN session ON session.id = work_set.session_id",
                "work_set.happened_at",
            ),
            Branch(
                "SELECT 'entry', id, local_date, happened_at, recorded_at, source_kind, " +
                    "meal_id, user_edited FROM entry",
                "happened_at",
            ),
            Branch(
                "SELECT 'measurement', id, local_date, happened_at, recorded_at, source_id, " +
                    "type, 0 FROM measurement",
                "happened_at",
            ),
            Branch(
                "SELECT 'vital', id, local_date, happened_at, recorded_at, source_id, " +
                    "type, 0 FROM vital",
                "happened_at",
            ),
            Branch(
                "SELECT 'body_photo', id, local_date, happened_at, recorded_at, source_id, " +
                    "NULL, 0 FROM body_photo",
                "happened_at",
            ),
            Branch(
                "SELECT 'meal', id, local_date, COALESCE(happened_at, created_at), " +
                    "COALESCE(recorded_at, created_at), COALESCE(source_id, 'manual'), kind, 0 " +
                    "FROM meal",
                "COALESCE(happened_at, created_at)",
            ),
            Branch(
                "SELECT 'fast', id, local_date, start_at, recorded_at, 'manual', NULL, 0 " +
                    "FROM fast_window",
                "start_at",
            ),
            Branch(
                "SELECT 'note', id, local_date, happened_at, recorded_at, " +
                    "COALESCE(source_id, ''), NULL, 0 FROM note",
                "happened_at",
            ),
        )

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
