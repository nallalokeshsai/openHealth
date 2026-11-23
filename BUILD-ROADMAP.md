# openHealth — Build Roadmap

**Version:** 1.0 · 2026-11-22 — rewritten; supersedes 0.1 (UI-first, Hilt/Room, six tabs)
**Authority:** the **sequence and exit tests** of the build. It owns no values: stack is `Tech-Stack.md`, appearance is `DESIGN.md`, behaviour and scope are `pr.md`. Decisions are closed in `pr.md` §13 and `docs/adr/`.
**Run it with:** [`prompts.md`](prompts.md) — one prompt per phase below.
**Progress log:** `docs/build-log.md` (created in B0; every phase appends to it).

---

## 1. How the build is organised

- **Thin vertical order, correctness first.** Design system and data model before screens; logic as pure Kotlin with tests before UI that calls it; every phase ends with something runnable on an emulator or phone.
- **Phases are named B0–B15** so they are never confused with `pr.md` §9 product phases. B0–B10 deliver the MVP (`pr.md` §9 Phase 0 and 1, with nutrition in per ADR 0002); B11–B12 deliver depth the owner asked for early; B13–B15 are the ecosystem.
- **Scope rule.** A phase builds the `MVP`-tagged items of the `pr.md` sections it names, plus any `V1` item it names explicitly. Everything else waits. Never build `Later` items or the cut list (`pr.md` §14.5).
- **Gate at every exit.** (1) `./gradlew check` and `./gradlew :app-android:assembleDebug` pass. (2) Every screen touched passes `DESIGN.md` §14.5. (3) The exit tests below pass, run on an emulator or device where they are UI. (4) `docs/build-log.md` has an entry. (5) The iOS gate (`pr.md` §13.17) is reviewed: answer is recorded, default "not yet".
- **No time estimates.** Order and exit tests are the plan; calendar time depends on the owner's agent budget.
- **Stable interfaces between phases.** Each phase lists what it hands to the next so a fresh agent can resume from the repo and the log alone.

## 2. Phase map

| Phase | Name | Delivers | Needs |
|---|---|---|---|
| B0 | Foundation | Gradle build, toolchain, guards, CI file, licences, log | — |
| B1 | Design system | `OhTheme`, 19 components, icons, bars, gallery, screenshot tests | B0 |
| B2 | Core and data | Domain, units, provenance, five-layer schema, rollups, dedup, formulas, export/restore | B0 |
| B3 | Shell and onboarding | Splash, onboarding, five tabs, top bar, quick-add shell, settings, timeline, demo | B1, B2 |
| B4 | Body model and exercise data | Path renderer, fat layer, recovery and plan maps, exercise library | B1, B2 |
| B5 | Gym training | Routines, session, set logging, rest timer, progression, history, maps on Train | B3, B4 |
| B6 | Nutrition | Diary, foods, macros and micros, water, barcode, recipes | B3 |
| B7 | Body and vitals | Weight, measurements, composition, photos, vitals, dynamic body model data | B3, B4 |
| B8 | Activity sources | Step sensor, Health Connect, Garmin FIT import, BLE devices, dedup | B2, B3 |
| B9 | Today and Stats | Today dashboard, energy and goal ETA, readiness, Stats charts, tables | B5–B8 |
| B10 | Hardening and MVP release | A11y, light theme, RTL, performance, backup, trash, release build | B0–B9 |
| B11 | Yoga, cardio, GPS | Yoga flows, activities, GPS recording, FIT/GPX/TCX, Strava archive, maps | B10 |
| B12 | Sleep, clinical, reports | Sleep, recovery depth, medications, labs, doctor PDF, FHIR export | B10 |
| B13 | Sync and server | Change-log sync, Ktor server, Compose deployment | B10 |
| B14 | Wear OS and Connect IQ | Watch apps | B10, B13 |
| B15 | AI Coach and MCP | Proposals, BYOK, MCP server | B13 |

B11 and B12 are independent of each other. B13 onward only start when the owner asks.

---

## B0 — Foundation

**Goal:** an empty but correct multi-module project that builds and enforces the architecture.
**Read:** `Tech-Stack.md` §2–3, §11, §13–14, §16; `docs/adr/0002-closed-defaults.md`.

