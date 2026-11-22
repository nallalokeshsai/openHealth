# AGENTS.md

Guidance for agent sessions in this repository.

## What this repo is

A **documentation-only product and design contract** for openHealth, a private, local-first personal health record. There is no application code, no Gradle project, no linter, no test runner and no CI. Do not scaffold an app, add a package manager, create source directories or start a framework unless explicitly asked. When the build starts it follows `Tech-Stack.md` §13 for layout and `BUILD-ROADMAP.md` Phase 0 for the first UI slice, with `Tech-Stack.md` and `DESIGN.md` overriding that phase's Hilt/Room/token values.

**The product in one paragraph.** One Android app for the whole of a person's health: a daily loop (Today → log → trend), five tabs (Today, Vitals, Train, Nutrition, Stats) with a raised-centre Train and Settings in the top bar, thirteen switchable modules, and a five-layer data model (raw → observation → daily rollup → domain → settings) in which every value carries provenance. It is local-first with an optional, self-hostable server; open-format; no ads, no telemetry; not a medical device; and complete enough to export and leave with. Android ships first. iOS, watchOS, the web client and desktop are deferred behind the gate at `pr.md` §13.17 — do not write, design or plan them before it opens.

Before changing anything, read `pr.md` §0–2, `DESIGN.md` §0–1 and `Tech-Stack.md` §2–3; then use the task table below.

It is not a screen-by-screen wireframe set: `DESIGN.md` §14.1–14.2 covers the five roots plus the settings surface and the key global surfaces, not every future screen.

## Documents and precedence

| File | Version | Owns | Authority |
|---|---|---|---|
| `pr.md` | 0.3 · 2026-11-20 | Features and MVP/V1/Later tags (§6); five-layer data model (§7); architecture summary (§8); product roadmap (§9); metrics (§10); non-goals (§11); regulatory, licensing and platform constraints (§12); open decisions (§13); cut list (§14.5) | **Behaviour contract.** Wins on scope, wording and what a screen does. |
| `DESIGN.md` | 0.1 · 2026-11-21 | Every colour, type role, size, radius, state, motion, component, chart, provenance visual, body/media rule and a11y rule; closed token set (§0.2); screen inventory and recipes (§14.1–14.2); review gate (§14.5); canonical tokens (Appendix A); Compose mapping (Appendix B); asset licences (Appendix C) | **Appearance contract.** Wins on pixels, interaction feel and presentation. |
| `Tech-Stack.md` | 2.0 · 2026-11-20 | Native-Android decision and rejected alternatives (§2); portability discipline (§3); client, storage, sync, server and Android APIs (§4–8); AI (§9); testing, CI and performance budgets (§10–12); repository layout (§13); versions (§14); closes §13 items | **Build contract.** Wins on stack, storage, sync and server. |
| `BUILD-ROADMAP.md` | 0.1 · 2026-11-21 | UI-first phase order 0–6 + beta, component/screen deliverables, exit tests, effort estimates, risks | **Sequence only, and stale.** Never evidence for stack, tokens or a11y. |
| `COMPOSE-GUIDE.md` | 0.1 · 2026-11-21 | Compose snippets, a token-file sketch, one screen template, a file tree | **Patterns only.** Not authoritative on values or structure; see conflicts. |

Resolution: each file wins in its own domain, and a specialist beats a generalist (`Tech-Stack` > `pr.md` §8; `DESIGN` > `COMPOSE-GUIDE`). `pr.md` wins on behaviour, `DESIGN` on appearance, `Tech-Stack` on build. When `Tech-Stack` and `BUILD-ROADMAP` disagree, `Tech-Stack` wins.

## Scope, schedule and tags

