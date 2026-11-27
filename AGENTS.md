# AGENTS.md

Guidance for agent sessions in this repository.

## What this repo is

The **contract and the build of openHealth**, a private, local-first personal health record for Android. The contract documents (`pr.md`, `DESIGN.md`, `Tech-Stack.md`, `BUILD-ROADMAP.md`, `docs/adr/`) are complete enough to build from. The build proceeds one phase at a time (B0 to B15) by pasting a prompt from `prompts.md` into a fresh session; `docs/build-log.md` is the memory between sessions. If `docs/build-log.md` does not exist, no code has been written and the next phase is B0.

**If you were not given a phase prompt,** do not scaffold or build: answer, review or edit the documents. If you were given one, follow `prompts.md` "Standing rules" exactly; they say what you may do, when to ask the owner and how to finish.

**The product in one paragraph.** One Android app for the whole of a person's health: a daily loop (Today → log → trend), five tabs (Today, Vitals, Train, Nutrition, Stats) with a raised-centre Train and Settings in the top bar, thirteen switchable modules, and a five-layer data model (raw → observation → daily rollup → domain → settings) in which every value carries provenance. It is local-first with an optional, self-hostable server; open-format; no ads, no telemetry; not a medical device; and complete enough to export and leave with. Android ships first. iOS, watchOS, the web client and desktop are deferred behind the gate at `pr.md` §13.17 — do not write, design or plan them before it opens.

Before changing anything, read `pr.md` §0–2, `DESIGN.md` §0–1 and `Tech-Stack.md` §2–3; then use the task table below.

It is not a screen-by-screen wireframe set: `DESIGN.md` §14.1–14.2 covers the five roots plus the settings surface and the key global surfaces, not every future screen.

## Documents and precedence

| File | Version | Owns | Authority |
|---|---|---|---|
| `pr.md` | 0.4 · 2026-11-22 | Features and MVP/V1/Later tags (§6); five-layer data model (§7); architecture summary (§8); product roadmap (§9); metrics (§10); non-goals (§11); regulatory, licensing and platform constraints (§12); open decisions (§13); cut list (§14.5) | **Behaviour contract.** Wins on scope, wording and what a screen does. |
| `DESIGN.md` | 0.7 · 2026-11-27 | Every colour, type role, size, radius, state, motion, component, chart, provenance visual, body/media rule and a11y rule; closed token set (§0.2); screen inventory and recipes (§14.1–14.2); review gate (§14.5); canonical tokens (Appendix A); Compose mapping (Appendix B); asset licences (Appendix C) | **Appearance contract.** Wins on pixels, interaction feel and presentation. |
| `Tech-Stack.md` | 2.5 · 2026-11-27 | Native-Android decision and rejected alternatives (§2); portability discipline (§3); client, storage, sync, server and Android APIs (§4–8); AI (§9); testing, CI and performance budgets (§10–12); repository layout (§13); versions (§14); closes §13 items | **Build contract.** Wins on stack, storage, sync and server. |
| `BUILD-ROADMAP.md` | 1.0 · 2026-11-22 | Build phases B0–B15: deliverables, exit tests, hand-offs, what to ask the owner, risks | **Build sequence.** Wins on order and exit tests; owns no values. |
| `prompts.md` | 1.0 · 2026-11-22 | One copy-paste prompt per phase and the Standing rules every agent follows | **Operating procedure** for build sessions. |
| `docs/adr/` | 0019 · 2026-11-27 | Dated closed decisions, one per phase. 0001 Android first; 0002 the defaults that closed `pr.md` §13; 0003 pinned versions; 0004–0019 one per build phase, the last being B14 Wear OS and Connect IQ | **Decision record.** |
| `.opencode/commands/build-phase.md`, `opencode.json` | 2026-11-23 | The `/build-phase n or next` OpenCode command that runs a phase from `prompts.md`, verifies the whole build, fixes what it finds, then pushes the phase branch, opens its pull request, drives CI green and merges it; and the permission policy (no blind `git add -A`, no force-push, no direct push to `main`, no protection bypass) | **Launcher.** Overrides `prompts.md` only on the authorised ship at the phase exit, which `prompts.md` now records too. |
| `docs/build-log.md` | created in B0 | What each phase did, ran and left open | **Memory between sessions.** |
| `COMPOSE-GUIDE.md` | 0.1 · 2026-11-21 | Compose snippets, a token-file sketch, one screen template, a file tree | **Patterns only.** Not authoritative on values or structure; see conflicts. |

