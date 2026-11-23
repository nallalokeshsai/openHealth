package app.openhealth.core.units

import kotlin.math.pow

/**
 * Canonical storage units are UCUM codes (`pr.md` §7.2). One canonical unit exists per
 * dimension; the canonical unit of each dimension is the unit its [UcumUnit] declares
 * with a scale of exactly 1 and an offset of 0 (temperature uses a canonical Celsius).
 *
 * The glucose choice is recorded in `docs/adr/0005-core-and-data.md`: mmol/L.
 */
enum class Dimension {
    MASS,
    BODY_LENGTH,
    DISTANCE,
    TEMPERATURE,
    GLUCOSE,
    ENERGY,
    DURATION,
    VOLUME,
    PRESSURE,
    HEART_RATE,
    FRACTION,
    STEPS,
    COUNT,
    SCORE,
}

/**
 * A closed unit set. [toCanonical] is `value * scale + offset`, so a temperature can be
 * affine and everything else linear.
 */
enum class UcumUnit(
    val ucum: String,
    val dimension: Dimension,
    val scale: Double,
    val offset: Double = 0.0,
    val canonical: Boolean = false,
) {
    // Mass, canonical kilogram.
    KILOGRAM(ucum = "kg", dimension = Dimension.MASS, scale = 1.0, canonical = true),
    GRAM(ucum = "g", dimension = Dimension.MASS, scale = 0.001),
    MILLIGRAM(ucum = "mg", dimension = Dimension.MASS, scale = 0.000001),
    POUND(ucum = "[lb_av]", dimension = Dimension.MASS, scale = 0.45359237),

    // Body length, canonical centimetre (`pr.md` §7.2).
    CENTIMETRE(ucum = "cm", dimension = Dimension.BODY_LENGTH, scale = 1.0, canonical = true),
    INCH(ucum = "[in_i]", dimension = Dimension.BODY_LENGTH, scale = 2.54),

    // Distance, canonical metre (`pr.md` §7.2).
    METRE(ucum = "m", dimension = Dimension.DISTANCE, scale = 1.0, canonical = true),
    KILOMETRE(ucum = "km", dimension = Dimension.DISTANCE, scale = 1000.0),
    MILE(ucum = "[mi_i]", dimension = Dimension.DISTANCE, scale = 1609.344),
    FOOT(ucum = "[ft_i]", dimension = Dimension.DISTANCE, scale = 0.3048),

    // Temperature, canonical degree Celsius.
    CELSIUS(ucum = "Cel", dimension = Dimension.TEMPERATURE, scale = 1.0, canonical = true),
    FAHRENHEIT(
        ucum = "[degF]",
        dimension = Dimension.TEMPERATURE,
        scale = 5.0 / 9.0,
        offset =
        -32.0 * 5.0 / 9.0,
    ),

    // Blood glucose, canonical mmol/L (ADR 0005).
    MILLIMOL_PER_LITRE(
        ucum = "mmol/L",
        dimension = Dimension.GLUCOSE,
        scale = 1.0,
        canonical = true,
    ),
    MILLIGRAM_PER_DECILITRE(ucum = "mg/dL", dimension = Dimension.GLUCOSE, scale = 1.0 / 18.0182),

    // Energy, canonical kilocalorie.
    KILOCALORIE(ucum = "kcal", dimension = Dimension.ENERGY, scale = 1.0, canonical = true),
    KILOJOULE(ucum = "kJ", dimension = Dimension.ENERGY, scale = 0.239005736),

    // Duration, canonical second (`pr.md` §7.2).
    SECOND(ucum = "s", dimension = Dimension.DURATION, scale = 1.0, canonical = true),
    MINUTE(ucum = "min", dimension = Dimension.DURATION, scale = 60.0),
    HOUR(ucum = "h", dimension = Dimension.DURATION, scale = 3600.0),
    MILLISECOND(ucum = "ms", dimension = Dimension.DURATION, scale = 0.001),

    // Volume, canonical millilitre.
    MILLILITRE(ucum = "mL", dimension = Dimension.VOLUME, scale = 1.0, canonical = true),
    LITRE(ucum = "L", dimension = Dimension.VOLUME, scale = 1000.0),
    FLUID_OUNCE_US(ucum = "[foz_us]", dimension = Dimension.VOLUME, scale = 29.5735295625),

    // Pressure, canonical millimetre of mercury.
    MILLIMETRE_MERCURY(
        ucum = "mm[Hg]",
        dimension = Dimension.PRESSURE,
        scale = 1.0,
        canonical = true,
    ),
    KILOPASCAL(ucum = "kPa", dimension = Dimension.PRESSURE, scale = 7.500615),

    // Heart rate and cadence.
    PER_MINUTE(ucum = "/min", dimension = Dimension.HEART_RATE, scale = 1.0, canonical = true),

    // Fraction, canonical percent 0-100. A ratio such as 0.25 converts to 25 %.
    PERCENT(ucum = "%", dimension = Dimension.FRACTION, scale = 1.0, canonical = true),
    RATIO(ucum = "1", dimension = Dimension.FRACTION, scale = 100.0),

    // Counts.
    STEP(ucum = "{steps}", dimension = Dimension.STEPS, scale = 1.0, canonical = true),
    COUNT(ucum = "{count}", dimension = Dimension.COUNT, scale = 1.0, canonical = true),
    SCORE(ucum = "{score}", dimension = Dimension.SCORE, scale = 1.0, canonical = true),
    ;

    fun toCanonical(value: Double): Double = value * scale + offset

    fun fromCanonical(canonicalValue: Double): Double = (canonicalValue - offset) / scale

    companion object {
        fun canonicalFor(dimension: Dimension): UcumUnit =
            entries.first { it.dimension == dimension && it.canonical }

        fun byUcum(code: String): UcumUnit {
            val matches = entries.filter { it.ucum == code }
            require(matches.isNotEmpty()) { "Unknown UCUM code: $code" }
            require(matches.size == 1) { "UCUM code $code is ambiguous; pass a dimension" }
            return matches.single()
        }

        fun byUcum(code: String, dimension: Dimension): UcumUnit =
            entries.firstOrNull { it.ucum == code && it.dimension == dimension }
                ?: throw IllegalArgumentException("Unknown UCUM code $code for $dimension")
    }
}

