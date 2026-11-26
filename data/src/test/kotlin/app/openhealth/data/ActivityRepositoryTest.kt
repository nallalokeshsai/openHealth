package app.openhealth.data

import app.openhealth.core.activity.ActivityPoint
import app.openhealth.core.activity.ActivityRecord
import app.openhealth.core.activity.ActivitySource
import app.openhealth.core.activity.ActivityType
import app.openhealth.core.ids.TombstoneId
import app.openhealth.data.repo.TimelineRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class ActivityRepositoryTest {
    private val zone = TimeZone.of("Europe/Berlin")
    private val start = Instant.parse("2026-11-24T08:00:00Z")

    private fun activity(id: String = "activity-1", points: List<ActivityPoint> = route()) =
        ActivityRecord(
            id = id,
            type = ActivityType.RUN,
            source = ActivitySource.RECORDED,
            startedAt = start,
            endedAt = start.plus(kotlin.time.Duration.parse("30m")),
            localDate = start.toLocalDateTime(zone).date,
            zoneId = zone.id,
            distanceM = 5_000.0,
            averageHeartRate = 150.0,
            maxHeartRate = 172.0,
            energyKcal = 350.0,
            name = "Lunch run",
            points = points,
        )

    private fun route(): List<ActivityPoint> = (0..10).map { index ->
        ActivityPoint(
            latitude = 52.5200 + index * 0.0004,
            longitude = 13.4050,
            elapsedSeconds = index * 180L,
            elevationM = 34.0 + index,
            heartRate = 140.0 + index,
        )
    }

    @Test
    fun anActivityAndItsRouteRoundTrip() {
        val store = inMemoryStore().let { LocalStore(it.driver, it.db) }
        val id = store.activities.record(activity(), "manual", "activity-1")
        assertEquals("activity-1", id)
        val loaded = store.activities.byIdWithPoints("activity-1")
        assertNotNull(loaded)
        assertEquals(ActivityType.RUN, loaded.type)
        assertEquals(ActivitySource.RECORDED, loaded.source)
        assertEquals(5_000.0, loaded.distanceM)
        assertEquals(11, loaded.points.size)
        assertEquals(34.0, loaded.points.first().elevationM!!, 0.001)
        assertEquals(180, loaded.points[1].elapsedSeconds)
        assertEquals(11L, store.activities.pointCount("activity-1"))
    }

    @Test
    fun theSameIdempotencyKeyIsInsertedOnce() {
        val store = inMemoryStore().let { LocalStore(it.driver, it.db) }
        assertNotNull(store.activities.record(activity(), "manual", "same-key"))
        assertNull(store.activities.record(activity(id = "activity-2"), "manual", "same-key"))
        assertEquals(1L, store.activities.count())
    }

    @Test
    fun activitiesAreReadByDayAndByRange() {
        val store = inMemoryStore().let { LocalStore(it.driver, it.db) }
        store.activities.record(activity(), "manual", "a1")
        val day = LocalDate(2026, 11, 24)
        assertEquals(1, store.activities.forDay(day).size)
        assertEquals(1, store.activities.between(day, day).size)
        assertEquals(0, store.activities.between(day.plusDays(1), day.plusDays(2)).size)
        assertEquals(1, store.activities.recent().size)
    }

    @Test
    fun deletingAnActivityLeavesATombstoneAndUndoRestoresTheRoute() {
        val store = inMemoryStore().let { LocalStore(it.driver, it.db) }
        store.activities.record(activity(), "manual", "activity-1")
        val tombstone: TombstoneId? = store.trash.deleteActivity("activity-1")
        assertNotNull(tombstone)
        assertNull(store.activities.byIdWithPoints("activity-1"))
        assertEquals(0L, store.activities.count())
        val active = store.trash.active().single { it.entity_id == "activity-1" }
        assertEquals("activity", active.entity_table)
        assertTrue(store.trash.undo(tombstone!!))
        assertEquals(1L, store.activities.count())
        assertEquals(11, store.activities.points("activity-1").size)
    }

    @Test
    fun deletingAnActivityAlsoRemovesTheObservationsItsRollupsCount() {
        val store = inMemoryStore().let { LocalStore(it.driver, it.db) }
        store.activities.record(activity(), "manual", "activity-1")
        val group = app.openhealth.core.activity.activityGroupKey("activity-1")
        val observations = listOf(
            testObservation(
                id = "activity-1-distance",
                type = app.openhealth.core.observation.ObservationType.DISTANCE,
                value = 5_000.0,
                unit = app.openhealth.core.units.UcumUnit.METRE,
                start = start,
                end = start.plus(kotlin.time.Duration.parse("30m")),
            ).copy(groupKey = group),
            testObservation(
                id = "activity-1-active",
                type = app.openhealth.core.observation.ObservationType.ACTIVE_MINUTES,
                value = 30.0,
                unit = app.openhealth.core.units.UcumUnit.MINUTE,
                start = start,
                end = start.plus(kotlin.time.Duration.parse("30m")),
            ).copy(groupKey = group),
        )
        app.openhealth.data.repo.ObservationRepository(store.db).recordAll(observations)
        assertEquals(
            2,
            store.db.observationQueries.observationsForGroup(group).executeAsList().size,
        )

        val tombstone = store.trash.deleteActivity("activity-1")
        assertNotNull(tombstone)
        // A rollup is rebuilt from layer 2, so leaving these behind would keep a deleted
        // run in the day's distance and active minutes (`pr.md` §7.3).
        assertEquals(
            emptyList(),
            store.db.observationQueries.observationsForGroup(group).executeAsList(),
        )

        assertTrue(store.trash.undo(tombstone!!))
        val restored = store.db.observationQueries.observationsForGroup(group).executeAsList()
        assertEquals(2, restored.size)
        assertEquals(
            setOf("activity-1-distance", "activity-1-active"),
            restored.map { it.id }.toSet(),
        )
        assertEquals(5_000.0, restored.single { it.id == "activity-1-distance" }.value_)
    }

    @Test
    fun theTimelineShowsTheActivity() {
        val store = inMemoryStore().let { LocalStore(it.driver, it.db) }
        store.activities.record(activity(), "manual", "activity-1")
        val timeline = TimelineRepository(store.db, store.driver)
        assertTrue(timeline.page(10).any { it.kind == "activity" && it.entityId == "activity-1" })
    }

    @Test
    fun notesCanBeEditedWithoutTouchingTheRoute() {
        val store = inMemoryStore().let { LocalStore(it.driver, it.db) }
        store.activities.record(activity(), "manual", "activity-1")
        assertTrue(store.activities.updateNotes("activity-1", "Felt easy"))
        assertEquals("Felt easy", store.activities.byId("activity-1")!!.notes)
        assertEquals(11, store.activities.points("activity-1").size)
    }

    private fun LocalDate.plusDays(days: Long): LocalDate = this.plusDaysCompat(days)

    private fun LocalDate.plusDaysCompat(days: Long): LocalDate =
        this.plus(kotlinx.datetime.DatePeriod(days = days.toInt()))
}
