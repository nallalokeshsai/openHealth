package app.openhealth.ui.screens.activity

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.screens.common.OhProgressLine
import app.openhealth.ui.screens.common.OhScreen
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.theme.OhTheme

private const val PERCENT = 100f

/**
 * Map packs (`Tech-Stack.md` §8): one PMTiles archive downloaded on the person's action,
 * capped, stored app-private and deletable. The map works without one by drawing the
 * route on the neutral grid.
 */
@Composable
fun MapPacksScreen(
    model: MapPacksModel,
    onBack: () -> Unit,
    onDownload: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var url by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    OhScreen(modifier = modifier) {
        OhPageHeader(title = stringResource(R.string.map_packs_title), onBack = onBack)
        Text(
            text = stringResource(R.string.map_packs_note),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        OhTextField(
            value = url,
            onValueChange = { url = it },
            placeholder = stringResource(R.string.map_packs_url),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        OhTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(R.string.map_packs_name),
            singleLine = true,
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        if (model.downloading) {
            OhProgressLine(fraction = model.progress)
            Text(
                text = stringResource(
                    R.string.map_packs_downloading,
                    (model.progress * PERCENT).toInt(),
                ),
                style = OhTheme.type.caption,
                color = OhTheme.colors.label2,
            )
        }
        OhButton(
            text = stringResource(R.string.map_packs_download),
            onClick = { onDownload(url.trim(), name.trim()) },
            enabled = !model.downloading && url.isNotBlank(),
            variant = OhButtonVariant.Secondary,
        )
        Text(
            text = stringResource(R.string.map_packs_limit),
            style = OhTheme.type.caption,
            color = OhTheme.colors.label3,
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
        OhSectionTitle(stringResource(R.string.map_packs_installed))
        if (model.packs.isEmpty()) {
            OhCard {
                Text(
                    text = stringResource(R.string.map_packs_empty),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
            }
        } else {
            OhInsetGroup {
                model.packs.forEachIndexed { index, pack ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OhRow(
                            title = pack.name,
                            subtitle = pack.installedText,
                            value = pack.sizeText,
                            showDivider = index > 0,
                            modifier = Modifier.weight(1f),
                        )
                        OhButton(
                            text = stringResource(R.string.map_packs_delete),
                            onClick = { onDelete(pack.name) },
                            variant = OhButtonVariant.Ghost,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(OhTheme.space.s5))
        Text(
            text = stringResource(R.string.map_packs_attribution),
            style = OhTheme.type.caption2,
            color = OhTheme.colors.label3,
        )
    }
}
