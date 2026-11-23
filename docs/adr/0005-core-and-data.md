# ADR 0005 — Core model, storage, export and the decisions B2 made

**Date:** 2026-11-23 · **Phase:** B2 · **Status:** accepted
**Context:** `BUILD-ROADMAP.md` §B2 asks for the five-layer model, the formulas, the
schema, rollups, trash, export and the platform seams before any feature screen. The
contract fixes the layers (`pr.md` §7), the stack (`Tech-Stack.md` §3, §5–6) and the
quality bars, but leaves the choices below to the build. Each is recorded here with the
reason it went the way it did.

## 1. The module boundary

`:core` holds ids, time, units, provenance, observations, the source registry, dedup
and merge policies, validators, formulas, citations, rollup and raw-record types, and the
platform seams. It has no SQLDelight type, no `android.*`, no `androidx.*` and no
`java.*` in its public API. `:data` holds the SQLDelight schema, repositories, the rollup
engine, trash, export and the fixture corpus; it also stays free of Android.

The seams — clock, secret vault, file store, notifications, health bus, step sensor,
BLE, camera, PDF and the export cipher — are interfaces in `:core`. `app-android`
implements the secret vault and the encrypted database driver; the rest arrive with the
features that use them. This is `Tech-Stack.md` §3 applied, not a new decision.

## 2. Canonical units (`pr.md` §7.2)

Canonical storage units, one per dimension:

| Dimension | Canonical | Notes |
|---|---|---|
| Mass | `kg` | grams and pounds convert in and out |
| Body length | `cm` | height and tape measurements |
| Distance | `m` | steps-derived distance and activities |
| Temperature | `Cel` | `[degF]` is affine |
| Blood glucose | `mmol/L` | US `mg/dL` divides by 18.0182 |
| Energy | `kcal` | `kJ` converts |
| Duration | `s` | sleep and HRV are seconds; the UI shows hours and ms |
| Volume | `mL` | water |
| Pressure | `mm[Hg]` | blood pressure |
| Heart rate | `/min` | |
| Fraction | `%` | 0-100, not 0-1 |
| Steps | `{steps}` | its own dimension, so a score is not a step count |

Body length and distance are separate dimensions because the contract names both `cm`
and `metres` as canonical storage. A cross-dimension conversion is refused rather than
guessed. Glucose uses `mmol/L` (one decimal is enough for every input device the MVP
supports) and a value from `mg/dL` is converted at the boundary.

## 3. Time and the two timestamps

`kotlin.time.Instant` (the type kotlinx-datetime 0.8 re-exports) plus `kotlinx.datetime`
calendar types. Every value carries `happened_at`, `recorded_at`, the local date and the
zone. `TimeProvenance` validates that the local date really is `happened_at` in that
zone. Backfill never touches `recorded_at`; the repository only inserts, and the export
test proves the pair survives a round trip. A day is 23, 24 or 25 hours long around a
DST change, and the day boundaries are computed in the zone the observation was recorded
in.

## 4. Dedup (`pr.md` §7.6)

The source registry orders sources by a **lower-is-better priority** per observation
type, with a family so Garmin over FIT and Garmin over Health Connect are one upstream
device. The defaults are in `SourcePriorityDefaults` and every source may override them;
every rule is visible and overridable.

- Steps, distance, active calories and minutes: the highest-priority source that covers
  the day wins. Coverage means no internal gap longer than one hour and at least sixteen
  tracked hours, which is a waking day. The leading and trailing hours of a day are not
  counted as gaps: a watch that reports only the hours someone is awake is complete, and
  demanding zero-step buckets from 00:00 to 07:00 would hand every day to the second
  source. A source that synced one hour is not a day. A gap hands the whole day to the
  next complete source; sources are never stitched and totals are never summed.
- Weight and resting heart rate: a manual entry beats a device entry; between two of the
  same kind the later wins; two values within a minute merge.
- Sleep: the source with the greatest recorded duration wins, so a partial night never
  replaces a full one.