- **Tags:** `MVP`, `V1`, `Later` in `pr.md` §6 are the schedule. There is no V2; if a stale file says V2, read `Later`.
- **Product phases:** `pr.md` §9 — Phase 0 Foundations, Phase 1 MVP, Phase 2 Depth, Phase 3 Ecosystem, Phase 4 Best in the world. `Tech-Stack`'s Phase 0/1/2 labels refer to those phases, not to `BUILD-ROADMAP`'s UI phases.
- **Build phases:** `BUILD-ROADMAP` Phase 0–6 + beta are a UI implementation order (tokens → screens → logging → sheets → charts → sync → a11y). Do not map them 1:1 to `pr.md` §9, and do not cite them as product scope.
- **Android only:** phone first, Wear OS in Phase 2 (`pr.md` §0.1, `Tech-Stack` §2); Garmin is the owner's primary watch (`pr.md` §6.18 V1). Health Connect is the platform bus. Any HealthKit, Apple Watch, iOS or browser feature is `Later` until §13.17 opens, whatever tag it carries. File imports (Apple Health export, Garmin/Strava archive, FIT/GPX/TCX/CSV) stay in scope because they need no vendor platform.
- **Phase 1 reality:** `pr.md` §9 prices the MVP at 6–9 months, not 3–4, or nutrition moves to Phase 2 (§13.16). `BUILD-ROADMAP`'s 20-week single-dev plan is a UI schedule, not that estimate.

## Decisions already made

| Area | Decision | Source |
|---|---|---|
| Client | Native Android, Kotlin + Jetpack Compose; Material 3 only as behaviour primitives | `Tech-Stack` §2, §4 |
| Navigation | Five tabs — Today, Vitals, Train (raised centre), Nutrition, Stats — plus a global top bar with quick add and Settings; no More tab | `DESIGN.md` §4.4, §5.15, §13.1; `Tech-Stack.md` §4 |
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

`Tech-Stack.md` §3 requires **Koin** and **SQLDelight** and rejects **Room** explicitly. `BUILD-ROADMAP.md` says "Hilt + Compose + Material 3 configured" and "Models map to room DB"/"Local database (Room)" in Phase 5; `COMPOSE-GUIDE.md`'s screen template uses `hiltViewModel()`. `Tech-Stack` wins. Do not add Hilt, Room or `androidx` to `core/` or `data/`.

### Tokens and component counts

- `DESIGN.md` Appendix A is canonical and §0.2 is closed: 11 type roles (9 text roles plus `display` and `timer`), 9 spacing values (`2 4 6 8 10 12 16 22 32`), 6 radii (`8 10 12 14 16 22`) plus full, 19 components, 9 accent presets. Dark is canonical; light is a verified sibling.
- `COMPOSE-GUIDE.md`'s `tokens.kt` is not usable as a source of values: spacing 4–40, radii 4–20, different type role names and sizes, light `bg` `#FFFFFF`, borders on cards, 44 dp buttons, 60 dp nav, sheet on `surface` with 14 dp corners and a 32% scrim.
- Every `Color(0x…)` literal in `COMPOSE-GUIDE.md` is six hex digits, so all of them are alpha-zero (invisible) in Compose. Write `Color(0xFFRRGGBB)`. Its `gold` `0xFFB84D` is not `DESIGN.md`'s `#FFD60A`; its `red` `0xFF453A` has the right RGB but is still alpha-zero as a Compose literal.
- `BUILD-ROADMAP.md` Phase 0 is wrong too: "11 colours", spacing 4–40, radii 4–20, "13 sp baseline → 34 sp" (DESIGN starts at `caption2` 11 sp and adds `display` 28 / `timer` 28–64), and "All 10 components" where `DESIGN.md` has 19.
- Sizes differ: `DESIGN.md` rows are ≥46 dp, the primary button is 50 dp and the tab bar is 56 dp; both guides use 44 dp rows/buttons, and `COMPOSE-GUIDE.md` uses a 60 dp nav.

### `COMPOSE-GUIDE.md` is patterns, not a style contract