Deliver:
1. Gradle wrapper, `settings.gradle.kts`, `gradle/libs.versions.toml`, `build-logic/` convention plugins, modules `:core :data :sync :integrations :ui :app-android` with the §13 directory layout.
2. Verify every version floor in `Tech-Stack.md` §14 against its current release, pin them, and record in `docs/adr/0003-pinned-versions.md`.
3. `checkPortability` and `checkLicences` tasks (`Tech-Stack.md` §16), ktlint, Detekt, wired into `check`.
4. `LICENSE` (Apache-2.0), a note that `server/` will be AGPL-3.0 (built in B13), `NOTICE.md` with the MuscleMap MIT notice text copied from `references/openGym/NOTICE.md`.
5. `.gitignore` (build outputs, `local.properties`, keystores, `references/` except the two indexes), GitHub Actions workflow file (not pushed).
6. `app-android` shows one blank screen; `docs/build-log.md` started.

Exit: `./gradlew check` green; a deliberate `import android.os.Bundle` in `core` makes `check` fail, then is removed; the app installs and launches on the owner's phone.
Hands to B1/B2: a green build and pinned versions.
Ask the owner: nothing. The test target is the physical phone (`AGENTS.md` "Test device") and the working copy is on a plain local path, so neither B0 question stands.

## B1 — Design system

**Goal:** the closed token set and 19 components exist once, in code, and cannot drift.
**Read:** `DESIGN.md` §0–6, §10–12, Appendix A–B; `AGENTS.md` "Tokens" conflicts.

Deliver in `:ui`:
1. `OhColors`, `OhType`, `OhSpace`, `OhRadius`, `OhMotion` and `OhTheme` per Appendix B; dark canonical, light sibling, nine accent presets, `fat` and recovery tokens; `Color(0xFF…)` literals only; no `MaterialTheme.colorScheme` defaults, no dynamic colour, no ripple.
2. The typeface and the 11 type roles per §3 (check the font licence; bundle only a freely licensed face and record it in `NOTICE.md`).
3. The 19 components of §5 including the global top bar (§4.4), the five-tab bar with the raised Train disc (§5.15), sheet, dialog, toast and undo, timer bar, rows and inset groups, with every state in §6.
4. The core icon set (§10) drawn as vector paths (no emoji, none copied from openGym).
5. A debug-only component gallery screen in `:app-android` showing every component and state in dark, light and 200% font.
6. Roborazzi screenshot tests for each component and state in dark, light, 200% font and RTL; a unit test that computes every contrast pair in `DESIGN.md` §12.5 from the token values and fails below the stated ratio.

Exit: gallery renders; screenshot and contrast tests pass; a Detekt or unit check fails if a `Color(` literal or a raw `.dp` literal appears outside `ui/theme`.
Hands on: `OhTheme`, components, icons.

## B2 — Core and data

**Goal:** the five-layer model and every formula, pure Kotlin, heavily tested, before any feature screen.
**Read:** `pr.md` §2.2, §6.3–6.5 (formulas), §6.27–6.29, §7 (all), §12.1; `Tech-Stack.md` §3, §5–6.

Deliver:
1. `:core`: ids, `kotlinx-datetime` time (`happened_at`, `recorded_at`, local date, timezone), UCUM unit conversion with canonical storage units, provenance type, observation types (`pr.md` §7.2), source registry and priority (§7.6), merge-policy interface (§7.7), validators.
2. Formulas with tests: Mifflin-St Jeor BMR, US Navy body fat (male and female), BMI, FFMI, TDEE from activity level, MET-based exercise energy (Compendium values bundled with citation), estimated 1RM, session volume, trend smoothing (exponentially weighted weight trend), goal-rate and ETA window (`pr.md` §6.4), muscle recovery steps (`pr.md` §6.5), readiness v1 (`pr.md` §6.10), sleep debt. Each returns a value, its inputs and a citation key for the explanation sheet.
3. `:data`: SQLDelight 2.x schema for `raw_record`, `observation`, `daily_rollup`, domain tables (profile, exercise, routine, programme, session, set, food, meal, entry, measurement, vital, and the rest as later phases need them), `settings`, `tombstone`; migrations as files; WAL; integrity check on open; auto-backup before migration; every UI query indexed on `(local_date)` or `(type, happened_at)`.
4. Rollup engine: rebuild affected days from observations on any backfill; never patches; source-priority dedup (one source wins a day).
5. Trash: tombstones with session-length undo and 30-day purge (`pr.md` §6.29).
6. Platform seams as interfaces in `:core`: clock, keystore, files, notifications, health bus, step sensor, BLE, camera, PDF. SQLCipher driver and Keystore key in `:app-android` behind the seam.
7. Export and restore: versioned JSON export of every layer including raw records, passphrase-encrypted (Argon2id), round-trip tested on fixtures; CSV export of observations.
8. `tools/fixtures`: a generator for a seeded five-year corpus (steps, weight, meals, sessions) and a benchmark that proves no query above 16 ms.