Resolution: each file wins in its own domain, and a specialist beats a generalist (`Tech-Stack` > `pr.md` §8; `DESIGN` > `COMPOSE-GUIDE`). `pr.md` wins on behaviour, `DESIGN` on appearance, `Tech-Stack` on build. When `Tech-Stack` and `BUILD-ROADMAP` disagree, `Tech-Stack` wins; `prompts.md` never overrides a contract document.

## Scope, schedule and tags

- **Tags:** `MVP`, `V1`, `Later` in `pr.md` §6 are the schedule. There is no V2; if a stale file says V2, read `Later`.
- **Product phases:** `pr.md` §9 — Phase 0 Foundations, Phase 1 MVP, Phase 2 Depth, Phase 3 Ecosystem, Phase 4 Best in the world. `Tech-Stack`'s Phase 0/1/2 labels refer to those phases, not to `BUILD-ROADMAP`'s UI phases.
- **Build phases:** `BUILD-ROADMAP` B0–B15 are the build order and are named B-numbers so they are never confused with `pr.md` §9 product phases. B0–B10 are the MVP (with nutrition in); B11–B12 are early depth; B13–B15 start only when the owner asks. Do not cite them as product scope.
- **Android only:** phone first, Wear OS in Phase 2 (`pr.md` §0.1, `Tech-Stack` §2); Garmin is the owner's primary watch (`pr.md` §6.18 V1). Health Connect is the platform bus. Any HealthKit, Apple Watch, iOS or browser feature is `Later` until §13.17 opens, whatever tag it carries. File imports (Apple Health export, Garmin/Strava archive, FIT/GPX/TCX/CSV) stay in scope because they need no vendor platform.
- **Phase 1 reality:** nutrition is in the MVP (ADR 0002, `pr.md` §13.16), so the MVP is the long version priced in `pr.md` §9. The roadmap carries no calendar estimates.

## Decisions already made

| Area | Decision | Source |
|---|---|---|
| Client | Native Android, Kotlin + Jetpack Compose; Material 3 only as behaviour primitives | `Tech-Stack` §2, §4 |
| Navigation | Five tabs — Today, Vitals, Train (raised centre), Nutrition, Stats — plus a global top bar with quick add and Settings; no More tab | `DESIGN.md` §4.4, §5.15, §13.1; `Tech-Stack.md` §4 |
| Watch | Any Garmin; vívoactive 5 is the reference device. FIT import, Health Connect (one-way from Garmin Connect), then Connect IQ (V1). Garmin Health API not assumed | `pr.md` §6.18, §13.8 |
| Body visuals | Recovery map red → orange → green (Recovering / Easing / Ready, 24/48/72 h defaults); `fat` layer `#F2C230` dark / `#A87C00` light (re-valued 2026-11-23, `DESIGN.md` §2.5), outside muscle shapes only; 2D schematic, no photo-derived shape | `DESIGN.md` §9.1, §9.1.1, §12.5; `pr.md` §6.5, §6.8; `docs/adr/0009` |
| Energy | Net = in − (BMR + steps + workouts), one source per day; goal ETA is a window, not a date, needs 14 days of data, hidden in weight-neutral mode | `pr.md` §6.4 |
| Core | Platform-neutral Kotlin modules, KMP-ready; no `android.*`/`androidx.*` | §3 |
| Storage | SQLite + SQLDelight 2.x + SQLCipher; five physical layers | §5 |
| DI | **Koin**, not Hilt | §3 |
| Sync | Custom append-only change log; idempotency keys; hybrid logical clock; per-entity merge policies | §6 |
| Server | Kotlin/Ktor 3 + PostgreSQL 16 + Flyway + S3/MinIO; optional | §7 |
| Distribution | Play Store **and** a Play-Services-free F-Droid build | §4 |
| Licence | AGPL-3.0 `server/`, Apache-2.0 everything else | `pr.md` §12.4; `Tech-Stack` §13 |
| AI | Off by default; BYOK or self-hosted; proposals only, never applied unapproved | `Tech-Stack` §9; `pr.md` §6.15 |
| Rejected | Flutter, React Native, Capacitor, Compose Multiplatform UI now, Room, Hilt, PowerSync/ElectricSQL, CRDT libraries outside notes, Google Maps, dynamic colour | `Tech-Stack` §2, §6; `DESIGN` §12.4 |

