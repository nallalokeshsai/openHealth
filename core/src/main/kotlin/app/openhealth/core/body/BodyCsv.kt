package app.openhealth.core.body

import app.openhealth.core.observation.MeasurementMethod
import app.openhealth.core.units.UcumUnit
import kotlinx.datetime.LocalDate

/**
 * One row of the body-metrics CSV (`pr.md` §6.8: "CSV import/export, including Apple
 * Health weight exports"). A row names its own unit, because a file outlives the
 * person's current unit preference.
 */
data class BodyCsvRow(
    val date: LocalDate,
    val type: String,
    val value: Double,
    val unit: UcumUnit,
    val method: MeasurementMethod?,
    val notes: String? = null,
)

data class BodyCsvProblem(val line: Int, val reason: String)

data class BodyCsvParseResult(
    val rows: List<BodyCsvRow>,
    val problems: List<BodyCsvProblem>,
) {
    val ok: Boolean get() = problems.isEmpty() && rows.isNotEmpty()
}

/**
 * The body CSV: `date,type,value,unit,method,notes`, one reading per line.
 *
 * The unit column is never assumed. A pound is converted to kilograms and an inch to
 * centimetres before the row reaches storage, so a file cannot silently double a value
 * (`pr.md` §6.8 acceptance: the trend is never distorted by unit conversion). Values are
 * parsed and validated here, in the pure module, so the importer and its tests use the
 * same rule.
 */
object BodyCsv {
    const val HEADER = "date,type,value,unit,method,notes"
    private const val COLUMNS = 6
    private const val MIN_WEIGHT_KG = 1.0
    private const val MAX_WEIGHT_KG = 500.0
    private const val MIN_CM = 1.0
    private const val MAX_CM = 400.0
    private const val MIN_PERCENT = 1.0
    private const val MAX_PERCENT = 70.0
    private const val MAX_NOTE = 500

    fun export(rows: List<BodyCsvRow>): String {
        val body = rows.sortedWith(compareBy({ it.date }, { it.type })).joinToString("\n") { row ->
            listOf(
                row.date.toString(),
                row.type,
                trim(row.value),
                row.unit.ucum,
                row.method?.name?.lowercase().orEmpty(),
                escape(row.notes.orEmpty()),
            ).joinToString(",")
        }
        return if (body.isEmpty()) "$HEADER\n" else "$HEADER\n$body\n"
    }

    fun parse(text: String): BodyCsvParseResult {
        val rows = mutableListOf<BodyCsvRow>()
        val problems = mutableListOf<BodyCsvProblem>()
        text.removePrefix("\uFEFF").lineSequence().forEachIndexed { index, raw ->
            val lineNumber = index + 1
            if (raw.isBlank()) return@forEachIndexed
            val cells = split(raw)
            if (index == 0 && cells.firstOrNull()?.trim()?.equals("date", ignoreCase = true) == true) {
                return@forEachIndexed
            }
            if (cells.size < 4) {
                problems += BodyCsvProblem(lineNumber, "expected at least four columns")
                return@forEachIndexed
            }
            val date = runCatching { LocalDate.parse(cells[0].trim()) }.getOrNull()
            if (date == null) {
                problems += BodyCsvProblem(lineNumber, "not a date: ${cells[0].trim()}")
                return@forEachIndexed
            }
            val type = cells[1].trim().lowercase()
            if (type != WEIGHT && MeasurementSite.fromType(type) == null &&
                CompositionMeasure.fromType(type) == null
            ) {
                problems += BodyCsvProblem(lineNumber, "not a body metric: $type")
                return@forEachIndexed
            }
            val value = cells[2].trim().replace(',', '.').toDoubleOrNull()
            if (value == null || !value.isFinite()) {
                problems += BodyCsvProblem(lineNumber, "not a number: ${cells[2].trim()}")
                return@forEachIndexed
            }
            val unit = runCatching {
                UcumUnit.byUcum(cells[3].trim(), expectedDimension(type))
            }.getOrNull()
            if (unit == null) {
                problems += BodyCsvProblem(lineNumber, "unit does not fit $type: ${cells[3].trim()}")
                return@forEachIndexed
            }
            val canonical = unit.toCanonical(value)
            if (!plausible(type, canonical)) {
                problems += BodyCsvProblem(lineNumber, "value is outside a plausible range")
                return@forEachIndexed
            }
            val method = cells.getOrNull(4)?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
                MeasurementMethod.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            }
            val notes = cells.getOrNull(5)?.trim()?.takeIf { it.isNotEmpty() }
            if (notes != null && notes.length > MAX_NOTE) {
                problems += BodyCsvProblem(lineNumber, "note is longer than $MAX_NOTE characters")
                return@forEachIndexed
            }
            rows += BodyCsvRow(
                date = date,
                type = type,
                value = canonical,
                unit = UcumUnit.canonicalFor(unit.dimension),
                method = method,
                notes = notes,
            )
        }
        return BodyCsvParseResult(rows, problems)
    }

    private fun expectedDimension(type: String) = when {
        type == WEIGHT -> app.openhealth.core.units.Dimension.MASS
        MeasurementSite.fromType(type) != null -> app.openhealth.core.units.Dimension.BODY_LENGTH
        else -> app.openhealth.core.units.Dimension.FRACTION
    }

    private fun plausible(type: String, canonical: Double): Boolean = when {
        type == WEIGHT -> canonical in MIN_WEIGHT_KG..MAX_WEIGHT_KG
        MeasurementSite.fromType(type) != null -> canonical in MIN_CM..MAX_CM
        else -> canonical in MIN_PERCENT..MAX_PERCENT
    }

    /** A minimal RFC 4180 reader: quotes, doubled quotes and embedded commas. */
    private fun split(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                quoted && char == '"' && line.getOrNull(index + 1) == '"' -> {
                    current.append('"')
                    index += 2
                    continue
                }

                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    cells += current.toString()
                    current.clear()
                }

                else -> current.append(char)
            }
            index++
        }
        cells += current.toString()
        return cells
    }

    private fun escape(text: String): String =
        if (text.contains(',') || text.contains('"') || text.contains('\n')) {
            "\"" + text.replace("\"", "\"\"") + "\""
        } else {
            text
        }

    private fun trim(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    /** The one weight type key; measurements use their [MeasurementSite.type]. */
    const val WEIGHT = "weight"
}
