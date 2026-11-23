@file:Suppress("MagicNumber")

package app.openhealth.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import app.openhealth.ui.theme.ohDims

/** Navigation, actions and status icons (`DESIGN.md` §10.2). */
object OhIcons {

    val today: ImageVector = ohIcon("today") {
        stroke {
            moveTo(7.8f, 12f)
            arcTo(4.2f, 4.2f, 0f, false, true, 16.2f, 12f)
            arcTo(4.2f, 4.2f, 0f, false, true, 7.8f, 12f)
            moveTo(12f, 5.8f)
            lineTo(12f, 3f)
            moveTo(12f, 18.2f)
            lineTo(12f, 21f)
            moveTo(5.8f, 12f)
            lineTo(3f, 12f)
            moveTo(18.2f, 12f)
            lineTo(21f, 12f)
            moveTo(7.6f, 7.6f)
            lineTo(5.6f, 5.6f)
            moveTo(16.4f, 7.6f)
            lineTo(18.4f, 5.6f)
            moveTo(7.6f, 16.4f)
            lineTo(5.6f, 18.4f)
            moveTo(16.4f, 16.4f)
            lineTo(18.4f, 18.4f)
        }
    }

    val vitals: ImageVector = ohIcon("vitals") {
        stroke {
            moveTo(9.2f, 8f)
            arcTo(2.8f, 2.8f, 0f, false, true, 14.8f, 8f)
            arcTo(2.8f, 2.8f, 0f, false, true, 9.2f, 8f)
            moveTo(4.5f, 20.5f)
            lineTo(4.5f, 18.6f)
            curveTo(4.5f, 15.4f, 7.2f, 13.4f, 12f, 13.4f)
            curveTo(16.8f, 13.4f, 19.5f, 15.4f, 19.5f, 18.6f)
            lineTo(19.5f, 20.5f)
        }
    }

    val train: ImageVector = ohIcon("train") {
        stroke {
            moveTo(7f, 12f)
            lineTo(17f, 12f)
            moveTo(7f, 8.5f)
            lineTo(7f, 15.5f)
            moveTo(17f, 8.5f)
            lineTo(17f, 15.5f)
            moveTo(4.5f, 10.5f)
            lineTo(4.5f, 13.5f)
            moveTo(19.5f, 10.5f)
            lineTo(19.5f, 13.5f)
        }
    }

    val nutrition: ImageVector = ohIcon("nutrition") {
        stroke {
            moveTo(7f, 3.5f)
            lineTo(7f, 8.5f)
            moveTo(9.5f, 3.5f)
            lineTo(9.5f, 8.5f)
            moveTo(12f, 3.5f)
            lineTo(12f, 8.5f)
            moveTo(7f, 8.5f)
            lineTo(12f, 8.5f)
            moveTo(9.5f, 8.5f)
            lineTo(9.5f, 20.5f)
            moveTo(16.5f, 3.5f)
            lineTo(16.5f, 20.5f)
            moveTo(16.5f, 3.5f)
            curveTo(18.6f, 5.5f, 18.6f, 9.5f, 16.5f, 12f)
        }
    }

    val stats: ImageVector = ohIcon("stats") {
        stroke {
            moveTo(4f, 4.5f)
            lineTo(4f, 20f)
            lineTo(20.5f, 20f)
            moveTo(8f, 20f)
            lineTo(8f, 14.5f)
            moveTo(12.5f, 20f)
            lineTo(12.5f, 8.5f)
            moveTo(17f, 20f)
            lineTo(17f, 12f)
        }
    }

    val more: ImageVector = ohIcon("more") {
        stroke {
            moveTo(3.5f, 6.5f)
            lineTo(9f, 6.5f)
            arcTo(2f, 2f, 0f, false, true, 13f, 6.5f)
            arcTo(2f, 2f, 0f, false, true, 9f, 6.5f)
            moveTo(13f, 6.5f)
            lineTo(20.5f, 6.5f)
            moveTo(3.5f, 12.5f)
            lineTo(6f, 12.5f)
            arcTo(2f, 2f, 0f, false, true, 10f, 12.5f)
            arcTo(2f, 2f, 0f, false, true, 6f, 12.5f)
            moveTo(10f, 12.5f)
            lineTo(20.5f, 12.5f)
            moveTo(3.5f, 18.5f)
            lineTo(13f, 18.5f)
            arcTo(2f, 2f, 0f, false, true, 17f, 18.5f)
            arcTo(2f, 2f, 0f, false, true, 13f, 18.5f)
            moveTo(17f, 18.5f)
            lineTo(20.5f, 18.5f)
        }
    }

