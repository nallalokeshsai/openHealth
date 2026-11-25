package app.openhealth.tools.fixtures

import java.io.File

/**
 * Writes the FIT golden fixtures used by `:integrations` tests (`BUILD-ROADMAP.md` §B8).
 * Run it when the fixtures change; the bytes are committed so a parser change is diffed
 * against a stable file rather than against a generator that changed with it.
 *
 * `./gradlew :tools:fixtures:run --args="--fit integrations/src/test/resources/fit"`
 */
object FitFixtureWriter {
    @JvmStatic
    fun main(args: Array<String>) {
        val out = args.toList().let { list ->
            val index = list.indexOf("--fit")
            if (index >= 0 && index + 1 < list.size) list[index + 1] else DEFAULT_OUT
        }
        write(File(out))
    }

    fun write(directory: File): List<File> {
        directory.mkdirs()
        val files = listOf(
            "activity-walk.fit" to FitFixtures.activityWalk(),
            "daily-monitoring.fit" to FitFixtures.dailyMonitoring(),
            "big-endian.fit" to FitFixtures.bigEndian(),
            "compressed-timestamp.fit" to FitFixtures.compressedTimestamp(),
            "corrupt-crc.fit" to FitFixtures.corrupt(FitFixtures.activityWalk()),
        )
        return files.map { (name, bytes) ->
            File(directory, name).apply { writeBytes(bytes) }
        }
    }

    const val DEFAULT_OUT = "integrations/src/test/resources/fit"
}
