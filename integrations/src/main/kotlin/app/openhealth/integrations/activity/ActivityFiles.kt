@file:Suppress(
    "MagicNumber",
    "CyclomaticComplexMethod",
    "LongMethod",
    "LongParameterList",
    "ThrowsCount",
    "TooGenericExceptionCaught",
)

package app.openhealth.integrations.activity

import app.openhealth.core.activity.ActivityPoint
import app.openhealth.core.activity.ActivityRecord
import app.openhealth.core.activity.ActivityType
import app.openhealth.core.activity.Geo
import app.openhealth.integrations.fit.FitImport
import app.openhealth.integrations.fit.FitImporter
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.w3c.dom.Document
import org.w3c.dom.Element

/** A file that could not be read, with a message fit to show a person. */
class ActivityFileException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

/** One lap the file declared. Splits are computed from the route when a file has none. */
data class ImportedLap(
    val index: Int,
    val start: Instant,
    val end: Instant?,
    val distanceM: Double?,
    val durationSeconds: Double?,
    val averageHeartRate: Double?,
    val maximumHeartRate: Double?,
)

/**
 * One activity read from a GPX, TCX or FIT file, before it becomes a stored record. The
 * importer keeps the route, the sensors and the laps (`pr.md` §6.11).
 */
data class ImportedActivity(
    val type: ActivityType,
    val name: String?,
    val startedAt: Instant,
    val endedAt: Instant,
    val distanceM: Double,
    val calories: Double? = null,
    val averageHeartRate: Double? = null,
    val maximumHeartRate: Double? = null,
    val averageCadence: Double? = null,
    val averagePowerWatts: Double? = null,
    val device: String? = null,
    val points: List<ActivityPoint> = emptyList(),
    val laps: List<ImportedLap> = emptyList(),
) {
    init {
        require(endedAt >= startedAt) { "an activity cannot end before it starts" }
        require(distanceM >= 0.0) { "a distance cannot be negative" }
    }
}

/** Dispatch by extension so one picker handles GPX, TCX and FIT. */
object ActivityFiles {
    fun import(fileName: String, bytes: ByteArray): List<ImportedActivity> {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".gpx") -> listOf(GpxReader.read(bytes))
            lower.endsWith(".tcx") -> TcxReader.read(bytes)
            lower.endsWith(".fit") -> FitActivityReader.read(bytes)
            else -> throw ActivityFileException("Only GPX, TCX and FIT files can be imported")
        }
    }
}