    val add: ImageVector = ohIcon("add") {
        stroke {
            moveTo(12f, 4f)
            lineTo(12f, 20f)
            moveTo(4f, 12f)
            lineTo(20f, 12f)
        }
    }

    val check: ImageVector = ohIcon("check") {
        stroke {
            moveTo(4.5f, 12.5f)
            lineTo(9.5f, 17.5f)
            lineTo(19.5f, 6.5f)
        }
    }

    val close: ImageVector = ohIcon("close") {
        stroke {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
    }

    val chevronLeft: ImageVector = ohIcon("chevronLeft", autoMirror = true) {
        stroke(strokeWidth = ohDims.chevronStroke) {
            moveTo(14.5f, 5f)
            lineTo(7.5f, 12f)
            lineTo(14.5f, 19f)
        }
    }

    val chevronRight: ImageVector = ohIcon("chevronRight", autoMirror = true) {
        stroke(strokeWidth = ohDims.chevronStroke) {
            moveTo(9.5f, 5f)
            lineTo(16.5f, 12f)
            lineTo(9.5f, 19f)
        }
    }

    val chevronDown: ImageVector = ohIcon("chevronDown", autoMirror = true) {
        stroke(strokeWidth = ohDims.chevronStroke) {
            moveTo(5f, 9.5f)
            lineTo(12f, 16.5f)
            lineTo(19f, 9.5f)
        }
    }

    val search: ImageVector = ohIcon("search") {
        stroke {
            moveTo(4.8f, 10.8f)
            arcTo(6f, 6f, 0f, false, true, 16.8f, 10.8f)
            arcTo(6f, 6f, 0f, false, true, 4.8f, 10.8f)
            moveTo(15f, 15f)
            lineTo(20.2f, 20.2f)
        }
    }

    val filter: ImageVector = ohIcon("filter") {
        stroke {
            moveTo(3.5f, 4.5f)
            lineTo(20.5f, 4.5f)
            lineTo(13.5f, 12.5f)
            lineTo(13.5f, 20f)
            lineTo(10.5f, 17.5f)
            lineTo(10.5f, 12.5f)
            close()
        }
    }

    val scan: ImageVector = ohIcon("scan") {
        stroke {
            moveTo(3.5f, 8f)
            lineTo(3.5f, 3.5f)
            lineTo(8f, 3.5f)
            moveTo(16f, 3.5f)
            lineTo(20.5f, 3.5f)
            lineTo(20.5f, 8f)
            moveTo(20.5f, 16f)
            lineTo(20.5f, 20.5f)
            lineTo(16f, 20.5f)
            moveTo(8f, 20.5f)
            lineTo(3.5f, 20.5f)
            lineTo(3.5f, 16f)
            moveTo(6f, 12f)
            lineTo(18f, 12f)
        }
    }

    val camera: ImageVector = ohIcon("camera") {
        stroke {
            moveTo(8.6f, 7.5f)
            lineTo(9.6f, 4.8f)
            lineTo(14.4f, 4.8f)
            lineTo(15.4f, 7.5f)
            moveTo(5.5f, 7.5f)
            lineTo(18.5f, 7.5f)
            quadTo(20.5f, 7.5f, 20.5f, 9.5f)
            lineTo(20.5f, 18f)
            quadTo(20.5f, 20f, 18.5f, 20f)
            lineTo(5.5f, 20f)
            quadTo(3.5f, 20f, 3.5f, 18f)
            lineTo(3.5f, 9.5f)
            quadTo(3.5f, 7.5f, 5.5f, 7.5f)
            moveTo(8.7f, 13.6f)
            arcTo(3.3f, 3.3f, 0f, false, true, 15.3f, 13.6f)
            arcTo(3.3f, 3.3f, 0f, false, true, 8.7f, 13.6f)
        }
    }

    val play: ImageVector = ohIcon("play") {
        stroke {
            moveTo(7.5f, 4.5f)
            lineTo(19f, 12f)
            lineTo(7.5f, 19.5f)
            close()
        }
    }

    val pause: ImageVector = ohIcon("pause") {
        stroke {
            moveTo(9f, 5f)
            lineTo(9f, 19f)
            moveTo(15f, 5f)
            lineTo(15f, 19f)
        }
    }

    val skip: ImageVector = ohIcon("skip") {
        stroke {
            moveTo(6f, 5f)
            lineTo(16f, 12f)
            lineTo(6f, 19f)
            close()
            moveTo(18.5f, 5f)
            lineTo(18.5f, 19f)
        }
    }

    val timer: ImageVector = ohIcon("timer") {
        stroke {
            moveTo(5f, 13.5f)
            arcTo(7f, 7f, 0f, false, true, 19f, 13.5f)
            arcTo(7f, 7f, 0f, false, true, 5f, 13.5f)
            moveTo(12f, 6.5f)
            lineTo(12f, 3.5f)
            moveTo(9f, 3.5f)
            lineTo(15f, 3.5f)
            moveTo(12f, 13.5f)
            lineTo(12f, 9.5f)
            moveTo(12f, 13.5f)
            lineTo(15.2f, 15.2f)
        }
    }

    val edit: ImageVector = ohIcon("edit") {
        stroke {
            moveTo(4f, 20f)
            lineTo(5.5f, 15f)
            lineTo(16.5f, 4f)
            lineTo(20f, 7.5f)
            lineTo(9f, 18.5f)
            close()
            moveTo(15f, 5.5f)
            lineTo(18.5f, 9f)
        }
    }

    val delete: ImageVector = ohIcon("delete") {
        stroke {
            moveTo(4f, 6.5f)
            lineTo(20f, 6.5f)
            moveTo(9.5f, 6.5f)
            lineTo(9.5f, 3.5f)
            lineTo(14.5f, 3.5f)
            lineTo(14.5f, 6.5f)
            moveTo(6.2f, 6.5f)
            lineTo(7f, 19.4f)
            quadTo(7.1f, 20.5f, 8.2f, 20.5f)
            lineTo(15.8f, 20.5f)
            quadTo(16.9f, 20.5f, 17f, 19.4f)
            lineTo(17.8f, 6.5f)
            moveTo(10.3f, 10.5f)
            lineTo(10.6f, 17f)
            moveTo(13.7f, 10.5f)
            lineTo(13.4f, 17f)
        }
    }

    val undo: ImageVector = ohIcon("undo", autoMirror = true) {
        stroke {
            moveTo(6f, 10f)
            lineTo(10f, 6f)
            moveTo(6f, 10f)
            lineTo(10f, 14f)
            moveTo(6f, 10f)
            lineTo(15f, 10f)
            curveTo(19.5f, 10f, 19.5f, 17.5f, 14f, 17.5f)
            lineTo(11f, 17.5f)
        }
    }

    val share: ImageVector = ohIcon("share", autoMirror = true) {
        stroke {
            moveTo(8f, 10.5f)
            lineTo(5f, 10.5f)
            lineTo(5f, 20.5f)
            lineTo(19f, 20.5f)
            lineTo(19f, 10.5f)
            lineTo(16f, 10.5f)
            moveTo(12f, 14.5f)
            lineTo(12f, 3.5f)
            moveTo(8.5f, 7f)
            lineTo(12f, 3.5f)
            lineTo(15.5f, 7f)
        }
    }

    val export: ImageVector = ohIcon("export", autoMirror = true) {
        stroke {
            moveTo(3.5f, 14f)
            lineTo(3.5f, 20.5f)
            lineTo(20.5f, 20.5f)
            lineTo(20.5f, 14f)
            moveTo(12f, 16f)
            lineTo(12f, 3.5f)
            moveTo(8f, 7.5f)
            lineTo(12f, 3.5f)
            lineTo(16f, 7.5f)
        }
    }

    val lock: ImageVector = ohIcon("lock") {
        stroke {
            moveTo(8f, 10.5f)
            lineTo(8f, 8f)
            quadTo(8f, 4f, 12f, 4f)
            quadTo(16f, 4f, 16f, 8f)
            lineTo(16f, 10.5f)
            moveTo(6.5f, 10.5f)
            lineTo(17.5f, 10.5f)
            quadTo(19f, 10.5f, 19f, 12f)
            lineTo(19f, 19f)
            quadTo(19f, 20.5f, 17.5f, 20.5f)
            lineTo(6.5f, 20.5f)
            quadTo(5f, 20.5f, 5f, 19f)
            lineTo(5f, 12f)
            quadTo(5f, 10.5f, 6.5f, 10.5f)
            moveTo(12f, 14f)
            lineTo(12f, 17f)
        }
    }

    val cloudOff: ImageVector = ohIcon("cloudOff") {
        stroke {
            moveTo(7f, 17.5f)
            curveTo(4f, 17.5f, 3.5f, 12f, 6.5f, 11f)
            curveTo(7f, 6.5f, 14f, 6.5f, 15f, 11f)
            curveTo(20.5f, 11f, 20.5f, 17.5f, 17f, 17.5f)
            lineTo(7f, 17.5f)
            moveTo(4.5f, 20.5f)
            lineTo(19.5f, 4.5f)
        }
    }

    val refresh: ImageVector = ohIcon("refresh") {
        stroke {
            moveTo(19f, 12f)
            curveTo(19f, 15.9f, 15.9f, 19f, 12f, 19f)
            curveTo(8.1f, 19f, 5f, 15.9f, 5f, 12f)
            curveTo(5f, 8.1f, 8.1f, 5f, 12f, 5f)
            moveTo(9.7f, 3.3f)
            lineTo(12f, 5f)
            lineTo(9.7f, 6.7f)
        }
    }

    val info: ImageVector = ohIcon("info") {
        stroke {
            moveTo(4f, 12f)
            arcTo(8f, 8f, 0f, false, true, 20f, 12f)
            arcTo(8f, 8f, 0f, false, true, 4f, 12f)
            moveTo(12f, 11f)
            lineTo(12f, 17f)
            moveTo(12f, 7.4f)
            lineTo(12f, 7.5f)
        }
    }

    val warning: ImageVector = ohIcon("warning") {
        stroke {
            moveTo(10.2f, 5.2f)
            quadTo(12f, 2.5f, 13.8f, 5.2f)
            lineTo(20.4f, 17.4f)
            quadTo(21.3f, 19.5f, 18.9f, 19.5f)
            lineTo(5.1f, 19.5f)
            quadTo(2.7f, 19.5f, 3.6f, 17.4f)
            close()
            moveTo(12f, 9.5f)
            lineTo(12f, 14.5f)
            moveTo(12f, 17f)
            lineTo(12f, 17.1f)
        }
    }

    val externalLink: ImageVector = ohIcon("externalLink", autoMirror = true) {
        stroke {
            moveTo(11f, 4.5f)
            lineTo(4.5f, 4.5f)
            lineTo(4.5f, 19.5f)
            lineTo(19.5f, 19.5f)
            lineTo(19.5f, 13f)
            moveTo(10f, 14f)
            lineTo(19.5f, 4.5f)
            moveTo(13.5f, 4.5f)
            lineTo(19.5f, 4.5f)
            lineTo(19.5f, 10.5f)
        }
    }

    val settings: ImageVector = ohIcon("settings") {
        stroke {
            moveTo(8.6f, 12f)
            arcTo(3.4f, 3.4f, 0f, false, true, 15.4f, 12f)
            arcTo(3.4f, 3.4f, 0f, false, true, 8.6f, 12f)
            moveTo(12f, 8.6f)
            lineTo(12f, 5.6f)
            moveTo(12f, 15.4f)
            lineTo(12f, 18.4f)
            moveTo(8.6f, 12f)
            lineTo(5.6f, 12f)
            moveTo(15.4f, 12f)
            lineTo(18.4f, 12f)
            moveTo(9.6f, 9.6f)
            lineTo(7.5f, 7.5f)
            moveTo(14.4f, 9.6f)
            lineTo(16.5f, 7.5f)
            moveTo(9.6f, 14.4f)
            lineTo(7.5f, 16.5f)
            moveTo(14.4f, 14.4f)
            lineTo(16.5f, 16.5f)
        }
    }

    val remove: ImageVector = ohIcon("remove") {
        stroke {
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    }
}
