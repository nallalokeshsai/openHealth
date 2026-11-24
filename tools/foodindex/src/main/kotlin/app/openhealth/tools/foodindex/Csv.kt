@file:Suppress(
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "ReturnCount",
    "LoopWithTooManyJumpStatements",
    "MagicNumber",
    "MatchingDeclarationName",
)

package app.openhealth.tools.foodindex

import java.io.Reader

/**
 * A small RFC 4180 reader. USDA's CSV files quote anything containing a comma, and a
 * quoted field can contain a line break, so splitting on `\n` is not enough.
 */
class CsvReader(private val reader: Reader) : AutoCloseable {
    private var pending: Int = -2

    fun readRecord(): List<String>? {
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var sawAnything = false
        while (true) {
            val next = read()
            if (next == -1) {
                if (!sawAnything && fields.isEmpty() && field.isEmpty()) return null
                fields += field.toString()
                return fields
            }
            sawAnything = true
            val c = next.toChar()
            if (inQuotes) {
                when (c) {
                    '"' -> {
                        val after = read()
                        if (after == '"'.code) {
                            field.append('"')
                        } else {
                            inQuotes = false
                            pending = after
                        }
                    }

                    else -> field.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> {
                        fields += field.toString()
                        field.setLength(0)
                    }

                    '\r' -> {
                        val after = read()
                        if (after != '\n'.code) pending = after
                        fields += field.toString()
                        return fields
                    }

                    '\n' -> {
                        fields += field.toString()
                        return fields
                    }

                    else -> field.append(c)
                }
            }
        }
    }

    fun readAll(): List<List<String>> = buildList {
        while (true) {
            val record = readRecord() ?: break
            if (record.isNotEmpty() && record.any { it.isNotBlank() }) add(record)
        }
    }

    private fun read(): Int {
        if (pending != -2) {
            val value = pending
            pending = -2
            return value
        }
        return reader.read()
    }

    override fun close() {
        reader.close()
    }
}

/** Reads a CSV file into header-keyed records without holding the whole file in memory. */
fun <T> readCsv(path: java.nio.file.Path, block: (Map<String, String>) -> T?): List<T> =
    java.nio.file.Files.newBufferedReader(path).use { buffered ->
        CsvReader(buffered).use { csv ->
            val header =
                csv.readRecord()?.map { it.removePrefix("\uFEFF").trim() } ?: return emptyList()
            val result = mutableListOf<T>()
            while (true) {
                val record = csv.readRecord() ?: break
                if (record.all { it.isBlank() }) continue
                val row = header.mapIndexed { index, name ->
                    name to (record.getOrNull(index)?.trim().orEmpty())
                }.toMap()
                block(row)?.let(result::add)
            }
            result
        }
    }

fun forEachCsvRow(path: java.nio.file.Path, block: (Map<String, String>) -> Unit) {
    java.nio.file.Files.newBufferedReader(path).use { buffered ->
        CsvReader(buffered).use { csv ->
            val header = csv.readRecord()?.map { it.removePrefix("\uFEFF").trim() } ?: return
            while (true) {
                val record = csv.readRecord() ?: break
                if (record.all { it.isBlank() }) continue
                val row = header.mapIndexed { index, name ->
                    name to (record.getOrNull(index)?.trim().orEmpty())
                }.toMap()
                block(row)
            }
        }
    }
}