Exit: property tests show rollups are identical whether built incrementally or from scratch, dedup never sums two sources, export then restore reproduces the database, backfilling a past day does not change any `recorded_at`; the corpus benchmark passes.
Hands on: repository interfaces, formulas, the seeded corpus.
Ask the owner: nothing; if an exercise or MET source licence is unclear, see `prompts.md` "When to ask".

## B3 — Shell and onboarding

**Goal:** first launch to Today, with navigation and settings in place and nothing hollow.
**Read:** `pr.md` §5, §6.1, §6.2, §6.21–6.23, §6.25, §6.27, §6.29; `DESIGN.md` §4, §5.15, §14.2 (Today, Settings, onboarding, quick add, timeline), §14.5.

Deliver:
1. Android 12 SplashScreen API splash, correct on API 28–35, no network wait; first launch routes to onboarding, every later launch to Today.
2. Onboarding with the three tiers of `pr.md` §6.1, one question per screen, skip always visible, units pre-detected, under-16 blocking, weight-neutral offer, module preset, demo-data row. Every tier-3 field stored with provenance and asked once later where used.
3. Five-tab shell with raised Train, global top bar (app name, date, quick add, settings), predictive back, sheet layer.
4. Settings (top-bar gear): profile, modules (13 toggles that hide surfaces and keep data), devices placeholder, data (export, restore, Trash), appearance (theme, accent preset), privacy (app lock with `BiometricPrompt`), about; destructive group with tombstone semantics.
5. Quick-add sheet shell with the six tiles routing to sheets that later phases fill; timeline (every stored event, filterable); demo mode with realistic sample data from `tools/fixtures`; weight-neutral switch that removes the numbers app-wide through one policy object.
6. Today skeleton in the §14.2 order with real empty states.

Exit (Maestro flows): fresh install to first logged note in under three minutes; relaunch shows splash then Today; turning a module off hides it and re-enabling restores its data; weight-neutral hides every weight, calorie and body-fat value present so far; export then restore from Settings round-trips.
Hands on: navigation, settings store, quick-add contract, profile repository.
Ask the owner: nothing (all onboarding content is specified).

## B4 — Body model and exercise data

**Goal:** the signature visual and the data it depends on.
**Read:** `DESIGN.md` §9, §11.3, §12.5, Appendix C; `pr.md` §6.5, §6.8; `references/openGym/frontend/src/lib/body-paths.js` and `body-geometry.js` (geometry only) and `references/openGym/NOTICE.md`.

Deliver:
1. Port the MuscleMap path geometry (MIT) for male and female, front and back, into a resource; keep the notice. Write our own renderer (`OhBodyMap`) as Compose `Path`s per Appendix B: base, 2.5 dp surface stroke, selection, mini variant, one accessibility node per muscle, never mirrored in RTL.
2. Layers: adipose layer (outside muscle shapes only, thickness from body fat % then tape measurements, clamped, `DESIGN.md` §9.1.1), recovery colouring (red, orange, green with hatch on Recovering), plan colouring (accent ramp).
3. Muscle taxonomy of whole muscles and the exercise library: at least 150 exercises with primary and secondary muscles, equipment, body-part tags, text instructions, alternatives, from a source whose licence the agent read and recorded in `NOTICE.md`; custom exercises.
4. The recovery engine wired to the `:core` formula, with a table alternative and one-sentence description for every map.
5. A debug harness to scrub body fat %, waist and hours-since-load and see the figure update.

Exit: screenshot tests for four views × dark/light × each map type; TalkBack announces "Forearms" once; fat layer never overlaps a recovery-coloured shape; a unit test proves recovery steps at 0, 23, 25, 47, 49, 73 hours for default thresholds.
Hands on: `OhBodyMap`, exercise repository, muscle taxonomy.
Ask the owner: only if no exercise data source with a clear licence can be found.

## B5 — Gym training

