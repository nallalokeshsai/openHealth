package app.openhealth.core.body

import app.openhealth.core.formula.FormulaOutcome
import app.openhealth.core.formula.NavyBodyFat
import app.openhealth.core.formula.SexAtBirth

/**
 * The adipose layer of the dynamic body model (`DESIGN.md` §9.1.1).
 *
 * It is a 2D schematic, not a likeness: each of the seven regions gets a thickness as a
 * fraction of the figure's drawn height, and the renderer offsets that region's outline by
 * it. The thickness comes from the person's own data only:
 *
 * 1. body fat % when one is recorded; otherwise the cited US Navy tape estimate when the
 *    neck, waist and (female) hip are known; otherwise there is no layer at all;
 * 2. a per-region anatomical factor, because a waist and a forearm do not thicken alike;
 * 3. a tape measurement at a region scales that region by its size against the median of
 *    the person's *own measured regions* (clamped), so no population reference is invented
 *    and a single measurement changes nothing.
 *
 * Every value is clamped so the figure stays anatomical. The UI labels the layer an
 * estimate, hides it entirely in weight-neutral mode, and draws it only outside the muscle
 * shapes (`DESIGN.md` §9.1.1, §7.6).
 */
data class AdiposeInputs(
    val sex: BodySex,
    val bodyFatPercent: Double? = null,
    val heightCm: Double? = null,
    /** Tape circumferences in centimetres, keyed by measurement type (`waist`, `hips`, …). */
    val tapeCm: Map<String, Double> = emptyMap(),
)

/** Region thicknesses as a fraction of the drawn figure height. */
data class AdiposeLayer(val thickness: Map<MuscleRegion, Double>) {
    fun thicknessFor(region: MuscleRegion): Double = thickness[region] ?: 0.0

    /** The thickest band, used as the one scale factor a renderer needs. */
    val maxThickness: Double get() = thickness.values.maxOrNull() ?: 0.0
}

object AdiposeModel {
    /** At 25 % body fat the band is 2 % of the figure height (`DESIGN.md` §9.1.1). */
    private const val BASE_PER_PERCENT = 0.08

    /** Never thinner than a visible hairline, never thicker than a plausible outline. */
    const val MIN_THICKNESS: Double = 0.004
    const val MAX_THICKNESS: Double = 0.035

    private const val MIN_BODY_FAT = 1.0
    private const val MAX_BODY_FAT = 70.0
    private const val PERCENT = 100.0
    private const val TAPE_SCALE_FLOOR = 0.75
    private const val TAPE_SCALE_CEILING = 1.3
    private const val SINGLE_MEASUREMENT = 1
    private const val NO_SCALE = 1.0

    // A waist and a forearm do not thicken alike; these factors say by how much.
    private const val NECK_FACTOR = 0.5
    private const val CHEST_FACTOR = 0.9
    private const val MIDSECTION_FACTOR = 1.0
    private const val HIPS_FACTOR = 1.05
    private const val THIGHS_FACTOR = 0.95
    private const val ARMS_FACTOR = 0.6
    private const val CALVES_FACTOR = 0.55

    private val REGION_FACTOR: Map<MuscleRegion, Double> = mapOf(
        MuscleRegion.NECK to NECK_FACTOR,
        MuscleRegion.CHEST to CHEST_FACTOR,
        MuscleRegion.MIDSECTION to MIDSECTION_FACTOR,
        MuscleRegion.HIPS to HIPS_FACTOR,
        MuscleRegion.THIGHS to THIGHS_FACTOR,
        MuscleRegion.ARMS to ARMS_FACTOR,
        MuscleRegion.CALVES to CALVES_FACTOR,
    )

    /**
     * The layer, or null when the person has neither a body-fat figure nor the tape
     * measurements the Navy estimate needs (`DESIGN.md` §9.1.1: "with neither it is not
     * drawn").
     */
    fun compute(inputs: AdiposeInputs): AdiposeLayer? {
        val percent = fatPercent(inputs)?.takeIf { it in MIN_BODY_FAT..MAX_BODY_FAT }
        val base = percent?.div(PERCENT)?.times(BASE_PER_PERCENT)
        val measured = measuredRegions(inputs.tapeCm)
        val median = median(measured.values.toList())
        val thickness = base?.let {
            REGION_FACTOR.mapValues { (region, factor) ->
                val measuredHere = measured[region]
                val scale = if (
                    measuredHere == null || measured.size <= SINGLE_MEASUREMENT || median == null
                ) {
                    NO_SCALE
                } else {
                    (measuredHere / median).coerceIn(TAPE_SCALE_FLOOR, TAPE_SCALE_CEILING)
                }
                (it * factor * scale).coerceIn(MIN_THICKNESS, MAX_THICKNESS)
            }
        }
        return thickness?.let(::AdiposeLayer)
    }

    /**
     * The person's own measurements that actually shaped this layer, as measurement
     * types, for the provenance sheet (`DESIGN.md` §9.1.1 Provenance, `pr.md` §2.2).
     *
     * With no recorded body fat the layer stands entirely on the tape sites the Navy
     * estimate consumed, so those are its provenance. With a recorded figure, only the
     * sites that scaled a region count: one measurement scales nothing, so it is not
     * claimed to have shaped anything. Empty when there is no layer at all.
     */
    fun provenanceSites(inputs: AdiposeInputs): List<String> {
        val measured = measuredRegions(inputs.tapeCm)
        val regions = when {
            compute(inputs) == null -> emptySet()
            // The estimate stands on exactly the sites the Navy equation consumed.
            inputs.bodyFatPercent == null -> buildSet {
                add(MuscleRegion.NECK)
                add(MuscleRegion.MIDSECTION)
                if (inputs.sex == BodySex.FEMALE) add(MuscleRegion.HIPS)
            }
            measured.size <= SINGLE_MEASUREMENT -> emptySet()
            else -> measured.keys
        }
        return regions.sortedBy { it.ordinal }.map { it.measurementType }
    }

    /**
     * The recorded percentage, else the Navy tape estimate. The estimate is cited and
     * tagged as one wherever it is shown; it never overwrites a measured value.
     */
    fun fatPercent(inputs: AdiposeInputs): Double? = inputs.bodyFatPercent ?: navyEstimate(inputs)

    private fun navyEstimate(inputs: AdiposeInputs): Double? {
        val male = inputs.sex == BodySex.MALE
        val height = inputs.heightCm
        val neck = inputs.tapeCm[MuscleRegion.NECK.measurementType]
        val waist = inputs.tapeCm[MuscleRegion.MIDSECTION.measurementType]
        val hip = if (male) null else inputs.tapeCm[MuscleRegion.HIPS.measurementType]
        val missingBase = height == null || neck == null || waist == null
        val missingHip = !male && hip == null
        if (missingBase || missingHip) return null
        val outcome = NavyBodyFat.compute(
            sex = if (male) SexAtBirth.MALE else SexAtBirth.FEMALE,
            heightCm = height,
            neckCm = neck,
            waistCm = waist,
            hipCm = hip,
        )
        return (outcome as? FormulaOutcome.Computed)?.value
    }

    private fun measuredRegions(tapeCm: Map<String, Double>): Map<MuscleRegion, Double> =
        MuscleRegion.entries.mapNotNull { region ->
            val circumference = tapeCm[region.measurementType]
            if (circumference == null || circumference <= 0.0) null else region to circumference
        }.toMap()

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }
}
