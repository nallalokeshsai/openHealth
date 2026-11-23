package app.openhealth.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/** The whole appearance contract of `DESIGN.md`, exposed once (`Appendix B`). */
val LocalOhColors = staticCompositionLocalOf<OhColors> {
    error("OhTheme is missing: every screen is composed inside OhTheme (DESIGN.md Appendix B).")
}

val LocalOhType = staticCompositionLocalOf<OhType> {
    error("OhTheme is missing: every screen is composed inside OhTheme (DESIGN.md Appendix B).")
}

val LocalOhMotion = staticCompositionLocalOf<OhMotion> {
    error("OhTheme is missing: every screen is composed inside OhTheme (DESIGN.md Appendix B).")
}

/**
 * The one theme object.
 *
 * Dark is canonical and is the default; light is the verified sibling
 * (`DESIGN.md` §1.10, §12). There is no dynamic colour and no Material palette:
 * `MaterialTheme.colorScheme` is never installed or read (`§12.4`, `Appendix B`).
 */
@Composable
fun OhTheme(
    dark: Boolean = isSystemInDarkTheme(),
    accent: OhAccent = OhAccent.Brand,
    motion: OhMotion = rememberOhMotion(),
    content: @Composable () -> Unit,
) {
    val colors =
        remember(dark, accent) { if (dark) ohDarkColors(accent) else ohLightColors(accent) }
    val windowWidth = LocalConfiguration.current.screenWidthDp
    val type = remember(windowWidth) { ohType(windowWidth) }
    CompositionLocalProvider(
        LocalOhColors provides colors,
        LocalOhType provides type,
        LocalOhMotion provides motion,
        LocalContentColor provides colors.label,
        content = content,
    )
}

/** Token accessors. `OhTheme.colors.acc`, never a hex (`DESIGN.md` §12.6). */
object OhTheme {
    val colors: OhColors
        @Composable @ReadOnlyComposable
        get() = LocalOhColors.current

    val type: OhType
        @Composable @ReadOnlyComposable
        get() = LocalOhType.current

    val motion: OhMotion
        @Composable @ReadOnlyComposable
        get() = LocalOhMotion.current

    val space: OhSpace get() = OhSpace

    val radius: OhRadius get() = OhRadius

    val dims: OhDims get() = ohDims
}

/**
 * Reads the system animation scale. Android's "Remove animations" setting sets it to
 * zero; every motion token then resolves to instant (`DESIGN.md` §6.9).
 */
@Composable
fun rememberOhMotion(): OhMotion {
    val context = LocalContext.current
    val reduced = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    return remember(reduced) { OhMotion(reduced = reduced) }
}