/** Small, namespace-tolerant DOM helpers shared by the two XML readers. */
internal object Xml {
    fun parse(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance()
        runCatching {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        runCatching { factory.isXIncludeAware = false }
        runCatching { factory.isExpandEntityReferences = false }
        return try {
            factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        } catch (exception: Exception) {
            throw ActivityFileException(
                "The file is not readable XML: ${exception.message}",
                exception,
            )
        }
    }

    fun elements(root: Element, name: String): List<Element> {
        val nodes = root.getElementsByTagName(name)
        if (nodes.length > 0) {
            return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
        }
        return descendants(root).filter { it.tagName.substringAfter(':') == name }
    }

    fun child(element: Element, name: String): Element? =
        (0 until element.childNodes.length).mapNotNull { element.childNodes.item(it) as? Element }
            .firstOrNull { it.tagName == name || it.tagName.substringAfter(':') == name }

    fun text(element: Element, name: String): String? =
        child(element, name)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    fun double(element: Element, name: String): Double? = text(element, name)?.toDoubleOrNull()

    fun descendantsWithLocalName(root: Element, name: String): List<Element> =
        descendants(root).filter { it.tagName.substringAfter(':') == name }

    private fun descendants(root: Element): List<Element> {
        val nodes = root.getElementsByTagName("*")
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }
}

/**
 * GPX 1.0/1.1 reader. A track point's optional heart rate and cadence may live in the
 * Garmin/TrackPointExtension namespace, so an extension is read by its local name.
 */
object GpxReader {
    fun read(bytes: ByteArray): ImportedActivity {
        val document = Xml.parse(bytes)
        val root = document.documentElement
            ?: throw ActivityFileException("The GPX has no root element")
        val points = Xml.elements(root, "trkpt").map { element ->
            val latitude = element.getAttribute("lat").toDoubleOrNull()
            val longitude = element.getAttribute("lon").toDoubleOrNull()
            if (latitude == null || longitude == null) {
                throw ActivityFileException("A track point has no latitude or longitude")
            }
            GpxPoint(
                latitude = latitude,
                longitude = longitude,
                time = Xml.text(element, "time")?.let {
                    runCatching { Instant.parse(it) }.getOrNull()
                },
                elevation = Xml.double(element, "ele"),
                heartRate = firstExtensionDouble(element, "hr"),
                cadence = firstExtensionDouble(element, "cad"),
                power = firstExtensionDouble(element, "power"),
                speed = firstExtensionDouble(element, "speed"),
            )
        }
        if (points.isEmpty()) throw ActivityFileException("The GPX has no track points")
        val startedAt = points.firstNotNullOfOrNull { it.time }
            ?: Xml.text(root, "time")?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: throw ActivityFileException("The GPX has no time on any track point")
        val resolved = points.mapIndexed { index, point ->
            ActivityPoint(
                latitude = point.latitude,
                longitude = point.longitude,
                elapsedSeconds = point.time?.let {
                    (it - startedAt).inWholeSeconds.coerceAtLeast(0)
                } ?: index.toLong(),
                elevationM = point.elevation,
                heartRate = point.heartRate,
                cadence = point.cadence,
                powerWatts = point.power,
                speedMps = point.speed,
            )
        }
        val endedAt = points.mapNotNull { it.time }.maxOrNull()
            ?: startedAt.plus(resolved.last().elapsedSeconds.seconds)
        val track = Xml.elements(root, "trk").firstOrNull()
        val name = track?.let { Xml.text(it, "name") } ?: Xml.text(root, "name")
        val typeText = track?.let { Xml.text(it, "type") } ?: Xml.text(root, "type")
        return summarize(
            type = ActivityType.fromSportName(typeText),
            name = name,
            startedAt = startedAt,
            endedAt = endedAt,
            distanceM = Geo.routeDistanceMeters(resolved),
            points = resolved,
            device = root.getAttribute("creator").takeIf { it.isNotBlank() },
        )
    }

    private fun firstExtensionDouble(element: Element, name: String): Double? =
        Xml.descendantsWithLocalName(element, name).firstOrNull()
            ?.textContent?.trim()?.toDoubleOrNull()

    private data class GpxPoint(
        val latitude: Double,
        val longitude: Double,
        val time: Instant?,
        val elevation: Double?,
        val heartRate: Double?,
        val cadence: Double?,
        val power: Double?,
        val speed: Double?,
    )
}

/**
 * TCX reader. One file normally holds one activity; each `Lap` becomes a lap and every
 * `Trackpoint` becomes a route point with its sensors.
 */
object TcxReader {
    fun read(bytes: ByteArray): List<ImportedActivity> {
        val document = Xml.parse(bytes)
        val root = document.documentElement
            ?: throw ActivityFileException("The TCX has no root element")
        val activities = Xml.elements(root, "Activity")
        if (activities.isEmpty()) throw ActivityFileException("The TCX has no activity")
        return activities.mapNotNull { activity -> readActivity(activity) }
    }

