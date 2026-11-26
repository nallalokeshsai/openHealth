package app.openhealth.ui.screens.clinical

/** The seven clinical records the hub can show (`pr.md` §6.9). */
enum class ClinicalSectionKind {
    MEDICATIONS,
    SYMPTOMS,
    LABS,
    ALLERGIES,
    CONDITIONS,
    VACCINATIONS,
}

/** What an editor is editing. A dose and an episode are events, not definitions. */
enum class ClinicalKind {
    MEDICATION,
    DOSE,
    SYMPTOM,
    EPISODE,
    LAB,
    ALLERGY,
    CONDITION,
    VACCINATION,
}

data class ClinicalRowModel(
    val id: String,
    val kind: ClinicalKind,
    val title: String,
    val subtitle: String,
    val value: String?,
    val deletable: Boolean,
)

data class ClinicalSectionModel(
    val kind: ClinicalSectionKind,
    val title: String,
    val addLabel: String,
    val emptyBody: String,
    val rows: List<ClinicalRowModel>,
)

/**
 * The clinical hub. A section with no rows still shows its empty sentence and its add
 * action: an empty record is a starting point, not a dead end.
 */
data class ClinicalModel(
    val sections: List<ClinicalSectionModel>,
    val countLabel: String,
    val note: String,
)

/** The field kinds the one generic editor renders. */
enum class ClinicalFieldType { TEXT, DECIMAL, INTEGER, DATE, TIME, CHOICE, CHOICE_MULTI }

data class ClinicalFieldModel(
    val key: String,
    val label: String,
    val value: String = "",
    val hint: String = "",
    val type: ClinicalFieldType = ClinicalFieldType.TEXT,
    val options: List<String> = emptyList(),
    val selected: List<String> = emptyList(),
    val required: Boolean = false,
)

/**
 * One editor for every clinical record, keyed by [kind]. It is data, not seven screen
 * variants: the same layout renders a medicine, a lab result and an allergy.
 */
data class ClinicalEditorModel(
    val kind: ClinicalKind,
    val title: String,
    val fields: List<ClinicalFieldModel>,
    val saveLabel: String,
    val deleteLabel: String? = null,
    val note: String? = null,
    val error: String? = null,
    /** A saved medicine offers Taken and Skipped beside the save action. */
    val canLogDose: Boolean = false,
)

/** A value the person typed into the editor, keyed by [ClinicalFieldModel.key]. */
typealias ClinicalDraft = Map<String, String>
