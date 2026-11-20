# openHealth — Phase-wise Build Plan

**Version:** 2.0 · 2026-11-20 — **Android only for the first releases** (`pr.md` §0.1)
**Companions:** [`pr.md`](pr.md) (what to build) · [`DESIGN.md`](DESIGN.md) (how it looks) · [`Tech-Stack.md`](Tech-Stack.md) (what to build it with)
**Assumed team:** 2–3 full-time engineers plus a part-time designer. Every duration below scales roughly linearly; a solo builder should multiply by 2.

---

## 0. How to use this file

- Work the phases in order. A phase is not finished when its features exist; it is finished when its **exit test** passes on a real device with no network.
- Each phase lists **Build**, **Do not build**, **Exit test** and **Gates**. The "do not build" list is as binding as the build list — it is what keeps the dates real.
- Every feature is done only when it meets the four conditions in §1.
- Decisions that must be made before a phase starts are in §2. A phase does not start with an open gate.

---

## 1. Definition of done (every feature, every phase)

A feature ships when all four are true:

1. **Spec** — it satisfies its acceptance criteria in `pr.md` §6, including the offline and provenance clauses.
2. **Design** — it uses only tokens and components from `DESIGN.md`; no new color, size or component was invented.
3. **Proof** — it has: unit tests for its rules, a screenshot test for each state (loaded, empty, offline, error), and an entry in the E2E flow for its phase.
4. **Reversible** — every write it performs is undoable, exportable, and appears in the timeline.

Non-negotiable on every screen from Phase 0 onward: WCAG 2.2 AA (`DESIGN.md` §10), works offline, 44px targets, no telemetry.

---

## 2. Gates — decide these before writing the code they touch

From `pr.md` §13. Recommended answers are given so the project can move; record the chosen answer in `docs/adr/` with a date.

| # | Decision | Must be closed before | Recommendation |
|---|---|---|---|
| 5 | **Licence split** | the **first public commit** | AGPL-3.0 server, Apache-2.0 client (`pr.md` §12.4). Irreversible once contributors arrive. |
| 1 | Client stack | Phase 0, day 1 | **Closed:** native Android, Kotlin + Compose, over KMP-ready core modules (`Tech-Stack.md` §2–3). |
| 2 | Local DB + sync | Phase 0, day 1 | SQLite/SQLDelight + custom append-only log (`Tech-Stack.md` §5–6). |
| 3 | Server stack | Phase 0 (schema shared) | Kotlin/Ktor + Postgres. |
| 4 | Account model | Phase 0 | Local-only first; server optional; family in Phase 3. |
| 6 | Medical scope | Phase 0 (it edits the feature list) | Wellness only. Every clinical feature ships as neutral display against a cited range, or not at all (`pr.md` §12.1). |
| 15 | Encryption + recovery | Phase 0 (schema and key handling) | At-rest encryption now; E2EE opt-in in Phase 3, recovery code designed first (`pr.md` §8.7). |
| 14 | Minors | Phase 0 (profile schema) | Block under 16 at launch; revisit with pediatric ranges later. |
| 16 | Nutrition in MVP | end of Phase 0 | **Split it.** Phase 1A = training, body, steps. Phase 1B = nutrition. See §4. |
| 11 | Offline food index | Phase 1B | Region bundle ≤ 25 MB, ODbL attribution in app and exports. |
| 8 | First devices | Phase 1 (hardware purchase lead time) | Health Connect, one BLE scale, one BLE cuff, one HR strap, one Wear OS watch. |
| 12 | How §10 metrics are measured | Phase 1 | Recruited beta cohort + opt-in aggregate only; delete metrics that cannot be measured honestly. |
| 7 | AI provider strategy | Phase 3 | BYOK, provider-agnostic, per-category consent. |
| 9 | First languages | Phase 2 | English + one RTL language, to force the RTL work early. |
| 13 | Business model | before Phase 2 | Decides whether a server team exists, therefore whether Phase 3 exists (`pr.md` §12.5). |
| 10 | Brand, domain, store presence | before Phase 2 | Play Store **and** an F-Droid-compatible build (`Tech-Stack.md` §4). |
| 17 | **iOS start** | reviewed at every phase exit | Not yet. Reopen when Android adoption, a dedicated engineer, or funding makes it real. Until then nothing iOS-shaped is built, and §3's portability discipline is what keeps it cheap (`pr.md` §0.1). |