## Invariants (fail the task if you break one)

- **Provenance on every value:** source, device, method, happened-at, recorded-at, local date, timezone, idempotency key, raw-record ref (`pr.md` §2.2, §7.2).
- **`happened_at` ≠ `recorded_at`:** backfill never rewrites when something was recorded; it recomputes the affected day's rollups (`pr.md` §6.27, §7.1, §7.3).
- **Five layers** (`pr.md` §7, `Tech-Stack` §5): raw → observation → daily rollup → domain → settings. Rollups are rebuildable; fix bugs by rebuilding, never by patching.
- **Dedup:** one configured-priority source wins a day — never the sum, never the largest value (`pr.md` §7.6).
- **Local-first:** no core action waits for a network; the server is optional and self-hostable forever (`pr.md` §2.3, §12.5).
- **Not a medical device:** clinical features are a neutral display of the user's own data against a **cited** range, or they do not ship. No wording that diagnoses, warns passively or shames (`pr.md` §12.1, §6.25; `DESIGN` §7.5, §11.7).
- **No telemetry:** no ads, no third-party analytics, nothing leaves the device without an explicit action (`pr.md` §6.23, §6.31). Budgets are measured in a device lab and recruited cohorts (`Tech-Stack` §12); `pr.md` §13.12 still records the final measurement decision as open.
- **Portability:** `core/`, `data/`, `sync/` and the API half of `integrations/` take no `android.*`/`androidx.*`, use `kotlinx-datetime`/`kotlinx.serialization`, and define platform seams even when only Android implements them (`Tech-Stack` §3).
- **Licence split:** AGPL-3.0 `server/`, Apache-2.0 everything else; settle before the first public commit (`pr.md` §12.4).
- **The cut list (`pr.md` §14.5) is closed.** Form-check AI, VBT, photo body composition, smart alarm, safety beacon, live group sessions, community libraries and the rest return only with a written argument naming what they displace.
- **Modules hide, never delete:** turning a module off removes its surfaces and keeps its data (`pr.md` §2.4, §6.21).
- **Weight-neutral mode is first-class:** one switch hides calories, weight, body fat and photos; nothing is deleted and nothing is blurred (`pr.md` §6.25; `DESIGN` §7.6).
- **Undo is a tombstone, not a timer:** session-length undo plus 30-day Trash, not a 3-second toast (`pr.md` §6.29; `DESIGN` §5.18).
- **Every chart** has a table alternative and a one-sentence accessible description (`pr.md` §6.16; `DESIGN` §8.6). **Every report** is light, carries source, citation and disclaimer, and works offline (`DESIGN` §8.7).
- **Quality bars:** cold start < 1.5 s, warm Today < 300 ms, set < 2 s, food < 5 s, doctor report < 30 s offline, passive steps < 2% battery/day (`pr.md` §6.26; `Tech-Stack` §12). WCAG 2.2 AA, 200% text, RTL, 44 dp targets (`pr.md` §6.22; `DESIGN` §11).

## Known conflicts — resolve this way

### DI and database

`Tech-Stack.md` §3 requires **Koin** and **SQLDelight** and rejects **Room** and **Hilt**. `COMPOSE-GUIDE.md`'s screen template uses `hiltViewModel()`; ignore it. Do not add Hilt, Room or `androidx` to `core/` or `data/`.

### Tokens and component counts

