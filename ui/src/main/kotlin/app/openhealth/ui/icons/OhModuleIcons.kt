@file:Suppress("MagicNumber")

package app.openhealth.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector

/** Module glyphs (`DESIGN.md` §10.2): one per switchable module. */
object OhModuleIcons {

    val workout: ImageVector = ohIcon("workout") {
        stroke {
            moveTo(3f, 12f)
            lineTo(21f, 12f)
            moveTo(6.5f, 7f)
            lineTo(6.5f, 17f)
            moveTo(9f, 9.5f)
            lineTo(9f, 14.5f)
            moveTo(17.5f, 7f)
            lineTo(17.5f, 17f)
            moveTo(15f, 9.5f)
            lineTo(15f, 14.5f)
        }
    }

    val yoga: ImageVector = ohIcon("yoga") {
        stroke {
            moveTo(9.8f, 6.4f)
            arcTo(2.2f, 2.2f, 0f, false, true, 14.2f, 6.4f)
            arcTo(2.2f, 2.2f, 0f, false, true, 9.8f, 6.4f)
            moveTo(5.5f, 13.5f)
            quadTo(12f, 8f, 18.5f, 13.5f)
            moveTo(6f, 19.5f)
            quadTo(12f, 14f, 18f, 19.5f)
        }
    }

    val steps: ImageVector = ohIcon("steps") {
        stroke {
            moveTo(5.8f, 8.5f)
            arcTo(2.9f, 3.9f, 0f, false, true, 11.6f, 8.5f)
            arcTo(2.9f, 3.9f, 0f, false, true, 5.8f, 8.5f)
            moveTo(12.4f, 15.5f)
            arcTo(2.9f, 3.9f, 0f, false, true, 18.2f, 15.5f)
            arcTo(2.9f, 3.9f, 0f, false, true, 12.4f, 15.5f)
        }
    }

    val weight: ImageVector = ohIcon("weight") {
        stroke {
            moveTo(6.5f, 3.5f)
            lineTo(17.5f, 3.5f)
            quadTo(20.5f, 3.5f, 20.5f, 6.5f)
            lineTo(20.5f, 17.5f)
            quadTo(20.5f, 20.5f, 17.5f, 20.5f)
            lineTo(6.5f, 20.5f)
            quadTo(3.5f, 20.5f, 3.5f, 17.5f)
            lineTo(3.5f, 6.5f)
            quadTo(3.5f, 3.5f, 6.5f, 3.5f)
            moveTo(8.5f, 8f)
            lineTo(15.5f, 8f)
            lineTo(15.5f, 11.5f)
            lineTo(8.5f, 11.5f)
            close()
        }
    }

    val heart: ImageVector = ohIcon("heart") {
        stroke {
            moveTo(12f, 19.8f)
            curveTo(4.2f, 14.4f, 3.4f, 6.8f, 7.9f, 5.9f)
            curveTo(10.4f, 5.4f, 11.7f, 6.9f, 12f, 8.4f)
            curveTo(12.3f, 6.9f, 13.6f, 5.4f, 16.1f, 5.9f)
            curveTo(20.6f, 6.8f, 19.8f, 14.4f, 12f, 19.8f)
        }
    }

    val sleep: ImageVector = ohIcon("sleep") {
        stroke {
            moveTo(16f, 3.5f)
            curveTo(6f, 6f, 6f, 18f, 16f, 20.5f)
            curveTo(13f, 18f, 13f, 6f, 16f, 3.5f)
            close()
        }
    }

    val activity: ImageVector = ohIcon("activity") {
        stroke {
            moveTo(3f, 12f)
            lineTo(7f, 12f)
            lineTo(9.5f, 6.5f)
            lineTo(13.5f, 17.5f)
            lineTo(16f, 12f)
            lineTo(21f, 12f)
        }
    }

    val mood: ImageVector = ohIcon("mood") {
        stroke {
            moveTo(4f, 12f)
            arcTo(8f, 8f, 0f, false, true, 20f, 12f)
            arcTo(8f, 8f, 0f, false, true, 4f, 12f)
            moveTo(9f, 10f)
            lineTo(9f, 10.1f)
            moveTo(15f, 10f)
            lineTo(15f, 10.1f)
            moveTo(8.2f, 14.2f)
            quadTo(12f, 18.2f, 15.8f, 14.2f)
        }
    }

    val cycle: ImageVector = ohIcon("cycle") {
        stroke {
            moveTo(12f, 5f)
            curveTo(15.9f, 5f, 19f, 8.1f, 19f, 12f)
            curveTo(19f, 15.9f, 15.9f, 19f, 12f, 19f)
            curveTo(8.1f, 19f, 5f, 15.9f, 5f, 12f)
            moveTo(3.2f, 14.2f)
            lineTo(5f, 12f)
            lineTo(6.8f, 14.2f)
            moveTo(12f, 12f)
            lineTo(12f, 12.1f)
        }
    }

    val family: ImageVector = ohIcon("family") {
        stroke {
            moveTo(5.8f, 6.5f)
            arcTo(2.2f, 2.2f, 0f, false, true, 10.2f, 6.5f)
            arcTo(2.2f, 2.2f, 0f, false, true, 5.8f, 6.5f)
            moveTo(4.2f, 19.5f)
            curveTo(4.2f, 15f, 5.9f, 12f, 8f, 12f)
            curveTo(10.1f, 12f, 11.8f, 15f, 11.8f, 19.5f)
            moveTo(14.6f, 8f)
            arcTo(1.9f, 1.9f, 0f, false, true, 18.4f, 8f)
            arcTo(1.9f, 1.9f, 0f, false, true, 14.6f, 8f)
            moveTo(13.5f, 19.5f)
            curveTo(13.6f, 16.2f, 14.9f, 13.8f, 16.5f, 13.8f)
            curveTo(18.1f, 13.8f, 19.4f, 16.2f, 19.5f, 19.5f)
        }
    }

    val coach: ImageVector = ohIcon("coach") {
        stroke {
            moveTo(6f, 4.5f)
            lineTo(18f, 4.5f)
            quadTo(20.5f, 4.5f, 20.5f, 7f)
            lineTo(20.5f, 13.5f)
            quadTo(20.5f, 16f, 18f, 16f)
            lineTo(9f, 16f)
            lineTo(5f, 20f)
            lineTo(5f, 16f)
            quadTo(3.5f, 15.5f, 3.5f, 13.5f)
            lineTo(3.5f, 7f)
            quadTo(3.5f, 4.5f, 6f, 4.5f)
            moveTo(12f, 7.2f)
            lineTo(14f, 9.4f)
            lineTo(12f, 11.6f)
            lineTo(10f, 9.4f)
            close()
        }
    }

    val integrations: ImageVector = ohIcon("integrations") {
        stroke {
            moveTo(6f, 7.5f)
            lineTo(10f, 7.5f)
            quadTo(12.5f, 7.5f, 12.5f, 10f)
            quadTo(12.5f, 12.5f, 10f, 12.5f)
            lineTo(6f, 12.5f)
            quadTo(3.5f, 12.5f, 3.5f, 10f)
            quadTo(3.5f, 7.5f, 6f, 7.5f)
            moveTo(12f, 11.5f)
            lineTo(16f, 11.5f)
            quadTo(18.5f, 11.5f, 18.5f, 14f)
            quadTo(18.5f, 16.5f, 16f, 16.5f)
            lineTo(12f, 16.5f)
            quadTo(9.5f, 16.5f, 9.5f, 14f)
            quadTo(9.5f, 11.5f, 12f, 11.5f)
        }
    }
}
