package app.openhealth.core.clinical

import kotlinx.datetime.LocalDate

/** One lab reading from a file; the unit is already the analyte's own. */
data class LabCsvRow(
    val date: LocalDate,
    val analyte: LabAnalyte,
    val value: Double,
    val lab: String?,
    val note: String?,
)

data class LabCsvProblem(val line: Int, val reason: String)

data class LabCsvParseResult(val rows: List<LabCsvRow>, val problems: List<LabCsvProblem>)

/**
 * Lab results as `date,analyte,value,unit,lab,note` (`pr.md` §6.9). The analyte is a LOINC
 * code or its name, and the unit must be the analyte's own: a file never converts a value
 * silently, so a mismatched unit is a problem the person sees, not a guessed number.
 */
object LabCsv {
    const val HEADER = "date,analyte,value,unit,lab,note"
    private const val MIN_COLUMNS = 4
    private const val UNIT_COLUMN = 3
    private const val LAB_COLUMN = 4
    private const val NOTE_COLUMN = 5

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun parse(text: String): LabCsvParseResult {
        val rows = mutableListOf<LabCsvRow>()
        val problems = mutableListOf<LabCsvProblem>()
        text.removePrefix("").lineSequence().forEachIndexed { index, raw ->
            val line = index + 1
            if (raw.isBlank()) return@forEachIndexed
            val cells = split(raw)
            if (index == 0 && cells.firstOrNull()?.trim().equals("date", ignoreCase = true)) {
                return@forEachIndexed
            }
            if (cells.size < MIN_COLUMNS) {
                problems += LabCsvProblem(line, "expected at least four columns")
                return@forEachIndexed
            }
            val date = runCatching { LocalDate.parse(cells[0].trim()) }.getOrNull()
            if (date == null) {
                problems += LabCsvProblem(line, "not a date: ${cells[0].trim()}")
                return@forEachIndexed
            }
            val key = cells[1].trim()
            val analyte = LabAnalyte.entries.firstOrNull {
                it.loinc == key ||
                    it.display.equals(key, ignoreCase = true) ||
                    it.name.equals(key, ignoreCase = true)
            }
            if (analyte == null) {
                problems += LabCsvProblem(line, "unknown analyte: $key")
                return@forEachIndexed
            }
            val value = cells[2].trim().replace(',', '.').toDoubleOrNull()
            if (value == null || !value.isFinite() || value < 0.0) {
                problems += LabCsvProblem(line, "not a valid value: ${cells[2].trim()}")
                return@forEachIndexed
            }
            val unit = cells[UNIT_COLUMN].trim()
            if (unit.isNotEmpty() && !unit.equals(analyte.unit.ucum, ignoreCase = true)) {
                problems += LabCsvProblem(
                    line,
                    "${analyte.display} is in ${analyte.unit.ucum}, not $unit",
                )
                return@forEachIndexed
            }
            rows += LabCsvRow(
                date = date,
                analyte = analyte,
                value = value,
                lab = cells.getOrNull(LAB_COLUMN)?.trim()?.takeIf { it.isNotEmpty() },
                note = cells.getOrNull(NOTE_COLUMN)?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
        return LabCsvParseResult(rows, problems)
    }

    /** A minimal RFC 4180 reader: quotes, doubled quotes and embedded commas. */
    private fun split(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && line.getOrNull(i + 1) == '"' -> {
                    current.append('"')
                    i += 1
                }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> {
                    cells += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
            i += 1
        }
        cells += current.toString()
        return cells
    }
}
