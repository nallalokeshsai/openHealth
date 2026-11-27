package app.openhealth.data.sync

/**
 * The entities that cross the wire, the table each one lives in and its key.
 *
 * The list is the same one the merge registry names (`SyncMergePolicyRegistry.default`).
 * Adding a row here without a policy (or the reverse) is caught by a test. Derived rows
 * (`daily_rollup`), raw payloads and local Trash tombstones are never here.
 *
 * [rank] orders an applied page: a row is written only after the rows it references, and
 * deleted before them, so foreign keys hold during a sync.
 */
data class SyncTable(
    val entity: String,
    val table: String,
    val keys: List<String>,
    val rank: Int,
    /** Columns a value must carry to keep its provenance (`pr.md` §7.2). */
    val required: List<String> = emptyList(),
)

@Suppress("MagicNumber") // ranks are an ordering scale, not values
object SyncTables {
    val all: List<SyncTable> = listOf(
        SyncTable("source", "source", listOf("id"), 0),
        SyncTable("exercise", "exercise", listOf("id"), 10),
        SyncTable("food", "food", listOf("id"), 10),
        SyncTable("profile", "profile", listOf("id"), 10),
        SyncTable("settings", "settings", listOf("key"), 10),
        SyncTable("meal", "meal", listOf("id"), 20),
        SyncTable("meal_template", "meal_template", listOf("id"), 20),
        SyncTable("water_container", "water_container", listOf("id"), 20),
        SyncTable("nutrition_target", "nutrition_target", listOf("id"), 20),
        SyncTable("fast_window", "fast_window", listOf("id"), 20),
        SyncTable("routine", "routine", listOf("id"), 30),
        SyncTable("programme", "programme", listOf("id"), 30),
        SyncTable(
            "observation",
            "observation",
            listOf("id"),
            40,
            listOf("source_id", "recorded_at", "local_date"),
        ),
        SyncTable(
            "measurement",
            "measurement",
            listOf("id"),
            50,
            listOf("source_id", "recorded_at", "local_date"),
        ),
        SyncTable(
            "vital",
            "vital",
            listOf("id"),
            50,
            listOf("source_id", "recorded_at", "local_date"),
        ),
        SyncTable(
            "note",
            "note",
            listOf("id"),
            50,
            listOf("source_id", "recorded_at", "local_date"),
        ),
        SyncTable(
            "medication",
            "medication",
            listOf("id"),
            50,
            listOf("source_id", "recorded_at", "local_date"),
        ),
        SyncTable("symptom", "symptom", listOf("id"), 50),
        SyncTable(
            "symptom_episode",
            "symptom_episode",
            listOf("id"),
            60,
            listOf("source_id", "recorded_at", "local_date"),
        ),
        SyncTable(
            "lab_result",
            "lab_result",
            listOf("id"),
            60,
            listOf("source_id", "recorded_at", "local_date"),
        ),
        SyncTable("allergy", "allergy", listOf("id"), 60),
        SyncTable("condition", "condition", listOf("id"), 60),
        SyncTable("vaccination", "vaccination", listOf("id"), 60),
        SyncTable("routine_exercise", "routine_exercise", listOf("id"), 60),
        SyncTable("programme_routine", "programme_routine", listOf("id"), 60),
        SyncTable(
            "session",
            "session",
            listOf("id"),
            70,
            listOf("source_id", "recorded_at", "local_date"),
        ),
        SyncTable("session_exercise", "session_exercise", listOf("id"), 80),
        SyncTable(
            "work_set",
            "work_set",
            listOf("id"),
            90,
            listOf("source_id", "recorded_at", "local_date"),
        ),
        SyncTable(
            "entry",
            "entry",
            listOf("id"),
            90,
            listOf("source_id", "recorded_at", "local_date"),
        ),
        SyncTable("recipe_item", "recipe_item", listOf("id"), 90),
        SyncTable("meal_template_item", "meal_template_item", listOf("id"), 90),
        SyncTable(
            "medication_dose",
            "medication_dose",
            listOf("id"),
            90,
            listOf("source_id", "recorded_at", "local_date"),
        ),
        SyncTable(
            "activity",
            "activity",
            listOf("id"),
            90,
            listOf("source_id", "recorded_at", "local_date"),
        ),
        SyncTable("activity_point", "activity_point", listOf("activity_id", "position"), 100),
    )

    val byEntity: Map<String, SyncTable> = all.associateBy(SyncTable::entity)

    fun of(entity: String): SyncTable? = byEntity[entity]

    /** Rows this device must never push even when a repository writes them. */
    val neverSynced: Set<String> = setOf(
        "daily_rollup",
        "raw_record",
        "tombstone",
        "body_photo",
        // The coach's conversation and the MCP tokens/audit belong to this device
        // (`docs/adr/0020`); a provider key or a bearer token never crosses the wire.
        "coach_message",
        "coach_proposal",
        "coach_action",
        "coach_snapshot",
        "mcp_token",
        "mcp_access",
    )
}