- Heart rate and HRV are per-source series (`pr.md` §7.6): the day's row is the
  highest-priority source's mean, and the other sources stay in layer 2 untouched.
  Nothing is averaged across devices into one number.
- Blood pressure, glucose, temperature, SpO₂, respiratory rate, mood and stress: every
  reading from the deduplicated family set is kept and averaged for the day's row.
- Nutrition entries add up; they are different foods, not two reports of one number.
- **Family collapse runs first.** Two sources with the same upstream family — Garmin over
  FIT and Garmin over Health Connect — are reduced to the higher-priority member, with
  the source id as a deterministic tie-break, before any policy sees them, so a
  heart-rate series or a water total is never counted twice (`pr.md` §6.18). A source
  that reports a daily total and its intervals is aggregated from the total only, never
  both.

`LocalStore` is the production write path: it constructs the repositories and the
engine, wires every repository that changes a day to `engine.rebuildDay`, and refreshes
the registry then rebuilds all days when a source or its priorities changes. The plain
repositories stay available for importers and tests that do their own batching; the app
constructs `LocalStore` (B3).

The engine also rolls layer 4 into layer 3: a day's nutrition totals are the exact sum
of its diary entries (a macro no entry reports is skipped, not written as zero; an entry
carries `source_id` so B6 can apply its family and user-edited rules), a vital
type follows its observation aggregation (mean or last, never a blind cross-device mean),
and a tape or scale measurement that names an observation type (weight, body fat, lean
mass) contributes its latest value. A layer-2 observation for the same type wins: layer 4
fills a gap, it never overwrites a canonical observation. A full
rebuild walks every day that has an observation, an entry or a vital, so a diary-only
day is not missed. Entry and
vital writes invalidate their day the same way an observation does. Sets feed the
recovery map directly from layer 4 and are not stored as rollups.

The engine writes one `daily_rollup` row per type and local day, with the winning source
(absent when several sources contribute to a mean), the number of contributing sources,
the reason and the observation watermark. An observation carries a raw-record reference
whenever there was an ingestion record; a manual entry has none because the observation
is the entry. Layer-4 measurements, vitals, entries, sessions and sets carry their own
`idempotency_key` (unique), an `observation_id` link, raw-record ref, device and app
version; the phases that write them (B5/B6/B7) write both rows in one transaction. They
are append-only (`INSERT OR IGNORE`): a replayed import cannot move `recorded_at`, and a
correction is a separate path that never rewrites it. A repository also refuses a
`local_date` that is not `happened_at` in the stated zone. Property
tests compare an incremental rebuild with a full rebuild on random event orders and
prove a day never sums two sources.

**Downstream aggregates are read-time.** Weekly averages, streaks, sleep debt and the
adaptive TDEE are computed from the stored days when read, so a backfill only needs the
affected day rebuilt. `affectedDays` returns that day's ISO week as the declared scope;
if a later phase caches one of those aggregates, it must add an invalidation rule here.

## 5. Schema, migrations and opening

SQLDelight 2.4.1, schema version **2** with `1.sqm`. Version 1 is the pre-B2 baseline
and was never shipped: the migration adds the indexes the timeline and the recovery map
need, and the test builds a version-1 database, migrates it and checks every index is
back. A new database is created at version 2.

`DatabaseOpener` refuses a database newer than the app, refuses an unversioned one that
has tables, runs `PRAGMA integrity_check` before the first read and enables WAL and
foreign keys. On Android the WAL pragma runs as a query, because `SqlDriver.execute`
cannot run a statement that returns a row, and the device test asserts the journal mode
is `wal` after opening. `BackupBeforeMigrationSchema` takes a snapshot before any forward
migration and refuses to migrate if the platform cannot copy the database; on Android
the snapshot is the database plus its `-wal` and `-shm` files in app-private storage.
On a device, SQLCipher encrypts the file; the passphrase is a random 32-byte secret
wrapped by a non-exportable Android Keystore AES-256-GCM key. If the Keystore key is
lost, the app refuses to create a new passphrase silently — the database is not
recoverable and the user must be told, not shown an empty diary.

