package app.openhealth.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
            val container = runCatching { app.container }.getOrNull()
            if (app.startupError != null || container == null) {
                StartupErrorScreen()
            } else {
                OpenHealthRoot(container)
            }
        }
    }
}
