package app.openhealth.ui.screens.timeline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonSize
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhChipStrip
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.ohHairline
import app.openhealth.ui.components.ohPressFill
import app.openhealth.ui.components.rememberInteraction
import app.openhealth.ui.icons.OhIcon
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.icons.OhModuleIcons
import app.openhealth.ui.icons.OhObjectIcons
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.screens.common.mediumDate
import app.openhealth.ui.screens.common.recordingDelay
import app.openhealth.ui.screens.common.timeOf
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * The global timeline of `pr.md` §6.27 and `DESIGN.md` §14.2: grouped by local day,
 * newest first, filterable, and every row opens its provenance in one tap. A long press
 * deletes a row with undo; older pages load on request so nothing is invisible.
 */
@Composable
fun TimelineScreen(
    model: TimelineModel,
    onBack: () -> Unit,
    onFilter: (TimelineFilter) -> Unit,
    onDelete: (TimelineRowModel) -> Unit,
    onExclude: (TimelineRowModel) -> Unit,
    onLogNote: () -> Unit,
    onLoadMore: () -> Unit,
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
            val filtered = model.filter != TimelineFilter.ALL
            OhCard {
                OhEmptyState(
                    title = stringResource(
                        if (filtered) {
                            R.string.timeline_empty_filter_title
                        } else {
                            R.string.timeline_empty_title
                        },
                    ),
                    body = stringResource(R.string.timeline_empty_body),
                    glyph = OhIcons.timer,
                    actionLabel = stringResource(
                        if (filtered) {
                            R.string.timeline_clear_filter
                        } else {
                            R.string.timeline_empty_action
                        },
                    ),
                    onAction = if (filtered) {
                        { onFilter(TimelineFilter.ALL) }
                    } else {
                        onLogNote
                    },
                )
            }
            return@Column
        }
        var selected by remember { mutableStateOf<TimelineRowModel?>(null) }
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            model.days.forEach { day ->
                DayHeader(day.date)
                OhCard(modifier = Modifier.padding(bottom = OhTheme.space.s5)) {
                    day.rows.forEachIndexed { index, row ->
                        TimelineRow(
                            row = row,
                            showDivider = index > 0,
                            onClick = { selected = row },
                            onLongClick = { if (row.deletable) onDelete(row) },
                        )
                    }
                }
            }
            if (model.hasMore) {
                Spacer(Modifier.height(OhTheme.space.s5))
                OhButton(
                    text = stringResource(R.string.timeline_show_older),
                    onClick = onLoadMore,
                    variant = OhButtonVariant.Secondary,
                    size = OhButtonSize.Small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(OhTheme.space.s8))
        }
        selected?.let { row ->
            ProvenanceSheet(
                row = row,
                onDismiss = { selected = null },
                onExclude = {
                    selected = null
                    onExclude(row)
                },
                onDelete = {
                    selected = null
                    onDelete(row)
                },
            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineRow(
    row: TimelineRowModel,
    showDivider: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val dims = OhTheme.dims
    val interaction = rememberInteraction()
    val shape = RectangleShape
    val deleteLabel = stringResource(R.string.timeline_delete_action)
    var surface = Modifier
        .fillMaxWidth()
        .heightIn(min = dims.mediaRowMin)
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            role = androidx.compose.ui.semantics.Role.Button,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        .ohPressFill(interaction, shape)
        .semantics(mergeDescendants = true) {
            if (row.deletable) {
                customActions = listOf(
                    androidx.compose.ui.semantics.CustomAccessibilityAction(deleteLabel) {
                        onLongClick()
                        true
                    },
                )
            }
        }
    if (showDivider) {
        surface = surface.ohHairline(
            color = colors.sep,
            insetStart = dims.rowIconInset,
            atBottom = false,
        )
    }
    Row(
        modifier = surface.padding(horizontal = dims.rowPadH, vertical = dims.rowPadV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(dims.iconTile)
                .clip(RoundedCornerShape(OhTheme.radius.sm))
                .background(row.kind.tint()),
            contentAlignment = Alignment.Center,
        ) {
            OhIcon(
                icon = row.kind.glyph(),
                contentDescription = null,
                size = dims.iconButtonGlyph,
                tint = colors.onAcc,
            )
        }
        Spacer(Modifier.width(dims.rowGap))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = type.body,
                color = colors.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.detail,
                style = type.footnote,
                color = colors.label2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            RowTags(row)
        }
        if (row.valueText != null) {
            Spacer(Modifier.width(OhTheme.space.s3))
            Text(
                text = row.valueText.orEmpty(),
                style = type.subhead,
                color = colors.label2,
                maxLines = 1,
            )
        }
    }
}

/**
 * The 12 sp tags of `DESIGN.md` §7.3. `Late` is the "entered late" mark `pr.md` §6.27
 * asks for and uses the same one-minute threshold as the provenance sheet's delay line.
 * `Edited` is drawn neutral rather than `indigo`: at 12 sp on `surface` indigo reaches
 * only 3.36:1 in dark, under the 4.5:1 body-text bar of §11.1 (see the audit note).
 */
@Composable
private fun RowTags(row: TimelineRowModel) {
    val late = row.provenance.recordedAt - row.provenance.happenedAt >= 1.minutes
    val tags = buildList {
        if (row.imported) add(stringResource(R.string.timeline_tag_imported) to false)
        if (late) add(stringResource(R.string.timeline_tag_late) to false)
        if (row.provenance.edited) add(stringResource(R.string.timeline_tag_edited) to false)
        if (row.excluded) add(stringResource(R.string.timeline_tag_excluded) to true)
    }
    if (tags.isEmpty()) return
    Spacer(Modifier.height(OhTheme.space.s0))
    Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s2)) {
        tags.forEach { (label, warning) ->
            Text(
                text = label,
                style = OhTheme.type.caption,
                color = if (warning) OhTheme.colors.orangeInk else OhTheme.colors.label2,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ProvenanceSheet(
    row: TimelineRowModel,
    onDismiss: () -> Unit,
    onExclude: () -> Unit,
    onDelete: () -> Unit,
) {
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
            row.valueText?.let {
                ProvenanceLine(stringResource(R.string.timeline_value), it)
            }
            ProvenanceLine(stringResource(R.string.timeline_source), row.provenance.sourceLabel)
            row.provenance.method?.let {
                ProvenanceLine(stringResource(R.string.timeline_method), methodLabel(it))
            }
            ProvenanceLine(
                stringResource(R.string.timeline_happened),
                instantLabel(row.provenance.happenedAt),
            )
            val delay = recordingDelay(
                row.provenance.happenedAt,
                row.provenance.recordedAt,
            )
            ProvenanceLine(
                label = stringResource(R.string.timeline_recorded),
                value = if (delay != null) {
                    stringResource(
                        R.string.timeline_recorded_value,
                        instantLabel(row.provenance.recordedAt),
                        delay,
                    )
                } else {
                    instantLabel(row.provenance.recordedAt)
                },
            )
            if (row.provenance.localDay.isNotBlank()) {
                ProvenanceLine(
                    label = stringResource(R.string.timeline_local_day),
                    value = stringResource(
                        R.string.timeline_local_day_value,
                        localDayLabel(row.provenance.localDay),
                        zoneLabel(row.provenance.zoneId),
                    ),
                )
            }
            row.provenance.quality?.let {
                ProvenanceLine(stringResource(R.string.timeline_quality), methodLabel(it))
            }
            row.provenance.device?.let {
                ProvenanceLine(stringResource(R.string.timeline_device), it)
            }
            ProvenanceLine(
                label = stringResource(R.string.timeline_edited),
                value = if (row.provenance.edited) {
                    stringResource(R.string.timeline_yes)
                } else {
                    stringResource(R.string.timeline_no)
                },
            )
            ProvenanceLine(
                label = stringResource(R.string.timeline_raw),
                value = if (row.provenance.rawRecord) {
                    stringResource(R.string.timeline_raw_kept)
                } else {
                    stringResource(R.string.timeline_raw_none)
                },
            )
        }
        if (row.excludable) {
            Spacer(Modifier.height(OhTheme.space.s5))
            OhRow(
                title = stringResource(
                    if (row.excluded) R.string.timeline_include else R.string.timeline_exclude,
                ),
                leadingIcon = OhIcons.filter,
                onClick = onExclude,
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
    val stack = androidx.compose.ui.platform.LocalDensity.current.fontScale >=
        OhTheme.dims.largeFontScale
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = OhTheme.dims.rowMin)
            .padding(horizontal = OhTheme.space.s5, vertical = OhTheme.space.s3),
        verticalArrangement = if (stack) Arrangement.Center else Arrangement.SpaceBetween,
    ) {
        if (stack) {
            Text(text = label, style = OhTheme.type.footnote, color = OhTheme.colors.label2)
            Text(text = value, style = OhTheme.type.footnote, color = OhTheme.colors.label)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                    modifier = Modifier.weight(1f),
                )
                Text(text = value, style = OhTheme.type.footnote, color = OhTheme.colors.label)
            }
        }
    }
}

