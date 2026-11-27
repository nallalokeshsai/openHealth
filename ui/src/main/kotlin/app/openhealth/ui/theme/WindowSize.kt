package app.openhealth.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * The size of the window this content is drawn in, in dp. It is the window, not the
 * device screen: in split-screen, on a foldable or in a freeform window the two differ
 * (`Configuration.screenWidthDp` reports the display, which is wrong there).
 */
@Composable
@ReadOnlyComposable
fun windowWidthDp(): Int =
    (LocalWindowInfo.current.containerSize.width / LocalDensity.current.density).toInt()

@Composable
@ReadOnlyComposable
fun windowHeightDp(): Int =
    (LocalWindowInfo.current.containerSize.height / LocalDensity.current.density).toInt()
