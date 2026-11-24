# ADR 0007 — Shell, onboarding and the quick-add writes

**Date:** 2026-11-23
**Status:** Accepted
**Phase:** B3 (`BUILD-ROADMAP.md` §B3)

## Context

B3 is the first phase with a running app: a splash, onboarding, the five-tab shell,
Settings, the timeline, quick add and the Today skeleton. The contract fixes the
behaviour and the look; it does not fix how the shell is wired, what a note is stored
as, or how the demo corpus coexists with a real profile. This ADR records those
choices.

## Decisions

1. **Screens live in `:ui`, the state holder lives in `:app-android`.**
   `:ui` gained `screens/` composables that take an immutable state object and
   callbacks; `AppViewModel` in the app reads the repositories and builds those
   objects. `:ui` therefore depends on `:core` for the pure policies (modules,
   display policy, age rules), never on `:data` or Android storage. This keeps every
   screen renderable in a Roborazzi test and leaves the design-token gate over it.

2. **One state holder for the shell, not one per screen.** The five roots, Settings,
   the timeline and the quick-add sheet share one `StateFlow` and rebuild their models
   from the database after every write. It is 80 small functions, so Detekt's
   `LargeClass` and `TooManyFunctions` are scoped to that file only (and to
   `screens/**`, where one file naturally holds several composables). Every other
   rule stays on. Splitting into per-screen holders is the next structural step, and
   is recorded as an open issue in the build log.

3. **Notes are a layer-4 table, schema v4.** `pr.md` §6.27 makes a note a first-class
   timeline event and quick add a promise; B2's schema had no note table. `note`
   carries the same provenance as any other value (`source_id`, `happened_at`,
   `recorded_at`, local date, zone, idempotency key, raw-record ref), a `3.sqm`
   migration creates it, the timeline view unions it, the export registry includes it
   and `TrashService` can delete and undo it. It has no rollup: a note is not a
   measurement.

4. **Undo covers observations too.** Quick add water writes an `observation`, and the
   contract says undo is on every write. `TrashEntity.OBSERVATION` stores the whole
   observation row as the tombstone payload and restores it unchanged. The toast is the
   shortcut; the 30-day Trash and the tombstone marker are the promise.

5. **Koin builds the container; the database opens lazily on IO.** `OpenHealthApp`
   starts Koin and constructs `AppContainer`, which is cheap: SQLCipher, the integrity
   check and the manual-source seed happen on the first access from
   `AppViewModel.rebuild()`, on `Dispatchers.IO`, after the first frame. This is what
   keeps the cold start near the B0 number instead of adding the database to the
   launch path.

6. **App lock uses the platform `BiometricPrompt`, not the AndroidX biometric
   library.** `minSdk` is 28, so the framework class exists on every supported device;
   it needs no fragment dependency and no new library. Device credential is allowed
   (`setDeviceCredentialAllowed` on 29, `Authenticators` on 30+). On API 28 the
   framework prompt has no device-credential path, so the lock is unavailable there
   rather than offering a switch that could never succeed. From API 29 availability is
   `KeyguardManager.isDeviceSecure`; the app never sees the biometric. It re-arms on
   `ON_STOP`, not only on the first load.

7. **The splash is the Android 12 SplashScreen API.** `core-splashscreen` 1.2.0 adds
   the compat call for 28–31; API 31+ draws the system splash from the starting theme,
   and 28–30 draw the same centred mark from `windowBackground` so no white frame
   flashes. The activity theme posts to `Theme.OpenHealth`. Nothing waits on the
   network.

8. **Demo mode uses a dedicated demo profile.** "Try with sample data" writes 90 days
   through `CorpusGenerator` with `writeProfile = true` and a dedicated demo profile
   id, so onboarding is skipped and the sample data is labelled on Today. It never
   mixes fabricated device provenance into a real profile; there is no "add sample
   data" action in Settings.

9. **Quick-add writes choose the honest storage shape.** Each water glass is its own
   observation with `end = start`, one part of the manual source; the rollup sums that
   source's parts, so a backdated glass adds and a deleted glass subtracts. A source
   that reports a daily total replaces only **its own** intervals, never another
   source's parts. A weight is a `WEIGHT` observation (`LAST` wins), not a layer-4
   measurement, so a second weigh-in replaces the day instead of being skipped by the
   rollup. A body-fat entry is the same observation shape. A note is the note table.

10. **Onboarding marks progress, not a score.** `intake_tier` is the highest tier the
    person reached, each skipped field is simply absent, and Skip is visible on every
    question. It cannot advance a required tier-1 field; it states why. Units are
    detected from the locale and changing them converts the typed values rather than
    reinterpreting them.

11. **Backdating is a seven-day strip plus an hour stepper in B3.** `pr.md` §6.27
    requires a past date and time for every log; a full calendar picker is not a B3
    deliverable and a Material date dialog would break the closed theme. The strip and
    stepper write an honest `happened_at` while `recorded_at` stays now, and the clamp
    never puts a time in the future. The fuller picker belongs with the logging screens
    of B5–B7 and is logged as a placeholder.

12. **The water droplet joins the object set.** Quick add needs a glyph for water and
    `DESIGN.md` §10.2 had none. It is added in a dated edit as a geometric 24 dp icon;
    no colour, type role, spacing value or component is added.

13. **Export and restore use the Storage Access Framework behind a `DocumentStore`
    seam.** The app never asks for a storage permission, the file is chosen by the
    person, and the encrypted bytes go straight to that stream. The passphrase sheet
    lives in Settings, the Argon2id/AES-GCM container is B2's, and the instrumented
    round-trip drives the same `ExportService` through an in-memory store. The system
    picker itself is exercised by hand, not by a test.

14. **Settings is a pushed page, not a root with the bars.** The inventory calls it a
    root, but `DESIGN.md` §4.4 lets a screen skip parts; the recipe gives it a profile
    row and six groups, and a pushed page with its own back header keeps the tab bar
    for the five data roots. The top bar's date block opens the timeline, so the
    timeline needs no invented glyph and Today keeps at most one trailing control.

15. **Restore takes an encrypted local snapshot first.** `pr.md` §6.29 treats a
    restore as a bulk import. The app exports the current store to
    `files/pre-restore.ohe` with a Keystore-held passphrase before it replaces
    anything, and Settings offers Undo last restore. The system picker is the only
    part that is manual.

16. **Detekt is scoped to the shell state holder and the screen files.** `LargeClass`
    and `TooManyFunctions` are excluded for `ui/AppViewModel.kt` and `screens/**`;
    every other rule stays on. This is recorded as the structural debt it is, not a
    precedent for other files.

## Consequences

- A new screen adds a state object and a composable in `:ui`; the app maps repositories
  to it. No screen reads `SettingsRepository` directly, and no screen reads the
  weight-neutral switch: it asks `DisplayPolicy`.
- Schema v4 needs the About screen's SQLCipher notice (it is there), the export
  registry (it is there) and the migration to keep the B2 device assertion green (it
  does, `connectedDebugAndroidTest`).
- The cold start is a debug measurement until B14's macrobenchmark; the release
  number is not claimed here.
- The `AppViewModel` exception in `gradle/detekt.yml` is deliberate and narrow. Do not
  widen it to other rules or other app files without a new ADR entry.
