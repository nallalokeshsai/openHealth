// Published thresholds are literal facts; the citation beside each one is what makes it
// meaningful, and moving 3.9 into a constant would hide which range owns it.
@file:Suppress("MagicNumber", "ReturnCount")

package app.openhealth.core.vitals

import app.openhealth.core.citation.CitationKey
import app.openhealth.core.observation.ObservationContext
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.units.UcumUnit

/**
 * A published reference range (`pr.md` §6.9, §12.1; `DESIGN.md` §7.5).
 *
 * A range without a citation is an opinion and does not ship: [CitationKey] is required.
 * [low] or [high] may be null for a one-sided range (oxygen saturation has no upper
 * bound). The app never draws a red edge or calls a value "high" as a verdict; it says
 * whether the reading sits inside or outside this published range.
 */
data class ReferenceRange(
    val type: ObservationType,
    val low: Double?,
    val high: Double?,
    val unit: UcumUnit,
    val citation: CitationKey,
    /** The context the range applies to, or null for any context. */
    val context: ObservationContext? = null,
    /** A short published statement, e.g. "below 135/85". Always paired with the citation. */
    val statement: String,
    /** The population and setting, e.g. "adults, home measurement". */
    val population: String,
    /** True when the published range includes its upper bound ("60-100", "36.2-37.0"). */
    val highInclusive: Boolean = false,
) {
    init {
        require(unit.dimension == type.canonicalUnit.dimension) {
            "${type.name} is in ${type.canonicalUnit.ucum}, the range is in ${unit.ucum}"
        }
        require(low != null || high != null) { "a range needs at least one bound" }
    }

    fun contains(value: Double): Boolean = (low == null || value >= low) &&
        (high == null || value < high || (highInclusive && value == high))

    /** Where a reading sits. Neutral: never a diagnosis, never an alarm. */
    fun position(value: Double): RangePosition = when {
        low != null && value < low -> RangePosition.BELOW
        high != null && (value > high || (!highInclusive && value == high)) ->
            RangePosition.ABOVE
        else -> RangePosition.WITHIN
    }

    /** The numeric bounds in words, for the citation line and the table alternative. */
    fun boundsText(): String = when {
        low != null && high != null -> "${trim(low)}-${trim(high)} ${unitSymbol(unit)}"
        low != null -> "from ${trim(low)} ${unitSymbol(unit)}"
        else -> "below ${trim(high!!)} ${unitSymbol(unit)}"
    }

    private fun trim(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    private fun unitSymbol(unit: UcumUnit): String = when (unit) {
        UcumUnit.MILLIMETRE_MERCURY -> "mmHg"
        UcumUnit.PER_MINUTE -> "bpm"
        UcumUnit.CELSIUS -> "\u00b0C"
        UcumUnit.MILLIMOL_PER_LITRE -> "mmol/L"
        UcumUnit.PERCENT -> "%"
        else -> unit.ucum
    }
}

enum class RangePosition { BELOW, WITHIN, ABOVE }

/**
 * One stop of the clinical range strip (`DESIGN.md` §8.2.1). The four stops are zones,
 * not alarms: no stop is red. A strip is only drawn for a reading whose published
 * boundaries split the scale this way; a one-sided range uses the band instead.
 */
data class RangeBand(
    val label: RangeBandLabel,
    val from: Double?,
    val to: Double?,
    val citation: CitationKey,
)

enum class RangeBandLabel { LOW, NORMAL, RAISED, HIGH }

/** The ranges every vital ships against (`pr.md` §6.9). Every row is cited. */
object VitalRanges {
    private val BLOOD_PRESSURE_SYSTOLIC = ReferenceRange(
        type = ObservationType.BLOOD_PRESSURE_SYSTOLIC,
        low = 90.0,
        high = 135.0,
        unit = UcumUnit.MILLIMETRE_MERCURY,
        citation = CitationKey.ESH_HYPERTENSION_2023,
        statement = "below 135/85",
        population = "adults, home measurement",
    )

    private val BLOOD_PRESSURE_DIASTOLIC = ReferenceRange(
        type = ObservationType.BLOOD_PRESSURE_DIASTOLIC,
        low = 60.0,
        high = 85.0,
        unit = UcumUnit.MILLIMETRE_MERCURY,
        citation = CitationKey.ESH_HYPERTENSION_2023,
        statement = "below 135/85",
        population = "adults, home measurement",
    )

    private val RESTING_HEART_RATE = ReferenceRange(
        type = ObservationType.RESTING_HEART_RATE,
        low = 60.0,
        high = 100.0,
        unit = UcumUnit.PER_MINUTE,
        citation = CitationKey.AHA_RESTING_HEART_RATE,
        statement = "60-100",
        population = "adults at rest",
        highInclusive = true,
    )

    private val OXYGEN_SATURATION = ReferenceRange(
        type = ObservationType.OXYGEN_SATURATION,
        low = 95.0,
        high = null,
        unit = UcumUnit.PERCENT,
        citation = CitationKey.WHO_PULSE_OXIMETRY_2011,
        statement = "95-100",
        population = "healthy adults at sea level",
    )

    private val BODY_TEMPERATURE = ReferenceRange(
        type = ObservationType.BODY_TEMPERATURE,
        low = 35.7,
        high = 37.5,
        unit = UcumUnit.CELSIUS,
        citation = CitationKey.GENEVA_BODY_TEMPERATURE_2019,
        statement = "35.7-37.5",
        population = "adults, any measurement site (mean +/- 2 SD)",
        highInclusive = true,
    )

    private val GLUCOSE_FASTING = ReferenceRange(
        type = ObservationType.BLOOD_GLUCOSE,
        low = 3.9,
        high = 5.6,
        unit = UcumUnit.MILLIMOL_PER_LITRE,
        citation = CitationKey.ADA_GLYCEMIC_TARGETS_2026,
        context = ObservationContext.FASTING,
        statement = "3.9-5.6",
        population = "adults, fasting plasma glucose",
    )

    private val GLUCOSE_POST_MEAL = ReferenceRange(
        type = ObservationType.BLOOD_GLUCOSE,
        low = 3.9,
        high = 7.8,
        unit = UcumUnit.MILLIMOL_PER_LITRE,
        citation = CitationKey.ADA_GLYCEMIC_TARGETS_2026,
        context = ObservationContext.POST_MEAL,
        statement = "below 7.8",
        population = "adults, two-hour 75 g oral glucose test",
    )

    /** The range for a reading, honouring the context a glucose reading carries. */
    fun forReading(type: ObservationType, context: String?): ReferenceRange? = when (type) {
        ObservationType.BLOOD_PRESSURE_SYSTOLIC -> BLOOD_PRESSURE_SYSTOLIC
        ObservationType.BLOOD_PRESSURE_DIASTOLIC -> BLOOD_PRESSURE_DIASTOLIC
        ObservationType.RESTING_HEART_RATE -> RESTING_HEART_RATE
        ObservationType.OXYGEN_SATURATION -> OXYGEN_SATURATION
        ObservationType.BODY_TEMPERATURE -> BODY_TEMPERATURE
        ObservationType.BLOOD_GLUCOSE -> when (VitalContextCode.decode(context).meal) {
            MealContext.FASTING -> GLUCOSE_FASTING
            MealContext.POST_MEAL -> GLUCOSE_POST_MEAL
            MealContext.PRE_MEAL -> GLUCOSE_FASTING
            null -> GLUCOSE_FASTING
        }

        else -> null
    }

    /** The one line a range row prints: population, setting and the published statement. */
    fun citationLine(range: ReferenceRange): String =
        "${range.population} - ${range.statement} ${range.unit.ucum}"

    /** Every bound the tests under `:core` must find a citation for. */
    fun all(): List<ReferenceRange> = listOf(
        BLOOD_PRESSURE_SYSTOLIC,
        BLOOD_PRESSURE_DIASTOLIC,
        RESTING_HEART_RATE,
        OXYGEN_SATURATION,
        BODY_TEMPERATURE,
        GLUCOSE_FASTING,
        GLUCOSE_POST_MEAL,
    )

    /**
     * The four zones of the clinical strip for a reading, when the published boundaries
     * support one (`DESIGN.md` §8.2.1). Only blood pressure and glucose have the two
     * published upper thresholds the four stops need; every other vital uses a band.
     */
    fun scaleFor(type: ObservationType, context: String?): List<RangeBand>? = when (type) {
        ObservationType.BLOOD_PRESSURE_SYSTOLIC -> listOf(
            RangeBand(RangeBandLabel.LOW, null, 90.0, CitationKey.AHA_LOW_BLOOD_PRESSURE),
            RangeBand(RangeBandLabel.NORMAL, 90.0, 135.0, CitationKey.ESH_HYPERTENSION_2023),
            RangeBand(RangeBandLabel.RAISED, 135.0, 140.0, CitationKey.ESH_HYPERTENSION_2023),
            RangeBand(RangeBandLabel.HIGH, 140.0, null, CitationKey.ESH_HYPERTENSION_2023),
        )

        ObservationType.BLOOD_PRESSURE_DIASTOLIC -> listOf(
            RangeBand(RangeBandLabel.LOW, null, 60.0, CitationKey.AHA_LOW_BLOOD_PRESSURE),
            RangeBand(RangeBandLabel.NORMAL, 60.0, 85.0, CitationKey.ESH_HYPERTENSION_2023),
            RangeBand(RangeBandLabel.RAISED, 85.0, 90.0, CitationKey.ESH_HYPERTENSION_2023),
            RangeBand(RangeBandLabel.HIGH, 90.0, null, CitationKey.ESH_HYPERTENSION_2023),
        )

        ObservationType.BLOOD_GLUCOSE -> when (VitalContextCode.decode(context).meal) {
            MealContext.POST_MEAL -> listOf(
                RangeBand(RangeBandLabel.LOW, null, 3.9, CitationKey.ADA_GLYCEMIC_TARGETS_2026),
                RangeBand(
                    RangeBandLabel.NORMAL,
                    3.9,
                    7.8,
                    CitationKey.ADA_GLYCEMIC_TARGETS_2026,
                ),
                RangeBand(
                    RangeBandLabel.RAISED,
                    7.8,
                    11.1,
                    CitationKey.ADA_GLYCEMIC_TARGETS_2026,
                ),
                RangeBand(RangeBandLabel.HIGH, 11.1, null, CitationKey.ADA_GLYCEMIC_TARGETS_2026),
            )

            else -> listOf(
                RangeBand(RangeBandLabel.LOW, null, 3.9, CitationKey.ADA_GLYCEMIC_TARGETS_2026),
                RangeBand(
                    RangeBandLabel.NORMAL,
                    3.9,
                    5.6,
                    CitationKey.ADA_GLYCEMIC_TARGETS_2026,
                ),
                RangeBand(
                    RangeBandLabel.RAISED,
                    5.6,
                    7.0,
                    CitationKey.ADA_GLYCEMIC_TARGETS_2026,
                ),
                RangeBand(RangeBandLabel.HIGH, 7.0, null, CitationKey.ADA_GLYCEMIC_TARGETS_2026),
            )
        }

        else -> null
    }
}

/** The reading contexts a vital can carry, encoded in the one `context` column. */
data class VitalContextCode(
    val arm: ArmSide? = null,
    val position: BodyPosition? = null,
    val meal: MealContext? = null,
    val homeSlot: HomeSlot? = null,
) {
    fun encode(): String? {
        val parts = buildList {
            arm?.let { add("arm=${it.key}") }
            position?.let { add("position=${it.key}") }
            meal?.let { add("meal=${it.key}") }
            homeSlot?.let { add("home=${it.key}") }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(";")
    }

    companion object {
        /**
         * Reads a stored context. Older rows may hold a plain [ObservationContext] name
         * (B3 wrote `RESTING` and friends), so that is understood too.
         */
        fun decode(raw: String?): VitalContextCode {
            if (raw.isNullOrBlank()) return VitalContextCode()
            val fields = raw.split(';').mapNotNull { part ->
                val index = part.indexOf('=')
                if (index > 0) part.substring(0, index) to part.substring(index + 1) else null
            }.toMap()
            if (fields.isEmpty()) {
                return when (raw.trim().uppercase()) {
                    ObservationContext.FASTING.name -> VitalContextCode(meal = MealContext.FASTING)
                    ObservationContext.POST_MEAL.name ->
                        VitalContextCode(meal = MealContext.POST_MEAL)

                    ObservationContext.PRE_MEAL.name ->
                        VitalContextCode(meal = MealContext.PRE_MEAL)

                    ObservationContext.LEFT_ARM.name -> VitalContextCode(arm = ArmSide.LEFT)
                    ObservationContext.RIGHT_ARM.name -> VitalContextCode(arm = ArmSide.RIGHT)
                    ObservationContext.SITTING.name ->
                        VitalContextCode(position = BodyPosition.SITTING)

                    ObservationContext.STANDING.name ->
                        VitalContextCode(position = BodyPosition.STANDING)

                    ObservationContext.LYING.name ->
                        VitalContextCode(position = BodyPosition.LYING)

                    ObservationContext.RESTING.name ->
                        VitalContextCode(meal = null, homeSlot = null)

                    else -> VitalContextCode()
                }
            }
            return VitalContextCode(
                arm = ArmSide.fromKey(fields["arm"]),
                position = BodyPosition.fromKey(fields["position"]),
                meal = MealContext.fromKey(fields["meal"]),
                homeSlot = HomeSlot.fromKey(fields["home"]),
            )
        }
    }
}

enum class ArmSide(val key: String) {
    LEFT("left"),
    RIGHT("right"),
    ;

    companion object {
        fun fromKey(key: String?): ArmSide? = entries.firstOrNull { it.key == key }
    }
}

enum class BodyPosition(val key: String) {
    SITTING("sitting"),
    STANDING("standing"),
    LYING("lying"),
    ;

    companion object {
        fun fromKey(key: String?): BodyPosition? = entries.firstOrNull { it.key == key }
    }
}

enum class MealContext(val key: String) {
    FASTING("fasting"),
    PRE_MEAL("pre_meal"),
    POST_MEAL("post_meal"),
    ;

    companion object {
        fun fromKey(key: String?): MealContext? = entries.firstOrNull { it.key == key }
    }
}

enum class HomeSlot(val key: String) {
    MORNING("morning"),
    EVENING("evening"),
    ;

    companion object {
        fun fromKey(key: String?): HomeSlot? = entries.firstOrNull { it.key == key }
    }
}
