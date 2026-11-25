package app.openhealth.ui.screens.vitals

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.openhealth.core.citation.CitationKey
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.screens.common.shortDate
import app.openhealth.ui.screens.common.timeOf
import app.openhealth.ui.theme.OhTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.TimeZone

/** What a formula sheet shows: the rule, its inputs and the citation behind it. */
data class FormulaSheetModel(val title: String, val body: String, val citation: String)

/**
 * The provenance sheet every Vitals number opens (`DESIGN.md` §7.1-7.2): source, method,
 * when it happened, when it was recorded, the local day and zone, quality, device, whether
 * it was edited and whether the raw record is still kept. Delete is destructive and is
 * only offered by the screen that owns the row, with its tombstone and undo.
 */
@Composable
fun VitalsProvenanceSheet(
    provenance: ValueProvenance,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.vitals_provenance_title),
        cancelLabel = stringResource(R.string.vitals_provenance_close),
        onCancel = onDismiss,
    ) {
        OhInsetGroup {
            OhRow(
                title = stringResource(R.string.vitals_detail_latest),
                value = provenance.valueText,
            )
            provenance.trendText?.let { trend ->
                OhRow(
                    title = stringResource(R.string.vitals_provenance_quality),
                    value = trend,
                )
            }
            OhRow(
                title = stringResource(R.string.vitals_provenance_source),
                value = provenance.sourceLabel,
            )
            provenance.method?.let { method ->
                OhRow(
                    title = stringResource(R.string.vitals_provenance_method),
                    value = method,
                )
            }
            OhRow(
                title = stringResource(R.string.vitals_provenance_happened),
                value = "${shortDate(provenance.localDate)} · " +
                    timeOf(provenance.happenedAt, TimeZone.of(provenance.zoneId)),
            )
            OhRow(
                title = stringResource(R.string.vitals_provenance_recorded),
                value = recordedText(provenance),
            )
            OhRow(
                title = stringResource(R.string.vitals_provenance_local_day),
                value = "${shortDate(provenance.localDate)} · ${provenance.zoneId}",
            )
            provenance.quality?.let { quality ->
                OhRow(
                    title = stringResource(R.string.vitals_provenance_quality),
                    value = quality,
                )
            }
            provenance.device?.let { device ->
                OhRow(
                    title = stringResource(R.string.vitals_provenance_device),
                    value = device,
                )
            }
            OhRow(
                title = stringResource(R.string.vitals_provenance_edited),
                value = if (provenance.edited) "Yes" else "No",
            )
            OhRow(
                title = stringResource(R.string.vitals_provenance_raw),
                value = if (provenance.rawRecord) {
                    stringResource(R.string.vitals_provenance_raw)
                } else {
                    stringResource(R.string.vitals_provenance_raw_missing)
                },
            )
        }
        if (onDelete != null) {
            Spacer(Modifier.height(OhTheme.space.s5))
            OhButton(
                text = stringResource(R.string.vitals_provenance_delete),
                onClick = onDelete,
                variant = OhButtonVariant.Danger,
            )
        }
    }
}

@Composable
private fun recordedText(provenance: ValueProvenance): String {
    val delay = provenance.recordedAt - provenance.happenedAt
    val base = "${shortDate(provenance.localDate)} · " +
        timeOf(provenance.recordedAt, TimeZone.of(provenance.zoneId))
    return if (delay < 1.minutes) {
        base
    } else {
        base + " · " + stringResource(R.string.vitals_provenance_later, delayWords(delay))
    }
}

@Composable
private fun delayWords(delay: Duration): String = when {
    delay >= 1.days -> {
        val days = delay.inWholeDays
        if (days == 1L) "1 day" else "$days days"
    }

    delay >= 1.hours -> {
        val hours = delay.inWholeHours
        if (hours == 1L) "1 hour" else "$hours hours"
    }

    else -> {
        val minutes = delay.inWholeMinutes.coerceAtLeast(1)
        if (minutes == 1L) "1 minute" else "$minutes minutes"
    }
}

/** The "how this is calculated" sheet of `pr.md` §6.4 and `DESIGN.md` §14.2. */
@Composable
fun FormulaSheet(sheet: FormulaSheetModel, onDismiss: () -> Unit) {
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = sheet.title,
        cancelLabel = stringResource(R.string.vitals_provenance_close),
        onCancel = onDismiss,
    ) {
        Text(
            text = sheet.body,
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        if (sheet.citation.isNotEmpty() && sheet.citation != "app") {
            val key = runCatching { CitationKey.valueOf(sheet.citation) }.getOrNull()
            if (key != null) {
                Spacer(Modifier.height(OhTheme.space.s4))
                Text(
                    text = citationLabel(key),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label,
                )
                Text(
                    text = citationTitle(key),
                    style = OhTheme.type.caption,
                    color = OhTheme.colors.label2,
                )
                Text(
                    text = citationAuthors(key),
                    style = OhTheme.type.caption,
                    color = OhTheme.colors.label2,
                )
            }
        }
    }
}

/** The before/after comparison sheet (`DESIGN.md` §9.4): older photo first, always. */
@Composable
fun PhotoCompareSheet(model: PhotoCompareModel, onDismiss: () -> Unit) {
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.vitals_photos_compare),
        cancelLabel = stringResource(R.string.vitals_table_close),
        onCancel = onDismiss,
    ) {
        OhBeforeAfterSlider(model = model)
        Spacer(Modifier.height(OhTheme.space.s3))
        Text(
            text = stringResource(R.string.vitals_photo_private),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
    }
}
