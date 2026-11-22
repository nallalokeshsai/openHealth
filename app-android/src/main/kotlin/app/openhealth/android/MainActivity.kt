package app.openhealth.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * B0's one blank screen. It proves the app installs and launches; B1 replaces it with `OhTheme`
 * and the shell arrives in B3. No tokens, colours or copy are defined here.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BlankScreen() }
    }
}

@Composable
private fun BlankScreen() {
    Box(modifier = Modifier.fillMaxSize())
}
