package app.openhealth.data.sync

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

/**
 * Reads and writes a domain row as a map of column name to text, so the change log can
 * carry any entity without a hand-written DTO per table.
 *
 * SQLite's column affinity converts the text back to the declared type on write, and a
 * real `NULL` stays null. Blob tables are not syncable in B13 (media is content-addressed
 * and uploaded separately, `pr.md` §7.7), so no value here is binary.
 */
class SyncRowCodec(private val driver: SqlDriver) {
    private val columnCache = mutableMapOf<String, List<String>>()

    fun columns(table: String): List<String> = columnCache.getOrPut(table) {
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA table_info($table)",
            mapper = { cursor ->
                val names = mutableListOf<String>()
                while (cursor.next().value) {
                    cursor.getString(PRAGMA_NAME_COLUMN)?.let(names::add)
                }
                QueryResult.Value(names)
            },
            parameters = 0,
        ).value
    }

    /** Every key in a table, for the one-time pre-v11 backfill. */
    fun allKeys(table: app.openhealth.data.sync.SyncTable): List<List<String>> =
        driver.executeQuery(
            identifier = null,
            sql = "SELECT ${table.keys.joinToString(", ")} FROM ${table.table}",
            mapper = { cursor ->
                val rows = mutableListOf<List<String>>()
                while (cursor.next().value) {
                    rows += table.keys.indices.map { index -> cursor.getString(index).orEmpty() }
                }
                QueryResult.Value(rows)
            },
            parameters = 0,
        ).value

    fun read(table: String, keys: List<String>, keyValues: List<String>): Map<String, String?>? {
        require(keys.size == keyValues.size) { "key column/value count differs" }
        val where = keys.joinToString(" AND ") { "$it = ?" }
        return driver.executeQuery(
            identifier = null,
            sql = "SELECT * FROM $table WHERE $where LIMIT 1",
            mapper = { cursor ->
                if (!cursor.next().value) {
                    QueryResult.Value(null)
                } else {
                    val columns = columns(table)
                    val row = LinkedHashMap<String, String?>(columns.size)
                    columns.forEachIndexed { index, column ->
                        row[column] = cursor.getString(index)
                    }
                    QueryResult.Value(row)
                }
            },
            parameters = keyValues.size,
        ) {
            keyValues.forEachIndexed { index, value -> bindString(index, value) }
        }.value
    }

    /** Update the row, or insert it when it does not exist yet. */
    @Suppress("ReturnCount")
    fun write(table: String, keys: List<String>, fields: Map<String, String?>): Boolean {
        val actual = columns(table)
        val present = actual.filter { it in fields }
        if (present.isEmpty()) return false
        val writable = present.filter { it !in keys }
        val where = keys.joinToString(" AND ") { "$it = ?" }
        val keyValues = keys.map { key -> fields[key] ?: return false }
        if (writable.isNotEmpty()) {
            val assignments = writable.joinToString(", ") { "$it = ?" }
            driver.execute(
                identifier = null,
                sql = "UPDATE $table SET $assignments WHERE $where",
                parameters = writable.size + keys.size,
            ) {
                var index = 0
                writable.forEach { column ->
                    bindStringOrNull(index, fields[column])
                    index += 1
                }
                keyValues.forEach { value ->
                    bindString(index, value)
                    index += 1
                }
            }
        }
        // When every column is a key there is nothing to update, and `changes()` would
        // report the previous statement on the connection; go straight to the insert.
        val changed = if (writable.isEmpty()) {
            0L
        } else {
            driver.executeQuery(
                identifier = null,
                sql = "SELECT changes()",
                mapper = { cursor ->
                    cursor.next()
                    QueryResult.Value(cursor.getLong(0) ?: 0L)
                },
                parameters = 0,
            ).value
        }
        if (changed > 0L) return true
        val columns = present
        val placeholders = columns.joinToString(", ") { "?" }
        driver.execute(
            identifier = null,
            sql = "INSERT INTO $table(${columns.joinToString(", ")}) VALUES ($placeholders)",
            parameters = columns.size,
        ) {
            columns.forEachIndexed { index, column ->
                bindStringOrNull(index, fields[column])
            }
        }
        return true
    }

    fun delete(table: String, keys: List<String>, keyValues: List<String>): Boolean {
        val where = keys.joinToString(" AND ") { "$it = ?" }
        driver.execute(
            identifier = null,
            sql = "DELETE FROM $table WHERE $where",
            parameters = keyValues.size,
        ) {
            keyValues.forEachIndexed { index, value -> bindString(index, value) }
        }
        return driver.executeQuery(
            identifier = null,
            sql = "SELECT changes()",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0) ?: 0L)
            },
            parameters = 0,
        ).value > 0L
    }

    private fun app.cash.sqldelight.db.SqlPreparedStatement.bindStringOrNull(
        index: Int,
        value: String?,
    ) {
        bindString(index, value)
    }

    companion object {
        private const val PRAGMA_NAME_COLUMN = 1

        /** The change key for a composite row id; neither a UUID nor a table id has `#`. */
        fun entityId(values: List<String>): String = values.joinToString("#")

        fun keyValues(entityId: String, keyCount: Int): List<String> =
            if (keyCount <= 1) listOf(entityId) else entityId.split("#")
    }
}
