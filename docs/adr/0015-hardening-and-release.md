# 0015 — Hardening and MVP release

**Date:** 2026-11-26
**Phase:** B10 (`BUILD-ROADMAP.md` §B10)
**Status:** Closed

## Context

B10 turns the built app into a release: an accessibility contract that works with a
screen reader and at 200 % text, recoverable data, a provable security posture, and a
signed artefact the owner installs. Several of those needed a decision that outlives the
phase.

## Decisions

### 1. The three accessibility options are app preferences, mapped onto existing tokens

`DESIGN.md` §12.4 already listed reduced motion, transparency and contrast as themable.
They became three switches in Settings → Appearance and are recorded in `DESIGN.md`
§12.4.1:

- high contrast: `label-2`/`label-dim` take `label`, `sep-op` takes `sep`;
- reduce transparency: the bar, toast and timer fills become opaque `bg-el`/`surface-2`;
- reduce motion: every `OhMotion` token is instant. Android's "Remove animations" is
  still honoured, and Material's own sheet animation is outside the token, so the copy
  does not claim it stops.

**Why not branch on platform settings:** Android does not expose all three consistently
across versions, and a preference works on every device. No colour, type role, spacing
value or component was added; the options re-point existing tokens.

### 2. A corrupt database is restored from the pre-migration snapshot, and nothing is deleted

`AndroidDatabaseFactory.open` catches the two shapes that mean "this file is not a usable
database", restores the newest snapshot whose name belongs to that database, reopens, and
records the date in Settings. The damaged file and its sidecars are renamed
`.corrupt-<stamp>` rather than deleted, and the snapshot is copied to a temporary file and
moved into place atomically so a failed copy leaves the record untouched.

**Why automatic:** the alternative is a record that cannot open at all, and the snapshot
exists for exactly this. **Why scoped to the database name:** the backup directory is
shared, and a test or benchmark database must never be used to restore the real record.

### 3. The review inbox asks, and never edits by itself

`:core` `ReviewInbox` builds three kinds of item from the last 30 days: two sources
disagreeing on a day, a reading outside the plausibility screen, and a probable
duplicate. The screen's answers are keep (the day's current value), leave out of trends
(the reading stays, the rollup ignores it), merge (the extra duplicate is tombstoned for
30 days), edit (the timeline) and ignore. Dismissals are a capped list of keys in layer 5;
no health value is stored there.

**Rules:** a duplicate is only asked about for a type that is not `INSTANT` (a heart-rate
strap writes one sample a second); one item draws at most five readings while merging
every extra; a module that is off hides its questions; weight-neutral mode hides weight,
body fat and energy rows entirely rather than showing a number.

### 4. Release signing comes from the environment, and an absent keystore builds unsigned

`OH_KEYSTORE`, `OH_KEYSTORE_PASSWORD`, `OH_KEY_ALIAS` and `OH_KEY_PASSWORD` reach Gradle
from `.env.local` (gitignored). Without them the release variant builds unsigned; it
never falls back to the debug key, so an unsigned artefact cannot be mistaken for a
shippable one. The owner creates and holds the keystore (`docs/reproducible-builds.md`).

### 5. The benchmark variant is unminified

`BaselineProfileGenerator` reads the running app's class names. R8 renames them into a
mapping only that build shares, so a profile generated from a minified build is useless
to the next one. The benchmark variant therefore keeps `isMinifyEnabled = false`; it still
runs the release dependency graph, and the committed profile carries real names.

### 6. The telemetry gate is a deny list separate from the licence allowlist

`checkNoTelemetrySdks` fails on a resolved analytics, crash-reporting or attribution SDK
family, on every module that applies a convention plugin. It is deliberately not an
allowlist row: a permissively licensed telemetry SDK must not be able to enter by editing
`gradle/licences/allowed.tsv`. The two `com.google.*` wildcards were removed from that
list and replaced with five named artifacts.

### 7. The design-token gate scans production source only

`checkDesignTokens` now excludes `src/test` and `src/androidTest`, because a test that
reasons about colour or geometry needs a literal to do it. The gate's purpose is the
shipping tokens; the goldens still render the real theme.

## Consequences

- The accessibility options are one more thing a screen never branches on: they change
  only the token values.
- A recovered database is visible in Settings as a dated line, and the damaged bytes
  remain on the device.
- A review answer is final for that item until the readings change; "ignore" is not
  undoable from the inbox (the reading itself is untouched, and the timeline remains the
  place to correct it).
- A release build without the environment variables is unsigned rather than unshippable-
  looking; the CI job does not build release, so it never needed the secrets.
- The profile must be regenerated on a device when startup work changes materially; the
  procedure is in the generator's KDoc.

## Alternatives rejected

- **Branching on the platform's high-contrast and transparency settings.** Inconsistent
  across versions; a preference works everywhere and is testable.
- **Deleting the corrupt database and starting fresh.** Contradicts §6.26's zero-tolerance
  data loss and §6.29's tombstone rule.
- **Fixing contrast by adding high-contrast colours.** That would add a token; the closed
  palette already has a stronger ink.
- **Signing releases with the debug key when no keystore is configured.** It would make an
  unshippable artefact look shippable.
- **A SQLDelight migration verifier.** It needs a schema snapshot per released version;
  the project has only the current one, so the per-version upgrade tests (v1–v8) are the
  gate instead.
