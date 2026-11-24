package app.openhealth.ui.screens.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.openhealth.core.nutrition.Nutrient
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonSize
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhKpiBand
import app.openhealth.ui.components.OhKpiFigure
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhFocusedScreen
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.screens.common.number
import app.openhealth.ui.theme.OhTheme

/**
 * The barcode page (`pr.md` §6.3). The camera slot is supplied by `app-android` so the
 * screen itself stays platform-free. Every state has a manual path: permission denied,
 * offline and not found all land on typing the food in.
 */
@Composable
fun BarcodeScanScreen(
    model: BarcodeModel,
    showKcal: Boolean,
    cameraPreview: @Composable (onBarcode: (String) -> Unit) -> Unit,
    onBack: () -> Unit,
    onAcceptDisclosure: () -> Unit,
    onScanAgain: () -> Unit,
    onManual: () -> Unit,
    onLookup: (String) -> Unit,
    onLog: (FoodChoiceModel) -> Unit,
    onCreateFood: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    OhFocusedScreen(modifier = modifier) {
        OhPageHeader(title = stringResource(R.string.nutrition_scan_title), onBack = onBack)
        when (model.state) {
            BarcodeState.DISCLOSURE -> {
                Text(
                    text = stringResource(R.string.nutrition_scan_disclosure_title),
                    style = OhTheme.type.title3,
                    color = OhTheme.colors.label,
                )
                Spacer(Modifier.height(OhTheme.space.s3))
                Text(
                    text = stringResource(R.string.nutrition_scan_disclosure_body),
                    style = OhTheme.type.body,
                    color = OhTheme.colors.label2,
                )
                Spacer(Modifier.height(OhTheme.space.s5))
                OhButton(
                    text = stringResource(R.string.nutrition_scan_disclosure_accept),
                    onClick = onAcceptDisclosure,
                )
                Spacer(Modifier.height(OhTheme.space.s3))
                OhButton(
                    text = stringResource(R.string.nutrition_scan_manual),
                    onClick = onManual,
                    variant = OhButtonVariant.Quiet,
                )
            }

            BarcodeState.SCANNING -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(OhTheme.dims.scanPreview)
                        .clip(RoundedCornerShape(OhTheme.radius.card)),
                ) {
                    cameraPreview { code -> onLookup(code) }
                }
                Spacer(Modifier.height(OhTheme.space.s3))
                Text(
                    text = stringResource(R.string.nutrition_scan_hint),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
                Spacer(Modifier.height(OhTheme.space.s4))
                OhButton(
                    text = stringResource(R.string.nutrition_scan_manual),
                    onClick = onManual,
                    variant = OhButtonVariant.Secondary,
                )
            }

            BarcodeState.LOOKING_UP -> OhEmptyState(
                title = stringResource(R.string.nutrition_scan_looking),
                body = stringResource(R.string.nutrition_scan_hint),
            )

            BarcodeState.FOUND -> {
                val product = model.product
                if (product == null) {
                    OhEmptyState(
                        title = stringResource(R.string.nutrition_scan_not_found),
                        body = stringResource(R.string.nutrition_scan_not_found_body),
                        actionLabel = stringResource(R.string.nutrition_scan_again),
                        onAction = onScanAgain,
                    )
                } else {
                    Text(
                        text = product.name,
                        style = OhTheme.type.title3,
                        color = OhTheme.colors.label,
                    )
                    Text(
                        text = buildString {
                            product.brand?.let { append(it) }
                            if (product.brand != null) append(" · ")
                            append(sourceLabel(product.sourceKey))
                        },
                        style = OhTheme.type.footnote,
                        color = OhTheme.colors.label2,
                    )
                    Spacer(Modifier.height(OhTheme.space.s4))
                    val kcal = product.per100g[Nutrient.ENERGY]
                    val protein = product.per100g[Nutrient.PROTEIN]
                    val carbs = product.per100g[Nutrient.CARBS]
                    val fat = product.per100g[Nutrient.FAT]
                    OhKpiBand(
                        figures = listOf(
                            OhKpiFigure(
                                value = if (showKcal && kcal != null) {
                                    number(kcal)
                                } else {
                                    stringResource(R.string.nutrition_micros_missing)
                                },
                                label = stringResource(R.string.nutrient_energy),
                                unit = if (showKcal && kcal != null) {
                                    stringResource(R.string.unit_kcal)
                                } else {
                                    null
                                },
                            ),
                            OhKpiFigure(
                                value = protein?.let { number(it, 1) }
                                    ?: stringResource(R.string.nutrition_micros_missing),
                                label = stringResource(R.string.nutrition_macro_protein),
                                unit = stringResource(R.string.unit_g),
                            ),
                            OhKpiFigure(
                                value = carbs?.let { number(it, 1) }
                                    ?: stringResource(R.string.nutrition_micros_missing),
                                label = stringResource(R.string.nutrition_macro_carbs),
                                unit = stringResource(R.string.unit_g),
                            ),
                        ),
                    )
                    Spacer(Modifier.height(OhTheme.space.s2))
                    Text(
                        text = stringResource(R.string.nutrition_portion_per_100g),
                        style = OhTheme.type.caption,
                        color = OhTheme.colors.label3,
                    )
                    if (fat != null) {
                        Spacer(Modifier.height(OhTheme.space.s2))
                        Text(
                            text = "${stringResource(R.string.nutrition_macro_fat)} " +
                                stringResource(R.string.nutrition_macro_value, number(fat, 1)),
                            style = OhTheme.type.footnote,
                            color = OhTheme.colors.label2,
                        )
                    }
                    if (product.allergens.isNotEmpty()) {
                        Spacer(Modifier.height(OhTheme.space.s3))
                        Text(
                            text = stringResource(
                                R.string.nutrition_portion_allergen,
                                product.allergens
                                    .map { offAllergenLabel(it) }
                                    .joinToString(", "),
                            ),
                            style = OhTheme.type.footnote,
                            color = OhTheme.colors.orangeInk,
                        )
                    }
                    Spacer(Modifier.height(OhTheme.space.s5))
                    OhButton(
                        text = stringResource(R.string.nutrition_scan_log),
                        onClick = { onLog(product) },
                    )
                    Spacer(Modifier.height(OhTheme.space.s3))
                    OhButton(
                        text = stringResource(R.string.nutrition_scan_again),
                        onClick = onScanAgain,
                        variant = OhButtonVariant.Secondary,
                    )
                    Spacer(Modifier.height(OhTheme.space.s4))
                    Text(
                        text = stringResource(R.string.nutrition_scan_attribution),
                        style = OhTheme.type.caption,
                        color = OhTheme.colors.label3,
                    )
                }
            }

            BarcodeState.NOT_FOUND -> {
                OhEmptyState(
                    title = stringResource(R.string.nutrition_scan_not_found),
                    body = stringResource(R.string.nutrition_scan_not_found_body),
                    actionLabel = stringResource(R.string.nutrition_create_food),
                    onAction = { onCreateFood(model.barcode) },
                )
                Spacer(Modifier.height(OhTheme.space.s4))
                OhButton(
                    text = stringResource(R.string.nutrition_scan_again),
                    onClick = onScanAgain,
                    variant = OhButtonVariant.Secondary,
                )
            }

            BarcodeState.UNAVAILABLE -> {
                OhEmptyState(
                    title = stringResource(R.string.nutrition_scan_unavailable),
                    body = stringResource(R.string.nutrition_scan_unavailable_body),
                    actionLabel = stringResource(R.string.nutrition_create_food),
                    onAction = { onCreateFood(model.barcode) },
                )
                Spacer(Modifier.height(OhTheme.space.s4))
                OhButton(
                    text = stringResource(R.string.nutrition_scan_again),
                    onClick = onScanAgain,
                    variant = OhButtonVariant.Secondary,
                )
            }

            BarcodeState.MANUAL -> {
                var barcode by rememberSaveable { mutableStateOf(model.barcode.orEmpty()) }
                Text(
                    text = stringResource(R.string.nutrition_scan_manual_title),
                    style = OhTheme.type.title3,
                    color = OhTheme.colors.label,
                )
                Spacer(Modifier.height(OhTheme.space.s3))
                OhTextField(
                    value = barcode,
                    onValueChange = { barcode = it.filter(Char::isDigit) },
                    placeholder = stringResource(R.string.nutrition_scan_manual_field),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    contentDescription = stringResource(R.string.nutrition_scan_manual_field),
                )
                Spacer(Modifier.height(OhTheme.space.s3))
                val valid = barcode.length in MIN_BARCODE..MAX_BARCODE
                if (!valid && barcode.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.nutrition_scan_invalid),
                        style = OhTheme.type.footnote,
                        color = OhTheme.colors.orangeInk,
                    )
                }
                Spacer(Modifier.height(OhTheme.space.s3))
                Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s3)) {
                    OhButton(
                        text = stringResource(R.string.nutrition_scan_look_up),
                        onClick = { onLookup(barcode) },
                        enabled = valid,
                    )
                    OhButton(
                        text = stringResource(R.string.nutrition_create_food),
                        onClick = { onCreateFood(barcode.ifBlank { null }) },
                        variant = OhButtonVariant.Secondary,
                        size = OhButtonSize.Small,
                    )
                }
                Spacer(Modifier.height(OhTheme.space.s4))
                Text(
                    text = stringResource(R.string.nutrition_scan_disclosure_body),
                    style = OhTheme.type.caption,
                    color = OhTheme.colors.label3,
                    modifier = Modifier.padding(end = OhTheme.space.s4),
                )
            }
        }
    }
}

private const val MIN_BARCODE = 8
private const val MAX_BARCODE = 14
