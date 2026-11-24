package app.openhealth.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openhealth.android.data.AndroidDatabaseFactory
import app.openhealth.core.exercise.ExerciseCatalogue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

/**
 * The bundled library crosses the Android boundary: the plain-text resource is packaged in
 * the APK, parsed by `:core`, and written into the encrypted store once. This is the
 * assertion a JVM test cannot make (`BUILD-ROADMAP.md` §B4, deliverable 3).
 */
@RunWith(AndroidJUnit4::class)
class ExerciseLibraryDeviceTest {
    private lateinit var context: Context
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val app = context.applicationContext as OpenHealthApp
        runCatching { app.container.close() }
        context.deleteDatabase(AndroidDatabaseFactory.DEFAULT_DATABASE)
        context.getSharedPreferences("openhealth.vault", Context.MODE_PRIVATE)
            .edit().clear().commit()
        container = AppContainer(context)
    }

    @After
    fun tearDown() {
        container.close()
    }

    @Test
    fun theLibraryIsPackagedAndWrittenOnce() {
        val started = System.nanoTime()
        val first = container.store.training.countExercises()
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue(elapsedMs < SEED_BUDGET_MS, "seeding took ${elapsedMs}ms")
        assertTrue(first >= 150, "only $first exercises reached the store")
        assertEquals(ExerciseCatalogue.bundled.size.toLong(), first)

        val catalogue = container.store.training.catalogue()
        assertTrue(catalogue.all { it.instructions.isNotEmpty() })
        assertTrue(catalogue.all { it.primary.isNotEmpty() })

        // A second store on the same database skips the write and duplicates nothing.
        container.close()
        container = AppContainer(context)
        assertEquals(first, container.store.training.countExercises())
    }

    private companion object {
        const val SEED_BUDGET_MS = 5_000L
    }
}
