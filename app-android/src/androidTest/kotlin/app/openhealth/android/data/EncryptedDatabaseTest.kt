package app.openhealth.android.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.repo.ObservationRepository
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves on a real device that the SQLCipher driver and the Keystore-held passphrase
 * work together: a value survives close and reopen, and the file on disk is not a plain
 * SQLite database. Run with `./gradlew :app-android:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clean() {
        context.deleteDatabase(DATABASE)
        context.deleteDatabase(RESTORED)
        context.getSharedPreferences(VAULT, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(DATABASE)
        context.deleteDatabase(RESTORED)
        context.getSharedPreferences(VAULT, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun aValueSurvivesCloseAndReopenAndTheFileIsEncrypted() {
        val vault = AndroidSecretVault(context, VAULT)
        val first = AndroidDatabaseFactory(context, vault, DATABASE).open()
        assertEquals("wal", app.openhealth.data.db.journalMode(first.driver).lowercase())
        assertForeignKeysEnforced(first)
        seedManualSource(first)
        val observation = weight()
        val outcome = ObservationRepository(first.db).record(observation)
        assertTrue(outcome is app.openhealth.data.repo.RecordOutcome.Inserted)
        first.close()

        val file = context.getDatabasePath(DATABASE)
        assertEquals(true, file.exists())
        val header = ByteArray(SQLITE_HEADER.size)
        file.inputStream().use { it.read(header) }
        assertFalse(
            "the database file must not start with the SQLite header",
            header.contentEquals(SQLITE_HEADER),
        )

        val reopened = AndroidDatabaseFactory(context, vault, DATABASE).open()
        assertForeignKeysEnforced(reopened)
        val stored = ObservationRepository(reopened.db).forDay(LocalDate(2026, 3, 1)).single()
        assertEquals(80.5, stored.value.value, 0.0001)
        assertEquals(observation.provenance.recordedAt, stored.provenance.recordedAt)
        exportAndRestoreOnDevice(reopened)
        reopened.close()
    }

    private fun exportAndRestoreOnDevice(source: app.openhealth.data.db.OpenHealthStore) {
        val cipher = app.openhealth.data.export.Argon2ExportCipher(
            iterations = 1,
            memoryKiB = 8_192,
            parallelism = 1,
        )
        val exporter = app.openhealth.data.export.ExportService(source.driver, source.db, cipher)
        val container = exporter.export("device passphrase".toCharArray())

        context.deleteDatabase(RESTORED)
        AndroidDatabaseFactory(context, vault(), RESTORED).open().use { restored ->
            val report = app.openhealth.data.export.ExportService(
                restored.driver,
                restored.db,
                cipher,
            )
                .restore(container, "device passphrase".toCharArray())
            assertTrue(report.rows > 0)
            val restoredObservation = ObservationRepository(restored.db)
                .forDay(LocalDate(2026, 3, 1))
                .single()
            assertEquals(80.5, restoredObservation.value.value, 0.0001)
        }
        context.deleteDatabase(RESTORED)
    }

    /**
     * SQLCipher's own SQLite, with Android's connection pool, is the only place the
     * foreign keys are really proved: `PRAGMA foreign_keys` is ignored on a live
     * connection, so enforcement comes from `setForeignKeyConstraintsEnabled` in the
     * open-helper callback and has to be read back on the device to be believed.
     */
    private fun assertForeignKeysEnforced(store: app.openhealth.data.db.OpenHealthStore) {
        val enforced = store.driver.executeQuery(
            identifier = null,
            sql = "PRAGMA foreign_keys",
            mapper = { cursor ->
                app.cash.sqldelight.db.QueryResult.Value(
                    if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L,
                )
            },
            parameters = 0,
        ).value
        assertEquals("foreign keys must be enforced on device", 1L, enforced)

        // And they must bite: an observation may not name a source that does not exist.
        var refused = false
        try {
            store.driver.execute(
                null,
                "INSERT INTO observation(id, type, value, unit, start_at, local_date, " +
                    "zone_id, happened_at, recorded_at, source_id, idempotency_key) VALUES " +
                    "('fk-probe','WEIGHT',1.0,'kg',0,'2026-03-01','UTC',0,0,'ghost','fk-probe')",
                0,
            )
        } catch (expected: Exception) {
            refused = true
        }
        assertTrue("a dangling source_id must be refused on device", refused)
    }

    /** `observation.source_id` is a foreign key, so the source has to exist first. */
    private fun seedManualSource(store: app.openhealth.data.db.OpenHealthStore) {
        app.openhealth.data.repo.SourceRepository(store.db).upsert(
            app.openhealth.core.source.Source(
                id = SourceId("manual"),
                kind = app.openhealth.core.observation.SourceKind.MANUAL,
                label = "Manual entry",
            ),
        )
    }

    private fun vault(): AndroidSecretVault = AndroidSecretVault(context, VAULT)

    private fun weight(): Observation {
        val happened = Instant.parse("2026-03-01T07:00:00Z")
        return Observation(
            id = ObservationId("instrumented-weight"),
            type = ObservationType.WEIGHT,
            value = Measure(80.5, UcumUnit.KILOGRAM),
            start = happened,
            end = null,
            provenance = Provenance(
                sourceId = SourceId("manual"),
                time = TimeProvenance.of(happened, happened, TimeZone.of("Europe/Berlin")),
                idempotencyKey = "instrumented-weight",
                method = app.openhealth.core.observation.MeasurementMethod.MANUAL_ENTRY,
            ),
        )
    }

    private companion object {
        const val DATABASE = "openhealth-instrumented.db"
        const val RESTORED = "openhealth-instrumented-restore.db"
        const val VAULT = "openhealth.instrumented.vault"
        val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
