package app.openhealth.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.openhealth.data.db.DatabaseOpener
import app.openhealth.data.db.MigrationBackup
import app.openhealth.data.db.OpenHealthDb
import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The v5 → v8 upgrade (`BUILD-ROADMAP.md` §B10: a migration test from every released
 * schema version). v1, v2, v3, v4, v6 and v7 each have a test in [DatabaseTest]; this is
 * the one version that was only ever exercised as a step inside a longer chain, so a
 * defect that only a v5 database triggers could have shipped unnoticed.
 *
 * v5 is the schema after B5's training columns and before B6's diary: it has the whole
 * training model, and it has the food, meal and entry tables but not the recipe,
 * template, target or fasting tables.
 */
class V5MigrationTest {
    @Test
    fun aV5TrainingDatabaseKeepsItsSessionThroughTwoMigrations() {
        val directory = Files.createTempDirectory("openhealth-v5-training")
        val path = directory.resolve("v5.db").toString()
        val first = JdbcSqliteDriver("jdbc:sqlite:$path", Properties())
        OpenHealthDb.Schema.create(first).value
        first.execute(null, "DROP VIEW IF EXISTS timeline", 0)
        stripNutrition(first)
        stripTraining(first)
        // 7.sqm created body_photo; a v5 database has no photo table.
        listOf("body_photo_date", "body_photo_happened").forEach {
            first.execute(null, "DROP INDEX IF EXISTS $it", 0)
        }
        first.execute(null, "DROP TABLE IF EXISTS body_photo", 0)
        first.execute(
            null,
            "INSERT INTO source(id, kind, label, family, created_at, updated_at) VALUES " +
                "('manual','MANUAL','Manual entry','manual',0,0)",
            0,
        )
        first.execute(
            null,
            "INSERT INTO exercise(id, name, primary_muscles, secondary_muscles, is_custom, " +
                "created_at, updated_at, revision) VALUES " +
                "('bench','Bench press','chest','triceps',0,0,0,1)",
            0,
        )
        first.execute(
            null,
            "INSERT INTO routine(id, name, source_id, archived_at, created_at, updated_at, " +
                "revision) VALUES ('r1','Push day','manual',NULL,0,0,1)",
            0,
        )
        first.execute(
            null,
            "INSERT INTO routine_exercise(id, routine_id, exercise_id, position, " +
                "target_sets, target_reps, target_weight_kg) VALUES " +
                "('re1','r1','bench',0,4,5,80)",
            0,
        )
        first.execute(
            null,
            "INSERT INTO session(id, started_at, local_date, zone_id, happened_at, " +
                "recorded_at, routine_id, source_id, idempotency_key, revision, " +
                "updated_at) VALUES ('s1',0,'2026-01-02','Europe/Berlin',0,0,'r1'," +
                "'manual','s1',1,0)",
            0,
        )
        first.execute(
            null,
            "INSERT INTO session_exercise(id, session_id, exercise_id, position) VALUES " +
                "('se1','s1','bench',0)",
            0,
        )
        first.execute(
            null,
            "INSERT INTO work_set(id, session_id, session_exercise_id, exercise_id, position, " +
                "weight_kg, reps, is_warmup, completed_at, happened_at, recorded_at, " +
                "source_id, idempotency_key, revision) VALUES " +
                "('w1','s1','se1','bench',0,80,5,0,0,0,0,'manual','w1',1)",
            0,
        )
        // A v5 diary row: the food, meal and entry tables existed before the v6 additions.
        first.execute(
            null,
            "INSERT INTO food(id, name, source_kind, energy_kcal_per_100g, " +
                "protein_g_per_100g, carbs_g_per_100g, fat_g_per_100g, fibre_g_per_100g, " +
                "verified, created_at, updated_at, revision) VALUES " +
                "('f1','Oats','manual',389,16.9,66,7,10,0,0,0,1)",
            0,
        )
        first.execute(
            null,
            "INSERT INTO meal(id, name, kind, local_date, created_at, updated_at, revision) " +
                "VALUES ('m1','Breakfast','breakfast','2026-01-02',0,0,1)",
            0,
        )
        first.execute(
            null,
            "INSERT INTO entry(id, meal_id, food_id, local_date, zone_id, happened_at, " +
                "recorded_at, quantity_g, energy_kcal, protein_g, carbs_g, fat_g, fibre_g, " +
                "source_kind, source_id, user_verified, user_edited, idempotency_key, " +
                "revision) VALUES ('e1','m1','f1','2026-01-02','Europe/Berlin',0,0,100,389," +
                "16.9,66,7,10,'manual','manual',0,0,'e1',1)",
            0,
        )
        first.execute(null, "PRAGMA user_version = 5", 0)
        first.close()

        val store = DatabaseOpener(MigrationBackup { from, to -> "backup-$from-$to" }).open(
            jdbcFactory("jdbc:sqlite:$path"),
        )
        assertEquals(10L, store.schemaVersion)

        // The training rows survive v6, v7 and v8 untouched.
        val set = store.db.trainingQueries.workSetById("w1").executeAsOne()
        assertEquals(80.0, set.weight_kg)
        assertEquals(5L, set.reps)
        val routine = store.db.trainingQueries.routineById("r1").executeAsOne()
        assertEquals("Push day", routine.name)
        // favourite arrived in 5.sqm, i.e. with v6; an upgraded row keeps the default.
        assertEquals(false, routine.favourite)
        assertEquals(null, store.db.trainingQueries.sessionById("s1").executeAsOne().title)

        // The diary row survives, and the tables v6 introduced now exist.
        assertEquals(
            389.0,
            store.db.nutritionQueries.entryById("e1").executeAsOne().energy_kcal,
        )
        listOf("recipe_item", "meal_template", "water_container", "nutrition_target", "fast_window")
            .forEach { table ->
                assertTrue(tableExists(store.driver, table), "$table was not created")
            }
        assertEquals(
            0L,
            store.driver.executeQuery(
                null,
                "PRAGMA foreign_key_check",
                { cursor ->
                    var rows = 0L
                    while (cursor.next().value) rows++
                    QueryResult.Value(rows)
                },
                0,
            ).value,
        )
        store.close()
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(directory)
    }