## 6. Cryptography for the export

`crypto/` uses **Bouncy Castle** (`bcprov-jdk18on`, Bouncy Castle Licence, MIT-style)
for Argon2id and the platform JCE for AES-256-GCM. No other crypto library was added;
`de.mkammerer:argon2-jvm` was rejected because it ships native binaries, which is extra
work for the iOS port and the F-Droid build.

- Implementation note: the encryption primitives (javax.crypto and Bouncy Castle) live in
  `:data` behind the `ExportCipher` seam; SHA-256 for payload hashes uses
  `java.security.MessageDigest` in `:data` and is not behind a seam yet. They compile for
  the JVM and Android; when the iOS gate opens, an actual for that target replaces these
  files rather than reimplementing the export format. This is recorded debt, not an
  Android leak.
- Argon2id parameters: 64 MiB, 3 passes, 1 lane, 16-byte salt, 32-byte key.
- Container: magic `OHX1`, format version, KDF parameters, salt, 12-byte nonce,
  ciphertext and GCM tag. Parameters are capped on read (8–256 MiB, 1–10 passes,
  1–8 lanes) so a hostile file cannot exhaust the phone.
- The format is versioned independently of the database schema; an export whose schema
  is not the current one is refused with a clear message rather than half-restored.
- Tests use 8 MiB and one pass to stay fast; the production default is the one above.

## 7. Export and restore shape

The export includes **every table of every layer**, including raw records, settings and
tombstones, plus the schema version, format version and export timestamp. `daily_rollup`
is dumped for inspection but **never restored from the file**: restore clears it and
rebuilds it through the same engine inside the restore transaction, so a hand-edited
bundle cannot seed a lie and a failed rebuild leaves the old database (`Tech-Stack.md`
§5-6). A bundle missing any table, or carrying an unknown one, is refused rather than
half-restored, and `restoreBundle` re-checks the format and schema itself rather than
trusting its caller. It is a generic column dump driven by one table registry
(`ExportTables`) rather than a hand-written mapper per table: a mapper that forgets a
column is a silent data-loss bug, and the registry is checked against the live schema by
a table/column test. Values move as JSON scalars; unknown columns are ignored by the
local loader because the same schema is required. The round-trip test is export →
Argon2id container → restore into a fresh database → export again, and the two bundles
must be equal.

CSV export covers observations only, with their provenance, RFC 4180 quoting and ISO
timestamps. A report surface is never the place to hide where a number came from.

## 8. Trash

`TrashService` writes a tombstone holding the row, deletes the row and marks the
tombstone restorable for 30 days. Undo restores it, clears the payload and marks the
tombstone used, so it cannot be restored twice and no copy of the data survives the
undo. Purge clears the payload past the window but keeps the row as
the permanent deletion marker, so a device that syncs after 30 days cannot resurrect the
entity (`pr.md` §7.7); "delete permanently" is the only path that removes the marker. The four leaf entities the MVP deletes — a set, a diary entry, a measurement,
a vital — are restorable today; a session or a recipe that owns children needs a
cascading trash that arrives with the screen that deletes it (B3/B5).

"Session-length undo" is the UI behaviour of the same tombstone: the Timeline row can
undo until the process ends, and Trash still has it for 30 days. The tombstone is not a
three-second toast. The repositories that own those rows expose no `delete` method;
`TrashService` is the only delete path in `:data`, so a future screen cannot bypass the
tombstone by calling a repository directly.

## 9. Formulas and their citations

