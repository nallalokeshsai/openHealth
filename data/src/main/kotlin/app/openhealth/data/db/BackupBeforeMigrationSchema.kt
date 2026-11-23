package app.openhealth.data.db

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * A schema wrapper that takes a restorable snapshot immediately before a forward
 * migration (`pr.md` §6.29, §7.9). A migration that cannot be backed up does not run: a
 * failed upgrade must never be the first time the app learns the copy was missing.
 */
class BackupBeforeMigrationSchema(
    private val delegate: SqlSchema<QueryResult.Value<Unit>>,
    private val backup: MigrationBackup,
) : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long get() = delegate.version

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> = delegate.create(driver)

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion,
    ): QueryResult.Value<Unit> {
        check(backup.snapshot(oldVersion, newVersion) != null) {
            "Refusing to migrate $oldVersion -> $newVersion without a backup"
        }
        return delegate.migrate(driver, oldVersion, newVersion, *callbacks)
    }
}
