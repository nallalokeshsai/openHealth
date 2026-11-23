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
        context.getSharedPreferences(VAULT, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(DATABASE)
        context.getSharedPreferences(VAULT, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun aValueSurvivesCloseAndReopenAndTheFileIsEncrypted() {
        val vault = AndroidSecretVault(context, VAULT)
        val first = AndroidDatabaseFactory(context, vault, DATABASE).open()
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
        val stored = ObservationRepository(reopened.db).forDay(LocalDate(2026, 3, 1)).single()
        assertEquals(80.5, stored.value.value, 0.0001)
        assertEquals(observation.provenance.recordedAt, stored.provenance.recordedAt)
        reopened.close()
    }

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
        const val VAULT = "openhealth.instrumented.vault"
        val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
