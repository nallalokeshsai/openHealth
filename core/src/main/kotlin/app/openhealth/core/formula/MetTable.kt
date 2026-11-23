package app.openhealth.core.formula

/**
 * A curated slice of the Compendium of Physical Activities (`pr.md` §6.4): the
 * activities the app offers for manual exercise logging. Each row carries the MET value
 * as a cited fact (`CitationKey.COMPENDIUM_OF_PHYSICAL_ACTIVITIES_2024`); the label is
 * ours and lives in the UI string table.
 *
 * A value not in this table is not guessed. The user picks the closest activity, and the
 * explanation sheet names it.
 */
enum class MetGroup {
    CYCLING,
    CONDITIONING,
    WALKING,
    RUNNING,
    WATER,
    SPORTS,
    DAILY_LIVING,
    MOBILITY,
}

enum class MetActivity(val labelKey: String, val met: Double, val group: MetGroup) {
    BICYCLING_LEISURE(labelKey = "met.bicycling.leisure", met = 4.0, group = MetGroup.CYCLING),
    BICYCLING_LIGHT(labelKey = "met.bicycling.light", met = 6.8, group = MetGroup.CYCLING),
    BICYCLING_MODERATE(labelKey = "met.bicycling.moderate", met = 8.0, group = MetGroup.CYCLING),
    BICYCLING_MOUNTAIN(labelKey = "met.bicycling.mountain", met = 8.5, group = MetGroup.CYCLING),
    BICYCLING_STATIONARY_MODERATE(
        labelKey = "met.bicycling.stationary",
        met = 6.8,
        group = MetGroup.CYCLING,
    ),

    STAIR_CLIMBING(labelKey = "met.stair.climbing", met = 8.8, group = MetGroup.CONDITIONING),
    ROWING_STATIONARY_MODERATE(
        labelKey = "met.rowing.stationary",
        met = 7.0,
        group = MetGroup.CONDITIONING,
    ),
    CALISTHENICS_MODERATE(
        labelKey = "met.calisthenics.moderate",
        met = 3.8,
        group = MetGroup.CONDITIONING,
    ),
    WEIGHT_LIFTING_MODERATE(
        labelKey = "met.weightlifting.moderate",
        met = 3.5,
        group = MetGroup.CONDITIONING,
    ),
    RESISTANCE_TRAINING(
        labelKey = "met.resistance.training",
        met = 3.5,
        group = MetGroup.CONDITIONING,
    ),
    CIRCUIT_TRAINING(labelKey = "met.circuit.training", met = 5.8, group = MetGroup.CONDITIONING),
    ELLIPTICAL_MODERATE(
        labelKey = "met.elliptical.moderate",
        met = 5.0,
        group = MetGroup.CONDITIONING,
    ),
    STAIR_CLIMBER(labelKey = "met.stair.climber", met = 9.0, group = MetGroup.CONDITIONING),
    ROPE_JUMPING_MODERATE(labelKey = "met.rope.jumping", met = 11.8, group = MetGroup.CONDITIONING),

    WALKING_2_5_MPH(labelKey = "met.walking.slow", met = 3.0, group = MetGroup.WALKING),
    WALKING_3_MPH(labelKey = "met.walking.moderate", met = 3.3, group = MetGroup.WALKING),
    WALKING_3_5_MPH(labelKey = "met.walking.brisk", met = 4.3, group = MetGroup.WALKING),
    WALKING_4_MPH(labelKey = "met.walking.very_brisk", met = 5.0, group = MetGroup.WALKING),
    HIKING_GENERAL(labelKey = "met.hiking.general", met = 5.3, group = MetGroup.WALKING),
    HIKING_HILLY(labelKey = "met.hiking.hilly", met = 6.0, group = MetGroup.WALKING),

    JOGGING_GENERAL(labelKey = "met.jogging.general", met = 7.0, group = MetGroup.RUNNING),
    RUNNING_5_MPH(labelKey = "met.running.5mph", met = 8.3, group = MetGroup.RUNNING),
    RUNNING_6_MPH(labelKey = "met.running.6mph", met = 9.8, group = MetGroup.RUNNING),
    RUNNING_7_MPH(labelKey = "met.running.7mph", met = 11.0, group = MetGroup.RUNNING),
    RUNNING_8_MPH(labelKey = "met.running.8mph", met = 11.8, group = MetGroup.RUNNING),

    SWIMMING_GENERAL(labelKey = "met.swimming.general", met = 6.0, group = MetGroup.WATER),
    SWIMMING_LAPS_MODERATE(
        labelKey = "met.swimming.laps.moderate",
        met = 5.8,
        group = MetGroup.WATER,
    ),
    SWIMMING_LAPS_VIGOROUS(
        labelKey = "met.swimming.laps.vigorous",
        met = 9.8,
        group = MetGroup.WATER,
    ),
    WATER_AEROBICS(labelKey = "met.water.aerobics", met = 5.5, group = MetGroup.WATER),

    BASKETBALL_GAME(labelKey = "met.basketball.game", met = 8.0, group = MetGroup.SPORTS),
    SOCCER_CASUAL(labelKey = "met.soccer.casual", met = 7.0, group = MetGroup.SPORTS),
    TENNIS_SINGLES(labelKey = "met.tennis.singles", met = 8.0, group = MetGroup.SPORTS),
    GOLF_WALKING(labelKey = "met.golf.walking", met = 4.8, group = MetGroup.SPORTS),
    SKIING_DOWNHILL_MODERATE(labelKey = "met.skiing.downhill", met = 6.0, group = MetGroup.SPORTS),

    YOGA_GENERAL(labelKey = "met.yoga.general", met = 2.5, group = MetGroup.MOBILITY),
    YOGA_HATHA(labelKey = "met.yoga.hatha", met = 2.5, group = MetGroup.MOBILITY),
    PILATES(labelKey = "met.pilates", met = 3.0, group = MetGroup.MOBILITY),
    STRETCHING(labelKey = "met.stretching", met = 2.3, group = MetGroup.MOBILITY),

    COOKING(labelKey = "met.cooking", met = 2.5, group = MetGroup.DAILY_LIVING),
    GARDENING_GENERAL(labelKey = "met.gardening.general", met = 3.8, group = MetGroup.DAILY_LIVING),
    HOUSEWORK_GENERAL(labelKey = "met.housework.general", met = 2.5, group = MetGroup.DAILY_LIVING),
    MOWING_LAWN(labelKey = "met.mowing.lawn", met = 4.5, group = MetGroup.DAILY_LIVING),
}
