package app.openhealth.core.rollup

import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.units.Measure
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * Layer 3 (`pr.md` §7.3): one row per type and local day. It records the observation
 * watermark it was computed from, the source that won, and why a day fell back or
 * merged. It is derived data: rebuild it, never patch it.
 */
data class DailyRollup(
    val localDate: LocalDate,
    val type: ObservationType,
    val value: Measure,
    val secondaryValue: Double? = null,
    val sourceId: SourceId? = null,
    val contributingSources: Int = 0,
    val dedupReason: String? = null,
    val watermark: Instant,
    val computedAt: Instant,
    val detail: String? = null,
) {
    init {
        require(value.unit == type.canonicalUnit) {
            "${type.name} rolls up in ${type.canonicalUnit.ucum}, was ${value.unit.ucum}"
        }
    }
}
