package app.openhealth.data.repo

import app.cash.sqldelight.db.SqlDriver

/**
 * Removes the demo corpus a "Try with sample data" run inserted, so demo mode can be
 * left without wiping a record the person started afterwards.
 *
 * Every corpus row carries an idempotency key beginning `corpus-` (or the generated
 * food ids), and demo sync capture is paused while it is written, so nothing in the
 * change log points at it. Deletes run child-first because the schema restricts most
 * session and meal references; rollups are rebuilt by the caller.
 */
class DemoDataPurger(private val driver: SqlDriver) {
    fun purge() {
        // Work sets before their session, and session exercises before the session.
        exec("DELETE FROM work_set WHERE idempotency_key LIKE 'corpus-%'")
        exec(
            "DELETE FROM session_exercise WHERE session_id IN " +
                "(SELECT id FROM session WHERE idempotency_key LIKE 'corpus-%')",
        )
        exec("DELETE FROM session WHERE idempotency_key LIKE 'corpus-%'")
        exec("DELETE FROM entry WHERE idempotency_key LIKE 'corpus-%'")
        exec("DELETE FROM meal WHERE idempotency_key LIKE 'corpus-%'")
        exec("DELETE FROM measurement WHERE idempotency_key LIKE 'corpus-%'")
        exec("DELETE FROM vital WHERE idempotency_key LIKE 'corpus-%'")
        // The raw records the corpus minted for its step observations.
        exec(
            "DELETE FROM raw_record WHERE id IN " +
                "(SELECT raw_record_id FROM observation " +
                "WHERE idempotency_key LIKE 'corpus-%' AND raw_record_id IS NOT NULL)",
        )
        exec("DELETE FROM observation WHERE idempotency_key LIKE 'corpus-%'")
        // The synthetic foods exist only to give the search index a realistic size.
        exec("DELETE FROM food WHERE id LIKE 'food-indexed-%'")
        exec("DELETE FROM profile WHERE id = 'profile-demo'")
    }

    private fun exec(sql: String) {
        driver.execute(identifier = null, sql = sql, parameters = 0)
    }
}
