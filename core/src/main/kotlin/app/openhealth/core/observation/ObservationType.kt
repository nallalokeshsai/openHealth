package app.openhealth.core.observation

import app.openhealth.core.units.Dimension
import app.openhealth.core.units.UcumUnit

/** What a value is about. The set is closed; a later phase adds a type, never a string. */
enum class ObservationCategory { ACTIVITY, VITALS, BODY, NUTRITION, SLEEP, WELLBEING, TRAINING }

/** How a value is shaped in time (`pr.md` §7.2: start and end). */
enum class TemporalShape {
    /** A single moment, e.g. a heart-rate reading. */
    INSTANT,

    /** An interval, e.g. an hour of steps or a sleep stage. */
    INTERVAL,

    /** A total for the local day, e.g. steps. */
    DAILY_TOTAL,

    /** One value per local day, e.g. weight. */
    DAILY_VALUE,
}

/**
 * The canonical observation types of `pr.md` §7.2. Each type has one canonical storage
 * unit; a value arriving in another unit is converted before it is stored.
 */
enum class ObservationType(
    val canonicalUnit: UcumUnit,
    val shape: TemporalShape,
    val category: ObservationCategory,
) {
    STEPS(UcumUnit.STEP, TemporalShape.INTERVAL, ObservationCategory.ACTIVITY),
    DISTANCE(UcumUnit.METRE, TemporalShape.INTERVAL, ObservationCategory.ACTIVITY),
    ACTIVE_CALORIES(UcumUnit.KILOCALORIE, TemporalShape.INTERVAL, ObservationCategory.ACTIVITY),
    ACTIVE_MINUTES(UcumUnit.MINUTE, TemporalShape.INTERVAL, ObservationCategory.ACTIVITY),
    FLIGHTS_CLIMBED(UcumUnit.COUNT, TemporalShape.INTERVAL, ObservationCategory.ACTIVITY),

    HEART_RATE(UcumUnit.PER_MINUTE, TemporalShape.INSTANT, ObservationCategory.VITALS),
    RESTING_HEART_RATE(UcumUnit.PER_MINUTE, TemporalShape.DAILY_VALUE, ObservationCategory.VITALS),
    HEART_RATE_VARIABILITY(UcumUnit.SECOND, TemporalShape.INSTANT, ObservationCategory.VITALS),
    BLOOD_PRESSURE_SYSTOLIC(
        UcumUnit.MILLIMETRE_MERCURY,
        TemporalShape.INSTANT,
        ObservationCategory.VITALS,
    ),
    BLOOD_PRESSURE_DIASTOLIC(
        UcumUnit.MILLIMETRE_MERCURY,
        TemporalShape.INSTANT,
        ObservationCategory.VITALS,
    ),
    BLOOD_GLUCOSE(UcumUnit.MILLIMOL_PER_LITRE, TemporalShape.INSTANT, ObservationCategory.VITALS),
    BODY_TEMPERATURE(UcumUnit.CELSIUS, TemporalShape.INSTANT, ObservationCategory.VITALS),
    OXYGEN_SATURATION(UcumUnit.PERCENT, TemporalShape.INSTANT, ObservationCategory.VITALS),
    RESPIRATORY_RATE(UcumUnit.PER_MINUTE, TemporalShape.INSTANT, ObservationCategory.VITALS),

    WEIGHT(UcumUnit.KILOGRAM, TemporalShape.DAILY_VALUE, ObservationCategory.BODY),
    BODY_FAT_PERCENT(UcumUnit.PERCENT, TemporalShape.DAILY_VALUE, ObservationCategory.BODY),
    LEAN_MASS(UcumUnit.KILOGRAM, TemporalShape.DAILY_VALUE, ObservationCategory.BODY),

    /** Sleep is attributed to the local date it **started**, like every other event. */
    SLEEP_DURATION(UcumUnit.SECOND, TemporalShape.INTERVAL, ObservationCategory.SLEEP),
    SLEEP_DEEP(UcumUnit.SECOND, TemporalShape.INTERVAL, ObservationCategory.SLEEP),
    SLEEP_LIGHT(UcumUnit.SECOND, TemporalShape.INTERVAL, ObservationCategory.SLEEP),
    SLEEP_REM(UcumUnit.SECOND, TemporalShape.INTERVAL, ObservationCategory.SLEEP),
    SLEEP_AWAKE(UcumUnit.SECOND, TemporalShape.INTERVAL, ObservationCategory.SLEEP),
    SLEEP_IN_BED(UcumUnit.SECOND, TemporalShape.INTERVAL, ObservationCategory.SLEEP),

    /** The person's own 1–5 rating of a night; never inferred from the stages. */
    SLEEP_QUALITY(UcumUnit.SCORE, TemporalShape.INSTANT, ObservationCategory.SLEEP),

    NUTRITION_ENERGY(
        UcumUnit.KILOCALORIE,
        TemporalShape.DAILY_TOTAL,
        ObservationCategory.NUTRITION,
    ),
    NUTRITION_PROTEIN(UcumUnit.GRAM, TemporalShape.DAILY_TOTAL, ObservationCategory.NUTRITION),
    NUTRITION_CARBS(UcumUnit.GRAM, TemporalShape.DAILY_TOTAL, ObservationCategory.NUTRITION),
    NUTRITION_FAT(UcumUnit.GRAM, TemporalShape.DAILY_TOTAL, ObservationCategory.NUTRITION),
    NUTRITION_FIBRE(UcumUnit.GRAM, TemporalShape.DAILY_TOTAL, ObservationCategory.NUTRITION),
    WATER(UcumUnit.MILLILITRE, TemporalShape.DAILY_TOTAL, ObservationCategory.NUTRITION),
    CAFFEINE(UcumUnit.MILLIGRAM, TemporalShape.DAILY_TOTAL, ObservationCategory.NUTRITION),

    /** `pr.md` §7.3: training volume, sets and duration, summed from a day's sessions. */
    TRAINING_VOLUME(UcumUnit.KILOGRAM, TemporalShape.DAILY_TOTAL, ObservationCategory.TRAINING),
    TRAINING_SETS(UcumUnit.COUNT, TemporalShape.DAILY_TOTAL, ObservationCategory.TRAINING),
    TRAINING_DURATION(UcumUnit.SECOND, TemporalShape.DAILY_TOTAL, ObservationCategory.TRAINING),

    MOOD(UcumUnit.SCORE, TemporalShape.INSTANT, ObservationCategory.WELLBEING),
    STRESS(UcumUnit.SCORE, TemporalShape.INSTANT, ObservationCategory.WELLBEING),
    ;

    val dimension: Dimension get() = canonicalUnit.dimension
}
