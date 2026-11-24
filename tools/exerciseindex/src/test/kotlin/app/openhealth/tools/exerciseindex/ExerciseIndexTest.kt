package app.openhealth.tools.exerciseindex

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The committed library must be exactly what the vendored dataset generates: the mapping,
 * the enrichment and the "text only" promise are checked on every `check`, not trusted.
 */
class ExerciseIndexTest {
    private val upstream = File("upstream/exercises.json")
    private val shipped = File(
        "../../core/src/main/resources/app/openhealth/core/exercise/exercises.tsv",
    )

    @Test
    fun theShippedLibraryIsWhatTheVendoredDatasetGenerates() {
        assertTrue(upstream.isFile, "missing ${upstream.absolutePath}")
        assertTrue(shipped.isFile, "missing ${shipped.absolutePath}")
        assertEquals(
            ExerciseIndexCli.convert(upstream.readText(Charsets.UTF_8)),
            shipped.readText(Charsets.UTF_8),
            "the shipped exercise library is stale: run ./gradlew :tools:exerciseindex:run",
        )
    }

    @Test
    fun theLibraryCarriesTextOnlyAndCoversTheWholeTaxonomy() {
        val rows = shipped.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue(rows.size >= 150)
        assertTrue(rows.all { it.split('\t').size == 10 })
        assertTrue(rows.all { it.split('\t')[8].isNotBlank() }, "every row has a body part")
        assertTrue(rows.all { it.split('\t')[2].isNotBlank() }, "every row has a primary muscle")
        val text = rows.joinToString("\n")
        assertFalse(text.contains(".jpg") || text.contains(".png") || text.contains("http"))
        assertFalse(text.contains("Everkinetic", ignoreCase = true))
    }
}