- `DESIGN.md` Appendix A is canonical and §0.2 is closed: 11 type roles (9 text roles plus `display` and `timer`), 9 spacing values (`2 4 6 8 10 12 16 22 32`), 6 radii (`8 10 12 14 16 22`) plus full, 19 components, 9 accent presets. Dark is canonical; light is a verified sibling.
- `COMPOSE-GUIDE.md`'s `tokens.kt` is not usable as a source of values: spacing 4–40, radii 4–20, different type role names and sizes, light `bg` `#FFFFFF`, borders on cards, 44 dp buttons, 60 dp nav, sheet on `surface` with 14 dp corners and a 32% scrim.
- Every `Color(0x…)` literal in `COMPOSE-GUIDE.md` is six hex digits, so all of them are alpha-zero (invisible) in Compose. Write `Color(0xFFRRGGBB)`. Its `gold` `0xFFB84D` is not `DESIGN.md`'s `#FFD60A`; its `red` `0xFF453A` has the right RGB but is still alpha-zero as a Compose literal.
- Sizes differ: `DESIGN.md` rows are ≥46 dp, the primary button is 50 dp and the tab bar is 56 dp; both guides use 44 dp rows/buttons, and `COMPOSE-GUIDE.md` uses a 60 dp nav.

### `COMPOSE-GUIDE.md` is patterns, not a style contract

- It maps tokens into `MaterialTheme`/`colorScheme`; `DESIGN.md` Appendix B requires one `OhTheme` with `OhColors`, `OhType`, `OhSpace`, `OhRadius` and `OhMotion`, no `MaterialTheme.colorScheme` defaults and no dynamic colour.
- Card borders, bottom-border card headers, ripple indication, emoji icons and emoji card glyphs are all forbidden (`DESIGN.md` §2.4, §5.1–5.2, §6.1, §10.1).
- "Undo toast expires after 3–5 s" is wrong (`DESIGN.md` §5.18: 4 s plain / 6 s with Undo; tombstone for the session; Trash 30 days).
- "1.3× font scale" is wrong: 200% (`DESIGN.md` §4.3, §11.1; `pr.md` §6.22).
- RTL checklist "no hardcoded `start`/`end` or `left`/`right`" is wrong: logical `start`/`end` are required; physical `left`/`right` are banned (`DESIGN.md` §11.5, §14.5).
- Its file tree (`app/src/main/kotlin/com/openhealth/…`, `:design`) does not match `Tech-Stack.md` §13 (`app-android/`, `ui/`, `core/`, …), and its `OHBottomNavigation` builds six tabs; the contract is five tabs with a raised Train and top-bar Settings (`DESIGN.md` §4.4, §5.15).
- The snippets are illustrative and do not compile as written (for example `Modifier.border(bottom = …)`, undefined `isDarkTheme`, `hiltViewModel()` without Hilt). Rebuild from `DESIGN.md` §5 and Appendix B.

### `BUILD-ROADMAP.md` was rewritten (2026-11-22)

Version 1.0 replaces the stale 0.1 (Hilt, Room, six tabs, WCAG 2.1, Crashlytics, undo toasts). Anything you remember from 0.1 is wrong; read the current file. The readiness formula is now defined in `pr.md` §6.10.

### `pr.md`'s own stale spots

`pr.md` §13 is now a closed decisions register. §8.1–8.4 still name Node, Go, WatermelonDB, PowerSync and ElectricSQL and are superseded by `Tech-Stack.md`; treat them as history. Do not reopen settled questions. If you change a decision, record it in an ADR and propagate it.

### Reference library count

`references/README.md` and `references/MANIFEST.md` index 41 folders (`openGym` plus 40 upstream); all 41 are checked out (`wger` and `xDrip`, missing until 2026-11-25, are present again — verified in the B8 audit). Verify with a directory listing before citing a project. `references/openGym` is the only full (non-shallow) clone at ~747 MB; every other checkout is `--depth 1`.

## Dead references (do not chase them)

`WIREFRAMES.md`, `wireframes.html`, `wireframes-*.svg`, `CLAUDE.md`, `BUILD-PLAN.md` and the old mockup export do not exist and are not cited by the live documents any more. The screen review gate is `DESIGN.md` §14.5; the inventory and recipes are §14.1–14.2. `COMPOSE-GUIDE.md` still points at `WIREFRAMES.md`; ignore it. There is no root `README.md`; this file is the entry point and `references/README.md` indexes the study library.

## Where to look