**Goal:** the best strength log: a set in under 2 seconds.
**Read:** `pr.md` §6.5 (MVP), §6.26, §6.29; `DESIGN.md` §14.2 (Train, Live session), §5.19; study `references/openGym`, `Liftosaur`, `FastNFitness` for ideas only (AGPL: reimplement).

Deliver:
1. Routines (weekday and A/B/C), folders, favourites; programmes (multi-week) if the scope allows, else cut as `pr.md` §6.5 says; each routine shows its muscle map and each programme its coverage map with sets per week.
2. Session flow: start today's plan, freestyle, log a past workout; the live session surface with set table, warm-ups, work, back-off, drop, failure, tempo; supersets and circuits; plate math; one equipment profile; bodyweight handling; notes.
3. Rest timer as an ongoing foreground-service notification with actions, sound and vibration; survives process death; a session persists through kill and resume.
4. Progression policies: the interface plus linear, double progression and off; deloads, stall detection, PR detection, estimated 1RM, per-exercise history.
5. Session summary with the loaded-muscles mini map; edit, move date, delete with undo and Trash.
6. Train root: week strip, today's plan with Start, Recovery/Plan segmented body map, history; recovery map live from logged sets.

Exit (Maestro plus timing): a set is logged in under 2 s with one hand (weight and reps prefilled); kill the app mid-session and resume without loss; a leg session turns quads red, the next simulated day orange, the third green; moving a past workout recomputes rollups without changing `recorded_at`.
Hands on: workout repository, set model, rest-timer service.
Ask the owner: nothing.

## B6 — Nutrition

**Goal:** a food log that takes under 5 seconds from barcode to logged.
**Read:** `pr.md` §6.3 (MVP), §6.4, §12.2; `DESIGN.md` §14.2 (Nutrition); study `OpenNutriTracker`, `Waistline`, `FoodYou`, `OpenFoodFactsServer` (reimplement).

Deliver:
1. Generic foods: build `tools/foodindex` that converts USDA FoodData Central (CC0) into a compact bundled SQLite table with macros and micronutrients; record source, version and licence in `NOTICE.md`.
2. Diary by meal, serving and portion math, custom foods, copy meal, recent and frequent, micronutrient totals against cited reference intakes, macro targets, water with container presets, edit and delete with undo.
3. Barcode: CameraX with the bundled ML Kit barcode model (no Play Services), Open Food Facts lookup on explicit action with a disclosure of what leaves the phone, local cache, ODbL attribution screen; manual entry when offline.
4. Recipes (create, scale, log a portion); meal templates; fasting window timer.
5. Energy card inputs exposed to B9: intake per day with provenance.

Exit: barcode to logged under 5 s on the warm path (timed E2E); a day's totals equal the sum of its entries exactly; the diary works in airplane mode; weight-neutral hides calories and energy; micronutrients show a cited reference.
Hands on: nutrition repository and food index.
Ask the owner: nothing.

## B7 — Body and vitals

**Goal:** the Vitals tab, with the dynamic body model driven by the owner's real data.
**Read:** `pr.md` §6.8 (MVP), §6.9 (MVP), §6.25, §12.1; `DESIGN.md` §7.5, §9.1.1–9.4, §14.2 (Vitals, Vitals detail).

Deliver:
1. Weight check-ins with the one-value-per-day rule, trend, weekly rate, goal line; body fat, lean mass, water, BMI, FFMI; Navy estimate from tape measurements tagged as an estimate; all ten tape sites plus wrist and ankle; custom measurements; method tags.
2. The dynamic body model on Vitals with measurement anchors, before/after scrub, "Fat layer" chip; hidden in weight-neutral mode.
3. Progress photos in app-private encrypted storage with the before/after slider.
4. Vitals MVP of `pr.md` §6.9: resting heart rate, blood pressure with the home-measurement protocol, temperature, SpO2, glucose manual entry, each shown against a **cited** range (record each citation in code and in `NOTICE.md`), orange at most, red never; vital detail with table alternative.
5. CSV import and export for weight and measurements.

Exit: changing waist or body fat visibly changes the figure and a before/after scrub works; a BP reading shows range and citation with no alarming wording; every number opens the provenance sheet; a wording test fails on "risk", "warning", "diagnos" and "abnormal" used as a verdict.
Hands on: body repository, vitals repository.
Ask the owner: nothing.

## B8 — Activity sources

