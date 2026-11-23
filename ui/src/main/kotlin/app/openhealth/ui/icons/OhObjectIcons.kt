@file:Suppress("MagicNumber")

package app.openhealth.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector

/** Equipment and object glyphs (`DESIGN.md` §10.2). */
object OhObjectIcons {

    val barbell: ImageVector = ohIcon("barbell") {
        stroke {
            moveTo(4f, 19.5f)
            lineTo(20f, 4.5f)
            moveTo(4.5f, 15.2f)
            lineTo(8.3f, 19.3f)
            moveTo(6.8f, 14.7f)
            lineTo(8.9f, 17.1f)
            moveTo(15.1f, 6.9f)
            lineTo(17.3f, 9.3f)
            moveTo(15.7f, 4.7f)
            lineTo(19.5f, 8.8f)
        }
    }

    val dumbbell: ImageVector = ohIcon("dumbbell") {
        stroke {
            moveTo(8f, 12f)
            lineTo(16f, 12f)
            moveTo(8f, 8f)
            lineTo(8f, 16f)
            moveTo(16f, 8f)
            lineTo(16f, 16f)
            moveTo(5.5f, 10f)
            lineTo(5.5f, 14f)
            moveTo(18.5f, 10f)
            lineTo(18.5f, 14f)
            moveTo(11f, 10.5f)
            lineTo(11f, 13.5f)
            moveTo(13f, 10.5f)
            lineTo(13f, 13.5f)
        }
    }

    val kettlebell: ImageVector = ohIcon("kettlebell") {
        stroke {
            moveTo(7f, 15f)
            arcTo(5f, 5f, 0f, false, true, 17f, 15f)
            arcTo(5f, 5f, 0f, false, true, 7f, 15f)
            moveTo(8.6f, 11.4f)
            quadTo(8.4f, 4.5f, 12f, 4.5f)
            quadTo(15.6f, 4.5f, 15.4f, 11.4f)
        }
    }

    val plate: ImageVector = ohIcon("plate") {
        stroke {
            moveTo(4f, 12f)
            arcTo(8f, 8f, 0f, false, true, 20f, 12f)
            arcTo(8f, 8f, 0f, false, true, 4f, 12f)
            moveTo(9.5f, 12f)
            arcTo(2.5f, 2.5f, 0f, false, true, 14.5f, 12f)
            arcTo(2.5f, 2.5f, 0f, false, true, 9.5f, 12f)
        }
    }

    val machine: ImageVector = ohIcon("machine") {
        stroke {
            moveTo(5f, 20.5f)
            lineTo(5f, 4f)
            lineTo(12f, 4f)
            moveTo(5f, 20.5f)
            lineTo(19.5f, 20.5f)
            moveTo(9.4f, 7f)
            arcTo(2.6f, 2.6f, 0f, false, true, 14.6f, 7f)
            arcTo(2.6f, 2.6f, 0f, false, true, 9.4f, 7f)
            moveTo(12f, 9.6f)
            lineTo(12f, 13.5f)
            moveTo(9f, 14f)
            lineTo(15f, 14f)
        }
    }

    val band: ImageVector = ohIcon("band") {
        stroke {
            moveTo(4f, 12f)
            curveTo(4f, 8.5f, 7.5f, 6.5f, 12f, 6.5f)
            curveTo(16.5f, 6.5f, 20f, 8.5f, 20f, 12f)
            curveTo(20f, 15.5f, 16.5f, 17.5f, 12f, 17.5f)
            curveTo(7.5f, 17.5f, 4f, 15.5f, 4f, 12f)
            close()
        }
    }

    val mat: ImageVector = ohIcon("mat") {
        stroke {
            moveTo(5f, 12f)
            arcTo(3.5f, 3.5f, 0f, false, true, 12f, 12f)
            arcTo(3.5f, 3.5f, 0f, false, true, 5f, 12f)
            moveTo(8.5f, 10.4f)
            quadTo(10.6f, 12f, 8.5f, 13.6f)
            moveTo(12f, 8.5f)
            lineTo(19f, 8.5f)
            quadTo(21f, 8.5f, 21f, 12f)
            quadTo(21f, 15.5f, 19f, 15.5f)
            lineTo(12f, 15.5f)
        }
    }

