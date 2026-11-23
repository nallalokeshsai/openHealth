package app.openhealth.core.preferences

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class AgeRulesTest {
    private val today = LocalDate(2026, 11, 23)

    @Test
    fun ageCountsCompletedYears() {
        assertEquals(30, AgeRules.ageOn(LocalDate(1996, 11, 23), today))
        assertEquals(29, AgeRules.ageOn(LocalDate(1996, 11, 24), today))
        assertEquals(30, AgeRules.ageOn(LocalDate(1996, 1, 1), today))
    }

    @Test
    fun theDayBeforeTheSixteenthBirthdayIsStillBlocked() {
        assertFalse(AgeRules.isSupported(LocalDate(2010, 11, 24), today))
        assertTrue(AgeRules.isSupported(LocalDate(2010, 11, 23), today))
    }

    @Test
    fun sixteenAndSeventeenMayTrackButNotLoseWeight() {
        val sixteen = LocalDate(2010, 3, 1)
        val seventeen = LocalDate(2009, 3, 1)
        assertTrue(AgeRules.isSupported(sixteen, today))
        assertFalse(AgeRules.mayUseWeightLossGoal(sixteen, today))
        assertFalse(AgeRules.mayUseWeightLossGoal(seventeen, today))
        assertTrue(AgeRules.mayUseWeightLossGoal(LocalDate(2008, 3, 1), today))
    }

    @Test
    fun aLeapDayBirthdayTurnsOverOnTheFirstOfMarch() {
        val born = LocalDate(2008, 2, 29)
        assertEquals(17, AgeRules.ageOn(born, LocalDate(2026, 2, 28)))
        assertEquals(18, AgeRules.ageOn(born, LocalDate(2026, 3, 1)))
    }
}
