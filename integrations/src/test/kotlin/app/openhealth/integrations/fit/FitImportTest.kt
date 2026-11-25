package app.openhealth.integrations.fit

import app.openhealth.core.ids.SeededIdGenerator
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.units.UcumUnit
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/**
 * The FIT import against committed golden fixtures (`BUILD-ROADMAP.md` §B8,
 * `Tech-Stack.md` §10). The fixtures are written by `tools/fixtures`, an independent
 * encoder, so these assertions prove the parser, not a round trip through itself.
 */
private const val DAY_START_UNIX_SECONDS = 1_795_420_800L
private const val WALK_START_OFFSET_SECONDS = 9L * 60 * 60

class FitImportTest {

    private fun fixture(name: String): ByteArray {
        val stream: InputStream? = javaClass.getResourceAsStream("/fit/$name")
        assertNotNull(stream, "fixture $name is missing from the test resources")
        return stream.use { it.readBytes() }
    }

    @Test
    fun aWalkImportsItsActivityHeartRateAndSteps() {
        val import = FitImporter.import(fixture("activity-walk.fit"), TimeZone.UTC)

        assertEquals(1, import.activities.size)
        val walk = import.activities.single()
        assertEquals(11L, walk.sport)
        assertEquals(45.0, walk.durationMinutes, 1e-9)
        assertEquals(2385.0, walk.distanceMetres ?: 0.0, 1e-9)
        assertEquals(142.0, walk.calories)
        assertEquals(108, walk.averageHeartRate)
        assertEquals(124, walk.maximumHeartRate)
        assertEquals(3000L, walk.steps)
        assertEquals(FitStepSource.ACTIVITY, import.stepSource)
        assertEquals(3000L, import.stepTotal)
        assertEquals("vivoactive 5", import.device)
        assertEquals(1L, import.manufacturer)
    }

    @Test
    fun theHeartRateSeriesKeepsEveryRecord() {
        val import = FitImporter.import(fixture("activity-walk.fit"), TimeZone.UTC)

        assertEquals(45, import.heartRates.size)
        assertEquals(96, import.heartRates.first().bpm)
        // One reading every minute, +1 bpm every five minutes.
        assertEquals(96 + 44 / 5, import.heartRates.last().bpm)
        val start = Instant.fromEpochSeconds(
            DAY_START_UNIX_SECONDS + WALK_START_OFFSET_SECONDS,
        )
        assertEquals(start, import.heartRates.first().at)
    }

    @Test
    fun aDailyMonitoringFileCountsHalfStepsAsSteps() {
        val import = FitImporter.import(fixture("daily-monitoring.fit"), TimeZone.UTC)

        assertEquals(FitStepSource.MONITORING, import.stepSource)
        assertEquals(18_000L, import.stepTotal)
        // Only the waking buckets carry steps; the sleeping stretch is buckets with none.
        assertEquals(72, import.steps.size)
        assertTrue(import.steps.all { it.steps == 250L })
        assertTrue(import.steps.all { it.end > it.start })
        assertEquals(18_000L, import.steps.sumOf { it.steps })
    }

    @Test
    fun aDailyFileWithBothMessagesDoesNotDoubleCountItsOwnSteps() {
        val walk = fixture("activity-walk.fit")
        val monitoring = fixture("daily-monitoring.fit")
        val mixed = FitImporter.import(monitoring, TimeZone.UTC)

        // A monitoring file's own steps are the monitoring buckets, never summed with a
        // session's cycles as well.
        assertEquals(FitStepSource.MONITORING, mixed.stepSource)
        assertEquals(18_000L, mixed.stepTotal)
        // An activity file has no monitoring, so its session cycles are used.
        assertEquals(FitStepSource.ACTIVITY, FitImporter.import(walk).stepSource)
        assertEquals(3000L, FitImporter.import(walk).stepTotal)
    }

    @Test
    fun aBigEndianDefinitionIsDecodedWithItsArchitecture() {
        val import = FitImporter.import(fixture("big-endian.fit"), TimeZone.UTC)

        assertEquals(1, import.heartRates.size)
        assertEquals(77, import.heartRates.single().bpm)
        assertEquals(
            Instant.fromEpochSeconds(DAY_START_UNIX_SECONDS),
            import.heartRates.single().at,
        )
    }

