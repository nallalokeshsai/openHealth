package app.openhealth.android

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.openhealth.core.preferences.HealthModule
import app.openhealth.core.preferences.ModulePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Benchmark-build only (B9): writes the five-year seeded corpus and completes onboarding so
 * a macrobenchmark measures the real Today behind real rows. The class lives in the
 * `benchmark` source set, so a release or debug APK does not contain it (ADR 0014).
 */
class BenchmarkSeedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = TextView(this).apply {
            text = "Seeding"
            textSize = 20f
        }
        setContentView(label)
        val app = application as OpenHealthApp
        lifecycleScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val container = app.container
                    container.addSampleData(
                        days = FIVE_YEARS,
                        profileId = "profile-demo",
                        writeProfile = true,
                    )
                    container.preferences.setPreset(ModulePreset.EVERYTHING)
                    container.preferences.setModules(HealthModule.entries.toSet())
                    container.preferences.setWeightNeutral(false)
                    container.preferences.setDemo(true)
                    container.preferences.markOnboardingComplete(tier = 3)
                    if (container.store.settings.get("goal.target_weight_kg").isNullOrBlank()) {
                        container.store.settings.put("goal.target_weight_kg", "78.0")
                    }
                }
            }.isSuccess
            label.text = if (ok) "Seeded" else "Failed"
        }
    }

    private companion object {
        const val FIVE_YEARS = 365 * 5
    }
}
