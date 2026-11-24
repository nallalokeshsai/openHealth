package app.openhealth.tools.foodindex

import app.openhealth.core.nutrition.Nutrient
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvReaderTest {
    @Test
    fun quotedCommasAndLineBreaksSurvive() {
        val text = "a,b,c\r\n\"one, two\",\"line\nbreak\",3\n"
        CsvReader(text.reader()).use { reader ->
            assertEquals(listOf("a", "b", "c"), reader.readRecord())
            assertEquals(listOf("one, two", "line\nbreak", "3"), reader.readRecord())
            assertEquals(null, reader.readRecord())
        }
    }

    @Test
    fun doubledQuotesMeanOneQuote() {
        CsvReader("x\n\"say \"\"hi\"\"\"\n".reader()).use { reader ->
            assertEquals(listOf("x"), reader.readRecord())
            assertEquals(listOf("say \"hi\""), reader.readRecord())
        }
    }
}

class FoodNamesTest {
    @Test
    fun accentsPunctuationAndSpacingNormalise() {
        assertEquals("creme brulee", FoodNames.normalize("  Crème, Brûlée! "))
        assertEquals("milk 2 reduced fat", FoodNames.normalize("Milk, 2% reduced fat"))
        assertEquals("", FoodNames.normalize("!!! ***"))
    }

    @Test
    fun tokensDropStopWordsAndSingleLetters() {
        assertEquals(listOf("oats", "raw"), FoodNames.tokens("oats and raw"))
        assertEquals(listOf("milk", "reduced", "fat"), FoodNames.tokens("milk 2% reduced fat"))
    }
}

class FdcNutrientMapTest {
    @Test
    fun everyMappedIdHasAKnownNutrientAndCompatibleUnit() {
        val units = mapOf(
            "1008" to "KCAL",
            "2047" to "KCAL",
            "2048" to "KCAL",
            "1003" to "G",
            "1079" to "G",
            "1093" to "MG",
            "1103" to "UG",
            "1106" to "UG",
        )
        FdcNutrientMap.byFdcId.forEach { (id, nutrient) ->
            if (id !in units) return@forEach
            val converted = FdcNutrientMap.convert(1.0, units.getValue(id), nutrient.unit)
            assertTrue(converted != null, "$id ${nutrient.key} has an incompatible unit")
        }
    }

    @Test
    fun aMilligramIsAThousandMicrograms() {
        assertEquals(
            1_000.0,
            FdcNutrientMap.convert(1.0, "MG", Nutrient.VITAMIN_D.unit)!!,
            1e-9,
        )
        assertEquals(
            1.0,
            FdcNutrientMap.convert(1_000.0, "UG", app.openhealth.core.units.UcumUnit.MILLIGRAM)!!,
            1e-9,
        )
        assertEquals(
            0.001,
            FdcNutrientMap.convert(1.0, "MG", Nutrient.PROTEIN.unit)!!,
            1e-12,
        )
        assertEquals(null, FdcNutrientMap.convert(1.0, "KCAL", Nutrient.PROTEIN.unit))
    }
}

