package app.openhealth.data

import app.openhealth.core.ai.CoachCategory
import app.openhealth.core.ai.CoachConsent
import app.openhealth.core.ai.CoachPrompt
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.MeasurementMethod
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.seam.FixedClock
import app.openhealth.core.source.Source
import app.openhealth.core.source.SourcePermission
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.LocalStore
import app.openhealth.data.repo.CoachDataBuilder
import app.openhealth.data.repo.CoachPlanApplier
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The Health Connect flag has a real producer: the source row. A Garmin watch that
 * arrived through the platform bus is kind GARMIN but still flagged, so its weight is
 * excluded from a provider request unless the separate opt-in is on (`pr.md` §12.3).
 */
class CoachDataSourceTest {
    private val now = Instant.parse("2026-11-27T09:00:00Z")
    private val clock = FixedClock(now)

    @Test
    fun theFlagRoundTripsThroughTheSourceRegistry() {
        val opened = inMemoryStore()
        val store = LocalStore(opened.driver, opened.db)
        store.sources.upsert(
            Source(
                id = SourceId("garmin-hc"),
                kind = app.openhealth.core.observation.SourceKind.GARMIN,
                label = "Garmin via Health Connect",
                family = "garmin",
                permissions = setOf(SourcePermission.READ_WEIGHT),
                viaHealthConnect = true,
            ),
        )
        assertTrue(store.sources.byId(SourceId("garmin-hc"))!!.viaHealthConnect)
        assertFalse(store.sources.byId(SourceId("manual"))!!.viaHealthConnect)
        store.close()
    }

    @Test
    fun aHealthConnectWeightIsExcludedUntilTheSecondSwitch() {
        val opened = inMemoryStore()
        val store = LocalStore(opened.driver, opened.db)
        store.sources.upsert(
            Source(
                id = SourceId("garmin-hc"),
                kind = app.openhealth.core.observation.SourceKind.GARMIN,
                label = "Garmin via Health Connect",
                family = "garmin",
                permissions = setOf(SourcePermission.READ_WEIGHT),
                viaHealthConnect = true,
            ),
        )
        store.observations.record(
            Observation.of(
                id = app.openhealth.core.ids.ObservationId("hc-weight"),
                type = ObservationType.WEIGHT,
                value = Measure(74.0, UcumUnit.KILOGRAM),
                start = now,
                provenance = Provenance(
                    sourceId = SourceId("garmin-hc"),
                    time = TimeProvenance.of(now, now, TEST_ZONE),
                    idempotencyKey = "hc-weight",
                    method = MeasurementMethod.SCALE,
                ),
            ),
        )
        val builder = CoachDataBuilder(
            store,
            CoachPlanApplier(store.db, store.training, store.coach, clock),
            TEST_ZONE,
            clock,
        )
        val dataset = builder.dataset()
        assertTrue(dataset.vitals.single { it.type == ObservationType.WEIGHT }.viaHealthConnect)

        val without = CoachPrompt.build(
            dataset,
            CoachConsent(enabled = true, categories = setOf(CoachCategory.VITALS)),
            weightNeutral = false,
            note = "Review.",
        ).user
        assertFalse(without.contains("74.0"))

        val with = CoachPrompt.build(
            dataset,
            CoachConsent(
                enabled = true,
                categories = setOf(CoachCategory.VITALS),
                healthConnect = true,
            ),
            weightNeutral = false,
            note = "Review.",
        ).user
        assertTrue(with.contains("74.0"))
        store.close()
    }
}
