package app.openhealth.ui.screens.clinical

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonSize
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.icons.OhObjectIcons
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.theme.OhTheme

/**
 * The clinical hub (`pr.md` §6.9; `DESIGN.md` §14.1 recipes). Seven records, each with
 * its own section and its own add action. A value opens its editor; a delete goes to
 * Trash and keeps a tombstone (`pr.md` §6.29).
 */
@Composable
fun ClinicalScreen(
    model: ClinicalModel,
    onBack: () -> Unit,
    onAdd: (ClinicalSectionKind) -> Unit,
    onOpen: (ClinicalRowModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OhTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = OhTheme.space.s6),
    ) {
        OhPageHeader(title = stringResource(R.string.clinical_title), onBack = onBack)
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = OhTheme.space.s8),
        ) {
            Text(
                text = model.countLabel,
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
                modifier = Modifier.padding(bottom = OhTheme.space.s4),
            )
            model.sections.forEach { section ->
                SectionCard(
                    section = section,
                    onAdd = { onAdd(section.kind) },
                    onOpen = onOpen,
                )
                Spacer(Modifier.height(OhTheme.space.s5))
            }
            Text(
                text = model.note,
                style = OhTheme.type.caption,
                color = OhTheme.colors.label2,
            )
        }
    }
}

@Composable
private fun SectionCard(
    section: ClinicalSectionModel,
    onAdd: () -> Unit,
    onOpen: (ClinicalRowModel) -> Unit,
) {
    OhCard {
        Row(
            modifier = Modifier
                .padding(horizontal = OhTheme.space.s4, vertical = OhTheme.space.s3)
                .semantics { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = section.title,
                style = OhTheme.type.title3,
                color = OhTheme.colors.label,
                modifier = Modifier.weight(1f),
            )
            OhButton(
                text = section.addLabel,
                onClick = onAdd,
                variant = OhButtonVariant.Ghost,
                size = OhButtonSize.Small,
            )
        }
        if (section.rows.isEmpty()) {
            Text(
                text = section.emptyBody,
                style = OhTheme.type.body,
                color = OhTheme.colors.label2,
                modifier = Modifier.padding(
                    start = OhTheme.space.s4,
                    end = OhTheme.space.s4,
                    bottom = OhTheme.space.s4,
                ),
            )
        } else {
            OhInsetGroup {
                section.rows.forEachIndexed { index, row ->
                    OhRow(
                        title = row.title,
                        subtitle = row.subtitle,
                        value = row.value,
                        leadingIcon = row.kind.glyph(),
                        showDivider = index != section.rows.lastIndex,
                        onClick = { onOpen(row) },
                    )
                }
            }
            Spacer(Modifier.height(OhTheme.space.s2))
        }
    }
}

private fun ClinicalKind.glyph() = when (this) {
    ClinicalKind.MEDICATION, ClinicalKind.DOSE -> OhObjectIcons.pill
    ClinicalKind.SYMPTOM, ClinicalKind.EPISODE -> OhObjectIcons.cuff
    ClinicalKind.LAB -> OhObjectIcons.flask
    ClinicalKind.ALLERGY -> OhObjectIcons.glucose
    ClinicalKind.CONDITION -> OhObjectIcons.file
    ClinicalKind.VACCINATION -> OhObjectIcons.watch
}

/** The editor is a page, not a sheet: a medicine has more fields than a sheet holds. */
@Composable
fun ClinicalEditorScreen(
    model: ClinicalEditorModel,
    onBack: () -> Unit,
    onSave: (ClinicalDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onLogDose: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // The error copy of the model carries the same fields; keeping the draft keyed on
    // the fields means a validation message does not wipe what the person typed.
    val values = androidx.compose.runtime.remember(model.kind, model.title, model.fields) {
        androidx.compose.runtime.mutableStateMapOf<String, String>().apply {
            model.fields.forEach { field ->
                put(
                    field.key,
                    field.value.ifBlank { field.selected.joinToString(",") },
                )
            }
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OhTheme.colors.bg)
            .padding(horizontal = OhTheme.space.s6),
    ) {
        OhPageHeader(title = model.title, onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            model.fields.forEach { field ->
                Field(field = field, value = values[field.key].orEmpty()) { updated ->
                    values[field.key] = updated
                }
                Spacer(Modifier.height(OhTheme.space.s4))
            }
            model.error?.let { error ->
                Text(
                    text = error,
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.red,
                    modifier = Modifier.padding(bottom = OhTheme.space.s3),
                )
            }
            model.note?.let { note ->
                Text(
                    text = note,
                    style = OhTheme.type.caption,
                    color = OhTheme.colors.label2,
                    modifier = Modifier.padding(bottom = OhTheme.space.s4),
                )
            }
            OhButton(
                text = model.saveLabel,
                onClick = { onSave(values.toMap()) },
            )
            if (model.canLogDose && onLogDose != null) {
                Spacer(Modifier.height(OhTheme.space.s3))
                Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s3)) {
                    OhButton(
                        text = stringResource(R.string.clinical_dose_taken),
                        onClick = { onLogDose(false) },
                        variant = OhButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                    OhButton(
                        text = stringResource(R.string.clinical_dose_skipped),
                        onClick = { onLogDose(true) },
                        variant = OhButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (onDelete != null && model.deleteLabel != null) {
                Spacer(Modifier.height(OhTheme.space.s3))
                OhButton(
                    text = model.deleteLabel,
                    onClick = onDelete,
                    variant = OhButtonVariant.Danger,
                )
            }
            Spacer(Modifier.height(OhTheme.space.s8))
        }
    }
}

@Composable
private fun Field(field: ClinicalFieldModel, value: String, onChange: (String) -> Unit) {
    Column {
        Text(
            text = field.label,
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
            modifier = Modifier.padding(bottom = OhTheme.space.s2),
        )
        when (field.type) {
            ClinicalFieldType.CHOICE, ClinicalFieldType.CHOICE_MULTI -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(OhTheme.space.s2),
                ) {
                    field.options.chunked(CHIPS_PER_ROW).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s2)) {
                            row.forEach { option ->
                                val selected = if (field.type == ClinicalFieldType.CHOICE_MULTI) {
                                    value.split(',')
                                        .map { it.trim() }
                                        .any { it == option }
                                } else {
                                    value == option
                                }
                                app.openhealth.ui.components.OhChip(
                                    text = option,
                                    selected = selected,
                                    onClick = {
                                        if (field.type == ClinicalFieldType.CHOICE) {
                                            onChange(option)
                                        } else {
                                            val current = value.split(',')
                                                .map { it.trim() }
                                                .filter { it.isNotEmpty() }
                                                .toMutableSet()
                                            if (option in current) {
                                                current.remove(option)
                                            } else {
                                                current.add(option)
                                            }
                                            onChange(current.joinToString(","))
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                app.openhealth.ui.components.OhTextField(
                    value = value,
                    onValueChange = onChange,
                    placeholder = field.hint,
                    keyboardOptions = field.type.keyboard(),
                )
            }
        }
    }
}

private fun ClinicalFieldType.keyboard() = when (this) {
    ClinicalFieldType.DECIMAL -> androidx.compose.foundation.text.KeyboardOptions(
        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
    )
    ClinicalFieldType.INTEGER -> androidx.compose.foundation.text.KeyboardOptions(
        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
    )
    else -> androidx.compose.foundation.text.KeyboardOptions.Default
}

private const val CHIPS_PER_ROW = 3
