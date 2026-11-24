package app.openhealth.data.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** The value types a column can hold in an export. */
enum class ExportType { TEXT, LONG, DOUBLE, BOOLEAN }

data class ExportColumn(val name: String, val type: ExportType)

data class ExportTable(val name: String, val columns: List<ExportColumn>)

/**
 * Every stored table, with the columns and types its dump and restore use. The order is
 * also the restore order: parents before children, layers one to five. The timeline is
 * a view and is not exported; it is rebuilt from its tables.
 */
object ExportTables {
    private fun text(name: String) = ExportColumn(name, ExportType.TEXT)
    private fun long(name: String) = ExportColumn(name, ExportType.LONG)
    private fun double(name: String) = ExportColumn(name, ExportType.DOUBLE)
    private fun bool(name: String) = ExportColumn(name, ExportType.BOOLEAN)

    val all: List<ExportTable> = listOf(
        ExportTable(
            "source",
            listOf(
                text("id"), text("kind"), text("label"), text("family"), text("device"),
                text("priorities"), long("expected_cadence_seconds"), text("reliability_note"),
                long("last_sync"), text("permissions"), long("created_at"), long("updated_at"),
            ),
        ),
        ExportTable(
            "profile",
            listOf(
                text("id"), text("name"), text("date_of_birth"), text("sex_at_birth"),
                text("gender"), double("height_cm"), double("weight_kg"), text("units"),
                text("zone_id"), text("language"), text("goal"), text("activity_level"),
                text("training_experience"), bool("weight_neutral"), long("intake_tier"),
                long("created_at"), long("updated_at"), text("source_id"), long("revision"),
            ),
        ),
        ExportTable(
            "exercise",
            listOf(
                text("id"), text("name"), text("primary_muscles"), text("secondary_muscles"),
                text("equipment"), text("instructions"), text("level"), text("mechanic"),
                text("force"), text("met_activity"),
                bool("is_custom"), text("source_id"), long("created_at"), long("updated_at"),
                long("revision"),
            ),
        ),
        ExportTable(
            "routine",
            listOf(
                text("id"), text("name"), text("folder"), text("notes"), long("archived_at"),
                long("created_at"), long("updated_at"), text("source_id"), long("revision"),
                long("weekday"), long("rotation_index"), bool("favourite"),
                text("progression"), long("target_rest_seconds"),
            ),
        ),
        ExportTable(
            "routine_exercise",
            listOf(
                text("id"),
                text("routine_id"),
                text("exercise_id"),
                long("position"),
                long("target_sets"),
                long("target_reps"),
                long("target_reps_high"),
                double("target_weight_kg"),
                text("notes"),
                double("target_rir"), long("rest_seconds"), text("progression"),
                long("group_index"),
            ),
        ),
        ExportTable(
            "programme",
            listOf(
                text("id"), text("name"), long("weeks"), text("notes"), long("archived_at"),
                long("created_at"), long("updated_at"), text("source_id"), long("revision"),
            ),
        ),
        ExportTable(
            "programme_routine",
            listOf(
                text("id"),
                text("programme_id"),
                text("routine_id"),
                long("week"),
                long("position"),
            ),
        ),
        ExportTable(
            "session",
            listOf(
                text("id"), long("started_at"), long("ended_at"), text("local_date"),
                text("zone_id"), long("happened_at"), long("recorded_at"), text("routine_id"),
                text("programme_id"), text("notes"), text("source_id"),
                text("idempotency_key"), text("observation_id"), text("raw_record_id"),
                text("device"), text("app_version"), long("revision"), long("updated_at"),
                text("title"), text("feeling"), double("perceived_effort"),
            ),
        ),
        ExportTable(
            "session_exercise",
            listOf(
                text("id"),
                text("session_id"),
                text("exercise_id"),
                long("position"),
                text("notes"),
                long("group_index"),
                long("target_sets"),
                long("target_reps"),
                long("target_reps_high"),
                double("target_weight_kg"),
                text("progression"),
                long("rest_seconds"),
            ),
        ),
        ExportTable(
            "work_set",
            listOf(
                text("id"), text("session_id"), text("session_exercise_id"),
                text("exercise_id"), long("position"), double("weight_kg"), long("reps"),
                double("rir"), double("rpe"), bool("is_warmup"), bool("is_backoff"),
                bool("is_failure"), bool("is_bodyweight"), text("tempo"),
                long("rest_seconds"), long("completed_at"), long("happened_at"),
                long("recorded_at"), text("source_id"), text("idempotency_key"),
                text("observation_id"), text("raw_record_id"), text("device"),
                text("app_version"), long("revision"), bool("is_drop"), bool("is_assisted"),
                double("assistance_kg"), double("band_kg"), text("notes"),
                double("original_weight_kg"), long("original_reps"), text("edit_reason"),
                long("edited_at"), bool("user_edited"),
            ),
        ),
        ExportTable(
            "food",
            listOf(
                text("id"), text("name"), text("brand"), text("barcode"),
                text("source_kind"), text("source_ref"), text("category"),
                text("food_type"), double("energy_kcal_per_100g"),
                double("protein_g_per_100g"), double("carbs_g_per_100g"),
                double("fat_g_per_100g"), double("fibre_g_per_100g"),
                text("nutrients_json"), text("serving_json"), double("default_serving_g"),
                text("default_serving_label"), text("allergens_json"),
                text("ingredients_text"), text("analysis_json"), bool("verified"),
                long("created_at"), long("updated_at"), long("revision"),
            ),
        ),
        ExportTable(
            "meal",
            listOf(
                text("id"),
                text("name"),
                text("kind"),
                text("local_date"),
                text("zone_id"),
                long("happened_at"),
                long("recorded_at"),
                text("source_id"),
                text("idempotency_key"),
                text("notes"),
                long("created_at"),
                long("updated_at"),
                long("revision"),
            ),
        ),
        ExportTable(
            "entry",
            listOf(
                text("id"), text("meal_id"), text("food_id"), text("local_date"),
                text("zone_id"), long("happened_at"), long("recorded_at"),
                double("quantity_g"), text("serving_label"), double("serving_g"),
                double("servings"), text("food_name"), text("brand"),
                double("energy_kcal"), double("protein_g"),
                double("carbs_g"), double("fat_g"), double("fibre_g"),
                text("micros_json"), text("source_kind"),
                text(
                    "source_id",
                ),
                bool("user_verified"), bool("user_edited"), text("observation_id"),
                text("raw_record_id"), text("idempotency_key"), text("device"),
                text("app_version"), long("revision"),
            ),
        ),
        ExportTable(
            "recipe_item",
            listOf(
                text("id"), text("recipe_food_id"), text("food_id"), long("position"),
                double("grams"), text("food_name"),
            ),
        ),
        ExportTable(
            "meal_template",
            listOf(
                text("id"), text("name"), text("kind"), long("created_at"),
                long("updated_at"), long("revision"),
            ),
        ),
        ExportTable(
            "meal_template_item",
            listOf(
                text("id"), text("template_id"), text("food_id"), long("position"),
                double("grams"), text("food_name"),
            ),
        ),
        ExportTable(
            "water_container",
            listOf(
                text("id"), text("label"), double("millilitres"), long("position"),
                bool("is_default"),
            ),
        ),
        ExportTable(
            "nutrition_target",
            listOf(
                text("id"), text("effective_from"), double("kcal"), double("protein_g"),
                double("carbs_g"), double("fat_g"), double("fibre_g"), double("water_ml"),
                long("created_at"), long("updated_at"), long("revision"),
            ),
        ),
        ExportTable(
            "fast_window",
            listOf(
                text("id"), long("start_at"), double("target_hours"), long("ended_at"),
                text("local_date"), text("zone_id"), long("recorded_at"),
                long("created_at"), long("updated_at"), long("revision"),
            ),
        ),
        ExportTable(
            "measurement",
            listOf(
                text("id"), text("type"), double("value"), text("unit"),
                text("local_date"), text("zone_id"), long("happened_at"),
                long("recorded_at"), text("source_id"), text("method"), text("notes"),
                bool("excluded_from_trends"), text("observation_id"), text("raw_record_id"),
                text("idempotency_key"), text("device"), text("app_version"), long("revision"),
            ),
        ),
        ExportTable(
            "vital",
            listOf(
                text("id"), text("type"), double("value"), text("unit"),
                text("local_date"), text("zone_id"), long("happened_at"),
                long("recorded_at"), text("source_id"), text("method"), text("context"),
                text("note"), bool("excluded_from_trends"), text("observation_id"),
                text("raw_record_id"), text("idempotency_key"), text("device"),
                text("app_version"), long("revision"),
            ),
        ),
        ExportTable(
            "raw_record",
            listOf(
                text("id"), text("source_id"), text("source_kind"), text("device"),
                text("app_version"), long("received_at"), long("recorded_at"),
                long("happened_at"), text("payload_hash"), text("payload_ref"),
                text("payload"), text("idempotency_key"), long("schema_version"),
                long("compacted_at"),
            ),
        ),
        ExportTable(
            "observation",
            listOf(
                text("id"), text("type"), double("value"), text("unit"),
                long("start_at"), long("end_at"), text("local_date"), text("zone_id"),
                long("happened_at"), long("recorded_at"), text("source_id"), text("device"),
                text("app_version"), text("method"), text("context"), double("confidence"),
                text("quality"), bool("user_verified"), bool("user_edited"),
                double("original_value"), text("original_unit"), text("edit_reason"),
                long(
                    "edited_at",
                ),
                text("group_key"), bool("excluded_from_trends"), text("idempotency_key"),
                text("raw_record_id"),
            ),
        ),
        ExportTable(
            "daily_rollup",
            listOf(
                text("local_date"), text("type"), double("value"), text("unit"),
                double("secondary_value"), text("source_id"), long("contributing_sources"),
                text("dedup_reason"), long("watermark"), long("computed_at"),
                text("detail"),
            ),
        ),
        ExportTable(
            "note",
            listOf(
                text("id"), text("body"), text("local_date"), text("zone_id"),
                long("happened_at"), long("recorded_at"), long("edited_at"),
                text("source_id"), text("raw_record_id"), text("idempotency_key"),
                text("device"), text("app_version"), long("revision"),
            ),
        ),
        ExportTable(
            "settings",
            listOf(
                text("key"),
                text("value"),
                long("updated_at"),
                long("schema_version"),
            ),
        ),
        ExportTable(
            "tombstone",
            listOf(
                text("id"),
                text("entity_table"),
                text("entity_id"),
                long("deleted_at"),
                long("purge_after"),
                text("payload"),
                long("restored_at"),
                text("reason"),
            ),
        ),
    )

    fun byName(name: String): ExportTable? = all.firstOrNull { it.name == name }
}

@Serializable
data class ExportTableData(val table: String, val rows: List<Map<String, JsonElement>>)

/**
 * A versioned snapshot of every layer, including the raw records (`pr.md` §6.29: the
 * acceptance test is rebuilding the account on a clean install from the export alone).
 */
@Serializable
data class ExportBundle(
    val format: String,
    val version: Int,
    val schemaVersion: Long,
    val exportedAt: String,
    val tables: List<ExportTableData>,
) {
    companion object {
        const val FORMAT = "openhealth-export"
        const val VERSION = 1
    }
}

data class RestoreReport(val tables: Int, val rows: Long, val schemaVersion: Long)
