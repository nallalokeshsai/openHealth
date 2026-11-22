# openHealth — Build prompts

**Version:** 1.1 · 2026-11-22 (Standing rules amended: the owner allows a push when a session is asked to push, `docs/build-log.md` B0)
**What this is:** one copy-paste prompt per build phase of [`BUILD-ROADMAP.md`](BUILD-ROADMAP.md). Paste a prompt into a fresh agent session opened in this repository, answer the few questions it asks up front, and let it run to the phase exit.
**What it is not:** a spec. The specs are `pr.md` (behaviour), `DESIGN.md` (appearance), `Tech-Stack.md` (build) and the phase section of `BUILD-ROADMAP.md`. The prompts only point at them.

## How to use

**With OpenCode (recommended):** open OpenCode in this folder and type `/build-phase 3` (or `B3`), or `/build-phase next`. The command lives in `.opencode/commands/build-phase.md`, loads `AGENTS.md` and this file, resolves the phase and runs it with the Standing rules. `opencode.json` lets the agent edit and run commands without prompting, denies `git push` and blind `git add -A`, and asks before destructive deletes. The copy-paste prompts below are the same instructions for any other tool.

**Without it:**

1. Run phases **in order**, one per session. B0 first. B13 to B15 only when you want them.
2. Easiest path: paste the **Next phase** prompt every time. It reads `docs/build-log.md`, finds the first incomplete phase and runs it.
3. Or paste the specific phase prompt (B0 … B15 below).
4. The agent asks you its questions **once, at the start**, using a question dialog with a recommended answer first. Answer them and leave it. It will not stop between milestones.
5. When it finishes it tells you what passed, what did not, and anything only you can do. If a session ends early, paste the same prompt again: it resumes from the log.
6. Start a **new session for every phase**. The repo and `docs/build-log.md` carry the memory.

What you will be asked, across the whole build (everything else is already decided in `docs/adr/0002-closed-defaults.md`):

| When | Question |
|---|---|
| B0 | Emulator or your phone? Is the repo allowed to move out of OneDrive (recommended) or should OneDrive be paused for build folders? |
| B8 | Which scale and BP cuff you own, if any; a physical device for the battery test? |
| B10 | Create the signing keystore yourself; Play, F-Droid or sideload; privacy-policy hosting |
| B11 | Map tile source, one-time map-pack download allowed? |
| B12 | Report fields for a clinician |
| B13–B15 | Hosting, domain, accounts; watch developer setup; AI provider and key handling |

---

## Standing rules (every prompt below says "follow the Standing rules")

**Orient first.**
1. Read `AGENTS.md`, then `docs/build-log.md` (absent only in B0), then the phase section in `BUILD-ROADMAP.md` and every document that section names under **Read**. Read the sections, not just the headings.
2. **Precondition:** the previous phase must have a "Completed" entry in `docs/build-log.md` and a green `./gradlew check`. If not, say so and do that phase first (or ask the owner to paste its prompt) rather than building on sand.
3. **Resume:** if the log shows this phase in progress, continue from its last milestone; do not redo finished work.

**Authority you have been given by running this prompt.** Create and edit any file in this repository outside `references/`; run Gradle, `adb`, the emulator and `sdkmanager`/`avdmanager`; install the debug app on an attached emulator or device; create a branch `build/<phase>-<slug>` and **commit locally** at each milestone (stage explicit paths, never a blind `git add -A`, never anything under `references/`, never a keystore, `local.properties` or secret; follow the commit attribution rule in effect). **Push only when the owner asks for it in that session** (owner decision, 2026-11-22: `git push` is allowed in `opencode.json`); never publish, upload or send anything else off the machine, and never force-push or rewrite published history. Network use is limited to downloading build dependencies, SDK components and the open data sources a phase names.

**When to ask the owner** (use the question dialog, batch every question into one dialog right after orienting, put the recommended option first, give each question enough context to stand alone):
- Anything the phase section lists under "Ask the owner".
- A data source, font, library or asset whose licence you cannot establish after reading its licence file.
- Hardware, credentials, accounts or money.
- A change to a closed decision, a design token, a component, or the cut list. Amend the docs with a dated edit only after the owner agrees.
- A destructive or outward action: push, publish, deleting outside the project, moving the repository.
- You are blocked after two real attempts with different approaches.

