package app.openhealth.data

import app.openhealth.core.seam.FixedClock
import app.openhealth.core.seam.UnsupportedExportFormat
import app.openhealth.data.db.Exercise
import app.openhealth.data.export.Argon2ExportCipher
import app.openhealth.data.export.ExportService
import app.openhealth.data.repo.TrainingRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * `pr.md` §6.29: an export must be able to rebuild the record. B4 raised the schema to v5
 * by adding exercise columns only, so a v4 bundle (which lacks them) still restores; a
 * bundle from a future schema is refused rather than guessed at.
 */
class OlderSchemaRestoreTest {
    private val now = Instant.parse("2026-11-24T12:00:00Z")
    private val clock = FixedClock(now)

    private fun storeWithAnExercise(): app.openhealth.data.db.OpenHealthStore =
        inMemoryStore().also { store ->
            TrainingRepository(store.db).upsertExercise(
                Exercise(
                    id = "ex-1",
                    name = "Barbell bench press",
                    primary_muscles = "chest",
                    secondary_muscles = "triceps",
                    equipment = "barbell",
                    instructions = "Press.",
                    level = "intermediate",
                    mechanic = "compound",
                    force = "push",
                    met_activity = null,
                    is_custom = false,
                    source_id = "manual",
                    created_at = now.toEpochMilliseconds(),
                    updated_at = now.toEpochMilliseconds(),
                    revision = 1L,
                ),
            )
        }

    private fun service(store: app.openhealth.data.db.OpenHealthStore) =
        ExportService(store.driver, store.db, Argon2ExportCipher(), clock)

    @Test
    fun aV4BundleRestoresIntoV5AndTheNewColumnsTakeTheirDefault() {
        val source = storeWithAnExercise()
        val current = service(source).snapshot()
        val older = current.copy(
            schemaVersion = 4,
            tables = current.tables.map { table ->
                if (table.table != "exercise") {
                    table
                } else {
                    table.copy(
                        rows = table.rows.map { row ->
                            row - "level" - "mechanic" - "force"
                        },
                    )
                }
            },
        )
        val target = inMemoryStore()
        service(target).restoreBundle(older)
        assertEquals(1L, target.db.trainingQueries.countExercises().executeAsOne())
        val restored = target.db.trainingQueries.exerciseById("ex-1").executeAsOne()
        assertEquals("Barbell bench press", restored.name)
        assertEquals(null, restored.level)
        assertTrue(restored.primary_muscles == "chest")
    }

    @Test
    fun aBundleFromANewerSchemaIsRefused() {
        val source = storeWithAnExercise()
        val newer = service(source).snapshot().copy(schemaVersion = 99)
        assertFailsWith<UnsupportedExportFormat> {
            service(source).restoreBundle(newer)
        }
    }
}
