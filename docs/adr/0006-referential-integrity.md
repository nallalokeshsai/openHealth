# ADR 0006 — Referential integrity, and where foreign keys can actually be switched on

**Date:** 2026-11-23 · **Phase:** B2 follow-up · **Status:** accepted
**Supersedes:** the "no foreign keys" placeholder recorded in `docs/build-log.md` §B2.

## Context

B2 shipped the five-layer schema with `PRAGMA foreign_keys` set at open time but **no
`REFERENCES` clause on any table**. Every link between layers — `observation.source_id`,
`observation.raw_record_id`, `entry.meal_id`, `entry.food_id`, `work_set.session_id`,
`work_set.exercise_id` and the rest — was a bare `TEXT` column. Nothing stopped a value
from naming a source, meal or session that does not exist, and the provenance invariant
(`pr.md` §2.2, §7.2) rests on those links being real. The build log recorded it as
deferred to B3/B5/B10; a review asked whether that was deliberate, and the owner chose to
close it now, while v1 and v2 have never shipped and there is no installed base to
migrate.

## Decision

### 1. Every cross-table link is a declared foreign key

36 `REFERENCES` clauses across 15 tables, in two flavours:

- **Structural parent → `ON DELETE RESTRICT`.** `observation.source_id`,
  `raw_record.source_id`, `work_set.session_id`, `work_set.exercise_id`,
  `session_exercise.session_id`, `routine_exercise.routine_id` and their peers. A parent
  with history cannot be deleted at all. This is deliberately *not* `CASCADE`: a cascade
  would delete children with no tombstone, which breaks "undo is a tombstone, not a
  timer" (`pr.md` §6.29). B3/B5 must write tombstones for the children first, and the
  database now forces them to.
- **Optional provenance link → `ON DELETE SET NULL`.** `observation.raw_record_id`,
  `*.observation_id`, `entry.meal_id`, `entry.food_id`, `daily_rollup.source_id` and the
  optional `source_id` columns. Compacting a raw record (`pr.md` §7.1) or deleting a meal
  must leave the value and its own provenance block intact, not take it down.

### 2. Schema v3, rebuilt table by table

SQLite cannot add a constraint with `ALTER TABLE`, so `2.sqm` rebuilds all 15 affected
tables. Three constraints shaped how it is written, each learned by it failing:

- **Columns are listed explicitly.** SQLDelight drops the `*` from `SELECT *` when it
  emits migration SQL, so `CREATE TABLE t AS SELECT * FROM t` reaches SQLite as
  `SELECT  FROM t` and fails to parse.
- **The carry tables are ordinary tables, not `TEMP`.** `TEMP` is per-connection and the
  driver does not promise one connection for the whole migration.
- **No `ALTER TABLE RENAME`.** Renaming reparses the whole schema, which throws while a
  referenced table is momentarily missing.

`2.sqm` is generated from the `.sq` files rather than hand-written, so the migration's
DDL cannot drift from the schema it migrates to.

### 3. Foreign keys are a connection property, not a PRAGMA

This is the finding that forced an API change. **`PRAGMA foreign_keys` is silently
ignored on an established connection.** Measured on sqlite-jdbc: a driver opened without
the property reads back `foreign_keys = 0`, and `execute("PRAGMA foreign_keys = ON")`
leaves it at `0`. B2's `DatabaseOpener` issued exactly that statement, so **the
pragma it relied on had never taken effect** — the only reason nothing broke is that
there were no constraints to enforce.

Enforcement therefore has to be set where the connection is created:

- **JVM (tests, `tools:fixtures`):** the `foreign_keys` connection property.
- **Android (SQLCipher):** `setForeignKeyConstraintsEnabled` in the open-helper callback,
  which applies to every connection in the pool, unlike a `PRAGMA` that reaches one.

`DatabaseOpener` now takes a `DatabaseDriverFactory` instead of a ready-made driver,
because it needs two connections with different settings: one with foreign keys **off**
to run the upgrade (a migration rebuilds referenced tables), and one with them **on** for
use. It runs `PRAGMA foreign_key_check` after every migration and refuses to return a
store whose connection does not actually enforce the keys — read back, not assumed.

### 4. Parent upserts stopped being `INSERT OR REPLACE`

`INSERT OR REPLACE` deletes the old row and inserts a new one. Against a table that is
now a foreign key target that trips `RESTRICT` — and under any cascade it would have
silently destroyed the children. `source`, `food`, `meal`, `exercise`, `routine`,
`programme` and `session_exercise` now use `ON CONFLICT(id) DO UPDATE`, which updates in
place and leaves `created_at` alone. This needed the SQLDelight dialect moved from
`sqlite-3-18` to `sqlite-3-38`: UPSERT is SQLite 3.24+. That is safe below `minSdk 28`
because SQLCipher bundles its own SQLite (4.20.0 ships the 3.4x line) and the platform's
SQLite is never the one that runs.

### 5. Three unreachable delete statements removed

`deleteProfile`, `deleteSource` and `deleteTombstone` were generated queries with no
caller. `TrashService` is the only delete path (`pr.md` §6.29), and leaving them in the
`.sq` files invited a later phase to bypass it. Each is replaced by a comment saying why
there is no such query.

## Consequences

- A test or fixture that writes a value must now create the source, session, exercise or
  food it names, exactly as the app does. Several tests were relying on dangling links.
- Writes carry a foreign key check. Measured on the five-year corpus after the change:
  the rollup rebuild takes about 0.7 s and the slowest UI query is the timeline page at
  5.2 ms against the 16 ms budget — unchanged from B2's figure, so no budget moved. (A
  12 ms reading for that query appeared during a full parallel `check`; re-measured on
  its own it is 5.2 ms. The benchmark is sensitive to machine load, which is already an
  open issue in the build log.)
- B3/B5 inherit a database that refuses to delete a session or routine with children, so
  the cascade has to be written deliberately, with tombstones.
- B10's migration matrix now has a real v2 → v3 upgrade to test, not only machinery.