- It maps tokens into `MaterialTheme`/`colorScheme`; `DESIGN.md` Appendix B requires one `OhTheme` with `OhColors`, `OhType`, `OhSpace`, `OhRadius` and `OhMotion`, no `MaterialTheme.colorScheme` defaults and no dynamic colour.
- Card borders, bottom-border card headers, ripple indication, emoji icons and emoji card glyphs are all forbidden (`DESIGN.md` §2.4, §5.1–5.2, §6.1, §10.1).
- "Undo toast expires after 3–5 s" is wrong (`DESIGN.md` §5.18: 4 s plain / 6 s with Undo; tombstone for the session; Trash 30 days).
- "1.3× font scale" is wrong: 200% (`DESIGN.md` §4.3, §11.1; `pr.md` §6.22).
- RTL checklist "no hardcoded `start`/`end` or `left`/`right`" is wrong: logical `start`/`end` are required; physical `left`/`right` are banned (`DESIGN.md` §11.5, §14.5).
- Its file tree (`app/src/main/kotlin/com/openhealth/…`, `:design`) does not match `Tech-Stack.md` §13 (`app-android/`, `ui/`, `core/`, …), and its `OHBottomNavigation` builds six tabs; the contract is five tabs with a raised Train and top-bar Settings (`DESIGN.md` §4.4, §5.15).
- The snippets are illustrative and do not compile as written (for example `Modifier.border(bottom = …)`, undefined `isDarkTheme`, `hiltViewModel()` without Hilt). Rebuild from `DESIGN.md` §5 and Appendix B.

### `BUILD-ROADMAP.md` is stale outside its sequence

- Stack: Hilt/Room/Material 3 setup (above), and an `:app`/`:core`/`:data`/`:feature:*`/`:design` layout where `Tech-Stack.md` §13 defines `core/`, `data/`, `sync/`, `integrations/`, `ui/`, `app-android/`, `app-wear/`, `server/` and `tools/`.
- Navigation: the body still says six tabs, a More screen and 6 destinations (the 2026-11-22 banner at the top corrects this); the contract is five tabs with Train raised and Settings in the top bar (`DESIGN.md` §4.4, §5.15).
- A11y: "WCAG 2.1 AA" and "1.3× font scale" throughout; the contract is WCAG 2.2 AA at 200% (`pr.md` §6.22; `DESIGN.md` §11). A11y is a per-screen gate from Phase 1 (`DESIGN.md` §14.5), not a Phase 6 retrofit.
- Themes: light theme deferred to Phase 6; `DESIGN.md` ships dark canonical and light as an equal from 1.0 (§12.1–12.2).
- Telemetry and distribution: Crashlytics and a Play Integrity token in Beta; the contract is no telemetry and a Play-Services-free F-Droid build (`pr.md` §6.31; `Tech-Stack.md` §4, §12).
- Sync: "last-write-wins + source priority"; `Tech-Stack.md` §6 requires per-entity merge policies, tombstones and surfaced conflicts.
- Undo: Phase 2 says "undo toast (3–5 s)"; the contract is a session-length tombstone plus 30-day Trash (`DESIGN.md` §5.18; `pr.md` §6.29).
- Calculations: the readiness formula `(sleep % × 0.4) + (HRV % × 0.3) + (RHR % × 0.3)` appears only in `BUILD-ROADMAP.md`; `pr.md` §6.10 requires an explainable score and `DESIGN.md` §14.2 an explanation line, but neither defines the formula. Treat it as a proposal, not a contract.
- Devices: "Health Kit (Android 12+)" is wrong — the Android bus is Health Connect; HealthKit is Apple and gated. Oura/Galaxy Watch pairing in Phase 5 also contradicts `pr.md` §9's Phase 2/3 placement.
- Server timing: `BUILD-ROADMAP.md` puts the sync engine and server in Phase 5, `Tech-Stack.md` §2 puts the server in Phase 1, and `pr.md` §9 puts multi-device sync and managed hosting in Phase 3. Only the stack choice is settled; the timing is not.
- Vocabulary: "V2" does not exist; use `Later`.
- Citations: `Tech-Stack.md` §8.2 in Phase 5 does not exist (the server is §7). "SVG-to-Compose" conflicts with `DESIGN.md` Appendix B's path renderer, and the review gate points at dead `WIREFRAMES.md` §end — use `DESIGN.md` §14.5.
- Component count: "All 10 components" versus `DESIGN.md`'s 19.

### `pr.md`'s own stale spots

`pr.md` §13.1 is marked decided, but §13.2 (local DB), §13.3 (server) and §13.5 (licence) still read as open and list options `Tech-Stack.md` has rejected. `pr.md` §8.3 still says "Node/TypeScript or Go" even though §8's introduction says 8.1–8.4 are superseded. `Tech-Stack.md` wins; do not reopen settled questions. If the task is to update the contract, fix these and record the change rather than routing around them.

### Reference library count

