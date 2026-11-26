package app.openhealth.core.review

import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * `pr.md` §6.28: the inbox asks about probable duplicates, values outside the plausible
 * screen and days where two sources disagree. It never sums, never picks a winner and
 * never edits: those are the rollup engine's and the person's decisions.
 */
class ReviewInboxTest {
    private val zone = TimeZone.of("Europe/Berlin")
    private val noon = Instant.parse("2026-11-25T11:00:00Z")
    private val day = LocalDate(2026, 11, 25)
    private val labels = mapOf("garmin" to "Garmin", "fitbit" to "Fitbit", "manual" to "Manual")

    private fun observation(
        id: String,
        source: String,
        value: Double,
        type: ObservationType = ObservationType.STEPS,
        at: Instant = noon,
        excluded: Boolean = false,
    ): Observation = Observation(
        id = ObservationId(id),
        type = type,
        value = Measure(value, type.canonicalUnit),
        start = at,
        end = null,
        provenance = Provenance(
            sourceId = SourceId(source),
            time = TimeProvenance.of(at, at, zone),
            idempotencyKey = "key:$id",
        ),
        excludedFromTrends = excluded,
    )

    @Test
    fun twoSourcesThatAgreeAreNotAQuestion() {
        val items = ReviewInbox.build(
            listOf(
                observation("a", "garmin", 8_000.0),
                observation("b", "fitbit", 8_100.0),
            ),
            labels,
        )
        assertTrue(items.none { it.kind == ReviewKind.DISAGREEMENT }, "$items")
    }

    @Test
    fun twoSourcesThatDifferByMoreThanFivePercentAreOneQuestion() {
        val items = ReviewInbox.build(
            listOf(
                observation("a", "garmin", 8_400.0),
                observation("b", "fitbit", 6_100.0),
            ),
            labels,
            winnerBy = { _, _ -> "garmin" },
        )
        val disagreement = items.single { it.kind == ReviewKind.DISAGREEMENT }
        assertEquals(ObservationType.STEPS, disagreement.type)
        assertEquals("garmin", disagreement.winnerSourceId)
        val labels = disagreement.readings.map { it.sourceLabel }.toSet()
        assertEquals(setOf("Garmin", "Fitbit"), labels)
        assertEquals(2, disagreement.readings.size)
    }

    @Test
    fun oneSourceNeverDisagreesWithItself() {
        val items = ReviewInbox.build(
            listOf(
                observation("a", "garmin", 8_400.0),
                observation("b", "garmin", 6_100.0, at = Instant.parse("2026-11-25T18:00:00Z")),
            ),
            labels,
        )
        assertTrue(items.none { it.kind == ReviewKind.DISAGREEMENT }, "$items")
    }

    @Test
    fun anExcludedReadingIsNotAskedAboutAgain() {
        val items = ReviewInbox.build(
            listOf(observation("a", "garmin", 900_000.0, excluded = true)),
            labels,
        )
        assertTrue(items.isEmpty(), "$items")
    }

    @Test
    fun aValueOutsideThePlausibleScreenIsFlaggedAndCarriesItsRange() {
        val items = ReviewInbox.build(
            listOf(observation("a", "garmin", 800_000.0, type = ObservationType.WEIGHT)),
            labels,
        )
        val item = items.single { it.kind == ReviewKind.OUTSIDE_RANGE }
        assertEquals(1.0, item.range?.min)
        assertEquals(500.0, item.range?.max)
        assertEquals(ObservationType.WEIGHT, item.type)
    }

    @Test
    fun aReadingInPlausibleRangeIsNeverAQuestion() {
        val items = ReviewInbox.build(
            listOf(observation("a", "garmin", 82.0, type = ObservationType.WEIGHT)),
            labels,
        )
        assertNull(items.firstOrNull { it.kind == ReviewKind.OUTSIDE_RANGE })
    }

    @Test
    fun twoReadingsAMinuteApartWithTheSameValueAreADuplicate() {
        val items = ReviewInbox.build(
            listOf(
                observation("a", "garmin", 8_412.0, at = noon),
                observation(
                    "b",
                    "garmin",
                    8_412.0,
                    at = Instant.parse("2026-11-25T11:01:00Z"),
                ),
            ),
            labels,
        )
        val duplicate = items.single { it.kind == ReviewKind.DUPLICATE }
        // The kept reading is shown first so the sheet can state the gap; only "b" moves.
        assertEquals(listOf("a", "b"), duplicate.readings.map { it.observationId })
        assertEquals(listOf("b"), duplicate.mergeIds)
    }

    @Test
    fun aHeartRateStreamIsNotADuplicate() {
        val items = ReviewInbox.build(
            listOf(
                observation(
                    "a",
                    "garmin",
                    62.0,
                    type = ObservationType.HEART_RATE,
                    at = noon,
                ),
                observation(
                    "b",
                    "garmin",
                    62.0,
                    type = ObservationType.HEART_RATE,
                    at = Instant.parse("2026-11-25T11:00:01Z"),
                ),
            ),
            labels,
        )
        assertTrue(items.none { it.kind == ReviewKind.DUPLICATE }, "$items")
    }

    @Test
    fun twoReadingsAnHourApartAreNotADuplicate() {
        val items = ReviewInbox.build(
            listOf(
                observation("a", "garmin", 8_412.0, at = noon),
                observation("b", "garmin", 8_412.0, at = Instant.parse("2026-11-25T12:30:00Z")),
            ),
            labels,
        )
        assertTrue(items.none { it.kind == ReviewKind.DUPLICATE }, "$items")
    }

    @Test
    fun aKeyIsStableForTheSameQuestion() {
        val readings = listOf(
            observation("a", "garmin", 9_000.0),
            observation("b", "fitbit", 6_000.0),
        )
        val first = ReviewInbox.build(readings, labels)
        val second = ReviewInbox.build(readings.reversed(), labels)
        assertEquals(first.map { it.key }, second.map { it.key })
        assertEquals(day, first.first().localDate)
    }
}
