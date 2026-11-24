# 0008 — Body model and exercise library (B4)

**Date:** 2026-11-24
**Phase:** B4 (`BUILD-ROADMAP.md` §B4)
**Status:** Accepted

The contract fixes the body map's appearance (`DESIGN.md` §9.1, §9.1.1, Appendix C) and
its behaviour (`pr.md` §6.5, §6.8). This record keeps the implementation choices that
went beyond either text, so a later phase does not have to rediscover them.

## Geometry

1. **Whole muscles, no sub-groups.** Only MuscleMap's `body-paths.js` path data is
   ported, as MIT permits. openGym's `body-geometry.js` splits the deltoids into heads and
   the upper back into lats; that split is deliberately **not** applied, because
   `DESIGN.md` §9.1 requires whole muscles and a sub-region would imply a precision the
   exercise data does not have. The dataset's `lats` and `middle back` therefore both map
   to `upper-back`, and `shoulders` to `deltoids`.
2. **A generated TSV in `res/raw`.** `tools/bodygeometry/convert.mjs` (Node, run by hand)
   turns the upstream module into `ui/src/main/res/raw/body_paths.tsv`: four viewBox rows
   and one row per path, tab-separated. It is plain text in the APK, parsed by
   `PathParser` once per process, so no JSON parser is shipped and the resource cannot
   drift from the notice. `BodyGeometry.parse` fails if a part key is neither a whole
   muscle nor one of the silhouette parts, so a taxonomy change breaks loudly.
3. **Loaded off the first frame.** `OhBodyMap` parses on an IO dispatcher and keeps its
   height while loading (`DESIGN.md` Appendix B). Tests and previews inject a parsed
   `BodyGeometry`.

## The adipose layer

4. **A single rim, not one band per limb.** *(Superseded by `0009`: the rim is a raster
   mask now, because Skia's path operators fail on tape-scaled input. The rule itself —
   one rim, never under a muscle — is unchanged.)* Stroking every region separately draws a gold
   seam between the arm and the torso. The renderer instead builds one rim from the union
   of every muscle, then adds an outward ring only where a region is thicker than the
   thinnest one, and cuts the result against the muscle union. The band is therefore
   *only* outside the muscle shapes by construction, and a unit test proves it against
   real Skia paths.
5. **A pure, testable thickness model.** `AdiposeModel` in `:core` returns a per-region
   fraction of the figure height (base from body-fat fraction × 0.08, clamped 0.4–3.5 %,
   region factors, and a self-relative tape scaling that clamps 0.75–1.3). It never knows
   about dp or pixels; the renderer multiplies by the drawn height.
6. **Body fat first, Navy estimate second, nothing third.** The recorded percentage wins;
   otherwise the cited US Navy tape estimate; otherwise the layer is not drawn. A single
   tape measurement changes nothing, because there is no second site to compare it with,
   and no population reference circumference is invented.
7. **Unknown sex at birth draws the male figure.** `pr.md` §6.1 lets a person prefer not
   to say, and the map still needs a body. The figure is a schematic, not a likeness, so
   the default is recorded here rather than hidden in a null branch.

## Recovery

8. **Computed, never stored.** `RecoveryMap` in `:core` takes completed sets, groups them
   by session and muscle, and asks B2's `MuscleRecovery` for the step. Primary muscles
   count 1, secondary 0.5; warm-ups are not load; the window is the last 28 days.
9. **One session is not its own baseline.** The hard-session comparison uses the median of
   the *earlier* sessions for that muscle; with one session there is no median to beat, so
   nothing is flagged hard on a first workout. The median is taken per session, as B2's
   `HardSession` documents, never from a weekly total.

## Exercise library

10. **Free Exercise DB (Unlicense) is the source.** Its JSON is vendored at
    `tools/exerciseindex/upstream/exercises.json`; the tool maps 17 dataset muscles to the
    19 whole muscles, derives the body-part tag from the primary muscles, and writes a
    TSV resource in `:core`. Only text crosses: the dataset's images are Everkinetic
    material with separate share-alike terms and are not used (`NOTICE.md`).
11. **Four drawn muscles have no dataset name.** The obliques, serratus, hip flexors and
    tibialis would otherwise be shapes that can never light up. The tool adds them as
    secondary load by exercise-name idiom (twist → obliques, leg raise/sit-up → hip
    flexors, push-up/dip/pullover → serratus, tibialis/toe raise → tibialis). This is our
    mapping, recorded in the tool and here, not the dataset's.
