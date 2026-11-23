package app.openhealth.data.mapping

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Layer-4 rows carry a local date and a zone as separate columns. The rule that the
 * date really is `happened_at` in that zone is the same one `TimeProvenance` enforces
 * for observations; apply it at every layer-4 write so a row cannot be filed on the
 * wrong day (`pr.md` §2.2, §7.8).
 */
fun requireConsistentDay(localDate: String, zoneId: String, happenedAt: Long) {
    val zone = TimeZone.of(zoneId)
    val expected = Instant.fromEpochMilliseconds(happenedAt).toLocalDateTime(zone).date
    val supplied = LocalDate.parse(localDate)
    require(supplied == expected) {
        "local date $supplied does not match $happenedAt in ${zone.id} ($expected)"
    }
}
