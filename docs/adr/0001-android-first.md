# ADR 0001 — Android first

**Date:** 2026-11-20 · **Status:** accepted · **Owner:** project owner

## Decision

openHealth ships as a native Android app (Kotlin, Jetpack Compose) first. iOS,
watchOS, the web client and desktop are deferred behind the gate in `pr.md`
§13.17 and are not designed, written or planned before it opens.

## Why

- Device access is the product: Health Connect, step sensor, BLE, camera, GPS,
  foreground services, widgets and a watch. A cross-platform layer sits between
  the app and every one of them.
- Correctness (dedup, merge, units, formulas) is shared by compiling pure Kotlin
  modules into the app and the optional server; this needs no iOS.
- An Android-only launch fits the owner's own device (Garmin watch, Android
  phone) and the self-hosting persona (sideload, F-Droid).

## Consequences

- `core/`, `data/`, `sync/` and the API half of `integrations/` stay free of
  `android.*` so the iOS port is "add targets, write actuals" (`Tech-Stack.md` §3).
- Every platform capability sits behind an interface declared in core.
- The gate is reviewed at each phase exit; the default answer is "not yet".

## Rejected

Flutter, React Native, Capacitor, Compose Multiplatform UI now: see `Tech-Stack.md` §2.
