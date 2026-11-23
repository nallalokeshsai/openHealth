@file:Suppress("MagicNumber")

package app.openhealth.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector

/** Routine glyph keys (`DESIGN.md` §10.3): strength, equipment, cardio, recovery. */
object OhGlyphIcons {

    val strength: ImageVector = ohIcon("strength") {
        stroke {
            moveTo(4f, 12f)
            lineTo(20f, 12f)
            moveTo(7f, 7.5f)
            lineTo(7f, 16.5f)
            moveTo(17f, 7.5f)
            lineTo(17f, 16.5f)
            moveTo(4.5f, 9.5f)
            lineTo(4.5f, 14.5f)
            moveTo(19.5f, 9.5f)
            lineTo(19.5f, 14.5f)
        }
    }

    val arms: ImageVector = ohIcon("arms") {
        stroke {
            moveTo(4.5f, 17.5f)
            quadTo(4.2f, 9.5f, 11.5f, 9f)
            quadTo(14.4f, 8.8f, 15.6f, 11.2f)
            moveTo(13.3f, 14.2f)
            arcTo(3.2f, 3.2f, 0f, false, true, 19.7f, 14.2f)
            arcTo(3.2f, 3.2f, 0f, false, true, 13.3f, 14.2f)
        }
    }

    val abs: ImageVector = ohIcon("abs") {
        stroke {
            moveTo(8f, 6.5f)
            quadTo(8f, 3.5f, 11f, 3.5f)
            lineTo(13f, 3.5f)
            quadTo(16f, 3.5f, 16f, 6.5f)
            quadTo(15.2f, 10.5f, 15.6f, 16f)
            quadTo(15.8f, 20.5f, 12f, 20.5f)
            quadTo(8.2f, 20.5f, 8.4f, 16f)
            quadTo(8.8f, 10.5f, 8f, 6.5f)
            moveTo(9.5f, 11f)
            lineTo(14.5f, 11f)
            moveTo(9.5f, 15f)
            lineTo(14.5f, 15f)
        }
    }

    val legs: ImageVector = ohIcon("legs") {
        stroke {
            moveTo(9.5f, 4f)
            lineTo(9.5f, 15.5f)
            lineTo(6f, 19.5f)
            moveTo(14.5f, 4f)
            lineTo(14.5f, 15.5f)
            lineTo(18f, 19.5f)
            moveTo(9.5f, 6f)
            lineTo(14.5f, 6f)
        }
    }

    val pullUp: ImageVector = ohIcon("pullUp") {
        stroke {
            moveTo(3.5f, 4.5f)
            lineTo(20.5f, 4.5f)
            moveTo(8f, 4.5f)
            lineTo(8f, 9f)
            lineTo(9.5f, 11f)
            moveTo(16f, 4.5f)
            lineTo(16f, 9f)
            lineTo(14.5f, 11f)
        }
    }

    val run: ImageVector = ohIcon("run") {
        stroke {
            moveTo(12.4f, 6.2f)
            arcTo(2.2f, 2.2f, 0f, false, true, 16.8f, 6.2f)
            arcTo(2.2f, 2.2f, 0f, false, true, 12.4f, 6.2f)
            moveTo(13.6f, 8.6f)
            lineTo(9.4f, 13.8f)
            lineTo(6.4f, 17.4f)
            moveTo(10.8f, 12.2f)
            lineTo(14.4f, 14.2f)
            lineTo(17.4f, 19.4f)
        }
    }

    val swim: ImageVector = ohIcon("swim") {
        stroke {
            moveTo(3.5f, 8f)
            quadTo(7f, 4.5f, 12f, 8f)
            quadTo(17f, 11.5f, 20.5f, 8f)
            moveTo(3.5f, 12f)
            quadTo(7f, 8.5f, 12f, 12f)
            quadTo(17f, 15.5f, 20.5f, 12f)
            moveTo(3.5f, 16f)
            quadTo(7f, 12.5f, 12f, 16f)
            quadTo(17f, 19.5f, 20.5f, 16f)
        }
    }

    val boxing: ImageVector = ohIcon("boxing") {
        stroke {
            moveTo(7f, 16.5f)
            quadTo(6f, 5.5f, 13f, 4.5f)
            quadTo(19.5f, 4f, 19.5f, 11f)
            quadTo(19.5f, 16.5f, 13f, 16.5f)
            close()
            moveTo(8.5f, 16.5f)
            lineTo(8.5f, 20.5f)
            lineTo(15f, 20.5f)
            lineTo(15f, 16.5f)
        }
    }

    val stretch: ImageVector = ohIcon("stretch") {
        stroke {
            moveTo(10f, 5.9f)
            arcTo(2f, 2f, 0f, false, true, 14f, 5.9f)
            arcTo(2f, 2f, 0f, false, true, 10f, 5.9f)
            moveTo(12f, 8.8f)
            lineTo(6.5f, 4.5f)
            moveTo(12f, 8.8f)
            lineTo(17.5f, 4.5f)
            moveTo(12f, 9.6f)
            lineTo(12f, 16f)
            moveTo(12f, 16f)
            lineTo(8.5f, 20.5f)
            moveTo(12f, 16f)
            lineTo(15.5f, 20.5f)
        }
    }

    val moon: ImageVector = ohIcon("moon") {
        stroke {
            moveTo(15.5f, 4.5f)
            curveTo(7f, 6.5f, 7f, 17.5f, 15.5f, 19.5f)
            curveTo(11.5f, 17.5f, 11.5f, 6.5f, 15.5f, 4.5f)
            close()
        }
    }

    val flame: ImageVector = ohIcon("flame") {
        stroke {
            moveTo(12.4f, 3.5f)
            curveTo(14.6f, 6.8f, 17.8f, 8.8f, 17.8f, 13.4f)
            curveTo(17.8f, 17.4f, 15.2f, 20.5f, 12f, 20.5f)
            curveTo(9f, 20.5f, 6.4f, 17.6f, 6.4f, 13.8f)
            curveTo(6.4f, 11f, 8f, 9.6f, 8.6f, 7.4f)
            curveTo(9.2f, 9.2f, 10.6f, 9.8f, 11.2f, 11.2f)
            curveTo(11.6f, 8.4f, 11.6f, 5.4f, 12.4f, 3.5f)
            close()
        }
    }

    val bolt: ImageVector = ohIcon("bolt") {
        stroke {
            moveTo(13f, 3.5f)
            lineTo(6.5f, 13.5f)
            lineTo(11f, 13.5f)
            lineTo(10f, 20.5f)
            lineTo(17.5f, 10.5f)
            lineTo(13f, 10.5f)
            close()
        }
    }
}
