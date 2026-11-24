package app.openhealth.core.training

/**
 * Training loads are stored in kilograms, the one canonical unit (`pr.md` §7.2), and
 * shown in the profile's unit. The conversion is here so the presenter and the plate
 * sheet cannot disagree about a pound.
 */
object TrainingUnits {
    const val POUND_KILOGRAMS: Double = 0.45359237

    fun toDisplay(kilograms: Double, imperial: Boolean): Double =
        if (imperial) kilograms / POUND_KILOGRAMS else kilograms

    fun toKilograms(display: Double, imperial: Boolean): Double =
        if (imperial) display * POUND_KILOGRAMS else display

    fun label(imperial: Boolean): String = if (imperial) "lb" else "kg"
}
