package app.openhealth.core.training

/** One bar the person owns: a barbell, an EZ bar, a trap bar, a Smith machine or a custom bar. */
data class BarType(val key: String, val weightKg: Double)

/**
 * The one equipment profile the MVP ships (`pr.md` §6.5: plates, dumbbells, bars). Per-gym
 * profiles are V1; this record is serialised into one settings value by a small, versioned
 * text codec so `:core` needs no serialization plugin.
 */
data class EquipmentProfile(
    val bars: List<BarType> = DEFAULT_BARS,
    /** Available plates per side, in kilograms, in pairs. */
    val platesKg: List<Double> = DEFAULT_PLATES_KG,
    val dumbbellIncrementKg: Double = 2.5,
    val defaultBarKey: String = "barbell",
) {
    fun bar(key: String? = null): BarType =
        bars.firstOrNull { it.key == (key ?: defaultBarKey) } ?: bars.first()

    companion object {
        val DEFAULT_BARS: List<BarType> = listOf(
            BarType("barbell", 20.0),
            BarType("ez", 10.0),
            BarType("trap", 25.0),
            BarType("smith", 15.0),
        )
        val DEFAULT_PLATES_KG: List<Double> =
            listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

        private const val VERSION = "v1"
        private const val FIELD = ";"
        private const val LIST = ","
        private const val BAR_FIELD = ":"
        private const val LEGACY_BAR_PARTS = 3

        /** A stable, human-readable encoding; an unreadable value falls back to the defaults. */
        fun encode(profile: EquipmentProfile): String = listOf(
            VERSION,
            "default=${profile.defaultBarKey}",
            "dumbbell=${profile.dumbbellIncrementKg}",
            "plates=${profile.platesKg.joinToString(LIST)}",
            "bars=" +
                profile.bars.joinToString(LIST) {
                    "${it.key}$BAR_FIELD${it.weightKg}"
                },
        ).joinToString(FIELD)

        fun decode(value: String?): EquipmentProfile {
            if (value.isNullOrBlank()) return EquipmentProfile()
            return runCatching {
                val fields = value.split(FIELD).associate { part ->
                    val at = part.indexOf('=')
                    if (at <= 0) part to "" else part.substring(0, at) to part.substring(at + 1)
                }
                val bars = fields["bars"]?.split(LIST)?.mapNotNull { entry ->
                    val parts = entry.split(BAR_FIELD)
                    when (parts.size) {
                        2 -> BarType(parts[0], parts[1].toDouble())
                        // A profile written before the label moved to the UI carried
                        // key:label:weight; keep it readable rather than reset the plates.
                        LEGACY_BAR_PARTS -> BarType(parts[0], parts[2].toDouble())
                        else -> null
                    }
                }?.takeIf { it.isNotEmpty() } ?: DEFAULT_BARS
                val plates = fields["plates"]?.split(LIST)?.mapNotNull { it.toDoubleOrNull() }
                    ?.takeIf { it.isNotEmpty() } ?: DEFAULT_PLATES_KG
                EquipmentProfile(
                    bars = bars,
                    platesKg = plates,
                    dumbbellIncrementKg = fields["dumbbell"]?.toDoubleOrNull()
                        ?.takeIf { it > 0.0 } ?: 2.5,
                    defaultBarKey = fields["default"]?.takeIf { key ->
                        bars.any { it.key == key }
                    } ?: bars.first().key,
                )
            }.getOrDefault(EquipmentProfile())
        }
    }
}

/** A plate solution for one side of the bar. */
data class PlateSolution(
    val targetKg: Double,
    val barKg: Double,
    val perSideKg: List<Double>,
    val achievedKg: Double,
) {
    val exact: Boolean get() = kotlin.math.abs(achievedKg - targetKg) < TOLERANCE_KG
    val perSideGapKg: Double get() = (targetKg - achievedKg) / 2.0
}

private const val TOLERANCE_KG = 0.01
private const val SIDES = 2

/**
 * Plate math (`pr.md` §6.5): the smallest list of available plates that reaches the target
 * from one bar. Plates are greedy from the largest because the sets are conventional
 * (25/20/15/10/5/2.5/1.25), and a target below the bar is refused rather than invented.
 */
object PlateMath {
    fun solve(targetKg: Double, barKg: Double, platesKg: List<Double>): PlateSolution? {
        if (targetKg <= 0.0 || barKg <= 0.0 || targetKg < barKg) return null
        var remaining = (targetKg - barKg) / SIDES
        val chosen = mutableListOf<Double>()
        platesKg.sortedDescending().forEach { plate ->
            if (plate <= 0.0) return@forEach
            while (remaining + TOLERANCE_KG >= plate) {
                chosen += plate
                remaining -= plate
            }
        }
        val achieved = barKg + chosen.sum() * SIDES
        return PlateSolution(
            targetKg = targetKg,
            barKg = barKg,
            perSideKg = chosen,
            achievedKg = (achieved * 100).toInt() / 100.0,
        )
    }
}
