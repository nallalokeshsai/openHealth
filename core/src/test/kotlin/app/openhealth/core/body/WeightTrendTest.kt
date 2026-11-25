package app.openhealth.core.body

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class WeightTrendTest {
    private val today = LocalDate(2026, 11, 25)

    @Test
    fun theDailyValueIsTheLatestCheckInOfThatDay() {
        val day = LocalDate(2026, 11, 20)
        val trend = WeightTrendCalculator.compute(
            listOf(
                WeighIn(day, 82.0, 1L),
                WeighIn(day, 81.6, 2L),
                WeighIn(LocalDate(2026, 11, 21), 81.4, 3L),
            ),
            today,
        )
        assertEquals(81.4, trend.latestKg)
        assertEquals(2, trend.points.size)
        assertEquals(81.6, trend.points.first().kg)
    }

    @Test
    fun aTrendNeedsThreeDistinctDaysAcrossAWeek() {
        val twoPoints = WeightTrendCalculator.compute(
            listOf(
                WeighIn(LocalDate(2026, 11, 1), 84.0),
                WeighIn(LocalDate(2026, 11, 8), 83.0),
            ),
            today,
        )
        assertNull(twoPoints.weeklyRateKg)

        val sameWeek = WeightTrendCalculator.compute(
            listOf(
                WeighIn(LocalDate(2026, 11, 20), 83.0),
                WeighIn(LocalDate(2026, 11, 21), 82.8),
                WeighIn(LocalDate(2026, 11, 22), 82.6),
            ),
            today,
        )
        assertNull(sameWeek.weeklyRateKg, "three days is not a weekly rate")
    }

    @Test
    fun aFallingSeriesHasANegativeWeeklyRate() {
        val trend = WeightTrendCalculator.compute(
            listOf(
                WeighIn(LocalDate(2026, 11, 1), 84.0),
                WeighIn(LocalDate(2026, 11, 8), 83.3),
                WeighIn(LocalDate(2026, 11, 15), 82.6),
                WeighIn(LocalDate(2026, 11, 22), 81.9),
            ),
            today,
        )
        assertEquals(-0.7, trend.weeklyRateKg!!, 0.0001)
        assertTrue(trend.changeKg!! < 0.0)
        assertTrue(trend.changeSentence!!.startsWith("Down"))
    }

    @Test
    fun checkInsOlderThanTheRateWindowAreIgnored() {
        val trend = WeightTrendCalculator.compute(
            listOf(
                WeighIn(LocalDate(2026, 1, 1), 95.0),
                WeighIn(LocalDate(2026, 11, 1), 84.0),
                WeighIn(LocalDate(2026, 11, 8), 83.5),
                WeighIn(LocalDate(2026, 11, 15), 83.0),
            ),
            today,
        )
        assertEquals(83.0, trend.latestKg)
        assertEquals(4, trend.checkIns)
    }

    @Test
    fun noCheckInsShowsNoNumber() {
        val trend = WeightTrendCalculator.compute(emptyList(), today)
        assertNull(trend.latestKg)
        assertNull(trend.changeSentence)
        assertNull(trend.weeklyRateKg)
    }
}

class BodyCsvTest {
    private val today = LocalDate(2026, 11, 25)

    @Test
    fun aRoundTripKeepsUnitsAndMethods() {
        val rows = listOf(
            BodyCsvRow(today, BodyCsv.WEIGHT, 82.4, app.openhealth.core.units.UcumUnit.KILOGRAM,
                app.openhealth.core.observation.MeasurementMethod.SCALE, "morning"),
            BodyCsvRow(today, "waist", 84.0,
                app.openhealth.core.units.UcumUnit.CENTIMETRE,
                app.openhealth.core.observation.MeasurementMethod.MANUAL_ENTRY),
        )
        val parsed = BodyCsv.parse(BodyCsv.export(rows))
        assertTrue(parsed.problems.isEmpty())
        assertEquals(2, parsed.rows.size)
        assertEquals(82.4, parsed.rows[1].value)
        assertEquals("waist", parsed.rows[0].type)
        assertEquals("morning", parsed.rows[1].notes)
    }

    @Test
    fun poundsAndInchesAreConvertedBeforeStorage() {
        val parsed = BodyCsv.parse(
            "date,type,value,unit,method,notes\n" +
                "2026-11-25,weight,180,[lb_av],scale,\n" +
                "2026-11-25,waist,34,[in_i],,,\n",
        )
        assertEquals(81.6466, parsed.rows[0].value, 0.001)
        assertEquals(86.36, parsed.rows[1].value, 0.001)
        assertEquals(app.openhealth.core.units.UcumUnit.KILOGRAM, parsed.rows[0].unit)
        assertEquals(app.openhealth.core.units.UcumUnit.CENTIMETRE, parsed.rows[1].unit)
    }

    @Test
    fun aBadRowIsReportedAndDoesNotStopTheGoodOnes() {
        val parsed = BodyCsv.parse(
            "date,type,value,unit,method,notes\n" +
                "not-a-date,weight,80,kg,,\n" +
                "2026-11-25,weight,80,kg,,\n" +
                "2026-11-25,unicorn,80,kg,,\n" +
                "2026-11-25,weight,99999,kg,,\n",
        )
        assertEquals(1, parsed.rows.size)
        assertEquals(3, parsed.problems.size)
    }

    @Test
    fun quotesAndCommasInANoteSurvive() {
        val parsed = BodyCsv.parse(
            "date,type,value,unit,method,notes\n" +
                "2026-11-25,weight,80,kg,,\"after a cold, before breakfast\"\n",
        )
        assertEquals("after a cold, before breakfast", parsed.rows.single().notes)
    }
}
