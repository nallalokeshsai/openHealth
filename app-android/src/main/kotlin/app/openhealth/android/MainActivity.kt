package app.openhealth.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

/**
 * B0 shell: one blank screen, nothing else.
 *
 * The design system arrives in B1 and the navigation shell, splash and Today
 * skeleton in B3; until then this screen only proves the app installs and launches.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}
