package app.openhealth.core.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `pr.md` §6.15 and §12.3: consent is per category, the coach is off by default, and
 * Health Connect data needs its own switch.
 */
class CoachConsentTest {
    @Test
    fun theDefaultSendsNothing() {
        val consent = CoachConsent()
        assertFalse(consent.enabled)
        assertFalse(consent.sendsAnything)
        CoachCategory.entries.forEach { assertFalse(consent.allows(it)) }
    }

    @Test
    fun aCategoryThatIsOffIsNotAllowedEvenWhenTheCoachIsOn() {
        val consent = CoachConsent(enabled = true, categories = setOf(CoachCategory.TRAINING))
        assertTrue(consent.allows(CoachCategory.TRAINING))
        assertFalse(consent.allows(CoachCategory.VITALS))
        assertFalse(consent.allows(CoachCategory.SLEEP))
    }

    @Test
    fun aHealthConnectRowNeedsTheSecondSwitch() {
        val without = CoachConsent(enabled = true, categories = setOf(CoachCategory.VITALS))
        assertTrue(without.allows(CoachCategory.VITALS, viaHealthConnect = false))
        assertFalse(without.allows(CoachCategory.VITALS, viaHealthConnect = true))

        val with = without.copy(healthConnect = true)
        assertTrue(with.allows(CoachCategory.VITALS, viaHealthConnect = true))
        assertTrue(with.allows(CoachCategory.VITALS, viaHealthConnect = false))
    }

    @Test
    fun healthConnectAloneDoesNotTurnACategoryOn() {
        val consent = CoachConsent(enabled = true, healthConnect = true)
        assertFalse(consent.allows(CoachCategory.VITALS, viaHealthConnect = true))
        assertFalse(consent.sendsAnything)
    }

    @Test
    fun theDisclosureListsEveryCategoryOnce() {
        val consent = CoachConsent(enabled = true, categories = setOf(CoachCategory.TRAINING))
        val rows = CoachDisclosure.rows(consent)
        assertEquals(CoachCategory.entries.size, rows.size)
        assertEquals(CoachCategory.entries, rows.map { it.category })
        assertEquals(1, rows.count { it.included })
    }

    @Test
    fun turningTheCoachOffClosesEveryCategoryWithoutForgettingThem() {
        val on = CoachConsent(enabled = true, categories = setOf(CoachCategory.NUTRITION))
        val off = on.copy(enabled = false)
        assertFalse(off.allows(CoachCategory.NUTRITION))
        assertEquals(setOf(CoachCategory.NUTRITION), off.categories)
    }
}
