@file:Suppress(
    "MagicNumber",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "ThrowsCount",
)

package app.openhealth.integrations.activity

import app.openhealth.core.activity.ActivityType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * A Strava bulk-export archive, read without Strava's live API (`pr.md` §6.11 V1 and
 * B11). The archive is a ZIP of `activities.csv` and `activities/<id>.gpx.gz`,
 * `activities/<id>.tcx.gz` or `activities/<id>.fit.gz`.
 */
data class StravaArchiveImport(
    val activities: List<ImportedActivity>,
    val entriesSeen: Int,
    val skipped: List<String> = emptyList(),
    val csvRows: Int = 0,
) {
    val isEmpty: Boolean get() = activities.isEmpty()
}

object StravaArchive {
    private const val MAX_ENTRIES = 5_000
    private const val MAX_ENTRY_BYTES = 40 * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 500 * 1024 * 1024

    fun read(bytes: ByteArray): StravaArchiveImport {
        val entries = unzip(bytes)
        val csv = entries.firstOrNull { it.name.substringAfterLast('/') == "activities.csv" }
            ?.let { parseCsv(it.bytes.toString(Charsets.UTF_8)) }
            .orEmpty()
        val activityFiles = entries.filter { entry ->
            val name = entry.name.substringAfterLast('/')
            val lower = name.lowercase()
            lower.endsWith(".gpx") ||
                lower.endsWith(".tcx") ||
                lower.endsWith(".fit") ||
                lower.endsWith(".gpx.gz") ||
                lower.endsWith(".tcx.gz") ||
                lower.endsWith(".fit.gz")
        }
        val chosen = activityFiles
            .groupBy { idOf(it.name) }
            .mapNotNull { (_, group) -> group.minByOrNull { rank(it.name) } }
            .sortedBy { it.name }
        val activities = mutableListOf<ImportedActivity>()
        val skipped = mutableListOf<String>()
        chosen.forEach { entry ->
            val name = entry.name.substringAfterLast('/')
            val plain = if (name.lowercase().endsWith(".gz")) gunzip(entry.bytes) else entry.bytes
            val fileName = if (name.lowercase().endsWith(".gz")) name.dropLast(3) else name
            val row = csv[idOf(name)]
            val parsed = runCatching {
                ActivityFiles.import(fileName, plain).first()
            }.getOrNull()
            if (parsed == null) {
                skipped += name
                return@forEach
            }
            activities += applyCsvRow(parsed, row)
        }
        return StravaArchiveImport(
            activities = activities,
            entriesSeen = entries.size,
            skipped = skipped,
            csvRows = csv.size,
        )
    }

    private fun applyCsvRow(
        activity: ImportedActivity,
        row: Map<String, String>?,
    ): ImportedActivity {
        if (row == null) return activity
        val name = row["Activity Name"]?.takeIf { it.isNotBlank() } ?: activity.name
        val type = ActivityType.fromSportName(row["Activity Type"])
        val calories = row["Calories"]?.toDoubleOrNull()?.takeIf { it > 0.0 }
            ?: activity.calories
        val averageHr = row["Average Heart Rate"]?.toDoubleOrNull()?.takeIf { it > 0.0 }
            ?: activity.averageHeartRate
        val maxHr = row["Max Heart Rate"]?.toDoubleOrNull()?.takeIf { it > 0.0 }
            ?: activity.maximumHeartRate
        val distance = row["Distance"]?.toDoubleOrNull()?.takeIf { it > 0.0 }
            ?: activity.distanceM
        return activity.copy(
            name = name,
            type = if (activity.type == ActivityType.OTHER) type else activity.type,
            distanceM = distance,
            calories = calories,
            averageHeartRate = averageHeartRate(averageHr, activity),
            maximumHeartRate = maxHr,
        )
    }

    private fun averageHeartRate(value: Double?, fallback: ImportedActivity): Double? {
        if (value != null) return value
        val samples = fallback.points.mapNotNull { it.heartRate }.filter { it > 0.0 }
        return if (samples.isEmpty()) null else samples.average()
    }

    private data class Entry(val name: String, val bytes: ByteArray)

    private fun unzip(bytes: ByteArray): List<Entry> {
        val entries = mutableListOf<Entry>()
        var total = 0L
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var next = zip.nextEntry
                while (next != null) {
                    if (entries.size >= MAX_ENTRIES) break
                    if (!next.isDirectory) {
                        val read = readLimited(zip, MAX_ENTRY_BYTES)
                        total += read.size
                        if (total > MAX_TOTAL_BYTES) {
                            throw ActivityFileException("The archive is larger than 500 MB")
                        }
                        entries += Entry(next.name, read)
                    }
                    zip.closeEntry()
                    next = zip.nextEntry
                }
            }
        } catch (exception: ActivityFileException) {
            throw exception
        } catch (exception: java.util.zip.ZipException) {
            throw ActivityFileException("The ZIP archive is not readable", exception)
        } catch (exception: java.io.IOException) {
            throw ActivityFileException("The ZIP archive is not readable", exception)
        }
        if (entries.isEmpty()) throw ActivityFileException("The archive has no files")
        return entries
    }

    private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw ActivityFileException("An archive entry is larger than 40 MB")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { readLimited(it, MAX_ENTRY_BYTES) }

    /** Prefer a full-sensor FIT over TCX over GPX when an export carries more than one. */
    private fun rank(name: String): Int {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".fit") || lower.endsWith(".fit.gz") -> 0
            lower.endsWith(".tcx") || lower.endsWith(".tcx.gz") -> 1
            else -> 2
        }
    }

    private fun idOf(name: String): String = name.substringAfterLast('/')
        .substringBefore('.')
        .trim()

    /**
     * A small CSV reader: quoted commas, escaped quotes and a header row. Strava writes
     * a plain comma-separated file, so a full CSV library would be a dependency for one
     * known shape.
     */
    internal fun parseCsv(text: String): Map<String, Map<String, String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                quoted && char == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                    cell.append('"')
                    index += 1
                }
                char == '"' -> quoted = !quoted
                !quoted && char == ',' -> {
                    row += cell.toString()
                    cell.clear()
                }
                !quoted && (char == '\n' || char == '\r') -> {
                    if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') {
                        index += 1
                    }
                    row += cell.toString()
                    cell.clear()
                    if (row.any { it.isNotBlank() }) rows += row
                    row = mutableListOf()
                }
                else -> cell.append(char)
            }
            index += 1
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            if (row.any { it.isNotBlank() }) rows += row
        }
        if (rows.size < 2) return emptyMap()
        val header = rows.first().map { it.trim() }
        val idIndex = header.indexOfFirst { it == "Activity ID" }
            .takeIf { it >= 0 } ?: 0
        return rows.drop(1).mapNotNull { values ->
            val id = values.getOrNull(idIndex)?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            id to header.mapIndexedNotNull { column, name ->
                values.getOrNull(column)?.let { name to it.trim() }
            }.toMap()
        }.toMap()
    }
}