    /** Removes everything migration 5 added, leaving a genuine v5 training schema. */
    private fun stripTraining(driver: JdbcSqliteDriver) {
        listOf("session_ended", "session_routine").forEach {
            driver.execute(null, "DROP INDEX IF EXISTS $it", 0)
        }
        mapOf(
            "routine" to listOf(
                "weekday",
                "rotation_index",
                "favourite",
                "progression",
                "target_rest_seconds",
            ),
            "routine_exercise" to listOf(
                "target_reps_high",
                "target_rir",
                "rest_seconds",
                "progression",
                "group_index",
            ),
            "session" to listOf("title", "feeling", "perceived_effort"),
            "session_exercise" to listOf(
                "group_index",
                "target_sets",
                "target_reps",
                "target_reps_high",
                "target_weight_kg",
                "progression",
                "rest_seconds",
            ),
            "work_set" to listOf(
                "is_drop",
                "is_assisted",
                "assistance_kg",
                "band_kg",
                "notes",
                "original_weight_kg",
                "original_reps",
                "edit_reason",
                "edited_at",
                "user_edited",
            ),
        ).forEach { (table, columns) ->
            columns.forEach { column ->
                driver.execute(null, "ALTER TABLE $table DROP COLUMN $column", 0)
            }
        }
    }

    private fun tableExists(driver: app.cash.sqldelight.db.SqlDriver, name: String): Boolean =
        driver.executeQuery(
            null,
            "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = '$name'",
            { cursor ->
                cursor.next()
                QueryResult.Value((cursor.getLong(0) ?: 0L) > 0L)
            },
            0,
        ).value

    /** Removes everything migration 6 added, leaving a genuine v5 database. */
    private fun stripNutrition(driver: JdbcSqliteDriver) {
        listOf(
            "food_type_index",
            "entry_food",
            "recipe_item_recipe",
            "meal_template_item_template",
            "nutrition_target_effective",
            "fast_window_start",
            "meal_effective_time",
        ).forEach { driver.execute(null, "DROP INDEX IF EXISTS $it", 0) }
        listOf(
            "recipe_item",
            "meal_template_item",
            "meal_template",
            "water_container",
            "nutrition_target",
            "fast_window",
        ).forEach { driver.execute(null, "DROP TABLE IF EXISTS $it", 0) }
        mapOf(
            "food" to listOf(
                "category",
                "food_type",
                "nutrients_json",
                "default_serving_g",
                "default_serving_label",
                "allergens_json",
                "ingredients_text",
                "analysis_json",
            ),
            "meal" to listOf(
                "zone_id",
                "happened_at",
                "recorded_at",
                "source_id",
                "idempotency_key",
            ),
            "entry" to listOf(
                "serving_label",
                "serving_g",
                "servings",
                "food_name",
                "brand",
                "micros_json",
            ),
        ).forEach { (table, columns) ->
            columns.forEach { column ->
                driver.execute(null, "ALTER TABLE $table DROP COLUMN $column", 0)
            }
        }
        // The v5 timeline had no meal provenance and no fasting window.
        driver.execute(
            null,
            """
            CREATE VIEW timeline AS
            SELECT 'observation' AS kind, id AS entity_id, local_date, happened_at,
                recorded_at, source_id, type AS detail, user_edited AS edited
            FROM observation
            UNION ALL
            SELECT 'session', id, local_date, happened_at, recorded_at, source_id,
                routine_id, 0 FROM session
            UNION ALL
            SELECT 'work_set', work_set.id, session.local_date, work_set.happened_at,
                work_set.recorded_at, work_set.source_id, work_set.exercise_id, 0
            FROM work_set JOIN session ON session.id = work_set.session_id
            UNION ALL
            SELECT 'entry', id, local_date, happened_at, recorded_at, source_kind, meal_id,
                user_edited FROM entry
            UNION ALL
            SELECT 'measurement', id, local_date, happened_at, recorded_at, source_id, type, 0
            FROM measurement
            UNION ALL
            SELECT 'vital', id, local_date, happened_at, recorded_at, source_id, type, 0
            FROM vital
            UNION ALL
            SELECT 'meal', id, local_date, created_at, created_at, 'manual', kind, 0
            FROM meal
            UNION ALL
            SELECT 'note', id, local_date, happened_at, recorded_at, source_id, NULL, 0
            FROM note
            """.trimIndent(),
            0,
        )
    }
}
