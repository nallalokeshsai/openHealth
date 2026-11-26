package app.openhealth.data.repo

import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.db.Profile

/** Layer 4 profile rows. One active profile in the MVP; the table already keys by id. */
class ProfileRepository(private val db: OpenHealthDb) {
    private var sync: app.openhealth.data.sync.SyncRepository? = null

    fun recordChangesWith(recorder: app.openhealth.data.sync.SyncRepository?) {
        sync = recorder
    }

    fun upsert(profile: Profile) {
        db.profileQueries.upsertProfile(
            id = profile.id,
            name = profile.name,
            dateOfBirth = profile.date_of_birth,
            sexAtBirth = profile.sex_at_birth,
            gender = profile.gender,
            heightCm = profile.height_cm,
            weightKg = profile.weight_kg,
            units = profile.units,
            zoneId = profile.zone_id,
            language = profile.language,
            goal = profile.goal,
            activityLevel = profile.activity_level,
            trainingExperience = profile.training_experience,
            weightNeutral = profile.weight_neutral,
            intakeTier = profile.intake_tier,
            createdAt = profile.created_at,
            updatedAt = profile.updated_at,
            sourceId = profile.source_id,
            revision = profile.revision,
        )
        sync?.capture("profile", profile.id)
    }

    fun byId(id: String): Profile? = db.profileQueries.profileById(id).executeAsOneOrNull()

    fun active(): Profile? = db.profileQueries.activeProfile().executeAsOneOrNull()

    fun all(): List<Profile> = db.profileQueries.allProfiles().executeAsList()
}
