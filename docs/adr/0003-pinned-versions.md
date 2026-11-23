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
  module builds. The plugin is wired into `:data` in B2 with the real schema.
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
