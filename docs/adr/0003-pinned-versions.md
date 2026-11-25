# ADR 0003 — Pinned build versions

**Date:** 2026-11-23 · **Phase:** B0 · **Status:** accepted
**Context:** `Tech-Stack.md` §14 gives floors, not pins, and asks that every version
be verified against its current release before pinning. This ADR records what was
checked on 2026-11-23, what was pinned, and the compatibility evidence. The values
live in `gradle/libs.versions.toml`; this file explains them.

## Pins

| Tool | Pinned | Current on 2026-11-23 | Why this one |
|---|---|---|---|
| Gradle wrapper | 9.9.2 | 9.9.2 (`services.gradle.org/versions/current`, released 2026-11-19) | Current stable; wraps JDK 21 and embeds Kotlin 2.4.21. SHA-256 pinned in `gradle-wrapper.properties`. |
| JDK toolchain | 21 | 21 (Temurin 21.0.13.1+2) | `Tech-Stack.md` §14, §16. |
| AGP | 9.5.2 | 9.5.2 stable (Google Maven metadata, 2026-11-20; 9.6.0 alphas exist but are not stable) | Current stable. 9.x has built-in Kotlin, so `org.jetbrains.kotlin.android` must not be applied; the build fails with an explicit message if it is. |
| Kotlin / KGP | 2.4.21 | 2.4.21 stable (Maven Central; 2.5.0-RC excluded) | Latest stable, and the version Gradle 9.9.2 embeds. |
| Compose BOM | 2026.11.01 | 2026.11.01 (Google Maven) | Latest; its AAR metadata requires compiling against API 37.1+, hence `compileSdk = 37` with `compileSdkMinor = 2`. |
| Compose compiler plugin | 2.4.21 | ships with Kotlin | Must equal KGP. |
| Android SDK | `compileSdk` 37 (minor 2), `targetSdk` 37, `minSdk` 28 | platform `android-37.2` installed; build-tools 36.0.0 | `minSdk 28` is set by Health Connect (`Tech-Stack.md` §8); 37.2 is the installed stable platform and the one the owner's phone runs. |
| SQLDelight | 2.4.1 | 2.4.1 (Maven Central) | Latest 2.x. |
| AndroidX core / activity / lifecycle / navigation | 1.19.1 / 1.13.0 / 2.11.1 / 2.10.2 | the same, verified on Google Maven on 2026-11-23 | Latest stable of each; only activity + core are used by B0, the rest are pinned for B1/B3 to save a later bump. |
| Koin | 4.2.2 | 4.2.2 stable (4.3.0-Beta1 excluded) | Latest stable 4.x. |
| Ktor | 3.6.1 | 3.6.1 (Maven Central) | Latest 3.x; used from B13. |
| kotlinx-coroutines | 1.11.0 | 1.11.0 | Latest stable. |
| kotlinx-datetime | 0.8.0 | 0.8.0 | Latest stable. |
| kotlinx-serialization | 1.13.0 | 1.13.0 | Latest stable. |
| Detekt | 1.23.8 | 1.23.8 (1.23.x is the current stable line) | Current stable. |
| ktlint (Gradle plugin) | 14.2.0 | 14.2.0 | Current stable. |
| Kotest | 6.2.7 | 6.2.7 (Maven Central, 2026-11-23) | Property and table tests, JVM only. |
| Bouncy Castle | 1.87 | 1.87 (Maven Central, 2026-11-23) | Argon2id for the export passphrase; pure Java, Bouncy Castle (MIT-style) licence. |
| SQLCipher for Android | 4.20.0 | 4.20.0 (Maven Central, 2026-11-23) | Community edition, BSD-3-Clause; encrypted database on device. |
| Health Connect client | 1.1.0 (added in B8, 2026-11-25) | 1.1.0 stable; 1.2.0-alpha06 is the newest alpha (Google Maven metadata, last updated 2026-08-26) | The stable channel. Requires `compileSdk 36` (the app is on 37), supports API 26+ and has no Google Play Services dependency, so the F-Droid flavour still builds. |
| AndroidX test | runner 1.7.0, ext:junit 1.3.0 | the same (Google Maven, 2026-11-23) | The instrumented encrypted-database test. |
| AndroidX Benchmark (B9) | 1.5.0 | 1.5.0 stable; 1.6.0-alpha02 is the newest alpha (Google Maven metadata, 2026-11-18) | Macrobenchmark for Today's cold and warm start; test-only, never in the APK. |
| AndroidX UI Automator (B9) | 2.3.0 | 2.3.0 stable (Google Maven) | Drives the benchmark seed activity; test-only. |