    val shoe: ImageVector = ohIcon("shoe") {
        stroke {
            moveTo(4f, 17f)
            lineTo(4.5f, 11f)
            quadTo(4.6f, 9.5f, 6f, 9.5f)
            lineTo(9f, 9.5f)
            lineTo(12.5f, 12.5f)
            lineTo(19f, 13.5f)
            quadTo(20.5f, 13.7f, 20.5f, 15.2f)
            lineTo(20.5f, 17f)
            close()
            moveTo(8f, 10.2f)
            lineTo(10.8f, 12.9f)
        }
    }

    val bike: ImageVector = ohIcon("bike") {
        stroke {
            moveTo(3f, 15.5f)
            arcTo(3.5f, 3.5f, 0f, false, true, 10f, 15.5f)
            arcTo(3.5f, 3.5f, 0f, false, true, 3f, 15.5f)
            moveTo(14f, 15.5f)
            arcTo(3.5f, 3.5f, 0f, false, true, 21f, 15.5f)
            arcTo(3.5f, 3.5f, 0f, false, true, 14f, 15.5f)
            moveTo(12f, 15.5f)
            lineTo(10.5f, 8.5f)
            moveTo(8.5f, 8f)
            lineTo(12.5f, 8f)
            moveTo(10.5f, 8.5f)
            lineTo(16f, 8.5f)
            moveTo(12f, 15.5f)
            lineTo(16f, 8.5f)
            moveTo(12f, 15.5f)
            lineTo(6.5f, 15.5f)
            moveTo(16f, 8.5f)
            lineTo(17.5f, 15.5f)
            moveTo(16f, 8.5f)
            lineTo(18.5f, 7.5f)
        }
    }

    val route: ImageVector = ohIcon("route") {
        stroke {
            moveTo(6.5f, 18.5f)
            curveTo(7f, 14f, 13.5f, 15.5f, 14f, 12f)
            curveTo(14.5f, 8.5f, 15.5f, 8f, 17f, 5.5f)
            moveTo(5.1f, 18.5f)
            arcTo(1.4f, 1.4f, 0f, false, true, 7.9f, 18.5f)
            arcTo(1.4f, 1.4f, 0f, false, true, 5.1f, 18.5f)
            moveTo(15.6f, 5.5f)
            arcTo(1.4f, 1.4f, 0f, false, true, 18.4f, 5.5f)
            arcTo(1.4f, 1.4f, 0f, false, true, 15.6f, 5.5f)
        }
    }

    val scale: ImageVector = ohIcon("scale") {
        stroke {
            moveTo(6f, 9.5f)
            lineTo(18f, 9.5f)
            quadTo(20.5f, 9.5f, 20.5f, 12f)
            lineTo(20.5f, 15.5f)
            quadTo(20.5f, 18f, 18f, 18f)
            lineTo(6f, 18f)
            quadTo(3.5f, 18f, 3.5f, 15.5f)
            lineTo(3.5f, 12f)
            quadTo(3.5f, 9.5f, 6f, 9.5f)
            moveTo(6.9f, 13.7f)
            arcTo(1.8f, 2.8f, 0f, false, true, 10.5f, 13.7f)
            arcTo(1.8f, 2.8f, 0f, false, true, 6.9f, 13.7f)
            moveTo(13.5f, 13.7f)
            arcTo(1.8f, 2.8f, 0f, false, true, 17.1f, 13.7f)
            arcTo(1.8f, 2.8f, 0f, false, true, 13.5f, 13.7f)
        }
    }

    val cuff: ImageVector = ohIcon("cuff") {
        stroke {
            moveTo(6.5f, 6f)
            lineTo(17.5f, 6f)
            quadTo(20.5f, 6f, 20.5f, 9f)
            lineTo(20.5f, 11f)
            quadTo(20.5f, 13.5f, 17.5f, 13.5f)
            lineTo(6.5f, 13.5f)
            quadTo(3.5f, 13.5f, 3.5f, 11f)
            lineTo(3.5f, 9f)
            quadTo(3.5f, 6f, 6.5f, 6f)
            moveTo(8f, 6f)
            lineTo(8f, 13.5f)
            moveTo(19f, 13.5f)
            lineTo(19f, 17f)
            quadTo(19f, 20.5f, 15.5f, 20.5f)
            lineTo(13f, 20.5f)
        }
    }

