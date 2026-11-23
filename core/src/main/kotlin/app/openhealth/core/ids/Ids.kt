package app.openhealth.core.ids

/**
 * Stable identifiers. Every user-created object carries a UUID (`pr.md` §7.8); the
 * value classes stop a `SessionId` and a `SetId` from being swapped at a call site.
 *
 * They are strings on the wire and in SQLite, so the storage and sync layers can read
 * them without knowing the Kotlin type.
 */
@JvmInline
value class RawRecordId(val value: String) {
    init {
        require(value.isNotBlank()) { "RawRecordId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class ObservationId(val value: String) {
    init {
        require(value.isNotBlank()) { "ObservationId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class SourceId(val value: String) {
    init {
        require(value.isNotBlank()) { "SourceId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class ProfileId(val value: String) {
    init {
        require(value.isNotBlank()) { "ProfileId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class ExerciseId(val value: String) {
    init {
        require(value.isNotBlank()) { "ExerciseId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class RoutineId(val value: String) {
    init {
        require(value.isNotBlank()) { "RoutineId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class ProgrammeId(val value: String) {
    init {
        require(value.isNotBlank()) { "ProgrammeId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class SessionId(val value: String) {
    init {
        require(value.isNotBlank()) { "SessionId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class SetId(val value: String) {
    init {
        require(value.isNotBlank()) { "SetId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class FoodId(val value: String) {
    init {
        require(value.isNotBlank()) { "FoodId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class MealId(val value: String) {
    init {
        require(value.isNotBlank()) { "MealId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class EntryId(val value: String) {
    init {
        require(value.isNotBlank()) { "EntryId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class MeasurementId(val value: String) {
    init {
        require(value.isNotBlank()) { "MeasurementId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class VitalId(val value: String) {
    init {
        require(value.isNotBlank()) { "VitalId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class TombstoneId(val value: String) {
    init {
        require(value.isNotBlank()) { "TombstoneId must not be blank" }
    }

    override fun toString(): String = value
}