| Task | Start here |
|---|---|
| Add, cut or retag a feature | `pr.md` §6, §13, §14.5 |
| Schema, provenance, rollups, dedup | `pr.md` §7 (especially §7.1–7.6, §7.9); `Tech-Stack.md` §5–6 |
| Sync, conflicts, schema versions | `pr.md` §7.7–7.9; `Tech-Stack.md` §6 |
| Storage, server, deploy | `Tech-Stack.md` §5, §7, §13 |
| Screen composition | `DESIGN.md` §4.4, §14.1–14.2; gate §14.5 |
| Colour, type, space, shape | `DESIGN.md` §2–4; Appendix A |
| Component or state | `DESIGN.md` §5–6 |
| Provenance UI | `DESIGN.md` §7 |
| Charts, reports, export | `DESIGN.md` §8 |
| Body map, photos, media licensing | `DESIGN.md` §9, Appendix C; `pr.md` §12.2 |
| Accessibility, RTL, copy | `DESIGN.md` §11, §14.5; `pr.md` §6.22 |
| Clinical wording and scope | `pr.md` §6.9, §6.25, §12.1; `DESIGN.md` §7.5 |
| Android APIs and permissions | `Tech-Stack.md` §8 |
| Performance budgets | `Tech-Stack.md` §12; `pr.md` §6.26 |
| Tests and CI | `Tech-Stack.md` §10–11 |
| Build order, exit tests | `BUILD-ROADMAP.md`; run with `prompts.md` |
| Why a decision was made | `docs/adr/` |
| Open decisions | `pr.md` §13 |

## Decisions

All 17 items of `pr.md` §13 are closed or deferred (iOS). On 2026-11-22 the owner delegated every open item to the recommended default; `docs/adr/0002-closed-defaults.md` records each with its reason. The owner may overrule a row; an agent never does, and asks instead (`prompts.md` "When to ask").

## Commands

```sh
# Verify a reference checkout before trusting it
git -C references/<name> log -1 --oneline

# Update one shallow reference clone
git -C references/<name> pull --ff-only

# Search the study library without drowning in .git objects
rg -n --glob '!**/.git/**' '<pattern>' references/<name>
```

A headless audit or any other tool may leave the working tree on a different branch: read
`git status -sb` immediately before staging, and push only the phase branch
(`build/b<n>-<slug>`). Never commit or push to `main`; it moves through a merged pull
request. The 2026-11-24 B4 audit is the recorded instance of getting this wrong.

The workstation is Linux. `git`, `adb`, JDK 21 and the Android SDK are installed (verified 2026-11-23); Gradle is the wrapper (`./gradlew`), not a `PATH` install, and `rg` may not be present. Shell fallbacks:

```sh
test -d references/wger && echo present || echo missing
ls -1 references
grep -rn 'WIREFRAMES\|BUILD-PLAN' --include='*.md' .
```

The working copy is on a plain local path, not OneDrive or any synced folder; earlier drafts of `prompts.md` and `BUILD-ROADMAP.md` assumed a Windows + OneDrive machine and no longer do.

### Test device

The owner tests on a physical phone over USB debugging, not an emulator, and its identity — serial, model, OS build, anything `adb` prints — must not enter this public repository. The machine-local values live in `.env.local` at the repository root, which the existing `.env.*` rule in `.gitignore` keeps ignored; source it before any `adb` or Gradle call so both target the phone:

```sh
set -a; . ./.env.local; set +a   # ANDROID_SERIAL for this machine only
adb devices -l
./gradlew :app-android:installDebug
adb -s "$ANDROID_SERIAL" shell monkey -p app.openhealth -c android.intent.category.LAUNCHER 1
adb -s "$ANDROID_SERIAL" logcat -s AndroidRuntime:E
```

When `adb devices -l` lists the phone as `device`, install and test on it instead of starting an emulator. This answers B0's "emulator or my physical phone" question: record "physical phone" in `docs/build-log.md` and do not ask again. If it is `unauthorized` or absent, ask the owner to unlock and accept the prompt; do not silently fall back to an emulator. Never write device output into the tree — no serial, model, IMEI, `adb devices` dump, raw `logcat` or screenshot carrying a status bar or notification — and never commit `.env.local`. Two exits need more than the phone: B12's sync test needs a second target (the phone plus one emulator is fine), and B14's macrobenchmark numbers come from the phone and are reported as real-hardware; an emulator run is the fallback and is flagged as not the reference device class.

