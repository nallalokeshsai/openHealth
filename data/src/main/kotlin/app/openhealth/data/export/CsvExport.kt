package app.openhealth.data.export

import app.openhealth.core.observation.Observation

/**
 * CSV of observations with their provenance (`BUILD-ROADMAP.md` §B2). RFC 4180 quoting,
 * canonical units, ISO instants and the local day. A spreadsheet is a report surface, so
 * nothing here is hidden: source, device, method and both timestamps travel with a value.
 */
object CsvExport {
    private const val SEPARATOR = ','
    private const val NEWLINE = "\r\n"

    val HEADER: List<String> = listOf(
        "id",
        "type",
        "value",
        "unit",
        "start",
        "end",
        "happened_at",
        "recorded_at",
        "local_date",
        "zone",
        "source",
        "device",
        "method",
        "context",
        "user_verified",
        "user_edited",
        "excluded_from_trends",
        "idempotency_key",
    )

    fun observations(observations: List<Observation>): String = buildString {
        append(HEADER.joinToString(SEPARATOR.toString()) { escape(it) })
        append(NEWLINE)
        observations.forEach { observation ->
            val provenance = observation.provenance
            val row = listOf(
                observation.id.value,
                observation.type.name,
                observation.value.value.toString(),
                observation.value.unit.ucum,
                observation.start.toString(),
                observation.end?.toString().orEmpty(),
                provenance.happenedAt.toString(),
                provenance.recordedAt.toString(),
                observation.localDate.toString(),
                provenance.zone.id,
                provenance.sourceId.value,
                provenance.device.orEmpty(),
                provenance.method?.name.orEmpty(),
                observation.context?.name.orEmpty(),
                observation.userVerified.toString(),
                observation.userEdited.toString(),
                observation.excludedFromTrends.toString(),
                provenance.idempotencyKey,
            )
            append(row.joinToString(SEPARATOR.toString()) { escape(it) })
            append(NEWLINE)
        }
    }

    private fun escape(value: String): String {
        val needsQuotes = value.any { it == SEPARATOR || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuotes) return value
        return '"' + value.replace("\"", "\"\"") + '"'
    }
}
