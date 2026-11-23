package app.openhealth.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The ink a vector is built with. `Icon` recolours every icon through its `ColorFilter`,
 * so this value is never seen; it lives in the theme package because it is a colour, and
 * `checkDesignTokens` allows a colour only here (`DESIGN.md` §12.6).
 */
val ohIconInk: Color = Color.Black