Every formula returns a `FormulaOutcome`: a value, its inputs with units, a citation key
and a version, or a reason it cannot run (`NOT_ENOUGH_DATA`, `MISSING_INPUT`,
`OUT_OF_RANGE`, `NOT_SUPPORTED`). The citation table in `:core` holds the real sources:
Mifflin-St Jeor, the US Navy tape equations (Hodgdon & Beckett), WHO BMI bands, Schutz
FFMI, the 2024 Compendium, FAO/WHO/UNU activity levels, Epley, Brzycki, Wishnofsky,
CDC's weight-loss rate, the AASM/SRS sleep consensus, UCUM and an explicit `APP_RULE`
row pointing here.

Decisions inside the formulas:

- **BMR needs a sex at birth.** Mifflin-St Jeor has no neutral variant; the app does not
  invent an average, and a profile with "prefer not to say" gets no BMR rather than a
  wrong one. Nutrition and energy screens show the missing-input reason.
- **Navy body fat** uses the inch-fitted equations. The 495-form constants are the
  centimetre-fitted set; mixing the two understates body fat by a wide margin, which a
  test caught during B2.
- **1RM** is shown up to 15 reps only. Above that the equation is extrapolation, and the
  sheet says so.
- **Session volume** adds weight × reps over completed work sets; warm-up sets count as
  sets and reps but not as external load; bodyweight work contributes no kilograms.
- **Weight trend** is an EWMA with a seven-day half-life, applied per **elapsed day** so
  a weekly weigher gets the same smoothing as a daily one; the weekly rate compares the
  trend with its value seven days earlier. A family tie is broken by source id, never by
  the order rows happen to arrive in.
- **Goal ETA** is always a window: ±20 % of the central day count, at least seven days
  wide. It needs 14 days of data, refuses to project when the trend moves away from the
  goal, and divides the **last smoothed trend value** (not the latest scale reading) by
  the rate. The what-if path uses 7,700 kcal/kg. A goal rate is clamped to 0.25 %–1.0 %
  of body weight per week for loss and up to 0.5 % for gain; the outcome carries that
  allowed range separately from CDC's cited 0.5–1 kg/week loss range, so the sheet never
  attributes the app's clamp to the CDC. The safety floor is the greater of a
  1,200/1,500 kcal absolute floor and the person's BMR.
- **Readiness v1** is versioned and explained: sleep against the sleep need, resting
  heart rate and HRV against their 28-day baselines, load against the usual 48 hours
  (the caller passes how many days the load average covers). A supplied baseline shorter
  than seven days drops that input; a sleep need below an hour is an out-of-range input,
  not "not enough data";
  weights 0.35/0.25/0.20/0.20 renormalised over the inputs present. Fewer than two
  inputs or fewer than seven baseline days returns "not enough data", never a number.
  The mappings are documented in `Readiness` and change only with a version bump.
- **Recovery** is computed, never stored: under 24 h Recovering, 24–48 Easing, 48–72
  Ready, later Untrained. A "hard" session for that muscle — sets or tonnage above the
  median **session** over its own four weeks — shifts the whole scale by one 24-hour
  step, so a hard session is still Recovering at 30 h. A weekly total would make every
  session of a high-volume week look hard.
- **Plausibility ranges** are entry screens, not clinical judgements; a value outside
  one is flagged for the review inbox and never edited or diagnosed. They cite
  `APP_RULE`.

## 10. Corpus and benchmark

`CorpusGenerator` writes a deterministic five-year corpus through the real repositories:
hourly steps from one watch with a second source on some days, a daily weight with a
slow downward trend, nightly sleep, three meals with entries, three sessions a week with
sets, a tape measurement every 30 days and occasional vitals. It builds in about two seconds and
produces roughly 33,000 observations, 16,000 entries, 5,900 sets and 260 raw records.
The food table is seeded with 2,000 indexed rows so the search benchmark measures an
indexed range seek rather than a ten-row table. The corpus lacks no layer: nutrition
entries and vitals are included, so the engine's layer-4 rollups are exercised too. The
timeline view and its paged read include meals as well as observations, sessions,
entries, measurements and vitals. The query benchmark rebuilds every rollup through the
engine, warms each query and takes the best of five runs. The slowest query is the
timeline page at about 6 ms; every query is under the 16 ms budget, and the margin is
about 2.5x on the slowest. The Timeline page originally used the SQL `UNION` view with a global ORDER BY,
which measured 23.7 ms; it now reads each table through its own `happened_at` index and
merges 50 rows in Kotlin.

