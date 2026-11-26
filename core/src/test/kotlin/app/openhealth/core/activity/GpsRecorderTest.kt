package app.openhealth.core.activity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class GpsRecorderTest {
    private val start = Instant.parse("2026-11-24T08:00:00Z")
    private val zone = TimeZone.of("Europe/Berlin")

    private fun snapshot() = GpsRecorder.start(
        id = "recording-1",
        type = ActivityType.RUN,
        startedAt = start,
        zoneId = zone.id,
        localDate = start.toLocalDateTime(zone).date,
    )

    private fun fix(offsetSeconds: Long, latitude: Double, longitude: Double = 13.4) = GpsFix(
        latitude = latitude,
        longitude = longitude,
        timeEpochMillis = start.toEpochMilliseconds() + offsetSeconds * 1_000,
        accuracyM = 5.0,
    )

    @Test
    fun fixesBecomePointsAndDistance() {
        var state = snapshot()
        state = GpsRecorder.addFix(state, fix(0, 52.5200))
        state = GpsRecorder.addFix(state, fix(60, 52.5210))
        state = GpsRecorder.addFix(state, fix(120, 52.5220))
        assertEquals(3, state.points.size)
        assertTrue(state.distanceM > 200.0, "distance ${state.distanceM}")
        assertEquals(120, state.points.last().elapsedSeconds)
    }

    @Test
    fun aBadAccuracyFixIsIgnored() {
        var state = snapshot()
        state = GpsRecorder.addFix(state, fix(0, 52.5200).copy(accuracyM = 500.0))
        assertTrue(state.points.isEmpty())
    }

    @Test
    fun pauseExcludesTimeAndFixings() {
        var state = snapshot()
        state = GpsRecorder.addFix(state, fix(0, 52.5200))
        val pausedAt = start.toEpochMilliseconds() + 30_000
        state = GpsRecorder.pause(state, pausedAt)
        state = GpsRecorder.addFix(state, fix(60, 52.5300))
        assertEquals(1, state.points.size)
        state = GpsRecorder.resume(state, pausedAt + 120_000)
        state = GpsRecorder.addFix(state, fix(200, 52.5210))
        assertEquals(2, state.points.size)
        // 200 s wall clock, 120 s paused: 80 s elapsed.
        assertEquals(80, state.points.last().elapsedSeconds)
    }

    @Test
    fun aSnapshotRoundTripsThroughJson() {
        var state = snapshot()
        state =
            GpsRecorder.addFix(state, fix(0, 52.5200).copy(elevationM = 34.0, heartRate = 140.0))
        state = GpsRecorder.addFix(state, fix(60, 52.5220))
        val encoded = GpsSnapshotCodec.encode(state)
        val decoded = GpsSnapshotCodec.decode(encoded)
        assertEquals(state, decoded)
        assertEquals(state, GpsRecorder.toGpsSnapshot(encoded))
        assertNull(GpsRecorder.toGpsSnapshot("not json"))
    }

    @Test
    fun finishBuildsAnActivityWithAveragesAndMetrics() {
        var state = snapshot()
        state = GpsRecorder.addFix(
            state,
            fix(0, 52.5200).copy(heartRate = 140.0, elevationM = 30.0, cadence = 80.0),
        )
        state = GpsRecorder.addFix(
            state,
            fix(60, 52.5210).copy(heartRate = 160.0, elevationM = 34.0, cadence = 82.0),
        )
        val record = GpsRecorder.finish(state, start.toEpochMilliseconds() + 120_000)
        assertEquals(ActivityType.RUN, record.type)
        assertEquals(ActivitySource.RECORDED, record.source)
        assertEquals(120, record.durationSeconds)
        assertTrue(record.hasRoute)
        assertEquals(150.0, record.averageHeartRate!!, 0.001)
        assertEquals(160.0, record.maxHeartRate!!, 0.001)
        assertEquals(4.0, record.elevationGainM!!, 0.001)
        val metrics = ActivityCalculator.metrics(record)
        assertTrue(metrics.distanceM > 100.0)
    }

    @Test
    fun finishingWhilePausedEndsAtThePause() {
        var state = snapshot()
        state = GpsRecorder.addFix(state, fix(0, 52.5200))
        val pausedAt = start.toEpochMilliseconds() + 60_000
        state = GpsRecorder.pause(state, pausedAt)
        val record = GpsRecorder.finish(state, pausedAt + 600_000)
        assertEquals(60, record.durationSeconds)
    }

    @Test
    fun elapsedSecondsExcludesThePausedInterval() {
        var state = snapshot()
        val now = start.toEpochMilliseconds() + 10_000
        assertEquals(10, GpsRecorder.elapsedSeconds(state, now))
        state = GpsRecorder.pause(state, now)
        assertEquals(10, GpsRecorder.elapsedSeconds(state, now + 60_000))
        state = GpsRecorder.resume(state, now + 60_000)
        assertEquals(20, GpsRecorder.elapsedSeconds(state, now + 70_000))
    }

    @Test
    fun finishAfterAResumeLeavesThePausedTimeOut() {
        var state = snapshot()
        state = GpsRecorder.addFix(state, fix(0, 52.5200))
        val pausedAt = start.toEpochMilliseconds() + 30_000
        state = GpsRecorder.pause(state, pausedAt)
        state = GpsRecorder.resume(state, pausedAt + 120_000)
        state = GpsRecorder.addFix(state, fix(200, 52.5210))
        val record = GpsRecorder.finish(state, start.toEpochMilliseconds() + 200_000)
        // 200 s on the wall clock, 120 s of it paused: the activity lasted 80 s, which
        // is what the screen counted and what the route's elapsed axis ends at.
        assertEquals(80, record.durationSeconds)
        assertEquals(80, record.points.last().elapsedSeconds)
        assertEquals(
            80,
            GpsRecorder.elapsedSeconds(state, start.toEpochMilliseconds() + 200_000),
        )
    }

    @Test
    fun movingWhilePausedAddsNoDistance() {
        var state = snapshot()
        state = GpsRecorder.addFix(state, fix(0, 52.5200))
        state = GpsRecorder.addFix(state, fix(60, 52.5210))
        val walked = state.distanceM
        val pausedAt = start.toEpochMilliseconds() + 70_000
        state = GpsRecorder.pause(state, pausedAt)
        // The person is driven a kilometre away while the recording is paused.
        state = GpsRecorder.resume(state, pausedAt + 300_000)
        state = GpsRecorder.addFix(state, fix(400, 52.5300))
        assertEquals(3, state.points.size, "the fix after a resume is still recorded")
        assertEquals(walked, state.distanceM, 0.001)
        // Distance starts counting again from the new segment.
        state = GpsRecorder.addFix(state, fix(460, 52.5310))
        assertTrue(state.distanceM > walked, "distance ${state.distanceM} after walked $walked")
    }

    @Test
    fun aRecordingWithoutRouteStillBuildsAValidRecord() {
        val state = snapshot()
        val record = GpsRecorder.finish(state, start.toEpochMilliseconds() + 30_000)
        assertNotNull(record.localDate)
        assertEquals(LocalDate(2026, 11, 24), record.localDate)
        assertTrue(!record.hasRoute)
    }
}
