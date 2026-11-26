package app.openhealth.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.openhealth.data.db.DatabaseOpener
import app.openhealth.data.db.MigrationBackup
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.db.journalMode
import app.openhealth.data.db.openHealthDatabase
import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DatabaseTest {
    @Test
    fun aNewDatabaseIsCreatedAtTheCurrentSchemaVersion() {
        val store = inMemoryStore()
        assertEquals(OpenHealthDb.Schema.version, store.schemaVersion)
        assertEquals(11L, store.schemaVersion)
        assertEquals(0L, store.db.observationQueries.countObservations().executeAsOne())
        assertEquals(0L, store.db.trainingQueries.countWorkSets().executeAsOne())
        store.close()
    }

    @Test
    fun aFileDatabaseRunsInWalMode() {
        val directory = Files.createTempDirectory("openhealth-db")
        val path = directory.resolve("openhealth.db").toString()
        val store = fileStore(path)
        assertEquals("wal", journalMode(store.driver).lowercase())
        store.close()
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(java.nio.file.Path.of("$path-shm"))
        Files.deleteIfExists(java.nio.file.Path.of("$path-wal"))
        Files.deleteIfExists(directory)
    }

    @Test
    fun anOlderDatabaseIsBackedUpThenMigrated() {
        val directory = Files.createTempDirectory("openhealth-migrate")
        val path = directory.resolve("v1.db").toString()
        // v1 never shipped; it is exactly the v2 schema without the six indexes that
        // 1.sqm adds, so dropping them reproduces the pre-migration state faithfully.
        val first = JdbcSqliteDriver("jdbc:sqlite:$path", Properties())
        OpenHealthDb.Schema.create(first).value
        // Drop the v5 columns before anything the timeline view reads: SQLite re-parses
        // every view on a schema change and the view names note.
        dropTimelineView(first)
        stripV7Columns(first)
        stripV6Columns(first)
        stripV5Columns(first)
        // The note table arrives in v4; this fixture must really be older than it.
        first.execute(null, "DROP TABLE note", 0)
        V2_INDEXES.forEach { first.execute(null, "DROP INDEX $it", 0) }
        createV6Timeline(first)
        first.execute(null, "PRAGMA user_version = 1", 0)
        first.close()

        var backupLabel: String? = null
        val backup = MigrationBackup { from, to ->
            backupLabel = "v$from-to-v$to"
            "backup-$from-$to"
        }
        val store = DatabaseOpener(backup).open(
            jdbcFactory("jdbc:sqlite:$path"),
        )
        assertEquals("v1-to-v11", backupLabel)
        assertEquals(11L, store.schemaVersion)
        assertEquals(11L, userVersion(store.driver))
        V2_INDEXES.forEach { assertEquals(1L, indexCount(store.driver, it), "index $it") }
        store.close()
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(directory)
    }

    @Test
    fun everyStoredTableIsInTheExportRegistry() {
        val store = inMemoryStore()
        val tables = store.db.transactionWithResult {
            store.driver.executeQuery(
                null,
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
                { cursor ->
                    val names = mutableListOf<String>()
                    while (cursor.next().value) names += cursor.getString(0).orEmpty()
                    QueryResult.Value(names)
                },
                0,
            ).value
        }
        val exported = app.openhealth.data.export.ExportTables.all.map { it.name }.toSet()
        assertEquals(
            emptySet<String>(),
            tables.toSet() - exported,
            "a table is missing from ExportTables and would be silently dropped from an export",
        )
        assertEquals(tables.toSet(), exported)
        store.close()
    }

    @Test
    fun theExportRegistryMatchesEveryTableAndColumn() {
        val store = inMemoryStore()
        app.openhealth.data.export.ExportTables.all.forEach { table ->
            val columns = store.db.transactionWithResult {
                store.driver.executeQuery(
                    null,
                    "PRAGMA table_info(${table.name})",
                    { cursor ->
                        val names = mutableListOf<String>()
                        while (cursor.next().value) names += cursor.getString(1).orEmpty()
                        QueryResult.Value(names)
                    },
                    0,
                ).value
            }
            assertEquals(
                columns.toSet(),
                table.columns.map { it.name }.toSet(),
                "columns of ${table.name}",
            )
        }
        store.close()
    }

    @Test
    fun aMigrationWithoutABackupIsRefused() {
        val directory = Files.createTempDirectory("openhealth-nobackup")
        val path = directory.resolve("v1.db").toString()
        val first = JdbcSqliteDriver("jdbc:sqlite:$path", Properties())
        OpenHealthDb.Schema.create(first).value
        // Drop the v5 columns before anything the timeline view reads: SQLite re-parses
        // every view on a schema change and the view names note.
        dropTimelineView(first)
        stripV7Columns(first)
        stripV6Columns(first)
        stripV5Columns(first)
        // The note table arrives in v4; this fixture must really be older than it.
        first.execute(null, "DROP TABLE note", 0)
        V2_INDEXES.forEach { first.execute(null, "DROP INDEX $it", 0) }
        createV6Timeline(first)
        first.execute(null, "PRAGMA user_version = 1", 0)
        first.close()
        assertFailsWith<IllegalStateException> {
            DatabaseOpener().open(jdbcFactory("jdbc:sqlite:$path"))
        }
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(directory)
    }

    @Test
    fun aFailedSnapshotRefusesTheMigration() {
        val directory = Files.createTempDirectory("openhealth-badsnapshot")
        val path = directory.resolve("v1.db").toString()
        val first = JdbcSqliteDriver("jdbc:sqlite:$path", Properties())
        OpenHealthDb.Schema.create(first).value
        // Drop the v5 columns before anything the timeline view reads: SQLite re-parses
        // every view on a schema change and the view names note.
        dropTimelineView(first)
        stripV7Columns(first)
        stripV6Columns(first)
        stripV5Columns(first)
        // The note table arrives in v4; this fixture must really be older than it.
        first.execute(null, "DROP TABLE note", 0)
        V2_INDEXES.forEach { first.execute(null, "DROP INDEX $it", 0) }
        createV6Timeline(first)
        first.execute(null, "PRAGMA user_version = 1", 0)
        first.close()
        assertFailsWith<IllegalStateException> {
            DatabaseOpener(MigrationBackup { _, _ -> null })
                .open(jdbcFactory("jdbc:sqlite:$path"))
        }
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(directory)
    }

    @Test
    fun aV3DatabaseRecreatesTheTimelineViewWithNotes() {
        val directory = Files.createTempDirectory("openhealth-v3-view")
        val path = directory.resolve("v3.db").toString()
        val first = JdbcSqliteDriver("jdbc:sqlite:$path", Properties())
        OpenHealthDb.Schema.create(first).value
        dropTimelineView(first)
        stripV7Columns(first)
        stripV6Columns(first)
        stripV5Columns(first)
        // The v3 schema has the note-less view and no note table.
        first.execute(null, "DROP TABLE note", 0)
        first.execute(
            null,
            "CREATE VIEW timeline AS SELECT 'observation' AS kind, id AS entity_id, " +
                "local_date, happened_at, recorded_at, source_id, type AS detail, " +
                "user_edited AS edited FROM observation",
            0,
        )
        first.execute(null, "PRAGMA user_version = 3", 0)
        first.close()

        val store = DatabaseOpener(MigrationBackup { from, to -> "backup-$from-$to" }).open(
            jdbcFactory("jdbc:sqlite:$path"),
        )
        assertEquals(11L, store.schemaVersion)
        val view = store.driver.executeQuery(
            null,
            "SELECT sql FROM sqlite_master WHERE type = 'view' AND name = 'timeline'",
            { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getString(0).orEmpty())
            },
            0,
        ).value
        assertTrue(view.contains("FROM note"), "the migrated view must include notes")
        store.close()
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(directory)
    }

    @Test
    fun aNewerDatabaseIsRefused() {
        val directory = Files.createTempDirectory("openhealth-newer")
        val path = directory.resolve("newer.db").toString()
        val first = JdbcSqliteDriver("jdbc:sqlite:$path", Properties())
        OpenHealthDb.Schema.create(first).value
        first.execute(null, "PRAGMA user_version = 99", 0)
        first.close()
        assertFailsWith<IllegalArgumentException> {
            DatabaseOpener().open(jdbcFactory("jdbc:sqlite:$path"))
        }
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(directory)
    }

    @Test
    fun anUnversionedDatabaseWithTablesIsRefused() {
        val directory = Files.createTempDirectory("openhealth-unversioned")
        val path = directory.resolve("unversioned.db").toString()
        val first = JdbcSqliteDriver("jdbc:sqlite:$path", Properties())
        OpenHealthDb.Schema.create(first).value
        first.execute(null, "PRAGMA user_version = 0", 0)
        first.close()
        assertFailsWith<IllegalStateException> {
            DatabaseOpener().open(jdbcFactory("jdbc:sqlite:$path"))
        }
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(directory)
    }

    @Test
    fun aV2DatabaseGainsItsForeignKeysAndKeepsEveryRow() {
        val directory = Files.createTempDirectory("openhealth-v2")
        val path = directory.resolve("v2.db").toString()

        // v2 is this schema without the REFERENCES clauses, so strip them from the stored
        // DDL and rebuild. The fixture therefore tracks the schema instead of drifting.
        val first = JdbcSqliteDriver("jdbc:sqlite:$path", Properties())
        OpenHealthDb.Schema.create(first).value
        dropTimelineView(first)
        stripV7Columns(first)
        stripV6Columns(first)
        stripV5Columns(first)
        // The note table arrives in v4; this fixture must really be older than it.
        first.execute(null, "DROP TABLE note", 0)
        val ddl = tableDdl(first)
        first.execute(null, "PRAGMA foreign_keys = OFF", 0)
        ddl.forEach { (table, sql) ->
            if (!sql.contains("REFERENCES")) return@forEach
            first.execute(null, "CREATE TABLE copy_$table AS SELECT * FROM $table", 0)
            first.execute(null, "DROP TABLE $table", 0)
            first.execute(null, REFERENCES.replace(sql, ""), 0)
            first.execute(null, "INSERT INTO $table SELECT * FROM copy_$table", 0)
            first.execute(null, "DROP TABLE copy_$table", 0)
        }
        assertEquals(
            0,
            tableDdl(first).filterKeys { !it.startsWith("copy_") }
                .count { it.value.contains("REFERENCES") },
        )

        // One row per layer, including a child that the v3 keys will have to accept.
        val db = openHealthDatabase(first)
        seedSources(app.openhealth.data.db.OpenHealthStore(first, db, 2L))
        db.observationQueries.insertObservation(
            id = "o-1", type = "STEPS", value = 8000.0, unit = "{step}",
            startAt = 0L, endAt = 86_400_000L, localDate = "2026-01-01", zoneId = TEST_ZONE.id,
            happenedAt = 0L, recordedAt = 1L, sourceId = "garmin", device = null,
            appVersion = null, method = null, context = null, confidence = null,
            quality = null, userVerified = false, userEdited = false, originalValue = null,
            originalUnit = null, editReason = null, editedAt = null, groupKey = null,
            excludedFromTrends = false, idempotencyKey = "k-1", rawRecordId = null,
        )
        createV6Timeline(first)
        first.execute(null, "PRAGMA user_version = 2", 0)
        first.close()

        val store = DatabaseOpener(MigrationBackup { from, to -> "backup-$from-$to" }).open(
            jdbcFactory("jdbc:sqlite:$path"),
        )
        assertEquals(11L, store.schemaVersion)
        assertEquals(1L, store.db.observationQueries.countObservations().executeAsOne())
        assertEquals(3, store.db.sourceQueries.allSources().executeAsList().size)

        // The keys are declared, and they bite: an observation may not name a source
        // that does not exist, and a source with history may not be deleted.
        val withKeys = tableDdl(store.driver)
        assertTrue(withKeys.getValue("observation").contains("REFERENCES source"))
        assertTrue(withKeys.getValue("work_set").contains("REFERENCES session"))
        assertFailsWith<Exception> {
            store.driver.execute(
                null,
                "INSERT INTO observation(id, type, value, unit, start_at, local_date, " +
                    "zone_id, happened_at, recorded_at, source_id, idempotency_key) VALUES " +
                    "('o-2','STEPS',1.0,'{step}',0,'2026-01-01','UTC',0,0,'ghost','k-2')",
                0,
            )
        }
        assertFailsWith<Exception> {
            store.driver.execute(null, "DELETE FROM source WHERE id = 'garmin'", 0)
        }
        store.close()
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(java.nio.file.Path.of("$path-shm"))
        Files.deleteIfExists(java.nio.file.Path.of("$path-wal"))
        Files.deleteIfExists(directory)
    }

    @Test
    fun aV6DiarySurvivesTheSevenMigration() {
        val directory = Files.createTempDirectory("openhealth-v6-diary")
        val path = directory.resolve("v6.db").toString()
        val first = JdbcSqliteDriver("jdbc:sqlite:$path", Properties())
        OpenHealthDb.Schema.create(first).value
        dropTimelineView(first)
        stripV7Columns(first)
        createV6Timeline(first)
        first.execute(
            null,
            "INSERT INTO food(id, name, source_kind, energy_kcal_per_100g, " +
                "protein_g_per_100g, carbs_g_per_100g, fat_g_per_100g, " +
                "fibre_g_per_100g, verified, created_at, updated_at, revision) VALUES " +
                "('legacy-food','Legacy oats','manual',389,16.9,66,7,10,0,0,0,1)",
            0,
        )
        first.execute(
            null,
            "INSERT INTO meal(id, name, kind, local_date, notes, created_at, " +
                "updated_at, revision) VALUES " +
                "('legacy-meal','Breakfast','breakfast','2026-01-01',NULL,0,0,1)",
            0,
        )
        first.execute(
            null,
            "INSERT INTO entry(id, meal_id, food_id, local_date, zone_id, happened_at, " +
                "recorded_at, quantity_g, energy_kcal, protein_g, carbs_g, fat_g, fibre_g, " +
                "source_kind, source_id, user_verified, user_edited, idempotency_key, " +
                "revision) VALUES ('legacy-entry','legacy-meal','legacy-food','2026-01-01'," +
                "'Europe/Berlin',0,0,100,389,16.9,66,7,10,'manual',NULL,0,0," +
                "'legacy-entry',1)",
            0,
        )
        first.execute(null, "PRAGMA user_version = 6", 0)
        first.close()

        val store = DatabaseOpener(MigrationBackup { from, to -> "backup-$from-$to" }).open(
            jdbcFactory("jdbc:sqlite:$path"),
        )
        assertEquals(11L, store.schemaVersion)
        val repository = app.openhealth.data.repo.NutritionRepository(store.db)
        val food = repository.foodById("legacy-food")!!
        assertEquals(389.0, food.per100g[app.openhealth.core.nutrition.Nutrient.ENERGY])
        assertEquals(16.9, food.per100g[app.openhealth.core.nutrition.Nutrient.PROTEIN])
        val meal = repository.mealById("legacy-meal")!!
        assertEquals(kotlinx.datetime.LocalDate(2026, 1, 1), meal.localDate)
        assertEquals(null, meal.zoneId)
        val entry = repository.entryById("legacy-entry")!!
        assertEquals(389.0, entry.totals.of(app.openhealth.core.nutrition.Nutrient.ENERGY))
        assertEquals(66.0, entry.totals.of(app.openhealth.core.nutrition.Nutrient.CARBS))
        assertTrue(
            app.openhealth.data.repo.TimelineRepository(store.db, store.driver)
                .page(10).any { it.kind == "entry" },
        )
        store.close()
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(directory)
    }

    @Test
    fun aV7DatabaseGainsThePhotoTableAndItsTimelineBranch() {
        val directory = Files.createTempDirectory("openhealth-v7-photo")
        val path = directory.resolve("v7.db").toString()
        val first = JdbcSqliteDriver("jdbc:sqlite:$path", Properties())
        OpenHealthDb.Schema.create(first).value
        dropTimelineView(first)
        first.execute(null, "DROP TABLE body_photo", 0)
        createV6Timeline(first)
        first.execute(null, "PRAGMA user_version = 7", 0)
        first.close()

        val store = DatabaseOpener(MigrationBackup { from, to -> "backup-$from-$to" }).open(
            jdbcFactory("jdbc:sqlite:$path"),
        )
        assertEquals(11L, store.schemaVersion)
        seedSources(store)
        store.db.bodyQueries.insertPhoto(
            id = "photo-1",
            localDate = "2026-11-25",
            zoneId = TEST_ZONE.id,
            happenedAt = 0L,
            recordedAt = 1L,
            sourceId = "manual",
            caption = null,
            notes = null,
            mime = "image/jpeg",
            width = 10L,
            height = 10L,
            bytesB64 = "AA==",
            thumbB64 = "AA==",
            sha256 = "abc",
            idempotencyKey = "photo-1",
            device = null,
            appVersion = null,
            revision = 1L,
        )
        assertEquals(1L, store.db.bodyQueries.countPhotos().executeAsOne())
        assertTrue(
            app.openhealth.data.repo.TimelineRepository(store.db, store.driver)
                .page(10).any { it.kind == "body_photo" },
        )
        store.close()
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(java.nio.file.Path.of("$path-shm"))
        Files.deleteIfExists(java.nio.file.Path.of("$path-wal"))
        Files.deleteIfExists(directory)
    }

    @Test
    fun aV4DatabaseGainsTheExerciseColumnsAndKeepsItsRows() {
        val directory = Files.createTempDirectory("openhealth-v4-exercise")
        val path = directory.resolve("v4.db").toString()
        val first = JdbcSqliteDriver("jdbc:sqlite:$path", Properties())
        OpenHealthDb.Schema.create(first).value
        dropTimelineView(first)
        stripV7Columns(first)
        stripV6Columns(first)
        stripV5Columns(first)
        first.execute(
            null,
            "INSERT INTO exercise(id, name, primary_muscles, secondary_muscles, is_custom, " +
                "created_at, updated_at, revision) VALUES " +
                "('legacy','Legacy press','chest','',0,0,0,1)",
            0,
        )
        // B3's demo corpus wrote muscle names that are not in the whole-muscle taxonomy.
        // Until 4.sqm normalises them, those sessions colour nothing on the map.
        LEGACY_MUSCLES.forEach { (id, stored) ->
            first.execute(
                null,
                "INSERT INTO exercise(id, name, primary_muscles, secondary_muscles, " +
                    "is_custom, created_at, updated_at, revision) VALUES " +
                    "('$id','$id','$stored','',0,0,0,1)",
                0,
            )
        }
        createV6Timeline(first)
        first.execute(null, "PRAGMA user_version = 4", 0)
        first.close()

        val store = DatabaseOpener(MigrationBackup { from, to -> "backup-$from-$to" }).open(
            jdbcFactory("jdbc:sqlite:$path"),
        )
        assertEquals(11L, store.schemaVersion)
        val columns = store.driver.executeQuery(
            null,
            "PRAGMA table_info(exercise)",
            { cursor ->
                val names = mutableListOf<String>()
                while (cursor.next().value) names += cursor.getString(1).orEmpty()
                QueryResult.Value(names)
            },
            0,
        ).value
        listOf("level", "mechanic", "force").forEach { assertTrue(it in columns, "column $it") }
        val row = store.db.trainingQueries.exerciseById("legacy").executeAsOne()
        assertEquals("Legacy press", row.name)
        assertEquals(null, row.level)
        LEGACY_MUSCLES.keys.forEach { id ->
            val stored = store.db.trainingQueries.exerciseById(id).executeAsOne().primary_muscles
            assertEquals(
                LEGACY_EXPECTED.getValue(id),
                stored,
                "$id was not normalised to the taxonomy",
            )
            assertNotNull(
                app.openhealth.core.body.Muscle.fromKey(stored),
                "$id does not name a whole muscle",
            )
        }
        store.close()
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(directory)
    }

    @Test
    fun theSchemaExposesEveryLayer() {
        val store = inMemoryStore()
        val tables = store.db.transactionWithResult {
            store.driver.executeQuery(
                null,
                "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name",
                { cursor ->
                    val names = mutableListOf<String>()
                    while (cursor.next().value) names += cursor.getString(0).orEmpty()
                    QueryResult.Value(names)
                },
                0,
            ).value
        }
        listOf(
            "raw_record", "observation", "daily_rollup", "profile", "source", "settings",
            "tombstone", "exercise", "routine", "routine_exercise", "programme",
            "programme_routine", "session", "session_exercise", "work_set", "food",
            "meal", "entry", "measurement", "vital", "note",
        ).forEach { assertTrue(it in tables, "missing table $it") }
        val views = store.db.transactionWithResult {
            store.driver.executeQuery(
                null,
                "SELECT name FROM sqlite_master WHERE type = 'view' ORDER BY name",
                { cursor ->
                    val names = mutableListOf<String>()
                    while (cursor.next().value) names += cursor.getString(0).orEmpty()
                    QueryResult.Value(names)
                },
                0,
            ).value
        }
        assertTrue("timeline" in views)
        store.close()
    }

    /** Every stored table's CREATE statement, by table name. */
    private fun tableDdl(driver: app.cash.sqldelight.db.SqlDriver): Map<String, String> =
        driver.executeQuery(
            null,
            "SELECT name, sql FROM sqlite_master WHERE type = 'table' " +
                "AND name NOT LIKE 'sqlite_%' AND sql IS NOT NULL",
            { cursor ->
                val rows = mutableMapOf<String, String>()
                while (cursor.next().value) {
                    rows[cursor.getString(0).orEmpty()] = cursor.getString(1).orEmpty()
                }
                QueryResult.Value(rows)
            },
            0,
        ).value

    private fun userVersion(driver: app.cash.sqldelight.db.SqlDriver): Long = driver.executeQuery(
        null,
        "PRAGMA user_version",
        { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
        0,
    ).value

    private fun indexCount(driver: app.cash.sqldelight.db.SqlDriver, name: String): Long =
        driver.executeQuery(
            null,
            "SELECT count(*) FROM sqlite_master WHERE type = 'index' AND name = '$name'",
            { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
            },
            0,
        ).value

    /** v5 added three nullable exercise columns; a pre-v5 fixture must not have them. */
    private fun stripV6Columns(driver: JdbcSqliteDriver) {
        // v5 is this schema without the B5 training columns. Dropping them (after their
        // indexes) reproduces a genuine pre-B5 database without drifting from the schema.
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
                "is_drop", "is_assisted", "assistance_kg", "band_kg", "notes",
                "original_weight_kg", "original_reps", "edit_reason", "edited_at",
                "user_edited",
            ),
        ).forEach { (table, columns) ->
            columns.forEach { column ->
                driver.execute(null, "ALTER TABLE $table DROP COLUMN $column", 0)
            }
        }
    }

    private fun dropTimelineView(driver: app.cash.sqldelight.db.SqlDriver) {
        driver.execute(null, "DROP VIEW IF EXISTS timeline", 0)
    }

    /** Removes the B6 columns and tables, leaving a genuine v6-shaped database. */
    private fun stripV7Columns(driver: app.cash.sqldelight.db.SqlDriver) {
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
    }

    /** The timeline as v6 left it: meals by created_at, and no fasting window. */
    private fun createV6Timeline(driver: app.cash.sqldelight.db.SqlDriver) {
        driver.execute(
            null,
            """
            CREATE VIEW timeline AS
            SELECT 'observation' AS kind, id AS entity_id, local_date AS local_date,
                happened_at AS happened_at, recorded_at AS recorded_at,
                source_id AS source_id, type AS detail, user_edited AS edited
            FROM observation
            UNION ALL
            SELECT 'session', id, local_date, happened_at, recorded_at, source_id, routine_id, 0
            FROM session
            UNION ALL
            SELECT 'work_set', work_set.id, session.local_date, work_set.happened_at,
                work_set.recorded_at, work_set.source_id, work_set.exercise_id, 0
            FROM work_set JOIN session ON session.id = work_set.session_id
            UNION ALL
            SELECT 'entry', id, local_date, happened_at, recorded_at, source_kind, meal_id, user_edited
            FROM entry
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

    @Test
    fun aV8DatabaseGainsActivitiesAndTheirTimelineBranch() {
        val directory = Files.createTempDirectory("openhealth-v8-activity")
        val path = directory.resolve("v8.db").toString()
        val first = JdbcSqliteDriver("jdbc:sqlite:$path", Properties())
        OpenHealthDb.Schema.create(first).value
        dropTimelineView(first)
        first.execute(null, "DROP TABLE activity_point", 0)
        first.execute(null, "DROP TABLE activity", 0)
        createV6Timeline(first)
        first.execute(null, "PRAGMA user_version = 8", 0)
        first.close()

        val store = DatabaseOpener(MigrationBackup { from, to -> "backup-$from-$to" }).open(
            jdbcFactory("jdbc:sqlite:$path"),
        )
        assertEquals(11L, store.schemaVersion)
        seedSources(store)
        store.driver.execute(
            null,
            """
            INSERT INTO activity(
                id, type, source, started_at, ended_at, duration_s, local_date, zone_id,
                happened_at, recorded_at, distance_m, source_id, idempotency_key,
                created_at, updated_at
            ) VALUES (
                'activity-1', 'RUN', 'RECORDED', 0, 60000, 60, '2026-11-24', 'Europe/Berlin',
                0, 1000, 1000.0, 'manual', 'activity-1', 1000, 1000
            )
            """.trimIndent(),
            0,
        )
        store.driver.execute(
            null,
            "INSERT INTO activity_point(activity_id, position, latitude, longitude, " +
                "elapsed_s) VALUES ('activity-1', 0, 52.52, 13.40, 0)",
            0,
        )
        assertEquals(1L, store.db.activityQueries.countActivities().executeAsOne())
        assertTrue(
            app.openhealth.data.repo.TimelineRepository(store.db, store.driver)
                .page(10).any { it.kind == "activity" },
        )
        store.close()
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(java.nio.file.Path.of("$path-shm"))
        Files.deleteIfExists(java.nio.file.Path.of("$path-wal"))
        Files.deleteIfExists(directory)
    }

    private fun stripV5Columns(driver: app.cash.sqldelight.db.SqlDriver) {
        listOf("level", "mechanic", "force").forEach {
            driver.execute(null, "ALTER TABLE exercise DROP COLUMN $it", 0)
        }
    }

    private companion object {
        /** What B3's demo corpus stored, and the taxonomy key 4.sqm has to turn it into. */
        val LEGACY_MUSCLES = mapOf(
            "legacy-back" to "back",
            "legacy-shoulders" to "shoulders",
            "legacy-glutes" to "glutes",
        )

        val LEGACY_EXPECTED = mapOf(
            "legacy-back" to "upper-back",
            "legacy-shoulders" to "deltoids",
            "legacy-glutes" to "gluteal",
        )

        /** The inline foreign key clause this schema adds at v3. */
        val REFERENCES =
            Regex(""" REFERENCES \w+\(\w+\)( ON DELETE (RESTRICT|SET NULL|CASCADE))?""")

        val V2_INDEXES = listOf(
            "observation_type_local_date",
            "observation_happened",
            "session_happened",
            "entry_happened",
            "measurement_happened",
            "vital_happened",
        )
    }
}
