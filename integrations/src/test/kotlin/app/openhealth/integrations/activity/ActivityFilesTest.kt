package app.openhealth.integrations.activity

import app.openhealth.core.activity.ActivityPoint
import app.openhealth.core.activity.ActivityRecord
import app.openhealth.core.activity.ActivitySource
import app.openhealth.core.activity.ActivityType
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class ActivityFilesTest {
    private val start = Instant.parse("2026-11-24T08:00:00Z")

    private fun fixture(name: String): ByteArray {
        val stream: InputStream? = javaClass.getResourceAsStream("/fit/$name")
        assertNotNull(stream, "fixture $name is missing")
        return stream.use { it.readBytes() }
    }

    private fun gpx(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="openHealth test"
             xmlns="http://www.topografix.com/GPX/1/1"
             xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">
          <trk>
            <name>Morning loop</name>
            <type>running</type>
            <trkseg>
              <trkpt lat="52.5200" lon="13.4050"><ele>34.0</ele>
                <time>2026-11-24T08:00:00Z</time>
                <extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>140</gpxtpx:hr>
                  <gpxtpx:cad>82</gpxtpx:cad></gpxtpx:TrackPointExtension></extensions>
              </trkpt>
              <trkpt lat="52.5210" lon="13.4055"><ele>36.0</ele>
                <time>2026-11-24T08:01:00Z</time>
                <extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>150</gpxtpx:hr>
                  <gpxtpx:cad>84</gpxtpx:cad></gpxtpx:TrackPointExtension></extensions>
              </trkpt>
              <trkpt lat="52.5220" lon="13.4060"><ele>35.0</ele>
                <time>2026-11-24T08:02:00Z</time>
              </trkpt>
            </trkseg>
          </trk>
        </gpx>
    """.trimIndent()

    private fun tcx(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
          <Activities><Activity Sport="Biking">
            <Id>2026-11-24T08:00:00Z</Id>
            <Lap StartTime="2026-11-24T08:00:00Z">
              <TotalTimeSeconds>600</TotalTimeSeconds>
              <DistanceMeters>4000</DistanceMeters>
              <AverageHeartRateBpm><Value>132</Value></AverageHeartRateBpm>
              <MaximumHeartRateBpm><Value>155</Value></MaximumHeartRateBpm>
              <Track>
                <Trackpoint><Time>2026-11-24T08:00:00Z</Time>
                  <Position><LatitudeDegrees>52.5200</LatitudeDegrees>
                    <LongitudeDegrees>13.4050</LongitudeDegrees></Position>
                  <AltitudeMeters>34</AltitudeMeters>
                  <HeartRateBpm><Value>120</Value></HeartRateBpm>
                  <Cadence>80</Cadence>
                  <Extensions><TPX xmlns="http://www.garmin.com/xmlschemas/ActivityExtension/v2">
                    <Speed>6.5</Speed><Watts>210</Watts></TPX></Extensions>
                </Trackpoint>
                <Trackpoint><Time>2026-11-24T08:05:00Z</Time>
                  <Position><LatitudeDegrees>52.5250</LatitudeDegrees>
                    <LongitudeDegrees>13.4090</LongitudeDegrees></Position>
                  <AltitudeMeters>38</AltitudeMeters>
                  <HeartRateBpm><Value>140</Value></HeartRateBpm>
                  <Cadence>82</Cadence>
                </Trackpoint>
              </Track>
            </Lap>
          </Activity></Activities>
        </TrainingCenterDatabase>
    """.trimIndent()

    @Test
    fun gpxReadsItsRouteTypeAndSensors() {
        val activity = GpxReader.read(gpx().toByteArray())
        assertEquals(ActivityType.RUN, activity.type)
        assertEquals("Morning loop", activity.name)
        assertEquals(3, activity.points.size)
        assertEquals(0, activity.points.first().elapsedSeconds)
        assertEquals(60, activity.points[1].elapsedSeconds)
        assertEquals(140.0, activity.points.first().heartRate)
        assertEquals(82.0, activity.points.first().cadence)
        assertTrue(activity.distanceM > 200.0)
        assertTrue(activity.averageHeartRate!! in 145.0..150.0)
        assertEquals("openHealth test", activity.device)
    }

    @Test
    fun gpxReaderRejectsAFileWithNoTrackPoints() {
        assertFailsWith<ActivityFileException> {
            GpxReader.read("<gpx version=\"1.1\"></gpx>".toByteArray())
        }
    }

    @Test
    fun gpxWriterProducesAValidFileThatReadsBack() {
        val record = ActivityRecord(
            id = "run-1",
            type = ActivityType.RUN,
            source = ActivitySource.RECORDED,
            startedAt = start,
            endedAt = start.plus(kotlin.time.Duration.parse("10m")),
            localDate = start.toLocalDateTime(TimeZone.UTC).date,
            zoneId = "UTC",
            distanceM = 1_200.0,
            points = listOf(
                ActivityPoint(52.5200, 13.4050, 0, elevationM = 34.0, heartRate = 140.0),
                ActivityPoint(52.5220, 13.4060, 600, elevationM = 38.0, heartRate = 150.0),
            ),
        )
        val xml = GpxWriter.write(record)
        assertTrue(xml.contains("<gpx version=\"1.1\""))
        assertTrue(xml.contains("gpxtpx:hr>140<"))
        val back = GpxReader.read(xml.toByteArray())
        assertEquals(2, back.points.size)
        assertEquals(140.0, back.points.first().heartRate)
        assertEquals(0, back.points.first().elapsedSeconds)
        assertEquals(600, back.points.last().elapsedSeconds)
        assertEquals(34.0, back.points.first().elevationM!!, 0.001)
    }

    @Test
    fun tcxReadsLapsAndTrackpoints() {
        val activities = TcxReader.read(tcx().toByteArray())
        assertEquals(1, activities.size)
        val activity = activities.single()
        assertEquals(ActivityType.RIDE, activity.type)
        assertEquals(2, activity.points.size)
        assertEquals(1, activity.laps.size)
        assertEquals(4_000.0, activity.laps.first().distanceM)
        assertEquals(132.0, activity.laps.first().averageHeartRate)
        assertEquals(210.0, activity.points.first().powerWatts)
        assertEquals(6.5, activity.points.first().speedMps)
        assertEquals(4_000.0, activity.distanceM)
        assertEquals(600, activity.endedAt.epochSeconds - activity.startedAt.epochSeconds)
    }

    @Test
    fun fitRoutePreservesTheTrackAndSensors() {
        val activities = FitActivityReader.read(fixture("activity-route.fit"))
        val activity = activities.single()
        assertEquals(ActivityType.RUN, activity.type)
        assertEquals(20, activity.points.size)
        assertEquals(0, activity.points.first().elapsedSeconds)
        assertEquals(570, activity.points.last().elapsedSeconds)
        assertTrue(activity.distanceM > 900.0)
        assertTrue(activity.points.first().latitude!! > 52.0)
        assertTrue(activity.averageHeartRate!! > 150.0)
        assertTrue(activity.points.all { it.powerWatts != null })
        assertEquals("vivoactive 5", activity.device)
    }

    @Test
    fun aStravaArchiveImportsItsActivitiesAndCsvMetadata() {
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { output ->
            output.putNextEntry(ZipEntry("activities.csv"))
            output.write(
                """
                Activity ID,Activity Date,Activity Name,Activity Type,Distance,Calories,Average Heart Rate,Max Heart Rate
                1001,"Nov 24, 2026, 8:00:00 AM","Lunch run",Run,2400,190,148,170
                1002,"Nov 24, 2026, 6:00:00 PM","Evening ride",Ride,12000,310,131,160
                """.trimIndent().toByteArray(),
            )
            output.closeEntry()
            output.putNextEntry(ZipEntry("activities/1001.gpx.gz"))
            output.write(gzip(gpx().toByteArray()))
            output.closeEntry()
            output.putNextEntry(ZipEntry("activities/1002.tcx.gz"))
            output.write(gzip(tcx().toByteArray()))
            output.closeEntry()
        }
        val archive = StravaArchive.read(zip.toByteArray())
        assertEquals(2, archive.activities.size)
        assertEquals(2, archive.csvRows)
        assertEquals(emptyList(), archive.skipped)
        val run = archive.activities.first { it.name == "Lunch run" }
        assertEquals(ActivityType.RUN, run.type)
        assertEquals(190.0, run.calories)
        val ride = archive.activities.first { it.name == "Evening ride" }
        assertEquals(ActivityType.RIDE, ride.type)
        assertEquals(12_000.0, ride.distanceM)
        assertEquals(310.0, ride.calories)
    }

    @Test
    fun theCsvReaderHandlesQuotedCommasAndEscapedQuotes() {
        val rows = StravaArchive.parseCsv(
            "Activity ID,Activity Name,Note\n1,\"A, B\",\"He said \"\"go\"\"\"\n",
        )
        assertEquals("A, B", rows.getValue("1")["Activity Name"])
        assertEquals("He said \"go\"", rows.getValue("1")["Note"])
    }

    @Test
    fun aBrokenArchiveSaysSo() {
        assertFailsWith<ActivityFileException> {
            StravaArchive.read("not a zip".toByteArray())
        }
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(bytes) }
        return output.toByteArray()
    }
}

