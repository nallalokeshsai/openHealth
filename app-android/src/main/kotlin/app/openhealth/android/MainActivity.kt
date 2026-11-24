package app.openhealth.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.openhealth.android.ui.OpenHealthRoot
import app.openhealth.android.ui.StartupErrorScreen

/**
 * The one activity (`BUILD-ROADMAP.md` §B3). The system splash is installed before the
 * first frame; no network or sync is awaited, so first launch goes straight to
 * onboarding and every later launch to Today.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as OpenHealthApp
        setContent {
            // Retrying rebuilds the container; the state is read through `attempt` so the
            // screen recomposes and the button is never a dead control.
            var attempt by remember { mutableIntStateOf(0) }
            val container = remember(attempt) {
                runCatching { app.container }.getOrNull().takeIf { app.startupError == null }
            }
            if (container == null) {
                StartupErrorScreen(
                    onRetry = {
                        app.retryStartup()
                        attempt += 1
                    },
                )
            } else {
                OpenHealthRoot(container)
            }
        }
    }
}