    val glucose: ImageVector = ohIcon("glucose") {
        stroke {
            moveTo(12f, 3.5f)
            curveTo(9.5f, 8f, 5f, 11f, 5f, 15f)
            curveTo(5f, 18.9f, 8.1f, 21f, 12f, 21f)
            curveTo(15.9f, 21f, 19f, 18.9f, 19f, 15f)
            curveTo(19f, 11f, 14.5f, 8f, 12f, 3.5f)
            moveTo(12f, 11.8f)
            lineTo(12f, 18.2f)
            moveTo(8.9f, 15f)
            lineTo(15.1f, 15f)
        }
    }

    val pill: ImageVector = ohIcon("pill") {
        stroke {
            moveTo(6.4f, 13.4f)
            lineTo(13.4f, 6.4f)
            quadTo(17.6f, 6.4f, 17.6f, 10.6f)
            lineTo(10.6f, 17.6f)
            quadTo(6.4f, 17.6f, 6.4f, 13.4f)
            close()
            moveTo(9.9f, 14.1f)
            lineTo(14.1f, 9.9f)
        }
    }

    val flask: ImageVector = ohIcon("flask") {
        stroke {
            moveTo(9.5f, 3.5f)
            lineTo(9.5f, 8.5f)
            lineTo(4.5f, 18.5f)
            quadTo(4.2f, 20.5f, 6.2f, 20.5f)
            lineTo(17.8f, 20.5f)
            quadTo(19.8f, 20.5f, 19.5f, 18.5f)
            lineTo(14.5f, 8.5f)
            lineTo(14.5f, 3.5f)
            close()
            moveTo(6.6f, 14.5f)
            lineTo(17.4f, 14.5f)
        }
    }

    val file: ImageVector = ohIcon("file") {
        stroke {
            moveTo(6f, 3.5f)
            lineTo(13.5f, 3.5f)
            lineTo(18.5f, 8.5f)
            lineTo(18.5f, 20.5f)
            lineTo(6f, 20.5f)
            close()
            moveTo(13.5f, 3.5f)
            lineTo(13.5f, 8.5f)
            lineTo(18.5f, 8.5f)
        }
    }

    val watch: ImageVector = ohIcon("watch") {
        stroke {
            moveTo(9.5f, 7f)
            lineTo(9.5f, 4.5f)
            lineTo(14.5f, 4.5f)
            lineTo(14.5f, 7f)
            moveTo(9.5f, 17f)
            lineTo(9.5f, 19.5f)
            lineTo(14.5f, 19.5f)
            lineTo(14.5f, 17f)
            moveTo(9f, 7f)
            lineTo(15f, 7f)
            quadTo(17f, 7f, 17f, 9f)
            lineTo(17f, 15f)
            quadTo(17f, 17f, 15f, 17f)
            lineTo(9f, 17f)
            quadTo(7f, 17f, 7f, 15f)
            lineTo(7f, 9f)
            quadTo(7f, 7f, 9f, 7f)
            moveTo(17f, 11f)
            lineTo(18.5f, 11f)
        }
    }

    val phone: ImageVector = ohIcon("phone") {
        stroke {
            moveTo(8.5f, 3f)
            lineTo(15.5f, 3f)
            quadTo(17.5f, 3f, 17.5f, 5f)
            lineTo(17.5f, 19f)
            quadTo(17.5f, 21f, 15.5f, 21f)
            lineTo(8.5f, 21f)
            quadTo(6.5f, 21f, 6.5f, 19f)
            lineTo(6.5f, 5f)
            quadTo(6.5f, 3f, 8.5f, 3f)
            moveTo(10.5f, 18f)
            lineTo(13.5f, 18f)
        }
    }
}
