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
    fun aV11BundleRestoresWithTheTransportFlagAtItsDefault() {
        val source = storeWithAnExercise()
        app.openhealth.data.repo.SourceRepository(source.db).upsert(
            app.openhealth.core.source.Source(
                id = app.openhealth.core.ids.SourceId("health-connect"),
                kind = app.openhealth.core.observation.SourceKind.HEALTH_CONNECT,
                label = "Health Connect",
                family = "health-connect",
                viaHealthConnect = false,
            ),
        )
        val current = service(source).snapshot()
        val b15Tables = setOf(
            "coach_message",
            "coach_proposal",
            "coach_action",
            "coach_snapshot",
            "mcp_token",
            "mcp_access",
        )
        val older = current.copy(
            schemaVersion = 11,
            tables = current.tables
                .filterNot { it.table in b15Tables }
                .map { table ->
                    if (table.table != "source") {
                        table
                    } else {
                        table.copy(rows = table.rows.map { row -> row - "via_health_connect" })
                    }
                },
        )
        val target = inMemoryStore()
        service(target).restoreBundle(older)
        val manual = app.openhealth.data.repo.SourceRepository(target.db)
            .byId(app.openhealth.core.ids.SourceId("manual"))
        assertEquals(false, manual!!.viaHealthConnect)
        assertEquals(
            true,
            app.openhealth.data.repo.SourceRepository(target.db)
                .byId(app.openhealth.core.ids.SourceId("health-connect"))!!.viaHealthConnect,
        )
        assertEquals(0L, target.db.coachQueries.countMessages().executeAsOne())
    }

    @Test
    fun restoredMcpTokensAreRevokedRatherThanRevived() {
        val source = storeWithAnExercise()
        app.openhealth.data.repo.McpRepository(source.db).issue(
            label = "Old assistant",
            scopes = setOf(app.openhealth.core.ai.McpScope.READ_DAY),
            tokenHash = "digest-that-is-not-a-token",
        )
        val bundle = service(source).snapshot()
        val target = inMemoryStore()
        service(target).restoreBundle(bundle)
        val restored = app.openhealth.data.repo.McpRepository(target.db).tokens().single()
        assertEquals(true, restored.revoked)
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
