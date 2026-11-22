# openHealth — Technology Stack

**Version:** 2.1 · 2026-11-22 (navigation row: five tabs; Garmin is the primary watch, see `pr.md` §6.18) — **Android only for the first releases** (`pr.md` §0.1)
**Status:** Closes `pr.md` §13 decisions 1, 2, 3 and supersedes `pr.md` §8.1–8.4.
**Companions:** [`pr.md`](pr.md) · [`DESIGN.md`](DESIGN.md) · [`BUILD-PLAN.md`](BUILD-PLAN.md) · ADR [`docs/adr/0001-android-first.md`](docs/adr/0001-android-first.md)

---

## 1. What the stack has to survive

1. **Device access is the product.** Background step counting, Health Connect, BLE scales, cuffs, straps and CGMs, camera barcode scanning, GPS recording, a Wear OS app, widgets, an ongoing workout notification. A plugin layer between the app and these APIs is a liability.
2. **Correctness is shared.** Dedup rules, merge policies, unit conversion, energy formulas, progression policies, rollups. If the app and the server implement these twice they will disagree, silently.
3. **Local-first, never blocked.** Every write lands in the local database first. The server is optional and may never exist for a given user.
4. **Five-year histories stay fast.** Sub-2-second set logging, sub-300 ms warm Today, over hundreds of thousands of rows.
5. **Self-hosting is a one-liner.** Docker Compose on a cheap box, no vendor cloud.
6. **Open formats and no Google dependency.** FIT/GPX/TCX, FHIR R4, LOINC, UCUM, ODbL/CC0 food data, OAuth 2.1, MCP — and a build that runs without Play Services so it can ship on F-Droid as well as Play.
7. **iOS later must be additive, not a rewrite.** Today's code must not make tomorrow's port expensive.

---

## 2. The decision

**A native Android app in Kotlin and Jetpack Compose, over platform-neutral Kotlin core modules, with a Kotlin/Ktor server that compiles those same modules.**

| Layer | Choice | When |
|---|---|---|
| Core modules (domain, policies, storage, sync, importers) | Pure Kotlin, KMP-ready (§3) | Phase 0 |
| Phone app | Kotlin + Jetpack Compose | Phase 0 |
| Widgets | Glance | Phase 1 |
| Watch | Wear OS + Compose for Wear | Phase 2 |
| Server | Kotlin + Ktor + PostgreSQL, reusing the core modules | Phase 1 |
| iOS, watchOS, web, desktop | **Deferred behind the gate in `pr.md` §13.17** | — |

### Why this, now

- **No cross-platform problem to solve.** Android-only removes the single argument for Flutter, React Native or Capacitor, while every cost they carry — a plugin between you and Health Connect, a second runtime, no first-class Wear story — stays. Native is strictly better here.
- **The shared-correctness win is available today, without iOS.** The core modules compile for both the app and the JVM server, so the dedup rule, the merge policy, the UCUM conversion and the progression policies exist once.
- **Health Connect, Wear OS, Glance, CameraX, BLE and foreground services are Kotlin-first.** You are never waiting on a plugin maintainer.
- **Compose renders `DESIGN.md` directly.** One theme object, used by phone and watch.
- **An Android-only launch is a real strategy for this product.** Health Connect is a better platform bus than HealthKit for an open app, sideloading and F-Droid serve the self-hoster persona, and one store review loop instead of two buys weeks back.

### What it costs

No iOS users at launch, and the iOS port is real work when it comes. The mitigation is §3's discipline, not optimism: the expensive half — schema, sync, formulas, importers — is written so it compiles unchanged for iOS, and only the UI and the platform seams are rewritten.

### Rejected

