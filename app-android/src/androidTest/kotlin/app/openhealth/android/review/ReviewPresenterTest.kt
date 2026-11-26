package app.openhealth.android.review

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openhealth.android.AppContainer
import app.openhealth.android.preferences.Preferences
import app.openhealth.android.preferences.ThemeMode
import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.observation.SourceKind
import app.openhealth.core.preferences.HealthModule
import app.openhealth.core.preferences.ModulePreset
import app.openhealth.core.review.ReviewKind
import app.openhealth.core.source.Source
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import app.openhealth.ui.theme.OhAccent
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The review inbox's copy, read through the real presenter (`pr.md` §6.28).
 *
 * A day whose rollup has not been built has no winning source, and the sentence that
 * describes the disagreement then has to name both sources. Naming one of them twice
 * reads as a contradiction ("Garmin says 8,400 and Garmin says 8,400. The two differ.").
 */
@RunWith(AndroidJUnit4::class)
class ReviewPresenterTest {
    private lateinit var context: android.content.Context
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DATABASE)
        context.getSharedPreferences(TEST_VAULT, android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        container = AppContainer(
            context,
            databaseName = TEST_DATABASE,
            vaultPreferences = TEST_VAULT,
        )
    }

    @After
    fun tearDown() {
        container.close()
        context.deleteDatabase(TEST_DATABASE)
        context.getSharedPreferences(TEST_VAULT, android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun aDisagreementWithNoWinnerYetNamesBothSources() {
        val store = container.store
        // No rollup is built, so the day has no winning source and the no-winner
        // sentence is the one the screen shows.
        store.observations.invalidateRollupsWith(null)
        store.sources.upsertAll(
            listOf(
                Source(id = SourceId("garmin"), kind = SourceKind.GARMIN, label = "Garmin"),
                Source(id = SourceId("fitbit"), kind = SourceKind.FITBIT, label = "Fitbit"),
            ),
        )
        val today = container.clock.now().toLocalDateTime(container.zone).date
        val at = today.atStartOfDayIn(container.zone)
        store.observations.recordAll(
            listOf("garmin" to 8_400.0, "fitbit" to 6_100.0).map { (source, steps) ->
                Observation(
                    id = ObservationId("$source-steps"),
                    type = ObservationType.STEPS,
                    value = Measure(steps, UcumUnit.STEP),
                    start = at,
                    end = null,
                    provenance = Provenance(
                        sourceId = SourceId(source),
                        time = TimeProvenance.of(at, at, container.zone),
                        idempotencyKey = "$source-$today",
                    ),
                )
            },
        )

        val model = ReviewPresenter(context, store, container.clock, container.zone)
            .model(preferences())
        val row = model.rows.single { it.kind == ReviewKind.DISAGREEMENT }
        assertTrue("Garmin" in row.subtitle, "the subtitle must name Garmin: ${row.subtitle}")
        assertTrue("Fitbit" in row.subtitle, "the subtitle must name Fitbit: ${row.subtitle}")
        assertEquals(
            2,
            row.readings.size,
            "one reading per source is compared: ${row.readings}",
        )
    }

    /**
     * `DESIGN.md` §11.7: the person reads units, not UCUM codes. Steps are stored in
     * "{steps}" and blood pressure in "mm[Hg]"; neither may reach a screen.
     */
    @Test
    fun noReadingShowsAUcumCode() {
        val store = container.store
        store.observations.invalidateRollupsWith(null)
        store.sources.upsertAll(
            listOf(
                Source(id = SourceId("garmin"), kind = SourceKind.GARMIN, label = "Garmin"),
                Source(id = SourceId("fitbit"), kind = SourceKind.FITBIT, label = "Fitbit"),
            ),
        )
        val today = container.clock.now().toLocalDateTime(container.zone).date
        val at = today.atStartOfDayIn(container.zone)
        store.observations.recordAll(
            listOf(
                Triple("garmin", ObservationType.STEPS, 8_400.0),
                Triple("fitbit", ObservationType.STEPS, 6_100.0),
                Triple("garmin", ObservationType.RESTING_HEART_RATE, 48.0),
                Triple("fitbit", ObservationType.RESTING_HEART_RATE, 61.0),
            ).map { (source, type, raw) ->
                Observation(
                    id = ObservationId("$source-$type"),
                    type = type,
                    value = Measure(raw, type.canonicalUnit),
                    start = at,
                    end = null,
                    provenance = Provenance(
                        sourceId = SourceId(source),
                        time = TimeProvenance.of(at, at, container.zone),
                        idempotencyKey = "$source-$type-$today",
                    ),
                )
            },
        )

        val model = ReviewPresenter(context, store, container.clock, container.zone)
            .model(preferences())
        val copy = model.rows.flatMap { row ->
            listOf(row.title, row.subtitle) +
                row.readings.map { it.valueText } +
                row.actions.map { it.label }
        }
        assertTrue(copy.isNotEmpty(), "the inbox must have something to read")
        copy.forEach { text ->
            assertTrue(
                '{' !in text && '[' !in text && "/min" !in text && "Cel" !in text,
                "a UCUM code reached the screen: \"$text\"",
            )
        }
    }

    private fun preferences() = Preferences(
        onboardingComplete = true,
        weightNeutral = false,
        modules = HealthModule.entries.toSet(),
        preset = ModulePreset.EVERYTHING,
        theme = ThemeMode.DARK,
        accent = OhAccent.entries.first(),
        appLock = false,
        demo = false,
        highContrast = false,
        reduceTransparency = false,
        reducedMotion = false,
    )

    private companion object {
        const val TEST_DATABASE = "review-presenter.db"
        const val TEST_VAULT = "review-presenter-vault"
    }
}