**Never ask** about anything already written in the contract documents, naming, file layout, library choices inside the contract, code style, whether to continue, or whether a milestone is "good enough". Decide, record it, and go on. A decision with consequences gets a short `docs/adr/NNNN-slug.md` (next free number).

**Invariants.** The invariants in `AGENTS.md` fail the task if broken: provenance on every value, `happened_at` ≠ `recorded_at`, five layers with rebuildable rollups, one source wins a day, local-first, not a medical device, no telemetry, portability rules for `core/ data/ sync/ integrations/`, tombstone undo, weight-neutral mode, every chart has a table and a sentence, closed design tokens, closed cut list. No `android.*` in the pure modules. No Hilt, Room or Material defaults. No GPL/AGPL code or dependency in the client; references are read, not copied (only the MIT MuscleMap geometry may cross, with its notice).

**Quality.**
- Write the tests the phase's exit section demands, and run them. Run `./gradlew check` and `./gradlew :app-android:assembleDebug` after every milestone. Run UI and instrumented tests on an emulator or device when the exit section says so; if none is available, ask once, then report the gap honestly.
- Never mark something done that you did not run. Placeholders and stubs are allowed only when the roadmap says a later phase fills them, and each is listed in the log.
- Report failures with their output. Do not weaken a test, lower a budget or skip a gate to get green. If a budget cannot be met, say so with the measured number.
- Screens follow `DESIGN.md` exactly (tokens from `OhTheme`, components from `:ui`, review gate §14.5). If a value is missing, add it to `DESIGN.md` Appendix A with a dated edit first, measure its contrast, then use it. UI copy follows `DESIGN.md` §11.7.
- Before the exit, spawn a fresh reviewer agent with only the diff and `AGENTS.md` invariants, and fix what it confirms.

**Work method.** Make a task list from the phase section and work through it. Use parallel subagents for independent modules when it saves time; keep their briefs self-contained. Long Gradle runs go in the background. The machine is Windows 11 with PowerShell and Git Bash; JDK 21, Android SDK (`%LOCALAPPDATA%\Android\Sdk`), Gradle 9.x, git and adb are installed. Do not build iOS, web, desktop or watchOS.

**Keep the docs true.** Anything you decide that changes a contract document is edited in that document, with its version/revision line updated, in the same commit. Fix the document text you touch; flag, do not sweep, unrelated staleness.

**Log and finish.** Append to `docs/build-log.md` at every milestone: date, phase, what was done, commit, what was run and its result, placeholders left, open issues. At the exit: tick every exit test in the log with its evidence, record the iOS-gate review ("not yet"), update the "Current state" section of `AGENTS.md`, and end with a message of at most ten lines: phase, pass/fail per exit test, what you need from the owner. Stop there; do not start the next phase.

---

## Next phase (paste this one every time)

```text
You are building openHealth, an Android personal health app, in the repository you are opened in. Read AGENTS.md first, then docs/build-log.md (if it does not exist, the phase is B0). Find the first phase in BUILD-ROADMAP.md section 2 (B0 to B12) whose log entry is not "Completed" and run exactly that phase, and no other. Do not start B13, B14 or B15 unless I name them. Follow the Standing rules in prompts.md exactly: orient, ask me every question you need in one batched question dialog before building, then work autonomously to the phase exit tests without stopping, verify by running everything, commit locally, log, and finish with a short report. If the previous phase is not complete or its checks are red, fix that first.
```

---

## B0 — Foundation