| Option | Why not |
|---|---|
| Flutter | Buys cross-platform the project no longer needs; adds a plugin layer in front of Health Connect and BLE; Wear OS support is an afterthought; the server would still need the rules implemented a second time. |
| React Native / Expo | Same, plus a JS runtime, for a sensor-heavy local-first app. |
| React + Capacitor PWA (`pr.md` §8.1 Option A) | Weakest exactly where this product lives: background sensors, BLE reliability, watch, foreground services. Still the right shape for the *later* web client, as a client of the API. |
| Kotlin Multiplatform with Compose Multiplatform UI today | The right answer *when iOS is in scope*. With no iOS, the iOS-shaped build complexity is cost without benefit — so the modules are written KMP-ready and the KMP targets are switched on the day the gate opens. |
| Room instead of SQLDelight | Fine on Android alone, but it pulls `androidx` into the storage module and weakens §3. |

---

## 3. The portability discipline (non-negotiable)

This is what keeps "iOS later" cheap. It is enforced by module boundaries and a CI check, not by good intentions.

In `core/`, `data/`, `sync/` and the API half of `integrations/`:

- **No `android.*` or `androidx.*` imports.** CI fails the build on one.
- **No `Context`, no `Activity`, no Android `Resources`** — pass data in, return data out.
- `kotlinx-datetime`, not `java.time`. `kotlinx.serialization`, not Gson or Moshi. `kotlinx.coroutines` only.
- **SQLDelight**, not Room. **Koin**, not Hilt. **Ktor client**, not Retrofit/OkHttp-specific types in signatures.
- Every platform capability sits behind an interface declared in core and implemented in `app-android/` or `integrations/android/`: health bus, step sensor, BLE, camera, files, crypto/keystore, notifications, PDF. Define the seam **even when only the Android implementation exists**.
- Strings and formatting: the core returns structured values and keys; the app formats them.

Day one of the iOS port is "add the KMP targets and write the actuals", not "find where the business logic leaked".

---

## 4. Client stack (Android)

| Concern | Choice | Notes |
|---|---|---|
| Language | Kotlin 2.x | JDK 21 toolchain |
| UI | Jetpack Compose, Compose BOM | Material components are **not** the look; `DESIGN.md` tokens drive a custom theme. Material 3 is used only as behaviour primitives where it saves work |
| Navigation | Navigation Compose, type-safe routes | Five tabs (raised centre Train) + top bar + sheet layer (`DESIGN.md` §4.3, §5.15); predictive back supported |
| State | Coroutines + Flow, one state holder per screen | Unidirectional; no extra reactive framework |
| DI | Koin | No annotation processing, KMP-ready |
| Charts | Hand-built Compose canvas | Library charts cannot honour `DESIGN.md` §6; each chart is ~150 lines |
| Images | Coil 3 | Progress photos stay in app-private encrypted storage |
| Widgets | Glance | Mirrors the Today card |
| Watch (Phase 2) | Compose for Wear OS + Data Layer | Shares `core/` verbatim |
| Background | WorkManager; foreground service (`health`, `dataSync`, `location` types) for live sessions and GPS | The rest timer is an ongoing notification with actions |
| Distribution | Play Store **and** a Play-Services-free build for F-Droid | No Firebase, no GMS-only API on a core path |

---

## 5. Local storage

- **SQLite** via **SQLDelight 2.x** — typed queries, migrations as files, no `androidx` in the storage module.
- **Encryption at rest:** SQLCipher; key in the Android Keystore, unlocked by `BiometricPrompt` when app lock is on.
- **WAL mode**, integrity check on open, automatic local backup before every migration, `allowBackup=false` (the app owns its export path).
- **Schema = `pr.md` §7's five layers**, physically separated: `raw_record` (immutable payloads) → `observation` (canonical, UCUM units, full provenance columns) → `daily_rollup` (derived, rebuildable) → domain tables → `settings`.
- **Rollups are always rebuildable from observations.** Backfill recomputes affected days; bugs are fixed by a rebuild, never a patch script.
- **Media** in app-private storage, encrypted, never in the database.

**Indexing rule:** every UI query hits an index on `(local_date)` or `(type, happened_at)`. Benchmarked against a seeded five-year corpus in `tools/fixtures`, not an empty database.

---

## 6. Sync

**Custom append-only change log with per-entity merge policies**, living in `sync/` and compiled into both app and server.