    private fun readActivity(activity: Element): ImportedActivity? {
        val laps = Xml.elements(activity, "Lap")
        val rawPoints = laps.flatMap { lap -> Xml.elements(lap, "Trackpoint") }
        val parsed = rawPoints.mapNotNull { point ->
            val time = Xml.text(point, "Time")?.let {
                runCatching { Instant.parse(it) }.getOrNull()
            } ?: return@mapNotNull null
            val position = Xml.child(point, "Position")
            val latitude = position?.let { Xml.double(it, "LatitudeDegrees") }
            val longitude = position?.let { Xml.double(it, "LongitudeDegrees") }
            if (latitude == null || longitude == null) return@mapNotNull null
            TcxPoint(
                time = time,
                latitude = latitude,
                longitude = longitude,
                elevation = Xml.double(point, "AltitudeMeters"),
                heartRate = Xml.child(point, "HeartRateBpm")?.let { Xml.double(it, "Value") },
                cadence = Xml.double(point, "Cadence"),
                power = Xml.child(point, "Extensions")?.let { extensionPower(it) },
                speed = Xml.child(point, "Extensions")?.let { extensionSpeed(it) },
            )
        }
        if (parsed.isEmpty()) return null
        val startedAt = parsed.minOf { it.time }
        val points = parsed.sortedBy { it.time }.map { point ->
            ActivityPoint(
                latitude = point.latitude,
                longitude = point.longitude,
                elapsedSeconds = (point.time - startedAt).inWholeSeconds.coerceAtLeast(0),
                elevationM = point.elevation,
                heartRate = point.heartRate,
                cadence = point.cadence,
                powerWatts = point.power,
                speedMps = point.speed,
            )
        }
        val importedLaps = laps.mapIndexedNotNull { index, lap ->
            val start = lap.getAttribute("StartTime").takeIf { it.isNotBlank() }
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: return@mapIndexedNotNull null
            val duration = Xml.double(lap, "TotalTimeSeconds")
            ImportedLap(
                index = index + 1,
                start = start,
                end = duration?.let { start.plus((it * 1_000).toLong().milliseconds) },
                distanceM = Xml.double(lap, "DistanceMeters"),
                durationSeconds = duration,
                averageHeartRate = Xml.child(lap, "AverageHeartRateBpm")
                    ?.let { Xml.double(it, "Value") },
                maximumHeartRate = Xml.child(lap, "MaximumHeartRateBpm")
                    ?.let { Xml.double(it, "Value") },
            )
        }
        val endedAt = maxOf(
            parsed.maxOf { it.time },
            importedLaps.mapNotNull { it.end }.maxOrNull() ?: parsed.maxOf { it.time },
        )
        val distance = importedLaps.sumOf { it.distanceM ?: 0.0 }
            .takeIf { it > 0.0 } ?: Geo.routeDistanceMeters(points)
        return summarize(
            type = ActivityType.fromSportName(
                activity.getAttribute("Sport").takeIf { it.isNotBlank() }
                    ?: Xml.text(activity, "Sport"),
            ),
            name = Xml.text(activity, "Notes"),
            startedAt = startedAt,
            endedAt = endedAt,
            distanceM = distance,
            points = points,
            laps = importedLaps,
        )
    }

    private fun extensionPower(extensions: Element): Double? =
        Xml.descendantsWithLocalName(extensions, "Watts").firstOrNull()
            ?.textContent?.trim()?.toDoubleOrNull()
            ?: Xml.descendantsWithLocalName(extensions, "Power").firstOrNull()
                ?.textContent?.trim()?.toDoubleOrNull()

    private fun extensionSpeed(extensions: Element): Double? =
        Xml.descendantsWithLocalName(extensions, "Speed").firstOrNull()
            ?.textContent?.trim()?.toDoubleOrNull()

    private data class TcxPoint(
        val time: Instant,
        val latitude: Double,
        val longitude: Double,
        val elevation: Double?,
        val heartRate: Double?,
        val cadence: Double?,
        val power: Double?,
        val speed: Double?,
    )
}

/** FIT activity reader: the route, laps and sensors the FIT importer already decoded. */
object FitActivityReader {
    fun read(bytes: ByteArray): List<ImportedActivity> = from(FitImporter.import(bytes))

