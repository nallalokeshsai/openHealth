package app.openhealth.tools.fixtures

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.openhealth.data.corpus.CorpusGenerator
import app.openhealth.data.corpus.CorpusRequest
import app.openhealth.data.db.DatabaseOpener
import java.util.Properties
import kotlinx.datetime.LocalDate

/**
 * Writes the seeded five-year corpus to a file, for the benchmark, for demo mode and for
 * anyone who wants a realistic database without waiting five years.
 *
 * `./gradlew :tools:fixtures:run --args="--out /tmp/openhealth-corpus.db --days 1825"`
 */
object FixtureCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val options = args.toList()
        val out = valueOf(options, "--out") ?: "openhealth-corpus.db"
        val days = valueOf(options, "--days")?.toIntOrNull() ?: CorpusRequest.FIVE_YEARS_OF_DAYS
        val seed = valueOf(options, "--seed")?.toLongOrNull() ?: DEFAULT_SEED
        val end = valueOf(options, "--end")?.let(LocalDate::parse) ?: DEFAULT_END_DATE

        val driver = JdbcSqliteDriver("jdbc:sqlite:$out", Properties())
        val store = DatabaseOpener().open(driver)
        val stats = CorpusGenerator(store.db).generate(
            CorpusRequest(days = days, seed = seed, endDate = end),
        )
        store.close()
        println(
            "Wrote $out: ${stats.days} days, ${stats.observations} observations, " +
                "${stats.sessions} sessions, ${stats.sets} sets, ${stats.entries} entries",
        )
    }

    private const val DEFAULT_SEED = 42L
    private val DEFAULT_END_DATE = LocalDate(2026, 11, 22)

    private fun valueOf(args: List<String>, name: String): String? {
        val index = args.indexOf(name)
        return if (index >= 0 && index + 1 < args.size) args[index + 1] else null
    }
}