**Goal:** steps and wearable data from the owner's Garmin and phone, deduplicated.
**Read:** `pr.md` §6.6 (MVP), §6.18 (MVP and the Garmin paths), §7.6; `Tech-Stack.md` §8; study `Stride`, `FitWren`, `Stepsy`, `Gadgetbridge`, `OpenScale` (ideas only).

Deliver:
1. Step sensor service (`TYPE_STEP_COUNTER`, reboot-safe, foreground service of the right type) with `ACTIVITY_RECOGNITION` requested at first use, battery-aware.
2. Health Connect reader through the health-bus seam: steps, heart rate, sleep, exercise sessions, weight, as granted; permission rationale screen; history backfill.
3. Garmin FIT file import (parser in `:integrations`, pure Kotlin, golden fixtures from `tools/fixtures`; check the FIT SDK terms and record them) producing activities, heart rate and steps with provenance family `garmin`.
4. Dedup: one source wins a day per type by the configured priority list; a source screen in Settings → Devices to reorder; never summed.
5. BLE: heart-rate strap (0x180D), weight scale (0x181D), BP cuff (0x1810) via the BLE seam with reconnect; a mid-workout disconnect loses nothing.
6. Devices screen: connected sources, last sync, what each provides, manual fallback.

Exit: the same day of steps from phone and Garmin yields one winner and the loser stays in raw; changing the priority list rebuilds rollups; a FIT file from `tools/fixtures` imports to the expected records; HR strap disconnect mid-session keeps all sets; passive step tracking is measured against the 2 % per day budget over 24 h on a device (report honestly if only an emulator is available).
Hands on: ingestion pipeline, devices repository.
Ask the owner: which scale and BP cuff models they own, if any (only changes test fixtures); whether a physical device is available for the battery test.

## B9 — Today and Stats

**Goal:** the daily dashboard and the statistics, every number interrogable.
**Read:** `pr.md` §6.2, §6.4, §6.10 (readiness, recovery), §6.16 (MVP); `DESIGN.md` §7, §8, §14.2 (Today, Stats).

Deliver:
1. Today in the §14.2 order: body card with recovery colours and the fat layer chip, readiness (v1 formula, explanation sheet), week strip and plan, energy card (in, out, net as ranges, ring, macros, water, goal-ETA line and what-if sheet), steps with source line, weight, due items.
2. Goal ETA per `pr.md` §6.4: window not date, 14-day minimum, what-if, floors, hidden in weight-neutral mode.
3. Stats: Overview, Body, Training, Sleep, Nutrition segments; hand-built Compose charts (line, bar, heatmap, sparkline, stat bars, macro ring) honouring `DESIGN.md` §8; each with a table alternative, a one-sentence description and a "how this is calculated" sheet with formula, inputs and citation; body and workout statistics (volume, per-muscle weekly sets, PRs, consistency, body-composition change).
4. Performance pass: warm Today under 300 ms from cache over the five-year corpus.

Exit: Macrobenchmark shows warm Today under 300 ms and cold start under 1.5 s (report the emulator number and flag it as not the reference device class); every chart has table, description and formula sheet (a test over the chart registry); ETA shows "not enough data yet" on day 13 and a window on day 14.
Hands on: dashboards, chart kit.
Ask the owner: nothing.

## B10 — Hardening and MVP release

**Goal:** the MVP is trustworthy enough to run the owner's whole day.
**Read:** `DESIGN.md` §11, §12, §14.5; `pr.md` §6.22, §6.23, §6.26, §6.28, §12.3–12.4; `Tech-Stack.md` §10–12.

Deliver:
1. Accessibility sweep: TalkBack on every screen, 200 % text, 44 dp targets, contrast, colour-blind check on the recovery map, reduced motion and transparency, high contrast, keyboard focus, RTL pass (logical start/end only, body map unmirrored).
2. Light theme verified on every screen; screenshot suite complete.
3. Performance budgets of `Tech-Stack.md` §12 on the five-year corpus; baseline profiles; R8.
4. Data safety: backup before migration, migration tests from every released schema version, encrypted export and restore verified end to end, corrupted-database recovery, duplicate review inbox (`pr.md` §6.28), Trash browser.
5. Security: SQLCipher verified, `allowBackup=false`, no cleartext traffic, network use limited to the explicit Open Food Facts action, permission audit against declared features, no analytics SDKs (dependency-list test).
6. Release: signed AAB and APK build with a keystore the owner creates, a Play-Services-free flavour for F-Droid, `fastlane` metadata, privacy policy text, store listing text avoiding any medical claim (`pr.md` §12.1, §12.3), reproducible-build notes.

