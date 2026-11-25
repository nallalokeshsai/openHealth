package app.openhealth.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.openhealth.ui.R
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.tabular

/** A chart's table alternative (`DESIGN.md` §8.6): the same numbers, never a picture. */
data class ChartTableModel(
    val title: String,
    val columns: List<String>,
    val rows: List<List<String>>,
    val citation: String? = null,
) {
    init {
        require(columns.isNotEmpty()) { "a chart table needs a column (DESIGN.md §8.6)" }
        require(rows.isNotEmpty()) { "a chart table needs at least one row (DESIGN.md §8.6)" }
        require(rows.all { it.size == columns.size }) {
            "every chart table row has one cell per column"
        }
    }
}

/**
 * One chart in a card: its title, the plot, a "Show as table" action and the "How this is
 * calculated" row of `DESIGN.md` §14.2. The description is the one sentence a screen
 * reader hears instead of the drawing.
 */
@Composable
fun OhChartCard(
    title: String,
    eyebrow: String,
    onShowTable: () -> Unit,
    onShowFormula: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: ImageVector? = null,
    subtitle: String? = null,
    footer: String? = null,
    tableLabel: String = stringResource(R.string.chart_show_table),
    formulaLabel: String = stringResource(R.string.chart_how_calculated),
    content: @Composable ColumnScope.() -> Unit,
) {
    OhCard(modifier = modifier) {
        OhCardHeader(eyebrow = eyebrow, title = title, glyph = glyph, subtitle = subtitle)
        Spacer(Modifier.height(OhTheme.space.s4))
        Box(
            modifier = Modifier.semantics {
                customActions = listOf(
                    CustomAccessibilityAction(tableLabel) {
                        onShowTable()
                        true
                    },
                )
            },
        ) {
            Column { content() }
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        OhChartLink(text = tableLabel, onClick = onShowTable)
        Spacer(Modifier.height(OhTheme.space.s1))
        OhChartLink(text = formulaLabel, onClick = onShowFormula)
        footer?.let {
            Spacer(Modifier.height(OhTheme.space.s2))
            Text(
                text = it,
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
    }
}

/** The 13 sp `label-2` action at the foot of a chart card, with a 44 dp target. */
@Composable
fun OhChartLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OhTheme.colors
    val interaction = rememberInteraction()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OhTheme.radius.sm))
            .ohClickable(interactionSource = interaction, onClick = onClick)
            .heightIn(min = OhTheme.dims.touchTarget)
            .padding(vertical = OhTheme.space.s1),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = OhTheme.type.footnote,
            color = colors.label2,
        )
    }
}

/**
 * The table a chart's "Show as table" action opens: a plain list of rows, the unit in
 * the column header, and the chart's citation at the foot. It is reachable from TalkBack
 * as real text, not as a canvas.
 */
@Composable
fun ChartTableSheet(model: ChartTableModel, onDismiss: () -> Unit) {
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = model.title,
        cancelLabel = stringResource(R.string.vitals_provenance_close),
        onCancel = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = OhTheme.dims.tableSheetMax)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                model.columns.forEachIndexed { index, column ->
                    Text(
                        text = column,
                        style = OhTheme.type.caption,
                        color = OhTheme.colors.label2,
                        modifier = Modifier.weight(if (index == 0) 2f else 1f),
                    )
                }
            }
            Spacer(Modifier.height(OhTheme.space.s2))
            model.rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            contentDescription = row.joinToString(", ")
                        },
                ) {
                    row.forEachIndexed { index, cell ->
                        Text(
                            text = cell,
                            style = OhTheme.type.footnote.tabular(),
                            color = OhTheme.colors.label,
                            textAlign = if (index == 0) TextAlign.Start else TextAlign.End,
                            modifier = Modifier.weight(if (index == 0) 2f else 1f),
                        )
                    }
                }
                Spacer(Modifier.height(OhTheme.space.s2))
            }
            model.citation?.let {
                Spacer(Modifier.height(OhTheme.space.s3))
                Text(
                    text = it,
                    style = OhTheme.type.caption,
                    color = OhTheme.colors.label2,
                )
            }
        }
    }
}
