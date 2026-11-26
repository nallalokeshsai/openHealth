package app.openhealth.ui.screens.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.openhealth.core.activity.ActivityType
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhChipStrip
import app.openhealth.ui.components.OhKpiBand
import app.openhealth.ui.components.OhKpiFigure
import app.openhealth.ui.components.OhMediaRow
import app.openhealth.ui.icons.OhGlyphIcons
import app.openhealth.ui.icons.OhIcon
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.screens.common.OhScreen
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight

/** Activity history (`pr.md` §6.11 MVP): weekly totals, filters and the route list. */
@Composable
fun ActivityListScreen(
    model: ActivityHistoryModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf<ActivityType?>(null) }
    val rows = model.rows.filter { filter == null || it.type == filter }
    OhScreen(modifier = modifier) {
        OhPageHeader(title = stringResource(R.string.activity_title), onBack = onBack)
        if (model.moduleOff) {
            OhCard {
                OhEmptyState(
                    title = stringResource(R.string.activity_module_off),
                    body = stringResource(R.string.activity_import_note),
                )
            }
            return@OhScreen
        }
        OhKpiBand(
            figures = listOf(
                OhKpiFigure(
                    model.weekCount.toString(),
                    stringResource(R.string.activity_week_sessions),
                ),
                OhKpiFigure(
                    model.weekMinutes.toString(),
                    stringResource(R.string.activity_week_minutes),
                ),
                OhKpiFigure(
                    model.weekDistanceText ?: stringResource(R.string.train_not_available),
                    stringResource(R.string.activity_week_distance),
                ),
            ),
            footer = model.note,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhButton(
            text = stringResource(R.string.activity_import),
            onClick = onImport,
            variant = OhButtonVariant.Secondary,
        )
        Text(
            text = stringResource(R.string.activity_import_note),
            style = OhTheme.type.caption,
            color = OhTheme.colors.label2,
            modifier = Modifier.padding(top = OhTheme.space.s1),
        )
        model.message?.let { message ->
            Text(
                text = message,
                style = OhTheme.type.footnote,
                color = OhTheme.colors.orangeInk,
                modifier = Modifier.padding(top = OhTheme.space.s2),
            )
        }
        OhSectionTitle(stringResource(R.string.activity_recent))
        OhChipStrip {
            OhChip(
                text = stringResource(R.string.activity_filter_all),
                selected = filter == null,
                onClick = { filter = null },
            )
            ActivityType.entries.forEach { type ->
                OhChip(
                    text = typeLabel(type),
                    selected = filter == type,
                    onClick = { filter = type },
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        if (rows.isEmpty()) {
            OhCard {
                OhEmptyState(
                    title = stringResource(R.string.activity_empty_title),
                    body = stringResource(R.string.activity_empty_body),
                    glyph = OhGlyphIcons.run,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s2)) {
                rows.forEach { row ->
                    OhMediaRow(
                        title = row.title,
                        subtitle = row.subtitle,
                        thumbnail = {
                            OhIcon(
                                icon = if (row.hasRoute) {
                                    app.openhealth.ui.icons.OhObjectIcons.route
                                } else {
                                    app.openhealth.ui.icons.OhModuleIcons.activity
                                },
                                contentDescription = null,
                                size = OhTheme.space.s8,
                                tint = OhTheme.colors.acc,
                            )
                        },
                        trailing = {
                            Text(
                                text = row.durationText,
                                style = OhTheme.type.subhead.copy(fontWeight = OhWeight.semibold),
                                color = OhTheme.colors.label,
                            )
                        },
                        onClick = { onOpen(row.id) },
                        showChevron = true,
                    )
                }
            }
        }
    }
}

@Composable
internal fun typeLabel(type: ActivityType): String = stringResource(
    when (type) {
        ActivityType.RUN -> R.string.activity_type_run
        ActivityType.WALK -> R.string.activity_type_walk
        ActivityType.HIKE -> R.string.activity_type_hike
        ActivityType.RIDE -> R.string.activity_type_ride
        ActivityType.SWIM -> R.string.activity_type_swim
        ActivityType.ROW -> R.string.activity_type_row
        ActivityType.SKI -> R.string.activity_type_ski
        ActivityType.YOGA -> R.string.activity_type_yoga
        ActivityType.MOBILITY -> R.string.activity_type_mobility
        ActivityType.OTHER -> R.string.activity_type_other
    },
)