@Composable
private fun localDayLabel(raw: String): String {
    val parsed = runCatching { LocalDate.parse(raw) }.getOrNull()
    return if (parsed != null) mediumDate(parsed) else raw
}

private fun zoneLabel(zoneId: String): String = runCatching {
    java.time.ZoneId.of(zoneId).getDisplayName(
        java.time.format.TextStyle.SHORT,
        java.util.Locale.getDefault(),
    )
}.getOrDefault(zoneId)

@Composable
private fun dayLabel(date: LocalDate): String {
    val today = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    return when (date) {
        today -> stringResource(R.string.timeline_today)
        today.minusDays(1) -> stringResource(R.string.timeline_yesterday)
        else -> mediumDate(date)
    }
}

@Composable
private fun instantLabel(instant: Instant): String =
    timeOf(instant, TimeZone.currentSystemDefault())

private fun methodLabel(raw: String): String = raw.lowercase()
    .replace('_', ' ')
    .replaceFirstChar { it.uppercase() }

private fun LocalDate.minusDays(days: Int): LocalDate =
    this + kotlinx.datetime.DatePeriod(days = -days)

private fun TimelineFilter.labelRes(): Int = when (this) {
    TimelineFilter.ALL -> R.string.timeline_filter_all
    TimelineFilter.NOTES -> R.string.timeline_filter_notes
    TimelineFilter.BODY -> R.string.timeline_filter_body
    TimelineFilter.NUTRITION -> R.string.timeline_filter_nutrition
    TimelineFilter.MANUAL -> R.string.timeline_filter_manual
    TimelineFilter.IMPORTED -> R.string.timeline_filter_imported
}

