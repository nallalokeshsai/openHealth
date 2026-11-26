package app.openhealth.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The document's own values (`DESIGN.md` §8.9, added 2026-11-26). A report is printed,
 * not themed: it is white paper and black ink in dark mode and light, so these are the
 * one set of colours that deliberately sit outside `OhTheme`. They are not a second
 * palette for screens, and nothing but the clinical report and its PDF may use them.
 */
@Suppress("MagicNumber")
object OhReportPaper {
    val paper = Color(0xFFFFFFFF)
    val ink = Color(0xFF111111)
    val inkSoft = Color(0xFF555555)
    val rule = Color(0xFFBEBEBE)
}