Exit: the full Maestro suite and all budgets pass; a signed APK installs on the owner's phone; `docs/build-log.md` lists open issues honestly.
Ask the owner: create the signing keystore and keep its password themselves (never in the repo); whether to publish to Play, F-Droid or sideload only; privacy-policy hosting.

## B11 — Yoga, cardio, GPS

**Read:** `pr.md` §6.7, §6.11 (MVP and named V1 items), §6.18; `DESIGN.md` §8, §9; study `InnerBreeze`, `Feeel`, `OpenTracks`, `Tempo`, `GoldenCheetah`, `StatisticsForStrava`, `FitTrackee`.

Deliver: yoga pose and flow library (own text, own diagrams on the body map, no scraped media) with a flow player, timers and breathing; manual cardio logging; GPS recording in a `location` foreground service with pause and recovery; GPX/TCX/FIT import and GPX export; Strava bulk-export archive import (no live Strava API); MapLibre with PMTiles and offline packs; activity detail with splits and heart-rate zones; training load (fitness, fatigue, form) in Stats; muscle loading for yoga where a pose names muscles.
Exit: a recorded 30-minute GPS activity survives process death and exports valid GPX; a Strava archive imports with correct totals and one winner per day; yoga sessions feed recovery and the energy card.
Ask the owner: which tile source to bundle or download for maps (record the licence), and whether to allow a one-time map-pack download.

## B12 — Sleep, clinical, reports

**Read:** `pr.md` §6.9, §6.10, §6.16, §6.17, §6.25, §12.1; `DESIGN.md` §7.5, §8.7; study `HealthLog`, `AortaLink`, `UBPM`, `FastenHealth`, `OpenmHealthSchemas` (ideas; code-system licence notes in `pr.md` §12.2).

Deliver: sleep views and consistency, sleep debt, stage charts where a source provides them; medications and reminders, symptoms, labs with units and cited ranges; weekly review; light-only reports with source, citation and disclaimer; doctor PDF generated on device in under 30 s offline; FHIR R4 export with LOINC codes for the vitals the app records; Open mHealth export where it fits.
Exit: a report renders offline in under 30 s; the FHIR bundle validates against the R4 schema in a test; medication reminders fire locally with no push server.
Ask the owner: the report fields they want a clinician to see, if any.

## B13 — Sync and server (start only when the owner asks)

**Read:** `pr.md` §6.24, §7.7–7.9; `Tech-Stack.md` §6–7.
Deliver: `:sync` change log with idempotency keys and hybrid logical clock; per-entity merge policies; conflict surface; Ktor 3 server compiling the same core, PostgreSQL 16, Flyway, MinIO, Caddy, `docker compose up`; passkey sign-in; scoped tokens; property tests that random interleavings converge.
Exit: two emulators converge after offline edits; killing the connection mid-push loses nothing.
Ask the owner: where it will be hosted, the domain, who else may sign in.

## B14 — Wear OS and Connect IQ (start only when the owner asks)

Deliver a Wear OS companion (workout, sets, rest timer, heart rate) sharing `:core`, and a Connect IQ app for the owner's vívoactive 5 and other Garmin models with a per-device compatibility table. Needs B13 or a direct phone link.
Ask the owner: a Connect IQ developer setup and a physical watch for testing.

## B15 — AI Coach and MCP (start only when the owner asks)

**Read:** `pr.md` §6.15; `Tech-Stack.md` §9. Deliver the provider adapter, per-category consent, diff-card proposals validated by the same `:core` validators, snapshot and undo, and the scoped MCP server.
Ask the owner: provider and key handling.

---

## 3. Risks

| Risk | Mitigation |
|---|---|
| Body map is slow or ugly | Pre-flattened paths drawn once to a cached picture; fat and colour layers recomposed only on change; screenshot tests per view |
| Exercise or food data has an unclear licence | Only CC0 or public-domain sources; record each in `NOTICE.md`; ask the owner if none found |
| Health Connect lacks a data type from Garmin | FIT import is the reliable path; the Devices screen states what each source provides |
| Rollup bugs corrupt trends | Rollups always rebuildable; property tests compare incremental and full rebuild |
| Scope creep | Cut list closed; `pr.md` tags are the schedule; nutrition is in, so the MVP is long (ADR 0002) |
| Medical wording | Wording test in B7; no "medical grade" anywhere |
