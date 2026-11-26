package app.openhealth.core.activity

import app.openhealth.core.body.ActivityMuscleLoad
import app.openhealth.core.body.Muscle
import app.openhealth.core.body.RecoveryMap
import app.openhealth.core.formula.RecoveryStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

class ActivityTest {
    private val zone = kotlinx.datetime.TimeZone.of("Europe/Berlin")
    private val start = Instant.parse("2026-11-24T08:00:00Z")

    @Test
    fun haversineMatchesAKnownDistance() {
        val metres = Geo.distanceMeters(52.5200, 13.4050, 52.5200, 13.4150)
        assertTrue(metres in 670.0..690.0, "one hundredth of a degree at Berlin was $metres m")
        assertEquals(0.0, Geo.distanceMeters(1.0, 2.0, 1.0, 2.0), 0.0001)
    }

    @Test
    fun routeDistanceIgnoresAnImpossibleJump() {
        val points = listOf(
            point(0.0, 0.0, 0),
            point(0.0, 0.001, 60),
            point(0.0, 5.0, 61),
            point(0.0, 0.002, 120),
        )
        val distance = Geo.routeDistanceMeters(points)
        assertTrue(distance < 300.0, "a 550 km jump must not count: $distance")
        assertTrue(distance > 100.0)
    }

    @Test
    fun elevationGainIgnoresNoise() {
        val points = listOf(
            point(1.0, 0.0, 0, elevation = 100.0),
            point(1.0, 0.001, 30, elevation = 100.2),
            point(1.0, 0.002, 60, elevation = 104.0),
            point(1.0, 0.003, 90, elevation = 101.0),
        )
        assertEquals(4.0, Geo.elevationGainMeters(points), 0.001)
    }

    @Test
    fun splitsCoverTheRouteInKilometres() {
        // 0.0006 degrees of longitude at the equator is about 67 m; 45 intervals are 3 km.
        val points = (0..45).map { index ->
            point(0.0, index * 0.0006, index * 60L)
        }
        val splits = ActivityCalculator.splits(points)
        assertEquals(3, splits.size)
        assertEquals(1, splits[0].index)
        assertTrue(splits[0].distanceM >= 1_000.0)
        assertEquals(3_000.0, splits.sumOf { it.distanceM }, 80.0)
    }

    @Test
    fun heartRateZonesAttributeTimeToTheEarlierSample() {
        val points = listOf(
            point(0.0, 0.0, 0, hr = 100.0),
            point(0.0, 0.001, 60, hr = 100.0),
            point(0.0, 0.002, 120, hr = 150.0),
            point(0.0, 0.003, 180, hr = 150.0),
        )
        val model = HeartRateZoneModel.from(points, maxHeartRate = 200.0, estimate = true)
        assertTrue(model.hasData)
        assertEquals(180, model.totalSeconds)
        assertEquals(120, model.slices.first { it.zone == HeartRateZone.Z1 }.seconds)
        assertEquals(60, model.slices.first { it.zone == HeartRateZone.Z3 }.seconds)
        assertTrue(model.maxIsEstimate)
    }

    @Test
    fun maxHeartRateEstimateIsOnlyAFallback() {
        assertEquals(190.0, HeartRateZoneModel.estimateMaxHeartRate(30))
        assertEquals(null, HeartRateZoneModel.estimateMaxHeartRate(null))
    }

    @Test
    fun bestEffortFindsTheFastestKilometre() {
        val points = (0..20).map { index ->
            point(0.0, index * 0.0005, index * 3L)
        }
        val efforts = ActivityCalculator.bestEfforts(points)
        val oneK = efforts.first { it.distanceM == 1_000.0 }
        assertTrue(oneK.achieved)
        assertTrue(oneK.durationSeconds in 45.0..65.0, "1 km was ${oneK.durationSeconds}s")
        assertFalse(efforts.first { it.distanceM == 5_000.0 }.achieved)
    }

    @Test
    fun activityFingerprintGroupsTheSameMinuteAndDistance() {
        val first = activity(distance = 5_000.0)
        val second =
            activity(distance = 5_040.0, startedAt = start.plus(kotlin.time.Duration.parse("20s")))
        assertEquals(first.fingerprint, second.fingerprint)
        assertTrue(ActivityDuplicates.sameSession(first, second))
        val different = activity(distance = 12_000.0)
        assertFalse(ActivityDuplicates.sameSession(first, different))
    }

    @Test
    fun trainingLoadRisesAndSettles() {
        val end = LocalDate(2026, 11, 24)
        val days = (0..100).map { back ->
            LoadDay(date = end.minus(DatePeriod(days = back)), load = 100.0)
        }
        val outcome = TrainingLoad.compute(end, days.reversed())
        val model = (outcome as app.openhealth.core.formula.FormulaOutcome.Computed).value
        assertEquals(90, model.points.size)
        assertTrue(model.fitness in 85.0..95.0, "fitness ${model.fitness}")
        assertTrue(model.fatigue > 95.0, "fatigue ${model.fatigue}")
        assertTrue(model.form in -12.0..-5.0, "form ${model.form}")
        assertTrue(model.hasData)
    }

    @Test
    fun trainingLoadWithoutSessionsIsNotAvailableAsATrend() {
        val end = LocalDate(2026, 11, 24)
        val outcome = TrainingLoad.compute(end, emptyList())
        val model = (outcome as app.openhealth.core.formula.FormulaOutcome.Computed).value
        assertFalse(model.hasData)
        assertEquals(0.0, model.fitness, 0.0)
        assertEquals(TrainingLoad.DEFAULT_WINDOW_DAYS, model.points.size)
    }

    @Test
    fun anActivityLoadMarksMusclesAsRecovering() {
        val now = start.plus(2.hours)
        val states = RecoveryMap.compute(
            now = now,
            sets = emptyList(),
            activityLoads = listOf(
                ActivityMuscleLoad(
                    activityId = "yoga-1",
                    at = start,
                    primary = listOf(Muscle.QUADS),
                    secondary = listOf(Muscle.GLUTES),
                ),
            ),
        )
        assertEquals(RecoveryStep.RECOVERING, states.getValue(Muscle.QUADS).step)
        assertEquals(RecoveryStep.RECOVERING, states.getValue(Muscle.GLUTES).step)
        assertEquals(RecoveryStep.UNTRAINED, states.getValue(Muscle.CHEST).step)
        assertNotNull(states.getValue(Muscle.QUADS).lastLoadedAt)
    }

    private fun activity(distance: Double, startedAt: Instant = start) = ActivityRecord(
        id = "a-1",
        type = ActivityType.RUN,
        source = ActivitySource.RECORDED,
        startedAt = startedAt,
        endedAt = startedAt.plus(kotlin.time.Duration.parse("30m")),
        localDate = startedAt.toLocalDateTime(zone).date,
        zoneId = zone.id,
        distanceM = distance,
    )

    private fun point(
        latitude: Double,
        longitude: Double,
        elapsed: Long,
        elevation: Double? = null,
        hr: Double? = null,
    ) = ActivityPoint(
        latitude = latitude,
        longitude = longitude,
        elapsedSeconds = elapsed,
        elevationM = elevation,
        heartRate = hr,
    )
}
