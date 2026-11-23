package app.openhealth.ui.screens.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.openhealth.ui.R
import app.openhealth.ui.icons.OhIcon
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.icons.OhModuleIcons
import app.openhealth.ui.icons.OhObjectIcons
import app.openhealth.ui.screens.today.QuickAction
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight

/**
 * The 84 dp quick-add tile of `DESIGN.md` §14.2: `surface`, radius 14, a 21 dp accent
 * glyph over a 13/500 label. Used on Today and in the global quick-add sheet so the two
 * cannot drift.
 */
@Composable
fun OhQuickTile(action: QuickAction, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OhTheme.colors
    val label = stringResource(quickLabelRes(action))
    Column(
        modifier = modifier
            .heightIn(min = OhTheme.dims.quickTile)
            .clip(RoundedCornerShape(OhTheme.radius.card))
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(OhTheme.space.s5)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OhIcon(
            icon = quickGlyph(action),
            contentDescription = null,
            size = OhTheme.dims.quickTileGlyph,
            tint = colors.accInk,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        Text(
            text = label,
            style = OhTheme.type.footnote.copy(fontWeight = OhWeight.medium),
            color = colors.label,
            textAlign = TextAlign.Center,
        )
    }
}

fun quickLabelRes(action: QuickAction): Int = when (action) {
    QuickAction.NOTE -> R.string.quick_note
    QuickAction.WATER -> R.string.quick_water
    QuickAction.WEIGHT -> R.string.quick_weight
    QuickAction.FOOD -> R.string.quick_food
    QuickAction.SET -> R.string.quick_set
    QuickAction.VITALS -> R.string.quick_vitals
}

fun quickGlyph(action: QuickAction): ImageVector = when (action) {
    QuickAction.NOTE -> OhIcons.edit
    QuickAction.WATER -> OhObjectIcons.water
    QuickAction.WEIGHT -> OhObjectIcons.scale
    QuickAction.FOOD -> OhObjectIcons.plate
    QuickAction.SET -> OhObjectIcons.barbell
    QuickAction.VITALS -> OhModuleIcons.heart
}
