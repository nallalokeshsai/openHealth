package app.openhealth.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    // A database and vault of this test's own: an instrumented run must never touch the
    // record on the owner's phone (`AGENTS.md` "Test device").
    private val database = "b4-library-test.db"
    private val vault = "openhealth.vault.b4-test"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(database)
        context.getSharedPreferences(vault, Context.MODE_PRIVATE).edit().clear().commit()
        container = AppContainer(context, databaseName = database, vaultPreferences = vault)
    }

    @After
    fun tearDown() {
        container.close()
        context.deleteDatabase(database)
        context.getSharedPreferences(vault, Context.MODE_PRIVATE).edit().clear().commit()
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
        container = AppContainer(context, databaseName = database, vaultPreferences = vault)
        assertEquals(first, container.store.training.countExercises())
    }

    private companion object {
        // Measured 1 717 ms on the owner's phone for the first open, which also loads
        // SQLCipher, checks integrity and creates the schema; 3 s is the regression wire.
        const val SEED_BUDGET_MS = 3_000L
    }
}
