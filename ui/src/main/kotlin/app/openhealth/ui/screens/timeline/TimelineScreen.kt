package app.openhealth.ui.screens.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.openhealth.ui.R
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhChipStrip
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.icons.OhModuleIcons
import app.openhealth.ui.icons.OhObjectIcons
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.screens.common.observedLocale
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * The global timeline of `pr.md` §6.27 and `DESIGN.md` §14.2: grouped by local day,
 * newest first, filterable, and every row opens its own provenance in one tap. Nothing
 * the app stores may be missing from it.
 */
@Composable
fun TimelineScreen(
    model: TimelineModel,
    onBack: () -> Unit,
    onFilter: (TimelineFilter) -> Unit,
    onDelete: (TimelineRowModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OhTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = OhTheme.space.s6),
    ) {
        OhPageHeader(title = stringResource(R.string.timeline_title), onBack = onBack)
        OhChipStrip {
            TimelineFilter.entries.forEach { filter ->
                OhChip(
                    text = stringResource(filter.labelRes()),
                    selected = model.filter == filter,
                    onClick = { onFilter(filter) },
                    neutralSelected = true,
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s5))
        if (model.days.isEmpty()) {
            OhEmptyState(
                title = stringResource(R.string.timeline_empty_title),
                body = stringResource(R.string.timeline_empty_body),
            )
            return@Column
        }
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            var selected by remember { mutableStateOf<TimelineRowModel?>(null) }
            model.days.forEach { day ->
                DayHeader(day.date)
                OhCard(modifier = Modifier.padding(bottom = OhTheme.space.s5)) {
                    day.rows.forEachIndexed { index, row ->
                        TimelineRow(
                            row = row,
                            showDivider = index > 0,
                            onClick = { selected = row },
                        )
                    }
                }
            }
            Spacer(Modifier.height(OhTheme.space.s8))
            selected?.let { row ->
                ProvenanceSheet(row = row, onDismiss = { selected = null }, onDelete = {
                    selected = null
                    onDelete(row)
                })
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    Text(
        text = dayLabel(date),
        style = OhTheme.type.footnote.copy(fontWeight = OhWeight.semibold),
        color = OhTheme.colors.label2,
        modifier = Modifier
            .padding(top = OhTheme.space.s7, bottom = OhTheme.space.s1)
            .semantics { heading() },
    )
}

@Composable
private fun TimelineRow(row: TimelineRowModel, showDivider: Boolean, onClick: () -> Unit) {
    OhRow(
        title = row.title,
        subtitle = row.detail,
        value = row.valueText,
        leadingIcon = row.kind.glyph(),
        leadingTint = row.kind.tint(),
        showDivider = showDivider,
        showChevron = true,
        onClick = onClick,
    )
}

@Composable
private fun ProvenanceSheet(row: TimelineRowModel, onDismiss: () -> Unit, onDelete: () -> Unit) {
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.timeline_provenance_title),
        cancelLabel = stringResource(R.string.timeline_close),
        onCancel = onDismiss,
    ) {
        Text(
            text = row.title,
            style = OhTheme.type.body.copy(fontWeight = OhWeight.medium),
            color = OhTheme.colors.label,
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(OhTheme.radius.card))
                .background(OhTheme.colors.surface2),
        ) {
            ProvenanceLine(
                label = stringResource(R.string.timeline_source),
                value = row.provenance.sourceLabel,
            )
            ProvenanceLine(
                label = stringResource(R.string.timeline_happened),
                value = instantLabel(row.provenance.happenedAt),
            )
            ProvenanceLine(
                label = stringResource(R.string.timeline_recorded),
                value = instantLabel(row.provenance.recordedAt),
            )
            row.provenance.method?.let {
                ProvenanceLine(label = stringResource(R.string.timeline_method), value = it)
            }
            row.provenance.device?.let {
                ProvenanceLine(label = stringResource(R.string.timeline_device), value = it)
            }
            ProvenanceLine(
                label = stringResource(R.string.timeline_edited),
                value = if (row.provenance.edited) {
                    stringResource(R.string.timeline_yes)
                } else {
                    stringResource(R.string.timeline_no)
                },
            )
        }
        if (row.deletable) {
            Spacer(Modifier.height(OhTheme.space.s7))
            Text(
                text = stringResource(R.string.timeline_delete_note),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
            Spacer(Modifier.height(OhTheme.space.s2))
            OhRow(
                title = stringResource(R.string.timeline_delete_action),
                leadingIcon = OhIcons.delete,
                destructive = true,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun ProvenanceLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OhTheme.space.s5, vertical = OhTheme.space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label,
        )
    }
}

@Composable
private fun dayLabel(date: LocalDate): String {
    val zone = TimeZone.currentSystemDefault()
    val today = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        .toLocalDateTime(zone).date
    return when (date) {
        today -> stringResource(R.string.timeline_today)
        today.minusDays(1) -> stringResource(R.string.timeline_yesterday)
        else -> "${date.dayOfMonth} ${date.month.name.lowercase().take(MONTH_ABBREVIATION)} " +
            date.year
    }
}

@Composable
private fun instantLabel(instant: Instant): String {
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return HOUR_FORMAT.format(observedLocale(), local.hour, local.minute)
}

private const val MONTH_ABBREVIATION = 3
private const val HOUR_FORMAT = "%02d:%02d"

private fun LocalDate.minusDays(days: Int): LocalDate =
    this + kotlinx.datetime.DatePeriod(days = -days)

private fun TimelineFilter.labelRes(): Int = when (this) {
    TimelineFilter.ALL -> R.string.timeline_filter_all
    TimelineFilter.NOTES -> R.string.timeline_filter_notes
    TimelineFilter.BODY -> R.string.timeline_filter_body
    TimelineFilter.NUTRITION -> R.string.timeline_filter_nutrition
}

private fun TimelineKind.glyph(): ImageVector = when (this) {
    TimelineKind.NOTE -> OhIcons.edit
    TimelineKind.WATER -> OhObjectIcons.water
    TimelineKind.WEIGHT -> OhObjectIcons.scale
    TimelineKind.MEASUREMENT -> OhObjectIcons.scale
    TimelineKind.VITAL -> OhModuleIcons.heart
    TimelineKind.MEAL, TimelineKind.NUTRITION -> OhObjectIcons.plate
    TimelineKind.SESSION -> OhObjectIcons.barbell
    TimelineKind.OBSERVATION -> OhIcons.stats
    TimelineKind.OTHER -> OhIcons.info
}

@Composable
private fun TimelineKind.tint() = when (this) {
    TimelineKind.NOTE -> OhTheme.colors.surface3
    TimelineKind.WATER -> OhTheme.colors.blue
    TimelineKind.WEIGHT, TimelineKind.MEASUREMENT -> OhTheme.colors.acc
    TimelineKind.VITAL -> OhTheme.colors.red
    TimelineKind.MEAL, TimelineKind.NUTRITION -> OhTheme.colors.orange
    TimelineKind.SESSION -> OhTheme.colors.acc
    TimelineKind.OBSERVATION -> OhTheme.colors.teal
    TimelineKind.OTHER -> OhTheme.colors.grey
}
