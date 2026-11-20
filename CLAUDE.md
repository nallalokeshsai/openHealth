# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

openHealth is a private, local-first home for every health signal (training, nutrition, steps, body, vitals, sleep, activities). **There is no application code yet.** The repository currently holds the product contract, the design system, the build sequence, the stack decision, and a read-only study library of 41 upstream projects.

**The first releases are Android only** (`pr.md` §0.1, `docs/adr/0001-android-first.md`): native Kotlin + Jetpack Compose over platform-neutral core modules, with a Kotlin/Ktor server. iOS, watchOS, web and desktop are deferred behind the gate in `pr.md` §13.17 — do not write, design or plan for them until it opens.

Do not scaffold an app, add a package manager, or start a framework unless asked. When the build does start, it follows `BUILD-PLAN.md` Phase 0 and the repo layout in `Tech-Stack.md` §13.

## The four documents and their precedence

| File | Owns | Rule |
|---|---|---|
| `pr.md` | What gets built, and why. Feature spec with MVP/V1/Later tags, data model (§7), constraints (§12), open decisions (§13), cut list (§14.5) | **The contract.** Behaviour disputes resolve here |
| `DESIGN.md` | Every color, size, component, state, chart and accessibility rule | Wins on *appearance*; `pr.md` wins on *behaviour* |
| `BUILD-PLAN.md` | Phase order, per-phase scope, exit tests, gates, risk register | Nothing starts with an open gate (§2) |
| `Tech-Stack.md` | Stack decision (closes `pr.md` §13 items 1, 2, 3, 5), the portability discipline (§3), storage, sync design, Android integrations, budgets | Supersedes `pr.md` §8.1–8.4 |

Screens are mocked at the canvas linked in `DESIGN.md` §14 — that is the visual source of truth, not the 3 MB `openHealth App Mockups.html` export sitting untracked at the root.

## Commands

No build system, linter or test runner exists yet. Until Phase 0 creates the Gradle project (`Tech-Stack.md` §11), the useful commands are:

```sh
# Verify a reference checkout before trusting it
git -C references/<name> log -1 --oneline

# Update one shallow reference clone
git -C references/<name> pull --ff-only

# Search the study library without drowning in .git objects
rg -n --glob '!**/.git/**' '<pattern>' references/<name>
```

`references/` is ~5 GB and gitignored except `README.md` and `MANIFEST.md`. **Never `git add -A` from the repo root without checking `git status` first**, and never commit anything under `references/`.

## Invariants that cut across every file

These are decided. Changing one means editing the contract, not working around it.

- **Provenance on every value.** Source, device, method, happened-at, recorded-at, local date, timezone, idempotency key, raw-record reference. A number without these does not ship (`pr.md` §2.2, `DESIGN.md` §7).
- **`happened_at` ≠ `recorded_at`.** Backfill never rewrites when something was recorded; it recomputes the affected day's rollups.
- **Five data layers** (`pr.md` §7): raw ingestion → canonical observations → daily rollups → domain objects → settings. Rollups are always rebuildable from observations; fix bugs by rebuilding, never by patching rollups.
- **Deduplication:** one configured-priority source wins a day. Never the sum, never the largest value (`pr.md` §7.6).
- **Local-first:** no core action waits for a network. The server is optional and self-hostable forever.
- **Not a medical device.** Clinical features ship as a neutral display of the user's own data against a **cited** published range, or not at all. Watch the words "risk", "detect", "warn", "screen" — `pr.md` §12.1 governs wording as much as behaviour.
- **No telemetry, ever.** That constrains how success metrics in `pr.md` §10 can be measured (device lab + recruited beta cohort only).
- **Android only, portable underneath.** `core/`, `data/`, `sync/` and the importer half of `integrations/` must contain no `android.*` or `androidx.*`, so the deferred iOS port stays additive (`Tech-Stack.md` §3). This is CI-enforced, not a style preference.
- **Licence split:** AGPL-3.0 for `server/`, Apache-2.0 for the client — must be settled before the first public commit (`pr.md` §12.4).
- **The cut list (`pr.md` §14.5) is closed.** Form-check AI, VBT, photo body composition, smart alarm, safety beacon, live group sessions, community libraries and the rest do not return without a written argument naming what they displace.

## Design work

`DESIGN.md` is a closed token set: use its hexes, its 11-step type scale, its 9 spacing values, its 6 radii and its 19 components. Do not invent a sibling shade or a new component variant. Three deliberate exceptions are listed in `DESIGN.md` §13 — the mockups are wrong there and the spec is right. Sizes are dp/sp in Compose at 1:1 with the px in that file; the CSS notation is the mockups' language, and §4.3 maps the platform specifics (insets, predictive back, font scale). Light mode only so far; the dark palette in §12 is derived, not contrast-verified.

## Using the reference library

41 upstream projects under `references/`, indexed by category in `references/README.md` with per-clone provenance in `references/MANIFEST.md`. They exist to be read, not imported.

- Copying code carries the upstream licence. Most are GPL/AGPL family; a client that must ship Apache-2.0 cannot absorb them.
- `SparkyFitness` and `HealthLog` are **source-available, non-commercial** — study their schema and feature ideas only.
- `openGym/` is the prior local-first training app and the closest reference for the training domain and sync model.
- Food, clinical and media data each carry separate terms (ODbL, CC0, CC BY, LOINC registration, Garmin FIT SDK). `pr.md` §12.2 is the list that matters.

## Working style for this repo

- These documents are dense and cross-referenced. When changing one, check whether `pr.md` §13 (open decisions) or `BUILD-PLAN.md` §2 (gates) records the same decision, and update both.
- Cite sections when you make a claim about scope (`pr.md` §6.5 MVP), because the tags — MVP, V1, Later — are the schedule.
- Closed decisions belong in `docs/adr/`, dated, one file each (`BUILD-PLAN.md` §2).