private fun TimelineKind.glyph(): ImageVector = when (this) {
    TimelineKind.NOTE -> OhIcons.edit
    TimelineKind.WATER -> OhObjectIcons.water
    TimelineKind.WEIGHT -> OhObjectIcons.scale
    TimelineKind.MEASUREMENT -> OhObjectIcons.scale
    TimelineKind.VITAL -> OhModuleIcons.heart
    TimelineKind.MEAL, TimelineKind.NUTRITION, TimelineKind.ENTRY -> OhObjectIcons.plate
    TimelineKind.SESSION, TimelineKind.SET -> OhObjectIcons.barbell
    TimelineKind.OBSERVATION -> OhIcons.stats
    TimelineKind.OTHER -> OhIcons.info
}

@Composable
private fun TimelineKind.tint() = when (this) {
    TimelineKind.NOTE -> OhTheme.colors.blue
    TimelineKind.WATER -> OhTheme.colors.blue
    TimelineKind.WEIGHT, TimelineKind.MEASUREMENT -> OhTheme.colors.acc
    TimelineKind.VITAL -> OhTheme.colors.teal
    TimelineKind.MEAL, TimelineKind.NUTRITION, TimelineKind.ENTRY -> OhTheme.colors.orange
    TimelineKind.SESSION, TimelineKind.SET -> OhTheme.colors.acc
    TimelineKind.OBSERVATION -> OhTheme.colors.teal
    TimelineKind.OTHER -> OhTheme.colors.grey
}