    @Test
    fun aCompressedTimestampIsRelativeToTheLastTimestamp() {
        val import = FitImporter.import(fixture("compressed-timestamp.fit"), TimeZone.UTC)

        assertEquals(2, import.heartRates.size)
        val base = Instant.fromEpochSeconds(DAY_START_UNIX_SECONDS)
        assertEquals(base, import.heartRates[0].at)
        assertEquals(base + 3.seconds, import.heartRates[1].at)
        assertEquals(88, import.heartRates[0].bpm)
        assertEquals(91, import.heartRates[1].bpm)
    }

    @Test
    fun aCorruptChecksumIsRejectedRatherThanImported() {
        assertFailsWith<FitFormatException> {
            FitImporter.import(fixture("corrupt-crc.fit"), TimeZone.UTC)
        }
    }

    @Test
    fun aFileThatIsNotFitIsRejected() {
        assertFailsWith<FitFormatException> {
            FitImporter.import(ByteArray(64) { 0x2A }, TimeZone.UTC)
        }
        assertFailsWith<FitFormatException> {
            FitImporter.import(byteArrayOf(12, 0x20, 0, 0, 0, 0), TimeZone.UTC)
        }
    }

    @Test
    fun aTruncatedFileIsRejected() {
        val bytes = fixture("activity-walk.fit")
        assertFailsWith<FitFormatException> {
            FitImporter.import(bytes.copyOfRange(0, bytes.size / 2), TimeZone.UTC)
        }
    }

    @Test
    fun observationsKeepHappenedAtAndRecordedAtApart() {
        val import = FitImporter.import(fixture("activity-walk.fit"), TimeZone.UTC)
        val recordedAt = Instant.fromEpochSeconds(DAY_START_UNIX_SECONDS + 3 * 86_400)
        val observations = FitObservationMapper.toObservations(
            import,
            FitImportContext(
                sourceId = SourceId("garmin-fit"),
                zone = TimeZone.UTC,
                recordedAt = recordedAt,
                device = import.device,
                ids = SeededIdGenerator(7),
            ),
        )

        val steps = observations.filter { it.type == ObservationType.STEPS }
        assertEquals(1, steps.size)
        assertEquals(3000.0, steps.single().value.convertTo(UcumUnit.STEP).value, 1e-9)
        // The walk happened in the morning; it was imported the next day.
        assertEquals(
            Instant.fromEpochSeconds(DAY_START_UNIX_SECONDS + WALK_START_OFFSET_SECONDS),
            steps.single().start,
        )
        assertEquals(recordedAt, steps.single().provenance.recordedAt)
        assertEquals("garmin-fit", steps.single().provenance.sourceId.value)
        assertTrue(steps.single().provenance.isBackfilled)

        assertEquals(45, observations.count { it.type == ObservationType.HEART_RATE })
        val activity = observations.filter {
            it.type == ObservationType.ACTIVE_MINUTES && it.end != null
        }
        assertEquals(1, activity.size)
        assertEquals(45.0, activity.single().value.convertTo(UcumUnit.MINUTE).value, 1e-6)
        assertEquals(
            observations.filter { it.groupKey != null }.map { it.groupKey }.distinct().size,
            1,
        )
    }

    @Test
    fun reimportingTheSameFileProducesTheSameIdempotencyKeys() {
        val import = FitImporter.import(fixture("activity-walk.fit"), TimeZone.UTC)
        val context = FitImportContext(
            sourceId = SourceId("garmin-fit"),
            zone = TimeZone.UTC,
            recordedAt = Instant.fromEpochSeconds(DAY_START_UNIX_SECONDS + 3_600),
            ids = SeededIdGenerator(1),
        )
        val first = FitObservationMapper.toObservations(import, context)
        val second = FitObservationMapper.toObservations(
            import,
            context.copy(ids = SeededIdGenerator(99)),
        )

        assertEquals(
            first.map { it.provenance.idempotencyKey },
            second.map { it.provenance.idempotencyKey },
        )
        assertEquals(first.size, first.map { it.provenance.idempotencyKey }.distinct().size)
    }
}
