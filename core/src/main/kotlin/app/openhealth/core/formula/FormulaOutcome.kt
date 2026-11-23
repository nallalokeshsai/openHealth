package app.openhealth.core.formula

import app.openhealth.core.citation.CitationKey

/** One number that went into a result, with its unit, for the explanation sheet. */
data class FormulaInput(val key: String, val value: Double, val unit: String)

enum class NotAvailableReason {
    NOT_ENOUGH_DATA,
    MISSING_INPUT,
    OUT_OF_RANGE,
    NOT_SUPPORTED,
}

/**
 * Every formula returns its value **and** the inputs that produced it and the citation
 * that backs it (`pr.md` §6.4, §6.10). A formula that cannot run says why instead of
 * returning a made-up number.
 */
sealed interface FormulaOutcome<out T> {
    data class Computed<T>(
        val value: T,
        val inputs: List<FormulaInput>,
        val citation: CitationKey,
        val version: Int = 1,
    ) : FormulaOutcome<T>

    data class NotAvailable(
        val reason: NotAvailableReason,
        val citation: CitationKey,
        val inputs: List<FormulaInput> = emptyList(),
    ) : FormulaOutcome<Nothing>
}

internal fun <T> computed(
    value: T,
    citation: CitationKey,
    inputs: List<FormulaInput>,
): FormulaOutcome<T> = FormulaOutcome.Computed(value, inputs, citation)

internal fun <T> notAvailable(
    reason: NotAvailableReason,
    citation: CitationKey,
    inputs: List<FormulaInput> = emptyList(),
): FormulaOutcome<T> = FormulaOutcome.NotAvailable(reason, citation, inputs)

internal fun input(key: String, value: Double, unit: String): FormulaInput =
    FormulaInput(key, value, unit)