- Every write appends an immutable record: entity, operation, payload, **idempotency key**, **hybrid logical clock**, device id, schema version.
- Pull by cursor; push is idempotent, so a retry after a dropped connection is free.
- **Merge policy per entity type, declared in `core/`:** one weight per day merges by the configured rule; sets are append-only; notes are last-writer-wins with both versions retained; rollups are never synced, always recomputed.
- Conflicts a policy cannot settle surface in the UI with both sources and their provenance. Never resolved silently.
- Transport: Ktor client, `kotlinx.serialization` JSON, gzip, HTTPS only. Unknown fields round-trip untouched (`pr.md` §7.9).

**Rejected:** PowerSync / ElectricSQL (they assume Postgres-shaped replication and move merge decisions out of the domain layer, which is where this product's correctness lives); CRDT libraries (overkill outside free-text notes).

---

## 7. Server (optional, self-hostable)

| Concern | Choice |
|---|---|
| Runtime | Kotlin + Ktor 3.x, compiling `core/`, `data/` and `sync/` for the JVM |
| Database | PostgreSQL 16+ |
| Migrations | Flyway |
| Object storage | S3-compatible; MinIO in the default Compose file |
| Auth | Passkeys/WebAuthn (webauthn4j) primary, password fallback, OIDC optional |
| Tokens | Scoped, category-level, revocable; OAuth 2.1 + PKCE for integrations |
| TLS | Caddy with automatic certificates |
| Deploy | `docker compose up`; Helm chart later |

The server stores and relays. It never becomes required for a core action.

---

## 8. Android platform integrations

| Capability | API | Notes |
|---|---|---|
| Steps, sleep, HR, weight import | **Health Connect SDK** | Declared permissions + the Play Console health-apps declaration; `minSdk 28` is set by Health Connect |
| Live step counting | `Sensor.TYPE_STEP_COUNTER` | Reboot-safe counter handling; the rollup owns dedup against Health Connect (`pr.md` §7.6) |
| Activity permission | `ACTIVITY_RECOGNITION` | Requested at the moment steps are first enabled, never at launch |
| BLE devices | Android BLE (`BLUETOOTH_SCAN`/`CONNECT`) | Standard GATT: weight 0x181D, BP 0x1810, HR 0x180D, glucose 0x1808 |
| Barcode | CameraX + **ML Kit barcode, bundled model** | Bundled keeps it offline and GMS-free |
| GPS activities | Fused or platform location + foreground service | FIT/GPX/TCX import and export |
| Maps | MapLibre + PMTiles | Open, self-hostable tiles, offline packs. Not Google Maps |
| Notifications | Local only; `POST_NOTIFICATIONS` at the point of use | No push server for reminders |
| PDF (doctor report) | `android.graphics.pdf.PdfDocument` | Generated on device so it works offline |
| Automation | Android intents, quick-settings tiles, deep links | `pr.md` §6.30 |

**Standards:** FHIR R4 (HAPI structures on the server), LOINC, UCUM, Open mHealth where it fits, Garmin FIT SDK (check its terms — `pr.md` §12.2).

---

## 9. AI (off by default)

- Provider-agnostic adapter in `core/`; **bring-your-own-key**, or a self-hosted/local endpoint.
- Default with an Anthropic key: **Claude Opus 5.5** (`claude-opus-5-5`) for weekly planning, **Claude Sonnet 5.5** (`claude-sonnet-5-5`) for routine summaries.
- Every response is parsed into domain objects and validated by the **same `core/` validators the UI uses**. One repair round, then fail visibly.
- Output is a **proposal**: diffed, explained, approved, snapshotted, undoable (`DESIGN.md` §14 `Coach`).
- Consent is **per data category**, and Health Connect-derived data is excluded unless separately opted in — a Play policy constraint as much as a privacy one (`pr.md` §12.3).
- MCP server exposes scoped read/write tools.

---

## 10. Testing

| Kind | Tool | Covers |
|---|---|---|
| Unit | kotlin.test + Kotest | Formulas, policies, unit conversion, rollups — runs on the JVM, no device |
| Property-based | Kotest property | **Sync merges, dedup, idempotency**: random interleavings must converge |
| Golden corpus | `tools/fixtures` | Real FIT/GPX/CSV/Health Connect exports, byte-compared importer output |
| Screenshot | Roborazzi | Every component and state, at 200% font scale and in RTL |
| E2E | Maestro | One flow per phase exit test in `BUILD-PLAN.md` |
| Performance | Macrobenchmark + baseline profiles | §12 budgets, against the five-year corpus |
| Migration | Every released schema version | Upgrade with data, each version |

---

## 11. Build, CI, release

Gradle with version catalogs and convention plugins · ktlint + Detekt, plus a **custom check that fails on `android.*` in a core module** (§3) · R8 with baseline profiles · GitHub Actions (build, unit, screenshot, benchmark on PR; signed AAB + APK on tag) · Play Console via Fastlane, plus a reproducible F-Droid-compatible APK · Renovate with a licence check that **fails on a GPL-family dependency in the client** (`pr.md` §12.4).

---

## 12. Performance budgets (enforced, not aspirational)

| Budget | Target | Measured by |
|---|---|---|
| Cold start | < 1.5 s | Macrobenchmark, mid-range device (the reference device is a 3-year-old mid-ranger, not a flagship) |
| Today, warm | < 300 ms from cache | Macrobenchmark |
| Log a set | < 2 s, one hand | E2E timing + manual |
| Barcode to logged food | < 5 s warm | E2E timing |
| Doctor report | < 30 s offline | Instrumented test |
| Passive step tracking | < 2% battery/day | 24 h device-lab run |
| Database | five-year corpus, no query > 16 ms | Benchmark suite |

With no telemetry (`pr.md` §6.31), these are measured in a device lab and a recruited beta cohort — never collected from users.

---

## 13. Repository layout

```
openhealth/
  core/            # domain types, units (UCUM), formulas, policies, validators  — no android.*
  data/            # SQLDelight schema, migrations, repositories                  — no android.*
  sync/            # change log, HLC, merge policies, client                      — no android.*
  integrations/    # importers/exporters (pure) + android/ implementations
  ui/              # Compose design system (DESIGN.md) + screens
  app-android/     # phone app, platform seams, widgets
  app-wear/        # Phase 2
  server/          # Ktor, Postgres, migrations, storage, auth
  tools/           # food index builder, fixture corpora, benchmarks
  docs/adr/        # one dated file per closed decision
  pr.md  DESIGN.md  BUILD-PLAN.md  Tech-Stack.md
```

Deferred modules (`app-ios/`, `app-watchos/`, `app-web/`, `app-desktop/`) are created when the gate opens, not before.

**Licence split:** `server/` AGPL-3.0, everything else Apache-2.0 — settle before the first public commit (`pr.md` §12.4).

---

## 14. Versions

Pin at the first commit, record in `docs/adr/`. Known-good floors: Kotlin 2.1+, AGP 8.7+, Gradle 8.9+, JDK 21, Compose BOM current, SQLDelight 2.x, Ktor 3.x, Koin 4.x, PostgreSQL 16+, **`minSdk` 28** (Health Connect), `targetSdk` current. **Verify each against its current release before pinning** — this file was written in November 2026.

---

## 15. Decisions

| `pr.md` §13 | Answer |
|---|---|
| 1 Client stack | Native Android, Kotlin + Compose, over KMP-ready core modules |
| 2 Local DB + sync | SQLite/SQLDelight + custom append-only log with per-entity merge policies |
| 3 Server | Kotlin/Ktor + PostgreSQL + S3-compatible storage, Docker Compose |
| 5 Licence | AGPL-3.0 server, Apache-2.0 client — **close before the first public commit** |
| 17 iOS start | Deferred; reviewed at every phase exit (`BUILD-PLAN.md` §2) |

Open and owned by `BUILD-PLAN.md` §2: account model, medical scope, encryption and recovery, minors, nutrition timing, food index, first devices, measurement, AI provider strategy, languages, business model, brand.
