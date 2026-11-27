package app.openhealth.data.export

import app.cash.sqldelight.db.SqlDriver
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.ExportCipher
import app.openhealth.core.seam.SystemClock
import app.openhealth.core.seam.UnsupportedExportFormat
import app.openhealth.core.source.SourceRegistry
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.repo.ObservationRepository
import app.openhealth.data.repo.RollupRepository
import app.openhealth.data.repo.SourceRepository
import app.openhealth.data.rollup.RollupEngine
import kotlinx.serialization.json.Json

/**
 * Export and restore of every layer (`pr.md` §6.29, §7.7). The snapshot is taken inside
 * one transaction so it cannot see a half-written import; restore clears and reloads
 * every table in the same transaction, so a failed restore leaves the old database.
 */
class ExportService(
    private val driver: SqlDriver,
    private val db: OpenHealthDb,
    private val cipher: ExportCipher,
    private val clock: ClockSeam = SystemClock,
    /** `DESIGN.md` §9.4: photos are excluded from a general export unless included. */
    private val includePhotos: () -> Boolean = { false },
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun snapshot(): ExportBundle {
        val tables = db.transactionWithResult {
            ExportTables.all.map { table ->
                if (table.name == PHOTO_TABLE && !includePhotos()) {
                    ExportTableData(table.name, emptyList())
                } else {
                    SqlDump.dump(driver, table)
                }
            }
        }
        return ExportBundle(
            format = ExportBundle.FORMAT,
            version = ExportBundle.VERSION,
            schemaVersion = OpenHealthDb.Schema.version,
            exportedAt = clock.now().toString(),
            tables = tables,
        )
    }

    fun encode(bundle: ExportBundle): ByteArray =
        json.encodeToString(ExportBundle.serializer(), bundle).encodeToByteArray()

    fun decode(bytes: ByteArray): ExportBundle {
        val bundle = json.decodeFromString(ExportBundle.serializer(), bytes.decodeToString())
        requireSupportedFormat(bundle)
        requireSupportedSchema(bundle)
        return bundle
    }

    private fun requireSupportedFormat(bundle: ExportBundle) {
        if (bundle.format != ExportBundle.FORMAT || bundle.version > ExportBundle.VERSION) {
            throw UnsupportedExportFormat(
                "Not a supported openHealth export: ${bundle.format} v${bundle.version}",
            )
        }
    }

    /**
     * An export from an older schema is accepted and restored into the current one: the
     * dump names its columns, and a column that did not exist then takes its default. A
     * newer export is refused, because this build cannot know what it means.
     */
    private fun requireSupportedSchema(bundle: ExportBundle) {
        if (bundle.schemaVersion > OpenHealthDb.Schema.version) {
            throw UnsupportedExportFormat(
                "Export schema ${bundle.schemaVersion} is newer than this app's " +
                    OpenHealthDb.Schema.version,
            )
        }
    }

    private companion object {
        const val ROLLUP_TABLE = "daily_rollup"
        const val PHOTO_TABLE = "body_photo"
    }

    fun export(passphrase: CharArray): ByteArray = try {
        cipher.seal(encode(snapshot()), passphrase)
    } finally {
        passphrase.fill('\u0000')
    }

    fun read(container: ByteArray, passphrase: CharArray): ExportBundle = try {
        decode(cipher.open(container, passphrase))
    } finally {
        passphrase.fill('\u0000')
    }

    fun restore(container: ByteArray, passphrase: CharArray): RestoreReport =
        restoreBundle(read(container, passphrase))

    fun restoreBundle(bundle: ExportBundle): RestoreReport {
        requireSupportedFormat(bundle)
        requireSupportedSchema(bundle)
        val byName = bundle.tables.associateBy { it.table }
        val expected = ExportTables.all.map { it.name }.toSet()
        // A table added after an older export was written is restored empty, not
        // treated as corruption: the person's old backup still restores and the new
        // table starts clean. Column additions already default the same way.
        val optional = setOf(
            "body_photo",
            // B15 tables, absent from every backup written before schema v12.
            "coach_message",
            "coach_proposal",
            "coach_action",
            "coach_snapshot",
            "mcp_token",
            "mcp_access",
        )
        val missing = expected - byName.keys - optional
        val unknown = byName.keys - expected
        if (missing.isNotEmpty() || unknown.isNotEmpty()) {
            throw UnsupportedExportFormat(
                "Export tables do not match this app (missing ${missing.sorted()}, " +
                    "unknown ${unknown.sorted()})",
            )
        }
        val counts = db.transactionWithResult {
            // Foreign keys are checked at COMMIT rather than per row, so a bundle can be
            // reloaded table by table without ordering parents before children. A
            // genuinely inconsistent bundle still fails, and fails the whole restore.
            driver.execute(null, "PRAGMA defer_foreign_keys = ON", 0)
            ExportTables.all.asReversed().forEach { SqlDump.clear(driver, it) }
            var rows = 0L
            // Rollups are derived and never restored from the file; they are rebuilt
            // from the observations after the transaction (`Tech-Stack.md` §5-6).
            ExportTables.all
                .filterNot { it.name == ROLLUP_TABLE }
                .forEach { table ->
                    val rowsOfTable = byName[table.name]?.rows ?: emptyList()
                    SqlDump.insert(driver, table, rowsOfTable)
                    rows += rowsOfTable.size
                }
            // A backup written before v12 has no transport flag and no revoked token
            // state. The two known platform sources are platform-derived by definition,
            // and a restored bearer token must be reissued rather than silently revived.
            driver.execute(
                null,
                "UPDATE source SET via_health_connect = 1 " +
                    "WHERE id IN ('health-connect', 'garmin-hc')",
                0,
            )
            driver.execute(
                null,
                "UPDATE mcp_token SET revoked_at = ${clock.now().toEpochMilliseconds()}",
                0,
            )
            // Rebuild in the same transaction: a failed rebuild leaves the old database.
            RollupEngine(
                observations = ObservationRepository(db),
                rollups = RollupRepository(db),
                db = db,
                registry = SourceRegistry(SourceRepository(db, clock).all()),
                clock = clock,
            ).rebuildAll()
            rows
        }
        return RestoreReport(
            tables = ExportTables.all.size,
            rows = counts,
            schemaVersion = OpenHealthDb.Schema.version,
        )
    }
}