class FoodIndexBuilderTest {
    @Test
    fun aTinyDatasetBecomesAQueryableIndex() {
        val root = Files.createTempDirectory("foodindex")
        val foundation = root.resolve("foundation").also { Files.createDirectories(it) }
        val sr = root.resolve("sr").also { Files.createDirectories(it) }
        writeCsv(
            foundation.resolve("measure_unit.csv"),
            "id,name",
            "1000,cup",
            "1001,tablespoon",
        )
        writeCsv(
            foundation.resolve("food_category.csv"),
            "id,code,description",
            "1,0100,Cereal Grains and Pasta",
        )
        writeCsv(
            foundation.resolve("nutrient.csv"),
            "id,name,unit_name,nutrient_nbr,rank",
            "2047,Energy (Atwater General Factors),KCAL,957,280.0",
            "1003,Protein,G,203,600.0",
            "1093,Sodium,MG,307,5800.0",
            "1162,Vitamin C,MG,401,6300.0",
        )
        writeCsv(
            foundation.resolve("food.csv"),
            "fdc_id,data_type,description,food_category_id,publication_date",
            "1,foundation_food,\"Oats, raw\",1,2026-04-30",
            "2,sample_food,Not a foundation food,1,2026-04-30",
        )
        writeCsv(
            foundation.resolve("food_nutrient.csv"),
            "id,fdc_id,nutrient_id,amount,data_points,derivation_id",
            "10,1,2047,389,1,1",
            "11,1,1003,16.9,1,1",
            "12,1,1093,2,1,1",
            "13,1,1162,0.5,1,1",
        )
        writeCsv(
            foundation.resolve("food_portion.csv"),
            "id,fdc_id,seq_num,amount,measure_unit_id,portion_description,modifier," +
                "gram_weight,data_points,footnote,min_year_acquired",
            "20,1,1,1,1000,1 cup,,80,1,,",
            "21,1,2,2,1001,2 tablespoons,,30,1,,",
        )
        writeCsv(
            sr.resolve("measure_unit.csv"),
            "id,name",
            "1000,cup",
        )
        writeCsv(
            sr.resolve("food_category.csv"),
            "id,code,description",
            "11,1100,Vegetables",
        )
        writeCsv(
            sr.resolve("nutrient.csv"),
            "id,name,unit_name,nutrient_nbr,rank",
            "1008,Energy,KCAL,208,300.0",
            "1003,Protein,G,203,600.0",
        )
        writeCsv(
            sr.resolve("food.csv"),
            "fdc_id,data_type,description,food_category_id,publication_date",
            "3,sr_legacy_food,Broccoli raw,11,2019-04-01",
        )
        writeCsv(
            sr.resolve("food_nutrient.csv"),
            "id,fdc_id,nutrient_id,amount,data_points,derivation_id",
            "30,3,1008,34,1,1",
            "31,3,1003,2.8,1,1",
        )
        writeCsv(
            sr.resolve("food_portion.csv"),
            "id,fdc_id,seq_num,amount,measure_unit_id,portion_description,modifier," +
                "gram_weight,data_points,footnote,min_year_acquired",
            "40,3,1,1,1000,1 cup chopped,,91,1,,",
        )
        val out = root.resolve("foodindex.db")
        val result = FoodIndexBuilder().build(
            FoodIndexBuilder.Request(
                foundationDir = foundation,
                srLegacyDir = sr,
                output = out,
                version = "test",
                foundationUrl = "https://example.invalid/foundation.zip",
                srLegacyUrl = "https://example.invalid/sr.zip",
            ),
        )
        assertEquals(2, result.foods)
        assertEquals(1, result.srLegacy)
        assertEquals(1, result.foundations)
        assertTrue(result.tokens >= 3)

        DriverManager.getConnection("jdbc:sqlite:$out").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT count(*) FROM food").use { rows ->
                    rows.next()
                    assertEquals(2, rows.getInt(1))
                }
                statement.executeQuery(
                    "SELECT name, category, nutrients, portions FROM food WHERE fdc_id = '1'",
                ).use { rows ->
                    rows.next()
                    assertEquals("Oats, raw", rows.getString(1))
                    assertEquals("Cereal Grains and Pasta", rows.getString(2))
                    val nutrients = rows.getString(3)
                    assertTrue(nutrients.contains("\"energy\":389.0"), nutrients)
                    assertTrue(nutrients.contains("\"protein\":16.9"), nutrients)
                    assertTrue(nutrients.contains("\"sodium\":2.0"), nutrients)
                    val portions = rows.getString(4)
                    assertTrue(portions.contains("1 cup"), portions)
                    assertTrue(portions.contains("\"grams\":80.0"), portions)
                }
                statement.executeQuery(
                    "SELECT count(*) FROM token WHERE token = 'oats'",
                ).use { rows ->
                    rows.next()
                    assertEquals(1, rows.getInt(1))
                }
            }
        }
        assertTrue(Files.exists(root.resolve("foodindex.json")))
    }

    private fun writeCsv(path: Path, vararg lines: String) {
        Files.writeString(path, lines.joinToString("\n") + "\n")
    }
}