**SQLite JDBC** is not pinned in the catalogue: `app.cash.sqldelight:sqlite-driver`
2.4.1 brings its own `org.xerial:sqlite-jdbc` for JVM tests and `tools/fixtures`. It
never reaches the APK.

**KSP is intentionally absent.** Nothing in the client uses annotation processing:
Koin is runtime DI, Compose uses `org.jetbrains.kotlin.plugin.compose`, and SQLDelight
generates code from its own Gradle plugin. If a later phase needs KSP, pin the version
that matches the Kotlin line and record it here.

## Compatibility evidence (checked, not assumed)

- **AGP 9 built-in Kotlin.** Applying `org.jetbrains.kotlin.android` fails with
  "no longer required for Kotlin support since AGP 9.0"; the convention plugins apply
  only `com.android.library` / `com.android.application`, and the root build declares
  `kotlin.jvm` `apply false` so KGP 2.4.21 wins over the 2.2.10 AGP carries.
- **compileSdk minor version.** With `compileSdk = 37` alone, `:app-android` fails
  AAR-metadata checks ("compose requires 37.1 or later"); `compileSdkMinor = 2`
  resolves it. `targetSdk` stays 37.
- **SQLDelight 2.x.** Verified in a throwaway JVM module on Gradle 9.9.2 / Kotlin
  2.4.21: the `app.cash.sqldelight` plugin applies, a `.sq` file generates, and the
  module builds. In B2 the plugin is wired into `:data` with the real schema, the JVM
  driver runs the tests and `tools/fixtures`; the Android driver plus SQLCipher 4.20.0
  is verified on the owner's phone by `EncryptedDatabaseTest`.
- **Guards.** `checkPortability` fails on `import android.os.Bundle` in `:core`;
  `checkLicences` fails when the LGPL-2.1 `org.hibernate.orm:hibernate-core` is added
  to a client module, whether as an `implementation` dependency of an Android library
  or a `compileOnly` dependency of the app. Both were proved green after removal in B0.
- **Licence gate scope.** `checkLicences` inspects every `*RuntimeClasspath` and
  `*CompileClasspath` of a client module, including test configurations, and fails if
  it finds no configuration at all, so it cannot pass by seeing nothing.
- **Known warning.** Gradle 9.9.2 reports that AGP and the ktlint plugin use deprecated
  Gradle features that will break on Gradle 10. Recorded as an open issue in
  `docs/build-log.md`; do not upgrade Gradle to 10 before AGP supports it.

## Consequences

- A version bump is one edit in `gradle/libs.versions.toml` plus this ADR's table.
- The KGP pin must be raised in the root build too, because AGP's built-in Kotlin
  would otherwise keep its bundled 2.2.10.
- Compose BOM and `compileSdk` minor version move together; the AAR metadata check
  enforces it.

## B3 additions (2026-11-23)

| Library | Version | Verified | Why |
|---|---|---|---|
| `androidx.core:core-splashscreen` | 1.2.0 | 1.2.0 stable read from Google Maven metadata on 2026-11-23 (artifact last updated 2025-11-05) | Android 12 splash API, with the compat path for API 28–31. |
| `androidx.navigation:navigation-compose` | 2.10.2 | pinned in B0; first used in B3 | Type-safe routes, five peers and pushed pages. |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.1 | same line as the pinned lifecycle | One `AppViewModel` over the shell. |
| `io.insert-koin:koin-core` / `koin-android` | 4.2.2 | pinned in B0; first used in B3 | The composition root `OpenHealthApp` starts. It pulls `co.touchlab:stately-*` (Apache-2.0), now recorded in `gradle/licences/allowed.tsv`. |
| `androidx.compose.ui:ui-test-junit4` / `ui-test-manifest` | Compose BOM 2026.11.01 | B1 already used them in `:ui`; B3 adds them to `:app-android` | The four instrumented exit scenarios. |

**Not added:** `androidx.biometric:biometric`. App lock uses the platform
`BiometricPrompt` (API 28+, `minSdk` 28), so no fragment or biometric dependency
enters the client (ADR 0007).
