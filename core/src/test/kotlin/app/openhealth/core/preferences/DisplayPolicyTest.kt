package app.openhealth.core.preferences

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DisplayPolicyTest {
    @Test
    fun theStandardPolicyShowsEverySensitiveMetric() {
        val policy = DisplayPolicy.Standard
        SensitiveMetric.entries.forEach { metric ->
            assertTrue(policy.shows(metric), "$metric should be shown")
        }
        assertFalse(policy.hidesSomething)
    }

    @Test
    fun weightNeutralHidesWeightCaloriesBodyFatAndPhotos() {
        val policy = DisplayPolicy.WeightNeutral
        assertEquals(
            setOf(
                SensitiveMetric.WEIGHT,
                SensitiveMetric.CALORIES,
                SensitiveMetric.BODY_FAT,
                SensitiveMetric.PROGRESS_PHOTOS,
            ),
            SensitiveMetric.entries.filterNot(policy::shows).toSet(),
        )
        assertTrue(policy.hidesSomething)
    }

    @Test
    fun aHiddenValueComesBackAsNullAndIsNeverGuessed() {
        val hidden = DisplayPolicy.WeightNeutral.value(SensitiveMetric.WEIGHT, 82.4)
        assertNull(hidden)
        val shown = DisplayPolicy.Standard.value(SensitiveMetric.WEIGHT, 82.4)
        assertEquals(82.4, shown)
    }

    @Test
    fun ofIsTheOnlyWayToBuildOne() {
        assertEquals(DisplayPolicy.WeightNeutral, DisplayPolicy.of(true))
        assertEquals(DisplayPolicy.Standard, DisplayPolicy.of(false))
    }

    @Test
    fun nonSensitiveActivitiesKeepWorkingUnderWeightNeutral() {
        // Training, steps, sleep and vitals are not SensitiveMetric members at all.
        assertTrue(SensitiveMetric.entries.none { it.name == "STEPS" })
        assertTrue(SensitiveMetric.entries.none { it.name == "SLEEP" })
    }
}
