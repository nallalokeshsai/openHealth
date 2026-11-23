package app.openhealth.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * The one theme of DESIGN.md Appendix B. There is no `MaterialTheme.colorScheme` default, no
 * dynamic colour and no second theme object (§12.4, Appendix B).
 *
 * Dark is canonical and the default comes from the device; light is the verified sibling. The
 * accent is one of the nine presets. Reduced motion and high contrast are inputs so a screen can
 * be reviewed in each without changing the token set.
 */
@Composable
fun OhTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: OhAccentPreset = OhAccentPreset.Brand,
    reduceMotion: Boolean = rememberReduceMotion(),
    content: @Composable () -> Unit
) {
    val colors = remember(darkTheme, accent) {
        if (darkTheme) darkColors(accent) else lightColors(accent)
    }
    val type = remember { OhType() }
    val space = remember { OhSpace() }
    val radius = remember { OhRadius() }
    val motion = remember(reduceMotion) { OhMotion(reduced = reduceMotion) }
    val geometry = remember { OhGeometry() }

    CompositionLocalProvider(
        LocalOhColors provides colors,
        LocalOhType provides type,
        LocalOhSpace provides space,
        LocalOhRadius provides radius,
        LocalOhMotion provides motion,
        LocalOhGeometry provides geometry,
        LocalTextStyle provides type.body,
        // Material's only role here: its minimum-target behaviour, set to the design's 44 dp
        // (§4.5) instead of Material's 48 dp default. No palette or shape comes from Material.
        androidx.compose.material3.LocalMinimumInteractiveComponentSize provides space.touchTarget
    ) {
        ProvideTextStyle(type.body, content)
    }
}

/** Token access for composables, in the shape Appendix B names. */
object OhTheme {
    val colors: OhColors
        @Composable @ReadOnlyComposable
        get() = LocalOhColors.current

    val type: OhType
        @Composable @ReadOnlyComposable
        get() = LocalOhType.current

    val space: OhSpace
        @Composable @ReadOnlyComposable
        get() = LocalOhSpace.current

    val radius: OhRadius
        @Composable @ReadOnlyComposable
        get() = LocalOhRadius.current

    val motion: OhMotion
        @Composable @ReadOnlyComposable
        get() = LocalOhMotion.current

    val geometry: OhGeometry
        @Composable @ReadOnlyComposable
        get() = LocalOhGeometry.current
}

val LocalOhColors = staticCompositionLocalOf<OhColors> {
    error("OhTheme is not applied: no screen may fall back to a Material palette (DESIGN.md Appendix B)")
}

val LocalOhType = staticCompositionLocalOf<OhType> {
    error("OhTheme is not applied (DESIGN.md Appendix B)")
}

val LocalOhSpace = staticCompositionLocalOf<OhSpace> {
    error("OhTheme is not applied (DESIGN.md Appendix B)")
}

val LocalOhRadius = staticCompositionLocalOf<OhRadius> {
    error("OhTheme is not applied (DESIGN.md Appendix B)")
}

val LocalOhMotion = staticCompositionLocalOf<OhMotion> {
    error("OhTheme is not applied (DESIGN.md Appendix B)")
}

val LocalOhGeometry = staticCompositionLocalOf<OhGeometry> {
    error("OhTheme is not applied (DESIGN.md Appendix B)")
}

/**
 * Android "Remove animations" (§6.9). Read once per theme; the system setting is not a live
 * stream, and a screen that changes it is restarted by the platform anyway.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    val scale = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
    }
    return scale == 0f
}