```text
Build phase B0 (Foundation) of openHealth. Read AGENTS.md, prompts.md "Standing rules" and BUILD-ROADMAP.md section B0, then every document it names. Follow the Standing rules exactly.

Specific to B0:
- The repository path contains OneDrive. Gradle build outputs and a 5 GB references folder inside a synced folder cause locked files, slow builds and long-path failures on Windows. After orienting, ask me one batched question dialog: (1) emulator or my physical phone (run `adb devices` and an `emulator -list-avds` first and tell me what you found; create an AVD with API 35 if none exists and I choose the emulator); (2) recommended: move the working copy to C:\dev\openHealth with git (you may do this only if I say yes), otherwise pause OneDrive sync for build folders and configure Gradle build directories outside the synced tree.
- Verify every version in Tech-Stack.md section 14 against its current release before pinning, and make sure the Gradle wrapper, AGP, Kotlin, KSP/SQLDelight and Compose BOM versions are mutually compatible. Record the result in docs/adr/0003-pinned-versions.md.
- Prove the guards work: add and then remove a forbidden import and a forbidden dependency and show that `check` fails and then passes.
- Create docs/build-log.md with the first entry. Do not write any feature code.
Finish per the Standing rules.
```

## B1 — Design system

```text
Build phase B1 (Design system) of openHealth. Read AGENTS.md, prompts.md "Standing rules" and BUILD-ROADMAP.md section B1, then DESIGN.md in full (sections 0 to 6, 10 to 12 and Appendices A and B are the spec; sections 13 and 14.5 are the departures and the review gate). Follow the Standing rules exactly.

Specific to B1:
- Appendix A is canonical. Use Color(0xFFRRGGBB). Ignore COMPOSE-GUIDE.md for every value and for structure.
- One OhTheme with OhColors, OhType, OhSpace, OhRadius, OhMotion. No MaterialTheme.colorScheme defaults, no dynamic colour, no ripple, no card borders, no emoji icons.
- Build all 19 components with every state, the five-tab bar with the raised Train disc, and the global top bar (name, date, quick add, settings).
- Fonts: DESIGN.md section 3.1 names the family; read its licence and bundle only if it is freely licensed, otherwise pick the closest freely licensed face and tell me in the final report. Record in NOTICE.md.
- The contrast unit test must compute every pair of DESIGN.md section 12.5 from the token values, including fat and the recovery tokens.
- Add a Detekt rule or a unit check that fails on Color( literals and raw dp literals outside the theme package.
Finish per the Standing rules.
```

## B2 — Core and data

```text
Build phase B2 (Core and data) of openHealth. Read AGENTS.md, prompts.md "Standing rules" and BUILD-ROADMAP.md section B2, then pr.md sections 2.2, 6.3 to 6.5, 6.10, 6.27 to 6.29, all of section 7 and 12.1, and Tech-Stack.md sections 3, 5 and 6. Follow the Standing rules exactly.

Specific to B2:
- Pure Kotlin only in :core and :data. kotlinx-datetime, kotlinx.serialization, kotlinx.coroutines, SQLDelight 2.x. JVM tests use a SQLite JDBC driver; the SQLCipher Android driver and Keystore key live in :app-android behind a seam declared in :core.
- Every formula returns the value, its inputs and a citation key. Write each citation (Mifflin-St Jeor, US Navy body fat, Compendium of Physical Activities, Epley or Brzycki for 1RM) into a table in :core and into NOTICE.md where licensing requires.
- Rollups are always rebuilt from observations. Write property tests (Kotest) that compare incremental and full rebuilds on random event orders, and that dedup never sums two sources.
- Argon2id for the export passphrase: choose a permissively licensed implementation, verify its licence, record an ADR.
- Generate the five-year seeded corpus in tools/fixtures and a benchmark that fails any UI query over 16 ms.
Finish per the Standing rules.
```

## B3 — Shell and onboarding

```text
Build phase B3 (Shell and onboarding) of openHealth. Read AGENTS.md, prompts.md "Standing rules" and BUILD-ROADMAP.md section B3, then pr.md sections 5, 6.1, 6.2, 6.21 to 6.23, 6.25, 6.27, 6.29 and DESIGN.md sections 4, 5.15 and 14.1 to 14.5. Follow the Standing rules exactly.

Specific to B3:
- Onboarding follows pr.md section 6.1 tiers exactly, one question per screen, Skip always visible. Under 16 is blocked with a plain message; 16 and 17 never receive a deficit target or weight-loss goal.
- Splash uses the Android 12 SplashScreen API (with the compat library for API 28 to 31). First launch goes to onboarding, every later launch to Today, with no network wait.
- Weight-neutral mode is one policy object that every screen asks; no screen reads the raw switch.
- Maestro flows for the exit tests; if Maestro is not installed, install it or use Compose UI tests and say which in the log.
- Today is a skeleton with honest empty states, not placeholder numbers.
Finish per the Standing rules.
```