## 11. Detekt scoping

`gradle/detekt.yml` scopes three families the same way B1 scoped `@Composable`:
repositories, the rollup engine and the fixture corpus may exceed the function-count
rule (a repository is
one function per query; the corpus numbers are sample data), and test sources are
excluded from the length/magic-number rules that would flag every fixture value. Every
other module and file keeps the default rules, including the rollup engine, formulas and
screens. The scopes are visible in one file and are not a global disable.

## Known limits recorded here

- Sleep stage types are deduplicated one type at a time; the sleep screen (B12) groups
  the stages into a night and keeps one source. Until then a stage row is the most
  detailed source for that stage, not a claim that all stages came from one device.
- An export from a different schema version is refused with a clear message; the
  conversion matrix is B10's.
- Observations, sessions, meals, foods and the profile have no Trash entry in B2. No
  code path deletes an observation; the Timeline undo and the session/recipe cascade
  arrive with B3/B5, as recorded in the build log.
- Layer-4 corrections (an entry, a measurement, a vital, a set) are append-only in B2
  and have no edit trail yet; B6/B7 add the correction UI and the trail tracks
  `edited_at` on the linked observation.
- Nutrition's "user-edited wins over imported" and source-family collapse at entry level
  are B6's, because the entry carries `source_kind` and not a source id yet.
- Training volume, sets and duration are read from sets when the training screen needs
  them (B5); they are not stored as daily rollups in B2.
- Foreign keys are not declared; link resolution is validated by the write path and
  the export registry, and B10 adds the constraint matrix.
- An imported observation should carry a raw record; B8's importers own that rule and
  the corpus demonstrates the link on its batch-import days.
- `verifyMigrations` stays off until B10 commits real schema snapshots; the v1→v2 test
  reproduces v1 exactly as v2 minus the six indexes `1.sqm` adds.
- The settings field merge keeps the local value on a conflict until B13 adds per-field
  clocks and the conflict surface.
- SQLCipher's BSD notice must appear on B3's About screen; `NOTICE.md` records it and
  B3's exit test carries it.
- The encrypted store is opened by `AndroidDatabaseFactory`, which loads SQLCipher's
  native library, sets WAL, foreign keys and the integrity check. A future entry point
  must not construct a driver directly. The Android path relies on SQLiteOpenHelper's
  own downgrade refusal rather than `DatabaseOpener`'s explicit message; B10 unifies the
  two when it has a real upgrade matrix.
- Foreign keys are not declared. Dangling `source_id`, `observation_id`, `meal_id` and
  `session_id` links are not yet rejected by the database; the export registry and the
  write paths validate the links they own, and B10 adds the constraint matrix.
- An imported observation is expected to carry a raw record; the boundary cannot tell an
  import from a manual entry, so B8's importers own the enforcement and the corpus
  demonstrates the link on its batch-import days.
- Observations, sessions, meals and foods have no Trash entry yet. No code path deletes
  an observation; the Timeline undo and the session/recipe cascade are B3/B5 work.

## Consequences

- `:data` is the only place SQLDelight appears; `:core` can be compiled for the server
  unchanged when B13 lands.
- A new observation type needs a canonical unit, a plausibility range, a dedup policy and
  an aggregation, and the compiler refuses the build until all four exist.
- A new table needs a row in `ExportTables`, or the round-trip test will fail on row
  counts; that is the intended tripwire.
- SQLCipher's attribution must reach the About screen in B3; it is recorded in
  `NOTICE.md` now.
- The Android encrypted-database test runs on the owner's phone, not in JVM CI; CI
  proves the JVM driver, migrations, rollups, export and the benchmark.