    fun from(import: FitImport): List<ImportedActivity> {
        if (import.activities.isEmpty()) {
            throw ActivityFileException("The FIT file has no session to import")
        }
        return import.activities.map { activity ->
            val points = import.track
                .filter { it.at >= activity.start && it.at <= activity.end && it.hasPosition }
                .map {
                    ActivityPoint(
                        latitude = it.latitude!!,
                        longitude = it.longitude!!,
                        elapsedSeconds = (it.at - activity.start).inWholeSeconds.coerceAtLeast(0),
                        elevationM = it.altitudeMetres,
                        heartRate = it.heartRate?.toDouble(),
                        cadence = it.cadence?.toDouble(),
                        powerWatts = it.powerWatts?.toDouble(),
                        speedMps = it.speedMetresPerSecond,
                    )
                }
            val distance = activity.distanceMetres?.takeIf { it > 0.0 }
                ?: Geo.routeDistanceMeters(points)
            summarize(
                type = fromSport(activity.sport),
                name = activity.name,
                startedAt = activity.start,
                endedAt = activity.end,
                distanceM = distance,
                points = points,
                laps = activity.laps.mapIndexed { index, lap ->
                    ImportedLap(
                        index = index + 1,
                        start = lap.start,
                        end = lap.end,
                        distanceM = lap.distanceMetres,
                        durationSeconds = lap.durationSeconds,
                        averageHeartRate = lap.averageHeartRate?.toDouble(),
                        maximumHeartRate = lap.maximumHeartRate?.toDouble(),
                    )
                },
                calories = activity.calories,
                averageHeartRate = activity.averageHeartRate?.toDouble(),
                maximumHeartRate = activity.maximumHeartRate?.toDouble(),
                averageCadence = activity.averageCadence?.toDouble(),
                averagePowerWatts = activity.averagePowerWatts?.toDouble(),
                device = import.device,
            )
        }
    }

    private fun fromSport(sport: Long?): ActivityType = when (sport) {
        1L -> ActivityType.RUN
        2L -> ActivityType.RIDE
        5L -> ActivityType.SWIM
        11L -> ActivityType.WALK
        12L, 13L -> ActivityType.SKI
        15L -> ActivityType.ROW
        17L -> ActivityType.HIKE
        else -> ActivityType.OTHER
    }
}

@Suppress("LongParameterList")
private fun summarize(
    type: ActivityType,
    name: String?,
    startedAt: Instant,
    endedAt: Instant,
    distanceM: Double,
    points: List<ActivityPoint>,
    laps: List<ImportedLap> = emptyList(),
    calories: Double? = null,
    averageHeartRate: Double? = null,
    maximumHeartRate: Double? = null,
    averageCadence: Double? = null,
    averagePowerWatts: Double? = null,
    device: String? = null,
): ImportedActivity {
    val heartRates = points.mapNotNull { it.heartRate }.filter { it > 0.0 }
    val cadences = points.mapNotNull { it.cadence }.filter { it > 0.0 }
    val powers = points.mapNotNull { it.powerWatts }.filter { it > 0.0 }
    return ImportedActivity(
        type = type,
        name = name,
        startedAt = startedAt,
        endedAt = if (endedAt > startedAt) endedAt else startedAt,
        distanceM = distanceM,
        calories = calories,
        averageHeartRate = averageHeartRate
            ?: if (heartRates.isEmpty()) null else heartRates.average(),
        maximumHeartRate = maximumHeartRate ?: heartRates.maxOrNull(),
        averageCadence = averageCadence
            ?: if (cadences.isEmpty()) null else cadences.average(),
        averagePowerWatts = averagePowerWatts
            ?: if (powers.isEmpty()) null else powers.average(),
        device = device,
        points = points,
        laps = laps,
    )
}

/** A compact GPX writer. The output validates against the GPX 1.1 schema. */
object GpxWriter {
    private const val GPX_NS = "http://www.topografix.com/GPX/1/1"
    private const val GPX_TPX_NS = "http://www.garmin.com/xmlschemas/TrackPointExtension/v1"

