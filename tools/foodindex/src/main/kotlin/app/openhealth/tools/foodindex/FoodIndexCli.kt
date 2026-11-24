package app.openhealth.tools.foodindex

import java.nio.file.Path

/**
 * Build the bundled food index from two unzipped USDA FoodData Central releases.
 *
 *   ./gradlew :tools:foodindex:run --args="--foundation <dir> --sr-legacy <dir> \
 *       --out app-android/src/main/assets/foodindex.db"
 *
 * The dataset is CC0. Nothing here runs at build time: the generated database is
 * committed so a build never downloads data.
 */
object FoodIndexCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val options = args.toList().chunked(2).associate { it[0] to it.getOrElse(1) { "" } }
        val foundation = Path.of(
            options["--foundation"]
                ?: "FoodData_Central_foundation_food_csv_2026-04-30",
        )
        val srLegacy = Path.of(
            options["--sr-legacy"] ?: "FoodData_Central_sr_legacy_food_csv_2018-04",
        )
        val output = Path.of(options["--out"] ?: "app-android/src/main/assets/foodindex.db")
        val version = options["--version"]
            ?: "USDA FoodData Central: Foundation Foods 2026-04-30, SR Legacy 2018-04"
        val result = FoodIndexBuilder().build(
            FoodIndexBuilder.Request(
                foundationDir = foundation,
                srLegacyDir = srLegacy,
                output = output,
                version = version,
                foundationUrl = "https://fdc.nal.usda.gov/fdc-datasets/" +
                    "FoodData_Central_foundation_food_csv_2026-04-30.zip",
                srLegacyUrl = "https://fdc.nal.usda.gov/fdc-datasets/" +
                    "FoodData_Central_sr_legacy_food_csv_2018-04.zip",
            ),
        )
        println(
            "foodindex: ${result.foods} foods " +
                "(${result.foundations} foundation, ${result.srLegacy} SR Legacy), " +
                "${result.nutrients} with nutrients, ${result.tokens} search tokens -> $output",
        )
    }
}