12. **Stable ids, seeded once.** Bundled exercises carry `fex:` ids. The library is written
    into the encrypted store on first open and gated by
    `SettingKeys.EXERCISE_CATALOGUE` = `ExerciseCatalogue.VERSION`; re-seeding is an
    upsert, and a custom exercise is a separate `is_custom` row. 871 rows in one
    transaction measured under a second on the owner's phone.
13. **Schema v5.** The library's level, mechanic and force are stored (three nullable
    columns, `4.sqm`), because the detail screen needs them and they cannot be derived.
    The body part stays derived from the primary muscles. The export registry lists the
    new columns, and `DatabaseTest` proves a v4 database migrates with its rows intact.

## Presentation

14. **One accessibility node per muscle, not one per view.** The forearm is drawn on the
    front and the back; TalkBack must hear "Forearms" once (`DESIGN.md` §9.1). The node is
    owned by the first view that draws the muscle, while the canvas hit-tests every drawn
    shape, so a touch on either view still selects it.
15. **Map types are base, recovery and plan.** The strength view (`DESIGN.md` §9.1) needs
    a per-muscle strength figure, which is B5's 1RM work; it is not faked here.
16. **The Today hero card replaces B3's empty state** and carries the chip row, the
    last-trained line, the fat provenance, the legend, the table alternative and a muscle
    sheet. Weight-neutral mode passes no adipose layer at all rather than hiding a value.
17. **The debug harness is a debug-only activity** (`BodyLabActivity`) registered in the
    debug manifest, like the B1 gallery; it scrubs body fat, waist and hours and shows the
    library count, and it cannot ship in a release build.

## Review fixes (2026-11-24)

Three independent reviewers read the phase; these are the decisions the fixes changed.

- **The band is built from the closed silhouette.** The muscle paths do not touch, so the
  raw union's outline runs down every seam; a first version drew a gold line between two
  abs. A 24-unit closing ring (`AdiposeGeometry.SEAM_UNITS`) fills the seams into the
  silhouette before the band is cut, and the unit test asserts the band never enters a
  seam. `DESIGN.md` §9.1.1 records the ring.
- **The light adipose layer is opaque.** `fat` at 55 % over a white card measured 1.97:1
  and failed §12.5's 3:1 graphic bar. The light theme draws it at 100 % (3.79:1 on
  `surface`, 3.39:1 on `bg`); dark stays 55 % (3.96:1). `OhColors.fatLayerAlpha` carries
  it and `OhContrastTest` measures all three bases.
- **"Recovered" is not "never loaded".** `RecoveryStep.UNTRAINED` means both, so the card,
  the table and the muscle sheet now derive their word from `hoursSinceLoad`: a trained
  muscle past 72 h says "Recovered", and the "no sessions" empty state appears only when
  nothing was loaded in the four-week window.
- **An older export restores; a newer one is refused.** Every schema bump would have
  invalidated every backup. `ExportService` now accepts `schemaVersion <= current`; a
  missing column (B4's case) takes its default. `OlderSchemaRestoreTest` proves a v4
  bundle loads into v5 with its rows intact.
- **The library seed is idempotent and non-destructive.** A re-seed bumps `revision`
  instead of resetting it and keeps an existing `met_activity` (`coalesce`). Migration
  `4.sqm` also normalises the muscle names B3's demo wrote (`back`, `shoulders`,
  `glutes`), which would otherwise light up nothing.
- **Five upstream exercises are skipped, and said so.** The tool prints the count
  (871 of 876); the five without instructions are Push Press, Side Bridge, Side
  Jackknife, Iron Cross and One-Arm Kettlebell Swings. `ExerciseIndexTest` asserts the
  shipped TSV is exactly what the vendored dataset generates and carries no image path.
- **Instrumented tests own their storage.** `AppContainer` takes a database name and a
  vault preference name; `ExerciseLibraryDeviceTest` and `ShellExitTest` use their own and
  delete only those, so a run can no longer wipe the owner's record.
- **A 44 dp touch target, not just a 44 dp node.** A tap that misses every path falls back
  to the nearest muscle within half a touch target; front and back taps are asserted in
  Robolectric. Two thin muscles (serratus, hip flexors) are still fully covered by their
  neighbours in the platform's *visible* node dump; Compose has all 19 nodes and the
  table alternative lists all 19. A dedicated accessibility pass can lay the nodes out
  without overlap.
- **Unknown sex at birth is stated.** The figure stays the schematic male body, and the
  card now says that the profile does not state a sex and the figure is generic.