`references/README.md` and `references/MANIFEST.md` index 41 folders (`openGym` plus 40 upstream); the working tree has 38. `references/wger` and `references/xDrip` are missing. Verify with `Test-Path` or a directory listing before citing a project. `references/openGym` is the only full (non-shallow) clone at ~747 MB; every other checkout is `--depth 1`.

## Dead references (do not chase them)

`BUILD-PLAN.md`, `WIREFRAMES.md`, `wireframes.html`, `wireframes-*.svg`, `CLAUDE.md`, `docs/adr/` and the old mockup export **do not exist**, though the live docs still cite them.

- `BUILD-PLAN.md` was replaced by `BUILD-ROADMAP.md`. `pr.md` §0.1 cites its §2; `Tech-Stack.md`'s header, §10, §13 and §15 cite it too.
- `WIREFRAMES.md` / `wireframes.html` pointers in `BUILD-ROADMAP.md` and `COMPOSE-GUIDE.md` are dangling. The screen review gate is `DESIGN.md` §14.5; the inventory and recipes are §14.1–14.2.
- `docs/adr/` is the intended home for dated closed decisions (`Tech-Stack.md` §13–14) but does not exist. `Tech-Stack.md`'s header links a non-existent `docs/adr/0001-android-first.md`.
- There is no root `README.md`; `references/README.md` is the study-library index, not product documentation.

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
| Build order | `BUILD-ROADMAP.md` phases (UI only) |
| Open decisions | `pr.md` §13 |

## Open decisions

`pr.md` §13 is the register. `Tech-Stack.md` §15 closes items 1, 2, 3 and 5. Genuinely open: 4 account model; 6 medical scope; 7 AI provider strategy; 8 first devices; 9 first languages; 10 brand/domain; 11 offline food index; 12 measurement without telemetry; 13 business model; 14 minors; 15 encryption and recovery; 16 nutrition in the MVP; 17 iOS start (deferred, reviewed at every phase exit).

No live document owns them: `Tech-Stack.md` §15 still says they are "owned by `BUILD-PLAN.md` §2", and `BUILD-ROADMAP.md` does not answer them. Do not invent an answer in code, a screen or a plan. Record a decision in `pr.md` §13 and propagate it to the other files.

## Commands

```sh
# Verify a reference checkout before trusting it
git -C references/<name> log -1 --oneline

# Update one shallow reference clone
git -C references/<name> pull --ff-only

# Search the study library without drowning in .git objects
rg -n --glob '!**/.git/**' '<pattern>' references/<name>
```

If `git` or `rg` is not on PATH (neither is currently available on this workstation), use PowerShell:

```powershell
Test-Path references\wger
Get-ChildItem references -Directory | Select-Object -ExpandProperty Name
Select-String -Path *.md -Pattern 'WIREFRAMES|BUILD-PLAN|docs/adr'
```

`references/` is ~5 GB and gitignored except `README.md` and `MANIFEST.md`. **Never `git add -A` from the repo root without checking `git status` first**, and never commit anything under `references/`. The working tree may hold deliberate deletions of superseded docs; do not restore them with `git checkout .`.

## Reference library

41 folders are indexed (`references/README.md`, `references/MANIFEST.md`); 38 are checked out, and `openGym/` is the closest reference for the training domain, the sync model and the design language (`DESIGN.md` §0.1). They are read, not imported: most are GPL/AGPL, `SparkyFitness` and `HealthLog` are source-available non-commercial, and food/clinical/media data carry separate terms (`pr.md` §12.2). `openGym`'s code is AGPL and its exercise stills/animations are licensed from Gym visual and **not reusable**; the only asset that may cross is the MIT MuscleMap body geometry (`DESIGN.md` Appendix C).

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
- Do not fix unrelated stale cross-references in passing. Correct the text your change touches and flag the rest; a docs sweep is its own task.
- Do not add a new document, section, token, component, metric or licence without an explicit request. `docs/adr/` gets its first file only when a decision is actually closed.
- UI copy bans "OK", "Submit", "Yes/No", "Are you sure?", exclamation marks, streak guilt, em dashes, and any wording that diagnoses or shames (`DESIGN.md` §11.7). This restriction is for product copy, not these documents.
