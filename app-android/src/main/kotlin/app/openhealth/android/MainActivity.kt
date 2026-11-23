package app.openhealth.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openhealth.ui.gallery.ComponentGallery
import app.openhealth.ui.theme.OhTheme

/**
 * B1's entry point. A debug build opens the component gallery, the surface every token, component
 * and state is reviewed on; a release build keeps the B0 placeholder until B3 adds the shell.
 * Nothing in the gallery is reachable from a release build.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            if (BuildConfig.DEBUG) {
                ComponentGallery()
            } else {
                PlaceholderScreen()
            }
        }
    }
}

@Composable
private fun PlaceholderScreen() {
    OhTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OhTheme.colors.bg)
        )
    }
}