**Long-lead items that must be started in Phase 1, not when they are needed:** Garmin, Fitbit, Oura, WHOOP, Dexcom partner API applications; the **Play Console health-apps declaration** and Health Connect permission review; Max Rubner-Institut licence enquiry for BLS if German food data is wanted (`pr.md` §12.2, §12.3). App Store review notes are deferred with iOS.

---

## 3. Phase 0 — Foundations · 4–6 weeks

**Goal:** the skeleton that every later phase hangs on, and the proof that data survives a round trip.

### Build
- **Repo and CI** — monorepo per `Tech-Stack.md` §13, lint, test, screenshot test and build on every PR; signed debug builds to testers.
- **Design system in code** — `DESIGN.md` tokens as the single theme source; the 19 components of §5 built, documented in a gallery screen, and screenshot-tested in light mode, at 200% text, and in RTL.
- **Data model, all five layers** (`pr.md` §7) — raw ingestion → canonical observations → daily rollups → domain objects → settings. Migrations and a migration test from day one.
- **Provenance plumbing** — every write path carries source, device, method, happened-at, recorded-at, timezone, idempotency key, raw-record reference. No table may store a value without them.
- **Local database** — encrypted SQLite, WAL, backup and restore, integrity check on open.
- **Profile, units, timezone, modules** — including the module switchboard (`pr.md` §6.21) so later modules plug in instead of being bolted on.
- **Shell** — six tabs, navigation, sheet pattern, quick add with the note type only, empty states.
- **Today and Timeline skeletons** — reading real rollups, even with one event type.
- **Export/import v0** — full JSON export and a restore that rebuilds the database; CSV export for whatever exists.
- **Undo, trash, 30-day recovery** (`pr.md` §6.29) — built once, used by everything after.
- **Privacy baseline** — no network calls at all in the local-only path, app lock, no analytics dependency in the build.

### Do not build
Training, food, vitals, charts, sync, server, any integration, any AI.

### Exit test
On a flight-mode device: create a profile → log a note → see it on Today and in the timeline with full provenance → export → wipe the app → restore → the note and its provenance are byte-identical. Cold start under 1.5 s.

---

## 4. Phase 1 — MVP · split in two · 5–7 months total

`pr.md` §9 already concedes that the single MVP is not 3–4 months of work. Split it, ship 1A, and let real use correct 1B.

### Phase 1A — Train, Body, Steps · 3–4 months

**Goal:** replace a gym app, a scale app and a step app.

#### Build
| Area | Scope (`pr.md` ref) |
|---|---|
| Strength training | Exercise library, custom exercises, routines, session flow, fast set logging, warm-up/work/back-off/drop sets, supersets, plate math, one equipment profile, linear + double progression + off, deloads, stalls, PR detection, e1RM, rest timer, session summary, edit/move/delete any workout (§6.5 MVP) |
| Body | Weight check-ins with the one-per-day merge rule, trend, weekly rate, goal line and projection, composition fields, circumferences, custom measurements, progress photos, CSV import/export (§6.8 MVP) |
| Steps | Phone sensor, Health Connect read, daily rollups with timezone handling, **source priority and dedup — one source wins the day, never the sum**, manual correction, exclude-a-day (§6.6 MVP) |
| Today | Readiness card (from what exists), plan, steps, weight, due items, quick actions (§6.2 MVP, minus nutrition) |
| Timeline | All of §6.27 MVP: filters, provenance in one tap, backfill with happened-at vs recorded-at, undo from the row |
| Insights | Weight, steps and volume trends; weekly summary; PRs; chart-as-table alternative (§6.16 MVP, partial) |
| Data | JSON + CSV export, CSV import, WebDAV backup, encrypted backup file (§6.17 MVP, minus food) |

