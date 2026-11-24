package app.openhealth.tools.exerciseindex

import app.openhealth.core.body.BodyPart
import app.openhealth.core.body.Muscle
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Converts `upstream/exercises.json` (Free Exercise DB, public domain / Unlicense) into
 * `core/src/main/resources/app/openhealth/core/exercise/exercises.tsv`.
 *
 *   ./gradlew :tools:exerciseindex:run --args="/path/to/repo"
 *
 * The default output path is the working directory's `core/` folder, so running it from
 * the repository root is enough. The tool is idempotent and sorted, so a diff is the
 * upstream change and nothing else. It never copies the dataset's images (`NOTICE.md`).
 */
object ExerciseIndexCli {
    private const val DEFAULT_ROOT = "."
    private const val UPSTREAM = "tools/exerciseindex/upstream/exercises.json"
    private const val OUTPUT =
        "core/src/main/resources/app/openhealth/core/exercise/exercises.tsv"
    private const val ID_PREFIX = "fex:"
    private const val MINIMUM_EXERCISES = 150

    /**
     * The dataset's 17 muscle names, resolved to whole muscles (`DESIGN.md` §9.1).
     * `lats` and `middle back` are the same drawn shape as the upper back; `abductors`
     * are the glutes that abduct.
     */
    private val MUSCLE_MAP: Map<String, Muscle> = mapOf(
        "abdominals" to Muscle.ABS,
        "abductors" to Muscle.GLUTES,
        "adductors" to Muscle.ADDUCTORS,
        "biceps" to Muscle.BICEPS,
        "calves" to Muscle.CALVES,
        "chest" to Muscle.CHEST,
        "forearms" to Muscle.FOREARMS,
        "glutes" to Muscle.GLUTES,
        "hamstrings" to Muscle.HAMSTRINGS,
        "lats" to Muscle.UPPER_BACK,
        "lower back" to Muscle.LOWER_BACK,
        "middle back" to Muscle.UPPER_BACK,
        "neck" to Muscle.NECK,
        "quadriceps" to Muscle.QUADS,
        "shoulders" to Muscle.DELTOIDS,
        "traps" to Muscle.TRAPEZIUS,
        "triceps" to Muscle.TRICEPS,
    )

    /**
     * Four drawn muscles have no name in the dataset: the obliques, the serratus, the hip
     * flexors and the tibialis. These idioms name them as a secondary load, so the map
     * does not leave a shape that can never light up. It is our mapping, not the
     * dataset's, and is the kind of judgement `pr.md` §6.5 leaves to the app.
     */
    private val ENRICHMENT: List<Pair<Regex, Muscle>> = listOf(
        Regex("twist|side bend|windmill|russian", RegexOption.IGNORE_CASE) to Muscle.OBLIQUES,
        Regex("leg raise|knee raise|sit-up|situp|v-up|hanging", RegexOption.IGNORE_CASE) to
            Muscle.HIP_FLEXORS,
        Regex("push-up|pushup|press-up|dip\\b|pullover", RegexOption.IGNORE_CASE) to
            Muscle.SERRATUS,
        Regex("tibialis|toe raise|shin", RegexOption.IGNORE_CASE) to Muscle.TIBIALIS,
    )

    private val EQUIPMENT_MAP: Map<String, String> = mapOf(
        "barbell" to "barbell",
        "e-z curl bar" to "ez-bar",
        "dumbbell" to "dumbbell",
        "machine" to "machine",
        "cable" to "cable",
        "kettlebells" to "kettlebell",
        "bands" to "bands",
        "body only" to "bodyweight",
        "exercise ball" to "ball",
        "medicine ball" to "ball",
        "foam roll" to "other",
        "other" to "other",
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val root = File(args.firstOrNull() ?: DEFAULT_ROOT)
        val upstream = File(root, UPSTREAM)
        require(upstream.isFile) { "Upstream dataset missing: ${upstream.absolutePath}" }
        val rows = convert(upstream.readText(Charsets.UTF_8))
        val out = File(root, OUTPUT)
        out.parentFile.mkdirs()
        out.writeText(rows, Charsets.UTF_8)
        val exercises = rows.lineSequence().count { it.isNotBlank() && !it.startsWith("#") }
        val upstreamCount = Json.parseToJsonElement(upstream.readText()).jsonArray.size
        println(
            "wrote ${out.absolutePath}: $exercises exercises " +
                "(${upstreamCount - exercises} upstream entries skipped: no instructions)",
        )
    }

    /** The whole TSV, header included, so a test can compare it with the shipped file. */
    fun convert(json: String): String {
        val array = Json.parseToJsonElement(json).jsonArray
        val seen = mutableSetOf<String>()
        val lines = array.mapNotNull { element -> row(element.jsonObject) }
            .filter { seen.add(it.first) }
            .sortedBy { it.second.lowercase() }
            .map { it.third }
        check(lines.size >= MINIMUM_EXERCISES) {
            "The library has ${lines.size} exercises; at least $MINIMUM_EXERCISES are required"
        }
        return buildString {
            appendLine("# Exercise library: Free Exercise DB (yuhonas/free-exercise-db).")
            appendLine("# Public domain: the Unlicense. Text fields only; no images (NOTICE.md).")
            appendLine("# Generated by tools/exerciseindex — do not hand-edit.")
            appendLine(
                "# id\tname\tprimary\tsecondary\tequipment\tlevel\tmechanic\tforce\t" +
                    "bodyPart\tinstructions",
            )
            lines.forEach { appendLine(it) }
        }
    }

    private fun row(exercise: JsonObject): Triple<String, String, String>? {
        val id = exercise.text("id")
        val name = exercise.text("name")?.trim()?.takeIf { it.isNotEmpty() }
        val primary = muscles(exercise.array("primaryMuscles"))
        val instructions = exercise.array("instructions")
            .mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
            .filter { it.isNotEmpty() }
        val noIdentity = id == null || name == null
        val noContent = primary.isEmpty() || instructions.isEmpty()
        return if (noIdentity || noContent) {
            null
        } else {
            val secondary = muscles(exercise.array("secondaryMuscles")) - primary.toSet()
            val enriched = secondary + ENRICHMENT
                .filter { (pattern, muscle) ->
                    muscle !in primary && muscle !in secondary && pattern.containsMatchIn(name)
                }
                .map { it.second }
            val bodyPart = BodyPart.of(primary)?.key.orEmpty()
            val cells = listOf(
                ID_PREFIX + id,
                name,
                primary.joinToString(",") { it.key },
                enriched.distinct().joinToString(",") { it.key },
                EQUIPMENT_MAP[exercise.text("equipment").orEmpty()] ?: "other",
                exercise.text("level") ?: "beginner",
                exercise.text("mechanic").orEmpty(),
                exercise.text("force").orEmpty(),
                bodyPart,
                instructions.joinToString("\\n") { escape(it) },
            )
            Triple(id, name, cells.joinToString("\t"))
        }
    }

    private fun muscles(array: JsonArray): List<Muscle> = array
        .mapNotNull { it.jsonPrimitive.contentOrNull }
        .mapNotNull { MUSCLE_MAP[it.trim().lowercase()] }
        .distinct()

    private fun escape(text: String): String = text
        .replace("\\", "\\\\")
        .replace("\r", " ")
        .replace("\n", " ")
        .replace("\t", " ")

    private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.array(key: String): JsonArray =
        this[key] as? JsonArray ?: JsonArray(emptyList())
}
