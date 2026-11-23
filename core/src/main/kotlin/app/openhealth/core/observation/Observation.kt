package app.openhealth.core.observation

import app.openhealth.core.ids.ObservationId
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * A canonical observation: layer 2 of `pr.md` §7. It always has provenance, a canonical
 * unit and a local day. It is never deleted without a tombstone (`pr.md` §6.29).
 */
data class Observation(
    val id: ObservationId,
    val type: ObservationType,
    val value: Measure,
    val start: Instant,
    val end: Instant?,
    val provenance: Provenance,
    val context: ObservationContext? = null,
    val quality: Quality = Quality(),
    val userVerified: Boolean = false,
    val userEdited: Boolean = false,
    val editTrail: EditTrail = EditTrail(),
    val groupKey: String? = null,
    val excludedFromTrends: Boolean = false,
) {
    init {
        require(value.unit == type.canonicalUnit) {
            "${type.name} is stored in ${type.canonicalUnit.ucum}, was ${value.unit.ucum}"
        }
        require(end == null || end >= start) { "end $end is before start $start" }
        require(!userEdited || !editTrail.isEmpty) {
            "a user-edited value must keep the value it replaced"
        }
    }

    val localDate: LocalDate get() = provenance.localDate

    fun duration(): kotlin.time.Duration? = end?.minus(start)

    /** The value moved into another unit for display; storage keeps the canonical one. */
    fun displayValue(unit: UcumUnit): Measure = value.convertTo(unit)

    companion object {
        /**
         * Converts to the type's canonical unit and returns an observation ready to
         * store. Optional context is set with `copy` at the call site.
         */
        fun of(
            id: ObservationId,
            type: ObservationType,
            value: Measure,
            start: Instant,
            provenance: Provenance,
        ): Observation = Observation(
            id = id,
            type = type,
            value = value.convertTo(type.canonicalUnit),
            start = start,
            end = null,
            provenance = provenance,
        )
    }
}