`references/` is ~5 GB and gitignored except `README.md` and `MANIFEST.md`. **Never `git add -A` from the repo root without checking `git status` first**, and never commit anything under `references/`. The working tree may hold deliberate deletions of superseded docs; do not restore them with `git checkout .`.

## Reference library

41 folders are indexed (`references/README.md`, `references/MANIFEST.md`) and all 41 are checked out; `openGym/` is the closest reference for the training domain, the sync model and the design language (`DESIGN.md` §0.1). They are read, not imported: most are GPL/AGPL, `SparkyFitness` and `HealthLog` are source-available non-commercial, and food/clinical/media data carry separate terms (`pr.md` §12.2). `openGym`'s code is AGPL and its exercise stills/animations are licensed from Gym visual and **not reusable**; the only asset that may cross is the MIT MuscleMap body geometry (`DESIGN.md` Appendix C).

## Design work

`DESIGN.md` is a closed set: its hexes, 11 type roles, 9 spacing values, 6 radii, 19 components and 9 accent presets. Do not invent a sibling shade, a 12th type role, a new card variant or a second accent. If a screen seems to need one, amend `DESIGN.md` first in a dated edit. Dark is canonical; light is a verified sibling (§12). Sizes are dp/sp in Compose, matching `DESIGN.md` 1:1; platform mapping (insets, predictive back, keyboard, font scale) is §4.3.

Non-negotiables in the visual contract:

- Five tabs, not six: Today, Vitals, Train (raised centre), Nutrition, Stats. Settings is the top-bar gear and quick add the top-bar plus; there is no More tab (`DESIGN.md` §4.4, §5.15, §13.1).
- One accent per screen, spent on the next action, the chosen state or the one number that matters. Red only for destructive actions, errors and user-set alerts; the muscle recovery map is the one exception and may appear on Today, Train and Stats, never on a vital, clinical card or report (`DESIGN.md` §1.1, §2.8, §9.1, §13.9).
- Hairlines, not boxes: 0.5 dp separators between rows only; no card borders, no Material palette, no ripple, no emoji icons (`DESIGN.md` §2.4, §5.1–5.3, §6.1, §10.1, Appendix B).
- Every number opens a provenance sheet; every destructive action leaves a tombstone and a Trash entry (`DESIGN.md` §7.1–7.2, §5.18).
- Reports and shared images are always light, with source, unit, citation and disclaimer (`DESIGN.md` §8.7). The body map is never mirrored (`DESIGN.md` §11.5).
- 200% text, 44 dp targets, 4.5:1 body text, 3:1 controls, reduced motion/transparency and high contrast (`DESIGN.md` §4.3, §6.9, §11.1).
- If a value is missing, add it to `DESIGN.md` Appendix A in a dated edit before any screen uses it. `COMPOSE-GUIDE.md` is not the place to add it.

## Editing rules

- When changing a decision, update every file that records it: `pr.md` §13 (open decisions), `Tech-Stack.md` (if stack), `BUILD-ROADMAP.md` (if phasing) and `DESIGN.md` (if visual). Update the version/last-revision line of each file you touch.
- Cite sections when claiming scope (e.g. `pr.md` §6.5 MVP); the MVP/V1/Later tags are the schedule. Do not cite line numbers — the docs move.
- Keep the markdown style of the file you touch: dense prose, tables, section numbering, no filler.
- Prefer `Later` over "V2".
- Correct the text your change touches and flag unrelated staleness; a docs sweep is its own task.
- Do not add a new document, section, token, component, metric or licence without an explicit request or a phase that calls for it. ADRs go in `docs/adr/` with the next free number when a decision with consequences is made.
- UI copy bans "OK", "Submit", "Yes/No", "Are you sure?", exclamation marks, streak guilt, em dashes, and any wording that diagnoses or shames (`DESIGN.md` §11.7). This restriction is for product copy, not these documents.

## Current state

