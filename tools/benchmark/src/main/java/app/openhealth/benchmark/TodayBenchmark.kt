package app.openhealth.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B9's performance exit test (BUILD-ROADMAP.md §B9, pr.md §6.2, Tech-Stack.md §12): cold
 * start under 1.5 s and a warm Today under 300 ms, over the five-year corpus the seed
 * activity writes once. The corpus is generated in the app's own repositories, so the
 * queries are the ones the screens run.
 */
@RunWith(AndroidJUnit4::class)
class TodayBenchmark {
    @get:Rule
    val benchmarkRule: MacrobenchmarkRule = MacrobenchmarkRule()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun seedTheFiveYearCorpus() {
        device.pressHome()
        device.executeShellCommand(
            "am start -W -n app.openhealth.bench/app.openhealth.android.BenchmarkSeedActivity",
        )
        val seeded = device.wait(Until.hasObject(By.text("Seeded")), SEED_TIMEOUT_MILLIS)
        if (!seeded) {
            val failed = device.hasObject(By.text("Failed"))
            error(
                if (failed) {
                    "The corpus could not be written"
                } else {
                    "The seed activity did not finish within ${SEED_TIMEOUT_MILLIS / 1_000} s"
                },
            )
        }
        device.pressHome()
    }

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
    }

    /** The app already ran: the process is warm and Today rebuilds from its stored rows. */
    @Test
    fun warmToday() = benchmarkRule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
    ) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("Due")), CONTENT_TIMEOUT_MILLIS)
    }

    private companion object {
        const val PACKAGE = "app.openhealth.bench"
        const val ITERATIONS = 5
        const val SEED_TIMEOUT_MILLIS = 30 * 60 * 1000L
        const val CONTENT_TIMEOUT_MILLIS = 2_000L
    }
}
