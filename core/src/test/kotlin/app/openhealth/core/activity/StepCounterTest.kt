package app.openhealth.core.activity

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * The platform counter is cumulative since boot and resets on a reboot (`pr.md` §6.6:
 * "a reboot or app kill does not lose the day's count"). These tests pin every way that
 * behaves badly: a new boot, a reset on the same boot, a clock that moves backwards, a
 * midnight crossing and a corrupt spike.
 */
class StepCounterTest {
    private val zone = TimeZone.of("Europe/Berlin")
    private val boot = Instant.parse("2026-11-23T06:00:00Z")
    private val bootMillis = boot.toEpochMilliseconds()

    @Test
    fun aMonotonicCounterBecomesOneInterval() {
        val first = sample(1_000, boot.plus(1.hours))
        val start = StepCounter.apply(StepCounterState(), first, zone)
        assertEquals(1_000, start.intervals.sumOf { it.steps })

        val next = sample(1_250, boot.plus(1.hours).plus(5.minutes))
        val update = StepCounter.apply(start.state, next, zone)
        assertEquals(250, update.intervals.sumOf { it.steps })
        assertEquals(boot.plus(1.hours), update.intervals.single().start)
        assertEquals(boot.plus(1.hours).plus(5.minutes), update.intervals.single().end)
        assertFalse(update.repaired)
    }

    @Test
    fun aRebootIsTreatedAsANewBootNotANegativeDelta() {
        val first = sample(9_000, boot.plus(10.hours))
        val state = StepCounter.apply(StepCounterState(), first, zone).state
        // The phone rebooted at 20:00 and the counter restarted at 0.
        val rebootedAt = boot.plus(14.hours)
        val afterReboot = StepCounterSample(
            count = 400,
            bootEpochMillis = rebootedAt.toEpochMilliseconds(),
            at = rebootedAt.plus(30.minutes),
        )
        val update = StepCounter.apply(state, afterReboot, zone)
        assertEquals(400, update.intervals.sumOf { it.steps })
        assertEquals(rebootedAt.toEpochMilliseconds(), update.state.bootEpochMillis)
        assertTrue(update.intervals.all { it.steps >= 0 })
    }

    @Test
    fun aCounterThatGoesBackwardsOnTheSameBootIsAReset() {
        val first = sample(5_000, boot.plus(2.hours))
        val state = StepCounter.apply(StepCounterState(), first, zone).state
        val reset = sample(120, boot.plus(2.hours).plus(10.minutes))
        val update = StepCounter.apply(state, reset, zone)
        assertEquals(120, update.intervals.sumOf { it.steps })
    }

    @Test
    fun aMidnightCrossingIsSplitAcrossTheTwoLocalDays() {
        val midnight = Instant.parse("2026-11-23T23:00:00Z") // 00:00 local in Berlin (CET)
        val before = StepCounterSample(10_000, bootMillis, midnight.minus(30.minutes))
        val state = StepCounter.apply(StepCounterState(), before, zone).state
        val after = StepCounterSample(11_000, bootMillis, midnight.plus(30.minutes))
        val update = StepCounter.apply(state, after, zone)

        assertEquals(2, update.intervals.size)
        assertEquals(1_000, update.intervals.sumOf { it.steps })
        val firstDay = update.intervals[0].start.toLocalDateTime(zone).date
        val secondDay = update.intervals[1].start.toLocalDateTime(zone).date
        assertEquals(firstDay.plus(DatePeriod(days = 1)), secondDay)
        assertEquals(500, update.intervals[0].steps)
        assertEquals(500, update.intervals[1].steps)
    }

    @Test
    fun aCorruptSpikeIsClampedToAPlausibleRate() {
        val first = sample(1_000, boot.plus(1.hours))
        val state = StepCounter.apply(StepCounterState(), first, zone).state
        val spike = sample(1_000_000, boot.plus(1.hours).plus(1.minutes))
        val update = StepCounter.apply(state, spike, zone)
        assertTrue(update.repaired)
        assertEquals(StepCounter.MAX_STEPS_PER_MINUTE.toLong(), update.intervals.sumOf { it.steps })
    }