    fun write(activity: ActivityRecord): String {
        val builder = StringBuilder(4_096)
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        builder.append("<gpx version=\"1.1\" creator=\"openHealth\"")
        builder.append(" xmlns=\"").append(GPX_NS).append('"')
        builder.append(" xmlns:gpxtpx=\"").append(GPX_TPX_NS).append("\">\n")
        builder.append(
            "  <metadata><time>",
        ).append(activity.startedAt).append("</time></metadata>\n")
        builder.append("  <trk>\n")
        builder.append(
            "    <name>",
        ).append(escape(activity.name ?: activity.type.name)).append("</name>\n")
        builder.append(
            "    <type>",
        ).append(escape(activity.type.name.lowercase())).append("</type>\n")
        builder.append("    <trkseg>\n")
        activity.points.forEach { point ->
            builder.append("      <trkpt lat=\"").append(point.latitude).append('"')
            builder.append(" lon=\"").append(point.longitude).append("\">\n")
            point.elevationM?.let {
                builder.append("        <ele>").append(it).append("</ele>\n")
            }
            val time = activity.startedAt.plus(point.elapsedSeconds.seconds)
            builder.append("        <time>").append(time).append("</time>\n")
            if (point.heartRate != null || point.cadence != null) {
                builder.append("        <extensions><gpxtpx:TrackPointExtension>")
                point.heartRate?.let {
                    builder.append("<gpxtpx:hr>").append(it.toInt()).append("</gpxtpx:hr>")
                }
                point.cadence?.let {
                    builder.append("<gpxtpx:cad>").append(it.toInt()).append("</gpxtpx:cad>")
                }
                builder.append("</gpxtpx:TrackPointExtension></extensions>\n")
            }
            builder.append("      </trkpt>\n")
        }
        builder.append("    </trkseg>\n  </trk>\n</gpx>\n")
        return builder.toString()
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

/**
 * Garmin Training Center XML for one recorded activity (`pr.md` §6.11): one lap, one track,
 * with time, position, altitude, running distance and heart rate on each point. Every
 * reader that takes a TCX takes this; `TcxReader` in this file reads it back.
 */
object TcxWriter {
    private const val TCX_NS = "http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"

    fun write(activity: ActivityRecord): String {
        val sport = when (activity.type) {
            ActivityType.RUN -> "Running"
            ActivityType.RIDE -> "Biking"
            else -> "Other"
        }
        val builder = StringBuilder(4_096)
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        builder.append("<TrainingCenterDatabase xmlns=\"").append(TCX_NS).append("\">\n")
        builder.append("  <Activities>\n    <Activity Sport=\"").append(sport).append("\">\n")
        builder.append("      <Id>").append(activity.startedAt).append("</Id>\n")
        builder.append("      <Lap StartTime=\"").append(activity.startedAt).append("\">\n")
        builder.append("        <TotalTimeSeconds>").append(activity.durationSeconds)
            .append("</TotalTimeSeconds>\n")
        builder.append("        <DistanceMeters>").append(activity.distanceM)
            .append("</DistanceMeters>\n")
        activity.energyKcal?.let {
            builder.append("        <Calories>").append(it.toInt()).append("</Calories>\n")
        }
        builder.append("        <Intensity>Active</Intensity>\n")
        builder.append("        <TriggerMethod>Manual</TriggerMethod>\n")
        builder.append("        <Track>\n")
        var distance = 0.0
        var previous: ActivityPoint? = null
        activity.points.forEach { point ->
            previous?.let {
                distance += Geo.distanceMeters(
                    it.latitude,
                    it.longitude,
                    point.latitude,
                    point.longitude,
                )
            }
            previous = point
            builder.append("          <Trackpoint>\n")
            builder.append("            <Time>")
                .append(activity.startedAt.plus(point.elapsedSeconds.seconds))
                .append("</Time>\n")
            builder.append("            <Position><LatitudeDegrees>").append(point.latitude)
                .append("</LatitudeDegrees><LongitudeDegrees>").append(point.longitude)
                .append("</LongitudeDegrees></Position>\n")
            point.elevationM?.let {
                builder.append("            <AltitudeMeters>").append(it)
                    .append("</AltitudeMeters>\n")
            }
            builder.append("            <DistanceMeters>").append(distance)
                .append("</DistanceMeters>\n")
            point.heartRate?.let {
                builder.append("            <HeartRateBpm><Value>").append(it.toInt())
                    .append("</Value></HeartRateBpm>\n")
            }
            builder.append("          </Trackpoint>\n")
        }
        builder.append("        </Track>\n      </Lap>\n    </Activity>\n  </Activities>\n")
        builder.append("</TrainingCenterDatabase>\n")
        return builder.toString()
    }
}