Updated by every build phase at its exit (`prompts.md` "Log and finish").

- Last completed phase: **B15 AI Coach and MCP** (2026-11-27). B15 is the last planned
  phase; there is no B16.
- Build: green. `./gradlew check :app-android:assembleDebug :app-wear:assembleDebug`
  passes; **901 JVM tests** (453 `:core`, 181 `:data`, 7 `:sync`, 55 `:integrations`,
  169 `:ui`, 14 `:server`, 10 across the three tools, 12 `:app-wear`) and **376 goldens**
  in dark, light, 200 % text and RTL, plus **70 instrumented tests** on the phone
  emulator and **1** on the Wear emulator. Both `play` and `fdroid` debug APKs and the
  Wear APK build. The five-tab shell, B4 body map, B5 Train, B6 Nutrition, B7 Vitals,
  B8 Devices, B9 Today/Stats, B10 review, B11 movement, B12 sleep/clinical/report pages,
  B13 Sync page, B14 watch surfaces and the B15 coach and external-access pages are
  present.
- B15 delivers: a provider-agnostic `:core` coach adapter (Anthropic Messages API and any
  OpenAI-compatible self-hosted endpoint), per-category consent with a separate Health
  Connect switch, a strict structured-output parser that validates plan changes with the
  same bounds as the editor, one repair round then a visible failure, a safety filter for
  diagnosis/prescription/dosing, diff-card proposals that apply only on approval and undo
  from a before-snapshot, and an on-device MCP JSON-RPC endpoint (`127.0.0.1:8765/mcp`,
  loopback only, off by default) with seven scoped tools, SHA-256 token digests,
  revocation and an argument-free audit. The provider key lives only in the Android
  Keystore through `SecretVault`. Schema v12 adds the coach and MCP tables and the
  `source.via_health_connect` flag; neither table syncs, and the encrypted export carries
  the conversation and only the token digest.
- B15's decisions are in `docs/adr/0020-ai-coach-and-mcp.md`; the coach and
  external-access recipes are `DESIGN.md` §14.2 (0.7.1); versions are unchanged.
- Verification: `CoachExitTest` on the phone emulator (proposal → accept → undo; an
  outage keeps the note and changes nothing; MCP 401 without a token, scope refusal, an
  out-of-range value rejected, a valid write with provenance, a revoked token refused),
  the JVM parser/consent/provider/MCP/applier suites, `CoachScreen`/`McpScreen` goldens in
  four variants, the whole **70-test** phone suite green, and a device walk of Settings →
  Coach and Settings → Data → External access (toggle, consent, disclosure, no-key error,
  token reveal and revoke) with no `AndroidRuntime` error.
- Owner answers recorded: Anthropic plus a self-hosted endpoint; the key in the Android
  Keystore; the MCP server on-device and loopback; use the emulators for QA. Carried from
  B14: the owner's phone was not attached.
- Open issues (see `docs/build-log.md`): the coach has never called a real provider (no
  key is stored in the repository or on the test device), so the adapters are proved
  against the published request/response shapes and a recording transport, not a live
  model; the self-hosted path has not met a real LM Studio/Ollama host; the MCP surface is
  local only and has no server-side counterpart; the carried B14 issues remain: the
  Connect IQ app is source and table only —
  Garmin's SDK and developer key were not accepted or installed, so Monkey C has not been
  compiled, signed or walked on the watch; the Data Layer was not paired between the two
  emulators, so its transport is compiled and its protocol/UI tested in-process; the GATT
  link is unauthenticated while advertising and only runs with a session open; the Wear
  APK is not embedded in the Play phone APK (a store packaging step); the optical heart
  rate has no sensor on the emulator; the owner's phone was not attached. Carried: the
  B13 loopback-http exception, Settings → Data golden, passkey live test, MinIO/OIDC,
  single-instance server; the B12 lab import, medicine deletion, free-text
  conditions/allergies and report trend rollups; the B11 map pack, gear totals, FIT/TCX
  export, custom flows, Strava memory cap, phone Macrobenchmarks, review import dry run,
  privacy-policy URL and force-RTL reboot. Run `connectedAndroidTest` on the emulator,
  never the phone.

