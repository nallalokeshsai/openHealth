package app.openhealth.data.export

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * A driver-level dump and load, so export covers every column without a hand-written
 * mapper per table that could silently miss one. Column names and types come from
 * [ExportTables]; values move as JSON scalars.
 */
object SqlDump {
    fun dump(driver: SqlDriver, table: ExportTable): ExportTableData = ExportTableData(
        table = table.name,
        rows = driver.executeQuery(
            identifier = null,
            sql = "SELECT ${table.columns.joinToString(", ") {
                it.name
            }} FROM ${table.name} ORDER BY rowid",
            mapper = { cursor ->
                val rows = mutableListOf<Map<String, JsonElement>>()
                while (cursor.next().value) {
                    val row = LinkedHashMap<String, JsonElement>(table.columns.size)
                    table.columns.forEachIndexed { index, column ->
                        row[column.name] = when (column.type) {
                            ExportType.TEXT -> cursor.getString(index)?.let(::JsonPrimitive)
                                ?: JsonNull
                            ExportType.LONG -> cursor.getLong(index)?.let(::JsonPrimitive)
                                ?: JsonNull
                            ExportType.DOUBLE -> cursor.getDouble(index)?.let(::JsonPrimitive)
                                ?: JsonNull
                            ExportType.BOOLEAN -> cursor.getBoolean(index)?.let(::JsonPrimitive)
                                ?: JsonNull
                        }
                    }
                    rows += row
                }
                QueryResult.Value(rows)
            },
            parameters = 0,
        ).value,
    )

    fun insert(driver: SqlDriver, table: ExportTable, rows: List<Map<String, JsonElement>>) {
        val columns = table.columns.joinToString(", ") { it.name }
        val placeholders = table.columns.joinToString(", ") { "?" }
        val sql = "INSERT OR REPLACE INTO ${table.name} ($columns) VALUES ($placeholders)"
        rows.forEach { row ->
            driver.execute(null, sql, table.columns.size) {
                table.columns.forEachIndexed { index, column ->
                    val value = row[column.name] ?: JsonNull
                    when (column.type) {
                        ExportType.TEXT -> bindString(index, value.textOrNull())
                        ExportType.LONG -> bindLong(index, value.longValue())
                        ExportType.DOUBLE -> bindDouble(index, value.doubleValue())
                        ExportType.BOOLEAN -> bindBoolean(index, value.booleanValue())
                    }
                }
            }
        }
    }

    fun clear(driver: SqlDriver, table: ExportTable) {
        driver.execute(null, "DELETE FROM ${table.name}", 0)
    }

    private fun JsonElement.textOrNull(): String? =
        if (this is JsonNull) null else (this as? JsonPrimitive)?.content

    private fun JsonElement.longValue(): Long? = (this as? JsonPrimitive)?.longOrNull

    private fun JsonElement.doubleValue(): Double? = (this as? JsonPrimitive)?.doubleOrNull

    private fun JsonElement.booleanValue(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull
}