/** A number with a unit. Storage always uses the canonical unit of the dimension. */
data class Measure(val value: Double, val unit: UcumUnit) {
    init {
        require(value.isFinite()) { "Measure value must be finite" }
    }

    val dimension: Dimension get() = unit.dimension

    fun toCanonical(): Measure =
        Measure(unit.toCanonical(value), UcumUnit.canonicalFor(unit.dimension))

    fun convertTo(target: UcumUnit): Measure {
        require(target.dimension == unit.dimension) {
            "Cannot convert ${unit.ucum} (${unit.dimension}) to " +
                "${target.ucum} (${target.dimension})"
        }
        if (target == unit) return this
        val canonicalValue = unit.toCanonical(value)
        return Measure(
            if (target.canonical) canonicalValue else target.fromCanonical(canonicalValue),
            target,
        )
    }

    fun isCanonical(): Boolean = unit.canonical

    override fun toString(): String = "$value ${unit.ucum}"

    companion object {
        fun canonical(value: Double, dimension: Dimension): Measure =
            Measure(value, UcumUnit.canonicalFor(dimension))
    }
}

/** Display rounding only; storage keeps the full value. */
fun Measure.rounded(decimals: Int): Measure {
    require(decimals in 0..MAX_DECIMALS) { "decimals out of range: $decimals" }
    val factor = POWERS_OF_TEN[decimals]
    return copy(value = kotlin.math.round(value * factor) / factor)
}

private const val MAX_DECIMALS = 6
private const val TEN = 10.0
private val POWERS_OF_TEN = DoubleArray(MAX_DECIMALS + 1) { index -> TEN.pow(index) }