class TcxWriterTest {
    @Test
    fun anExportedActivityReadsBackWithItsRoute() {
        val start = kotlin.time.Instant.parse("2026-11-24T08:00:00Z")
        val points = (0..5).map {
            app.openhealth.core.activity.ActivityPoint(
                latitude = 52.5 + it * 0.001,
                longitude = 13.4,
                elapsedSeconds = it * 60L,
                elevationM = 34.0 + it,
                heartRate = 140.0 + it,
            )
        }
        val record = app.openhealth.core.activity.ActivityRecord(
            id = "a1",
            type = app.openhealth.core.activity.ActivityType.RUN,
            source = app.openhealth.core.activity.ActivitySource.RECORDED,
            startedAt = start,
            endedAt = start + kotlin.time.Duration.parse("5m"),
            localDate = kotlinx.datetime.LocalDate(2026, 11, 24),
            zoneId = "UTC",
            distanceM = 555.0,
            points = points,
        )
        val xml = TcxWriter.write(record)
        val back = ActivityFiles.import("run.tcx", xml.toByteArray()).single()
        kotlin.test.assertEquals(points.size, back.points.size)
        kotlin.test.assertEquals(52.5, back.points.first().latitude, 1e-9)
        kotlin.test.assertEquals(app.openhealth.core.activity.ActivityType.RUN, back.type)
    }
}
