package app.openhealth.android.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.openhealth.android.body.PhotoCodec
import app.openhealth.core.observation.ObservationType
import app.openhealth.ui.screens.vitals.VitalDraft
import app.openhealth.ui.screens.vitals.VitalsModel
import app.openhealth.ui.screens.vitals.VitalsRootScreen

/**
 * The Vitals tab with its two document pickers and the system photo picker. The pickers
 * are the only place a file or a gallery image enters the app: the CSV is read into memory,
 * the photo is decoded and re-encoded before it is stored, and neither needs a storage
 * permission (`pr.md` §6.25, `DESIGN.md` §9.4).
 */
@Composable
internal fun VitalsRouteContent(
    model: app.openhealth.ui.screens.vitals.VitalsModel,
    onLogWeight: () -> Unit,
    onOpenDetail: (ObservationType) -> Unit,
    onSaveReading: (app.openhealth.ui.screens.vitals.VitalDraft) -> Unit,
    onSaveMeasurement: (String, String, app.openhealth.core.observation.MeasurementMethod) -> Unit,
    onAddPhoto: (ByteArray) -> Unit,
    onOpenPhoto: (String) -> Unit,
    onDeletePhoto: (String) -> Unit,
    onPhotosHidden: (Boolean) -> Unit,
    onPhotosInExport: (Boolean) -> Unit,
    onExportCsv: (android.net.Uri) -> Unit,
    onImportCsv: (android.net.Uri) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val createCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) onExportCsv(uri)
    }
    val openCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onImportCsv(uri)
    }
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            PhotoCodec.read(context, uri)?.let(onAddPhoto)
        }
    }
    VitalsRootScreen(
        model = model,
        onLogWeight = onLogWeight,
        onOpenDetail = onOpenDetail,
        onSaveReading = onSaveReading,
        onSaveMeasurement = onSaveMeasurement,
        onAddPhoto = {
            pickPhoto.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onOpenPhoto = onOpenPhoto,
        onDeletePhoto = onDeletePhoto,
        onPhotosHidden = onPhotosHidden,
        onPhotosInExport = onPhotosInExport,
        onExportCsv = { createCsv.launch("openHealth-body.csv") },
        onImportCsv = {
            openCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
        },
        onOpenSettings = onOpenSettings,
    )
}
