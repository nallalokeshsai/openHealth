package app.openhealth.ui.screens.clinical

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonSize
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhSegmentedControl
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.theme.OhReportPaper
import app.openhealth.ui.theme.OhTheme

/** The report paper is always light (`DESIGN.md` §8.9), even inside a dark app. */
private val Paper = OhReportPaper.paper
private val Ink = OhReportPaper.ink
private val InkSoft = OhReportPaper.inkSoft

/**
 * The reports page (`pr.md` §6.9, §6.17). The preview is the report's own text, printed
 * on light paper; the exports are the PDF, a plain CSV, FHIR R4 and Open mHealth. All
 * four are produced on the device and offline.
 */
@Composable
fun ReportsScreen(
    model: ReportsModel,
    onBack: () -> Unit,
    onSelectRange: (Int) -> Unit,
    onExport: (ReportFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OhTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = OhTheme.space.s6),
    ) {
        OhPageHeader(title = model.title, onBack = onBack)
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = OhTheme.space.s8),
        ) {
            OhSegmentedControl(
                options = model.ranges.map { it.label },
                selectedIndex = model.selectedRange,
                onSelect = onSelectRange,
            )
            Text(
                text = model.rangeNote,
                style = OhTheme.type.caption,
                color = OhTheme.colors.label2,
                modifier = Modifier.padding(vertical = OhTheme.space.s3),
            )
            OhCard {
                Text(
                    text = model.previewTitle,
                    style = OhTheme.type.title3,
                    color = OhTheme.colors.label,
                    modifier = Modifier.padding(
                        start = OhTheme.space.s4,
                        end = OhTheme.space.s4,
                        top = OhTheme.space.s4,
                    ),
                )
                Text(
                    text = model.generatedLabel,
                    style = OhTheme.type.caption,
                    color = OhTheme.colors.label2,
                    modifier = Modifier.padding(
                        start = OhTheme.space.s4,
                        end = OhTheme.space.s4,
                        bottom = OhTheme.space.s3,
                    ),
                )
                Column(
                    modifier = Modifier
                        .padding(horizontal = OhTheme.space.s3)
                        .clip(RoundedCornerShape(OhTheme.radius.sm))
                        .background(Paper)
                        .padding(OhTheme.space.s4),
                ) {
                    if (model.empty) {
                        Text(
                            text = model.emptyBody,
                            style = OhTheme.type.body,
                            color = Ink,
                        )
                    } else {
                        model.previewLines.forEach { line ->
                            Text(
                                text = line,
                                style = OhTheme.type.caption,
                                color = if (line.startsWith("openHealth")) Ink else InkSoft,
                            )
                        }
                        if (model.previewTruncated) {
                            Spacer(Modifier.height(OhTheme.space.s2))
                            Text(
                                text = stringResource(R.string.clinical_report_truncated),
                                style = OhTheme.type.caption,
                                color = InkSoft,
                            )
                        }
                    }
                }
                Text(
                    text = model.disclaimer,
                    style = OhTheme.type.caption,
                    color = OhTheme.colors.label2,
                    modifier = Modifier.padding(OhTheme.space.s4),
                )
            }
            Spacer(Modifier.height(OhTheme.space.s5))
            model.exports.forEach { format ->
                Row(modifier = Modifier.padding(bottom = OhTheme.space.s3)) {
                    OhButton(
                        text = format.label(),
                        onClick = { onExport(format) },
                        variant = if (format == ReportFormat.PDF) {
                            OhButtonVariant.Primary
                        } else {
                            OhButtonVariant.Secondary
                        },
                        enabled = !model.empty,
                        modifier = Modifier.weight(1f),
                        size = OhButtonSize.Default,
                    )
                    Spacer(Modifier.width(OhTheme.space.s3))
                }
            }
            Text(
                text = model.sourceNote,
                style = OhTheme.type.caption,
                color = OhTheme.colors.label2,
            )
        }
    }
}

@Composable
private fun ReportFormat.label(): String = stringResource(
    when (this) {
        ReportFormat.PDF -> R.string.clinical_export_pdf
        ReportFormat.CSV -> R.string.clinical_export_csv
        ReportFormat.FHIR -> R.string.clinical_export_fhir
        ReportFormat.MHEALTH -> R.string.clinical_export_mhealth
    },
)
