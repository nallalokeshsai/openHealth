# ADR 0001 — Android-only first releases, built native

**Date:** 2026-11-20
**Status:** Accepted
**Closes:** `pr.md` §13.1 (client stack), §13.2 (local database), §13.3 (server) · **Opens:** `pr.md` §13.17 (when iOS starts)

## Context

`pr.md` §8.1 offered three client options and declined to choose. The product's hard requirements are device-level: background step counting, Health Connect, BLE scales, cuffs, straps and CGMs, camera barcode scanning, GPS recording, a watch app, widgets and a foreground service for live sessions. Its correctness requirements are shared between client and server: dedup, merge policies, unit conversion, energy formulas, progression policies, rollups.

A cross-platform framework exists to serve two or more platforms. The first releases serve one.

## Decision

1. **The first releases are Android only.** iOS, watchOS, web and desktop are deferred — not cancelled — behind an explicit gate (`pr.md` §13.17, `BUILD-PLAN.md` §2 gate 17).
2. **The app is native:** Kotlin + Jetpack Compose, Wear OS in Phase 2, Glance widgets.
3. **The core is platform-neutral:** `core/`, `data/`, `sync/` and the importer half of `integrations/` contain no `android.*` or `androidx.*`, use `kotlinx-datetime`, `kotlinx.serialization`, SQLDelight and Koin, and declare every platform capability behind an interface. CI fails the build on a violation.
4. **The server is Kotlin/Ktor + PostgreSQL** and compiles those same core modules, so each rule exists once.

Full detail: `Tech-Stack.md` §2–3.

## Consequences

- No iOS users at launch; file-based import (Apple Health export, Garmin and Strava archives, FIT/GPX/TCX, CSV) is how an iPhone owner's history still arrives.
- One store review loop, a Play-Services-free build for F-Droid, and a device lab of one platform.
- Health Connect sets `minSdk` 28.
- Flutter, React Native and Capacitor were rejected: they buy cross-platform reach the project is not using, while keeping a plugin layer in front of Health Connect and BLE and leaving the server to implement the rules a second time.
- Compose Multiplatform was rejected **for now**, not on merit: it is the right answer the day iOS is in scope, and item 3 above is what makes switching it on cheap.
- The portability discipline is a standing cost. If it erodes, the iOS port stops being additive and the decision silently becomes permanent.

## Revisit when

Android adoption justifies it, an engineer owns iOS, or a port is funded. Reviewed at every phase exit.
