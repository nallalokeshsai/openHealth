package app.openhealth.core.preferences

/**
 * The common allergens of `pr.md` §6.3, stored by key in settings. The app never infers
 * an allergen: a product carries the tags its label declares (Open Food Facts publishes
 * them), and a food with no tag data says so rather than saying "safe".
 *
 * A person may type an allergy on onboarding; [fromKey] accepts the key, the human name,
 * a plural, or an Open Food Facts tag so "Peanuts", "tree nuts" and "en:milk" all match.
 */
enum class Allergen(
    val key: String,
    val labelKey: String,
    val offTags: Set<String>,
    val aliases: Set<String>,
) {
    GLUTEN(
        "gluten",
        "allergen.gluten",
        setOf("en:gluten"),
        setOf("gluten", "wheat", "wheat gluten", "cereals containing gluten"),
    ),
    MILK(
        "milk",
        "allergen.milk",
        setOf("en:milk"),
        setOf("milk", "dairy", "lactose", "milk and dairy"),
    ),
    EGGS("eggs", "allergen.eggs", setOf("en:eggs"), setOf("egg", "eggs")),
    PEANUTS(
        "peanuts",
        "allergen.peanuts",
        setOf("en:peanuts"),
        setOf("peanut", "peanuts", "groundnut", "groundnuts"),
    ),
    NUTS(
        "nuts",
        "allergen.nuts",
        setOf("en:nuts"),
        setOf("nut", "nuts", "tree nut", "tree nuts", "treenut", "treenuts"),
    ),
    SOY("soy", "allergen.soy", setOf("en:soybeans"), setOf("soy", "soya", "soybean", "soybeans")),
    FISH("fish", "allergen.fish", setOf("en:fish"), setOf("fish")),
    SHELLFISH(
        "shellfish",
        "allergen.shellfish",
        setOf("en:crustaceans", "en:molluscs"),
        setOf("shellfish", "crustacean", "crustaceans", "mollusc", "molluscs", "seafood"),
    ),
    SESAME(
        "sesame",
        "allergen.sesame",
        setOf("en:sesame-seeds"),
        setOf("sesame", "sesame seed", "sesame seeds", "tahini"),
    ),
    OTHER("other", "allergen.other", emptySet(), setOf("other")),
    ;

    fun declaredIn(tags: Set<String>): Boolean = offTags.any { it in tags }

    companion object {
        fun fromKey(key: String): Allergen? {
            val clean = key.trim().lowercase()
                .removePrefix("en:")
                .replace('_', ' ')
                .replace('-', ' ')
                .replace(Regex("\\s+"), " ")
            if (clean.isBlank()) return null
            return entries.firstOrNull { allergen ->
                clean == allergen.key || clean in allergen.aliases
            }
        }

        fun fromKeys(keys: Iterable<String>): Set<Allergen> = keys.mapNotNull(::fromKey).toSet()
    }
}
