package app.openhealth.ui.screens.train

/** One programme in the list (`pr.md` §6.5): a name, a length and what this week holds. */
data class ProgrammeRow(
    val id: String,
    val name: String,
    val weeks: Int,
    val active: Boolean,
    /** Which week of the programme today falls in, when it is the active one. */
    val currentWeek: Int?,
    /** The routine names of that week, or of week 1 when it is not running. */
    val routinesText: String,
)

/** What the editor saves: the routine ids of each week, in order. */
data class ProgrammeDraft(val id: String?, val name: String, val weeks: List<List<String>>)

data class ProgrammesModel(
    val programmes: List<ProgrammeRow>,
    /** Every routine a week can hold, as id to name. */
    val routines: List<Pair<String, String>>,
    /** The draft being edited, when the sheet is open. */
    val editing: ProgrammeDraft? = null,
)

/** The Train tab's one-line summary of the running programme. */
data class ProgrammeSummary(val name: String, val week: Int, val weeks: Int, val routines: String)
