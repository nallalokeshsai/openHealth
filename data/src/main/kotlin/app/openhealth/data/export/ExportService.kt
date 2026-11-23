package app.openhealth.data.export

import app.cash.sqldelight.db.SqlDriver
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.ExportCipher
import app.openhealth.core.seam.SystemClock
import app.openhealth.core.seam.UnsupportedExportFormat
import app.openhealth.data.db.OpenHealthDb
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
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun snapshot(): ExportBundle {
        val tables = db.transactionWithResult {
            ExportTables.all.map { SqlDump.dump(driver, it) }
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

    private fun requireSupportedSchema(bundle: ExportBundle) {
        if (bundle.schemaVersion != OpenHealthDb.Schema.version) {
            throw UnsupportedExportFormat(
                "Export schema ${bundle.schemaVersion} does not match this app's " +
                    OpenHealthDb.Schema.version,
            )
        }
    }

    fun export(passphrase: CharArray): ByteArray = cipher.seal(encode(snapshot()), passphrase)

    fun read(container: ByteArray, passphrase: CharArray): ExportBundle =
        decode(cipher.open(container, passphrase))

    fun restore(container: ByteArray, passphrase: CharArray): RestoreReport =
        restoreBundle(read(container, passphrase))

    fun restoreBundle(bundle: ExportBundle): RestoreReport {
        val byName = bundle.tables.associateBy { it.table }
        val known = ExportTables.all.map { table -> table to byName[table.name] }
        val unknown = byName.keys - ExportTables.all.map { it.name }.toSet()
        if (unknown.isNotEmpty()) {
            throw UnsupportedExportFormat("Export has unknown tables: ${unknown.sorted()}")
        }
        val counts = db.transactionWithResult {
            ExportTables.all.asReversed().forEach { SqlDump.clear(driver, it) }
            var rows = 0L
            known.forEach { (table, data) ->
                val rowsOfTable = data?.rows.orEmpty()
                SqlDump.insert(driver, table, rowsOfTable)
                rows += rowsOfTable.size
            }
            rows
        }
        return RestoreReport(
            tables = ExportTables.all.size,
            rows = counts,
            schemaVersion = OpenHealthDb.Schema.version,
        )
    }
}
