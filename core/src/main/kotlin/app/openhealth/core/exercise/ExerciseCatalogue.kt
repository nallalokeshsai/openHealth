package app.openhealth.core.exercise

import app.openhealth.core.body.BodyPart
import app.openhealth.core.body.Muscle

/**
 * The bundled exercise library: Free Exercise DB, released into the public domain
 * (Unlicense) by its authors. Only the text is used (names, muscles, equipment, level,
 * mechanic, force, instructions); the dataset's images are Everkinetic material with a
 * separate share-alike term and are not shipped (`NOTICE.md`, `pr.md` §12.2).
 *
 * `tools/exerciseindex` converts `upstream/exercises.json` into the TSV this object reads,
 * so the APK carries plain text, not JSON, and no parser runs at launch. The library is
 * immutable; a person's own exercises are `isCustom` rows in the database.
 *
 * The format, one row per exercise, tab-separated:
 * `id, name, primary, secondary, equipment, level, mechanic, force, bodyPart, instructions`
 * where muscle keys are comma-separated and instruction steps are joined with `\n` (two
 * characters) so a row is always one line.
 */
object ExerciseCatalogue {
    const val RESOURCE_PATH = "/app/openhealth/core/exercise/exercises.tsv"

    /** The upstream release this resource was generated from, recorded in `NOTICE.md`. */
    const val SOURCE_LABEL = "Free Exercise DB (yuhonas/free-exercise-db), Unlicense"

    /**
     * Stored in settings so the library is written into a database once, not on every
     * launch (`pr.md` §6.5). Bump it when the resource is regenerated.
     */
    const val VERSION = "free-exercise-db-1"

    private const val COLUMNS = 10
    private const val ID = 0
    private const val NAME = 1
    private const val PRIMARY = 2
    private const val SECONDARY = 3
    private const val EQUIPMENT = 4
    private const val LEVEL = 5
    private const val MECHANIC = 6
    private const val FORCE = 7
    private const val BODY_PART = 8
    private const val INSTRUCTIONS = 9

    val bundled: List<Exercise> by lazy { load() }

    fun load(): List<Exercise> {
        val stream = ExerciseCatalogue::class.java.getResourceAsStream(RESOURCE_PATH)
            ?: error("Exercise library resource is missing: $RESOURCE_PATH")
        return stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.mapNotNull(::parse).toList()
        }
    }

    private fun parse(line: String): Exercise? {
        if (line.isBlank() || line.startsWith("#")) return null
        val cells = line.split('\t')
        if (cells.size != COLUMNS) {
            error("Exercise row has ${cells.size} columns, expected $COLUMNS: ${cells.first()}")
        }
        val primary = muscles(cells[PRIMARY])
        return Exercise(
            id = cells[ID],
            name = cells[NAME],
            primary = primary,
            secondary = muscles(cells[SECONDARY]),
            equipment = Equipment.fromKey(cells[EQUIPMENT]),
            level = ExerciseLevel.fromKey(cells[LEVEL]),
            mechanic = ExerciseMechanic.fromKey(cells[MECHANIC]),
            force = ExerciseForce.fromKey(cells[FORCE]),
            bodyPart = BodyPart.fromKey(cells[BODY_PART]) ?: BodyPart.of(primary),
            instructions = decode(cells[INSTRUCTIONS]).split('\n').filter { it.isNotBlank() },
        )
    }

    /** `\n` is a step break and `\\` a literal backslash; nothing else is an escape. */
    private fun decode(cell: String): String = buildString {
        var index = 0
        while (index < cell.length) {
            val char = cell[index]
            if (char == '\\' && index + 1 < cell.length) {
                when (cell[index + 1]) {
                    'n' -> append('\n')
                    else -> append(cell[index + 1])
                }
                index += 2
            } else {
                append(char)
                index += 1
            }
        }
    }

    private fun muscles(cell: String): List<Muscle> = cell
        .split(',')
        .mapNotNull { Muscle.fromKey(it) }
        .distinct()
}
