@file:Suppress(
    "LongMethod",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "TooManyFunctions",
    "LongParameterList",
    "MagicNumber",
)

package app.openhealth.tools.foodindex

import app.openhealth.core.nutrition.Nutrient
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.DriverManager
import java.time.Instant
import java.util.Locale

/**
 * Builds the compact, read-only food index the app ships (`BUILD-ROADMAP.md` §B6).
 *
 * Input: the unzipped USDA FoodData Central CSV releases for Foundation Foods and SR
 * Legacy (both CC0). Output: one small SQLite file with a food row, its per-100 g
 * nutrients and its household portions, plus a token table for name search and a `meta`
 * table that records the version, licence and source hashes.
 */
class FoodIndexBuilder {

    data class Request(
        val foundationDir: Path,
        val srLegacyDir: Path,
        val output: Path,
        val version: String,
        val foundationUrl: String,
        val srLegacyUrl: String,
    )

    data class Result(
        val foods: Int,
        val nutrients: Int,
        val tokens: Int,
        val foundations: Int,
        val srLegacy: Int,
        val version: String,
    )

    fun build(request: Request): Result {
        val units = mutableMapOf<String, String>()
        val categories = mutableMapOf<String, String>()
        val foods = LinkedHashMap<String, FoodRow>()

        listOf(request.foundationDir, request.srLegacyDir).forEach { dir ->
            readUnits(dir.resolve("measure_unit.csv"), units)
            readCategories(dir.resolve("food_category.csv"), categories)
        }

        val foundationFoods = readFoods(
            request.foundationDir.resolve("food.csv"),
            setOf("foundation_food"),
        )
        val srLegacyFoods = readFoods(
            request.srLegacyDir.resolve("food.csv"),
            setOf("sr_legacy_food"),
        )
        foundationFoods.forEach { row ->
            foods[row.id] = row.copy(category = categories[row.categoryId])
        }
        srLegacyFoods.forEach { row ->
            foods[row.id] = row.copy(category = categories[row.categoryId])
        }
        if (foods.isEmpty()) error("No foundation or SR Legacy foods found in the two datasets.")

        val nutrients = HashMap<String, MutableMap<Nutrient, Double>>(foods.size)
        val energySource = HashMap<String, String>(foods.size)
        readNutrients(
            request.foundationDir.resolve("nutrient.csv"),
            request.foundationDir.resolve("food_nutrient.csv"),
            foods.keys,
            units,
            nutrients,
            energySource,
        )
        readNutrients(
            request.srLegacyDir.resolve("nutrient.csv"),
            request.srLegacyDir.resolve("food_nutrient.csv"),
            foods.keys,
            units,
            nutrients,
            energySource,
        )

        val portions = HashMap<String, MutableList<PortionRow>>(foods.size)
        readPortions(
            request.foundationDir.resolve("food_portion.csv"),
            request.foundationDir.resolve("measure_unit.csv"),
            foods.keys,
            portions,
        )
        readPortions(
            request.srLegacyDir.resolve("food_portion.csv"),
            request.srLegacyDir.resolve("measure_unit.csv"),
            foods.keys,
            portions,
        )

        Files.createDirectories(request.output.toAbsolutePath().parent)
        Files.deleteIfExists(request.output)
        var tokenCount = 0
        DriverManager.getConnection(
            "jdbc:sqlite:${request.output.toAbsolutePath()}",
        ).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA journal_mode = OFF")
                statement.execute("PRAGMA synchronous = OFF")
            }
            connection.autoCommit = false
            connection.createStatement().use { statement ->
                listOf(
                    "CREATE TABLE meta(key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)",
                    "CREATE TABLE food(" +
                        "fdc_id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                        "sort_name TEXT NOT NULL, category TEXT, " +
                        "nutrients TEXT NOT NULL, portions TEXT NOT NULL)",
                    "CREATE TABLE token(" +
                        "token TEXT NOT NULL, fdc_id TEXT NOT NULL, " +
                        "PRIMARY KEY(token, fdc_id)) WITHOUT ROWID",
                    "CREATE INDEX token_token ON token(token)",
                    "CREATE INDEX food_sort ON food(sort_name)",
                ).forEach(statement::execute)
            }
            connection.prepareStatement(
                "INSERT INTO food(fdc_id, name, sort_name, category, nutrients, portions) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
            ).use { insert ->
                foods.values.sortedBy { it.name }.forEach { food ->
                    val normalized = FoodNames.normalize(food.name)
                    if (normalized.isBlank()) return@forEach
                    val values = nutrients[food.id] ?: emptyMap()
                    insert.setString(1, food.id)
                    insert.setString(2, food.name)
                    insert.setString(3, normalized)
                    insert.setString(4, food.category)
                    insert.setString(5, encodeNutrients(values))
                    insert.setString(6, encodePortions(portions[food.id].orEmpty()))
                    insert.addBatch()
                }
                insert.executeBatch()
            }
            connection.prepareStatement(
                "INSERT OR IGNORE INTO token(token, fdc_id) VALUES (?, ?)",
            ).use { insert ->
                foods.values.forEach { food ->
                    val normalized = FoodNames.normalize(food.name)
                    FoodNames.tokens(normalized).forEach { token ->
                        insert.setString(1, token)
                        insert.setString(2, food.id)
                        insert.addBatch()
                    }
                }
                insert.executeBatch()
            }
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT count(*) FROM token").use { rows ->
                    if (rows.next()) tokenCount = rows.getInt(1)
                }
            }
            val meta = linkedMapOf(
                "version" to request.version,
                "licence" to "CC0-1.0",
                "licence_url" to "https://creativecommons.org/publicdomain/zero/1.0/",
                "source_foundation" to "USDA FoodData Central, Foundation Foods",
                "source_sr_legacy" to "USDA FoodData Central, SR Legacy",
                "foundation_url" to request.foundationUrl,
                "sr_legacy_url" to request.srLegacyUrl,
                "foundation_food_sha256" to sha256(request.foundationDir.resolve("food.csv")),
                "foundation_nutrient_sha256" to
                    sha256(request.foundationDir.resolve("food_nutrient.csv")),
                "sr_legacy_food_sha256" to sha256(request.srLegacyDir.resolve("food.csv")),
                "sr_legacy_nutrient_sha256" to
                    sha256(request.srLegacyDir.resolve("food_nutrient.csv")),
                "generated_at" to Instant.now().toString(),
                "food_count" to foods.size.toString(),
                "token_count" to tokenCount.toString(),
                "foundation_count" to foundationFoods.size.toString(),
                "sr_legacy_count" to srLegacyFoods.size.toString(),
                "nutrient_set" to Nutrient.entries.joinToString(",") { it.key },
                "schema" to "1",
            )
            connection.prepareStatement(
                "INSERT INTO meta(key, value) VALUES (?, ?)",
            ).use { insert ->
                meta.forEach { (key, value) ->
                    insert.setString(1, key)
                    insert.setString(2, value)
                    insert.addBatch()
                }
                insert.executeBatch()
            }
            connection.commit()
        }

        Files.writeString(
            request.output.resolveSibling(
                request.output.fileName.toString().removeSuffix(".db") + ".json",
            ),
            buildString {
                append("{\n")
                append("  \"version\": \"").append(escape(request.version)).append("\",\n")
                append("  \"licence\": \"CC0-1.0\",\n")
                append(
                    "  \"licenceUrl\": \"https://creativecommons.org/publicdomain/zero/1.0/\",\n",
                )
                append("  \"foodCount\": ").append(foods.size).append(",\n")
                append("  \"tokenCount\": ").append(tokenCount).append(",\n")
                append("  \"foundationCount\": ").append(foundationFoods.size).append(",\n")
                append("  \"srLegacyCount\": ").append(srLegacyFoods.size).append(",\n")
                append("  \"generatedAt\": \"").append(Instant.now()).append("\",\n")
                append("  \"sourceUrls\": [\n")
                append("    \"").append(escape(request.foundationUrl)).append("\",\n")
                append("    \"").append(escape(request.srLegacyUrl)).append("\"\n")
                append("  ]\n")
                append("}\n")
            },
        )
        return Result(
            foods = foods.size,
            nutrients = nutrients.size,
            tokens = tokenCount,
            foundations = foundationFoods.size,
            srLegacy = srLegacyFoods.size,
            version = request.version,
        )
    }

    private data class FoodRow(
        val id: String,
        val name: String,
        val categoryId: String?,
        val category: String? = null,
    )

    private data class PortionRow(val label: String, val grams: Double)

    private fun readUnits(path: Path, into: MutableMap<String, String>) {
        if (!Files.exists(path)) return
        forEachCsvRow(path) { row ->
            val id = row["id"] ?: return@forEachCsvRow
            val name = row["name"] ?: return@forEachCsvRow
            into[id] = name
        }
    }

    private fun readCategories(path: Path, into: MutableMap<String, String>) {
        if (!Files.exists(path)) return
        forEachCsvRow(path) { row ->
            val id = row["id"] ?: return@forEachCsvRow
            val description = row["description"] ?: return@forEachCsvRow
            into.putIfAbsent(id, description)
        }
    }

    private fun readFoods(path: Path, types: Set<String>): List<FoodRow> {
        if (!Files.exists(path)) error("Missing ${path.toAbsolutePath()}")
        val rows = mutableListOf<FoodRow>()
        forEachCsvRow(path) { row ->
            val type = row["data_type"].orEmpty()
            if (type !in types) return@forEachCsvRow
            val id = row["fdc_id"] ?: return@forEachCsvRow
            val name = row["description"] ?: return@forEachCsvRow
            if (name.isBlank()) return@forEachCsvRow
            rows += FoodRow(id, name.trim(), row["food_category_id"])
        }
        return rows
    }

    private fun readNutrients(
        nutrientPath: Path,
        foodNutrientPath: Path,
        wantedFoods: Set<String>,
        units: MutableMap<String, String>,
        into: MutableMap<String, MutableMap<Nutrient, Double>>,
        energySource: MutableMap<String, String>,
    ) {
        if (!Files.exists(nutrientPath) || !Files.exists(foodNutrientPath)) {
            error(
                "Missing ${nutrientPath.toAbsolutePath()} or ${foodNutrientPath.toAbsolutePath()}",
            )
        }
        forEachCsvRow(nutrientPath) { row ->
            val id = row["id"] ?: return@forEachCsvRow
            units[id] = row["unit_name"].orEmpty()
        }
        forEachCsvRow(foodNutrientPath) { row ->
            val foodId = row["fdc_id"] ?: return@forEachCsvRow
            if (foodId !in wantedFoods) return@forEachCsvRow
            val fdcId = row["nutrient_id"] ?: return@forEachCsvRow
            val nutrient = FdcNutrientMap.nutrientFor(fdcId) ?: return@forEachCsvRow
            val amount = row["amount"]?.toDoubleOrNull() ?: return@forEachCsvRow
            if (!amount.isFinite()) return@forEachCsvRow
            val target = nutrient.unit
            val converted = FdcNutrientMap.convert(amount, units[fdcId].orEmpty(), target)
                ?: return@forEachCsvRow
            val values = into.getOrPut(foodId) { LinkedHashMap() }
            if (nutrient == Nutrient.ENERGY) {
                val current = energySource[foodId]
                val priority = FdcNutrientMap.energyPriority
                val newRank = priority.indexOf(fdcId)
                val oldRank = current?.let(priority::indexOf) ?: Int.MAX_VALUE
                if (current != null && oldRank <= newRank) return@forEachCsvRow
                energySource[foodId] = fdcId
            } else if (values.containsKey(nutrient)) {
                return@forEachCsvRow
            }
            values[nutrient] = converted
        }
    }

    private fun readPortions(
        path: Path,
        unitsPath: Path,
        wantedFoods: Set<String>,
        into: MutableMap<String, MutableList<PortionRow>>,
    ) {
        if (!Files.exists(path)) return
        val units = mutableMapOf<String, String>()
        readUnits(unitsPath, units)
        forEachCsvRow(path) { row ->
            val foodId = row["fdc_id"] ?: return@forEachCsvRow
            if (foodId !in wantedFoods) return@forEachCsvRow
            val grams = row["gram_weight"]?.toDoubleOrNull() ?: return@forEachCsvRow
            if (!grams.isFinite() || grams <= 0.0 || grams > 5_000.0) return@forEachCsvRow
            val amount = row["amount"]?.toDoubleOrNull() ?: 1.0
            val description = row["portion_description"].orEmpty()
            val modifier = row["modifier"].orEmpty()
            val unitName = units[row["measure_unit_id"].orEmpty()].orEmpty()
            val label = when {
                description.isNotBlank() -> description
                modifier.isNotBlank() -> "${trimAmount(amount)} $modifier"
                unitName.isNotBlank() && unitName != "undetermined" ->
                    "${trimAmount(amount)} $unitName"

                else -> return@forEachCsvRow
            }.replace(Regex("\\s+"), " ").trim()
            if (label.isBlank()) return@forEachCsvRow
            val list = into.getOrPut(foodId) { mutableListOf() }
            if (list.size >= MAX_PORTIONS) return@forEachCsvRow
            if (list.none { it.label.equals(label, ignoreCase = true) }) {
                list += PortionRow(label, grams)
            }
        }
    }

    private fun trimAmount(amount: Double): String = if (amount == amount.toLong().toDouble()) {
        amount.toLong().toString()
    } else {
        String.format(Locale.ROOT, "%.2f", amount).trimEnd('0').trimEnd('.')
    }

    private fun encodeNutrients(values: Map<Nutrient, Double>): String = values.entries
        .sortedBy { it.key.ordinal }
        .joinToString(prefix = "{", postfix = "}") { (nutrient, amount) ->
            "\"${nutrient.key}\":$amount"
        }

    private fun encodePortions(portions: List<PortionRow>): String =
        portions.joinToString(prefix = "[", postfix = "]") { portion ->
            "{\"label\":\"${escape(portion.label)}\",\"grams\":${portion.grams}}"
        }

    private fun escape(text: String): String = text.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun sha256(path: Path): String {
        if (!Files.exists(path)) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MAX_PORTIONS = 8
    }
}