#### Screens (already designed)
`Onboarding` · `Main` (Today) · `QuickAdd` · `Timeline` · `Train` · `Session` · `Body` · `Insights` · `Settings` — see `DESIGN.md` §14.

#### Do not build
Nutrition, vitals, sleep detail, yoga, activities/GPS, watch apps, server sync, AI, any partner integration.

#### Exit test
Four weeks of a real training block logged by a real person, entirely offline, on the reference mid-range Android phone: a set logs in under 2 s one-handed; a killed app loses nothing; a progression never advances load after a missed rep; the export rebuilds the account on a second device.

### Phase 1B — Nutrition · 2–3 months

#### Build
Diary by day and meal; food search over the bundled offline index + Open Food Facts + USDA; barcode scan with a clear found/not-found state; serving units; quick add; custom foods and recipes; copy a meal or a day; targets for calories, protein, carbs, fat, fibre, water; micronutrient panel where the data supports it; water with configurable containers; fasting timer; allergies and dietary patterns; CSV/JSON import and export (§6.3 MVP). Energy model: BMR (Mifflin-St Jeor, formula visible), TDEE, MET-based exercise energy shown **as a range**, goal modes, safety floors, weekly averages (§6.4 MVP).

#### Also in 1B
The food index pipeline (`tools/`): build, licence-stamp and ship a regional bundle; attribution surfaced in the app and in every export (`pr.md` §12.2).

#### Screens
`Food` · `FoodSearch`, plus the nutrition card on `Main`.

#### Exit test
Barcode to logged food in under 5 s warm; the diary works offline for cached foods; a day's totals equal the sum of its entries with no hidden rounding; every entry shows its source and whether the user verified it.

### Phase 1 cross-cutting (runs through 1A and 1B)
- Sync v1 to an optional self-hosted server: append-only log, idempotent, conflict-aware, never blocking a write. Two devices and one server, converging.
- Performance budgets enforced in CI (`Tech-Stack.md` §12).
- Partner API applications filed (§2 long-lead).
- Beta cohort recruited, because §10 metrics cannot be collected silently.

---

## 5. Phase 2 — Depth · 3–4 months

**Goal:** a person with a wearable and a home cuff replaces three more apps and walks into an appointment with a clean report.

### Build
- **Vitals and clinical records** (§6.9 MVP): BP with arm/position/cuff and home-average protocol, HR, SpO2, temperature, glucose with context, medications and supplements with adherence, symptoms, allergies, conditions, vaccinations, manual + CSV labs with **cited** reference ranges, doctor-ready PDF and CSV.
- **Sleep and recovery** (§6.10 MVP): duration, stages where the wearable gives them, debt, consistency, RHR and HRV trends, explainable readiness, fatigue map, deload suggestion.
- **Yoga and mobility** (§6.7 MVP).
- **Activities** (§6.11 MVP): record GPS activities, import FIT/GPX/TCX, maps, pace/HR, basic load.
- **Reports**: weekly review, monthly summary, doctor PDF generated **on device**, FHIR R4 and Open mHealth export.
- **Wear OS app** (§6.18 V1): workout control, set logging, rest timer, heart rate, tiles. It shares `core/` verbatim (`Tech-Stack.md` §4).
- **Notifications and reminders** (§6.19), search and command palette (§6.20).
- **Localization**: second language live, RTL verified on every screen.

### Do not build
Partner cloud integrations, family sharing, AI, managed hosting.

### Exit test
A doctor report covering 90 days of BP, medication adherence, labs and weight generates offline in under 30 s, reads correctly on paper, and every number in it links back to a timeline entry with its source. A wearable disconnecting mid-workout loses nothing.

### Gate
Clinical wording review against `pr.md` §12.1 before any range, alert or interpretation ships. Anything that cannot be phrased as "your data against this cited range" is cut, not softened.

---

## 6. Phase 3 — Ecosystem · 3–4 months

**Goal:** a household can share selectively and an assistant can read and write with scoped consent.

