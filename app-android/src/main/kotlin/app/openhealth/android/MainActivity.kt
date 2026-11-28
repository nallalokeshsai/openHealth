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
import app.openhealth.android.activity.GpsRecordingService
import app.openhealth.android.activity.YogaSessionService
import app.openhealth.android.ui.OpenHealthRoot
import app.openhealth.android.ui.StartupErrorScreen
import app.openhealth.android.wear.WatchGattService

/**
 * The one activity (`BUILD-ROADMAP.md` §B3). The system splash is installed before the
 * first frame; no network or sync is awaited, so first launch goes straight to
 * onboarding and every later launch to Today.
 */
class MainActivity : ComponentActivity() {
    /**
     * The write path captures the zone when its presenters are built. After a flight or a
     * DST change the view model is dropped and the activity recreated, so every new
     * `local_date` and `zone_id` uses the live zone, not the one from process start.
     */
    private val zoneAtStart = kotlinx.datetime.TimeZone.currentSystemDefault()

    private val zoneReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (zoneAtStart != kotlinx.datetime.TimeZone.currentSystemDefault()) {
                viewModelStore.clear()
                recreate()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            zoneReceiver,
            android.content.IntentFilter(android.content.Intent.ACTION_TIMEZONE_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        enableEdgeToEdge()
        val app = application as OpenHealthApp
        if (app.startupError == null) {
            runCatching {
                GpsRecordingService.recoverIfNeeded(this)
                YogaSessionService.recoverIfNeeded(this)
                WatchGattService.recoverIfNeeded(this)
            }
        }
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

    override fun onDestroy() {
        runCatching { unregisterReceiver(zoneReceiver) }
        super.onDestroy()
    }
}
