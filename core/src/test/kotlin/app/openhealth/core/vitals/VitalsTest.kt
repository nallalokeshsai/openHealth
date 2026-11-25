package app.openhealth.core.vitals

import app.openhealth.core.citation.CitationKey
import app.openhealth.core.observation.ObservationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

class VitalRangesTest {
    @Test
    fun everyShippedRangeHasACitationAndAPublishedStatement() {
        val ranges = VitalRanges.all()
        assertEquals(7, ranges.size)
        ranges.forEach { range ->
            assertNotNull(CitationKey.entries.firstOrNull { it == range.citation })
            assertTrue(range.statement.isNotBlank())
            assertTrue(range.population.isNotBlank())
        }
    }

    @Test
    fun aBloodPressureReadingSitsInsideOrOutsideTheHomeRange() {
        val range = VitalRanges.forReading(ObservationType.BLOOD_PRESSURE_SYSTOLIC, null)!!
        assertEquals(CitationKey.ESH_HYPERTENSION_2023, range.citation)
        assertEquals(RangePosition.WITHIN, range.position(128.0))
        assertEquals(RangePosition.ABOVE, range.position(135.0))
        assertEquals(RangePosition.BELOW, range.position(85.0))
        assertEquals("below 135/85", range.statement)
        val resting = VitalRanges.forReading(ObservationType.RESTING_HEART_RATE, null)!!
        assertEquals(RangePosition.WITHIN, resting.position(100.0))
        assertEquals(RangePosition.ABOVE, resting.position(100.1))
    }

    @Test
    fun glucoseUsesTheContextItWasRecordedIn() {
        val fasting = VitalRanges.forReading(
            ObservationType.BLOOD_GLUCOSE,
            VitalContextCode(meal = MealContext.FASTING).encode(),
        )!!
        val postMeal = VitalRanges.forReading(
            ObservationType.BLOOD_GLUCOSE,
            VitalContextCode(meal = MealContext.POST_MEAL).encode(),
        )!!
        assertEquals(5.6, fasting.high)
        assertEquals(7.8, postMeal.high)
        assertEquals(RangePosition.ABOVE, fasting.position(6.0))
        assertEquals(RangePosition.WITHIN, postMeal.position(6.0))
    }

    @Test
    fun onlyBloodPressureAndGlucoseHaveAFourStopStrip() {
        assertNotNull(
            VitalRanges.scaleFor(ObservationType.BLOOD_PRESSURE_SYSTOLIC, null),
        )
        assertNotNull(VitalRanges.scaleFor(ObservationType.BLOOD_GLUCOSE, null))
        assertNull(VitalRanges.scaleFor(ObservationType.BODY_TEMPERATURE, null))
        assertNull(VitalRanges.scaleFor(ObservationType.OXYGEN_SATURATION, null))
    }

    @Test
    fun theStripsFourStopsMeetAtTheirBoundaries() {
        val bands = VitalRanges.scaleFor(ObservationType.BLOOD_PRESSURE_DIASTOLIC, null)!!
        assertEquals(listOf(60.0, 85.0, 90.0), bands.dropLast(1).map { it.to })
        assertEquals(listOf(60.0, 85.0, 90.0), bands.drop(1).map { it.from })
        assertEquals(RangeBandLabel.LOW, bands.first().label)
        assertEquals(RangeBandLabel.HIGH, bands.last().label)
    }

    @Test
    fun contextCodesRoundTripAndOlderPlainNamesStillRead() {
        val encoded = VitalContextCode(
            arm = ArmSide.LEFT,
            position = BodyPosition.SITTING,
            meal = MealContext.POST_MEAL,
            homeSlot = HomeSlot.MORNING,
        ).encode()
        assertEquals("arm=left;position=sitting;meal=post_meal;home=morning", encoded)
        val decoded = VitalContextCode.decode(encoded)
        assertEquals(ArmSide.LEFT, decoded.arm)
        assertEquals(BodyPosition.SITTING, decoded.position)
        assertEquals(MealContext.POST_MEAL, decoded.meal)
        assertEquals(HomeSlot.MORNING, decoded.homeSlot)
        assertEquals(
            MealContext.FASTING,
            VitalContextCode.decode("FASTING").meal,
        )
    }
}

class BloodPressureProtocolTest {
    private val start = LocalDate(2026, 11, 18)

    @Test
    fun sevenDaysOfTwoMorningAndTwoEveningReadingsCompleteTheProtocol() {
        val readings = (0 until 7).flatMap { day ->
            val date = start.plusDays(day)
            listOf(
                BpProtocolReading(date, HomeSlot.MORNING),
                BpProtocolReading(date, HomeSlot.MORNING),
                BpProtocolReading(date, HomeSlot.EVENING),
                BpProtocolReading(date, HomeSlot.EVENING),
            )
        }
        val state = BloodPressureProtocol.state(start, readings)
        assertEquals(28, state.recorded)
        assertTrue(state.complete)
        assertEquals(1f, state.fraction)
        assertEquals(7, state.days.size)
    }

    @Test
    fun anIncompleteSlotIsTheNextAction() {
        val state = BloodPressureProtocol.state(
            start,
            listOf(BpProtocolReading(start, HomeSlot.MORNING)),
        )
        val next = state.nextSlot(start)!!
        assertEquals(HomeSlot.MORNING, next.slot)
        assertEquals(start, next.date)
        assertTrue(!state.complete)
    }

    @Test
    fun theAverageCountsEachReadingOnce() {
        val average = BloodPressureProtocol.average(
            listOf(120.0 to 78.0, 130.0 to 82.0, 118.0 to 76.0),
        )!!
        assertEquals(122.666, average.systolic, 0.001)
        assertEquals(78.666, average.diastolic, 0.001)
        assertEquals(3, average.readings)
    }

    private fun LocalDate.plusDays(days: Int): LocalDate =
        this + kotlinx.datetime.DatePeriod(days = days)
}
