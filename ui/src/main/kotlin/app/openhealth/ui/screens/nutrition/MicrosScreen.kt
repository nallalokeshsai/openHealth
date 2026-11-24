@file:Suppress("MagicNumber")

package app.openhealth.ui.screens.nutrition

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.openhealth.core.nutrition.NutrientGroup
import app.openhealth.ui.R
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.screens.common.OhFocusedScreen
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.screens.common.mediumDate
import app.openhealth.ui.screens.common.number
import app.openhealth.ui.theme.OhTheme

/** The micronutrient table (`pr.md` §6.3): every value against a cited daily value. */
@Composable
fun MicrosScreen(model: MicrosModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    OhFocusedScreen(modifier = modifier) {
        OhPageHeader(title = stringResource(R.string.nutrition_micros_title), onBack = onBack)
        Text(
            text = mediumDate(model.date),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s4))
        if (model.entryCount == 0) {
            Text(
                text = stringResource(R.string.nutrition_micros_empty),
                style = OhTheme.type.body,
                color = OhTheme.colors.label2,
            )
        } else {
            val ordered = model.rows.sortedBy { it.nutrient.group.ordinal }
            ordered.groupBy { it.nutrient.group }.forEach { (group, rows) ->
                OhSectionTitle(
                    text = when (group) {
                        NutrientGroup.MINERAL ->
                            stringResource(R.string.nutrition_micros_minerals)

                        NutrientGroup.VITAMIN ->
                            stringResource(R.string.nutrition_micros_vitamins)

                        else -> stringResource(R.string.nutrition_micros_other)
                    },
                )
                rows.forEach { row ->
                    val amount = row.amount
                    val unit = unitLabel(row.nutrient.unit)
                    OhRow(
                        title = nutrientLabel(row.nutrient),
                        value = if (amount == null) {
                            stringResource(R.string.nutrition_micros_missing)
                        } else {
                            "${number(amount, decimalsFor(amount))} $unit"
                        },
                        subtitle = row.percentOfDailyValue?.let {
                            stringResource(
                                R.string.nutrition_micros_percent,
                                number(it * 100, 0),
                            )
                        },
                        showDivider = true,
                    )
                }
                Spacer(Modifier.height(OhTheme.space.s3))
            }
        }
        Spacer(Modifier.height(OhTheme.space.s4))
        Text(
            text = stringResource(R.string.nutrition_micros_note),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        Text(
            text = stringResource(R.string.nutrition_micros_citation, model.citationTitle),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Text(
            text = model.citationUrl,
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label3,
        )
    }
}

private fun decimalsFor(amount: Double): Int = when {
    amount >= 100 -> 0
    amount >= 10 -> 1
    amount >= 1 -> 2
    else -> 3
}