## B4 — Body model and exercise data

```text
Build phase B4 (Body model and exercise data) of openHealth. Read AGENTS.md, prompts.md "Standing rules" and BUILD-ROADMAP.md section B4, then DESIGN.md sections 9, 11.3 and 12.5 and Appendix C, and pr.md sections 6.5 and 6.8. Follow the Standing rules exactly.

Specific to B4:
- From references/openGym take ONLY the MuscleMap path geometry (MIT) from frontend/src/lib/body-paths.js and body-geometry.js, plus the notice from NOTICE.md. Do not read openGym's rendering, recovery or component code for implementation; write OhBodyMap yourself against DESIGN.md section 9.1.
- The recovery map is red (Recovering), orange (Easing), green (Ready) with a hatch on Recovering; the plan map uses the accent ramp; fat is drawn only outside muscle shapes (DESIGN.md 9.1.1). The map is never mirrored in RTL.
- Exercise data: find a source whose licence permits redistribution (public domain or CC0, for example a public-domain exercise dataset), read and record its licence, and write exercise names, muscle mappings and instructions as ours. Do not use openGym's catalogue, its muscle-map JSON, ExerciseDB, or any media. If no source with a clear licence is found, ask me.
- Provide the table alternative and one-sentence description for every map.
Finish per the Standing rules.
```

## B5 — Gym training

```text
Build phase B5 (Gym training) of openHealth. Read AGENTS.md, prompts.md "Standing rules" and BUILD-ROADMAP.md section B5, then pr.md sections 6.5, 6.26 and 6.29 and DESIGN.md sections 5.19 and 14.2 (Train, Live session). You may study references/openGym, Liftosaur and FastNFitness for ideas; they are AGPL or GPL, so reimplement, never copy. Follow the Standing rules exactly.

Specific to B5:
- Logging a set must take under 2 seconds with one hand: weight and reps prefilled from the last session, one tap to complete, rest timer starts itself.
- The rest timer is a foreground-service ongoing notification with actions and must survive process death; test by killing the process mid-session and resuming.
- Progression policies are an interface in :core with linear, double progression and off implemented; the others come later.
- Recovery map and routine and programme maps use the B4 renderer; plan maps and recovery maps never share colours.
- Backfilling or moving a workout recomputes rollups and never changes recorded_at.
Finish per the Standing rules.
```

## B6 — Nutrition

```text
Build phase B6 (Nutrition) of openHealth. Read AGENTS.md, prompts.md "Standing rules" and BUILD-ROADMAP.md section B6, then pr.md sections 6.3, 6.4 and 12.2 and DESIGN.md section 14.2 (Nutrition). You may study OpenNutriTracker, Waistline, FoodYou and OpenFoodFactsServer for ideas; reimplement, never copy. Follow the Standing rules exactly.

Specific to B6:
- Bundled food data is USDA FoodData Central (CC0) only, converted by a tool in tools/foodindex into a compact SQLite table; record source, version and licence in NOTICE.md. Open Food Facts is used only through an explicit user-initiated lookup, cached, with ODbL attribution and a disclosure of exactly what leaves the phone.
- Barcode scanning uses CameraX and the bundled ML Kit barcode model; no Google Play Services dependency on the core path (the F-Droid flavour must build).
- A day's totals equal the sum of its entries exactly, with no hidden rounding.
- Micronutrient reference intakes carry a citation.
Finish per the Standing rules.
```

## B7 — Body and vitals

```text
Build phase B7 (Body and vitals) of openHealth. Read AGENTS.md, prompts.md "Standing rules" and BUILD-ROADMAP.md section B7, then pr.md sections 6.8, 6.9, 6.25 and 12.1 and DESIGN.md sections 7.5, 9.1.1 to 9.4 and 14.2 (Vitals, Vitals detail). Follow the Standing rules exactly.

Specific to B7:
- This app is not a medical device. Every vital is shown against a cited range, orange at most and never red unless the owner configured an alert. Add the wording test described in the roadmap and keep it in CI.
- Record every reference range citation in code and NOTICE.md; if you cannot cite a range, do not ship it.
- The dynamic body model draws the fat layer from body fat percent, then tape measurements, with clamping; it is labelled an estimate and hidden entirely in weight-neutral mode.
- Progress photos live in app-private encrypted storage, never in shared storage or backups unless exported by an explicit action.
Finish per the Standing rules.
```

