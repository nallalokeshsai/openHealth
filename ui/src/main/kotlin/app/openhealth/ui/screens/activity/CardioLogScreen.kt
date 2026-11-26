package app.openhealth.ui.screens.activity

import androidx.compose.foundation.layout.Arrangement
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
import app.openhealth.core.activity.ActivityType
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhChipStrip
import app.openhealth.ui.components.OhSegmentedControl
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.screens.common.OhScreen
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.theme.OhTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * Manual cardio (`pr.md` §6.11 MVP): type, day, duration and whatever else is known.
 * [showEnergy] is false in weight-neutral mode, where a calorie is one of the four
 * values the switch hides (`pr.md` §6.25, `DESIGN.md` §7.6); nothing else changes.
 */
@Composable
fun CardioLogScreen(
    date: LocalDate,
    onBack: () -> Unit,
    onSave: (CardioEntry) -> Unit,
    modifier: Modifier = Modifier,
    showEnergy: Boolean = true,
) {
    var type by rememberSaveable { mutableStateOf(ActivityType.RUN) }
    var dayOffset by rememberSaveable { mutableStateOf(0) }
    var minutes by rememberSaveable { mutableStateOf("30") }
    var distance by rememberSaveable { mutableStateOf("") }
    var averageHeartRate by rememberSaveable { mutableStateOf("") }
    var maxHeartRate by rememberSaveable { mutableStateOf("") }
    var energy by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    val invalid = (minutes.toIntOrNull() ?: 0) <= 0
    OhScreen(modifier = modifier) {
        OhPageHeader(title = stringResource(R.string.cardio_title), onBack = onBack)
        OhSectionTitle(stringResource(R.string.cardio_type))
        OhChipStrip {
            ActivityType.entries.filter { it != ActivityType.YOGA }.forEach { option ->
                OhChip(
                    text = typeLabel(option),
                    selected = type == option,
                    onClick = { type = option },
                )
            }
        }
        OhSectionTitle(stringResource(R.string.cardio_date))
        OhSegmentedControl(
            options = listOf(
                stringResource(R.string.cardio_today),
                stringResource(R.string.cardio_yesterday),
            ),
            selectedIndex = dayOffset,
            onSelect = { dayOffset = it },
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        OhTextField(
            value = minutes,
            onValueChange = { minutes = it.filter(Char::isDigit) },
            placeholder = stringResource(R.string.cardio_minutes),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            error = if (invalid) stringResource(R.string.cardio_required) else null,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhTextField(
            value = distance,
            onValueChange = { distance = it.filter { char -> char.isDigit() || char == '.' } },
            placeholder = stringResource(R.string.cardio_distance),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Text(
            text = stringResource(R.string.cardio_distance_optional),
            style = OhTheme.type.caption,
            color = OhTheme.colors.label3,
            modifier = Modifier.padding(top = OhTheme.space.s1),
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhTextField(
            value = averageHeartRate,
            onValueChange = { averageHeartRate = it.filter(Char::isDigit) },
            placeholder = stringResource(R.string.cardio_average_heart_rate),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhTextField(
            value = maxHeartRate,
            onValueChange = { maxHeartRate = it.filter(Char::isDigit) },
            placeholder = stringResource(R.string.cardio_max_heart_rate),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        if (showEnergy) {
            Spacer(Modifier.height(OhTheme.space.s3))
            OhTextField(
                value = energy,
                onValueChange = { energy = it.filter(Char::isDigit) },
                placeholder = stringResource(R.string.cardio_energy),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        OhTextField(
            value = notes,
            onValueChange = { notes = it },
            placeholder = stringResource(R.string.cardio_notes),
            singleLine = false,
        )
        Spacer(Modifier.height(OhTheme.space.s6))
        OhCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OhButton(
                    text = stringResource(R.string.cardio_save),
                    enabled = !invalid,
                    onClick = {
                        onSave(
                            CardioEntry(
                                type = type,
                                date = if (dayOffset == 0) date else date.minusDays(1),
                                minutes = minutes.toIntOrNull() ?: 0,
                                distanceKm = distance.toDoubleOrNull(),
                                averageHeartRate = averageHeartRate.toDoubleOrNull(),
                                maxHeartRate = maxHeartRate.toDoubleOrNull(),
                                energyKcal = energy.toDoubleOrNull()
                                    .takeIf { showEnergy },
                                notes = notes.takeIf { it.isNotBlank() },
                            ),
                        )
                    },
                )
            }
        }
    }
}

data class CardioEntry(
    val type: ActivityType,
    val date: LocalDate,
    val minutes: Int,
    val distanceKm: Double?,
    val averageHeartRate: Double?,
    val maxHeartRate: Double?,
    val energyKcal: Double?,
    val notes: String?,
)

private fun LocalDate.minusDays(days: Long): LocalDate =
    this.minus(kotlinx.datetime.DatePeriod(days = days.toInt()))