### Build
- **Partner integrations**, in the order the approvals arrive: Strava (read/display only, per their terms), Garmin, Fitbit, Withings, Oura, WHOOP, Polar, Dexcom, Nightscout, Omron.
- **Family and caregiver** (§6.14): profiles, granular consent, caregiver mode, audit log.
- **AI Coach** (§6.15 MVP): off by default, BYOK, reads only opted-in categories, proposes and never applies, explains, validates against the data model, snapshots and undo. **Health Connect-derived data excluded from provider calls unless separately opted in** (`pr.md` §12.3).
- **MCP server** with scoped tokens; webhooks; calendar feed; WebDAV and scheduled backups; API tokens with category scopes.
- **Multi-device sync hardening**: three devices, conflicting edits, long offline periods, schema version skew (§7.9).
- **Managed hosting** if §2 gate 13 said so.
- **E2EE opt-in** with the recovery model from §8.7, recovery code designed and tested before the encryption ships.

### Exit test
Two people, three devices, one self-hosted server, two weeks offline on one device: no lost or duplicated records, every conflict surfaced and resolvable. An external assistant reads a month of training through MCP with a scoped token and cannot see vitals.

---

## 7. Phase 4 — Depth and trust · ongoing

Advanced correlations with honest confidence; community programmes and flows (authored, not user-generated — moderation is staffed work); research export with consent; on-device models for search, summaries and natural-language logging; **independent security audit**; accessibility and localization excellence. The **iOS client, desktop and web** land here too, once gate 17 opens — and only then.

Nothing in Phase 4 starts before the Phase 3 exit test passes.

---

## 8. Tracks that never stop

| Track | Cadence | Rule |
|---|---|---|
| Accessibility | every PR | Screenshot tests at 200% text and RTL; keyboard and screen-reader pass per screen |
| Performance | every PR | Budgets in CI; a regression blocks merge (`Tech-Stack.md` §12) |
| Provenance | every write path | No new table or importer without source, method, timestamps, idempotency key |
| Export completeness | every phase exit | "Rebuild the account elsewhere from the export" is a test, not a promise |
| Security | monthly | Dependency audit, permission review, secret scan; audit before Phase 4 |
| Licence hygiene | every dependency and dataset | Record licence, version and attribution in app and exports (`pr.md` §12.2) |
| Data-quality rules | every importer | Duplicate detection, correction path, "also seen / not used" surfaced (§6.28) |

---

## 9. Risk register

| Risk | Signal it is happening | Response |
|---|---|---|
| MVP slips past 9 months | Phase 1A exit test keeps moving | Ship 1A alone; 1B becomes Phase 2A |
| Sync eats the schedule | Merge rules keep changing | Freeze the merge policy per entity type; write property tests before more features |
| Partner APIs never arrive | Approvals stalled at Phase 3 | File-based import (FIT/GPX/CSV/archives) is the fallback and was always the honest promise |
| Clinical scope creep | A feature says "risk", "detect" or "warn" | §12.1 review; rephrase to cited display or cut |
| Food licensing | BLS or MET table blocked | USDA + Open Food Facts only; cut the dataset, not the date |
| Store rejection | Health permissions or licence clash | Gate 5 closed early; Play health-use-case declaration filed in Phase 1 |
| Design drift | New hexes or sizes appear in PRs | `DESIGN.md` §13 deltas are the only allowed exceptions; lint the token set |
| Android-only narrows the beta | Too few testers, or iOS-only demand from the people who want this product | Recruit the cohort from Android-first communities (F-Droid, self-hosters); file-based import means iPhone owners can still move their history in. Gate 17 is reviewed every phase |
| Portability discipline erodes | `android.*` creeps into `core/`, `data/` or `sync/` | The CI check in `Tech-Stack.md` §11 (portability rules in §3) fails the build; it is not a review comment |

---

## 10. Scope control

The cut list in `pr.md` §14.5 is closed. Form-check AI, VBT, photo body composition, smart alarm, safety beacon, live group sessions, community libraries, restaurant partnerships, insurance import, multi-agent coaching and real-time collaboration do not re-enter without a written argument that names what they displace.

A feature enters a phase only if it makes **the daily loop faster, the trend clearer, or the data more trustworthy**. Anything else waits.
