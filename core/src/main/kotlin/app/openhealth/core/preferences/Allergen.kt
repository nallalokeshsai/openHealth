package app.openhealth.core.preferences

/**
 * The common allergens of `pr.md` §6.3, stored by key in settings. The app never infers
 * an allergen: a product carries the tags its label declares (Open Food Facts publishes
 * them), and a food with no tag data says so rather than saying "safe".
 */
enum class Allergen(val key: String, val labelKey: String, val offTags: Set<String>) {
    GLUTEN("gluten", "allergen.gluten", setOf("en:gluten")),
    MILK("milk", "allergen.milk", setOf("en:milk")),
    EGGS("eggs", "allergen.eggs", setOf("en:eggs")),
    PEANUTS("peanuts", "allergen.peanuts", setOf("en:peanuts")),
    NUTS("nuts", "allergen.nuts", setOf("en:nuts")),
    SOY("soy", "allergen.soy", setOf("en:soybeans")),
    FISH("fish", "allergen.fish", setOf("en:fish")),
    SHELLFISH("shellfish", "allergen.shellfish", setOf("en:crustaceans", "en:molluscs")),
    SESAME("sesame", "allergen.sesame", setOf("en:sesame-seeds")),
    OTHER("other", "allergen.other", emptySet()),
    ;

    fun declaredIn(tags: Set<String>): Boolean = offTags.any { it in tags }

    companion object {
        fun fromKey(key: String): Allergen? = entries.firstOrNull { it.key == key }

        fun fromKeys(keys: Iterable<String>): Set<Allergen> =
            keys.mapNotNull(::fromKey).toSet()
    }
}
