package app.openhealth.core.ai

/**
 * The data categories the coach may read (`pr.md` §6.15, `Tech-Stack.md` §9). Consent is
 * granted per category, and Health Connect-derived rows inside a consented category need
 * a second, separate switch because the platform terms restrict passing that data on
 * (`pr.md` §12.3).
 *
 * A category that is not in [CoachConsent.categories] is never serialised into a provider
 * request. Nothing is inferred from another category being on.
 */
enum class CoachCategory(val key: String) {
    PROFILE("profile"),
    TRAINING("training"),
    RECOVERY("recovery"),
    NUTRITION("nutrition"),
    SLEEP("sleep"),
    VITALS("vitals"),
    ;

    companion object {
        fun fromKey(key: String?): CoachCategory? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The one consent object every coach path asks, mirroring `DisplayPolicy`'s shape: no
 * screen, request builder or MCP tool reads a raw switch. Off by default.
 */
data class CoachConsent(
    val enabled: Boolean = false,
    val categories: Set<CoachCategory> = emptySet(),
    val healthConnect: Boolean = false,
) {
    /**
     * True when [category] may be sent. A Health Connect-derived row needs
     * [healthConnect] as well; a manual entry in the same category does not.
     */
    fun allows(category: CoachCategory, viaHealthConnect: Boolean = false): Boolean =
        enabled && category in categories && (!viaHealthConnect || healthConnect)

    /** True when the person has turned the coach on and consented to at least one block. */
    val sendsAnything: Boolean get() = enabled && categories.isNotEmpty()

    companion object {
        val Off: CoachConsent = CoachConsent()
    }
}

/**
 * One row of the "what leaves the phone" list shown before the first send
 * (`DESIGN.md` §14 Coach). The copy is selected by the UI; the core decides which rows
 * apply.
 */
data class CoachDisclosureRow(
    val category: CoachCategory,
    val included: Boolean,
)

object CoachDisclosure {
    /** One row per category, in the order the settings surface lists them. */
    fun rows(consent: CoachConsent): List<CoachDisclosureRow> =
        CoachCategory.entries.map { category ->
            CoachDisclosureRow(category = category, included = consent.allows(category))
        }
}