## B8 — Activity sources

```text
Build phase B8 (Activity sources) of openHealth. Read AGENTS.md, prompts.md "Standing rules" and BUILD-ROADMAP.md section B8, then pr.md sections 6.6, 6.18 and 7.6 and Tech-Stack.md section 8. You may study Stride, FitWren, Stepsy, Gadgetbridge and OpenScale for ideas; reimplement, never copy. Follow the Standing rules exactly.

Specific to B8:
- The owner's watch is a Garmin vivoactive 5, and the app must work with any Garmin. FIT file import is the reliable path; Health Connect (which Garmin Connect writes to one way) is second. Before promising any data type, read the Health Connect permission screen and the current documentation, and write what you verified into the Devices screen copy and the log. Do not use Garmin's Health API.
- Check the Garmin FIT SDK licence terms before using or porting any part of it; write the parser in :integrations as pure Kotlin or use a permissively licensed library, and record the terms.
- Garmin via Health Connect and Garmin via FIT are one source family so a day is never counted twice. One source wins a day per type; the losers remain in raw records.
- Health Connect access goes through the health-bus seam so :core stays free of android.*.
- Ask me about the scale, cuff and a physical device in the single batched dialog.
Finish per the Standing rules.
```

## B9 — Today and Stats

```text
Build phase B9 (Today and Stats) of openHealth. Read AGENTS.md, prompts.md "Standing rules" and BUILD-ROADMAP.md section B9, then pr.md sections 6.2, 6.4, 6.10 and 6.16 and DESIGN.md sections 7, 8 and 14.2 (Today, Stats). Follow the Standing rules exactly.

Specific to B9:
- Today's order is DESIGN.md 14.2. The body card is the hero. One accent per screen: Start is the accent; the body colours are data hues.
- Net energy is In minus (BMR + steps + workouts) as ranges. The goal ETA is a window, never a single date, needs 14 days of weight data, has a what-if control, respects safety floors, and is hidden in weight-neutral mode. Readiness uses the v1 formula in pr.md 6.10.
- Charts are hand-built in Compose per DESIGN.md section 8: no chart library. Every chart registers a table alternative, a one-sentence description and a "how this is calculated" sheet; a test over the registry fails if one is missing.
- Measure cold start and warm Today with Macrobenchmark over the five-year corpus and report the real numbers.
Finish per the Standing rules.
```

## B10 — Hardening and MVP release

```text
Build phase B10 (Hardening and MVP release) of openHealth. Read AGENTS.md, prompts.md "Standing rules" and BUILD-ROADMAP.md section B10, then DESIGN.md sections 11, 12 and 14.5, pr.md sections 6.22, 6.23, 6.26, 6.28, 12.3 and 12.4, and Tech-Stack.md sections 10 to 12. Follow the Standing rules exactly.

Specific to B10:
- Walk every screen against DESIGN.md 14.5 in dark, light, 200 percent text and RTL, and with TalkBack semantics tests. Fix what fails; list anything you cannot fix.
- Prove data safety: migration tests from every schema version, backup before migration, encrypted export then restore on a clean install, corrupted-database recovery.
- Prove privacy: no analytics or crash-reporting SDKs, no cleartext traffic, network use only on the explicit Open Food Facts action, allowBackup false.
- Release engineering: signed AAB and APK, and a Play-Services-free flavour. Never create or store a keystore or its password in the repository: tell me how to create one and ask me to do it, using the single batched dialog. Store text and privacy policy must not claim medical use.
- Do not publish or upload anything.
Finish per the Standing rules.
```

## B11 — Yoga, cardio, GPS

