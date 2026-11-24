package app.openhealth.android.training

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The rest timer the app actually runs (`pr.md` §6.5, `docs/adr/0010-gym-training.md` §15).
 * [RestTimerStore] is the state that survives process death: the arithmetic in
 * `app.openhealth.core.training.RestTimer` is the same set of rules, but the service and
 * the notification read this file, so the file is what the exit test has to prove.
 *
 * Each case reads the state back through a **second** store instance, which is what a
 * restarted process does.
 */
@RunWith(AndroidJUnit4::class)
class RestTimerStoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val start = 1_700_000_000_000L

    private fun afterProcessDeath() = RestTimerStore(context)

    @Before
    fun clean() {
        RestTimerStore(context).clear()
    }

    @After
    fun tidy() {
        RestTimerStore(context).clear()
    }

    @Test
    fun aRestIsADeadlineOnDiskAndSurvivesTheProcess() {
        RestTimerStore(context).start(90, start)

        val resumed = afterProcessDeath()
        val atOnce = assertNotNull(resumed.read(start))
        assertEquals(90L, atOnce.remainingSeconds)
        assertEquals(90L, atOnce.totalSeconds)
        assertEquals("", atOnce.label)
        assertTrue(resumed.active())
        assertEquals(60L, resumed.read(start + 30_000L)!!.remainingSeconds)
        assertEquals(0L, resumed.read(start + 200_000L)!!.remainingSeconds)
    }

    @Test
    fun aPausedRestHoldsItsRemainingSecondsHowLongTheProcessIsGone() {
        val store = RestTimerStore(context)
        store.start(90, start)
        store.togglePause(start + 10_000L)

        val resumed = afterProcessDeath()
        val paused = assertNotNull(resumed.read(start + 120_000L))
        assertTrue(paused.paused)
        assertEquals(80L, paused.remainingSeconds)
        assertFalse(paused.running)

        resumed.togglePause(start + 120_000L)
        assertEquals(80L, resumed.read(start + 120_000L)!!.remainingSeconds)
        assertEquals(50L, resumed.read(start + 150_000L)!!.remainingSeconds)
    }

    @Test
    fun fifteenSecondsEitherWayKeepsTheBarHonest() {
        val store = RestTimerStore(context)
        store.start(90, start)
        store.adjust(15, start)
        assertEquals(105L, store.read(start)!!.remainingSeconds)
        // The bar can never show more remaining than its total.
        assertEquals(105L, store.read(start)!!.totalSeconds)
        store.adjust(-15, start)
        val shortened = assertNotNull(afterProcessDeath().read(start))
        assertEquals(90L, shortened.remainingSeconds)
        assertEquals(105L, shortened.totalSeconds)
    }

    @Test
    fun aRestNeverRunsPastTenMinutes() {
        val store = RestTimerStore(context)
        store.start(900, start)
        assertEquals(600L, store.read(start)!!.totalSeconds)
        store.adjust(300, start)
        assertEquals(600L, afterProcessDeath().read(start)!!.remainingSeconds)
    }

    @Test
    fun shorteningToZeroEndsTheRestAndSkipLeavesNothingToResume() {
        val store = RestTimerStore(context)
        store.start(10, start)
        store.adjust(-30, start)
        assertNull(afterProcessDeath().read(start))
        assertFalse(afterProcessDeath().active())

        store.start(90, start)
        store.clear()
        assertNull(afterProcessDeath().read(start))
        assertFalse(afterProcessDeath().active())
    }

    @Test
    fun anAdjustOrAPauseWithNothingRestingChangesNothing() {
        val store = RestTimerStore(context)
        store.adjust(15, start)
        store.togglePause(start)
        assertNull(afterProcessDeath().read(start))
        assertFalse(afterProcessDeath().active())
    }
}