    @Test
    fun aClockThatMovesBackwardsDoesNotInventAnInterval() {
        val first = sample(2_000, boot.plus(3.hours))
        val state = StepCounter.apply(StepCounterState(), first, zone).state
        val earlier = sample(2_300, boot.plus(3.hours).minus(10.minutes))
        val update = StepCounter.apply(state, earlier, zone)
        assertTrue(update.intervals.isEmpty())
        assertEquals(2_300, update.state.lastCount)
    }

    @Test
    fun aBootFromAnEarlierDayIsOnlyABaselineNotTodaysSteps() {
        // Booted three days ago: the 42,000 steps cover three days, so attributing them
        // to today would invent a number the person never walked.
        val now = Instant.parse("2026-11-23T12:00:00Z")
        val oldBoot = now.minus(72.hours).toEpochMilliseconds()
        val update = StepCounter.apply(
            StepCounterState(),
            StepCounterSample(42_000, oldBoot, now),
            zone,
        )
        assertTrue(update.intervals.isEmpty())
        assertEquals(42_000, update.state.lastCount)
        assertTrue(update.repaired)
    }

    @Test
    fun aBootTodayAttributesTheStepsSinceBoot() {
        // Booted at 09:00 today: the counter is today's steps since then.
        val booted = Instant.parse("2026-11-23T09:00:00Z")
        val now = Instant.parse("2026-11-23T12:00:00Z")
        val update = StepCounter.apply(
            StepCounterState(),
            StepCounterSample(3_200, booted.toEpochMilliseconds(), now),
            zone,
        )
        assertEquals(3_200, update.intervals.sumOf { it.steps })
        assertEquals(booted, update.intervals.first().start)
        assertEquals(now, update.intervals.last().end)
    }

    @Test
    fun everyIntervalIsContiguousAndTheTotalNeverChanges() {
        val sampleArb = arbitrary {
            val at = Instant.fromEpochMilliseconds(
                Arb.long(
                    boot.plus(
                        1.hours,
                    ).toEpochMilliseconds()..boot.plus(20.hours).toEpochMilliseconds(),
                ).bind(),
            )
            val count = Arb.long(0L..40_000L).bind()
            StepCounterSample(count, bootMillis, at)
        }
        var state = StepCounterState()
        val intervals = mutableListOf<StepInterval>()
        var lastTotal = 0L
        runBlocking {
            checkAll(50, sampleArb) { sample ->
                val update = StepCounter.apply(state, sample, zone)
                intervals += update.intervals
                lastTotal += update.intervals.sumOf { it.steps }
                assertTrue(update.intervals.all { it.steps >= 0 })
                assertTrue(update.intervals.all { it.end >= it.start })
                state = update.state
            }
        }
        assertTrue(lastTotal >= 0)
        val byStart = intervals.sortedBy { it.start }
        byStart.zipWithNext().forEach { (first, second) ->
            assertFalse(second.start < first.end, "intervals overlap")
        }
    }

    @Test
    fun samplingIsSlowerWhenNothingNeedsTheNumber() {
        assertTrue(StepSampling.interval(true, false, 80) < StepSampling.interval(false, false, 80))
        assertTrue(StepSampling.interval(false, true, 80) < StepSampling.interval(false, false, 80))
        assertTrue(
            StepSampling.interval(false, false, StepSampling.LOW_BATTERY_PERCENT) >
                StepSampling.interval(false, false, 80),
        )
        assertEquals(30.seconds, StepSampling.interval(true, false, 80))
        assertEquals(5.minutes, StepSampling.interval(false, false, 80))
    }

    private fun sample(count: Long, at: Instant): StepCounterSample =
        StepCounterSample(count, bootMillis, at)
}