```text
Build phase B11 (Yoga, cardio, GPS) of openHealth. Read AGENTS.md, prompts.md "Standing rules" and BUILD-ROADMAP.md section B11, then pr.md sections 6.7, 6.11 and 6.18 and DESIGN.md sections 8 and 9. Confirm B10 is Completed first. You may study InnerBreeze, Feeel, OpenTracks, Tempo, GoldenCheetah, StatisticsForStrava and FitTrackee for ideas; reimplement unless the licence is permissive and you keep its notice. Follow the Standing rules exactly.

Specific to B11:
- Yoga content is ours: pose names, text and body-map diagrams; no scraped or unlicensed media.
- Strava: import the bulk-export archive and FIT/GPX/TCX only; do not use Strava's live API.
- GPS recording is a foreground service of the location type that survives process death and pause; maps are MapLibre with PMTiles, not Google Maps. Ask me about the tile source and one-time download in the batched dialog and record the licence.
- Activities feed the recovery map, the energy card and Stats through the existing repositories and dedup rules.
Finish per the Standing rules.
```

## B12 — Sleep, clinical, reports

```text
Build phase B12 (Sleep, clinical, reports) of openHealth. Read AGENTS.md, prompts.md "Standing rules" and BUILD-ROADMAP.md section B12, then pr.md sections 6.9, 6.10, 6.16, 6.17, 6.25, 12.1 and 12.2 and DESIGN.md sections 7.5 and 8.7. Confirm B10 is Completed first. Follow the Standing rules exactly.

Specific to B12:
- Wellness only: neutral display against cited ranges; no diagnosis, no passive warnings.
- Reports are always light, carry source, unit, citation and a disclaimer, and generate offline on device in under 30 seconds using android.graphics.pdf behind the PDF seam.
- FHIR R4 export uses LOINC and UCUM codes only where their licences allow; record each code system's terms in NOTICE.md. Validate the bundle in a test.
- Medication reminders are local notifications; no push server.
Finish per the Standing rules.
```

## B13 — Sync and server

```text
Build phase B13 (Sync and server) of openHealth, which I am explicitly starting now. Read AGENTS.md, prompts.md "Standing rules" and BUILD-ROADMAP.md section B13, then pr.md sections 6.24 and 7.7 to 7.9 and Tech-Stack.md sections 6 and 7. Confirm B10 is Completed first. Follow the Standing rules exactly.

Specific to B13:
- The server is optional and self-hostable; the app must keep working with none. server/ is AGPL-3.0; everything else stays Apache-2.0.
- :sync is pure Kotlin compiled into both the app and the Ktor server. Merge policies are per entity, declared in :core; conflicts a policy cannot settle surface in the UI with both sources; rollups are never synced.
- Write the property test that random interleavings of offline edits converge before writing the transport.
- Ask me about hosting, domain and who may sign in in the batched dialog. Do not deploy anywhere.
Finish per the Standing rules.
```

## B14 — Wear OS and Connect IQ

```text
Build phase B14 (Wear OS and Connect IQ) of openHealth, which I am explicitly starting now. Read AGENTS.md, prompts.md "Standing rules" and BUILD-ROADMAP.md section B14, then pr.md sections 6.5 and 6.18. Confirm B10 is Completed first. Follow the Standing rules exactly.

Specific to B14:
- Wear OS uses Compose for Wear OS and shares :core. The Connect IQ app targets the owner's vivoactive 5 first and other Garmin models through a per-device compatibility table; check the Connect IQ SDK terms and device list at the time you build.
- Ask me, in the batched dialog, which watch I will test on and whether I have a Connect IQ developer setup.
Finish per the Standing rules.
```

## B15 — AI Coach and MCP

```text
Build phase B15 (AI Coach and MCP) of openHealth, which I am explicitly starting now. Read AGENTS.md, prompts.md "Standing rules" and BUILD-ROADMAP.md section B15, then pr.md section 6.15 and Tech-Stack.md section 9. Confirm B13 is Completed first. Follow the Standing rules exactly.

Specific to B15:
- Off by default. Bring-your-own-key or a self-hosted endpoint. Per-category consent, and Health Connect data excluded unless separately opted in. Every response is parsed and validated by the same :core validators as the UI, one repair round, then fails visibly. Output is a diff-card proposal that is never applied without approval and is undoable.
- Never hard-code or log a key. Ask me about provider and key handling in the batched dialog.
Finish per the Standing rules.
```
